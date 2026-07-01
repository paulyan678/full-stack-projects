package com.laioffer.spotify.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laioffer.spotify.data.model.Album
import com.laioffer.spotify.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PlayerUiState(
    val album: Album? = null,
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val currentMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
) {
    val isVisible: Boolean get() = album != null && song != null
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val selection = MutableStateFlow<Pair<Album, Song>?>(null)

    val uiState: StateFlow<PlayerUiState> = combine(selection, playbackController.state) { selected, playback ->
        PlayerUiState(
            album = selected?.first,
            song = selected?.second,
            isPlaying = playback.isPlaying,
            currentMs = playback.positionMs,
            durationMs = playback.durationMs,
            error = playback.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    fun load(song: Song, album: Album, autoplay: Boolean = true) {
        selection.value = album to song
        playbackController.load(song.src)
        if (autoplay) playbackController.play()
    }

    fun togglePlay() {
        if (uiState.value.isPlaying) playbackController.pause() else playbackController.play()
    }

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
}
