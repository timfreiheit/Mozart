package de.timfreiheit.mozart;

import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class MozartPlayCommand implements Parcelable {

    @Nullable
    public abstract String mediaId();

    @Nullable
    public abstract String playlistId();

    @Nullable
    abstract Integer startPlaylistPosition();

    public int playlistPosition() {
        if (startPlaylistPosition() == null) {
            return 0;
        }
        //noinspection ConstantConditions
        return startPlaylistPosition();
    }

    @Nullable
    abstract Long startMediaPlaybackPosition();

    public long mediaPlaybackPosition() {

        if (startMediaPlaybackPosition() == null) {
            return 0;
        }
        //noinspection ConstantConditions
        return startMediaPlaybackPosition();
    }

    static Builder builder() {
        return new AutoValue_MozartPlayCommand.Builder();
    }

    public static Builder playMedia(String mediaId) {
        return builder().mediaId(mediaId);
    }

    public static Builder playPlaylist(String playlistId) {
        return builder().playlistId(playlistId);
    }

    @AutoValue.Builder
    public static abstract class Builder {

        public abstract Builder mediaId(String value);

        public abstract Builder playlistId(String value);

        public abstract Builder startPlaylistPosition(Integer value);

        public abstract Builder startMediaPlaybackPosition(Long value);

        public abstract MozartPlayCommand build();
    }

}
