package com.laioffer.spotify.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteAlbumEntity::class], version = 1, exportSchema = true)
abstract class SpotifyDatabase : RoomDatabase() {
    abstract fun favoriteAlbumDao(): FavoriteAlbumDao
}
