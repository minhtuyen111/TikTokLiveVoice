package com.tlv.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.tlv.app.network.Http
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Phát âm thanh meme: tải một lần, lưu cache, phát bằng MediaPlayer (nhẹ, không giữ CPU khi rảnh). */
class MemePlayer(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: MediaPlayer? = null
    @Volatile private var lastAt = 0L
    private val dir get() = File(ctx.cacheDir, "memes").also { it.mkdirs() }

    /** Bỏ qua nếu vừa phát trong 2,5 giây để không chồng tiếng khi sự kiện dồn dập. */
    fun play(url: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAt < 2500) return
        lastAt = now
        scope.launch {
            val f = cached(url) ?: return@launch
            withContext(Dispatchers.Main) { start(f) }
        }
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

    private fun start(f: File) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ -> stop(); true }
            runCatching { setDataSource(f.absolutePath); prepareAsync() }.onFailure { stop() }
        }
    }

    fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
    }
}
