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

import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.model.MozartMediaMetadata;
import de.timfreiheit.mozart.model.Playlist;
import de.timfreiheit.mozart.utils.QueueHelper;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Simple data provider for queues. Keeps track of a current queue and a current index in the
 * queue. Also provides methods to set the current queue based on common queries, relying on a
 * given MusicProvider to provide the actual media metadata.
 */
public class QueueManager {

    private MozartMusicService mozartMusicService;
    private MetadataUpdateListener listener;

    // "Now playing" queue:
    private Playlist playlist;
    private List<MediaSessionCompat.QueueItem> playingQueue;
    private int currentIndex;

    public QueueManager(MozartMusicService service) {
        this.listener = service;
        mozartMusicService = service;
        playlist = new Playlist(null, null, Collections.emptyList());
        playingQueue = Collections.synchronizedList(new ArrayList<MediaSessionCompat.QueueItem>());
        currentIndex = 0;
    }

    private void setCurrentQueueIndex(int index) {
        if (index >= 0 && index < playingQueue.size()) {
            currentIndex = index;
            listener.onCurrentQueueIndexUpdated(currentIndex);
        }
    }

    public boolean setCurrentQueueItem(long queueId) {
        // set the current index on queue from the queue Id:
        int index = QueueHelper.getMusicIndexOnQueue(playingQueue, queueId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    public boolean setCurrentQueueItem(String mediaId) {
        // set the current index on queue from the music Id:
        int index = QueueHelper.getMusicIndexOnQueue(playingQueue, mediaId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    public boolean skipQueuePosition(int amount) {
        int index = currentIndex + amount;
        if (index < 0) {
            // skip backwards before the first song will keep you on the first song
            index = 0;
        } else {
            // skip forwards when in last song will cycle back to start of the queue
            index %= playingQueue.size();
        }
        if (!QueueHelper.isIndexPlayable(index, playingQueue)) {
            Timber.e("Cannot increment queue index by %d. Current=%d queue length=%d", amount, currentIndex, playingQueue.size());
            return false;
        }
        currentIndex = index;
        return true;
    }

    public MediaSessionCompat.QueueItem getCurrentMusic() {
        if (!QueueHelper.isIndexPlayable(currentIndex, playingQueue)) {
            return null;
        }
        return playingQueue.get(currentIndex);
    }

    public int getCurrentQueueSize() {
        if (playingQueue == null) {
            return 0;
        }
        return playingQueue.size();
    }

    public Completable setQueueByMediaId(String mediaId) {
        return mozartMusicService.getMediaProvider().getMediaById(mediaId)
                .flatMapCompletable(mediaMetadataCompat -> setQueueFromPlaylist(new Playlist(null, null, Collections.singletonList(mediaMetadataCompat)), 0))
                .doOnComplete(this::updateMetadata);
    }

    public Completable updateQueueByMediaId(String mediaId) {
        return Completable.defer(() -> {
            int index = playlist.getPositionByMediaId(mediaId);
            if (index >= 0) {
                currentIndex = index;
                updateMetadata();
                return Completable.complete();
            }
            return setQueueByMediaId(mediaId);
        });
    }

    public Completable setQueueByPlaylistId(String playlistId, String initialMedia) {
        return mozartMusicService.getMediaProvider().getPlaylistById(playlistId)
                .flatMapCompletable(playlist1 -> {
                    int index = playlist1.getPositionByMediaId(initialMedia);
                    return setQueueFromPlaylist(playlist1, index);
                });
    }

    public Completable setQueueByPlaylistId(String playlistId, int initialPosition) {
        return mozartMusicService.getMediaProvider().getPlaylistById(playlistId)
                .flatMapCompletable(playlist1 -> setQueueFromPlaylist(playlist1, initialPosition));
    }

    public Completable setQueueFromPlaylist(Playlist playlist, int initialPosition) {
        return Completable.fromAction(() -> {
            this.playlist = playlist;
            setCurrentQueue(playlist.getTitle(), QueueHelper.mediaQueueFromPlaylist(playlist), initialPosition);
        }).doOnComplete(this::updateMetadata);
    }

    private void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue) {
        setCurrentQueue(title, newQueue, 0);
    }

    private void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue,
                                 int initialPosition) {
        playingQueue = newQueue;
        currentIndex = Math.max(initialPosition, 0);
        listener.onQueueUpdated(title, newQueue);
    }

    public void updateMetadata() {
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
        if (currentMusic == null) {
            listener.onMetadataRetrieveError();
            return;
        }

        Single.defer(() -> {
            if (playlist != null) {
                for (MediaMetadataCompat track : playlist.getPlaylist()) {
                    if (currentMusic.getDescription().getMediaId().equals(track.getDescription().getMediaId())) {
                        return Single.just(track);
                    }
                }
            }
            return mozartMusicService.getMediaProvider().getMediaById(currentMusic.getDescription().getMediaId());
        }).subscribeOn(Schedulers.io())
                .subscribe(metadata -> {
                    metadata = new MediaMetadataCompat.Builder(metadata)
                            .putString(MozartMediaMetadata.META_DATA_PLAYLIST, playlist.getId())
                            .build();
                    listener.onMetadataChanged(metadata);
                }, throwable -> {
                    throw new IllegalArgumentException("Invalid musicId " + currentMusic.getDescription().getMediaId());
                });
    }

    public String getPlaylistId() {
        if (playlist != null) {
            return playlist.getId();
        }
        return null;
    }

    public interface MetadataUpdateListener {
        void onMetadataChanged(MediaMetadataCompat metadata);

        void onMetadataRetrieveError();

        void onCurrentQueueIndexUpdated(int queueIndex);

        void onQueueUpdated(String title, List<MediaSessionCompat.QueueItem> newQueue);
    }
}
