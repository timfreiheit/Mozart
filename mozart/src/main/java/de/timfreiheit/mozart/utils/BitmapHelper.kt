package de.timfreiheit.mozart.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import java.io.InputStream


internal fun scaleBitmap(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val scaleFactor = Math.min(
            maxWidth.toDouble() / src.width, maxHeight.toDouble() / src.height)
    return Bitmap.createScaledBitmap(src,
            (src.width * scaleFactor).toInt(), (src.height * scaleFactor).toInt(), false)
}

internal fun scaleBitmap(scaleFactor: Int, `is`: InputStream): Bitmap {
    // Get the dimensions of the bitmap
    val bmOptions = BitmapFactory.Options()

    // Decode the image file into a Bitmap sized to fill the View
    bmOptions.inJustDecodeBounds = false
    bmOptions.inSampleSize = scaleFactor

    return BitmapFactory.decodeStream(`is`, null, bmOptions)
}

internal fun findScaleFactor(targetW: Int, targetH: Int, `is`: InputStream): Int {
    // Get the dimensions of the bitmap
    val bmOptions = BitmapFactory.Options()
    bmOptions.inJustDecodeBounds = true
    BitmapFactory.decodeStream(`is`, null, bmOptions)
    val actualW = bmOptions.outWidth
    val actualH = bmOptions.outHeight

    // Determine how much to scale down the image
    return Math.min(actualW / targetW, actualH / targetH)
}
