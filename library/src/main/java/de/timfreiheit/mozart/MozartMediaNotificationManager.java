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

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.RemoteException;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import de.timfreiheit.mozart.ui.OpenAppShadowActivity;
import de.timfreiheit.mozart.utils.ResourceHelper;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
public abstract class MozartMediaNotificationManager extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 412;
    private static final int REQUEST_CODE = 100;

    public static final String ACTION_PAUSE = "de.timfreiheit.mozart.pause";
    public static final String ACTION_PLAY = "de.timfreiheit.mozart.play";
    public static final String ACTION_PREV = "de.timfreiheit.mozart.prev";
    public static final String ACTION_NEXT = "de.timfreiheit.mozart.next";
    public static final String ACTION_STOP = "de.timfreiheit.mozart.stop";
    public static final String ACTION_STOP_CASTING = "de.timfreiheit.mozart.stop_cast";

    private final MozartMusicService service;
    private MediaSessionCompat.Token mSessionToken;
    private MediaControllerCompat controller;
    private MediaControllerCompat.TransportControls transportControls;

    private PlaybackStateCompat playbackState;
    private MediaMetadataCompat metadata;

    private final NotificationManagerCompat notificationManager;

    private final PendingIntent pauseIntent;
    private final PendingIntent playIntent;
    private final PendingIntent previousIntent;
    private final PendingIntent nextIntent;
    private final PendingIntent stopIntent;

    private final PendingIntent stopCastIntent;

    private final int notificationColor;

    private boolean started = false;

    private String lastCoverImageUrl;
    private Bitmap lastCoverImage;

    public MozartMediaNotificationManager(MozartMusicService service) throws RemoteException {
        this.service = service;
        updateSessionToken();

        notificationColor = ResourceHelper.getThemeColor(this.service, R.attr.colorPrimary,
                Color.DKGRAY);

        notificationManager = NotificationManagerCompat.from(service);

        String pkg = this.service.getPackageName();
        pauseIntent = PendingIntent.getBroadcast(
                this.service,
                REQUEST_CODE,
                new Intent(ACTION_PAUSE).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        playIntent = PendingIntent.getBroadcast(
                this.service,
                REQUEST_CODE,
                new Intent(ACTION_PLAY).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        previousIntent = PendingIntent.getBroadcast(
                this.service,
                REQUEST_CODE,
                new Intent(ACTION_PREV).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        nextIntent = PendingIntent.getBroadcast(
                this.service, REQUEST_CODE,
                new Intent(ACTION_NEXT).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        stopIntent = PendingIntent.getBroadcast(
                this.service, REQUEST_CODE,
                new Intent(ACTION_STOP).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        stopCastIntent = PendingIntent.getBroadcast(
                this.service,
                REQUEST_CODE,
                new Intent(ACTION_STOP_CASTING).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        notificationManager.cancelAll();
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before {@link #stopNotification} is called.
     */
    @MainThread
    public void startNotification() {
        if (!started) {
            metadata = controller.getMetadata();
            playbackState = controller.getPlaybackState();

            // The notification must be updated after setting started to true
            Notification notification = createNotification();
            if (notification != null) {
                controller.registerCallback(mCb);
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_NEXT);
                filter.addAction(ACTION_PAUSE);
                filter.addAction(ACTION_PLAY);
                filter.addAction(ACTION_PREV);
                filter.addAction(ACTION_STOP);
                filter.addAction(ACTION_STOP_CASTING);
                service.registerReceiver(this, filter);

                service.startForeground(NOTIFICATION_ID, notification);
                started = true;
            }
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    public void stopNotification() {
        if (started) {
            started = false;
            controller.unregisterCallback(mCb);
            try {
                notificationManager.cancel(NOTIFICATION_ID);
                service.unregisterReceiver(this);
            } catch (IllegalArgumentException ex) {
                // ignore if the receiver is not registered.
            }
            service.stopForeground(true);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Timber.d("Received intent with action " + action);
        switch (action) {
            case ACTION_PAUSE:
                transportControls.pause();
                break;
            case ACTION_PLAY:
                transportControls.play();
                break;
            case ACTION_NEXT:
                transportControls.skipToNext();
                break;
            case ACTION_PREV:
                transportControls.skipToPrevious();
                break;
            case ACTION_STOP:
                transportControls.stop();
                break;
            case ACTION_STOP_CASTING:
                Intent i = new Intent(context, MozartMusicService.class);
                i.setAction(MozartMusicService.ACTION_CMD);
                i.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_STOP_CASTING);
                service.startService(i);
                break;
            default:
                Timber.w("Unknown intent ignored. Action %s", action);
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see {@link android.media.session.MediaController.Callback#onSessionDestroyed()})
     */
    private void updateSessionToken() throws RemoteException {
        MediaSessionCompat.Token freshToken = service.getSessionToken();
        if (mSessionToken == null && freshToken != null ||
                mSessionToken != null && !mSessionToken.equals(freshToken)) {
            if (controller != null) {
                controller.unregisterCallback(mCb);
            }
            mSessionToken = freshToken;
            if (mSessionToken != null) {
                controller = new MediaControllerCompat(service, mSessionToken);
                transportControls = controller.getTransportControls();
                if (started) {
                    controller.registerCallback(mCb);
                }
            }
        }
    }

    protected PendingIntent createContentIntent(MediaDescriptionCompat description) {
        Intent openUI = new Intent(service, OpenAppShadowActivity.class);
        return PendingIntent.getActivity(service, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private final MediaControllerCompat.Callback mCb = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            playbackState = state;
            Timber.d("Received new playback state %s", state);
            if (state.getState() == PlaybackStateCompat.STATE_STOPPED ||
                    state.getState() == PlaybackStateCompat.STATE_NONE) {
                stopNotification();
            } else {
                Notification notification = createNotification();
                if (notification != null) {
                    notificationManager.notify(NOTIFICATION_ID, notification);
                }
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            MozartMediaNotificationManager.this.metadata = metadata;
            Timber.d("Received new metadata %s", metadata);
            Notification notification = createNotification();
            if (notification != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            Timber.d("Session was destroyed, resetting to the new session token");
            try {
                updateSessionToken();
            } catch (RemoteException e) {
                Timber.e(e, "could not connect media controller");
            }
        }
    };

    protected Notification createNotification() {
        Timber.d("updateNotificationMetadata. mMetadata= %s" + metadata);
        if (metadata == null || playbackState == null) {
            return null;
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(service);

        int availableActions = addNotificationsActions(notificationBuilder);

        MediaDescriptionCompat description = metadata.getDescription();

        String fetchArtUrl = null;
        Bitmap art = null;
        if (description.getIconUri() != null) {
            // This sample assumes the iconUri will be a valid URL formatted String, but
            // it can actually be any valid Android Uri formatted String.
            // async fetch the album art icon
            String artUrl = description.getIconUri().toString();
            if (artUrl != null && artUrl.equals(lastCoverImageUrl)) {
                art = lastCoverImage;
            } else {
                art = service.getImageLoader().getCachedBitmapFromMemory(artUrl);
            }
            if (art == null) {
                fetchArtUrl = artUrl;
                // use a placeholder art while the remote art is being downloaded
                art = getDetailCover();
            }
        }

        notificationBuilder
                .setStyle(createMediaStyle(availableActions))
                .setColor(notificationColor)
                .setSmallIcon(getNotificationIcon())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setUsesChronometer(true)
                .setContentIntent(createContentIntent(description))
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle());

        if (art != null) {
            notificationBuilder.setLargeIcon(art);
        }

        if (controller != null && controller.getExtras() != null) {
            String castName = controller.getExtras().getString(MozartMusicService.EXTRA_CONNECTED_CAST);
            if (castName != null) {
                String castInfo = service.getResources()
                        .getString(R.string.casting_to_device, castName);
                notificationBuilder.setSubText(castInfo);
                notificationBuilder.addAction(R.drawable.ic_dialog_close_light,
                        service.getString(R.string.stop_casting), stopCastIntent);
            }
        }

        setNotificationPlaybackState(notificationBuilder);
        if (fetchArtUrl != null) {
            String finalFetchArtUrl = fetchArtUrl;
            service.getImageLoader().loadCover(fetchArtUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(bitmap -> {
                        if (metadata != null && metadata.getDescription().getIconUri() != null &&
                                metadata.getDescription().getIconUri().toString().equals(finalFetchArtUrl)) {
                            lastCoverImage = bitmap;
                            lastCoverImageUrl = finalFetchArtUrl;

                            // If the media is still the same, update the notification:
                            Timber.d("fetchBitmapFromURLAsync: set bitmap to %s", finalFetchArtUrl);
                            notificationBuilder.setLargeIcon(bitmap);
                            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                        }
                    }, throwable -> {
                        Timber.d(throwable, "error fetchBitmapFromURLAsync: set bitmap to %s", finalFetchArtUrl);
                    });
        }

        return notificationBuilder.build();
    }

    protected abstract int getNotificationIcon();

    protected Bitmap getDetailCover() {
        return BitmapFactory.decodeResource(service.getResources(), R.drawable.ic_default_art);
    }

    protected NotificationCompat.MediaStyle createMediaStyle(int availableActions) {

        if(availableActions < 0) {
            availableActions = 0;
        }
        if (availableActions > 3){
            availableActions = 3;
        }

        int[] compactViewActions = new int[availableActions];
        for (int i = 0; i < compactViewActions.length; i++) {
            compactViewActions[i] = i;
        }

        return new NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(compactViewActions)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopIntent)
                .setMediaSession(mSessionToken);
    }

    protected int addNotificationsActions(NotificationCompat.Builder builder) {

        int availableActions = 0;
        if ((playbackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            builder.addAction(R.drawable.ic_skip_previous_white_24dp,
                    service.getString(R.string.label_previous), previousIntent);
            availableActions++;
        }

        String label;
        int icon;
        PendingIntent intent;
        if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            label = service.getString(R.string.label_pause);
            icon = R.drawable.uamp_ic_pause_white_24dp;
            intent = pauseIntent;
        } else {
            label = service.getString(R.string.label_play);
            icon = R.drawable.uamp_ic_play_arrow_white_24dp;
            intent = playIntent;
        }
        builder.addAction(new NotificationCompat.Action(icon, label, intent));
        availableActions++;

        // If skip to next action is enabled
        if ((playbackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            builder.addAction(R.drawable.ic_skip_next_white_24dp,
                    service.getString(R.string.label_next), nextIntent);
            availableActions++;
        }

        return availableActions;
    }

    private void setNotificationPlaybackState(NotificationCompat.Builder builder) {
        Timber.d("updateNotificationPlaybackState. mPlaybackState=" + playbackState);
        if (playbackState == null || !started) {
            Timber.d("updateNotificationPlaybackState. cancelling notification!");
            service.stopForeground(true);
            return;
        }
        if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING
                && playbackState.getPosition() >= 0) {
            Timber.d("updateNotificationPlaybackState. updating playback position to %d seconds",
                    (System.currentTimeMillis() - playbackState.getPosition()) / 1000);
            builder
                    .setWhen(System.currentTimeMillis() - playbackState.getPosition())
                    .setShowWhen(true)
                    .setUsesChronometer(true);
        } else {
            Timber.d("updateNotificationPlaybackState. hiding playback position");
            builder
                    .setWhen(0)
                    .setShowWhen(false)
                    .setUsesChronometer(false);
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(playbackState.getState() == PlaybackStateCompat.STATE_PLAYING);
    }
}
