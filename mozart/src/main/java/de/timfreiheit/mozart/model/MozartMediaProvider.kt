package de.timfreiheit.mozart.model

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat

import java.util.ArrayList

import io.reactivex.Single
import timber.log.Timber

abstract class MozartMediaProvider {

    abstract fun getMediaById(mediaId: String): Single<MediaMetadataCompat>

    abstract fun getPlaylistById(playlistId: String): Single<Playlist>

    /**
     * @see MediaBrowserServiceCompat.onGetRoot
     */
    open fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        return null
    }

    /**
     * @see MediaBrowserServiceCompat.onLoadChildren
     */
    open fun onLoadChildren(parentMediaId: String,
                       result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        Timber.d("OnLoadChildren: parentMediaId= %s", parentMediaId)
        result.sendResult(arrayListOf())
    }

}
