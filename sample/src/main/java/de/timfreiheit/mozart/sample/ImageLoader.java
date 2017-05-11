package de.timfreiheit.mozart.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.util.Log;

import com.squareup.picasso.Cache;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

import java.io.IOException;

import de.timfreiheit.mozart.model.image.MozartMediaImageLoader;
import io.reactivex.Single;

public class ImageLoader extends MozartMediaImageLoader {

    private static final String TAG = ImageLoader.class.getSimpleName();
    private static ImageLoader instance = new ImageLoader();

    private Picasso picasso;

    public static ImageLoader getInstance() {
        return instance;
    }

    public void init(Context context) {

        picasso = new Picasso.Builder(context)
                .listener((picasso, uri, exception) -> {
            Log.d(TAG, "onImageLoadFailed() called with " + "picasso = [" + picasso + "], uri = [" + uri + "], exception = [" + exception + "]");
        }).build();
        Picasso.setSingletonInstance(picasso);
    }

    @Override
    public Single<Bitmap> loadCover(String uri) {
        return Single.defer(() -> {
            try {
                return Single.just(Picasso.with(App.instance()).load(uri).get());
            } catch (IOException e1) {
                return Single.error(e1);
            }
        });
    }

}
