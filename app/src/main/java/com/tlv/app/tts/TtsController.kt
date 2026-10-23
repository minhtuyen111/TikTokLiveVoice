package com.tlv.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.tlv.app.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Đọc bằng giọng chất lượng cao qua mạng ([CloudTts], nam/nữ) và tự rơi về engine đọc của hệ thống khi mất mạng/lỗi.
 * Nếu máy không có engine hệ thống (ROM nước ngoài) thì vẫn đọc được bằng giọng online.
 */
class TtsController(private val context: Context, private val cloud: CloudTts? = null) {
    private var tts: TextToSpeech? = null
    @Volatile private var sysReady = false
    @Volatile private var sysFailed = false
    @Volatile private var useCloud = true
    @Volatile private var curLang = "vi-VN"
    @Volatile private var curGender = "female"
    @Volatile private var curRate = 1f
    @Volatile private var curPitch = 1f
    @Volatile private var cloudFails = 0
    @Volatile private var cloudDownUntil = 0L
    private var player: MediaPlayer? = null
    private val cloudOn get() = cloud?.available == true && useCloud
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    @Volatile private var appliedKey: String? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String) { _speaking.value = true }
        override fun onDone(id: String) = complete(id, true)
        @Deprecated("Deprecated in Java")
        override fun onError(id: String) = complete(id, false)
        override fun onError(id: String, errorCode: Int) = complete(id, false)
        override fun onStop(id: String, interrupted: Boolean) = complete(id, false)
    }

    @Synchronized
    fun ensureInit() {
        // Giọng online không cần engine hệ thống: sẵn sàng ngay.
        if (cloud?.available == true) _ready.value = true
        if (tts != null) return
        _error.value = null
        sysFailed = false
        val t = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                sysReady = true
                _ready.value = true
            } else {
                sysFailed = true
                if (!cloudOn) _error.value = "Không khởi tạo được engine đọc giọng nói"
                tts = null
            }
        }
        t.setOnUtteranceProgressListener(listener)
        tts = t
    }

    private fun complete(id: String, ok: Boolean) {
        _speaking.value = false
        pending.remove(id)?.complete(ok)
    }

    /** Áp dụng giọng theo cài đặt. */
    fun apply(s: AppSettings): String? = applyLang(s, s.ttsLang, s.ttsVoice)

    /**
     * Áp dụng ngôn ngữ + giọng + tốc độ + cao độ. Luôn chọn rõ một giọng thuộc đúng ngôn ngữ (ưu tiên giọng đã cài,
     * ngoại tuyến, chất lượng cao) để tiếng Anh đọc bằng giọng Anh, tiếng Trung đọc bằng giọng Trung, v.v.
     */
    fun applyLang(s: AppSettings, langTag: String, voiceName: String): String? {
        curLang = langTag; curGender = s.ttsGender; curRate = s.ttsRate; curPitch = s.ttsPitch; useCloud = s.ttsCloud
        val t = tts
        if (t == null || !sysReady) {
            if (cloudOn) return null
            if (sysFailed) _error.value = "Không khởi tạo được engine đọc giọng nói"
            return "Engine đọc chưa sẵn sàng"
        }
        val key = "$langTag|$voiceName|${s.ttsRate}|${s.ttsPitch}"
        if (key == appliedKey) return null
        t.setSpeechRate(s.ttsRate)
        t.setPitch(s.ttsPitch)
        val want = Locale.forLanguageTag(langTag)
        var r = t.setLanguage(want)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = t.setLanguage(Locale(want.language))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (cloudOn) return null // đã có giọng online, bỏ qua thiếu dữ liệu của engine hệ thống
            _error.value = "Thiếu dữ liệu giọng ${voiceLangOf(langTag).native}. Cài trong Cài đặt Android > Chuyển văn bản thành giọng nói"
            return _error.value
        }
        val same = voices().filter { it.locale.language == want.language }
        val chosen = same.firstOrNull { it.name == voiceName }
            ?: same.filter { !it.features.contains("notInstalled") }
                .sortedWith(
                    compareByDescending<Voice> { it.locale.country == want.country }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                ).firstOrNull()
        chosen?.let { runCatching { t.voice = it } }
        _error.value = null
        appliedKey = key
        return null
    }

    fun voices(): List<Voice> = runCatching { tts?.voices?.toList().orEmpty() }.getOrDefault(emptyList())

    /** Đọc và chờ đến khi xong. Trả false nếu lỗi hoặc bị dừng. */
    suspend fun speak(text: String, volume: Float = 1f): Boolean {
        if (cloudOn) speakCloud(text, volume)?.let { return it }
        return speakSystem(text, volume)
    }

    /** true = đọc xong, false = bị dừng/lỗi phát, null = không lấy được giọng online (rơi về engine hệ thống). */
    private suspend fun speakCloud(text: String, volume: Float): Boolean? {
        val c = cloud ?: return null
        if (System.currentTimeMillis() < cloudDownUntil) return null
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<Boolean>()
        pending[id] = d
        try {
            val f = c.fetch(text, curLang, curGender, curRate, curPitch)
            if (f == null) {
                // Lỗi liên tiếp: tạm nghỉ 30 giây để khỏi chờ timeout ở từng bình luận.
                if (++cloudFails >= 2) { cloudDownUntil = System.currentTimeMillis() + 30_000; cloudFails = 0 }
                return null
            }
            cloudFails = 0
            if (d.isCompleted) return false // bị dừng trong lúc tải
            withContext(Dispatchers.Main) { startPlayer(f, volume, id) }
            return d.await()
        } finally {
            pending.remove(id)
            _speaking.value = false
        }
    }

    private fun startPlayer(f: File, volume: Float, id: String) {
        stopPlayer()
        val v = volume.coerceIn(0f, 1f)
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        mp.setVolume(v, v)
        mp.setOnPreparedListener { _speaking.value = true; it.start() }
        mp.setOnCompletionListener { stopPlayer(); complete(id, true) }
        mp.setOnErrorListener { _, _, _ -> stopPlayer(); complete(id, false); true }
        runCatching { mp.setDataSource(f.absolutePath); mp.prepareAsync() }.onFailure { stopPlayer(); complete(id, false) }
    }

    @Synchronized
    private fun stopPlayer() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    private suspend fun speakSystem(text: String, volume: Float): Boolean {
        val t = tts ?: return false
        if (!sysReady) return false
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<Boolean>()
        pending[id] = d
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (r != TextToSpeech.SUCCESS) { pending.remove(id); return false }
        return try { d.await() } finally { pending.remove(id) }
    }

    fun stop() {
        stopPlayer()
        tts?.stop()
        pending.values.forEach { it.complete(false) }
        pending.clear()
        _speaking.value = false
    }

    @Synchronized
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        sysReady = false
        appliedKey = null
        _ready.value = false
    }
}
