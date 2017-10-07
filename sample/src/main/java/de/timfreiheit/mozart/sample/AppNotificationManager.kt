package de.timfreiheit.mozart.sample

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object AppNotificationManager {

    val mediaNotificationChannel = "CHANNEL_NAME"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(mediaNotificationChannel) == null) {
            val channelId = mediaNotificationChannel
            val channelName = "MEDIA_CHANNEL_NAME"
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(channelId, channelName, importance).apply {
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

}
