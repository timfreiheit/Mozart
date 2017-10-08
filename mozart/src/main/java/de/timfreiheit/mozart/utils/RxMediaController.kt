package de.timfreiheit.mozart.utils

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.gojuno.koptional.Optional
import com.gojuno.koptional.toOptional

import io.reactivex.Observable

fun MediaControllerCompat.metadataChanges(): Observable<Optional<MediaMetadataCompat>> {
    return Observable.create<Optional<MediaMetadataCompat>> { emitter ->
        val callback = object : MediaControllerCompat.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                super.onMetadataChanged(metadata)
                emitter.onNext(metadata.toOptional())
            }
        }
        registerCallback(callback)

        emitter.setCancellable { unregisterCallback(callback) }
        emitter.onNext(metadata.toOptional())
    }
}

fun MediaControllerCompat.playbackStateChanges(): Observable<Optional<PlaybackStateCompat>> {
    return Observable.create<Optional<PlaybackStateCompat>> { emitter ->
        val callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                super.onPlaybackStateChanged(state)
                emitter.onNext(state.toOptional())
            }
        }
        registerCallback(callback)

        emitter.setCancellable { unregisterCallback(callback) }
        emitter.onNext(playbackState.toOptional())
    }
}
