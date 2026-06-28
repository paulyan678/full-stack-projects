package com.laioffer.spotify.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laioffer.spotify.data.model.Album
import com.laioffer.spotify.data.model.Song
import com.laioffer.spotify.data.repository.FavoritesRepository
import com.laioffer.spotify.data.repository.FeedRepository
import com.laioffer.spotify.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    private val albumId: Int = checkNotNull(savedStateHandle["albumId"]) { "albumId navigation argument is required" }
    private val mutableState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = mutableState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            favoritesRepository.isFavorite(albumId).collect { favorite ->
                mutableState.update { it.copy(isFavorite = favorite) }
            }
        }
    }

    fun retry() = load()

    fun toggleFavorite() {
        val snapshot = mutableState.value
        val album = snapshot.album ?: return
        viewModelScope.launch {
            favoritesRepository.setFavorite(album, !snapshot.isFavorite)
        }
    }

    private fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            try {
                val (album, playlist) = coroutineScope {
                    val album = async { feedRepository.album(albumId) }
                    val playlist = async { playlistRepository.playlist(albumId) }
                    album.await() to playlist.await()
                }
                check(playlist.id == album.id) { "Playlist response did not match album ${album.id}" }
                mutableState.update { it.copy(album = album, songs = playlist.songs, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(isLoading = false, error = error.message ?: "Unable to load this playlist")
                }
            }
        }
    }
}
