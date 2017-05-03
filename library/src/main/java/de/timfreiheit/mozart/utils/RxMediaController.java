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
                    emitter.onNext(Optional.of(metadata));
                }
            };
            controllerCompat.registerCallback(callback);

            emitter.setCancellable(() -> controllerCompat.unregisterCallback(callback));
        }).startWith(Optional.of(controllerCompat.getMetadata()));
    }

    public static Observable<PlaybackStateCompat> playbackState(MediaControllerCompat controllerCompat) {
        return Observable.<PlaybackStateCompat>create(emitter -> {
            MediaControllerCompat.Callback callback = new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    super.onPlaybackStateChanged(state);
                    emitter.onNext(state);
                }
            };
            controllerCompat.registerCallback(callback);

            emitter.setCancellable(() -> controllerCompat.unregisterCallback(callback));
        }).startWith(controllerCompat.getPlaybackState());
    }

}
