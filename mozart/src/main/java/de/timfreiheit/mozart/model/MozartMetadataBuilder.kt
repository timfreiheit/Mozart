package de.timfreiheit.mozart.model

import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat

import com.google.android.gms.cast.MediaInfo

class MozartMetadataBuilder {

    private val mediaMetadata = MediaMetadataCompat.Builder()

    init {
        contentType("audio/mp3")
        streamType(MediaInfo.STREAM_TYPE_BUFFERED)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_MEDIA_ID
     */
    fun mediaId(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_ARTIST
     */
    fun artist(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_ARTIST, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_DURATION
     */
    fun duration(value: Long): MozartMetadataBuilder {
        return putLong(MediaMetadataCompat.METADATA_KEY_DURATION, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_GENRE
     */
    fun genre(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_GENRE, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_ALBUM
     */
    fun album(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_ALBUM, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI
     */
    fun albumArtUri(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_TITLE
     */
    fun title(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_TITLE, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE
     */
    fun displayTitle(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER
     */
    fun trackNumber(value: Long): MozartMetadataBuilder {
        return putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_NUM_TRACKS
     */
    fun totalTrackCount(value: Long): MozartMetadataBuilder {
        return putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_RATING
     */
    fun rating(value: RatingCompat): MozartMetadataBuilder {
        return putRating(MediaMetadataCompat.METADATA_KEY_RATING, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_USER_RATING
     */
    fun userRating(value: RatingCompat): MozartMetadataBuilder {
        return putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE
     */
    fun displaySubtitle(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION
     */
    fun displayDescription(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_AUTHOR
     */
    fun author(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_WRITER
     */
    fun writer(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_WRITER, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_COMPOSER
     */
    fun composer(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_COMPILATION
     */
    fun compilation(value: String): MozartMetadataBuilder {
        mediaMetadata.putString(MediaMetadataCompat.METADATA_KEY_COMPILATION, value)
        return this
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_DATE
     */
    fun date(value: String): MozartMetadataBuilder {
        return putString(MediaMetadataCompat.METADATA_KEY_DATE, value)
    }

    /**
     * @see MediaMetadataCompat.METADATA_KEY_YEAR
     */
    fun year(value: Long): MozartMetadataBuilder {
        return putLong(MediaMetadataCompat.METADATA_KEY_YEAR, value)
    }

    fun contentUri(value: String): MozartMetadataBuilder {
        return putString(META_DATA_CONTENT_URI, value)
    }

    /**
     * @see MediaInfo.STREAM_TYPE_BUFFERED
     *
     * @see MediaInfo.STREAM_TYPE_LIVE
     *
     * @see MediaInfo.STREAM_TYPE_NONE
     *
     * @see MediaInfo.STREAM_TYPE_INVALID
     */
    fun streamType(value: Int): MozartMetadataBuilder {
        return putLong(META_DATA_STREAM_TYPE, value.toLong())
    }

    /**
     * for example: audio/mp3
     */
    fun contentType(value: String): MozartMetadataBuilder {
        mediaMetadata.putString(META_DATA_CONTENT_TYPE, value)
        return this
    }

    fun putString(key: String, value: String): MozartMetadataBuilder {
        mediaMetadata.putString(key, value)
        return this
    }

    fun putLong(key: String, value: Long): MozartMetadataBuilder {
        mediaMetadata.putLong(key, value)
        return this
    }

    fun putBitmap(key: String, value: Bitmap): MozartMetadataBuilder {
        mediaMetadata.putBitmap(key, value)
        return this
    }

    fun putRating(key: String, value: RatingCompat): MozartMetadataBuilder {
        mediaMetadata.putRating(key, value)
        return this
    }

    fun build(): MediaMetadataCompat {
        return mediaMetadata.build()
    }

}