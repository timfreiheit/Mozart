package de.timfreiheit.mozart.sample.player;

import android.support.v4.media.MediaMetadataCompat;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.timfreiheit.mozart.model.MozartMediaProvider;
import de.timfreiheit.mozart.model.MozartMetadataBuilder;
import de.timfreiheit.mozart.model.Playlist;
import de.timfreiheit.mozart.sample.App;
import de.timfreiheit.mozart.sample.model.MusicData;
import de.timfreiheit.mozart.sample.model.Track;
import io.reactivex.Single;

public class MediaProvider extends MozartMediaProvider {

    private static final MediaProvider INSTANCE = new MediaProvider();

    private Single<List<Track>> data;


    public static MediaProvider getInstance() {
        return INSTANCE;
    }

    public Single<List<Track>> loadData() {
        if (data == null) {
            data = Single.<List<Track>>fromCallable(() -> {
                Gson gson = new Gson();
                return gson.fromJson(
                        new InputStreamReader(App.Companion.instance().getAssets().open("music.json")), MusicData.class
                ).music;
            }).cache();
        }
        return data;
    }

    @Override
    public Single<MediaMetadataCompat> getMediaById(String mediaId) {
        return loadData().flatMap(tracks -> {
            for (Track track : tracks) {
                if (track.id.equals(mediaId)) {
                    return Single.just(metadataFromTrack(track));
                }
            }
            return Single.error(new Exception("media id not found"));
        });
    }

    public Single<List<Track>> getTracksForPlaylist(String playlistId) {

        String[] split = playlistId.split("/");
        if (split.length != 2) {
            return Single.error(new Exception("invalid playlist"));
        }

        switch (split[0]) {
            case "genre":
                return loadData().map(tracks -> {
                    String genre = split[1];

                    List<Track> playlistTracks = new ArrayList<>();
                    for (Track track : tracks) {
                        if (track.genre.equals(genre)) {
                            playlistTracks.add(track);
                        }
                    }
                    return playlistTracks;
                });
            default:
                return Single.error(new Exception("invalid playlist"));
        }
    }

    @Override
    public Single<Playlist> getPlaylistById(String playlistId) {

        String[] split = playlistId.split("/");
        if (split.length != 2) {
            return Single.error(new Exception("invalid playlist"));
        }

        switch (split[0]) {
            case "genre":
                return loadData().map(tracks -> {
                    String genre = split[1];

                    List<MediaMetadataCompat> playlistTracks = new ArrayList<>();
                    for (Track track : tracks) {
                        if (track.genre.equals(genre)) {
                            playlistTracks.add(metadataFromTrack(track));
                        }
                    }
                    return new Playlist(playlistId, split[1], playlistTracks);
                });
            default:
                return Single.error(new Exception("invalid playlist"));
        }

    }

    private static MediaMetadataCompat metadataFromTrack(Track track) {
        return new MozartMetadataBuilder()
                .mediaId(track.id)
                .contentUri(track.source)
                .artist(track.artist)
                .album(track.album)
                .duration(track.duration * 1000)
                .genre(track.genre)
                .albumArtUri(track.image)
                .title(track.title)
                .trackNumber(track.trackNumber)
                .totalTrackCount(track.totalTrackCount)
                .contentType("audio/mp3")
                .build();
    }

}
