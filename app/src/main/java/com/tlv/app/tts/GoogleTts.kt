package com.tlv.app.tts

import android.content.Context
import com.tlv.app.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Giọng "chị Google" (giống Google Dịch) cho cả 5 ngôn ngữ. Không cần khóa, không cần engine TTS của ROM.
 * Đây là điểm cuối KHÔNG chính thức của Google Dịch: có thể bị giới hạn hoặc ngừng bất cứ lúc nào,
 * nên [TtsController] luôn rơi về giọng khác khi lỗi. Văn bản dài được cắt thành đoạn ≤ 180 ký tự; âm thanh lưu đệm.
 */
class GoogleTts(private val ctx: Context) {
    private val client = Http.client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    private val dir get() = File(ctx.cacheDir, "gtts").also { it.mkdirs() }

    private fun tl(tag: String) = when (tag.substringBefore('-').lowercase()) {
        "vi" -> "vi"; "zh" -> "zh-CN"; "ja" -> "ja"; "ko" -> "ko"; else -> "en"
    }

    /** Danh sách file MP3 theo thứ tự đọc; null nếu bất kỳ đoạn nào lỗi mạng/bị chặn. */
    suspend fun fetch(text: String, langTag: String): List<File>? = withContext(Dispatchers.IO) {
        val chunks = split(text)
        if (chunks.isEmpty()) return@withContext null
        val lang = tl(langTag)
        val out = ArrayList<File>(chunks.size)
        for (c in chunks) out += one(c, lang) ?: return@withContext null
        trim()
        out
    }

    private fun one(chunk: String, lang: String): File? {
        val key = MessageDigest.getInstance("SHA-1").digest("g|$lang|$chunk".toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(dir, "$key.mp3")
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f }
        return runCatching {
            val url = "https://translate.google.com/translate_tts".toHttpUrl().newBuilder()
                .addQueryParameter("ie", "UTF-8").addQueryParameter("client", "tw-ob")
                .addQueryParameter("tl", lang).addQueryParameter("q", chunk).addQueryParameter("ttsspeed", "1").build()
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", "https://translate.google.com/").build()
            client.newCall(req).execute().use { r ->
                val bytes = if (r.isSuccessful) r.body?.bytes() else null
                if (bytes == null || bytes.size < 500) null
                else {
                    val tmp = File(dir, "$key.tmp")
                    tmp.writeBytes(bytes)
                    if (tmp.renameTo(f)) f else { tmp.delete(); null }
                }
            }
        }.getOrNull()
    }

    private fun split(t: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (w0 in t.replace(Regex("\\s+"), " ").trim().split(' ')) {
            val w = w0.take(180)
            if (w.isEmpty()) continue
            if (cur.length + w.length + 1 > 180) { out += cur.toString(); cur.setLength(0) }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out
    }

    private fun trim() {
        val files = dir.listFiles { x -> x.extension == "mp3" } ?: return
        if (files.size <= 200) return
        files.sortedBy { it.lastModified() }.take(files.size - 200).forEach { it.delete() }
    }
}
