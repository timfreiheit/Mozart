package de.timfreiheit.mozart.model.image

import android.app.Application
import android.support.v4.util.LruCache

import io.reactivex.Single

import de.timfreiheit.mozart.utils.scaleBitmap

open class MozartImageLoaderCache(val imageLoader: MozartMediaImageLoader) {
    val cache: LruCache<String, CoverImage> by lazy {
        val cacheSize = cacheSize
        object : LruCache<String, CoverImage>(cacheSize) {
            override fun sizeOf(key: String?, value: CoverImage?): Int = value?.byteCount ?: 0
        }
    }

    open val cacheSize: Int = Math.min(MAX_ALBUM_ART_CACHE_SIZE,
                Math.min(Integer.MAX_VALUE.toLong(), Runtime.getRuntime().maxMemory() / 8).toInt())

    /**
     * return the cached bitmap from memory if available
     * do not make any network or disk request
     */
    open fun getCachedBitmapFromMemory(uri: String): CoverImage? = cache.get(uri)

    open fun loadCover(uri: String): Single<CoverImage> {
        return Single.defer {
            if (cache.get(uri) == null) {
                return@defer imageLoader.loadCover(uri)
                        .map { loadedBitmap ->
                            val coverBitmap = scaleBitmap(loadedBitmap,
                                    MAX_ART_WIDTH_PX, MAX_ART_HEIGHT_PX)
                            val iconBitmap = scaleBitmap(loadedBitmap,
                                    MAX_ART_WIDTH_ICON_PX, MAX_ART_HEIGHT_ICON_PX)
                            val coverImage = CoverImage(coverBitmap, iconBitmap)
                            cache.put(uri, coverImage)
                            coverImage
                        }
            }
            Single.just(cache.get(uri))
        }
    }

    /**
     * @see Application.onTrimMemory
     */
    open fun onTrimMemory(level: Int) {
        when (level) {
            Application.TRIM_MEMORY_RUNNING_LOW, Application.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.resize(1)
                cache.resize(cacheSize)
            }
        }
    }

    companion object {

        private val MAX_ALBUM_ART_CACHE_SIZE = 8 * 1024 * 1024

        private val MAX_ART_WIDTH_PX = 800
        private val MAX_ART_HEIGHT_PX = 480

        // Resolution reasonable for carrying around as an icon (generally in
        // MediaDescription.getIconBitmap). This should not be bigger than necessary, because
        // the MediaDescription object should be lightweight. If you set it too high and try to
        // serialize the MediaDescription, you may get FAILED BINDER TRANSACTION errors.
        private val MAX_ART_WIDTH_ICON_PX = 128
        private val MAX_ART_HEIGHT_ICON_PX = 128
    }
}
