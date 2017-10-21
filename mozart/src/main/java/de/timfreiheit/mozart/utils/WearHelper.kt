package de.timfreiheit.mozart.utils

import android.os.Bundle
import android.support.wearable.media.MediaControlConstants

internal object WearHelper {
    private val WEAR_APP_PACKAGE_NAME = "com.google.android.wearable.app"

    fun isValidWearCompanionPackage(packageName: String): Boolean {
        return WEAR_APP_PACKAGE_NAME == packageName
    }

    fun setShowCustomActionOnWear(customActionExtras: Bundle, showOnWear: Boolean) {
        if (showOnWear) {
            customActionExtras.putBoolean(
                    MediaControlConstants.EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR, true)
        } else {
            customActionExtras.remove(MediaControlConstants.EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR)
        }
    }

    fun setUseBackgroundFromTheme(extras: Bundle, useBgFromTheme: Boolean) {
        if (useBgFromTheme) {
            extras.putBoolean(MediaControlConstants.EXTRA_BACKGROUND_COLOR_FROM_THEME, true)
        } else {
            extras.remove(MediaControlConstants.EXTRA_BACKGROUND_COLOR_FROM_THEME)
        }
    }

    fun setSlotReservationFlags(extras: Bundle, reserveSkipToNextSlot: Boolean,
                                reserveSkipToPrevSlot: Boolean) {
        if (reserveSkipToPrevSlot) {
            extras.putBoolean(MediaControlConstants.EXTRA_RESERVE_SLOT_SKIP_TO_PREVIOUS, true)
        } else {
            extras.remove(MediaControlConstants.EXTRA_RESERVE_SLOT_SKIP_TO_PREVIOUS)
        }
        if (reserveSkipToNextSlot) {
            extras.putBoolean(MediaControlConstants.EXTRA_RESERVE_SLOT_SKIP_TO_NEXT, true)
        } else {
            extras.remove(MediaControlConstants.EXTRA_RESERVE_SLOT_SKIP_TO_NEXT)
        }
    }
}
