package de.timfreiheit.mozart.playback.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.annotation.CallSuper;
import android.support.annotation.IntDef;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import de.timfreiheit.mozart.MozartServiceActions;
import de.timfreiheit.mozart.playback.Playback;
import de.timfreiheit.mozart.playback.PlaybackCallbackDelegate;
import timber.log.Timber;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

public abstract class LocalPlayback extends Playback implements AudioManager.OnAudioFocusChangeListener {

    // we don't have audio focus, and can't duck (play at a low volume)
    public static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    // we don't have focus, but can duck (play at a low volume)
    public static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
    // we have full audio focus
    public static final int AUDIO_FOCUSED = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            AUDIO_FOCUSED,
            AUDIO_NO_FOCUS_CAN_DUCK,
            AUDIO_NO_FOCUS_NO_DUCK
    })
    public @interface AudioFocusStatus {
    }

    private final Context context;

    // Type of audio focus we have:
    @AudioFocusStatus
    private int audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    private final AudioManager audioManager;

    private volatile boolean isAudioNoisyReceiverRegistered;
    private final IntentFilter audioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private final BroadcastReceiver audioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Timber.d("Headphones disconnected.");
                becomeNoisy();
            }
        }
    };

    public LocalPlayback(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setCallback(null);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @CallSuper
    @Override
    public void play(MediaMetadataCompat item) {
        tryToGetAudioFocus();
    }

    @Override
    public void onStop(boolean notifyListeners) {
        super.onStop(notifyListeners);
        unregisterAudioNoisyReceiver();
        giveUpAudioFocus();
    }

    protected void becomeNoisy() {
        if (isPlaying()) {
            context.startService(MozartServiceActions.pause(context));
        }
    }

    @Override
    public void setCallback(Callback callback) {
        super.setCallback(new PlaybackCallbackDelegate(callback) {
            @Override
            public void onPlaybackStatusChanged(@PlaybackStateCompat.State int state) {
                super.onPlaybackStatusChanged(state);
                switch (state) {
                    case STATE_BUFFERING:
                    case STATE_CONNECTING:
                    case STATE_PLAYING:
                        registerAudioNoisyReceiver();
                        break;
                    default:
                        unregisterAudioNoisyReceiver();
                        break;
                }
            }
        });
    }

    protected void registerAudioNoisyReceiver() {
        if (!isAudioNoisyReceiverRegistered) {
            context.registerReceiver(audioNoisyReceiver, audioNoisyIntentFilter);
            isAudioNoisyReceiverRegistered = true;
        }
    }

    protected void unregisterAudioNoisyReceiver() {
        if (isAudioNoisyReceiverRegistered) {
            context.unregisterReceiver(audioNoisyReceiver);
            isAudioNoisyReceiverRegistered = false;
        }
    }

    @AudioFocusStatus
    public int getAudioFocusStatus() {
        return audioFocus;
    }

    /**
     * @see AudioManager.OnAudioFocusChangeListener
     */
    @CallSuper
    @Override
    public void onAudioFocusChange(int focusChange) {
        Timber.d("onAudioFocusChange. focusChange= %d", focusChange);
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // We have gained focus:
            audioFocus = AUDIO_FOCUSED;

        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // We have lost focus. If we can duck (low playback volume), we can keep playing.
            // Otherwise, we need to pause the playback.
            boolean canDuck = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
            audioFocus = canDuck ? AUDIO_NO_FOCUS_CAN_DUCK : AUDIO_NO_FOCUS_NO_DUCK;

            // If we are playing, we need to reset media player by calling configMediaPlayerState
            // with mAudioFocus properly set.
            if (getState() == PlaybackStateCompat.STATE_PLAYING && !canDuck) {
                // If we don't have audio focus and can't duck, we save the information that
                // we were playing, so that we can resume playback once we get the focus back.
//                mPlayOnFocusGain = true;
            }
        } else {
            Timber.e("onAudioFocusChange: Ignoring unsupported focusChange: %d", focusChange);
        }
    }

    /**
     * Try to get the system audio focus.
     */
    private void tryToGetAudioFocus() {
        Timber.d("tryToGetAudioFocus");
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocus = AUDIO_FOCUSED;
        } else {
            audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        }
    }

    /**
     * Give up the audio focus.
     */
    private void giveUpAudioFocus() {
        Timber.d("giveUpAudioFocus");
        if (audioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        }
    }

}
