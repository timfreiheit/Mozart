package de.timfreiheit.mozart.utils

import android.os.Bundle

internal object WearHelper {

    /**
     * @see [https://developer.android.com/reference/android/support/wearable/media/MediaControlConstants.html]
     */
    private const val EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR = "android.support.wearable.media.extra.CUSTOM_ACTION_SHOW_ON_WEAR"
    private const val EXTRA_BACKGROUND_COLOR_FROM_THEME = "android.support.wearable.media.extra.BACKGROUND_COLOR_FROM_THEME"
    private const val EXTRA_RESERVE_SLOT_SKIP_TO_PREVIOUS = "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_PREVIOUS"
    private const val EXTRA_RESERVE_SLOT_SKIP_TO_NEXT = "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_NEXT"

    private const val WEAR_APP_PACKAGE_NAME = "com.google.android.wearable.app"

    fun isValidWearCompanionPackage(packageName: String): Boolean {
        return WEAR_APP_PACKAGE_NAME == packageName
    }

    fun setShowCustomActionOnWear(customActionExtras: Bundle, showOnWear: Boolean) {
        if (showOnWear) {
            customActionExtras.putBoolean(
                    EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR, true)
        } else {
            customActionExtras.remove(EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR)
        }
    }

    fun setUseBackgroundFromTheme(extras: Bundle, useBgFromTheme: Boolean) {
        if (useBgFromTheme) {
            extras.putBoolean(EXTRA_BACKGROUND_COLOR_FROM_THEME, true)
        } else {
            extras.remove(EXTRA_BACKGROUND_COLOR_FROM_THEME)
        }
    }

    fun setSlotReservationFlags(extras: Bundle, reserveSkipToNextSlot: Boolean,
                                reserveSkipToPrevSlot: Boolean) {
        if (reserveSkipToPrevSlot) {
            extras.putBoolean(EXTRA_RESERVE_SLOT_SKIP_TO_PREVIOUS, true)
        } else {
            extras.remove(EXTRA_RESERVE_SLOT_SKIP_TO_PREVIOUS)
        }
        if (reserveSkipToNextSlot) {
            extras.putBoolean(EXTRA_RESERVE_SLOT_SKIP_TO_NEXT, true)
        } else {
            extras.remove(EXTRA_RESERVE_SLOT_SKIP_TO_NEXT)
        }
    }
}
