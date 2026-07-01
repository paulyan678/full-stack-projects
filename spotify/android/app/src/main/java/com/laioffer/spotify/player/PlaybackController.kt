package com.laioffer.spotify.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

interface PlaybackController {
    val state: StateFlow<PlaybackSnapshot>
    fun load(url: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
}

@Singleton
class Media3PlaybackController @Inject constructor(
    private val player: ExoPlayer,
) : PlaybackController, Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackSnapshot())
    override val state: StateFlow<PlaybackSnapshot> = mutableState.asStateFlow()

    init {
        player.addListener(this)
        scope.launch {
            while (isActive) {
                updatePosition()
                delay(250)
            }
        }
    }

    override fun load(url: String) {
        mutableState.value = PlaybackSnapshot()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }
    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        val upperBound = state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        val bounded = positionMs.coerceIn(0, upperBound)
        mutableState.value = mutableState.value.copy(positionMs = bounded)
        player.seekTo(bounded)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        mutableState.value = mutableState.value.copy(isPlaying = isPlaying)
    }

    override fun onPlaybackStateChanged(playbackState: Int) = updatePosition()

    override fun onPlayerError(error: PlaybackException) {
        mutableState.value = mutableState.value.copy(
            isPlaying = false,
            error = error.localizedMessage ?: "Playback failed",
        )
    }

    private fun updatePosition() {
        val duration = player.duration.takeIf { it > 0 } ?: 0
        mutableState.value = mutableState.value.copy(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
        )
    }
}
