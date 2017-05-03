package de.timfreiheit.mozart.sample.ui.playlist;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import de.timfreiheit.mozart.sample.App;
import de.timfreiheit.mozart.sample.R;
import de.timfreiheit.mozart.sample.databinding.ItemPlaylistBinding;
import de.timfreiheit.mozart.sample.model.Track;
import de.timfreiheit.mozart.sample.ui.BindingViewHolder;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class PlaylistAdapter extends RecyclerView.Adapter<BindingViewHolder> {

    private final List<Track> data = new ArrayList<>();

    private final PublishSubject<Track> onItemClicked = PublishSubject.create();
    private MediaMetadataCompat currentMedia;
    private PlaybackStateCompat playbackState;

    @Override
    public BindingViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new BindingViewHolder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(BindingViewHolder holder, int position) {
        ItemPlaylistBinding binding = holder.getBinding();
        Track track = data.get(position);
        binding.title.setText(track.title);
        binding.artist.setText(track.artist);
        Picasso.with(App.instance()).load(track.image).into(binding.cover);

        binding.getRoot().setOnClickListener(v -> onItemClicked.onNext(track));

        configureMediaState(binding, track);
    }

    private void configureMediaState(ItemPlaylistBinding binding, Track track) {

        binding.playpause.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);

        if (playbackState == null || currentMedia == null || !currentMedia.getDescription().getMediaId().equals(track.id)) {
            binding.playpause.setImageResource(R.drawable.ic_play_arrow_black_24dp);
            return;
        }

        switch (playbackState.getState()) {
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_BUFFERING:
                binding.playpause.setVisibility(View.GONE);
                binding.progressBar.setVisibility(View.VISIBLE);
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                binding.playpause.setImageResource(R.drawable.ic_pause_black_24dp);
                break;
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
                binding.playpause.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                break;
            default:
                binding.playpause.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                break;
        }
    }

    public void setData(List<Track> playlists) {
        this.data.clear();
        this.data.addAll(playlists);
        notifyDataSetChanged();
    }

    public Observable<Track> onItemClicked() {
        return onItemClicked;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void setCurrentMedia(MediaMetadataCompat currentMedia, PlaybackStateCompat playbackState) {
        this.currentMedia = currentMedia;
        this.playbackState = playbackState;
        notifyDataSetChanged();
    }
}
