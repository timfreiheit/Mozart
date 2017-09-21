package de.timfreiheit.mozart.model.image;

import android.graphics.Bitmap;

import io.reactivex.Single;

public abstract class MozartMediaImageLoader {

    public abstract Single<Bitmap> loadCover(String uri);

}
