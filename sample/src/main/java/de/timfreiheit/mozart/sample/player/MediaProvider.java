package de.timfreiheit.mozart.sample.player;

import android.support.v4.media.MediaMetadataCompat;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.timfreiheit.mozart.model.MozartMediaMetadata;
import de.timfreiheit.mozart.model.MozartMediaProvider;
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
                        new InputStreamReader(App.instance().getAssets().open("music.json")), MusicData.class
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
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id)
                .putString(MozartMediaMetadata.META_DATA_CONTENT_URI, track.source)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, track.genre)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, track.image)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, track.trackNumber)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, track.totalTrackCount)
                .putString(MozartMediaMetadata.META_DATA_CONTENT_TYPE, "audio/mp3")
                .build();
    }

}
