package de.timfreiheit.mozart.sample.ui.browser

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import de.timfreiheit.mozart.sample.databinding.ActivityMainBinding
import de.timfreiheit.mozart.sample.player.MediaProvider
import de.timfreiheit.mozart.sample.ui.BaseActivity
import de.timfreiheit.mozart.sample.ui.playlist.PlaylistActivity
import io.reactivex.Observable

class PlaylistBrowserActivity : BaseActivity() {

    private val adapter: PlaylistBrowserAdapter = PlaylistBrowserAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playlists.layoutManager = LinearLayoutManager(this)
        binding.playlists.adapter = adapter

        adapter.onItemClicked()
                .subscribe { playlist ->
                    val intent = PlaylistActivity.newIntent(this, "genre/" + playlist)
                    startActivity(intent)
                }

        loadPlaylists()
    }

    private fun loadPlaylists() {
        MediaProvider.data
                .flatMapObservable { Observable.fromIterable(it) }
                .groupBy { track -> track.genre }
                .map { it.key ?: "" }
                .toList()
                .subscribe { playlists -> adapter.setData(playlists) }
    }
}
