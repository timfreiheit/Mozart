package de.timfreiheit.mozart.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;

import java.util.List;

public class Playlist {

    private final String id;

    private final String title;

    private final List<MediaMetadataCompat> playlist;

    public Playlist(String id, String title, List<MediaMetadataCompat> playlist) {
        this.id = id;
        this.title = title;
        this.playlist = playlist;
    }

    @Nullable
    public String getId() {
        return id;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @NonNull
    public List<MediaMetadataCompat> getPlaylist() {
        return playlist;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Playlist)) return false;

        Playlist playlist = (Playlist) o;

        return title != null ? title.equals(playlist.title) : playlist.title == null;

    }

    @Override
    public int hashCode() {
        return title != null ? title.hashCode() : 0;
    }

    /**
     * @return -1 when media not found
     */
    public int getPositionByMediaId(String mediaId) {
        List<MediaMetadataCompat> playlist1 = getPlaylist();
        for (int i = 0; i < playlist1.size(); i++) {
            MediaMetadataCompat mediaMetadataCompat = playlist1.get(i);
            String id = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            if (id != null && id.equals(mediaId)) {
                return i;
            }
        }
        return -1;
    }
}
