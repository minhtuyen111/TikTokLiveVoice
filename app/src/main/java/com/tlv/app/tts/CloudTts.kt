package com.tlv.app.tts

import android.content.Context
import com.tlv.app.auth.AuthRepo
import com.tlv.app.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Giọng đọc chất lượng cao (nam/nữ) qua Edge Function "tts" của Supabase (khóa Azure nằm trên máy chủ, không nằm trong APK).
 * Âm thanh MP3 được lưu đệm trong cache để câu lặp lại không phải tải lại.
 */
class CloudTts(private val ctx: Context, private val auth: AuthRepo) {
    private val client = Http.client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()
    private val dir get() = File(ctx.cacheDir, "tts").also { it.mkdirs() }

    val available get() = auth.configured

    /** Trả về file MP3 (từ cache hoặc vừa tải); null nếu lỗi mạng/máy chủ hoặc chưa đăng nhập. */
    suspend fun fetch(text: String, lang: String, gender: String, rate: Float, pitch: Float): File? {
        val key = sha1("$lang|$gender|$rate|$pitch|$text")
        val f = File(dir, "$key.mp3")
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f }
        val token = auth.validToken() ?: return null
        val body = JSONObject().put("text", text).put("lang", lang).put("gender", gender)
            .put("rate", rate.toDouble()).put("pitch", pitch.toDouble()).toString().toRequestBody(json)
        val bytes = try {
            download(client.newCall(auth.req("/functions/v1/tts", token).post(body).build()))
        } catch (e: IOException) { null } ?: return null
        if (bytes.size < 500) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(dir, "$key.tmp")
                tmp.writeBytes(bytes)
                if (tmp.renameTo(f)) { trim(); f } else { tmp.delete(); null }
            }.getOrNull()
        }
    }

    private suspend fun download(call: Call): ByteArray? = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resume(null) }
            override fun onResponse(call: Call, response: Response) {
                val out = response.use { r -> if (r.isSuccessful) runCatching { r.body?.bytes() }.getOrNull() else null }
                if (cont.isActive) cont.resume(out)
            }
        })
    }

    /** Giữ tối đa 80 file gần dùng nhất. */
    private fun trim() {
        val files = dir.listFiles { x -> x.extension == "mp3" } ?: return
        if (files.size <= 80) return
        files.sortedBy { it.lastModified() }.take(files.size - 80).forEach { it.delete() }
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
