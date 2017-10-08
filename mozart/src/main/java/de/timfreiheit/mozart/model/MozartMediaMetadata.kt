package de.timfreiheit.mozart.model

import android.support.v4.media.MediaMetadataCompat

internal const val META_DATA_CONTENT_URI = "de.timfreiheit.mozart.metadata.CONTENT_URI"
internal const val META_DATA_STREAM_TYPE = "de.timfreiheit.mozart.metadata.STREAM_TYPE"
internal const val META_DATA_CONTENT_TYPE = "de.timfreiheit.mozart.metadata.CONTENT_TYPE"
internal const val META_DATA_PLAYLIST = "de.timfreiheit.mozart.metadata.META_DATA_PLAYLIST"

/**
 * @see .META_DATA_CONTENT_URI
 */
fun MediaMetadataCompat.getContentUri(): String? = getString(META_DATA_CONTENT_URI)

/**
 * @see .META_DATA_PLAYLIST
 */
fun MediaMetadataCompat.getPlaylist(): String? = getString(META_DATA_PLAYLIST)

/**
 * @see .META_DATA_CONTENT_TYPE
 */
fun MediaMetadataCompat.getContentType(): String? = getString(META_DATA_CONTENT_TYPE)

/**
 * @see .META_DATA_CONTENT_TYPE
 */
fun MediaMetadataCompat.getStreamType(): Int = getLong(META_DATA_STREAM_TYPE).toInt()
