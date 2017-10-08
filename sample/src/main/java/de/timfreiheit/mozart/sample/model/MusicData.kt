package de.timfreiheit.mozart.sample.model

data class MusicData(
        val music: List<Track>
)

data class Track (

        var id: String,

        var title: String,

        var album: String,

        var artist: String,

        var genre: String,

        var source: String,

        var image: String,

        var trackNumber: Int,

        var totalTrackCount: Int,

        var duration: Long,

        var site: String
)

