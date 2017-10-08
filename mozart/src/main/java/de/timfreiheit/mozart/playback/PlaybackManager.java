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

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.MozartPlayCommand;
import de.timfreiheit.mozart.model.MozartPlaybackStateKt;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
public class PlaybackManager implements Playback.Callback {

    private MozartMusicService mozartMusicService;
    private Playback playback;
    private PlaybackServiceCallback serviceCallback;
    private MediaSessionCallback mediaSessionCallback;

    private MozartPlayCommand lastPlayCommand;

    private final CompositeDisposable getDataDisposable = new CompositeDisposable();

    public PlaybackManager(MozartMusicService service) {
        this.serviceCallback = service;
        mediaSessionCallback = new MediaSessionCallback();
        this.mozartMusicService = service;
        this.playback = service.createLocalPlayback();
        this.playback.setCallback(this);
    }

    public Playback getPlayback() {
        return playback;
    }

    public MediaSessionCompat.Callback getMediaSessionCallback() {
        return mediaSessionCallback;
    }

    /**
     * Handle a request to play music
     */
    public void handlePlayRequest() {
        Timber.d("handlePlayRequest: mState= %d", playback.getState());
        MediaSessionCompat.QueueItem currentMusic = mozartMusicService.getQueueManager().getCurrentMusic();
        if (currentMusic != null) {
            playMediaById(currentMusic.getDescription().getMediaId());
        }
    }

    public void playMediaById(String mediaId) {

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions())
                .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f, SystemClock.elapsedRealtime());
        serviceCallback.onPlaybackStateUpdated(stateBuilder.build());

        getDataDisposable.clear();
        getDataDisposable.add(mozartMusicService.getMediaProvider().getMediaById(mediaId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<MediaMetadataCompat>() {
                    @Override
                    public void onSuccess(MediaMetadataCompat mediaMetadata) {
                        playback.setCurrentMedia(mediaMetadata);
                        if (lastPlayCommand != null && lastPlayCommand.mediaId() != null && lastPlayCommand.mediaId().equals(mediaMetadata.getDescription().getMediaId())) {
                            playback.setCurrentStreamPosition(lastPlayCommand.mediaPlaybackPosition());
                            lastPlayCommand = null;
                        }

                        playback.play(mediaMetadata);
                        serviceCallback.onPlaybackStart();
                    }

                    @Override
                    public void onError(Throwable e) {
                        handleStopRequest(e.getLocalizedMessage());
                    }
                }));
    }

    /**
     * Handle a request to pause music
     */
    public void handlePauseRequest() {
        Timber.d("handlePauseRequest: mState= %d", playback.getState());
        if (playback.isPlaying()) {
            playback.pause();
            serviceCallback.onPlaybackStop();
        }
    }

    /**
     * Handle a request to stop music
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     *                  message will be set in the PlaybackState and will be visible to
     *                  MediaController clients.
     */
    public void handleStopRequest(String withError) {
        Timber.d("handleStopRequest: mState= %d error %s", playback.getState(), withError);
        playback.onStop(true);
        serviceCallback.onPlaybackStop();
        updatePlaybackState(withError);
    }


    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    public void updatePlaybackState(String error) {
        Timber.d("updatePlaybackState, playback state= %d", playback.getState());
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        long duration = -1;
        if (playback != null && playback.isConnected()) {
            position = playback.getCurrentStreamPosition();
            duration = playback.getStreamDuration();
        }

        if (duration < 0) {
            // fall back to provided metadata when available
            MediaMetadataCompat metaData = mozartMusicService.getMediaController().getMetadata();
            if (metaData != null) {
                duration = (int) metaData.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                if (duration == 0 || duration < position) {
                    // position is not set or invalid
                    duration = -1;
                }
            }
        }

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions());

        setCustomAction(stateBuilder);
        int state = playback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error);
            state = PlaybackStateCompat.STATE_ERROR;
        }

        Bundle extras = new Bundle();
        extras.putLong(MozartPlaybackStateKt.getSTATE_DURATION(), duration);
        addPlaybackStateExtras(extras);
        stateBuilder.setExtras(extras);

        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        MediaSessionCompat.QueueItem currentMusic = mozartMusicService.getQueueManager().getCurrentMusic();
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.getQueueId());
        }

        serviceCallback.onPlaybackStateUpdated(stateBuilder.build());
    }

    protected void addPlaybackStateExtras(Bundle bundle) {

    }

    protected void setCustomAction(PlaybackStateCompat.Builder stateBuilder) {
    }

    public long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;

        if (playback.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        } else {
            actions |= PlaybackStateCompat.ACTION_PLAY;
        }

        int playlistSize = mozartMusicService.getQueueManager().getCurrentQueueSize();
        int currentIndex = mozartMusicService.getQueueManager().getCurrentIndex();

        actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        if (currentIndex < playlistSize - 1) {
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        }

        return actions;
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    @Override
    public void onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mozartMusicService.getQueueManager().skipQueuePosition(1)) {
            handlePlayRequest();
            mozartMusicService.getQueueManager().updateMetadata();
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null);
        }
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        updatePlaybackState(null);
    }

    @Override
    public void onError(String error) {
        updatePlaybackState(error);
    }

    /**
     * Switch to a different Playback instance, maintaining all playback state, if possible.
     *
     * @param playback switch to this playback
     */
    public void switchToPlayback(Playback playback, boolean resumePlaying) {
        if (playback == null) {
            throw new IllegalArgumentException("Playback cannot be null");
        }
        Timber.d("switchToPlayback(%s)", playback);
        // suspend the current one.
        int oldState = this.playback.getState();
        long pos = this.playback.getCurrentStreamPosition();
        MediaMetadataCompat currentMedia = this.playback.getCurrentMedia();
        this.playback.onStop(false);
        playback.setCallback(this);
        playback.setCurrentStreamPosition(pos < 0 ? 0 : pos);
        playback.setCurrentMedia(currentMedia);
        playback.onStart();
        // finally swap the instance
        this.playback = playback;
        switch (oldState) {
            case PlaybackStateCompat.STATE_BUFFERING:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_PAUSED:
                this.playback.pause();
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                MediaSessionCompat.QueueItem currentMusic = mozartMusicService.getQueueManager().getCurrentMusic();
                if (resumePlaying && currentMusic != null) {
                    playMediaById(currentMusic.getDescription().getMediaId());
                } else if (!resumePlaying) {
                    this.playback.pause();
                } else {
                    this.playback.onStop(true);
                }
                break;
            case PlaybackStateCompat.STATE_NONE:
                break;
            default:
                Timber.d("Default called. Old state is %s", oldState);
        }
    }

    public void handlePlayFromMediaId(String mediaId, Bundle extras) {
        Timber.d("handlePlayFromMediaId() called with " + "mediaId = [" + mediaId + "], extras = [" + extras + "]");
        mozartMusicService.getQueueManager().updateQueueByMediaId(mediaId)
                .subscribe(this::handlePlayRequest, throwable -> {
                });
    }

    public void handlePlaySingleMediaId(String mediaId) {
        Timber.d("handlePlaySingleMediaId() called with " + "mediaId = [" + mediaId + "]");
        mozartMusicService.getQueueManager().setQueueByMediaId(mediaId)
                .subscribe(this::handlePlayRequest, throwable -> {
                });
    }

    public void handlePlayPlaylist(String playlistId, String mediaId) {
        Timber.d("handlePlayPlaylist() called with " + "playlistId = [" + playlistId + "], mediaId = [" + mediaId + "]");
        mozartMusicService.getQueueManager().setQueueByPlaylistId(playlistId, mediaId)
                .subscribe(this::handlePlayRequest, throwable -> {
                });
    }

    public void handlePlayPlaylist(String playlistId, int position) {
        Timber.d("handlePlayPlaylist() called with " + "playlistId = [" + playlistId + "], position = [" + position + "]");
        mozartMusicService.getQueueManager().setQueueByPlaylistId(playlistId, position)
                .subscribe(this::handlePlayRequest, throwable -> {
                });
    }

    /**
     * Handle free and contextual searches.
     * <p/>
     * All voice searches on Android Auto are sent to this method through a connected
     * {@link android.support.v4.media.session.MediaControllerCompat}.
     * <p/>
     * Threads and async handling:
     * Search, as a potentially slow operation, should run in another thread.
     * <p/>
     * Since this method runs on the main thread, most apps with non-trivial metadata
     * should defer the actual search to another thread (for example, by using
     * an {@link AsyncTask} as we do here).
     **/
    public void handleCustomAction(@NonNull String action, Bundle extras) {
        Timber.d("handleCustomAction() called with " + "action = [" + action + "], extras = [" + extras + "]");
    }

    public void handlePlayFromSearch(final String query, final Bundle extras) {
        Timber.d("playFromSearch  query=%s  extras=%s", query, extras);
    }

    public void handlePlayCommand(MozartPlayCommand playCommand) {
        if (playCommand.playlistId() == null && playCommand.mediaId() == null) {
            return;
        }
        lastPlayCommand = playCommand;
        if (playCommand.playlistId() == null) {
            handlePlaySingleMediaId(playCommand.mediaId());
        } else {
            if (playCommand.mediaId() != null) {
                handlePlayPlaylist(playCommand.playlistId(), playCommand.mediaId());
            } else {
                handlePlayPlaylist(playCommand.playlistId(), playCommand.playlistPosition());
            }
        }
    }

    protected class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Timber.d("play");
            handlePlayRequest();
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            Timber.d("OnSkipToQueueItem: %d", queueId);
            mozartMusicService.getQueueManager().setCurrentQueueItem(queueId);
            mozartMusicService.getQueueManager().updateMetadata();
        }

        @Override
        public void onSeekTo(long position) {
            Timber.d("onSeekTo: %d", position);
            playback.seekTo((int) position);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Timber.d("playFromMediaId mediaId: %s,  extras= %s", mediaId, extras);
            handlePlayFromMediaId(mediaId, extras);
        }

        @Override
        public void onPause() {
            Timber.d("pause. current state= %d", playback.getState());
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            Timber.d("stop. current state= %d", playback.getState());
            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            Timber.d("skipToNext");
            if (mozartMusicService.getQueueManager().skipQueuePosition(1)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip");
            }
            mozartMusicService.getQueueManager().updateMetadata();
        }

        @Override
        public void onSkipToPrevious() {
            if (mozartMusicService.getQueueManager().skipQueuePosition(-1)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip");
            }
            mozartMusicService.getQueueManager().updateMetadata();
        }

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
            handleCustomAction(action, extras);
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            handlePlayFromSearch(query, extras);
        }
    }


    public interface PlaybackServiceCallback {
        void onPlaybackStart();

        void onPlaybackStop();

        void onPlaybackStateUpdated(PlaybackStateCompat newState);
    }
}
