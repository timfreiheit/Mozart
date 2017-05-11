package de.timfreiheit.mozart.sample.player;

import android.os.RemoteException;

import de.timfreiheit.mozart.MozartMediaNotificationManager;
import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.model.image.MozartMediaImageLoader;
import de.timfreiheit.mozart.model.MozartMediaProvider;
import de.timfreiheit.mozart.sample.ImageLoader;

public class MusicService extends MozartMusicService {

    private MediaNotificationManager mediaNotificationManager;

    @Override
    public MozartMediaProvider getMediaProvider() {
        return MediaProvider.getInstance();
    }

    @Override
    public MozartMediaNotificationManager getMediaNotificationManager() {
        if (mediaNotificationManager == null) {
            try {
                mediaNotificationManager = new MediaNotificationManager(this);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return mediaNotificationManager;
    }

    @Override
    public MozartMediaImageLoader getImageLoader() {
        return ImageLoader.getInstance();
    }
}
