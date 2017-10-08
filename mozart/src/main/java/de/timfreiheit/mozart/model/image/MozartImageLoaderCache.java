package de.timfreiheit.mozart.model.image;

import android.app.Application;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;

import io.reactivex.Single;
import timber.log.Timber;

import static de.timfreiheit.mozart.utils.BitmapHelperKt.scaleBitmap;

public class MozartImageLoaderCache {

    private static final int MAX_ALBUM_ART_CACHE_SIZE = 8 * 1024 * 1024;

    private static final int MAX_ART_WIDTH_PX = 800;
    private static final int MAX_ART_HEIGHT_PX = 480;

    // Resolution reasonable for carrying around as an icon (generally in
    // MediaDescription.getIconBitmap). This should not be bigger than necessary, because
    // the MediaDescription object should be lightweight. If you set it too high and try to
    // serialize the MediaDescription, you may get FAILED BINDER TRANSACTION errors.
    private static final int MAX_ART_WIDTH_ICON_PX = 128;
    private static final int MAX_ART_HEIGHT_ICON_PX = 128;

    private final MozartMediaImageLoader imageLoader;
    private LruCache<String, CoverImage> cache;

    public MozartImageLoaderCache(MozartMediaImageLoader imageLoader) {
        this.imageLoader = imageLoader;

        int cacheSize = getCacheSize();
        Timber.d("cacheSize: %d", cacheSize);
        cache = new LruCache<String, CoverImage>(cacheSize) {
            @Override
            protected int sizeOf(String key, CoverImage value) {
                return value.getByteCount();
            }
        };
    }

    protected int getCacheSize() {
        return Math.min(MAX_ALBUM_ART_CACHE_SIZE,
                (int) (Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8)));
    }

    /**
     * return the cached bitmap from memory if available
     * do not make any network or disk request
     */
    @Nullable
    public CoverImage getCachedBitmapFromMemory(String uri) {
        return cache.get(uri);
    }

    public Single<CoverImage> loadCover(String uri) {
        return Single.defer(() -> {
            if(cache.get(uri) == null) {
                return imageLoader.loadCover(uri)
                        .map(loadedBitmap -> {
                            Bitmap coverBitmap = scaleBitmap(loadedBitmap,
                                    MAX_ART_WIDTH_PX, MAX_ART_HEIGHT_PX);
                            Bitmap iconBitmap = scaleBitmap(loadedBitmap,
                                    MAX_ART_WIDTH_ICON_PX, MAX_ART_HEIGHT_ICON_PX);
                            CoverImage coverImage = new CoverImage(coverBitmap, iconBitmap);
                            cache.put(uri, coverImage);
                            return coverImage;
                        });
            }
            return Single.just(cache.get(uri));
        });
    }

    /**
     * @see Application#onTrimMemory(int)
     */
    public void onTrimMemory(int level) {
        switch (level) {
            case Application.TRIM_MEMORY_RUNNING_LOW:
            case Application.TRIM_MEMORY_RUNNING_CRITICAL:
                cache.resize(1);
                cache.resize(getCacheSize());
                break;
            default:
        }
    }
}
