package de.timfreiheit.mozart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

public class Mozart {

    @SuppressLint("StaticFieldLeak")
    private static Mozart mozart;

    public static Mozart get(Context context) {
        if (mozart == null) {
            mozart = new Mozart(context.getApplicationContext());
        }
        return mozart;
    }

    private Context context;

    private final BehaviorSubject<MediaSessionCompat.Token> mediaSessionSubject = BehaviorSubject.create();

    private Mozart(Context context) {
        this.context = context.getApplicationContext();
    }

    public void playMedia(String mediaId) {
        playMedia(null, mediaId);
    }

    public void playMedia(String playlistId, String mediaId) {
        context.startService(MozartServiceActions.playMedia(context, playlistId, mediaId));
    }

    public void playMedia(String playlistId, int position) {
        context.startService(MozartServiceActions.playMedia(context, playlistId, position));
    }

    public void setMediaSessionToken(MediaSessionCompat.Token token) {
        mediaSessionSubject.onNext(token);
    }

    @Nullable
    public MediaSessionCompat.Token getMediaSessionToken() {
        return mediaSessionSubject.getValue();
    }

    public Observable<MediaSessionCompat.Token> mediaSessionToken() {
        return mediaSessionSubject;
    }

    public Observable<MediaControllerCompat> mediaController() {
        return mediaSessionSubject.map(token -> new MediaControllerCompat(context, token));
    }

    public MediaControllerCompat getMediaController() {
        if (mediaSessionSubject.getValue() != null) {
            try {
                return new MediaControllerCompat(context, mediaSessionSubject.getValue());
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }
}
