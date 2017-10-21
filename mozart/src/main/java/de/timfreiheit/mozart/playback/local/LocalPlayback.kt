package de.timfreiheit.mozart.playback.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.support.annotation.CallSuper
import android.support.annotation.IntDef
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import de.timfreiheit.mozart.MozartServiceActions
import de.timfreiheit.mozart.playback.Playback
import timber.log.Timber


// we don't have audio focus, and can't duck (play at a low volume)
const val AUDIO_NO_FOCUS_NO_DUCK = 0
// we don't have focus, but can duck (play at a low volume)
const val AUDIO_NO_FOCUS_CAN_DUCK = 1
// we have full audio focus
const val AUDIO_FOCUSED = 2

// The volume we set the media player to when we lose audio focus, but are
// allowed to reduce the volume instead of stopping playback.
const val VOLUME_DUCK = 0.2f
// The volume we set the media player when we have audio focus.
const val VOLUME_NORMAL = 1.0f

abstract class LocalPlayback(val context: Context) : Playback(), AudioManager.OnAudioFocusChangeListener {

    // Type of audio focus we have:
    @AudioFocusStatus
    @get:AudioFocusStatus
    var audioFocusStatus = AUDIO_NO_FOCUS_NO_DUCK
        private set
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var isAudioNoisyReceiverRegistered: Boolean = false
    private val audioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                Timber.d("Headphones disconnected.")
                becomeNoisy()
            }
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(AUDIO_FOCUSED.toLong(), AUDIO_NO_FOCUS_CAN_DUCK.toLong(), AUDIO_NO_FOCUS_NO_DUCK.toLong())
    annotation class AudioFocusStatus

    init {
        addCallback(object : SimpleCallback() {
            override fun onPlaybackStatusChanged(@PlaybackStateCompat.State state: Int) {
                when (state) {
                    STATE_BUFFERING, STATE_CONNECTING, STATE_PLAYING -> registerAudioNoisyReceiver()
                    else -> unregisterAudioNoisyReceiver()
                }
            }
        })
    }

    @CallSuper
    override fun play(item: MediaMetadataCompat) {
        tryToGetAudioFocus()
    }

    override fun onStop(notifyListeners: Boolean) {
        super.onStop(notifyListeners)
        unregisterAudioNoisyReceiver()
        giveUpAudioFocus()
    }

    protected fun becomeNoisy() {
        if (isPlaying) {
            context.startService(MozartServiceActions.pause(context))
        }
    }

    protected fun registerAudioNoisyReceiver() {
        if (!isAudioNoisyReceiverRegistered) {
            context.registerReceiver(audioNoisyReceiver, audioNoisyIntentFilter)
            isAudioNoisyReceiverRegistered = true
        }
    }

    protected fun unregisterAudioNoisyReceiver() {
        if (isAudioNoisyReceiverRegistered) {
            context.unregisterReceiver(audioNoisyReceiver)
            isAudioNoisyReceiverRegistered = false
        }
    }

    /**
     * @see AudioManager.OnAudioFocusChangeListener
     */
    @CallSuper
    override fun onAudioFocusChange(focusChange: Int) {
        Timber.d("onAudioFocusChange. focusChange= %d", focusChange)
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // We have gained focus:
            audioFocusStatus = AUDIO_FOCUSED

        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // We have lost focus. If we can duck (low playback volume), we can keep playing.
            // Otherwise, we need to pause the playback.
            val canDuck = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            audioFocusStatus = if (canDuck) AUDIO_NO_FOCUS_CAN_DUCK else AUDIO_NO_FOCUS_NO_DUCK

            // If we are playing, we need to reset media player by calling configMediaPlayerState
            // with mAudioFocus properly set.
            if (state == PlaybackStateCompat.STATE_PLAYING && !canDuck) {
                // If we don't have audio focus and can't duck, we save the information that
                // we were playing, so that we can resume playback once we get the focus back.
                //                mPlayOnFocusGain = true;
            }
        } else {
            Timber.e("onAudioFocusChange: Ignoring unsupported focusChange: %d", focusChange)
        }
    }

    /**
     * Try to get the system audio focus.
     */
    private fun tryToGetAudioFocus() {
        Timber.d("tryToGetAudioFocus")
        val result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusStatus = AUDIO_FOCUSED
        } else {
            audioFocusStatus = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    /**
     * Give up the audio focus.
     */
    private fun giveUpAudioFocus() {
        Timber.d("giveUpAudioFocus")
        if (audioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusStatus = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

}
