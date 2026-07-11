package com.laioffer.spotify.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.laioffer.spotify.data.model.Album

@Entity(tableName = "favorite_albums")
data class FavoriteAlbumEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val year: String,
    val cover: String,
    val artists: String,
    val description: String,
)

fun Album.toFavoriteEntity() = FavoriteAlbumEntity(id, name, year, cover, artists, description)

fun FavoriteAlbumEntity.toAlbum() = Album(id, name, year, cover, artists, description)
