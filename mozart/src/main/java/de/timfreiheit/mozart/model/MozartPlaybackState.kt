package de.timfreiheit.mozart.model

import android.support.v4.media.session.PlaybackStateCompat

import de.timfreiheit.mozart.playback.Playback

internal val STATE_DURATION = "de.timfreiheit.mozart.state.STATE_DURATION"

/**
 * @see .STATE_DURATION
 *
 * @see Playback.getStreamDuration
 */
fun PlaybackStateCompat.getStreamDuration(): Long {
    return extras?.getLong(STATE_DURATION, -1) ?: -1
}

