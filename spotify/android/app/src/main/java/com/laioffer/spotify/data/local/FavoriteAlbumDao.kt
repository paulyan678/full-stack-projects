package com.laioffer.spotify.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteAlbumDao {
    @Query("SELECT * FROM favorite_albums ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FavoriteAlbumEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_albums WHERE id = :albumId)")
    fun observeIsFavorite(albumId: Int): Flow<Boolean>

    @Upsert
    suspend fun upsert(album: FavoriteAlbumEntity)

    @Delete
    suspend fun delete(album: FavoriteAlbumEntity)
}
