package com.tlv.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.tlv.app.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TtsController(private val context: Context) {
    private var tts: TextToSpeech? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

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
        if (tts != null) return
        _error.value = null
        val t = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _ready.value = true
            } else {
                _error.value = "Không khởi tạo được engine đọc giọng nói"
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

    /** Áp dụng ngôn ngữ/giọng/tốc độ/cao độ. Trả thông báo lỗi nếu có (ví dụ thiếu dữ liệu ngôn ngữ). */
    fun apply(s: AppSettings): String? {
        val t = tts ?: return "Engine đọc chưa sẵn sàng"
        t.setSpeechRate(s.ttsRate)
        t.setPitch(s.ttsPitch)
        val locale = Locale.forLanguageTag(s.ttsLang)
        val r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            _error.value = "Thiếu dữ liệu giọng cho ${s.ttsLang}. Hãy cài trong Cài đặt Android > Chuyển văn bản thành giọng nói"
            return _error.value
        }
        if (s.ttsVoice.isNotEmpty()) {
            t.voices?.firstOrNull { it.name == s.ttsVoice }?.let { t.voice = it }
        }
        _error.value = null
        return null
    }

    fun voices(): List<Voice> = runCatching { tts?.voices?.toList().orEmpty() }.getOrDefault(emptyList())

    fun languages(): List<String> =
        voices().map { it.locale.toLanguageTag() }.distinct().sorted()

    /** Đọc và chờ đến khi xong. Trả false nếu lỗi hoặc bị dừng. */
    suspend fun speak(text: String): Boolean {
        val t = tts ?: return false
        if (!_ready.value) return false
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<Boolean>()
        pending[id] = d
        val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (r != TextToSpeech.SUCCESS) { pending.remove(id); return false }
        return try { d.await() } finally { pending.remove(id) }
    }

    fun stop() {
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
        _ready.value = false
    }
}
