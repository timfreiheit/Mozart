package de.timfreiheit.mozart.model;

import android.support.v4.media.MediaMetadataCompat;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Single;

/**
 * caches results of
 * {@link #getMediaById(String)}
 * and {@link #getPlaylistById(String)}
 */
public class CachedMediaProvider extends MozartMediaProvider {

    private MozartMediaProvider source;
    private Map<String, MediaMetadataCompat> metadataCache = new HashMap<>();
    private Map<String, Playlist> playlistCache = new HashMap<>();

    public CachedMediaProvider(MozartMediaProvider source) {
        this.source = source;
    }

    @Override
    public Single<MediaMetadataCompat> getMediaById(final String mediaId) {
        return Single.defer(() -> {
            if (metadataCache.containsKey(mediaId)) {
                return Single.just(metadataCache.get(mediaId));
            }
            return source.getMediaById(mediaId);
        }).doOnSuccess(mediaMetadataCompat -> metadataCache.put(mediaId, mediaMetadataCompat));
    }

    @Override
    public Single<Playlist> getPlaylistById(String playlistId) {
        return Single.defer(() -> {
            if (playlistCache.containsKey(playlistId)) {
                return Single.just(playlistCache.get(playlistId));
            }
            return source.getPlaylistById(playlistId);
        }).doOnSuccess(playlist -> {
            for (MediaMetadataCompat mediaMetadata : playlist.getPlaylist()) {
                String mediaId = mediaMetadata.getDescription().getMediaId();
                metadataCache.put(mediaId, mediaMetadata);
            }
            playlistCache.put(playlistId, playlist);
        });
    }

    public void clearCache() {
        clearMediaCache();
        clearPlaylistCache();
    }

    public void clearMediaCache() {
        metadataCache.clear();
    }

    public void clearPlaylistCache() {
        playlistCache.clear();
    }

    public void removePlaylistFromCache(String playlistId) {
        playlistCache.remove(playlistId);
    }

    public void removeMediaFromCache(String mediaId) {
        metadataCache.remove(mediaId);
    }
}
