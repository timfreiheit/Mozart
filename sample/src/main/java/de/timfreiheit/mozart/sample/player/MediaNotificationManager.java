package de.timfreiheit.mozart.sample.player;

import android.os.RemoteException;

import de.timfreiheit.mozart.MozartMediaNotificationManager;
import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.sample.R;

public class MediaNotificationManager extends MozartMediaNotificationManager {
    public MediaNotificationManager(MozartMusicService service) throws RemoteException {
        super(service);
    }

    @Override
    protected int getNotificationIcon() {
        return R.mipmap.ic_launcher_round;
    }

}
