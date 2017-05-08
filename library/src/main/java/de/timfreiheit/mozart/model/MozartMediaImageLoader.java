package de.timfreiheit.mozart.model;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import io.reactivex.Single;

public abstract class MozartMediaImageLoader {

    /**
     * return the cached bitmap from memory if available
     * do not make any network or disk request
     */
    @Nullable
    public abstract Bitmap getCachedBitmapFromMemory(String uri);

    public abstract Single<Bitmap> loadCover(String uri);

}
