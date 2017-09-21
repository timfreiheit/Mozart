package de.timfreiheit.mozart.utils;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.hadisatrio.optional.Optional;

import io.reactivex.Observable;

public class RxMediaController {

    public static Observable<Optional<MediaMetadataCompat>> metadata(MediaControllerCompat controllerCompat) {
        return Observable.<Optional<MediaMetadataCompat>>create(emitter -> {
            MediaControllerCompat.Callback callback = new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    super.onMetadataChanged(metadata);
                    emitter.onNext(Optional.ofNullable(metadata));
                }
            };
            controllerCompat.registerCallback(callback);

            emitter.onNext(Optional.ofNullable(controllerCompat.getMetadata()));
            emitter.setCancellable(() -> controllerCompat.unregisterCallback(callback));
        }).startWith(Optional.ofNullable(controllerCompat.getMetadata()));
    }

    public static Observable<Optional<PlaybackStateCompat>> playbackState(MediaControllerCompat controllerCompat) {
        return Observable.<Optional<PlaybackStateCompat>>create(emitter -> {
            MediaControllerCompat.Callback callback = new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    super.onPlaybackStateChanged(state);
                    emitter.onNext(Optional.ofNullable(state));
                }
            };
            controllerCompat.registerCallback(callback);

            emitter.setCancellable(() -> controllerCompat.unregisterCallback(callback));
        }).startWith(Optional.ofNullable(controllerCompat.getPlaybackState()));
    }

}
