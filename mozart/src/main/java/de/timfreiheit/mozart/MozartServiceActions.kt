package de.timfreiheit.mozart

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.support.v4.media.MediaBrowserServiceCompat

internal object MozartServiceActions {

    private fun getMusicService(context: Context): Class<out Service>? {
        Mozart.init(context)

        val intent = Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE)
        intent.`package` = context.packageName

        val handlers = context.packageManager.queryIntentServices(intent, 0)
        if (handlers == null || handlers.size != 1) {
            throw IllegalStateException("no music service registered")
        }

        val serviceName = handlers[0].serviceInfo.name

        var cls: Class<out Service>? = null
        try {

            cls = Class.forName(serviceName) as Class<out Service>
        } catch (e: ClassNotFoundException) {
            // do nothing
        }

        return cls
    }

    fun startIdle(context: Context): Intent {
        return Intent(context, getMusicService(context))
    }


    fun pause(context: Context): Intent {
        val intent = Intent(context, getMusicService(context))
        intent.action = MozartMusicService.ACTION_CMD
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PAUSE)
        return intent
    }

    fun stopCasting(context: Context): Intent {
        val intent = Intent(context, getMusicService(context))
        intent.action = MozartMusicService.ACTION_CMD
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_STOP_CASTING)
        return intent
    }

    fun executeCommand(context: Context, command: MozartPlayCommand): Intent {
        val intent = Intent(context, getMusicService(context))
        intent.action = MozartMusicService.ACTION_CMD
        intent.putExtra(MozartMusicService.CMD_NAME, MozartMusicService.CMD_PLAY)
        intent.putExtra(MozartMusicService.ARGS_START_COMMAND, command)
        return intent
    }
}
