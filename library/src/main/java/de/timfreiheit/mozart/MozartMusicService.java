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

package de.timfreiheit.mozart;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.framework.CastSession;

import java.lang.ref.WeakReference;
import java.util.List;

import de.timfreiheit.mozart.model.MozartMediaImageLoader;
import de.timfreiheit.mozart.model.MozartMediaProvider;
import de.timfreiheit.mozart.playback.LocalMediaPlayerPlayback;
import de.timfreiheit.mozart.playback.Playback;
import de.timfreiheit.mozart.playback.PlaybackManager;
import de.timfreiheit.mozart.playback.QueueManager;
import de.timfreiheit.mozart.playback.cast.CastPlayback;
import de.timfreiheit.mozart.playback.cast.CastPlaybackSwitcher;
import de.timfreiheit.mozart.ui.OpenAppShadowActivity;
import de.timfreiheit.mozart.utils.CarHelper;
import de.timfreiheit.mozart.utils.WearHelper;
import timber.log.Timber;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 * <p>
 * To implement a MediaBrowserService, you need to:
 * <p>
 * <ul>
 * <p>
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 * related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 * {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 * with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 * <p>
 * <li> Set a callback on the
 * {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 * The callback will receive all the user's actions, like play, pause, etc;
 * <p>
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 * {@link android.media.MediaPlayer})
 * <p>
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 * {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 * {@link android.media.session.MediaSession#setQueue(List)})
 * <p>
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 * <p>
 * </ul>
 * <p>
 * To make your app compatible with Android Auto, you also need to:
 * <p>
 * <ul>
 * <p>
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 * <p>
 * </ul>
 *
 * @see <a href="README.md">README.md</a> for more details.
 */
public abstract class MozartMusicService extends MediaBrowserServiceCompat implements
        PlaybackManager.PlaybackServiceCallback, QueueManager.MetadataUpdateListener {

    // The action of the incoming Intent indicating that it contains a command
    // to be executed (see {@link #onStartCommand})
    public static final String ACTION_CMD = "de.timfreiheit.mozart.ACTION_CMD";

    public static final String CMD_NAME = "CMD_NAME";

    public static final String CMD_PLAY = "CMD_PLAY";
    public static final String CMD_PAUSE = "CMD_PAUSE";
    public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";

    public static final String ARGS_MEDIA_ID = "ARGS_MEDIA_ID";
    public static final String ARGS_PLAYLIST_POSITION = "ARGS_PLAYLIST_POSITION";
    public static final String ARGS_PLAYLIST_ID = "ARGS_PLAYLIST_ID";

    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    private QueueManager queueManager;
    private PlaybackManager playbackManager;

    private MediaSessionCompat mediaSession;
    private Bundle sessionExtras;
    private final DelayedStopHandler delayedStopHandler = new DelayedStopHandler(this);
    private MediaRouter mediaRouter;

    private boolean isConnectedToCar;
    private BroadcastReceiver carConnectionReceiver;


    private CastPlaybackSwitcher castPlaybackSwitcher;

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("onCreate");

        // Start a new MediaSession
        mediaSession = new MediaSessionCompat(this, "MusicService");
        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setCallback(getPlaybackManager().getMediaSessionCallback());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Context context = getApplicationContext();

        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                getMediaSessionIntent(), PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setSessionActivity(pi);

        sessionExtras = new Bundle();

        WearHelper.setSlotReservationFlags(sessionExtras, true, true);
        WearHelper.setUseBackgroundFromTheme(sessionExtras, true);
        mediaSession.setExtras(sessionExtras);

        playbackManager.updatePlaybackState(null);

        castPlaybackSwitcher = new CastPlaybackSwitcher(this);
        castPlaybackSwitcher.onCreate();

        registerCarConnectionReceiver();
    }

    @NonNull
    protected Intent getMediaSessionIntent() {
        return new Intent(getApplicationContext(), OpenAppShadowActivity.class);
    }

    public abstract MozartMediaProvider getMediaProvider();

    public abstract MozartMediaNotificationManager getMediaNotificationManager();

    public abstract MozartMediaImageLoader getImageLoader();

    public Playback createLocalPlayback() {
        return new LocalMediaPlayerPlayback(this);
    }

    /**
     * create cast playback
     * this can be depended on the current CastSession
     * should return null when the Service should not connect with this session
     */
    public Playback createCastPlayback(CastSession session) {
        return new CastPlayback(this);
    }

    public PlaybackManager getPlaybackManager() {
        if (playbackManager == null) {
            playbackManager = new PlaybackManager(this);
        }
        return playbackManager;
    }

    public QueueManager getQueueManager() {
        if (queueManager == null) {
            queueManager = new QueueManager(this);
        }
        return queueManager;
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service#onStartCommand(Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            String action = startIntent.getAction();
            if (ACTION_CMD.equals(action)) {
                handleCMD(startIntent);
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mediaSession, startIntent);
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        delayedStopHandler.removeCallbacksAndMessages(null);
        delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        return START_STICKY;
    }

    protected void handleCMD(Intent startIntent) {
        String command = startIntent.getStringExtra(CMD_NAME);
        switch (command) {
            case CMD_PAUSE:
                getPlaybackManager().handlePauseRequest();
                break;
            case CMD_PLAY:
                String playlist = startIntent.getStringExtra(ARGS_PLAYLIST_ID);
                String mediaId = startIntent.getStringExtra(ARGS_MEDIA_ID);
                int playlistPosition = startIntent.getIntExtra(ARGS_PLAYLIST_POSITION, 0);
                if (playlist == null && mediaId == null) {
                    return;
                }
                if (playlist == null) {
                    getPlaybackManager().handlePlaySingleMediaId(mediaId);
                } else {
                    if (mediaId != null) {
                        getPlaybackManager().handlePlayPlaylist(playlist, mediaId);
                    } else {
                        getPlaybackManager().handlePlayPlaylist(playlist, playlistPosition);
                    }
                }
                break;
            case CMD_STOP_CASTING:
                castPlaybackSwitcher.stopCasting();
                onPlaybackStop();
                break;
        }
    }

    @Override
    public void setSessionToken(MediaSessionCompat.Token token) {
        super.setSessionToken(token);
        Mozart.get(this).setMediaSessionToken(token);
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        Timber.d("onDestroy");
        unregisterCarConnectionReceiver();
        // Service is being killed, so make sure we release our resources
        playbackManager.handleStopRequest(null);
        getMediaNotificationManager().stopNotification();

        castPlaybackSwitcher.onDestroy();

        delayedStopHandler.removeCallbacksAndMessages(null);
        mediaSession.release();

        Mozart.get(this).setMediaSessionToken(null);
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
                                 Bundle rootHints) {
        return getMediaProvider().onGetRoot(clientPackageName, clientUid, rootHints);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentMediaId,
                               @NonNull final Result<List<MediaItem>> result) {
        getMediaProvider().onLoadChildren(parentMediaId, result);
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    @Override
    public void onPlaybackStart() {
        mediaSession.setActive(true);

        delayedStopHandler.removeCallbacksAndMessages(null);

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        startService(new Intent(getApplicationContext(), MozartMusicService.class));
    }

    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    @Override
    public void onPlaybackStop() {
        mediaSession.setActive(false);
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        delayedStopHandler.removeCallbacksAndMessages(null);
        delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        stopForeground(true);
    }

    @Override
    public void onNotificationRequired() {
        new Handler(getMainLooper()).post(() -> {
            getMediaNotificationManager().startNotification();
        });
    }

    @Override
    public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
        mediaSession.setPlaybackState(newState);
    }

    private void registerCarConnectionReceiver() {
        IntentFilter filter = new IntentFilter(CarHelper.ACTION_MEDIA_STATUS);
        carConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS);
                isConnectedToCar = CarHelper.MEDIA_CONNECTED.equals(connectionEvent);
                Timber.i("Connection event to Android Auto: %s isConnectedToCar= %b", connectionEvent, isConnectedToCar);
            }
        };
        registerReceiver(carConnectionReceiver, filter);
    }

    private void unregisterCarConnectionReceiver() {
        unregisterReceiver(carConnectionReceiver);
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MozartMusicService> weakReference;

        private DelayedStopHandler(MozartMusicService service) {
            weakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MozartMusicService service = weakReference.get();
            if (service != null && service.playbackManager.getPlayback() != null) {
                if (service.playbackManager.getPlayback().isPlaying()) {
                    Timber.d("Ignoring delayed stop since the media player is in use.");
                    return;
                }
                Timber.d("Stopping service with delay handler.");
                service.stopSelf();
            }
        }
    }

    /**
     * @see QueueManager.MetadataUpdateListener#onMetadataChanged(MediaMetadataCompat)
     */
    @Override
    public void onMetadataChanged(MediaMetadataCompat metadata) {
        mediaSession.setMetadata(metadata);
    }

    /**
     * @see QueueManager.MetadataUpdateListener#onMetadataRetrieveError()
     */
    @Override
    public void onMetadataRetrieveError() {
        getPlaybackManager().updatePlaybackState(
                getString(R.string.error_no_metadata));
    }

    /**
     * @see QueueManager.MetadataUpdateListener#onCurrentQueueIndexUpdated(int)
     */
    @Override
    public void onCurrentQueueIndexUpdated(int queueIndex) {
        getPlaybackManager().handlePlayRequest();
    }


    /**
     * @see QueueManager.MetadataUpdateListener#onQueueUpdated(String, List)
     */
    @Override
    public void onQueueUpdated(String title,
                               List<MediaSessionCompat.QueueItem> newQueue) {
        mediaSession.setQueue(newQueue);
        mediaSession.setQueueTitle(title);
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public Bundle getSessionExtras() {
        return sessionExtras;
    }

    public void setSessionExtras(Bundle bundle) {
        sessionExtras.putAll(bundle);
        mediaSession.setExtras(sessionExtras);
    }

    public MediaRouter getMediaRouter() {
        if (mediaRouter == null) {
            mediaRouter = MediaRouter.getInstance(getApplicationContext());
        }
        return mediaRouter;
    }
}
