package de.timfreiheit.mozart.playback

import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.MozartPlayCommand
import de.timfreiheit.mozart.model.STATE_DURATION
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
open class PlaybackManager(
        val mozartMusicService: MozartMusicService
) : Playback.Callback {

    var playback: Playback = mozartMusicService.createLocalPlayback()
        private set
    private val serviceCallback: PlaybackServiceCallback
    val mediaSessionCallback: MediaSessionCallback = MediaSessionCallback()

    private var lastPlayCommand: MozartPlayCommand? = null

    private val getDataDisposable = CompositeDisposable()

    open val availableActions: Long
        get() {
            var actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

            actions = if (playback.isPlaying) {
                actions or PlaybackStateCompat.ACTION_PAUSE
            } else {
                actions or PlaybackStateCompat.ACTION_PLAY
            }

            val playlistSize = mozartMusicService.queueManager.currentQueueSize
            val currentIndex = mozartMusicService.queueManager.currentIndex

            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

            if (currentIndex < playlistSize - 1) {
                actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            }

            return actions
        }

    init {
        this.serviceCallback = mozartMusicService
        this.playback.addCallback(this)
    }

    fun getMediaSessionCallback(): MediaSessionCompat.Callback {
        return mediaSessionCallback
    }

    /**
     * Handle a request to play music
     */
    fun handlePlayRequest() {
        Timber.d("handlePlayRequest: mState= %d", playback.state)
        val currentMusic = mozartMusicService.queueManager.currentMusic
        if (currentMusic != null) {
            currentMusic.description.mediaId?.let { playMediaById(it) }
        }
    }

    fun playMediaById(mediaId: String) {

        val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(availableActions)
                .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f, SystemClock.elapsedRealtime())
        serviceCallback.onPlaybackStateUpdated(stateBuilder.build())

        getDataDisposable.clear()
        getDataDisposable.add(mozartMusicService.mediaProvider.getMediaById(mediaId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : DisposableSingleObserver<MediaMetadataCompat>() {
                    override fun onSuccess(mediaMetadata: MediaMetadataCompat) {
                        playback.currentMedia = mediaMetadata
                        val lastPlayCommand = lastPlayCommand
                        if (lastPlayCommand?.mediaId() != null && lastPlayCommand.mediaId() == mediaMetadata.description.mediaId) {
                            playback.currentStreamPosition = lastPlayCommand.mediaPlaybackPosition()
                            this@PlaybackManager.lastPlayCommand = null
                        }

                        playback.play(mediaMetadata)
                        serviceCallback.onPlaybackStart()
                    }

                    override fun onError(e: Throwable) {
                        handleStopRequest(e.localizedMessage)
                    }
                }))
    }

    /**
     * Handle a request to pause music
     */
    fun handlePauseRequest() {
        Timber.d("handlePauseRequest: mState= %d", playback.state)
        if (playback.isPlaying) {
            playback.pause()
            serviceCallback.onPlaybackStop()
        }
    }

    /**
     * Handle a request to stop music
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     * message will be set in the PlaybackState and will be visible to
     * MediaController clients.
     */
    fun handleStopRequest(withError: String?) {
        Timber.d("handleStopRequest: mState= %d error %s", playback.state, withError)
        playback.onStop(true)
        serviceCallback.onPlaybackStop()
        updatePlaybackState(withError)
    }


    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    fun updatePlaybackState(error: String? = null) {
        Timber.d("updatePlaybackState, playback state= %d", playback.state)
        var position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        var duration: Long = -1
        if (playback.isConnected) {
            position = playback.currentStreamPosition
            duration = playback.streamDuration
        }

        if (duration < 0) {
            // fall back to provided metadata when available
            val metaData = mozartMusicService.mediaController.metadata
            if (metaData != null) {
                duration = metaData.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt().toLong()
                if (duration == 0L || duration < position) {
                    // position is not set or invalid
                    duration = -1
                }
            }
        }

        val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(availableActions)

        setCustomAction(stateBuilder)
        var state = playback.state

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(1, error)
            state = PlaybackStateCompat.STATE_ERROR
        }

        val extras = Bundle()
        extras.putLong(STATE_DURATION, duration)
        addPlaybackStateExtras(extras)
        stateBuilder.setExtras(extras)

        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime())

        // Set the activeQueueItemId if the current index is valid.
        val currentMusic = mozartMusicService.queueManager.currentMusic
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.queueId)
        }

        serviceCallback.onPlaybackStateUpdated(stateBuilder.build())
    }

    open fun addPlaybackStateExtras(bundle: Bundle) {

    }

    open fun setCustomAction(stateBuilder: PlaybackStateCompat.Builder) {}

    /**
     * Implementation of the Playback.Callback interface
     */
    override fun onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mozartMusicService.queueManager.skipQueuePosition(1)) {
            handlePlayRequest()
            mozartMusicService.queueManager.updateMetadata()
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null)
        }
    }

    override fun onPlaybackStatusChanged(state: Int) {
        updatePlaybackState()
    }

    override fun onError(error: String?) {
        updatePlaybackState(error)
    }

    /**
     * Switch to a different Playback instance, maintaining all playback state, if possible.
     *
     * @param playback switch to this playback
     */
    fun switchToPlayback(playback: Playback, resumePlaying: Boolean) {
        Timber.d("switchToPlayback(%s)", playback)
        // suspend the current one.
        val oldState = this.playback.state
        val pos = this.playback.currentStreamPosition
        val currentMedia = this.playback.currentMedia
        this.playback.onStop(false)
        playback.addCallback(this)
        playback.currentStreamPosition = if (pos < 0) 0 else pos
        playback.currentMedia = currentMedia
        playback.onStart()
        // finally swap the instance
        this.playback = playback
        when (oldState) {
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING, PlaybackStateCompat.STATE_PAUSED -> this.playback.pause()
            PlaybackStateCompat.STATE_PLAYING -> {
                val currentMusic = mozartMusicService.queueManager.currentMusic
                if (resumePlaying && currentMusic != null) {
                    currentMusic.description.mediaId?.let { playMediaById(it) }
                } else if (!resumePlaying) {
                    this.playback.pause()
                } else {
                    this.playback.onStop(true)
                }
            }
            PlaybackStateCompat.STATE_NONE -> {
            }
            else -> Timber.d("Default called. Old state is %s", oldState)
        }
    }

    fun handlePlayFromMediaId(mediaId: String, extras: Bundle?) {
        Timber.d("handlePlayFromMediaId() called with mediaId = [$mediaId], extras = [$extras]")
        mozartMusicService.queueManager.updateQueueByMediaId(mediaId)
                .subscribe({ this.handlePlayRequest() }, { })
    }

    fun handlePlaySingleMediaId(mediaId: String) {
        Timber.d("handlePlaySingleMediaId() called with mediaId = [$mediaId]")
        mozartMusicService.queueManager.setQueueByMediaId(mediaId)
                .subscribe({ this.handlePlayRequest() }, { })
    }

    fun handlePlayPlaylist(playlistId: String, mediaId: String?) {
        Timber.d("handlePlayPlaylist() called with playlistId = [$playlistId], mediaId = [$mediaId]")
        mozartMusicService.queueManager.setQueueByPlaylistId(playlistId, mediaId)
                .subscribe({ this.handlePlayRequest() }, { })
    }

    fun handlePlayPlaylist(playlistId: String, position: Int) {
        Timber.d("handlePlayPlaylist() called with playlistId = [$playlistId], position = [$position]")
        mozartMusicService.queueManager.setQueueByPlaylistId(playlistId, position)
                .subscribe({ this.handlePlayRequest() }, { })
    }

    /**
     * Called when a {@link MediaControllerCompat} wants a
     * {@link PlaybackStateCompat.CustomAction} to be performed.
     *
     * @param action The action that was originally sent in the
     *            {@link PlaybackStateCompat.CustomAction}.
     * @param extras Optional extras specified by the
     *            {@link MediaControllerCompat}.
     * @see #ACTION_FLAG_AS_INAPPROPRIATE
     * @see #ACTION_SKIP_AD
     * @see #ACTION_FOLLOW
     * @see #ACTION_UNFOLLOW
     */
    open fun handleCustomAction(action: String, extras: Bundle?) {
        Timber.d("handleCustomAction() called with action = [$action], extras = [$extras]")
    }

    open fun handlePlayFromSearch(query: String?, extras: Bundle?) {
        Timber.d("playFromSearch  query=%s  extras=%s", query, extras)
    }

    fun handlePlayCommand(playCommand: MozartPlayCommand) {
        if (playCommand.playlistId() == null && playCommand.mediaId() == null) {
            return
        }
        lastPlayCommand = playCommand

        val playlistId = playCommand.playlistId()
        if (playlistId == null) {
            handlePlaySingleMediaId(playCommand.mediaId()!!)
        } else {
            if (playCommand.mediaId() != null) {
                handlePlayPlaylist(playlistId, playCommand.mediaId())
            } else {
                handlePlayPlaylist(playlistId, playCommand.playlistPosition())
            }
        }
    }

    open fun handleCustomCommand(command: String, extras: Bundle?) {

    }

    open inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Timber.d("play")
            handlePlayRequest()
        }

        override fun onSkipToQueueItem(queueId: Long) {
            Timber.d("OnSkipToQueueItem: %d", queueId)
            mozartMusicService.queueManager.setCurrentQueueItem(queueId)
            mozartMusicService.queueManager.updateMetadata()
        }

        override fun onSeekTo(position: Long) {
            Timber.d("onSeekTo: %d", position)
            playback.seekTo(position.toInt().toLong())
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            Timber.d("playFromMediaId mediaId: %s,  extras= %s", mediaId, extras)
            handlePlayFromMediaId(mediaId, extras)
        }

        override fun onPause() {
            Timber.d("pause. current state= %d", playback.state)
            handlePauseRequest()
        }

        override fun onStop() {
            Timber.d("stop. current state= %d", playback.state)
            handleStopRequest(null)
        }

        override fun onSkipToNext() {
            Timber.d("skipToNext")
            if (mozartMusicService.queueManager.skipQueuePosition(1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            mozartMusicService.queueManager.updateMetadata()
        }

        override fun onSkipToPrevious() {
            if (mozartMusicService.queueManager.skipQueuePosition(-1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            mozartMusicService.queueManager.updateMetadata()
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            handleCustomAction(action, extras)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            handlePlayFromSearch(query, extras)
        }
    }


    interface PlaybackServiceCallback {
        fun onPlaybackStart()

        fun onPlaybackStop()

        fun onPlaybackStateUpdated(newState: PlaybackStateCompat)
    }

}
