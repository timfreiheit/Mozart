/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.timfreiheit.mozart.playback;

import android.support.annotation.CallSuper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import de.timfreiheit.mozart.MozartMusicService;

/**
 * Interface representing either Local or Remote Playback. The {@link MozartMusicService} works
 * directly with an instance of the Playback object to make the various calls such as
 * play, pause etc.
 */
public abstract class Playback {

    private Callback callback;

    private int currentState = PlaybackStateCompat.STATE_NONE;
    private MediaMetadataCompat currentMedia;

    /**
     * Start/setup the playback.
     * Resources/listeners would be allocated by implementations.
     */
    @CallSuper
    public void onStart() {

    }

    /**
     * Stop the playback. All resources can be de-allocated by implementations here.
     *
     * @param notifyListeners if true and a callback has been set by setCallback,
     *                        callback.onPlaybackStatusChanged will be called after changing
     *                        the state.
     */
    @CallSuper
    public void onStop(boolean notifyListeners) {
        if (notifyListeners) {
            getCallback().onPlaybackStatusChanged(getState());
        }
    }

    /**
     * Set the latest playback state as determined by the caller.
     */
    public void setState(@PlaybackStateCompat.State int state) {
        this.currentState = state;
    }

    /**
     * Get the current {@link android.media.session.PlaybackState#getState()}
     */
    @PlaybackStateCompat.State
    public int getState() {
        return currentState;
    }

    /**
     * Set the current mediaId. This is only used when switching from one
     * playback to another.
     *
     * @param metadata to be set as the current.
     */
    public void setCurrentMedia(MediaMetadataCompat metadata) {
        this.currentMedia = metadata;
    }

    /**
     * @return the current media being processed in any state or null.
     */
    public MediaMetadataCompat getCurrentMedia() {
        return currentMedia;
    }

    /**
     * @return boolean that indicates that this is ready to be used.
     */
    public abstract boolean isConnected();

    /**
     * @return boolean indicating whether the player is playing or is supposed to be
     * playing when we gain audio focus.
     */
    public abstract boolean isPlaying();

    /**
     * @return pos if currently playing an item
     */
    public abstract int getCurrentStreamPosition();

    /**
     * @return duration of current item when available; -1 if unknown
     */
    public abstract int getStreamDuration();

    /**
     * Set the current position. Typically used when switching players that are in
     * paused state.
     *
     * @param pos position in the stream
     */
    public abstract void setCurrentStreamPosition(int pos);

    /**
     * Query the underlying stream and update the internal last known stream position.
     */
    public abstract void updateLastKnownStreamPosition();

    /**
     * @param item to play
     */
    public abstract void play(MediaMetadataCompat item);

    /**
     * Pause the current playing item
     */
    public abstract void pause();

    /**
     * Seek to the given position
     */
    public abstract void seekTo(int position);

    public interface Callback {
        /**
         * On current music completed.
         */
        void onCompletion();

        /**
         * on Playback status changed
         * Implementations can use this callback to update
         * playback state on the media sessions.
         */
        void onPlaybackStatusChanged(@PlaybackStateCompat.State int state);

        /**
         * @param error to be added to the PlaybackState
         */
        void onError(String error);
    }

    private static final Callback emptyCallback = new Callback() {

        @Override
        public void onCompletion() {
        }

        @Override
        public void onPlaybackStatusChanged(@PlaybackStateCompat.State int state) {
        }

        @Override
        public void onError(String error) {
        }

    };

    /**
     * @param callback to be called
     */
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @NonNull
    public Callback getCallback() {
        if (callback == null) {
            return emptyCallback;
        }
        return callback;
    }
}
