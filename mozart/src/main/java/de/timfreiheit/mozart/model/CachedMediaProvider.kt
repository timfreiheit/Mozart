package de.timfreiheit.mozart.model

import android.support.v4.media.MediaMetadataCompat
import io.reactivex.Single
import java.util.*

/**
 * caches results of
 * [.getMediaById]
 * and [.getPlaylistById]
 */
class CachedMediaProvider(private val source: MozartMediaProvider) : MozartMediaProvider() {
    private val metadataCache = HashMap<String, MediaMetadataCompat>()
    private val playlistCache = HashMap<String, Playlist>()

    override fun getMediaById(mediaId: String): Single<MediaMetadataCompat> {
        return Single.defer {
            if (metadataCache.containsKey(mediaId)) {
                return@defer Single.just<MediaMetadataCompat>(metadataCache[mediaId])
            }
            source.getMediaById(mediaId)
        }.doOnSuccess { mediaMetadataCompat -> metadataCache.put(mediaId, mediaMetadataCompat) }
    }

    override fun getPlaylistById(playlistId: String): Single<Playlist> {
        return Single.defer {
            if (playlistCache.containsKey(playlistId)) {
                return@defer Single.just<Playlist>(playlistCache[playlistId])
            }
            source.getPlaylistById(playlistId)
        }.doOnSuccess { playlist ->
            for (mediaMetadata in playlist.playlist) {
                val mediaId = mediaMetadata.description.mediaId
                if (mediaId != null) {
                    metadataCache.put(mediaId, mediaMetadata)
                }
            }
            playlistCache.put(playlistId, playlist)
        }
    }

    fun clearCache() {
        clearMediaCache()
        clearPlaylistCache()
    }

    fun clearMediaCache() {
        metadataCache.clear()
    }

    fun clearPlaylistCache() {
        playlistCache.clear()
    }

    fun removePlaylistFromCache(playlistId: String) {
        playlistCache.remove(playlistId)
    }

    fun removeMediaFromCache(mediaId: String) {
        metadataCache.remove(mediaId)
    }
}
