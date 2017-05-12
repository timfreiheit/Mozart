package de.timfreiheit.mozart.model.image;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CoverImage {

    @Nullable
    public abstract Bitmap largeImage();

    @Nullable
    public abstract Bitmap icon();

    public static CoverImage create(Bitmap largeImage, Bitmap icon) {
        return new AutoValue_CoverImage(largeImage, icon);
    }

    @SuppressWarnings("ConstantConditions")
    public int byteCount() {
        int size = 0;
        if (largeImage() != null) {
            size += largeImage().getByteCount();
        }
        if (icon() != null) {
            size += icon().getByteCount();
        }
        return size;
    }

}
