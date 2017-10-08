package de.timfreiheit.mozart.utils

import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

/**
 * Get a color value from a theme attribute.
 * @param context used for getting the color.
 * @param attribute theme attribute.
 * @param defaultColor default to use.
 * @return color value
 */
internal fun Context.getThemeColor(attribute: Int, defaultColor: Int): Int {
    var themeColor = 0
    val packageName = packageName
    try {
        val packageContext = createPackageContext(packageName, 0)
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageContext.setTheme(applicationInfo.theme)
        val theme = packageContext.theme
        val ta = theme.obtainStyledAttributes(intArrayOf(attribute))
        themeColor = ta.getColor(0, defaultColor)
        ta.recycle()
    } catch (e: Throwable) {
        Timber.e(e)
    }

    return themeColor
}
