package de.timfreiheit.mozart.playback

import android.support.v4.media.session.PlaybackStateCompat

open class PlaybackCallbackDelegate(callback: Playback.Callback) : Playback.Callback {

    private var delegate: Playback.Callback? = null

    init {
        setDelegate(callback)
    }

    fun setDelegate(callback: Playback.Callback) {
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
