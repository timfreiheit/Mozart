package de.timfreiheit.mozart.sample.ui.browser;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;

import de.timfreiheit.mozart.sample.databinding.ActivityMainBinding;
import de.timfreiheit.mozart.sample.player.MediaProvider;
import de.timfreiheit.mozart.sample.ui.BaseActivity;
import de.timfreiheit.mozart.sample.ui.playlist.PlaylistActivity;
import io.reactivex.Observable;
import io.reactivex.observables.GroupedObservable;

public class PlaylistBrowserActivity extends BaseActivity {

    private PlaylistBrowserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new PlaylistBrowserAdapter();
        binding.playlists.setLayoutManager(new LinearLayoutManager(this));
        binding.playlists.setAdapter(adapter);

        adapter.onItemClicked()
                .subscribe(playlist -> {
                    Intent intent = PlaylistActivity.newIntent(this, "genre/" + playlist);
                    startActivity(intent);
                });

        loadPlaylists();
    }

    private void loadPlaylists() {
        MediaProvider.getInstance().loadData()
                .flatMapObservable(Observable::fromIterable)
                .groupBy(track -> track.genre)
                .map(GroupedObservable::getKey)
                .toList()
                .subscribe(playlists -> {
                    adapter.setData(playlists);
                });
    }
}
