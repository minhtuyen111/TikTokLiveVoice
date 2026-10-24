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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    @Volatile private var offlineVi: OfflineVietnameseTts? = null
    @Volatile private var viFirst = true // tiếng Việt ưu tiên giọng tích hợp trong app (chạy mọi ROM, không cần mạng)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cloudOn get() = cloud?.available == true && useCloud
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    @Volatile private var appliedKey: String? = null
    @Volatile private var stopRequested = false

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
        // Giọng online hoặc giọng Việt tích hợp không cần engine hệ thống: sẵn sàng ngay.
        if (cloud?.available == true || offlineUsable()) _ready.value = true
        if (offlineUsable()) warmUpOffline()
        if (tts != null) return
        _error.value = null
        sysFailed = false
        val t = try {
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    sysReady = true
                    _ready.value = true
                } else {
                    sysFailed = true
                    if (!cloudOn && !offlineUsable()) _error.value = "Không khởi tạo được engine đọc giọng nói"
                    tts = null
                }
            }
        } catch (e: Throwable) { // ROM không có/engine hệ thống hỏng: vẫn chạy bằng giọng tích hợp hoặc online
            sysFailed = true
            if (!cloudOn && !offlineUsable()) _error.value = "Không khởi tạo được engine đọc giọng nói"
            return
        }
        t.setOnUtteranceProgressListener(listener)
        tts = t
    }

    private fun offlineUsable() = OfflineVietnameseTts.usable(context)

    private fun viTag(tag: String) = tag.equals("vi", true) || tag.startsWith("vi-", true)

    /** Nạp sẵn model ở nền để bình luận đầu tiên không phải chờ. */
    private fun warmUpOffline() {
        val e = offlineEngine() ?: return
        scope.launch { e.warmUp() }
    }

    private fun offlineEngine(): OfflineVietnameseTts? = try {
        offlineVi ?: synchronized(this) { offlineVi ?: OfflineVietnameseTts(context).also { offlineVi = it } }
    } catch (e: Throwable) { null }

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
        curLang = langTag; curGender = s.ttsGender; curRate = s.ttsRate; curPitch = s.ttsPitch; useCloud = s.ttsCloud; viFirst = s.ttsViOffline
        val viOk = viTag(langTag) && offlineUsable() // tiếng Việt đã có giọng tích hợp -> không cần engine hệ thống
        val t = tts
        if (t == null || !sysReady) {
            if (cloudOn || viOk) { if (viOk) _error.value = null; return null }
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
            if (cloudOn || viOk) { if (viOk) _error.value = null; return null } // đã có giọng online/tích hợp, bỏ qua thiếu dữ liệu của engine hệ thống
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

    /**
     * Đọc và chờ đến khi xong. Trả false nếu lỗi hoặc bị dừng.
     * Thứ tự cho tiếng Việt: giọng tích hợp (nếu bật) -> giọng online -> giọng tích hợp -> engine hệ thống.
     * Nhờ vậy máy không có engine TTS (ROM nội địa, K8A…) vẫn đọc được tiếng Việt.
     */
    suspend fun speak(text: String, volume: Float = 1f): Boolean {
        stopRequested = false
        val vi = isVietnamese() && offlineUsable()
        var triedOffline = false
        if (vi && viFirst) {
            triedOffline = true
            if (speakOfflineVietnamese(text, volume) == true) return true
            if (stopRequested) return false
        }
        if (cloudOn) {
            if (speakCloud(text, volume) == true) return true
            if (stopRequested) return false // người dùng bấm Dừng: không đọc lại
        }
        if (vi && !triedOffline) {
            if (speakOfflineVietnamese(text, volume) == true) return true
            if (stopRequested) return false
        }
        return speakSystem(text, volume)
    }

    private fun isVietnamese() = curLang.equals("vi", true) || curLang.startsWith("vi-", true)

    /** null: model/runtime không dùng được; false: tổng hợp/phát lỗi hoặc bị dừng; true: đọc xong. */
    private suspend fun speakOfflineVietnamese(text: String, volume: Float): Boolean? {
        val engine = offlineEngine() ?: run {
            _error.value = "Không khởi tạo được giọng Việt tích hợp"
            return null
        }
        _speaking.value = true
        val r = try {
            engine.speak(text, volume, curRate, curPitch)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            _error.value = "Giọng Việt tích hợp gặp lỗi: ${e.message ?: "không phát được"}"
            return null
        } finally {
            _speaking.value = false
        }
        if (r) _error.value = null
        return r
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
        stopRequested = true
        stopPlayer()
        offlineVi?.stop()
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
        offlineVi?.shutdown()
        offlineVi = null
        sysReady = false
        appliedKey = null
        _ready.value = false
    }
}
