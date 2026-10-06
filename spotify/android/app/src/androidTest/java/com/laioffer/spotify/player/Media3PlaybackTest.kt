package com.laioffer.spotify.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real Media3 decoding, position updates, pause, and seek on an Android runtime. */
@RunWith(AndroidJUnit4::class)
class Media3PlaybackTest {
    @Test
    fun generatedWavPlaysAndSeeks() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wav = File.createTempFile("playback-", ".wav", context.cacheDir)
        wav.writeBytes(silentWav())
        withContext(Dispatchers.Main) {
            val controller = Media3PlaybackController(ExoPlayer.Builder(context).build())
            try {
                controller.load(wav.toURI().toString())
                controller.play()
                val playing = withTimeout(15_000) {
                    controller.state.first { it.isPlaying && it.positionMs > 0 && it.durationMs >= 4_900 }
                }
                assertTrue(playing.error == null)
                controller.pause()
                withTimeout(5_000) { controller.state.first { !it.isPlaying } }
                controller.seekTo(2_000)
                // Wait beyond the controller's optimistic seek update and position poll.
                delay(750)
                assertTrue(controller.state.value.positionMs in 1_900L..2_100L)
                controller.play()
                withTimeout(5_000) { controller.state.first { it.isPlaying && it.positionMs > 2_500 } }
            } finally { controller.close() }
        }
        wav.delete()
    }

    private fun silentWav(): ByteArray {
        val sampleRate = 22_050
        val byteCount = sampleRate * 5 * 2
        return ByteBuffer.allocate(44 + byteCount).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + byteCount); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(byteCount)
            put(ByteArray(byteCount))
        }.array()
    }
}
