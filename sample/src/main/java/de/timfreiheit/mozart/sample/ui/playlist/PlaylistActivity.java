package de.timfreiheit.mozart.sample.ui.playlist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;

import de.timfreiheit.mozart.Mozart;
import de.timfreiheit.mozart.sample.databinding.ActivityMainBinding;
import de.timfreiheit.mozart.sample.player.MediaProvider;
import de.timfreiheit.mozart.sample.ui.BaseActivity;
import de.timfreiheit.mozart.utils.RxMediaController;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class PlaylistActivity extends BaseActivity {

    private static final String EXTRA_PLAYLIST = "EXTRA_PLAYLIST";


    private String playlistId;
    private PlaylistAdapter adapter;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public static Intent newIntent(Context context, String playlist) {
        Intent intent = new Intent(context, PlaylistActivity.class);
        intent.putExtra(EXTRA_PLAYLIST, playlist);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = getIntent().getStringExtra(EXTRA_PLAYLIST);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new PlaylistAdapter();
        binding.playlists.setLayoutManager(new LinearLayoutManager(this));
        binding.playlists.setAdapter(adapter);

        adapter.onItemClicked().subscribe(track -> {
            MediaControllerCompat mediaController = Mozart.get(this).getMediaController();
            if (mediaController == null || mediaController.getMetadata() == null || !mediaController.getMetadata().getDescription().getMediaId().equals(track.id)) {
                Mozart.get(this).playMedia(playlistId, track.id);
            } else {
                switch (mediaController.getPlaybackState().getState()) {
                    case PlaybackStateCompat.STATE_PLAYING:
                        mediaController.getTransportControls().pause();
                        break;
                    case PlaybackStateCompat.STATE_PAUSED:
                        mediaController.getTransportControls().play();
                        break;
                    case PlaybackStateCompat.STATE_STOPPED:
                        Mozart.get(this).playMedia(playlistId, track.id);
                        break;
                }
            }
        });
        loadPlaylist();
    }

    @Override
    protected void onStart() {
        super.onStart();
        compositeDisposable.add(Mozart.get(this).mediaController()
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(RxMediaController::playbackState)
                .subscribe(playbackState -> {
                    MediaControllerCompat mediaController = Mozart.get(this).getMediaController();
                    if (mediaController == null) {
                        adapter.setCurrentMedia(null, null);
                    } else {
                        adapter.setCurrentMedia(mediaController.getMetadata(), playbackState);
                    }
                }, Throwable::printStackTrace));
    }

    @Override
    protected void onStop() {
        super.onStop();
        compositeDisposable.clear();
    }

    private void loadPlaylist() {
        MediaProvider.getInstance().getTracksForPlaylist(playlistId)
                .subscribe(playlist -> adapter.setData(playlist), throwable -> {
                });
    }

}
