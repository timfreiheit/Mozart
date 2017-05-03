package de.timfreiheit.mozart;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.v4.media.MediaBrowserServiceCompat;

import java.util.List;

public class MozartServiceActions {

    public static Class<? extends Service> getMusicService(Context context) {
        Intent intent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
        intent.setPackage(context.getPackageName());

        List<ResolveInfo> handlers = context.getPackageManager().queryIntentServices(intent, 0);
        if (handlers == null || handlers.size() != 1) {
            throw new IllegalStateException("no music service registered");
        }

        String serviceName = handlers.get(0).serviceInfo.name;

        Class<? extends Service> cls = null;
        try {
            //noinspection unchecked
            cls = (Class<? extends Service>) Class.forName(serviceName);
        } catch (ClassNotFoundException e) {
            // do nothing
        }
        return cls;
    }

    public static Intent pause(Context context) {
        Intent intent = new Intent(context, getMusicService(context));
        intent.setAction(MozartMusicService.ACTION_CMD);
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PAUSE);
        return intent;
    }

    public static Intent playMedia(Context context, String mediaId) {
        return playMedia(context, mediaId, null);
    }

    public static Intent playMedia(Context context, String playlistId, String mediaId) {
        Intent intent = new Intent(context, getMusicService(context));
        intent.setAction(MozartMusicService.ACTION_CMD);
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PLAY);
        intent.putExtra(MozartMusicService.ARGS_MEDIA_ID, mediaId);
        intent.putExtra(MozartMusicService.ARGS_PLAYLIST_ID, playlistId);
        return intent;
    }

    public static Intent playMedia(Context context, String playlistId, int position) {
        Intent intent = new Intent(context, getMusicService(context));
        intent.setAction(MozartMusicService.ACTION_CMD);
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PLAY);
        intent.putExtra(MozartMusicService.ARGS_PLAYLIST_POSITION, position);
        intent.putExtra(MozartMusicService.ARGS_PLAYLIST_ID, playlistId);
        return intent;
    }
}
