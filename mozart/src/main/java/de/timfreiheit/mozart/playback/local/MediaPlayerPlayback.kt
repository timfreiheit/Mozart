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
package de.timfreiheit.mozart.playback.local

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.AudioAttributesCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import de.timfreiheit.mozart.model.getContentUri
import timber.log.Timber
import java.io.IOException

/**
 * A class that implements local media playback using [MediaPlayer]
 */
open class MediaPlayerPlayback(context: Context) : LocalPlayback(context), OnCompletionListener, OnErrorListener, OnPreparedListener, OnSeekCompleteListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnInfoListener {

    private val wifiLock: WifiManager.WifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "uAmp_lock")

    private var isPrepared = false
    private var playOnFocusGain: Boolean = false
    @Volatile private var currentPosition: Long = 0
    @Volatile private var duration: Long = -1

    private var mediaPlayer: MediaPlayer? = null

    override fun onStop(notifyListeners: Boolean) {
        super.onStop(notifyListeners)
        state = PlaybackStateCompat.STATE_STOPPED
        if (notifyListeners) {
            callback.onPlaybackStatusChanged(state)
        }
        currentPosition = currentStreamPosition
        // Relax all resources
        relaxResources(true)
    }

    override fun isConnected() = true

    override fun isPlaying() = playOnFocusGain || mediaPlayer?.isPlaying == true

    override fun getCurrentStreamPosition(): Long {
        return if (mediaPlayer != null && isPrepared) {
            mediaPlayer?.currentPosition?.toLong() ?: currentPosition
        } else {
            currentPosition
        }
    }

    override fun getStreamDuration(): Long {
        return if (mediaPlayer != null && isPrepared) {
            mediaPlayer?.duration?.toLong() ?: duration
        } else {
            duration
        }
    }

    override fun updateLastKnownStreamPosition() {
        mediaPlayer?.let { mediaPlayer ->
            currentPosition = mediaPlayer.currentPosition.toLong()
            duration = mediaPlayer.duration.toLong()
        }
    }

    override fun play(item: MediaMetadataCompat) {
        super.play(item)
        playOnFocusGain = true
        val mediaId = item.description.mediaId
        val mediaHasChanged = currentMedia == null || !TextUtils.equals(mediaId, currentMedia.description.mediaId)
        if (mediaHasChanged) {
            currentPosition = 0
            currentMedia = item
        }

        if (state == PlaybackStateCompat.STATE_PAUSED && !mediaHasChanged && mediaPlayer != null) {
            configMediaPlayerState()
        } else {
            state = PlaybackStateCompat.STATE_STOPPED
            relaxResources(false) // release everything except MediaPlayer


            var source = item.getContentUri()
            if (source != null) {
                source = source.replace(" ".toRegex(), "%20") // Escape spaces for URLs
            }

            try {
                createMediaPlayerIfNeeded()

                state = PlaybackStateCompat.STATE_BUFFERING


                updateAudioAttributes(item)
                mediaPlayer?.setDataSource(source)

                // Starts preparing the media player in the background. When
                // it's done, it will call our OnPreparedListener (that is,
                // the onPrepared() method on this class, since we set the
                // listener to 'this'). Until the media player is prepared,
                // we *cannot* call start() on it!
                mediaPlayer?.prepareAsync()

                // If we are streaming from the internet, we want to hold a
                // Wifi lock, which prevents the Wifi radio from going to
                // sleep while the song is playing.

                wifiLock.acquire()

                callback.onPlaybackStatusChanged(state)

            } catch (ex: IOException) {
                Timber.e(ex, "Exception playing song")
                callback.onError(ex.message)
            }

        }
    }

    /**
     * @see MediaPlayer.setAudioAttributes
     * @see MediaPlayer.setAudioStreamType
     *
     * by default is uses [AudioAttributesCompat.CONTENT_TYPE_MUSIC] with [AudioAttributesCompat.USAGE_MEDIA]
     */
    open fun updateAudioAttributes(item: MediaMetadataCompat) {
        if (Build.VERSION.SDK_INT >= 21) {
            val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .build()
            mediaPlayer?.setAudioAttributes(audioAttributes)
        } else {
            mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
    }

    override fun pause() {
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            // Pause media player and cancel the 'foreground service' state.

            mediaPlayer?.let { mediaPlayer ->
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    currentPosition = mediaPlayer.currentPosition.toLong()
                }
            }
            // while paused, retain the MediaPlayer but give up audio focus
            relaxResources(false)
        }
        state = PlaybackStateCompat.STATE_PAUSED
        callback.onPlaybackStatusChanged(state)
    }

    override fun seekTo(position: Long) {
        Timber.d("seekTo called with %s", position)

        currentPosition = position
        mediaPlayer?.let { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                state = PlaybackStateCompat.STATE_BUFFERING
            }
            mediaPlayer.seekTo(position.toInt())
            callback.onPlaybackStatusChanged(state)
        }
    }

    override fun setCurrentStreamPosition(pos: Long) {
        this.currentPosition = pos
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and
     * starts/restarts it. This method starts/restarts the MediaPlayer
     * respecting the current audio focus state. So if we have focus, it will
     * play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is
     * allowed by the current focus settings. This method assumes mPlayer !=
     * null, so if you are calling it, you have to do so from a context where
     * you are sure this is the case.
     */
    private fun configMediaPlayerState() {
        Timber.d("configMediaPlayerState. mAudioFocus= %d", audioFocusStatus)
        if (audioFocusStatus == AUDIO_NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck, we have to pause,
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                pause()
            }
        } else {  // we have audio focus:
            if (audioFocusStatus == AUDIO_NO_FOCUS_CAN_DUCK) {
                mediaPlayer?.setVolume(VOLUME_DUCK, VOLUME_DUCK) // we'll be relatively quiet
            } else {
                mediaPlayer?.setVolume(VOLUME_NORMAL, VOLUME_NORMAL) // we can be loud again
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (playOnFocusGain) {
                mediaPlayer?.let { mediaPlayer ->
                    if (!mediaPlayer.isPlaying) {
                        Timber.d("configMediaPlayerState startMediaPlayer. seeking to %d",
                                currentPosition)
                        state = if (currentPosition == mediaPlayer.currentPosition.toLong()) {
                            mediaPlayer.start()
                            PlaybackStateCompat.STATE_PLAYING
                        } else {
                            mediaPlayer.seekTo(currentPosition.toInt())
                            PlaybackStateCompat.STATE_BUFFERING
                        }
                    }
                }
                playOnFocusGain = false
            }
        }
        callback.onPlaybackStatusChanged(state)
    }

    /**
     * Called by AudioManager on audio focus changes.
     * Implementation of [AudioManager.OnAudioFocusChangeListener]
     */
    override fun onAudioFocusChange(focusChange: Int) {
        super.onAudioFocusChange(focusChange)
        Timber.d("onAudioFocusChange. focusChange= %d", focusChange)

        if (state == PlaybackStateCompat.STATE_PLAYING && state == AUDIO_NO_FOCUS_CAN_DUCK) {
            // If we don't have audio focus and can't duck, we save the information that
            // we were playing, so that we can resume playback once we get the focus back.
            playOnFocusGain = true
        }
        configMediaPlayerState()
    }

    /**
     * Called when MediaPlayer has completed a seek
     *
     * @see OnSeekCompleteListener
     */
    override fun onSeekComplete(mp: MediaPlayer) {
        Timber.d("onSeekComplete from MediaPlayer: %d", mp.currentPosition)
        currentPosition = mp.currentPosition.toLong()
        if (state == PlaybackStateCompat.STATE_BUFFERING) {
            mediaPlayer?.start()
            state = PlaybackStateCompat.STATE_PLAYING
        }
        callback.onPlaybackStatusChanged(state)
    }

    /**
     * Called when media player is done playing current song.
     *
     * @see OnCompletionListener
     */
    override fun onCompletion(player: MediaPlayer) {
        duration = 0
        isPrepared = false
        currentPosition = 0
        currentMedia = null
        state = PlaybackStateCompat.STATE_NONE
        Timber.d("onCompletion from MediaPlayer")
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        callback.onCompletion()
    }

    /**
     * Called when media player is done preparing.
     *
     * @see OnPreparedListener
     */
    override fun onPrepared(player: MediaPlayer) {
        Timber.d("onPrepared from MediaPlayer")
        // The media player is done preparing. That means we can start playing if we
        // have audio focus.
        isPrepared = true
        configMediaPlayerState()
    }

    /**
     * Called when there's an error playing media. When this happens, the media
     * player goes to the Error state. We warn the user about the error and
     * reset the media player.
     *
     * @see OnErrorListener
     */
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        Timber.e("Media player error: what= %d, extra=%d", what, extra)
        callback.onError("MediaPlayer error $what ($extra)")
        return true // true indicates we handled the error
    }

    /**
     * Makes sure the media player exists and has been reset. This will create
     * the media player if needed, or reset the existing media player if one
     * already exists.
     */
    private fun createMediaPlayerIfNeeded() {
        Timber.d("createMediaPlayerIfNeeded. needed? %b", mediaPlayer == null)
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.let { mediaPlayer ->
                // Make sure the media player will acquire a wake-lock while
                // playing. If we don't do that, the CPU might go to sleep while the
                // song is playing, causing playback to stop.
                mediaPlayer.setWakeMode(context.applicationContext,
                        PowerManager.PARTIAL_WAKE_LOCK)

                // we want the media player to notify us when it's ready preparing,
                // and when it's done playing:
                mediaPlayer.setOnPreparedListener(this)
                mediaPlayer.setOnCompletionListener(this)
                mediaPlayer.setOnErrorListener(this)
                mediaPlayer.setOnSeekCompleteListener(this)
                mediaPlayer.setOnBufferingUpdateListener(this)
                mediaPlayer.setOnInfoListener(this)
            }
        } else {
            mediaPlayer?.reset()
        }
        isPrepared = false
    }

    /**
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also
     * be released or not
     */
    private fun relaxResources(releaseMediaPlayer: Boolean) {
        Timber.d("relaxResources. releaseMediaPlayer= %b", releaseMediaPlayer)

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer && mediaPlayer != null) {
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        // we can also release the Wifi lock, if we're holding it
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
    }

    override fun onBufferingUpdate(mp: MediaPlayer, percent: Int) {
        // do not change state
        callback.onPlaybackStatusChanged(state)
    }

    override fun onInfo(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        Timber.d("onInfo what: %d, extra: %d", what, extra)
        when (what) {
            MediaPlayer.MEDIA_INFO_BUFFERING_START -> state = PlaybackStateCompat.STATE_BUFFERING

            MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (state == PlaybackStateCompat.STATE_BUFFERING) {
                state = PlaybackStateCompat.STATE_PLAYING
            }
        }
        // do not change state
        callback.onPlaybackStatusChanged(state)
        return false
    }

}
