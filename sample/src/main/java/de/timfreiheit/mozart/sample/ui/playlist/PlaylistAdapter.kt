package de.timfreiheit.mozart.sample.ui.playlist

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.squareup.picasso.Picasso

import java.util.ArrayList

import de.timfreiheit.mozart.sample.App
import de.timfreiheit.mozart.sample.R
import de.timfreiheit.mozart.sample.databinding.ItemPlaylistBinding
import de.timfreiheit.mozart.sample.model.Track
import de.timfreiheit.mozart.sample.ui.BindingViewHolder
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class PlaylistAdapter : RecyclerView.Adapter<BindingViewHolder>() {

    private val data = ArrayList<Track>()

    private val onItemClicked = PublishSubject.create<Track>()
    private var currentMedia: MediaMetadataCompat? = null
    private var playbackState: PlaybackStateCompat? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        return BindingViewHolder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        val binding = holder.getBinding<ItemPlaylistBinding>()
        val track = data[position]
        binding.title.text = track.title
        binding.artist.text = track.artist
        Picasso.with(App.instance()).load(track.image).into(binding.cover)

        binding.root.setOnClickListener { v -> onItemClicked.onNext(track) }

        configureMediaState(binding, track)
    }

    private fun configureMediaState(binding: ItemPlaylistBinding, track: Track) {

        binding.playpause.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE

        val playbackState = playbackState
        if (playbackState == null || currentMedia == null || currentMedia!!.description.mediaId != track.id) {
            binding.playpause.setImageResource(R.drawable.ic_play_arrow_black_24dp)
            return
        }

        when (playbackState.state) {
            PlaybackStateCompat.STATE_CONNECTING, PlaybackStateCompat.STATE_BUFFERING -> {
                binding.playpause.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
            }
            PlaybackStateCompat.STATE_PLAYING -> binding.playpause.setImageResource(R.drawable.ic_pause_black_24dp)
            PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> binding.playpause.setImageResource(R.drawable.ic_play_arrow_black_24dp)
            else -> binding.playpause.setImageResource(R.drawable.ic_play_arrow_black_24dp)
        }
    }

    fun setData(playlists: List<Track>) {
        this.data.clear()
        this.data.addAll(playlists)
        notifyDataSetChanged()
    }

    fun onItemClicked(): Observable<Track> = onItemClicked

    override fun getItemCount(): Int = data.size

    fun setCurrentMedia(currentMedia: MediaMetadataCompat?, playbackState: PlaybackStateCompat?) {
        this.currentMedia = currentMedia
        this.playbackState = playbackState
        notifyDataSetChanged()
    }
}
