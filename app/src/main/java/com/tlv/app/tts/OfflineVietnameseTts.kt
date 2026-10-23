package com.tlv.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Neural Vietnamese fallback embedded in the APK; does not depend on ROM-installed TTS voices. */
internal class OfflineVietnameseTts(context: Context) {
    private val app = context.applicationContext
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    private val stopped = AtomicBoolean(false)

    @Synchronized
    private fun getEngine(): OfflineTts {
        engine?.let { return it }
        val vits = OfflineTtsVitsModelConfig(
            model = "tts_vi/vi_VN-vais1000-medium.onnx",
            tokens = "tts_vi/tokens.txt",
            dataDir = "tts_vi/espeak-ng-data"
        )
        return OfflineTts(
            assetManager = app.assets,
            config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits), maxNumSentences = 1)
        ).also { engine = it }
    }

    suspend fun speak(text: String, volume: Float, rate: Float): Boolean = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext true
        stopped.set(false)
        try {
            val generated = getEngine().generate(text, sid = 0, speed = rate.coerceIn(0.65f, 1.8f))
            if (generated.samples.isEmpty() || generated.sampleRate <= 0) return@withContext false
            val minBuffer = AudioTrack.getMinBufferSize(
                generated.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val audio = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(generated.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = audio
            audio.play()
            val gain = volume.coerceIn(0f, 1f)
            val pcm = ShortArray(minBuffer / 2)
            var offset = 0
            while (offset < generated.samples.size && !stopped.get()) {
                val count = minOf(pcm.size, generated.samples.size - offset)
                for (i in 0 until count) {
                    val sample = (generated.samples[offset + i] * gain).coerceIn(-1f, 1f)
                    pcm[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }
                var written = 0
                while (written < count && !stopped.get()) {
                    val n = audio.write(pcm, written, count - written, AudioTrack.WRITE_BLOCKING)
                    if (n <= 0) throw IllegalStateException("AudioTrack write failed: $n")
                    written += n
                }
                offset += count
            }
            if (!stopped.get()) {
                // Wait until the queued PCM has actually left the device.
                while (audio.playbackHeadPosition.toLong() < generated.samples.size && !stopped.get()) {
                    kotlinx.coroutines.delay(20)
                }
            }
            !stopped.get()
        } catch (_: Throwable) {
            false
        } finally {
            releaseTrack()
        }
    }

    fun stop() {
        stopped.set(true)
        releaseTrack()
    }

    @Synchronized
    private fun releaseTrack() {
        track?.let { runCatching { it.pause(); it.flush(); it.stop(); it.release() } }
        track = null
    }

    @Synchronized
    fun shutdown() {
        stop()
        engine?.let { runCatching { it.free() } }
        engine = null
    }
}
