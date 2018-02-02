package de.timfreiheit.mozart.exoplayer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.CONTENT_TYPE_MUSIC
import com.google.android.exoplayer2.C.USAGE_MEDIA
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import de.timfreiheit.mozart.model.getContentUri
import de.timfreiheit.mozart.playback.local.AUDIO_NO_FOCUS_CAN_DUCK
import de.timfreiheit.mozart.playback.local.AUDIO_NO_FOCUS_NO_DUCK
import de.timfreiheit.mozart.playback.local.LocalPlayback
import timber.log.Timber


open class ExoplayerPlayback(context: Context) : LocalPlayback(context) {
    // The volume we set the media player to when we lose audio focus, but are
    // allowed to reduce the volume instead of stopping playback.
    private val VOLUME_DUCK = 0.2f
    // The volume we set the media player when we have audio focus.
    private val VOLUME_NORMAL = 1.0f

    private val wifiLock: WifiManager.WifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "uAmp_lock")

    private var exoPlayer: SimpleExoPlayer? = null
    private val eventListener = ExoPlayerEventListener()

    @Volatile private var currentPosition: Long = 0
    @Volatile private var duration: Long = -1

    // Whether to return STATE_NONE or STATE_STOPPED when mExoPlayer is null;
    private var exoPlayerNullIsStopped = false

    override val isConnected = true

    var isPreparing = false

    private var playOnFocusGain: Boolean = false

    override val isPlaying: Boolean
        get() = playOnFocusGain || exoPlayer?.playWhenReady ?: false

    override var currentStreamPosition: Long
        set(value) {}
        get() = exoPlayer?.currentPosition ?: 0

    override val streamDuration: Long
        get() = exoPlayer?.duration ?: 0

    override fun updateLastKnownStreamPosition() {
        // Nothing to do. Position maintained by ExoPlayer.
    }

    override fun pause() {
        // Pause player and cancel the 'foreground service' state.
        exoPlayer?.playWhenReady = false
        currentPosition = currentStreamPosition
        // While paused, retain the player instance, but give up audio focus.
        releaseResources(false)
    }

    override fun onStop(notifyListeners: Boolean) {
        super.onStop(notifyListeners)
        currentPosition = currentStreamPosition
        // Relax all resources
        releaseResources(true)
    }

    override fun play(item: MediaMetadataCompat) {
        super.play(item)
        playOnFocusGain = true
        val mediaId = item.description.mediaId
        val mediaHasChanged = currentMedia == null || mediaId != currentMedia?.description?.mediaId
        if (mediaHasChanged) {
            currentPosition = 0
            currentMedia = item
        }

        releaseResources(false) // release everything except the player
        var source = item.getContentUri()

        if (source != null) {
            source = source.replace(" ".toRegex(), "%20") // Escape spaces for URLs
        }

        if (exoPlayer == null) {
            exoPlayer = ExoPlayerFactory.newSimpleInstance(
                    DefaultRenderersFactory(context), DefaultTrackSelector(), DefaultLoadControl())
            exoPlayer?.addListener(eventListener)
        }

        updateAudioAttributes(item)

        // Produces DataSource instances through which media data is loaded.
        val dataSourceFactory = DefaultDataSourceFactory(
                context, Util.getUserAgent(context, "uamp"), null)
        // Produces Extractor instances for parsing the media data.
        val extractorsFactory = DefaultExtractorsFactory()
        // The MediaSource represents the media to be played.
        val mediaSource = ExtractorMediaSource(
                Uri.parse(source), dataSourceFactory, extractorsFactory, null, null)

        isPreparing = true
        // Prepares media to play (happens on background thread) and triggers
        // {@code onPlayerStateChanged} callback when the stream is ready to play.
        exoPlayer?.prepare(mediaSource)

        // If we are streaming from the internet, we want to hold a
        // Wifi lock, which prevents the Wifi radio from going to
        // sleep while the song is playing.
        wifiLock.acquire()


        configurePlayerState()
    }

    /**
     * @see SimpleExoPlayer.setAudioAttributes
     * by default is uses [CONTENT_TYPE_MUSIC] with [USAGE_MEDIA]
     */
    open fun updateAudioAttributes(item: MediaMetadataCompat) {
        val audioAttributes = AudioAttributes.Builder()
                .setContentType(CONTENT_TYPE_MUSIC)
                .setUsage(USAGE_MEDIA)
                .build()
        exoPlayer?.audioAttributes = audioAttributes
    }

    override var state: Int
        set(value) {
            super.state = value
        }
        get() {
            if (exoPlayer == null) {
                return if (exoPlayerNullIsStopped)
                    PlaybackStateCompat.STATE_STOPPED
                else
                    PlaybackStateCompat.STATE_NONE
            }
            when (exoPlayer?.playbackState) {
                Player.STATE_IDLE -> return PlaybackStateCompat.STATE_PAUSED
                Player.STATE_BUFFERING -> return PlaybackStateCompat.STATE_BUFFERING
                Player.STATE_READY -> return if (exoPlayer?.playWhenReady ?: false)
                    PlaybackStateCompat.STATE_PLAYING
                else
                    PlaybackStateCompat.STATE_PAUSED
                Player.STATE_ENDED -> return PlaybackStateCompat.STATE_PAUSED
                else -> return PlaybackStateCompat.STATE_NONE
            }
        }

    override fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    /**
     * Releases resources used by the service for playback, which is mostly just the WiFi lock for
     * local playback. If requested, the ExoPlayer instance is also released.

     * @param releasePlayer Indicates whether the player should also be released
     */
    private fun releaseResources(releasePlayer: Boolean) {

        // Stops and releases player (if requested and available).
        if (releasePlayer && exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer?.removeListener(eventListener)
            exoPlayer = null
            exoPlayerNullIsStopped = true
            playOnFocusGain = false
        }

        // we can also release the Wifi lock, if we're holding it
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
    }


    /**
     * Reconfigures the player according to audio focus settings and starts/restarts it. This method
     * starts/restarts the ExoPlayer instance respecting the current audio focus state. So if we
     * have focus, it will play normally; if we don't have focus, it will either leave the player
     * paused or set it to a low volume, depending on what is permitted by the current focus
     * settings.
     */
    private fun configurePlayerState() {
        Timber.d("configurePlayerState. mCurrentAudioFocusState=", audioFocusStatus)
        if (audioFocusStatus == AUDIO_NO_FOCUS_NO_DUCK) {
            // We don't have audio focus and can't duck, so we have to pause
            pause()
        } else {
            registerAudioNoisyReceiver()

            if (audioFocusStatus == AUDIO_NO_FOCUS_CAN_DUCK) {
                // We're permitted to play, but only if we 'duck', ie: play softly
                exoPlayer?.volume = VOLUME_DUCK
            } else {
                exoPlayer?.volume = VOLUME_NORMAL
            }

            // If we were playing when we lost focus, we need to resume playing.
            if (playOnFocusGain) {
                exoPlayer?.playWhenReady = true
                playOnFocusGain = false
            }
        }
    }

    open fun onPrepared() {
        Timber.d("onPrepared")
        exoPlayer?.seekTo(currentPosition)
    }

    private inner class ExoPlayerEventListener : Player.EventListener {

        override fun onTimelineChanged(timeline: Timeline, manifest: Any?) {
            // Nothing to do.
        }

        override fun onTracksChanged(
                trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
            // Nothing to do.
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            // Nothing to do.
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (isPreparing && playbackState == Player.STATE_READY) {
                isPreparing = false
                onPrepared()
            }
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY -> {
                    val state = state
                    Timber.d("onPlayerStateChanged($state)")
                    callback.onPlaybackStatusChanged(state)
                }
                Player.STATE_ENDED -> {
                    // The media player finished playing the current song.
                    callback.onCompletion()
                }
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            val what = when (error.type) {
                ExoPlaybackException.TYPE_SOURCE -> error.sourceException.message
                ExoPlaybackException.TYPE_RENDERER -> error.rendererException.message
                ExoPlaybackException.TYPE_UNEXPECTED -> error.unexpectedException.message
                else -> "Unknown: " + error
            }

            Timber.e("ExoPlayer error: what= $what")
            callback.onError("ExoPlayer error $what")
        }

        override fun onPositionDiscontinuity() {
            // Nothing to do.
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            // Nothing to do.
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            // Nothing to do.
        }
    }
}