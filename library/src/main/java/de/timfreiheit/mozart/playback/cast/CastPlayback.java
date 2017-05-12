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
package de.timfreiheit.mozart.playback.cast;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.json.JSONException;
import org.json.JSONObject;

import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.playback.Playback;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * An implementation of Playback that talks to Cast.
 */
public class CastPlayback extends Playback {

    private static final String ITEM_ID = "ITEM_ID";
    private static final String PLAYLIST_ID = "PLAYLIST_ID";

    private final MozartMusicService service;
    private final RemoteMediaClient remoteMediaClient;
    private final RemoteMediaClient.Listener mRemoteMediaClientListener;

    private volatile int currentPosition;
    private volatile int duration;

    public CastPlayback(MozartMusicService service) {
        this.service = service;
        CastSession castSession = CastContext.getSharedInstance(service.getApplicationContext()).getSessionManager()
                .getCurrentCastSession();
        remoteMediaClient = castSession.getRemoteMediaClient();
        mRemoteMediaClientListener = new CastMediaClientListener();
    }

    @Override
    public void onStart() {
        super.onStart();
        remoteMediaClient.addListener(mRemoteMediaClientListener);
    }

    @Override
    public void onStop(boolean notifyListeners) {
        super.onStop(notifyListeners);
        remoteMediaClient.removeListener(mRemoteMediaClientListener);
        setState(PlaybackStateCompat.STATE_STOPPED);
        if (notifyListeners) {
            getCallback().onPlaybackStatusChanged(getState());
        }
    }

    @Override
    public int getCurrentStreamPosition() {
        if (!isConnected()) {
            return currentPosition;
        }
        return (int) remoteMediaClient.getApproximateStreamPosition();
    }

    @Override
    public int getStreamDuration() {
        if (!isConnected()) {
            return duration;
        }
        return (int) remoteMediaClient.getStreamDuration();
    }

    @Override
    public void setCurrentStreamPosition(int pos) {
        this.currentPosition = pos;
    }

    @Override
    public void updateLastKnownStreamPosition() {
        currentPosition = getCurrentStreamPosition();
    }

    @Override
    public void play(MediaMetadataCompat item) {
        try {
            loadMedia(item, true);
            setState(PlaybackStateCompat.STATE_BUFFERING);
            getCallback().onPlaybackStatusChanged(getState());

        } catch (JSONException e) {
            Timber.e(e, "Exception loading media");
            getCallback().onError(e.getMessage());

        }
    }

    @Override
    public void pause() {
        try {
            if (remoteMediaClient.hasMediaSession()) {
                remoteMediaClient.pause();
                currentPosition = (int) remoteMediaClient.getApproximateStreamPosition();
                duration = (int) remoteMediaClient.getStreamDuration();
            } else {
                loadMedia(getCurrentMedia(), false);
            }
        } catch (JSONException e) {
            Timber.e(e, "Exception pausing cast playback");
            getCallback().onError(e.getMessage());

        }
    }

    @Override
    public void seekTo(int position) {
        if (getCurrentMedia() == null) {
            getCallback().onError("seekTo cannot be calling in the absence of mediaId.");

            return;
        }
        try {
            if (remoteMediaClient.hasMediaSession()) {
                remoteMediaClient.seek(position);
                currentPosition = position;
            } else {
                currentPosition = position;
                loadMedia(getCurrentMedia(), false);
            }
        } catch (JSONException e) {
            Timber.e(e, "Exception pausing cast playback");
            getCallback().onError(e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        CastSession castSession = CastContext.getSharedInstance(service.getApplicationContext()).getSessionManager()
                .getCurrentCastSession();
        return (castSession != null && castSession.isConnected());
    }

    @Override
    public boolean isPlaying() {
        return isConnected() && remoteMediaClient.isPlaying();
    }

    private void loadMedia(MediaMetadataCompat item, boolean autoPlay) throws JSONException {
        if (item == null) {
            return;
        }
        if (getCurrentMedia() == null || !TextUtils.equals(item.getDescription().getMediaId(), getCurrentMedia().getDescription().getMediaId())) {
            setCurrentMedia(item);
            currentPosition = 0;
        }
        JSONObject customData = new JSONObject();
        customData.put(ITEM_ID, item.getDescription().getMediaId());
        customData.put(PLAYLIST_ID, service.getQueueManager().getPlaylistId());
        MediaInfo media = MediaInfoUtils.metaDataToMediaInfo(item, customData);
        remoteMediaClient.load(media, autoPlay, currentPosition, customData);
    }

    private void setMetadataFromRemote() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            MediaInfo mediaInfo = remoteMediaClient.getMediaInfo();
            if (mediaInfo == null) {
                return;
            }
            JSONObject customData = mediaInfo.getCustomData();

            if (customData != null && customData.has(ITEM_ID)) {
                String remoteMediaId = customData.getString(ITEM_ID);
                String playlistId = customData.optString(PLAYLIST_ID, null);


                Completable completable = Completable.error(new Exception());
                if (playlistId != null) {
                    completable = service.getMediaProvider().getPlaylistById(playlistId)
                            .subscribeOn(Schedulers.io())
                            .flatMapCompletable(playlist -> {
                                int index = playlist.getPositionByMediaId(remoteMediaId);
                                return service.getQueueManager().setQueueFromPlaylist(playlist, index);
                            });
                }
                completable.onErrorResumeNext(throwable -> service.getMediaProvider().getMediaById(remoteMediaId)
                        .subscribeOn(Schedulers.io())
                        .flatMapCompletable(mediaMetadata -> service.getQueueManager().setQueueByMediaId(remoteMediaId)
                                .doOnComplete(() -> {
                                    // the remoteMedia should be skipped in queue
                                    service.getQueueManager().skipQueuePosition(1);
                                })))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::updateLastKnownStreamPosition, throwable -> {
                        });
            }
        } catch (JSONException e) {
            Timber.e(e, "Exception processing update metadata");
        }

    }

    private void updatePlaybackState() {
        int status = remoteMediaClient.getPlayerState();
        int idleReason = remoteMediaClient.getIdleReason();

        Timber.d("onRemoteMediaPlayerStatusUpdated %d", status);

        // Convert the remote playback states to media playback states.
        switch (status) {
            case MediaStatus.PLAYER_STATE_IDLE:
                if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    getCallback().onCompletion();
                }
                break;
            case MediaStatus.PLAYER_STATE_BUFFERING:
                setState(PlaybackStateCompat.STATE_BUFFERING);
                getCallback().onPlaybackStatusChanged(getState());

                break;
            case MediaStatus.PLAYER_STATE_PLAYING:
                setState(PlaybackStateCompat.STATE_PLAYING);
                setMetadataFromRemote();
                getCallback().onPlaybackStatusChanged(getState());

                break;
            case MediaStatus.PLAYER_STATE_PAUSED:
                setState(PlaybackStateCompat.STATE_PAUSED);
                setMetadataFromRemote();
                getCallback().onPlaybackStatusChanged(getState());

                break;
            default: // case unknown
                Timber.d("State default : %d", status);
                break;
        }
    }

    private class CastMediaClientListener implements RemoteMediaClient.Listener {

        @Override
        public void onMetadataUpdated() {
            Timber.d("RemoteMediaClient.onMetadataUpdated");
            setMetadataFromRemote();
        }

        @Override
        public void onStatusUpdated() {
            Timber.d("RemoteMediaClient.onStatusUpdated");
            updatePlaybackState();
        }

        @Override
        public void onSendingRemoteMediaRequest() {
            Timber.d("RemoteMediaClient.onSendingRemoteMediaRequest");
        }

        @Override
        public void onAdBreakStatusUpdated() {
            Timber.d("RemoteMediaClient.onAdBreakStatusUpdated");
        }

        @Override
        public void onQueueStatusUpdated() {
            Timber.d("RemoteMediaClient.onQueueStatusUpdated");
        }

        @Override
        public void onPreloadStatusUpdated() {
            Timber.d("RemoteMediaClient.onPreloadStatusUpdated");
        }
    }
}
