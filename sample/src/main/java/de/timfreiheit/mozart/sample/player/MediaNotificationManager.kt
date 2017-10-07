package de.timfreiheit.mozart.sample.player

import de.timfreiheit.mozart.MozartMediaNotificationManager
import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.sample.AppNotificationManager
import de.timfreiheit.mozart.sample.R


class MediaNotificationManager constructor(val service: MozartMusicService) : MozartMediaNotificationManager(service) {

    override val notificationChannelId: String = AppNotificationManager.mediaNotificationChannel

    override val notificationIcon: Int
        get() = R.mipmap.ic_launcher_round

}
