package com.tlv.app.live

import com.tlv.app.filter.CommentFilter
import com.tlv.app.history.SessionDao
import com.tlv.app.history.SessionEntity
import com.tlv.app.settings.AppSettings
import com.tlv.app.tts.LangDetect
import com.tlv.app.tts.TtsController
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.math.min
import kotlin.random.Random

/**
 * Điều phối: nhận bình luận -> lọc -> hàng đợi (có giới hạn) -> TTS tuần tự.
 * Sống trong tiến trình app; LiveService giữ tiến trình hoạt động bằng Foreground Service.
 */
class LiveEngine(
    private val source: LiveSource,
    private val tts: TtsController,
    private val dao: SessionDao,
    private val settings: () -> AppSettings,
    private val ownerId: () -> String,
    private val token: suspend () -> String?,
    private val bridge: suspend () -> String,
    private val eventSound: (kind: String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null
    private var speakJob: Job? = null
    private val lock = Any()

    private val _conn = MutableStateFlow<ConnState>(ConnState.Idle)
    val conn: StateFlow<ConnState> = _conn
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused
    private val _current = MutableStateFlow<Comment?>(null)
    val current: StateFlow<Comment?> = _current
    private val _channel = MutableStateFlow("")
    val channel: StateFlow<String> = _channel
    private val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt
    val speaking: StateFlow<Boolean> get() = tts.speaking
    val ttsError: StateFlow<String?> get() = tts.error

    val active: Boolean get() = _conn.value.let { it !is ConnState.Idle && it !is ConnState.Failed && it !is ConnState.Ended }

    private val queue = ArrayDeque<Long>() // id bình luận chờ đọc
    private val byId = HashMap<Long, Comment>()
    private val recent = ArrayDeque<String>()
    private val spokenTimes = ArrayDeque<Long>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var nextId = 1L
    private var received = 0
    private var spoken = 0
    private var filtered = 0
    private val COMMENT_CAP = 300

    /** Kết nối (hủy phiên cũ nếu có để không tạo kết nối trùng). */
    fun connect(channelName: String) {
        synchronized(lock) {
            connectJob?.cancel(); speakJob?.cancel()
            resetCounters()
            _channel.value = channelName
            _startedAt.value = System.currentTimeMillis()
            _conn.value = ConnState.Connecting
            tts.ensureInit()
            speakJob = scope.launch { speakLoop() }
            connectJob = scope.launch { connectLoop(channelName) }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            connectJob?.cancel(); speakJob?.cancel()
            connectJob = null; speakJob = null
            tts.shutdown()
            _current.value = null
            if (_conn.value !is ConnState.Failed && _conn.value !is ConnState.Ended) _conn.value = ConnState.Idle
        }
    }

    fun pause() { _paused.value = true }
    fun resume() { _paused.value = false; wake.trySend(Unit) }

    fun clearQueue() {
        synchronized(lock) {
            queue.forEach { id -> byId[id]?.let { put(it.copy(status = CommentStatus.FILTERED, filterNote = "Đã xóa khỏi hàng đợi")) } }
            queue.clear()
            publishStats()
        }
    }

    fun clearComments() {
        synchronized(lock) {
            queue.clear(); byId.clear(); recent.clear()
            _comments.value = emptyList()
            publishStats()
        }
    }

    /** Nghe thử giọng (không qua hàng đợi). */
    suspend fun preview(text: String): String? {
        tts.ensureInit()
        withTimeoutOrNull(4000) { tts.ready.first { it } } ?: return "Engine đọc chưa sẵn sàng"
        tts.apply(settings())?.let { return it }
        return if (tts.speak(text, settings().ttsVolume)) null else "Không đọc được"
    }

    private fun resetCounters() {
        queue.clear(); byId.clear(); recent.clear(); spokenTimes.clear()
        _comments.value = emptyList()
        received = 0; spoken = 0; filtered = 0
        publishStats()
    }

    private fun publishStats() { _stats.value = Stats(received, spoken, queue.size, filtered) }

    private fun put(c: Comment) {
        byId[c.id] = c
        _comments.update { list ->
            val i = list.indexOfLast { it.id == c.id }
            if (i >= 0) list.toMutableList().also { it[i] = c }
            else (list + c).let { if (it.size > COMMENT_CAP) it.takeLast(COMMENT_CAP) else it }
        }
        // giữ map gọn theo danh sách hiển thị
        if (byId.size > COMMENT_CAP * 2) {
            val keep = _comments.value.map { it.id }.toSet() + queue
            byId.keys.retainAll(keep)
        }
    }

    private fun onComment(user: String, text: String) {
        synchronized(lock) {
            received++
            val s = settings()
            val d = CommentFilter.decide(s, text, recent)
            val id = nextId++
            val now = System.currentTimeMillis()
            if (!d.accepted) {
                filtered++
                val note = when (d.reason) {
                    com.tlv.app.filter.Reason.EMPTY -> "Nội dung không hợp lệ"
                    com.tlv.app.filter.Reason.TOO_LONG -> "Quá dài"
                    com.tlv.app.filter.Reason.BLOCKED -> "Từ khóa bị chặn"
                    com.tlv.app.filter.Reason.DUPLICATE -> "Trùng lặp"
                    null -> ""
                }
                // Bình luận trùng không hiển thị lại (chống lặp); các loại khác hiển thị dạng đã lọc
                if (d.reason != com.tlv.app.filter.Reason.DUPLICATE)
                    put(Comment(id, user, text.take(300), now, CommentStatus.FILTERED, note))
                publishStats(); return
            }
            recent.addLast(CommentFilter.key(text)); if (recent.size > 60) recent.removeFirst()

            // Hàng đợi có giới hạn: bỏ mục cũ nhất không ưu tiên
            if (queue.size >= s.queueMax) {
                val victim = queue.firstOrNull { byId[it]?.filterNote != "prio" }
                if (victim != null) {
                    queue.remove(victim)
                    byId[victim]?.let { put(it.copy(status = CommentStatus.FILTERED, filterNote = "Hàng đợi đầy")) }
                    filtered++
                } else { filtered++; publishStats(); return }
            }
            val c = Comment(id, user, d.text, now, CommentStatus.PENDING, if (d.priority) "prio" else "")
            put(c)
            if (d.priority) queue.addFirst(id) else queue.addLast(id)
            publishStats()
        }
        wake.trySend(Unit)
    }

    private fun onEvent(kind: String, user: String, name: String, count: Int) {
        eventSound(kind)
        val s = settings()
        if (!s.announceEvents) return
        val who = user.ifBlank { "Một người xem" }
        val line = when (kind) {
            "follow" -> "$who đã theo dõi"
            "share" -> "$who đã chia sẻ"
            else -> "$who đã tặng ${if (count > 1) "$count " else ""}${name.ifBlank { "quà" }}"
        }
        synchronized(lock) {
            if (queue.size >= s.queueMax) return
            val id = nextId++
            put(Comment(id, user, line, System.currentTimeMillis(), CommentStatus.PENDING, "event"))
            queue.addLast(id); publishStats()
        }
        wake.trySend(Unit)
    }

    private suspend fun connectLoop(ch: String) {
        val owner = ownerId()
        val rowId = withContext(NonCancellable) {
            runCatching {
                dao.insert(SessionEntity(ownerId = owner, channel = ch, startedAt = _startedAt.value, lastState = "connecting"))
            }.getOrDefault(-1L)
        }
        var attempt = 0
        var last = "Đang kết nối"
        try {
            while (currentCoroutineContext().isActive) {
                _conn.value = if (attempt == 0) ConnState.Connecting else _conn.value
                val connectedAt = LongArray(1)
                val outcome = source.run(
                    bridge = bridge(), channel = ch, token = token(),
                    onConnected = { connectedAt[0] = System.currentTimeMillis(); _conn.value = ConnState.Connected },
                    onComment = ::onComment, onEvent = ::onEvent,
                )
                when (outcome) {
                    is Outcome.Ended -> { last = "Livestream đã kết thúc"; _conn.value = ConnState.Ended; return }
                    is Outcome.Fatal -> { last = outcome.message; _conn.value = ConnState.Failed(outcome.message); return }
                    is Outcome.Dropped -> {
                        if (connectedAt[0] != 0L && System.currentTimeMillis() - connectedAt[0] > 30_000) attempt = 0
                        attempt++
                        if (attempt > MAX_ATTEMPTS) {
                            last = "Không thể khôi phục kết nối"
                            _conn.value = ConnState.Failed(last); return
                        }
                        val wait = min(60_000L, 1000L shl min(attempt, 6)) + Random.nextLong(0, 800)
                        _conn.value = ConnState.Reconnecting(attempt, wait)
                        last = outcome.message
                        delay(wait)
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                val state = when (val c = _conn.value) {
                    is ConnState.Failed -> c.message
                    ConnState.Ended -> "Đã kết thúc"
                    else -> last.ifEmpty { "Đã ngắt" }
                }
                if (rowId > 0) runCatching {
                    dao.update(
                        SessionEntity(rowId, owner, ch, _startedAt.value, System.currentTimeMillis(), received, spoken, state)
                    )
                    dao.trim(owner)
                }
            }
        }
    }

    private suspend fun speakLoop() {
        tts.ready.first { it }
        while (currentCoroutineContext().isActive) {
            if (_paused.value) { wake.receive(); continue }
            val c = synchronized(lock) { queue.removeFirstOrNull()?.let { byId[it] } }
            if (c == null) { _current.value = null; publishStatsLocked(); wake.receive(); continue }

            val s = settings()
            // Giới hạn số bình luận đọc mỗi phút
            val now = System.currentTimeMillis()
            while (spokenTimes.isNotEmpty() && now - spokenTimes.first() > 60_000) spokenTimes.removeFirst()
            if (s.maxPerMinute > 0 && spokenTimes.size >= s.maxPerMinute) {
                synchronized(lock) { queue.addFirst(c.id) }
                delay((60_000 - (now - spokenTimes.first())).coerceIn(250, 60_000))
                continue
            }
            synchronized(lock) { put(c.copy(status = CommentStatus.SPEAKING)); publishStats() }
            _current.value = c
            val line = if (c.filterNote == "event") c.text
                else if (s.readUsername && c.user.isNotBlank()) "${c.user} nói ${c.text}" else c.text
            val lang = if (s.ttsAuto) LangDetect.detect(c.text, s.ttsLang) else s.ttsLang
            tts.applyLang(s, lang, if (lang == s.ttsLang) s.ttsVoice else "")
            val ok = tts.speak(line, s.ttsVolume)
            spokenTimes.addLast(System.currentTimeMillis())
            synchronized(lock) {
                if (ok) spoken++
                put(c.copy(status = if (ok) CommentStatus.SPOKEN else CommentStatus.FILTERED, filterNote = if (ok) "" else "Lỗi đọc"))
                publishStats()
            }
            if (!ok && tts.error.value != null) delay(2000)
        }
    }

    private fun publishStatsLocked() = synchronized(lock) { publishStats() }

    companion object { const val MAX_ATTEMPTS = 8 }
}
