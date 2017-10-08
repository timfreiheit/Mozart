package de.timfreiheit.mozart.sample.player

import android.support.v4.media.MediaMetadataCompat
import com.google.gson.Gson
import de.timfreiheit.mozart.model.MozartMediaProvider
import de.timfreiheit.mozart.model.MozartMetadataBuilder
import de.timfreiheit.mozart.model.Playlist
import de.timfreiheit.mozart.sample.App
import de.timfreiheit.mozart.sample.model.MusicData
import de.timfreiheit.mozart.sample.model.Track
import io.reactivex.Single
import java.io.InputStreamReader
import java.util.*

object MediaProvider : MozartMediaProvider() {

    val data: Single<List<Track>> = Single.fromCallable {
        val gson = Gson()
        gson.fromJson(
                InputStreamReader(App.instance().assets.open("music.json")), MusicData::class.java
        ).music
    }.cache()

    override fun getMediaById(mediaId: String): Single<MediaMetadataCompat> {
        return data.flatMap { tracks ->
            for (track in tracks) {
                if (track.id == mediaId) {
                    return@flatMap Single.just(metadataFromTrack(track))
                }
            }
            Single.error<MediaMetadataCompat>(Exception("media id not found"))
        }
    }

    fun getTracksForPlaylist(playlistId: String): Single<List<Track>> {

        val split = playlistId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (split.size != 2) {
            return Single.error(Exception("invalid playlist"))
        }

        return when (split[0]) {
            "genre" -> data.map { tracks ->
                val genre = split[1]

                tracks.filter { it.genre == genre }
            }
            else -> Single.error(Exception("invalid playlist"))
        }
    }

    override fun getPlaylistById(playlistId: String): Single<Playlist> {

        val split = playlistId.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (split.size != 2) {
            return Single.error(Exception("invalid playlist"))
        }

        return when (split[0]) {
            "genre" -> data.map { tracks ->
                val genre = split[1]

                val playlistTracks = tracks
                        .filter { it.genre == genre }
                        .map { metadataFromTrack(it) }
                Playlist(playlistId, split[1], playlistTracks)
            }
            else -> Single.error(Exception("invalid playlist"))
        }

    }

    private fun metadataFromTrack(track: Track): MediaMetadataCompat {
        return MozartMetadataBuilder()
                .mediaId(track.id)
                .contentUri(track.source)
                .artist(track.artist)
                .album(track.album)
                .duration(track.duration * 1000)
                .genre(track.genre)
                .albumArtUri(track.image)
                .title(track.title)
                .trackNumber(track.trackNumber.toLong())
                .totalTrackCount(track.totalTrackCount.toLong())
                .contentType("audio/mp3")
                .build()
    }
}
