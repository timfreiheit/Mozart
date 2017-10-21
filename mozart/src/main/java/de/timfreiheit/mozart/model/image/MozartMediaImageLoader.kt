package de.timfreiheit.mozart.model.image

import android.graphics.Bitmap

import io.reactivex.Single

abstract class MozartMediaImageLoader {

    abstract fun loadCover(uri: String): Single<Bitmap>

}
