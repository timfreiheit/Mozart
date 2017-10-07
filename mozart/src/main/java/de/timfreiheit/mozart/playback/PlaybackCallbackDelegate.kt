package de.timfreiheit.mozart.playback

import android.support.v4.media.session.PlaybackStateCompat

open class PlaybackCallbackDelegate(
       private var delegate: Playback.Callback?
) : Playback.Callback {

    fun setDelegate(callback: Playback.Callback?) {
        this.delegate = callback
    }

    override fun onCompletion() {
        delegate?.onCompletion()
    }

    override fun onPlaybackStatusChanged(@PlaybackStateCompat.State state: Int) {
        delegate?.onPlaybackStatusChanged(state)
    }

    override fun onError(error: String) {
        delegate?.onError(error)
    }

}
