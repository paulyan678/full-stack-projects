package com.laioffer.spotify.data.model

import com.google.gson.annotations.SerializedName

data class Album(
    val id: Int,
    @SerializedName("album") val name: String,
    val year: String,
    val cover: String,
    val artists: String,
    val description: String,
)

data class Section(
    @SerializedName("section_title") val sectionTitle: String,
    val albums: List<Album>,
)

data class Song(
    val name: String,
    val lyric: String,
    val src: String,
    val length: String,
)

data class Playlist(
    val id: Int,
    val songs: List<Song>,
)
