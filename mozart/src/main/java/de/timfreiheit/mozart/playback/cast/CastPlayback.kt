package de.timfreiheit.mozart.playback.cast

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.playback.Playback
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * An implementation of Playback that talks to Cast.
 */
class CastPlayback(private val service: MozartMusicService) : Playback() {
    private val remoteMediaClient: RemoteMediaClient
    private val remoteMediaClientListener: RemoteMediaClient.Listener

    @Volatile private var currentPosition: Long = 0
    @Volatile private var duration: Long = 0

    init {
        val castSession = CastContext.getSharedInstance(service.applicationContext).sessionManager
                .currentCastSession
        remoteMediaClient = castSession.remoteMediaClient
        remoteMediaClientListener = CastMediaClientListener()
    }

    override fun onStart() {
        super.onStart()
        remoteMediaClient.addListener(remoteMediaClientListener)
    }

    override fun onStop(notifyListeners: Boolean) {
        super.onStop(notifyListeners)
        remoteMediaClient.removeListener(remoteMediaClientListener)
        state = PlaybackStateCompat.STATE_STOPPED
        if (notifyListeners) {
            callback.onPlaybackStatusChanged(state)
        }
    }

    override var currentStreamPosition: Long
        set(value) {
            this.currentPosition = value
        }
        get() {
            return if (!isConnected) {
                currentPosition
            } else remoteMediaClient.approximateStreamPosition
        }

    override val streamDuration: Long
        get() {
            return if (!isConnected) {
                duration
            } else remoteMediaClient.streamDuration
        }

    override fun updateLastKnownStreamPosition() {
        currentPosition = currentStreamPosition
    }

    override fun play(item: MediaMetadataCompat) {
        try {
            Timber.d("play(%s)", item.description.mediaId)
            loadMedia(item, true)
            state = PlaybackStateCompat.STATE_BUFFERING
            callback.onPlaybackStatusChanged(state)

        } catch (e: JSONException) {
            Timber.e(e, "Exception loading media")
            callback.onError(e.message)

        }

    }

    override fun pause() {
        try {
            if (remoteMediaClient.hasMediaSession()) {
                remoteMediaClient.pause()
                currentPosition = remoteMediaClient.approximateStreamPosition.toInt().toLong()
                duration = remoteMediaClient.streamDuration.toInt().toLong()
            } else {
                loadMedia(currentMedia, false)
            }
        } catch (e: JSONException) {
            Timber.e(e, "Exception pausing cast playback")
            callback.onError(e.message)

        }

    }

    override fun seekTo(position: Long) {
        if (currentMedia == null) {
            callback.onError("seekTo cannot be calling in the absence of mediaId.")

            return
        }
        try {
            if (remoteMediaClient.hasMediaSession()) {
                remoteMediaClient.seek(position)
                currentPosition = position
            } else {
                currentPosition = position
                loadMedia(currentMedia, false)
            }
        } catch (e: JSONException) {
            Timber.e(e, "Exception pausing cast playback")
            callback.onError(e.message)
        }

    }

    override val isConnected: Boolean
        get() {
            val castSession = CastContext.getSharedInstance(service.applicationContext).sessionManager
                    .currentCastSession
            return castSession != null && castSession.isConnected
        }

    override val isPlaying: Boolean
        get() = isConnected && remoteMediaClient.isPlaying

    private fun loadMedia(item: MediaMetadataCompat?, autoPlay: Boolean) {
        if (item == null) {
            return
        }
        if (currentMedia == null || !TextUtils.equals(item.description.mediaId, currentMedia?.description?.mediaId)) {
            currentMedia = item
            currentPosition = 0
        }
        val customData = JSONObject()
        customData.put(ITEM_ID, item.description.mediaId)
        customData.put(PLAYLIST_ID, service.queueManager.playlistId)
        val media = item.toMediaInfo(customData)
        Timber.d("loadMedia(%s)", item.description.mediaId)
        remoteMediaClient.load(media, autoPlay, currentPosition, customData)
    }

    private fun setMetadataFromRemote() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            Timber.d("setMetadataFromRemote() called")
            val mediaInfo = remoteMediaClient.mediaInfo ?: return
            val customData = mediaInfo.customData

            if (customData != null && customData.has(ITEM_ID)) {
                val remoteMediaId = customData.getString(ITEM_ID)
                val playlistId = customData.optString(PLAYLIST_ID, null)

                Timber.d("setMetadataFromRemote(%s)", remoteMediaId)

                if (remoteMediaId != null && currentMedia != null && TextUtils.equals(remoteMediaId, currentMedia?.description?.mediaId)) {
                    return
                }

                if (!UPDATE_META_DATA_FROM_REMOTE) {
                    return
                }

                var completable = Completable.error(Exception())
                if (playlistId != null) {
                    completable = service.mediaProvider.getPlaylistById(playlistId)
                            .subscribeOn(Schedulers.io())
                            .flatMapCompletable { playlist ->
                                val index = playlist.getPositionByMediaId(remoteMediaId)
                                service.queueManager.setQueueFromPlaylist(playlist, index)
                            }
                }
                completable.onErrorResumeNext {
                    if (remoteMediaId != null) {
                        service.mediaProvider.getMediaById(remoteMediaId)
                                .subscribeOn(Schedulers.io())
                                .flatMapCompletable {
                                    Timber.d("setQueueByMediaId(%s)", remoteMediaId)
                                    service.queueManager.setQueueByMediaId(remoteMediaId)
                                }
                                .doOnComplete {
                                    // the remoteMedia should be skipped in queue
                                    service.queueManager.skipQueuePosition(1)
                                }
                    } else {
                        Completable.complete()
                    }
                }.observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ this.updateLastKnownStreamPosition() }) {
                            // remote media not found. stop playback
                            onStop(true)
                        }
            } else {
                // remote media not found. stop playback
                onStop(true)
            }
        } catch (e: JSONException) {
            Timber.e(e, "Exception processing update metadata")
        }

    }

    private fun updatePlaybackState() {
        val status = remoteMediaClient.playerState

        Timber.d("onRemoteMediaPlayerStatusUpdated %d", status)

        // Convert the remote playback states to media playback states.
        when (status) {
            MediaStatus.PLAYER_STATE_IDLE -> {
                Timber.d("onRemoteMediaPlayerStatusUpdated %d: IdleReason %d", status, remoteMediaClient.idleReason)
                when (remoteMediaClient.idleReason) {
                    MediaStatus.IDLE_REASON_FINISHED -> callback.onCompletion()
                    MediaStatus.IDLE_REASON_ERROR -> callback.onError("IDLE_REASON_ERROR")
                    MediaStatus.IDLE_REASON_CANCELED -> {
                        state = PlaybackStateCompat.STATE_STOPPED
                        callback.onPlaybackStatusChanged(state)
                    }
                }
            }
            MediaStatus.PLAYER_STATE_BUFFERING -> {
                state = PlaybackStateCompat.STATE_BUFFERING
                callback.onPlaybackStatusChanged(state)
            }
            MediaStatus.PLAYER_STATE_PLAYING -> {
                state = PlaybackStateCompat.STATE_PLAYING
                setMetadataFromRemote()
                callback.onPlaybackStatusChanged(state)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                state = PlaybackStateCompat.STATE_PAUSED
                setMetadataFromRemote()
                callback.onPlaybackStatusChanged(state)
            }
            else // case unknown
            -> Timber.d("State default : %d", status)
        }
    }

    private inner class CastMediaClientListener : RemoteMediaClient.Listener {

        override fun onMetadataUpdated() {
            Timber.d("RemoteMediaClient.onMetadataUpdated")
            setMetadataFromRemote()
        }

        override fun onStatusUpdated() {
            Timber.d("RemoteMediaClient.onStatusUpdated")
            updatePlaybackState()
        }

        override fun onSendingRemoteMediaRequest() {
            Timber.d("RemoteMediaClient.onSendingRemoteMediaRequest")
        }

        override fun onAdBreakStatusUpdated() {
            Timber.d("RemoteMediaClient.onAdBreakStatusUpdated")
        }

        override fun onQueueStatusUpdated() {
            Timber.d("RemoteMediaClient.onQueueStatusUpdated")
        }

        override fun onPreloadStatusUpdated() {
            Timber.d("RemoteMediaClient.onPreloadStatusUpdated")
        }
    }

    companion object {

        private val UPDATE_META_DATA_FROM_REMOTE = false

        private val ITEM_ID = "ITEM_ID"
        private val PLAYLIST_ID = "PLAYLIST_ID"
    }
}
