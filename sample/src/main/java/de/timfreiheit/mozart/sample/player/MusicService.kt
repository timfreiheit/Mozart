package de.timfreiheit.mozart.sample.player

import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.exoplayer.ExoplayerPlayback
import de.timfreiheit.mozart.model.MozartMediaProvider
import de.timfreiheit.mozart.model.image.MozartMediaImageLoader
import de.timfreiheit.mozart.playback.local.MediaPlayerPlayback
import de.timfreiheit.mozart.sample.ImageLoader

class MusicService : MozartMusicService() {

    override val mediaProvider: MozartMediaProvider
        get() = MediaProvider

    override val imageLoader: MozartMediaImageLoader
        get() = ImageLoader

    override fun createLocalPlayback() = ExoplayerPlayback(this)
}
