package de.timfreiheit.mozart.playback.cast

import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import de.timfreiheit.mozart.model.MozartMetadataBuilder
import de.timfreiheit.mozart.model.getContentType
import de.timfreiheit.mozart.model.getContentUri
import de.timfreiheit.mozart.model.getStreamType
import org.json.JSONObject

/**
 * Helper method to convert a [android.media.MediaMetadata] to a
 * [com.google.android.gms.cast.MediaInfo] used for sending media to the receiver app.
 *
 * @param track      [com.google.android.gms.cast.MediaMetadata]
 * @param customData custom data specifies the local mediaId used by the player.
 * @return mediaInfo [com.google.android.gms.cast.MediaInfo]
 */
internal fun MediaMetadataCompat.toMediaInfo(customData: JSONObject): MediaInfo {
    val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
    mediaMetadata.putString(MediaMetadata.KEY_TITLE, description?.title?.toString() ?: "")
    mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, description?.subtitle?.toString() ?: "")
    mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
            getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST))
    mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
            getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
    val image = WebImage(
            Uri.Builder().encodedPath(
                    getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
                    .build())

    // First image is used by the receiver for showing the audio album art.
    mediaMetadata.addImage(image)
    // Second image is used by Cast Companion Library on the full screen activity that is shown
    // when the cast dialog is clicked.
    mediaMetadata.addImage(image)

    val streamType = getStreamType()

    return MediaInfo.Builder(getContentUri())
            .setContentType(getContentType())
            .setStreamType(streamType)
            .setMetadata(mediaMetadata)
            .setCustomData(customData)
            .build()
}

internal fun MediaInfo.toMetadata(): MediaMetadataCompat {
    val builder = MozartMetadataBuilder()
            .title(metadata.getString(MediaMetadata.KEY_TITLE))
            .displaySubtitle(metadata.getString(MediaMetadata.KEY_SUBTITLE))
            .artist(metadata.getString(MediaMetadata.KEY_ALBUM_ARTIST))
            .album(metadata.getString(MediaMetadata.KEY_ALBUM_TITLE))

    if (metadata.images.size > 0) {
        builder.albumArtUri(metadata.images[0].url.toString())
    }

    return builder.build()
}

