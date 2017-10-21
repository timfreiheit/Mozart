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
package de.timfreiheit.mozart.playback

import android.support.annotation.CallSuper
import android.support.annotation.IntRange
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telecom.Call

import de.timfreiheit.mozart.MozartMusicService

/**
 * Interface representing either Local or Remote Playback. The [MozartMusicService] works
 * directly with an instance of the Playback object to make the various calls such as
 * play, pause etc.
 */
abstract class Playback {


    private val callbacks = Callbacks()
    /**
     * @param callback to be called
     */
    val callback: Callback = callbacks

    fun addCallback(callback: Callback) {
        callbacks.callbacks.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbacks.callbacks.remove(callback)
    }

    /**
     * Get the current [android.media.session.PlaybackState.getState]
     */
    /**
     * Set the latest playback state as determined by the caller.
     */
    @get:PlaybackStateCompat.State
    open var state = PlaybackStateCompat.STATE_NONE
    /**
     * @return the current media being processed in any state or null.
     */
    /**
     * Set the current mediaId. This is only used when switching from one
     * playback to another.
     *
     * @param metadata to be set as the current.
     */
    var currentMedia: MediaMetadataCompat? = null

    /**
     * @return boolean that indicates that this is ready to be used.
     */
    abstract val isConnected: Boolean

    /**
     * @return boolean indicating whether the player is playing or is supposed to be
     * playing when we gain audio focus.
     */
    abstract val isPlaying: Boolean

    /**
     * @return pos if currently playing an item
     */
    /**
     * Set the current position. Typically used when switching players that are in
     * paused state.
     *
     * @param pos position in the stream
     */
    abstract var currentStreamPosition: Long

    /**
     * @return duration of current item when available; -1 if unknown
     */
    abstract val streamDuration: Long

    /**
     * Start/setup the playback.
     * Resources/listeners would be allocated by implementations.
     */
    @CallSuper
    open fun onStart() {

    }

    /**
     * Stop the playback. All resources can be de-allocated by implementations here.
     *
     * @param notifyListeners if true and a callback has been set by setCallback,
     * callback.onPlaybackStatusChanged will be called after changing
     * the state.
     */
    @CallSuper
    open fun onStop(notifyListeners: Boolean) {
        if (notifyListeners) {
            callback.onPlaybackStatusChanged(state)
        }
    }

    /**
     * Query the underlying stream and update the internal last known stream position.
     */
    abstract fun updateLastKnownStreamPosition()

    /**
     * @param item to play
     */
    abstract fun play(item: MediaMetadataCompat)

    /**
     * Pause the current playing item
     */
    abstract fun pause()

    /**
     * Seek to the given position
     */
    abstract fun seekTo(position: Long)

    private class Callbacks: Callback {

        var callbacks: MutableSet<Callback> = mutableSetOf()

        override fun onCompletion() {
            callbacks.forEach { it.onCompletion() }
        }

        override fun onPlaybackStatusChanged(state: Int) {
            callbacks.forEach { it.onPlaybackStatusChanged(state) }
        }

        override fun onError(error: String?) {
            callbacks.forEach { it.onError(error) }
        }

    }

    abstract class SimpleCallback: Callback {
        override fun onCompletion() {

        }

        override fun onPlaybackStatusChanged(state: Int) {

        }

        override fun onError(error: String?) {
        }
    }

    interface Callback {
        /**
         * On current music completed.
         */
        fun onCompletion()

        /**
         * on Playback status changed
         * Implementations can use this callback to update
         * playback state on the media sessions.
         */
        fun onPlaybackStatusChanged(@PlaybackStateCompat.State state: Int)

        /**
         * @param error to be added to the PlaybackState
         */
        fun onError(error: String?)
    }

}
