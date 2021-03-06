
package de.timfreiheit.mozart.playback

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.model.META_DATA_PLAYLIST
import de.timfreiheit.mozart.model.Playlist
import de.timfreiheit.mozart.model.image.CoverImage
import de.timfreiheit.mozart.utils.createMediaQueue
import de.timfreiheit.mozart.utils.getMusicIndex
import de.timfreiheit.mozart.utils.isIndexPlayable
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

/**
 * Simple data provider for queues. Keeps track of a current queue and a current index in the
 * queue. Also provides methods to set the current queue based on common queries, relying on a
 * given MusicProvider to provide the actual media metadata.
 */
open class QueueManager(
        private val mozartMusicService: MozartMusicService
) {

    private val listeners = mutableSetOf<MetadataUpdateListener>()

    // "Now playing" queue:
    var playlist: Playlist = Playlist(null, null, emptyList())
        protected set

    var playingQueue: List<MediaSessionCompat.QueueItem> = listOf()
        protected set

    open var currentIndex: Int = 0
        protected set

    open val isInRepeatMode = false

    private val newMediaCompositeDisposable = CompositeDisposable()

    val currentMusic: MediaSessionCompat.QueueItem?
        get() = if (!playingQueue.isIndexPlayable(currentIndex)) {
            null
        } else playingQueue[currentIndex]

    val currentQueueSize: Int
        get() = playingQueue.size

    val playlistId: String?
        get() = playlist.id

    private fun setCurrentQueueIndex(index: Int) {
        if (index >= 0 && index < playingQueue.size) {
            currentIndex = index
            listeners.forEach { it.onCurrentQueueIndexUpdated(currentIndex) }
        }
    }

    fun addListener(listener: MetadataUpdateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MetadataUpdateListener) {
        listeners.remove(listener)
    }

    fun setCurrentQueueItem(queueId: Long): Boolean {
        // set the current index on queue from the queue Id:
        val index = playingQueue.getMusicIndex(queueId)
        setCurrentQueueIndex(index)
        return index >= 0
    }

    fun setCurrentQueueItem(mediaId: String): Boolean {
        // set the current index on queue from the music Id:
        val index = playingQueue.getMusicIndex(mediaId)
        setCurrentQueueIndex(index)
        return index >= 0
    }

    fun skipQueuePosition(amount: Int): Boolean {
        var index = currentIndex + amount
        if (index < 0) {
            // skip backwards before the first song will keep you on the first song
            index = 0
        } else {
            if (isInRepeatMode) {
                // skip forwards when in last song will cycle back to start of the queue
                index %= playingQueue.size
            }
        }
        if (!playingQueue.isIndexPlayable(index)) {
            Timber.e("Cannot increment queue index by %d. Current=%d queue length=%d", amount, currentIndex, playingQueue!!.size)
            return false
        }
        currentIndex = index
        return true
    }

    fun setQueueByMediaId(mediaId: String): Completable {
        return mozartMusicService.mediaProvider.getMediaById(mediaId)
                .flatMapCompletable { mediaMetadataCompat -> setQueueFromPlaylist(Playlist(null, null, listOf(mediaMetadataCompat)), 0) }
                .doOnComplete { this.updateMetadata() }
    }

    fun updateQueueByMediaId(mediaId: String): Completable {
        return Completable.defer {
            val index = playlist.getPositionByMediaId(mediaId)
            if (index >= 0) {
                currentIndex = index
                updateMetadata()
                return@defer Completable.complete()
            }
            setQueueByMediaId(mediaId)
        }
    }

    fun setQueueByPlaylistId(playlistId: String, initialMedia: String?): Completable {
        return mozartMusicService.mediaProvider.getPlaylistById(playlistId)
                .flatMapCompletable { playlist1 ->
                    val index = playlist1.getPositionByMediaId(initialMedia)
                    setQueueFromPlaylist(playlist1, index)
                }
    }

    fun setQueueByPlaylistId(playlistId: String, initialPosition: Int): Completable {
        return mozartMusicService.mediaProvider.getPlaylistById(playlistId)
                .flatMapCompletable { playlist1 -> setQueueFromPlaylist(playlist1, initialPosition) }
    }

    fun setQueueFromPlaylist(playlist: Playlist, initialPosition: Int): Completable {
        return Completable.fromAction {
            this.playlist = playlist
            setCurrentQueue(playlist.title, playlist.createMediaQueue(), initialPosition)
        }.doOnComplete { this.updateMetadata() }
    }

    private fun setCurrentQueue(title: String?, newQueue: List<MediaSessionCompat.QueueItem>,
                                initialPosition: Int = 0) {
        playingQueue = newQueue
        currentIndex = Math.max(initialPosition, 0)

        listeners.forEach { it.onQueueUpdated(title, newQueue) }
    }

    fun updateMetadata() {
        val currentMusic = currentMusic
        if (currentMusic == null) {
            listeners.forEach { it.onMetadataRetrieveError() }
            return
        }

        newMediaCompositeDisposable.clear()
        newMediaCompositeDisposable.add(Single.defer {
            for (track in playlist.playlist) {
                if (currentMusic.description.mediaId == track.description.mediaId) {
                    return@defer Single.just<MediaMetadataCompat>(track)
                }
            }
            currentMusic.description.mediaId?.let {
                mozartMusicService.mediaProvider.getMediaById(it)
            }
        }.subscribeOn(Schedulers.io())
                .subscribe({ metadata ->

                    listeners.forEach {
                        it.onMetadataChanged(fetchMediaImages(MediaMetadataCompat.Builder(metadata)
                                .putString(META_DATA_PLAYLIST, playlist.id)
                                .build()))
                    }

                }) { throw IllegalArgumentException("Invalid musicId " + currentMusic.description.mediaId!!) })
    }

    private fun fetchMediaImages(metadata: MediaMetadataCompat): MediaMetadataCompat {

        val iconUri = metadata.description.iconUri ?: return metadata

        val cachedCoverImage = mozartMusicService.imageLoaderCache.getCachedBitmapFromMemory(iconUri.toString())
        if (cachedCoverImage != null) {
            return fillWithCoverImage(metadata, cachedCoverImage)
        }

        newMediaCompositeDisposable.add(mozartMusicService.imageLoaderCache.loadCover(iconUri.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ coverImage ->

                    val mediaId = metadata.description.mediaId
                    if (mediaId != null && mediaId != currentMusic?.description?.mediaId) {
                        return@subscribe
                    }

                    val newMetadata = fillWithCoverImage(metadata, coverImage)

                    listeners.forEach { it.onMetadataChanged(newMetadata) }
                }) { throwable -> Timber.w(throwable, "loading cover failed") })
        return metadata
    }

    private fun fillWithCoverImage(metadata: MediaMetadataCompat, coverImage: CoverImage?): MediaMetadataCompat {
        if (coverImage == null) {
            return metadata
        }
        val newMetadata = MediaMetadataCompat.Builder(metadata)

        if (coverImage.largeImage != null) {
            newMetadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverImage.largeImage)
        }

        if (coverImage.icon != null) {
            newMetadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverImage.icon)
        }

        return newMetadata.build()
    }

    interface MetadataUpdateListener {
        fun onMetadataChanged(metadata: MediaMetadataCompat)

        fun onMetadataRetrieveError()

        fun onCurrentQueueIndexUpdated(queueIndex: Int)

        fun onQueueUpdated(title: String?, newQueue: List<MediaSessionCompat.QueueItem>)
    }
}
