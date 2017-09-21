package de.timfreiheit.mozart.playback.cast;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;

import org.json.JSONObject;

import de.timfreiheit.mozart.model.MozartMediaMetadata;
import de.timfreiheit.mozart.model.MozartMetadataBuilder;

public class MediaInfoUtils {


    /**
     * Helper method to convert a {@link android.media.MediaMetadata} to a
     * {@link com.google.android.gms.cast.MediaInfo} used for sending media to the receiver app.
     *
     * @param track      {@link com.google.android.gms.cast.MediaMetadata}
     * @param customData custom data specifies the local mediaId used by the player.
     * @return mediaInfo {@link com.google.android.gms.cast.MediaInfo}
     */
    public static MediaInfo metaDataToMediaInfo(MediaMetadataCompat track,
                                                JSONObject customData) {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                track.getDescription().getTitle() == null ? "" :
                        track.getDescription().getTitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                track.getDescription().getSubtitle() == null ? "" :
                        track.getDescription().getSubtitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST));
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
        WebImage image = new WebImage(
                new Uri.Builder().encodedPath(
                        track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
                        .build());

        // First image is used by the receiver for showing the audio album art.
        mediaMetadata.addImage(image);
        // Second image is used by Cast Companion Library on the full screen activity that is shown
        // when the cast dialog is clicked.
        mediaMetadata.addImage(image);

        int streamType = MozartMediaMetadata.getStreamType(track);

        return new MediaInfo.Builder(MozartMediaMetadata.getContentUri(track))
                .setContentType(MozartMediaMetadata.getContentType(track))
                .setStreamType(streamType)
                .setMetadata(mediaMetadata)
                .setCustomData(customData)
                .build();
    }

    public static MediaMetadataCompat mediaInfoToMetadata(MediaInfo mediaInfo) {
        MozartMetadataBuilder builder = new MozartMetadataBuilder()
                .title(mediaInfo.getMetadata().getString(MediaMetadata.KEY_TITLE))
                .displaySubtitle(mediaInfo.getMetadata().getString(MediaMetadata.KEY_SUBTITLE))
                .artist(mediaInfo.getMetadata().getString(MediaMetadata.KEY_ALBUM_ARTIST))
                .album(mediaInfo.getMetadata().getString(MediaMetadata.KEY_ALBUM_TITLE));

        if (mediaInfo.getMetadata().getImages().size() > 0) {
            builder.albumArtUri(mediaInfo.getMetadata().getImages().get(0).getUrl().toString());
        }

        return builder.build();
    }


}
