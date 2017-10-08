package de.timfreiheit.mozart.sample

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

import de.timfreiheit.mozart.Mozart
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        app = this

        Timber.plant(Timber.DebugTree())
        ImageLoader.init(this)

        Mozart.init(this)
    }



    companion object {

        private lateinit var app: App

        fun instance(): App {
            return app
        }
    }
}
