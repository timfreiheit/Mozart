package de.timfreiheit.mozart

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.media.MediaRouter
import com.google.android.gms.cast.framework.CastSession
import de.timfreiheit.mozart.model.MozartMediaProvider
import de.timfreiheit.mozart.model.image.MozartImageLoaderCache
import de.timfreiheit.mozart.model.image.MozartMediaImageLoader
import de.timfreiheit.mozart.playback.Playback
import de.timfreiheit.mozart.playback.PlaybackManager
import de.timfreiheit.mozart.playback.QueueManager
import de.timfreiheit.mozart.playback.cast.CastPlayback
import de.timfreiheit.mozart.playback.cast.CastPlaybackSwitcher
import de.timfreiheit.mozart.playback.local.MediaPlayerPlayback
import de.timfreiheit.mozart.ui.OpenAppShadowActivity
import de.timfreiheit.mozart.utils.WearHelper
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 *
 * To implement a MediaBrowserService, you need to:
 *
 *
 *
 *
 *
 *  *  Extend [android.service.media.MediaBrowserService], implementing the media browsing
 * related methods [android.service.media.MediaBrowserService.onGetRoot] and
 * [android.service.media.MediaBrowserService.onLoadChildren];
 *  *  In onCreate, start a new [android.media.session.MediaSession] and notify its parent
 * with the session's token [android.service.media.MediaBrowserService.setSessionToken];
 *
 *
 *  *  Set a callback on the
 * [android.media.session.MediaSession.setCallback].
 * The callback will receive all the user's actions, like play, pause, etc;
 *
 *
 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])
 *
 *
 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [android.media.session.MediaSession.setPlaybackState]
 * [android.media.session.MediaSession.setMetadata] and
 * [android.media.session.MediaSession.setQueue])
 *
 *
 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 *
 *
 *
 *
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 *
 *
 *
 *
 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 *
 *
 *

 * @see [README.md](README.md) for more details.
 */
abstract class MozartMusicService : MediaBrowserServiceCompat(), PlaybackManager.PlaybackServiceCallback {

    /**
     * must be a singleton and lazy evaluated
     */
    open val queueManager: QueueManager by lazy { QueueManager(this) }

    /**
     * must be a singleton and lazy evaluated
     */
    open val playbackManager: PlaybackManager by lazy { PlaybackManager(this) }

    val mediaSession: MediaSessionCompat by lazy { MediaSessionCompat(this, "MusicService")  }
    val mediaController by lazy { MediaControllerCompat(this, mediaSession) }

    private var sessionExtras: Bundle = Bundle()
    private val delayedStopHandler = DelayedStopHandler(this)

    private val castPlaybackSwitcher: CastPlaybackSwitcher by lazy { CastPlaybackSwitcher(this) }

    abstract val mediaProvider: MozartMediaProvider

    /**
     * must be a singleton and lazy evaluated
     */
    open val mediaNotificationManager: MozartMediaNotificationManager by lazy { MozartMediaNotificationManager(this) }

    abstract val imageLoader: MozartMediaImageLoader

    open val imageLoaderCache by lazy { MozartImageLoaderCache(imageLoader) }

    val mediaRouter: MediaRouter by lazy { MediaRouter.getInstance(applicationContext) }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")

        // Start a new MediaSession
        setSessionToken(mediaSession.sessionToken)
        mediaSession.setCallback(playbackManager.mediaSessionCallback)
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        val sessionActivityIntent = getMediaSessionIntent(null)
        if (sessionActivityIntent != null) {
            mediaSession.setSessionActivity(sessionActivityIntent)
        }

        WearHelper.setSlotReservationFlags(sessionExtras, true, true)
        WearHelper.setUseBackgroundFromTheme(sessionExtras, true)
        mediaSession.setExtras(sessionExtras)

        playbackManager.updatePlaybackState()

        castPlaybackSwitcher.onCreate()

        mediaNotificationManager.onCreate()

        queueManager.addListener(QueueManagerListener())
    }

    open fun getMediaSessionIntent(metadataCompat: MediaMetadataCompat?): PendingIntent? {
        val intent = Intent(applicationContext, OpenAppShadowActivity::class.java)
        return PendingIntent.getActivity(applicationContext, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    open fun createLocalPlayback(): Playback = MediaPlayerPlayback(this)

    /**
     * create cast playback
     * this can be depended on the current CastSession
     * should return null when the Service should not connect with this session
     */
    open fun createCastPlayback(session: CastSession): Playback = CastPlayback(this)

    /**
     * (non-Javadoc)

     * @see android.app.Service.onStartCommand
     */
    override fun onStartCommand(startIntent: Intent?, flags: Int, startId: Int): Int {
        if (startIntent != null) {
            val action = startIntent.action
            if (ACTION_CMD == action) {
                handleCMD(startIntent)
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mediaSession, startIntent)
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        delayedStopHandler.removeCallbacksAndMessages(null)
        delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        return Service.START_STICKY
    }

    open fun handleCMD(startIntent: Intent) {
        val command = startIntent.getStringExtra(CMD_NAME)
        when (command) {
            CMD_PAUSE -> playbackManager.handlePauseRequest()
            CMD_PLAY -> {

                val playCommand = startIntent.getParcelableExtra<MozartPlayCommand>(ARGS_START_COMMAND)
                playbackManager.handlePlayCommand(playCommand)
            }
            CMD_STOP_CASTING -> {
                castPlaybackSwitcher.stopCasting()
                onPlaybackStop()
            }
        }
    }

    override fun setSessionToken(token: MediaSessionCompat.Token) {
        super.setSessionToken(token)
        Mozart.mediaSessionToken = token
    }

    /**
     * (non-Javadoc)

     * @see android.app.Service.onDestroy
     */
    override fun onDestroy() {
        Timber.d("onDestroy")
        // Service is being killed, so make sure we release our resources
        playbackManager.handleStopRequest(null)
        mediaNotificationManager.onDestroy()

        castPlaybackSwitcher.onDestroy()

        delayedStopHandler.removeCallbacksAndMessages(null)
        mediaSession.release()

        Mozart.mediaSessionToken = null
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int,
                           rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        return mediaProvider.onGetRoot(clientPackageName, clientUid, rootHints)
    }

    override fun onLoadChildren(parentMediaId: String,
                                result: MediaBrowserServiceCompat.Result<List<MediaItem>>) {
        mediaProvider.onLoadChildren(parentMediaId, result)
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    override fun onPlaybackStart() {
        mediaSession.isActive = true

        delayedStopHandler.removeCallbacksAndMessages(null)

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        startService(Intent(applicationContext, MozartMusicService::class.java))
    }

    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    override fun onPlaybackStop() {
        mediaSession.isActive = false
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        delayedStopHandler.removeCallbacksAndMessages(null)
        delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        stopForeground(true)
    }

    override fun onPlaybackStateUpdated(newState: PlaybackStateCompat) {
        mediaSession.setPlaybackState(newState)
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private class DelayedStopHandler constructor(service: MozartMusicService) : Handler() {
        private val weakReference: WeakReference<MozartMusicService> = WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = weakReference.get()
            if (service != null && service.playbackManager.playback != null) {
                if (service.playbackManager.playback.isPlaying) {
                    Timber.d("Ignoring delayed stop since the media player is in use.")
                    return
                }
                Timber.d("Stopping service with delay handler.")
                service.stopSelf()
            }
        }
    }

    fun getSessionExtras(): Bundle {
        return sessionExtras
    }

    fun setSessionExtras(bundle: Bundle) {
        sessionExtras.clear()
        sessionExtras.putAll(bundle)
        mediaSession.setExtras(sessionExtras)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        imageLoaderCache.onTrimMemory(level)
    }

    open inner class QueueManagerListener: QueueManager.MetadataUpdateListener {

        /**
         * @see QueueManager.MetadataUpdateListener.onMetadataChanged
         */
        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            val sessionActivityIntent = getMediaSessionIntent(metadata)
            if (sessionActivityIntent != null) {
                mediaSession.setSessionActivity(sessionActivityIntent)
            }
            mediaSession.setMetadata(metadata)
        }

        /**
         * @see QueueManager.MetadataUpdateListener.onMetadataRetrieveError
         */
        override fun onMetadataRetrieveError() {
            playbackManager.updatePlaybackState(
                    getString(R.string.error_no_metadata))
        }

        /**
         * @see QueueManager.MetadataUpdateListener.onCurrentQueueIndexUpdated
         */
        override fun onCurrentQueueIndexUpdated(queueIndex: Int) {
            playbackManager.handlePlayRequest()
        }

        /**
         * @see QueueManager.MetadataUpdateListener.onQueueUpdated
         */
        override fun onQueueUpdated(title: String?, newQueue: List<MediaSessionCompat.QueueItem>) {
            mediaSession.setQueue(newQueue)
            mediaSession.setQueueTitle(title)
        }
    }

    companion object {

        // The action of the incoming Intent indicating that it contains a command
        // to be executed (see {@link #onStartCommand})
        val ACTION_CMD = "de.timfreiheit.mozart.ACTION_CMD"

        val CMD_NAME = "CMD_NAME"

        val CMD_PLAY = "CMD_PLAY"
        val CMD_PAUSE = "CMD_PAUSE"
        val CMD_STOP_CASTING = "CMD_STOP_CASTING"

        val ARGS_START_COMMAND = "ARGS_START_COMMAND"

        // Delay stopSelf by using a handler.
        private val STOP_DELAY = 30000
    }
}
