package de.timfreiheit.mozart.model;

import android.support.v4.media.session.PlaybackStateCompat;

import de.timfreiheit.mozart.playback.Playback;

public class MozartPlaybackState {

    public static final String STATE_DURATION = "de.timfreiheit.mozart.state.STATE_DURATION";

    /**
     * @see #STATE_DURATION
     * @see Playback#getStreamDuration()
     */
    public static long getStreamDuration(PlaybackStateCompat playbackState) {
        if (playbackState.getExtras() == null) {
            return -1;
        }
        return playbackState.getExtras().getLong(STATE_DURATION, -1);
    }

}
