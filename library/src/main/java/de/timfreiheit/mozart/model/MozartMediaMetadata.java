package de.timfreiheit.mozart.model;

import android.support.v4.media.MediaMetadataCompat;

public class MozartMediaMetadata {

    public static final String META_DATA_CONTENT_URI = "de.timfreiheit.mozart.metadata.CONTENT_URI";

    public static final String META_DATA_CONTENT_TYPE = "de.timfreiheit.mozart.metadata.CONTENT_TYPE";

    public static final String META_DATA_PLAYLIST = "de.timfreiheit.mozart.metadata.META_DATA_PLAYLIST";

    /**
     * @see #META_DATA_CONTENT_URI
     */
    public static String getContentUri(MediaMetadataCompat mediaMetadata) {
        return mediaMetadata.getString(META_DATA_CONTENT_URI);
    }

    /**
     * @see #META_DATA_PLAYLIST
     */
    public static String getPlaylist(MediaMetadataCompat mediaMetadata) {
        return mediaMetadata.getString(META_DATA_PLAYLIST);
    }


    /**
     * @see #META_DATA_CONTENT_TYPE
     */
    public static String getContentType(MediaMetadataCompat mediaMetadata) {
        return mediaMetadata.getString(META_DATA_CONTENT_TYPE);
    }
}
