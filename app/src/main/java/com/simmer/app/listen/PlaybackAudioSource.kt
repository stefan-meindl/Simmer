package com.simmer.app.listen

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import com.simmer.app.data.AudioSource
import com.simmer.app.domain.LISTEN_MAX_SECONDS
import com.simmer.app.domain.LISTEN_SAMPLE_RATE
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Reel's playback sound through `AudioPlaybackCapture` on a MediaProjection (never the microphone), at 16 kHz
 * mono. PCM is buffered in memory, at most [LISTEN_MAX_SECONDS] of it, and handed over by [stop]. The projection
 * is attached by [ListenService] after the system consent ([attach]).
 */
@Singleton
class PlaybackAudioSource @Inject constructor() : AudioSource {
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var buffer = ShortArray(0)

    @Volatile private var filled = 0

    @Volatile private var running = false

    fun attach(projection: MediaProjection) {
        this.projection = projection
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is asked before the consent screen.
    @Synchronized
    override fun start() {
        val projection = checkNotNull(projection) { "No MediaProjection attached" }
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(LISTEN_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBytes = AudioRecord.getMinBufferSize(LISTEN_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBytes, LISTEN_SAMPLE_RATE * 2))
            .build()
        check(audio.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord did not initialise" }

        buffer = ShortArray(LISTEN_MAX_SECONDS * LISTEN_SAMPLE_RATE)
        filled = 0
        running = true
        record = audio
        audio.startRecording()
        thread = Thread({
            val chunk = ShortArray(LISTEN_SAMPLE_RATE / 10)
            while (running && filled < buffer.size) {
                val n = audio.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                val count = minOf(n, buffer.size - filled)
                System.arraycopy(chunk, 0, buffer, filled, count)
                filled += count
            }
        }, "listen-capture").also { it.start() }
    }

    @Synchronized
    override fun stop(): FloatArray {
        running = false
        thread?.join(1_000)
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // Already stopped by the system when the projection ended.
            }
            it.release()
        }
        record = null
        val pcm = FloatArray(filled) { buffer[it] / 32768f }
        buffer = ShortArray(0)
        filled = 0
        return pcm
    }

    /** The service is done with the projection: drop it. */
    @Synchronized
    fun detach() {
        projection = null
    }
}
