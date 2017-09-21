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

    public static Intent startIdle(Context context) {
        return new Intent(context, getMusicService(context));
    }


    public static Intent pause(Context context) {
        Intent intent = new Intent(context, getMusicService(context));
        intent.setAction(MozartMusicService.ACTION_CMD);
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PAUSE);
        return intent;
    }

    public static Intent stopCasting(Context context) {
        Intent intent = new Intent(context, getMusicService(context));
        intent.setAction(MozartMusicService.ACTION_CMD);
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_STOP_CASTING);
        return intent;
    }

    public static Intent executeCommand(Context context, MozartPlayCommand command) {
        Intent intent = new Intent(context, getMusicService(context));
        intent.setAction(MozartMusicService.ACTION_CMD);
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PLAY);
        intent.putExtra(MozartMusicService.ARGS_START_COMMAND, command);
        return intent;
    }
}