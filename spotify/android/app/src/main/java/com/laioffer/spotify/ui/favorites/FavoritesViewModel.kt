package com.laioffer.spotify.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laioffer.spotify.data.model.Album
import com.laioffer.spotify.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    repository: FavoritesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.favorites()
                .catch { error ->
                    mutableState.value = FavoritesUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load favorites",
                    )
                }
                .collect { albums ->
                    mutableState.value = FavoritesUiState(albums = albums, isLoading = false)
                }
        }
    }
}
