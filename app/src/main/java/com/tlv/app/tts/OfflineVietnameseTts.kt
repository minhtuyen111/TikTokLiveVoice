package com.tlv.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.tlv.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Giọng Việt thần kinh NẰM TRONG APK (sherpa-onnx + Piper vais1000). Không cần engine TTS của ROM, không cần mạng,
 * nên chạy được trên ROM nội địa Trung Quốc / ROM nước ngoài / máy không cài sẵn giọng nào.
 *
 * Vì sao bản cũ bị "văng":
 *  1. R8 đổi tên lớp JNI của sherpa-onnx (thiếu luật keep) -> crash khi nạp.
 *  2. `espeak-ng-data` đặt là đường dẫn trong assets, nhưng thư viện native cần một thư mục THẬT trên bộ nhớ;
 *     không tìm thấy thì native gọi exit() và cả tiến trình chết (không bắt được bằng try/catch).
 *  Bản này chép dữ liệu ra filesDir, kiểm tra đủ file trước khi gọi native, và có "cầu chì" chống vòng lặp văng.
 */
internal class OfflineVietnameseTts(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex() // engine native KHÔNG an toàn đa luồng: mọi lần tạo/đọc/giải phóng đi qua khóa này
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    private val stopped = AtomicBoolean(false)

    companion object {
        private const val ASSET_DIR = "tts_vi"
        private const val MODEL = "vi_VN-vais1000-medium.onnx"
        private const val DATA_VERSION = "vais1000-r2"
        private const val PREFS = "tlv_tts_guard"
        private const val MIN_MODEL_BYTES = 1_000_000L

        @Volatile private var bundledCache: Boolean? = null
        @Volatile private var guardChecked = false

        /** Model có trong APK không (CI đã tải vào assets). */
        fun bundled(ctx: Context): Boolean {
            bundledCache?.let { return it }
            val ok = runCatching { ctx.applicationContext.assets.list(ASSET_DIR)?.contains(MODEL) == true }.getOrDefault(false)
            bundledCache = ok
            return ok
        }

        /** Có model và chưa bị cầu chì tắt (do lần trước native bị sập). */
        fun usable(ctx: Context): Boolean = bundled(ctx) && !guardDisabled(ctx.applicationContext)

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        @Synchronized
        private fun guardDisabled(c: Context): Boolean {
            val sp = prefs(c)
            if (sp.getInt("ver", -1) != BuildConfig.VERSION_CODE) {
                // Bản mới: cho thử lại từ đầu.
                sp.edit().clear().putInt("ver", BuildConfig.VERSION_CODE).commit()
            }
            if (!guardChecked) {
                guardChecked = true
                // Cờ "running" còn bật = lần chạy trước sập giữa chừng khi đang khởi tạo native.
                if (sp.getBoolean("running", false)) {
                    sp.edit().putBoolean("running", false).putInt("crashes", sp.getInt("crashes", 0) + 1).commit()
                }
            }
            return sp.getInt("crashes", 0) >= 2
        }

        private fun guardBegin(c: Context) { prefs(c).edit().putBoolean("running", true).commit() }
        private fun guardOk(c: Context) { prefs(c).edit().putBoolean("running", false).putInt("crashes", 0).commit() }
        private fun guardFail(c: Context) { prefs(c).edit().putBoolean("running", false).commit() }
    }

    // ───────────── chuẩn bị dữ liệu ─────────────

    /** Chép model + tokens + espeak-ng-data từ assets ra filesDir (một lần). Trả về thư mục gốc. */
    private fun prepare(): File {
        val root = File(app.filesDir, ASSET_DIR)
        val mark = File(root, ".ready")
        fun healthy() = mark.exists() && runCatching { mark.readText() }.getOrNull() == DATA_VERSION &&
            File(root, MODEL).length() > MIN_MODEL_BYTES &&
            File(root, "tokens.txt").length() > 0 &&
            File(root, "espeak-ng-data").list()?.isNotEmpty() == true
        if (healthy()) return root
        root.deleteRecursively()
        root.mkdirs()
        copyAsset(ASSET_DIR, root)
        check(File(root, MODEL).length() > MIN_MODEL_BYTES) { "Thiếu file model giọng Việt" }
        check(File(root, "tokens.txt").length() > 0) { "Thiếu tokens.txt của giọng Việt" }
        check(File(root, "espeak-ng-data").list()?.isNotEmpty() == true) { "Thiếu espeak-ng-data của giọng Việt" }
        mark.writeText(DATA_VERSION)
        return root
    }

    private fun copyAsset(path: String, dest: File) {
        val names = app.assets.list(path)
        if (names.isNullOrEmpty()) {
            try {
                dest.parentFile?.mkdirs()
                app.assets.open(path).use { i -> dest.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            } catch (_: FileNotFoundException) {
                dest.mkdirs() // thư mục rỗng
            }
        } else {
            dest.mkdirs()
            for (n in names) copyAsset("$path/$n", File(dest, n))
        }
    }

    /** Gọi khi đã giữ [lock]. */
    private fun getEngine(): OfflineTts {
        engine?.let { return it }
        check(usable(app)) { "Giọng Việt tích hợp đang tắt (thiếu model hoặc lần trước bị lỗi native)" }
        val root = prepare()
        val vits = OfflineTtsVitsModelConfig(
            model = File(root, MODEL).absolutePath,
            tokens = File(root, "tokens.txt").absolutePath,
            dataDir = File(root, "espeak-ng-data").absolutePath, // PHẢI là đường dẫn thật, không phải đường dẫn assets
        )
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(vits = vits, numThreads = 2, debug = false, provider = "cpu"),
            maxNumSentences = 1,
        )
        guardBegin(app)
        try {
            val t = OfflineTts(assetManager = null, config = cfg) // đường dẫn tệp thật -> không truyền AssetManager
            t.generate("xin chào", sid = 0, speed = 1.0f) // chạy thử: nếu native hỏng thì sập ở đây, cầu chì sẽ ghi nhận
            guardOk(app)
            engine = t
            return t
        } catch (e: Throwable) {
            guardFail(app)
            throw e
        }
    }

    /** Khởi tạo sớm (khi bắt đầu LIVE) để bình luận đầu tiên không bị trễ. true nếu sẵn sàng. */
    suspend fun warmUp(): Boolean = withContext(Dispatchers.Default) {
        try {
            lock.withLock { getEngine() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    // ───────────── đọc ─────────────

    suspend fun speak(text: String, volume: Float, rate: Float): Boolean = withContext(Dispatchers.Default) {
        val clean = text.trim().take(400)
        if (clean.isEmpty()) return@withContext true
        lock.withLock {
            stopped.set(false)
            try {
                val generated = getEngine().generate(clean, sid = 0, speed = rate.coerceIn(0.65f, 1.8f))
                val samples = generated.samples
                val sr = generated.sampleRate
                if (samples.isEmpty() || sr <= 0) return@withLock false
                if (stopped.get()) return@withLock false

                val minBuffer = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    .coerceAtLeast(sr / 2 * 2).coerceAtLeast(4096) // ~0,5 giây đệm; một số ROM trả giá trị âm/nhỏ bất thường
                val audio = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
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
                val pcm = ShortArray(minBuffer / 4)
                var offset = 0
                while (offset < samples.size && !stopped.get()) {
                    val count = minOf(pcm.size, samples.size - offset)
                    for (i in 0 until count) {
                        val v = (samples[offset + i] * gain).coerceIn(-1f, 1f)
                        pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
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
                    // Chờ phát hết phần còn trong bộ đệm; có hạn chót để ROM báo vị trí sai cũng không treo.
                    val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / sr + 2000L
                    while (!stopped.get() && audio.playbackHeadPosition < samples.size && SystemClock.elapsedRealtime() < deadline) {
                        delay(20)
                    }
                }
                !stopped.get()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            } finally {
                releaseTrack()
            }
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

    /** Giải phóng engine native SAU KHI lần đọc đang chạy (nếu có) đã dừng, tránh free khi đang generate. */
    fun shutdown() {
        stop()
        scope.launch {
            lock.withLock {
                engine?.let { runCatching { it.free() } }
                engine = null
            }
        }
    }
}
