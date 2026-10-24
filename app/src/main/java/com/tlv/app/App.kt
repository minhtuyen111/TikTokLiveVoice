package com.tlv.app

import android.app.Application
import android.content.Context
import com.tlv.app.audio.MemePlayer
import com.tlv.app.auth.AuthRepo
import com.tlv.app.data.Meme
import com.tlv.app.data.Remote
import com.tlv.app.security.Guard
import com.tlv.app.security.Verdict
import com.tlv.app.data.SettingsRepo
import com.tlv.app.history.AppDb
import com.tlv.app.live.LiveEngine
import com.tlv.app.live.LiveSource
import com.tlv.app.security.SessionStore
import com.tlv.app.service.LiveService
import com.tlv.app.settings.AppSettings
import com.tlv.app.tts.TtsController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Đối tượng dùng chung (đơn giản, không cần DI framework). */
object Graph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var app: Application
    private set
    lateinit var settingsRepo: SettingsRepo
    private set
    lateinit var db: AppDb
    private set
    lateinit var sessionStore: SessionStore
    private set
    lateinit var auth: AuthRepo
    private set
    lateinit var tts: TtsController
    private set
    lateinit var engine: LiveEngine
    private set
    lateinit var remote: Remote
    private set
    private lateinit var memePlayer: MemePlayer

    /** Danh sách âm thanh meme (do admin quản lý) và kết quả kiểm tra an toàn môi trường. */
    val memeList = MutableStateFlow<List<Meme>>(emptyList())
    val verdict = MutableStateFlow(Verdict.OK)
    val updateInfo = MutableStateFlow<com.tlv.app.data.UpdateInfo?>(null)
    val resumeTick = MutableStateFlow(0)
    val entitlement = MutableStateFlow<com.tlv.app.data.Entitlement?>(null)
    @Volatile private var lastUpdateCheck = 0L
    val announcement = MutableStateFlow<com.tlv.app.data.Announcement?>(null)
    @Volatile var foreground = false
    private lateinit var appCtx: Context
    @Volatile private var lastNotified = ""

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded

    /** Token khôi phục mật khẩu nhận qua liên kết trong email (tlv://auth#...). */
    val recoveryToken = MutableStateFlow<String?>(null)

    fun init(a: Application) {
        app = a
        settingsRepo = SettingsRepo(a)
        db = AppDb.create(a)
        sessionStore = SessionStore(a)
        appCtx = a.applicationContext
        auth = AuthRepo(sessionStore)
        remote = Remote(auth, { _settings.value.bridgeUrl }, { u -> update { copy(bridgeUrl = u) } })
        memePlayer = MemePlayer(a)
        tts = TtsController(a, com.tlv.app.tts.CloudTts(a, auth))
        engine = LiveEngine(
            source = LiveSource(), tts = tts, dao = db.sessions(),
            settings = { _settings.value },
            ownerId = { auth.session.value?.userId.orEmpty() },
            token = { auth.validToken() },
            bridge = { remote.bridgeUrl() },
            eventSound = { kind -> playEventAndWait(kind) },
        )
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val v = Guard.check(a)
                if (v != Verdict.OK) { verdict.value = v; engine.disconnect(); break }
                delay(20_000)
            }
        }
        // Báo "đang dùng app" và kênh đang theo dõi mỗi 60 giây khi đã đăng nhập (admin xem được ai đang online).
        scope.launch {
            auth.session.collectLatest { s ->
                if (s != null) while (isActive) {
                    remote.touch(if (engine.active) engine.channel.value else null)
                    remote.entitlement()?.let { entitlement.value = it }
                    checkAnnouncement()
                    delay(60_000)
                }
            }
        }
        loadSavedMemes()
        checkUpdate()
        scope.launch {
            _settings.value = settingsRepo.settingsFirst()
            _settingsLoaded.value = true
        }
    }

    private suspend fun SettingsRepo.settingsFirst() = flow.first()

    fun currentSettings() = _settings.value

    /** So mã phiên bản hiện tại với bản mới nhất admin đã phát hành; tối đa 10 phút một lần. */
    fun checkUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheck < 600_000) return
        lastUpdateCheck = now
        scope.launch {
            val info = remote.updateInfo()
            updateInfo.value = if (info != null && info.code > BuildConfig.VERSION_CODE) info else null
        }
    }

    suspend fun refreshMemes() {
        remote.memes()?.let { list ->
            memeList.value = list
            memePlayer.prefetch(list.map { remote.publicUrl(it.path) })
            // Lưu danh sách để lần sau (kể cả mất mạng) vẫn phát được âm đã tải.
            runCatching {
                val a = org.json.JSONArray()
                list.forEach { a.put(org.json.JSONObject().put("id", it.id).put("event", it.event).put("title", it.title).put("path", it.path)) }
                prefs.edit().putString("memes_json", a.toString()).apply()
            }
        }
    }
    private fun loadSavedMemes() {
        runCatching {
            val a = org.json.JSONArray(prefs.getString("memes_json", null) ?: return)
            memeList.value = (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { o -> Meme(o.optString("id"), o.optString("event"), o.optString("title"), o.optString("path")) }
            }
        }
    }
    private val prefs get() = appCtx.getSharedPreferences("tlv_misc", Context.MODE_PRIVATE)

    /** Lấy thông báo mới nhất của quản trị; chưa xem thì hiện hộp thoại, và báo bằng thông báo hệ thống nếu app đang ở nền. */
    suspend fun checkAnnouncement() {
        val a = remote.latestAnnouncement() ?: return
        if (System.currentTimeMillis() - a.createdMs > 3 * 86_400_000L) return
        if (a.id == prefs.getString("seen_ann", null)) return
        announcement.value = a
        if (!foreground && a.id != lastNotified) { lastNotified = a.id; notifyAnnouncement(a) }
    }

    fun dismissAnnouncement() {
        announcement.value?.let { prefs.edit().putString("seen_ann", it.id).apply() }
        announcement.value = null
    }

    private fun notifyAnnouncement(a: com.tlv.app.data.Announcement) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(appCtx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val nm = appCtx.getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(android.app.NotificationChannel("announce", "Thông báo từ quản trị", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val open = android.app.PendingIntent.getActivity(
                appCtx, 0, android.content.Intent(appCtx, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = androidx.core.app.NotificationCompat.Builder(appCtx, "announce")
                .setSmallIcon(R.drawable.ic_notif).setContentTitle(a.title).setContentText(a.body)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(a.body))
                .setAutoCancel(true).setContentIntent(open).build()
            nm.notify(7001, n)
        }
    }

    fun refreshAnnouncementAsync() { scope.launch { if (auth.session.value != null) checkAnnouncement() } }

    fun refreshEntitlement() { scope.launch { remote.entitlement()?.let { entitlement.value = it } } }
    fun refreshMemesAsync() { scope.launch { refreshMemes() } }

    /** Chọn và phát âm thanh meme cho sự kiện theo lựa chọn của người dùng (chỉ chọn trong danh sách của admin). */
    suspend fun playEventAndWait(kind: String) {
        val s = _settings.value
        val sel = when (kind) { "follow" -> s.memeFollow; "share" -> s.memeShare; "gift" -> s.memeGift; else -> s.memeIdle }
        if (sel == "off") return
        val pool = memeList.value.filter { it.event == kind }
        val m = (if (sel == "random") null else pool.firstOrNull { it.id == sel }) ?: pool.randomOrNull() ?: return
        memePlayer.playAndWait(remote.publicUrl(m.path), if (kind == "idle") 12_000L else 6_000L)
    }

    fun previewMeme(m: Meme) = memePlayer.play(remote.publicUrl(m.path), force = true)

    /** Cập nhật ngay trong bộ nhớ (UI phản hồi tức thời) rồi lưu bền vững. */
    fun update(block: AppSettings.() -> AppSettings) {
        val n = _settings.value.block()
        _settings.value = n
        scope.launch { settingsRepo.save(n) }
    }

    suspend fun logout(global: Boolean = false) {
        LiveService.stop(app)
        auth.signOut(global)
    }
}
