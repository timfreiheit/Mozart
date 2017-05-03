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

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import java.io.IOException;

import de.timfreiheit.mozart.model.MozartMediaMetadata;
import de.timfreiheit.mozart.playback.local.LocalPlayback;
import timber.log.Timber;

import static android.media.MediaPlayer.OnCompletionListener;
import static android.media.MediaPlayer.OnErrorListener;
import static android.media.MediaPlayer.OnPreparedListener;
import static android.media.MediaPlayer.OnSeekCompleteListener;

/**
 * A class that implements local media playback using {@link MediaPlayer}
 */
public class LocalMediaPlayerPlayback extends LocalPlayback implements
        OnCompletionListener, OnErrorListener, OnPreparedListener, OnSeekCompleteListener {

    // The volume we set the media player to when we lose audio focus, but are
    // allowed to reduce the volume instead of stopping playback.
    private static final float VOLUME_DUCK = 0.2f;
    // The volume we set the media player when we have audio focus.
    private static final float VOLUME_NORMAL = 1.0f;

    private final Context context;
    private final WifiManager.WifiLock wifiLock;

    private boolean playOnFocusGain;
    private volatile int currentPosition;

    private MediaPlayer mediaPlayer;

    public LocalMediaPlayerPlayback(Context context) {
        super(context);
        this.context = context;
        // Create the Wifi lock (this does not acquire the lock, this just creates it)
        this.wifiLock = ((WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "uAmp_lock");
    }

    @Override
    public void onStop(boolean notifyListeners) {
        super.onStop(notifyListeners);
        setState(PlaybackStateCompat.STATE_STOPPED);
        if (notifyListeners) {
            getCallback().onPlaybackStatusChanged(getState());
        }
        currentPosition = getCurrentStreamPosition();
        // Relax all resources
        relaxResources(true);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isPlaying() {
        return playOnFocusGain || (mediaPlayer != null && mediaPlayer.isPlaying());
    }

    @Override
    public int getCurrentStreamPosition() {
        return mediaPlayer != null ?
                mediaPlayer.getCurrentPosition() : currentPosition;
    }

    @Override
    public void updateLastKnownStreamPosition() {
        if (mediaPlayer != null) {
            currentPosition = mediaPlayer.getCurrentPosition();
        }
    }

    @Override
    public void play(MediaMetadataCompat item) {
        super.play(item);
        playOnFocusGain = true;
        String mediaId = item.getDescription().getMediaId();
        boolean mediaHasChanged = getCurrentMedia() == null || !TextUtils.equals(mediaId, getCurrentMedia().getDescription().getMediaId());
        if (mediaHasChanged) {
            currentPosition = 0;
            setCurrentMedia(item);
        }

        if (getState() == PlaybackStateCompat.STATE_PAUSED && !mediaHasChanged && mediaPlayer != null) {
            configMediaPlayerState();
        } else {
            setState(PlaybackStateCompat.STATE_STOPPED);
            relaxResources(false); // release everything except MediaPlayer

            //noinspection ResourceType
            String source = MozartMediaMetadata.getContentUri(item);
            if (source != null) {
                source = source.replaceAll(" ", "%20"); // Escape spaces for URLs
            }

            try {
                createMediaPlayerIfNeeded();

                setState(PlaybackStateCompat.STATE_BUFFERING);

                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDataSource(source);

                // Starts preparing the media player in the background. When
                // it's done, it will call our OnPreparedListener (that is,
                // the onPrepared() method on this class, since we set the
                // listener to 'this'). Until the media player is prepared,
                // we *cannot* call start() on it!
                mediaPlayer.prepareAsync();

                // If we are streaming from the internet, we want to hold a
                // Wifi lock, which prevents the Wifi radio from going to
                // sleep while the song is playing.

                wifiLock.acquire();

                getCallback().onPlaybackStatusChanged(getState());

            } catch (IOException ex) {
                Timber.e(ex, "Exception playing song");
                getCallback().onError(ex.getMessage());
            }
        }
    }

    @Override
    public void pause() {
        if (getState() == PlaybackStateCompat.STATE_PLAYING) {
            // Pause media player and cancel the 'foreground service' state.
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                currentPosition = mediaPlayer.getCurrentPosition();
            }
            // while paused, retain the MediaPlayer but give up audio focus
            relaxResources(false);
        }
        setState(PlaybackStateCompat.STATE_PAUSED);
        getCallback().onPlaybackStatusChanged(getState());
    }

    @Override
    public void seekTo(int position) {
        Timber.d("seekTo called with %s", position);

        if (mediaPlayer == null) {
            // If we do not have a current media player, simply update the current position
            currentPosition = position;
        } else {
            if (mediaPlayer.isPlaying()) {
                setState(PlaybackStateCompat.STATE_BUFFERING);
            }
            mediaPlayer.seekTo(position);
            getCallback().onPlaybackStatusChanged(getState());
        }
    }

    @Override
    public void setCurrentStreamPosition(int pos) {
        this.currentPosition = pos;
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
    private void configMediaPlayerState() {
        Timber.d("configMediaPlayerState. mAudioFocus= %d", getAudioFocusStatus());
        if (getAudioFocusStatus() == AUDIO_NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck, we have to pause,
            if (getState() == PlaybackStateCompat.STATE_PLAYING) {
                pause();
            }
        } else {  // we have audio focus:
            if (getAudioFocusStatus() == AUDIO_NO_FOCUS_CAN_DUCK) {
                mediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK); // we'll be relatively quiet
            } else {
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL); // we can be loud again
                } // else do something for remote client.
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (playOnFocusGain) {
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    Timber.d("configMediaPlayerState startMediaPlayer. seeking to %d",
                            currentPosition);
                    if (currentPosition == mediaPlayer.getCurrentPosition()) {
                        mediaPlayer.start();
                        setState(PlaybackStateCompat.STATE_PLAYING);
                    } else {
                        mediaPlayer.seekTo(currentPosition);
                        setState(PlaybackStateCompat.STATE_BUFFERING);
                    }
                }
                playOnFocusGain = false;
            }
        }
        getCallback().onPlaybackStatusChanged(getState());
    }

    /**
     * Called by AudioManager on audio focus changes.
     * Implementation of {@link AudioManager.OnAudioFocusChangeListener}
     */
    @Override
    public void onAudioFocusChange(int focusChange) {
        super.onAudioFocusChange(focusChange);
        Timber.d("onAudioFocusChange. focusChange= %d", focusChange);

        if (getState() == PlaybackStateCompat.STATE_PLAYING && getState() == AUDIO_NO_FOCUS_CAN_DUCK) {
            // If we don't have audio focus and can't duck, we save the information that
            // we were playing, so that we can resume playback once we get the focus back.
            playOnFocusGain = true;
        }
        configMediaPlayerState();
    }

    /**
     * Called when MediaPlayer has completed a seek
     *
     * @see OnSeekCompleteListener
     */
    @Override
    public void onSeekComplete(MediaPlayer mp) {
        Timber.d("onSeekComplete from MediaPlayer: %d", mp.getCurrentPosition());
        currentPosition = mp.getCurrentPosition();
        if (getState() == PlaybackStateCompat.STATE_BUFFERING) {
            mediaPlayer.start();
            setState(PlaybackStateCompat.STATE_PLAYING);
        }
        getCallback().onPlaybackStatusChanged(getState());
    }

    /**
     * Called when media player is done playing current song.
     *
     * @see OnCompletionListener
     */
    @Override
    public void onCompletion(MediaPlayer player) {
        Timber.d("onCompletion from MediaPlayer");
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        getCallback().onCompletion();
    }

    /**
     * Called when media player is done preparing.
     *
     * @see OnPreparedListener
     */
    @Override
    public void onPrepared(MediaPlayer player) {
        Timber.d("onPrepared from MediaPlayer");
        // The media player is done preparing. That means we can start playing if we
        // have audio focus.
        configMediaPlayerState();
    }

    /**
     * Called when there's an error playing media. When this happens, the media
     * player goes to the Error state. We warn the user about the error and
     * reset the media player.
     *
     * @see OnErrorListener
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Timber.e("Media player error: what= %d, extra=%d", what, extra);
        getCallback().onError("MediaPlayer error " + what + " (" + extra + ")");
        return true; // true indicates we handled the error
    }

    /**
     * Makes sure the media player exists and has been reset. This will create
     * the media player if needed, or reset the existing media player if one
     * already exists.
     */
    private void createMediaPlayerIfNeeded() {
        Timber.d("createMediaPlayerIfNeeded. needed? %b", (mediaPlayer == null));
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();

            // Make sure the media player will acquire a wake-lock while
            // playing. If we don't do that, the CPU might go to sleep while the
            // song is playing, causing playback to stop.
            mediaPlayer.setWakeMode(context.getApplicationContext(),
                    PowerManager.PARTIAL_WAKE_LOCK);

            // we want the media player to notify us when it's ready preparing,
            // and when it's done playing:
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnSeekCompleteListener(this);
        } else {
            mediaPlayer.reset();
        }
    }

    /**
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also
     *                           be released or not
     */
    private void relaxResources(boolean releaseMediaPlayer) {
        Timber.d("relaxResources. releaseMediaPlayer= %b", releaseMediaPlayer);

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer && mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // we can also release the Wifi lock, if we're holding it
        if (wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

}
