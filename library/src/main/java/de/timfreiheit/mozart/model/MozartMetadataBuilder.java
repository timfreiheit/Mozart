package de.timfreiheit.mozart.model;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;

import com.google.android.gms.cast.MediaInfo;

public class MozartMetadataBuilder {

    private MediaMetadataCompat.Builder mediaMetadata = new MediaMetadataCompat.Builder();

    public MozartMetadataBuilder() {
        contentType("audio/mp3");
        streamType(MediaInfo.STREAM_TYPE_BUFFERED);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_MEDIA_ID
     */
    public MozartMetadataBuilder mediaId(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_ARTIST
     */
    public MozartMetadataBuilder artist(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_ARTIST, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_DURATION
     */
    public MozartMetadataBuilder duration(long value) {
        return putLong(MediaMetadataCompat.METADATA_KEY_DURATION, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_GENRE
     */
    public MozartMetadataBuilder genre(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_GENRE, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_ALBUM
     */
    public MozartMetadataBuilder album(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_ALBUM, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_ALBUM_ART_URI
     */
    public MozartMetadataBuilder albumArtUri(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_TITLE
     */
    public MozartMetadataBuilder title(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_TITLE, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_DISPLAY_TITLE
     */
    public MozartMetadataBuilder displayTitle(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_TRACK_NUMBER
     */
    public MozartMetadataBuilder trackNumber(long value) {
        return putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_NUM_TRACKS
     */
    public MozartMetadataBuilder totalTrackCount(long value) {
        return putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_RATING
     */
    public MozartMetadataBuilder rating(RatingCompat value) {
        return putRating(MediaMetadataCompat.METADATA_KEY_RATING, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_USER_RATING
     */
    public MozartMetadataBuilder userRating(RatingCompat value) {
        return putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_DISPLAY_SUBTITLE
     */
    public MozartMetadataBuilder displaySubtitle(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_DISPLAY_DESCRIPTION
     */
    public MozartMetadataBuilder displayDescription(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_AUTHOR
     */
    public MozartMetadataBuilder author(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_WRITER
     */
    public MozartMetadataBuilder writer(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_WRITER, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_COMPOSER
     */
    public MozartMetadataBuilder composer(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_COMPILATION
     */
    public MozartMetadataBuilder compilation(String value) {
        mediaMetadata.putString(MediaMetadataCompat.METADATA_KEY_COMPILATION, value);
        return this;
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_DATE
     */
    public MozartMetadataBuilder date(String value) {
        return putString(MediaMetadataCompat.METADATA_KEY_DATE, value);
    }

    /**
     * @see MediaMetadataCompat#METADATA_KEY_YEAR
     */
    public MozartMetadataBuilder year(long value) {
        return putLong(MediaMetadataCompat.METADATA_KEY_YEAR, value);
    }

    public MozartMetadataBuilder contentUri(String value) {
        return putString(MozartMediaMetadata.META_DATA_CONTENT_URI, value);
    }

    /**
     * @see MediaInfo#STREAM_TYPE_BUFFERED
     * @see MediaInfo#STREAM_TYPE_LIVE
     * @see MediaInfo#STREAM_TYPE_NONE
     * @see MediaInfo#STREAM_TYPE_INVALID
     */
    public MozartMetadataBuilder streamType(int value) {
        return putLong(MozartMediaMetadata.META_DATA_STREAM_TYPE, value);
    }

    /**
     * for example: audio/mp3
     */
    public MozartMetadataBuilder contentType(String value) {
        mediaMetadata.putString(MozartMediaMetadata.META_DATA_CONTENT_TYPE, value);
        return this;
    }

    public MozartMetadataBuilder putString(String key, String value) {
        mediaMetadata.putString(key, value);
        return this;
    }

    public MozartMetadataBuilder putLong(String key, long value) {
        mediaMetadata.putLong(key, value);
        return this;
    }

    public MozartMetadataBuilder putBitmap(String key, Bitmap value) {
        mediaMetadata.putBitmap(key, value);
        return this;
    }

    public MozartMetadataBuilder putRating(String key, RatingCompat value) {
        mediaMetadata.putRating(key, value);
        return this;
    }

    public MediaMetadataCompat build() {
        return mediaMetadata.build();
    }

}