package de.timfreiheit.mozart.sample

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.squareup.picasso.Picasso
import de.timfreiheit.mozart.model.image.MozartMediaImageLoader
import io.reactivex.Single
import java.io.IOException

object ImageLoader : MozartMediaImageLoader() {

    private val TAG = "ImageLoader"
    private var picasso: Picasso? = null

    fun init(context: Context) {

        picasso = Picasso.Builder(context)
                .listener { picasso, uri, exception -> Log.d(TAG, "onImageLoadFailed() called with picasso = [$picasso], uri = [$uri], exception = [$exception]") }.build()
        Picasso.setSingletonInstance(picasso)
    }

    override fun loadCover(uri: String): Single<Bitmap> {
        return Single.defer {
            try {
                return@defer Single.just<Bitmap>(Picasso.with(App.instance()).load(uri).get())
            } catch (e1: IOException) {
                return@defer Single.error<Bitmap>(e1)
            }
        }
    }

}
