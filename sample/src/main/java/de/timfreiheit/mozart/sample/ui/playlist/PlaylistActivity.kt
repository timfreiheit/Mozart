package de.timfreiheit.mozart.sample.ui.playlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.widget.LinearLayoutManager
import de.timfreiheit.mozart.Mozart
import de.timfreiheit.mozart.MozartPlayCommand
import de.timfreiheit.mozart.sample.databinding.ActivityMainBinding
import de.timfreiheit.mozart.sample.player.MediaProvider
import de.timfreiheit.mozart.sample.ui.BaseActivity
import de.timfreiheit.mozart.utils.RxMediaController
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

class PlaylistActivity : BaseActivity() {


    private lateinit var playlistId: String
    private val adapter: PlaylistAdapter = PlaylistAdapter()

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistId = intent.getStringExtra(EXTRA_PLAYLIST)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playlists.layoutManager = LinearLayoutManager(this)
        binding.playlists.adapter = adapter

        adapter.onItemClicked().subscribe { (id) ->

            val playCommand = MozartPlayCommand.playPlaylist(playlistId)
                    .mediaId(id)
                    .build()

            val mediaController = Mozart.mediaController
            if (mediaController == null || mediaController.metadata == null || mediaController.metadata.description.mediaId != id) {
                Mozart.executeCommand(playCommand)
            } else {
                when (mediaController.playbackState.state) {
                    PlaybackStateCompat.STATE_PLAYING -> mediaController.transportControls.pause()
                    PlaybackStateCompat.STATE_PAUSED -> mediaController.transportControls.play()
                    PlaybackStateCompat.STATE_STOPPED -> Mozart.executeCommand(playCommand)
                }
            }
        }
        loadPlaylist()
    }

    override fun onStart() {
        super.onStart()
        compositeDisposable.add(Mozart.mediaController()
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap { RxMediaController.playbackState(it) }
                .subscribe({ playbackState ->
                    val mediaController = Mozart.mediaController
                    if (mediaController == null) {
                        adapter.setCurrentMedia(null, null)
                    } else {
                        adapter.setCurrentMedia(mediaController.metadata, playbackState.orNull())
                    }
                }, { it.printStackTrace() }))
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
    }

    private fun loadPlaylist() {
        MediaProvider.getTracksForPlaylist(playlistId)
                .subscribe({ playlist -> adapter.setData(playlist) }, { })
    }

    companion object {

        private val EXTRA_PLAYLIST = "EXTRA_PLAYLIST"

        fun newIntent(context: Context, playlist: String): Intent {
            val intent = Intent(context, PlaylistActivity::class.java)
            intent.putExtra(EXTRA_PLAYLIST, playlist)
            return intent
        }
    }

}
