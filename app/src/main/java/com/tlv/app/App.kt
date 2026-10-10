package com.tlv.app

import android.app.Application
import com.tlv.app.auth.AuthRepo
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
        auth = AuthRepo(sessionStore)
        tts = TtsController(a)
        engine = LiveEngine(
            source = LiveSource(), tts = tts, dao = db.sessions(),
            settings = { _settings.value },
            ownerId = { auth.session.value?.userId.orEmpty() },
            token = { auth.validToken() },
        )
        scope.launch {
            _settings.value = settingsRepo.settingsFirst()
            _settingsLoaded.value = true
        }
    }

    private suspend fun SettingsRepo.settingsFirst() = flow.first()

    fun currentSettings() = _settings.value

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
