package de.timfreiheit.mozart.model.image

import android.graphics.Bitmap

import com.google.auto.value.AutoValue

data class CoverImage(
    val largeImage: Bitmap?,
    val icon: Bitmap?
) {

    val byteCount: Int by lazy {
        var size = 0
        if (largeImage != null) {
            size += largeImage.byteCount
        }
        if (icon != null) {
            size += icon.byteCount
        }
        size
    }

}
