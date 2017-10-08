package de.timfreiheit.mozart.sample.player

import de.timfreiheit.mozart.MozartMediaNotificationManager
import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.sample.R


class MediaNotificationManager(service: MozartMusicService) : MozartMediaNotificationManager(service) {

    override val notificationIcon: Int
        get() = R.mipmap.ic_launcher_round

}
