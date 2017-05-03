package de.timfreiheit.mozart.model;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import timber.log.Timber;

public abstract class MozartMediaProvider {

    public abstract Single<MediaMetadataCompat> getMediaById(String mediaId);

    public abstract Single<Playlist> getPlaylistById(String playlistId);

    /**
     * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
     */
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
        return null;
    }

    /**
     * @see MediaBrowserServiceCompat#onLoadChildren(String, MediaBrowserServiceCompat.Result)
     */
    public void onLoadChildren(@NonNull final String parentMediaId,
                               @NonNull final MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        Timber.d("OnLoadChildren: parentMediaId= %s", parentMediaId);
        result.sendResult(new ArrayList<>());
    }

}
