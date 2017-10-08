package de.timfreiheit.mozart.model

import android.support.v4.media.MediaMetadataCompat
import android.text.TextUtils

data class Playlist(
        val id: String?,
        val title: String?,
        val playlist: List<MediaMetadataCompat>
) {

    /**
     * @return -1 when media not found
     */
    fun getPositionByMediaId(mediaId: String): Int {
        val playlist1 = playlist
        for (i in playlist1.indices) {
            val mediaMetadataCompat = playlist1[i]
            val id = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            if (TextUtils.equals(id, mediaId)) {
                return i
            }
        }
        return -1
    }
}
