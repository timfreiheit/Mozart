package de.timfreiheit.mozart

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.RemoteException
import android.support.annotation.CallSuper
import android.support.annotation.MainThread
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

import de.timfreiheit.mozart.model.image.CoverImage
import de.timfreiheit.mozart.playback.cast.CastPlaybackSwitcher
import de.timfreiheit.mozart.ui.OpenAppShadowActivity
import de.timfreiheit.mozart.utils.getThemeColor
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
abstract class MozartMediaNotificationManager @Throws(RemoteException::class) constructor(
        private val service: MozartMusicService
) {
    private var sessionToken: MediaSessionCompat.Token? = null
    private var controller: MediaControllerCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    private var mediaControllerCallback: MediaControllerCallback? = null

    var playbackState: PlaybackStateCompat? = null
    var metadata: MediaMetadataCompat? = null

    private val notificationManager = NotificationManagerCompat.from(service)

    private val notificationActionReceiver = MozartMediaNotificationActionReceiver(service, OnActionReceivedListener())

    open val notificationId: Int = 412

    val notificationChannelId: String by lazy { createPlaybackChannel(service.applicationContext) }

    val notificationColor: Int = this.service.getThemeColor(R.attr.colorPrimary,
            Color.DKGRAY)

    private var started = false

    init {
        updateSessionToken()

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        notificationManager.cancelAll()
    }

    @CallSuper
    fun onCreate() {
        registerMediaControllerCallback()
    }

    @CallSuper
    fun onDestroy() {
        stopNotification()
        unregisterMediaControllerCallback()
    }

    private fun createPlaybackChannel(context: Context): String {
        val channelId = "de.freiheit.mozart.playback_channel"
        if (Build.VERSION.SDK_INT < 26) {
            return channelId
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.playback_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(channelId, channelName, importance).apply {
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }
        return channelId
    }

    private fun registerMediaControllerCallback() {
        unregisterMediaControllerCallback()
        mediaControllerCallback = MediaControllerCallback().also {
            service.mediaController.registerCallback(it)
        }
        onPlaybackStateChanged(service.mediaController.playbackState)
    }

    private fun unregisterMediaControllerCallback() {
        mediaControllerCallback?.let { mediaControllerCallback ->
            service.mediaController.unregisterCallback(mediaControllerCallback)
        }
        mediaControllerCallback = null
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before [.stopNotification] is called.
     */
    @MainThread
    private fun startNotification() {
        Timber.d("startNotification")
        if (!started) {
            metadata = controller!!.metadata
            playbackState = controller!!.playbackState

            // The notification must be updated after setting started to true
            val notification = createNotification()
            if (notification != null) {
                controller?.registerCallback(updateNotificationCallback)

                service.registerReceiver(notificationActionReceiver, notificationActionReceiver.filter)

                service.startForeground(notificationId, notification)
                started = true
            }
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    private fun stopNotification() {
        if (started) {
            started = false
            controller?.unregisterCallback(updateNotificationCallback)
            try {
                notificationManager.cancel(notificationId)
                service.unregisterReceiver(notificationActionReceiver)
            } catch (ex: IllegalArgumentException) {
                // ignore if the receiver is not registered.
            }

            service.stopForeground(true)
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see [android.media.session.MediaController.Callback.onSessionDestroyed])
     */
    @Throws(RemoteException::class)
    private fun updateSessionToken() {
        val freshToken = service.sessionToken
        if (sessionToken == null && freshToken != null || sessionToken != null && sessionToken != freshToken) {
            controller?.unregisterCallback(updateNotificationCallback)

            sessionToken = freshToken
            if (freshToken != null) {
                controller = MediaControllerCompat(service, freshToken)
                transportControls = controller?.transportControls
                if (started) {
                    controller?.registerCallback(updateNotificationCallback)
                }
            }
        }
    }

    open fun createContentIntent(description: MediaDescriptionCompat): PendingIntent {
        val sessionActivityIntent: PendingIntent? = controller?.sessionActivity
        if (sessionActivityIntent == null) {
            val openUI = Intent(service, OpenAppShadowActivity::class.java)
            return PendingIntent.getActivity(service, notificationActionReceiver.requestCode, openUI,
                    PendingIntent.FLAG_CANCEL_CURRENT)
        }
        return sessionActivityIntent
    }

    private val updateNotificationCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            playbackState = state
            Timber.d("Received new playback state %s", state)
            if (state.state == PlaybackStateCompat.STATE_STOPPED || state.state == PlaybackStateCompat.STATE_NONE) {
                stopNotification()
            } else {
                val notification = createNotification()
                if (notification != null) {
                    notificationManager.notify(notificationId, notification)
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            this@MozartMediaNotificationManager.metadata = metadata
            Timber.d("Received new metadata %s", metadata)
            val notification = createNotification()
            if (notification != null) {
                notificationManager.notify(notificationId, notification)
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            Timber.d("Session was destroyed, resetting to the new session token")
            try {
                updateSessionToken()
            } catch (e: RemoteException) {
                Timber.e(e, "could not connect media controller")
            }

        }
    }

    open fun createNotification(): Notification? {
        Timber.d("updateNotificationMetadata. mMetadata= %s" + metadata!!)
        if (metadata == null || playbackState == null) {
            return null
        }

        val notificationBuilder = NotificationCompat.Builder(service)

        val availableActions = addNotificationsActions(notificationBuilder)

        val description = metadata!!.description

        var fetchArtUrl: String? = null
        var art: CoverImage? = null
        if (description.iconUri != null) {
            // This sample assumes the iconUri will be a valid URL formatted String, but
            // it can actually be any valid Android Uri formatted String.
            // async fetch the album art icon
            val artUrl = description.iconUri!!.toString()
            art = service.imageLoaderCache.getCachedBitmapFromMemory(artUrl)
            if (art == null) {
                fetchArtUrl = artUrl
                // use a placeholder art while the remote art is being downloaded
                art = defaultCover
            }
        }

        notificationBuilder
                .setChannelId(notificationChannelId)
                .setStyle(createMediaStyle(availableActions))
                .setSmallIcon(notificationIcon)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setUsesChronometer(true)
                .setContentIntent(createContentIntent(description))
                .setContentTitle(description.title)
                .setContentText(description.subtitle)
                .setDeleteIntent(notificationActionReceiver.stopIntent)

        if (Build.VERSION.SDK_INT < 26 && notificationColor > 0) {
            notificationBuilder.color = notificationColor
        }

        if (art != null && art.largeImage != null) {
            notificationBuilder.setLargeIcon(art.largeImage)
        }

        if (controller != null && controller?.extras != null) {
            val castName = controller!!.extras.getString(CastPlaybackSwitcher.EXTRA_CONNECTED_CAST)
            if (castName != null) {
                val castInfo = service.resources
                        .getString(R.string.casting_to_device, castName)
                notificationBuilder.setSubText(castInfo)
                notificationBuilder.addAction(R.drawable.ic_dialog_close_light,
                        service.getString(R.string.stop_casting), notificationActionReceiver.stopCastIntent)
            }
        }

        setNotificationPlaybackState(notificationBuilder)
        if (fetchArtUrl != null) {
            val finalFetchArtUrl = fetchArtUrl
            service.imageLoaderCache.loadCover(fetchArtUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ coverImage ->
                        if (metadata != null && metadata!!.description.iconUri != null &&
                                metadata!!.description.iconUri!!.toString() == finalFetchArtUrl) {
                            // If the media is still the same, update the notification:
                            if (coverImage.largeImage != null) {
                                notificationBuilder.setLargeIcon(coverImage.largeImage)
                                notificationManager.notify(notificationId, notificationBuilder.build())
                            }
                        }
                    }) { throwable -> Timber.d(throwable, "error fetchBitmapFromURLAsync: set bitmap to %s", finalFetchArtUrl) }
        }

        return notificationBuilder.build()
    }

    abstract val notificationIcon: Int

    open val defaultCover: CoverImage
        get() = CoverImage(BitmapFactory.decodeResource(service.resources, R.drawable.ic_default_art), null)

    open fun createMediaStyle(availableActions: Int): android.support.v4.media.app.NotificationCompat.MediaStyle {
        val actionCount = when {
            availableActions < 0 -> 0
            availableActions > 3 -> 3
            else -> availableActions
        }

        val compactViewActions = IntArray(actionCount)
        for (i in compactViewActions.indices) {
            compactViewActions[i] = i
        }

        return android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(*compactViewActions)
                .setShowCancelButton(true)
                .setCancelButtonIntent(notificationActionReceiver.stopIntent)
                .setMediaSession(sessionToken)
    }

    open fun addNotificationsActions(builder: NotificationCompat.Builder): Int {

        var availableActions = 0

        val showPrevButton = ((playbackState?.actions ?: 0L) and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0L
        if (showPrevButton) {
            builder.addAction(R.drawable.ic_skip_previous_white_24dp,
                    service.getString(R.string.label_previous), notificationActionReceiver.previousIntent)
            availableActions++
        }

        val label: String
        val icon: Int
        val intent: PendingIntent
        if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            label = service.getString(R.string.label_pause)
            icon = R.drawable.ic_pause_white_24dp
            intent = notificationActionReceiver.pauseIntent
        } else {
            label = service.getString(R.string.label_play)
            icon = R.drawable.ic_play_arrow_white_24dp
            intent = notificationActionReceiver.playIntent
        }
        builder.addAction(NotificationCompat.Action(icon, label, intent))
        availableActions++

        // If skip to next action is enabled
        val showSkipButton = ((playbackState?.actions ?: 0L) and PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0L
        if (showSkipButton) {
            builder.addAction(R.drawable.ic_skip_next_white_24dp,
                    service.getString(R.string.label_next), notificationActionReceiver.nextIntent)
            availableActions++
        }

        return availableActions
    }

    private fun setNotificationPlaybackState(builder: NotificationCompat.Builder) {
        val playbackState = playbackState
        Timber.d("updateNotificationPlaybackState. mPlaybackState=" + playbackState)
        if (playbackState == null || !started) {
            Timber.d("updateNotificationPlaybackState. cancelling notification!")
            service.stopForeground(true)
            return
        }
        if (playbackState.state == PlaybackStateCompat.STATE_PLAYING && playbackState.position >= 0) {
            Timber.d("updateNotificationPlaybackState. updating playback position to %d seconds",
                    (System.currentTimeMillis() - playbackState.position) / 1000)
            builder
                    .setWhen(System.currentTimeMillis() - playbackState.position)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
        } else {
            Timber.d("updateNotificationPlaybackState. hiding playback position")
            builder
                    .setWhen(0)
                    .setShowWhen(false)
                    .setUsesChronometer(false)
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(playbackState.state == PlaybackStateCompat.STATE_PLAYING)
    }

    private fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        if (state == null) {
            stopNotification()
            return
        }
        Timber.d("updatePlaybackState, playback state= %d", state.state)

        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.STATE_CONNECTING,
            PlaybackStateCompat.STATE_FAST_FORWARDING,
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM,
            PlaybackStateCompat.STATE_REWINDING -> {
                startNotification()
            }
            else -> {
                stopNotification()
            }
        }
    }

    private inner class OnActionReceivedListener : MozartMediaNotificationActionReceiver.ActionListener {

        override fun pause() {
            transportControls?.pause()
        }

        override fun play() {
            transportControls?.play()
        }

        override fun skipToNext() {
            transportControls?.skipToNext()
        }

        override fun skipToPrevious() {
            transportControls?.skipToPrevious()
        }

        override fun stop() {
            transportControls?.stop()
        }

        override fun stopCasting() {
            service.startService(MozartServiceActions.stopCasting(service))
        }

    }

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            this@MozartMediaNotificationManager.onPlaybackStateChanged(state)
        }

    }

}
