package de.timfreiheit.mozart.playback;

import android.support.v4.media.session.PlaybackStateCompat;

public class PlaybackCallbackDegelate implements Playback.Callback {

    private Playback.Callback delegate;

    public PlaybackCallbackDegelate(Playback.Callback callback) {
        setDelegate(callback);
    }

    public void setDelegate(Playback.Callback callback) {
        this.delegate = callback;
    }

    @Override
    public void onCompletion() {
        if (delegate != null) {
            delegate.onCompletion();
        }
    }

    @Override
    public void onPlaybackStatusChanged(@PlaybackStateCompat.State int state) {
        if (delegate != null) {
            delegate.onPlaybackStatusChanged(state);
        }
    }

    @Override
    public void onError(String error) {
        if (delegate != null) {
            delegate.onError(error);
        }
    }

}
