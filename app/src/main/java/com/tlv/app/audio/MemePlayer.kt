package com.tlv.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.tlv.app.network.Http
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Phát âm thanh meme: tải một lần, lưu cache, phát bằng MediaPlayer. Có bản "phát rồi chờ xong" để xếp hàng tuần tự. */
class MemePlayer(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var player: MediaPlayer? = null
    private val dir get() = File(ctx.cacheDir, "memes").also { it.mkdirs() }

    /** Nghe thử (không chờ). */
    fun play(url: String, @Suppress("UNUSED_PARAMETER") force: Boolean = false) {
        scope.launch {
            val f = cached(url) ?: return@launch
            withContext(Dispatchers.Main) { start(f, null) }
        }
    }

    /** Phát và chờ đến khi xong (hoặc hết [maxMs]). Bị hủy giữa chừng thì tự tắt tiếng. true = phát xong bình thường. */
    suspend fun playAndWait(url: String, maxMs: Long = 6000L): Boolean {
        val f = withContext(Dispatchers.IO) { cached(url) } ?: return false
        val done = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) { start(f, done) }
        try {
            return withTimeoutOrNull(maxMs) { done.await() } ?: false
        } finally {
            if (!done.isCompleted) withContext(NonCancellable + Dispatchers.Main) { stop() }
        }
    }

    /** Tải trước để lúc cần phát không bị trễ (và dùng được cả khi mất mạng). */
    fun prefetch(urls: List<String>) {
        scope.launch { urls.forEach { runCatching { cached(it) } } }
    }

    private fun cached(url: String): File? {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(dir, name)
        if (f.exists() && f.length() > 0) return f
        return runCatching {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body ?: return null
                if (!r.isSuccessful || body.contentLength() > 3_000_000) return null
                val tmp = File(dir, "$name.tmp")
                tmp.outputStream().use { o -> body.byteStream().copyTo(o) }
                if (tmp.length() > 3_000_000 || !tmp.renameTo(f)) { tmp.delete(); return null }
                f
            }
        }.getOrNull()
    }

    private fun start(f: File, done: CompletableDeferred<Boolean>?) {
        stop()
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        )
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { release(mp); done?.complete(true) }
        mp.setOnErrorListener { _, _, _ -> release(mp); done?.complete(false); true }
        runCatching { mp.setDataSource(f.absolutePath); mp.prepareAsync() }.onFailure { release(mp); done?.complete(false) }
    }

    private fun release(mp: MediaPlayer) {
        runCatching { mp.release() }
        if (player === mp) player = null
    }

    fun stop() {
        player?.let { release(it) }
    }
}
