package de.timfreiheit.mozart

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import timber.log.Timber

private const val ACTION_PAUSE = "de.timfreiheit.mozart.pause"
private const val ACTION_PLAY = "de.timfreiheit.mozart.play"
private const val ACTION_PREV = "de.timfreiheit.mozart.prev"
private const val ACTION_NEXT = "de.timfreiheit.mozart.next"
private const val ACTION_STOP = "de.timfreiheit.mozart.stop"
private const val ACTION_STOP_CASTING = "de.timfreiheit.mozart.stop_cast"

class MozartMediaNotificationActionReceiver(
        val context: Context,
        val listener: ActionListener
): BroadcastReceiver() {

    interface ActionListener {
        fun pause()
        fun play()
        fun skipToNext()
        fun skipToPrevious()
        fun stop()
        fun stopCasting()
    }

    val requestCode = 100

    val pauseIntent: PendingIntent
    val playIntent: PendingIntent
    val previousIntent: PendingIntent
    val nextIntent: PendingIntent
    val stopIntent: PendingIntent
    val stopCastIntent: PendingIntent

    val filter = IntentFilter().apply {
        addAction(ACTION_NEXT)
        addAction(ACTION_PAUSE)
        addAction(ACTION_PLAY)
        addAction(ACTION_PREV)
        addAction(ACTION_STOP)
        addAction(ACTION_STOP_CASTING)
    }

    init {

        val pkg = context.packageName
        pauseIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(ACTION_PAUSE).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        )

        playIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(ACTION_PLAY).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        )

        previousIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(ACTION_PREV).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        )

        nextIntent = PendingIntent.getBroadcast(
                context, requestCode,
                Intent(ACTION_NEXT).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        )

        stopIntent = PendingIntent.getBroadcast(
                context, requestCode,
                Intent(ACTION_STOP).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        )

        stopCastIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(ACTION_STOP_CASTING).setPackage(pkg),
                PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.d("Received intent with action " + action)
        when (action) {
            ACTION_PAUSE -> listener.pause()
            ACTION_PLAY -> listener.play()
            ACTION_NEXT -> listener.skipToNext()
            ACTION_PREV -> listener.skipToPrevious()
            ACTION_STOP -> listener.stop()
            ACTION_STOP_CASTING -> listener.stopCasting()
            else -> Timber.w("Unknown intent ignored. Action %s", action)
        }
    }

}