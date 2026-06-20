package com.laioffer.spotify.data.repository

import com.laioffer.spotify.data.local.FavoriteAlbumDao
import com.laioffer.spotify.data.local.toAlbum
import com.laioffer.spotify.data.local.toFavoriteEntity
import com.laioffer.spotify.data.model.Album
import com.laioffer.spotify.data.model.Playlist
import com.laioffer.spotify.data.model.Section
import com.laioffer.spotify.data.remote.SpotifyApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface FeedRepository {
    suspend fun feed(forceRefresh: Boolean = false): List<Section>
    suspend fun album(id: Int): Album
}

interface PlaylistRepository {
    suspend fun playlist(id: Int): Playlist
}

interface FavoritesRepository {
    fun favorites(): Flow<List<Album>>
    fun isFavorite(id: Int): Flow<Boolean>
    suspend fun setFavorite(album: Album, favorite: Boolean)
}

@Singleton
class NetworkFeedRepository @Inject constructor(
    private val api: SpotifyApi,
) : FeedRepository {
    private val cacheMutex = Mutex()
    @Volatile private var cachedFeed: List<Section>? = null

    override suspend fun feed(forceRefresh: Boolean): List<Section> {
        if (!forceRefresh) cachedFeed?.let { return it }
        return cacheMutex.withLock {
            if (!forceRefresh) cachedFeed?.let { return@withLock it }
            api.getHomeFeed().also { response -> cachedFeed = response }
        }
    }

    override suspend fun album(id: Int): Album = feed()
        .asSequence()
        .flatMap { it.albums.asSequence() }
        .firstOrNull { it.id == id }
        ?: throw NoSuchElementException("Album $id was not found in the feed")
}

@Singleton
class NetworkPlaylistRepository @Inject constructor(
    private val api: SpotifyApi,
) : PlaylistRepository {
    override suspend fun playlist(id: Int): Playlist = api.getPlaylist(id)
}

@Singleton
class RoomFavoritesRepository @Inject constructor(
    private val dao: FavoriteAlbumDao,
) : FavoritesRepository {
    override fun favorites(): Flow<List<Album>> = dao.observeAll().map { rows -> rows.map { it.toAlbum() } }

    override fun isFavorite(id: Int): Flow<Boolean> = dao.observeIsFavorite(id)

    override suspend fun setFavorite(album: Album, favorite: Boolean) {
        if (favorite) dao.upsert(album.toFavoriteEntity()) else dao.delete(album.toFavoriteEntity())
    }
}
