package de.timfreiheit.mozart.model.image;

import android.graphics.Bitmap;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CoverImage {

    public abstract Bitmap largeImage();

    public abstract Bitmap icon();

    public static CoverImage create(Bitmap largeImage, Bitmap icon) {
        return new AutoValue_CoverImage(largeImage, icon);
    }

}
