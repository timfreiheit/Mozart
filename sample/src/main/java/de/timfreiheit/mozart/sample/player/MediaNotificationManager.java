package de.timfreiheit.mozart.sample.player;

import android.graphics.Bitmap;
import android.os.RemoteException;

import com.squareup.picasso.Picasso;

import java.io.IOException;

import de.timfreiheit.mozart.MozartMediaNotificationManager;
import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.sample.App;
import de.timfreiheit.mozart.sample.R;
import io.reactivex.Single;

public class MediaNotificationManager extends MozartMediaNotificationManager {
    public MediaNotificationManager(MozartMusicService service) throws RemoteException {
        super(service);
    }

    @Override
    protected int getNotificationIcon() {
        return R.mipmap.ic_launcher_round;
    }

    @Override
    public Single<Bitmap> fetchBitmapFromURLAsync(String bitmapUrl) {
        return Single.defer(() -> {
            try {
                return Single.just(Picasso.with(App.instance()).load(bitmapUrl).get());
            } catch (IOException e1) {
                return Single.error(e1);
            }
        });
    }
}
