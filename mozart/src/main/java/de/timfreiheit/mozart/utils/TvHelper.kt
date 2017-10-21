package de.timfreiheit.mozart.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

import timber.log.Timber

internal object TvHelper {

    /**
     * Returns true when running Android TV
     *
     * @param c Context to detect UI Mode.
     * @return true when device is running in tv mode, false otherwise.
     */
    fun isTvUiMode(c: Context): Boolean {
        val uiModeManager = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            Timber.d("Running in TV mode")
            return true
        } else {
            Timber.d("Running on a non-TV mode")
            return false
        }
    }
}
