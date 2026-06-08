package com.laioffer.spotify.backend

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

/** Creates a deterministic, royalty-free five-second WAV tone for local playback. */
object AudioFixture {
    private const val sampleRate = 22_050
    private const val seconds = 5
    private const val bitsPerSample = 16
    private val cache = ConcurrentHashMap<String, ByteArray>()

    fun wavFor(name: String): ByteArray = cache.computeIfAbsent(name, ::generate)

    private fun generate(name: String): ByteArray {
        val frequency = 180 + (name.hashCode().toUInt().toLong() % 260).toInt()
        val samples = sampleRate * seconds
        val pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { index ->
            val fade = when {
                index < sampleRate / 10 -> index.toDouble() / (sampleRate / 10)
                index > samples - sampleRate / 5 -> (samples - index).toDouble() / (sampleRate / 5)
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val fundamental = sin(2.0 * PI * frequency * index / sampleRate)
            val harmonic = 0.25 * sin(2.0 * PI * frequency * 1.5 * index / sampleRate)
            pcm.putShort(((fundamental + harmonic) * fade * Short.MAX_VALUE * 0.22).toInt().toShort())
        }

        val data = pcm.array()
        return ByteArrayOutputStream(44 + data.size).apply {
            writeAscii("RIFF")
            writeIntLe(36 + data.size)
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeIntLe(16)
            writeShortLe(1)
            writeShortLe(1)
            writeIntLe(sampleRate)
            writeIntLe(sampleRate * bitsPerSample / 8)
            writeShortLe(bitsPerSample / 8)
            writeShortLe(bitsPerSample)
            writeAscii("data")
            writeIntLe(data.size)
            write(data)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))

    private fun ByteArrayOutputStream.writeIntLe(value: Int) = write(
        byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()),
    )

    private fun ByteArrayOutputStream.writeShortLe(value: Int) =
        write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
}
