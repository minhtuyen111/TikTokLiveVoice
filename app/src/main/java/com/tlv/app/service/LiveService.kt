package com.tlv.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tlv.app.Graph
import com.tlv.app.MainActivity
import com.tlv.app.R
import com.tlv.app.live.ConnState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.FlowPreview::class)
/** Foreground Service (mediaPlayback): giữ tiến trình để nhận bình luận và đọc TTS khi app ở nền. */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** Vuốt app khỏi danh sách gần đây: dịch vụ nền vẫn chạy, không làm gì cả. */
    override fun onTaskRemoved(rootIntent: Intent?) { /* giữ phiên LIVE */ }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = Graph.engine
        when (intent?.action) {
            ACTION_START -> {
                val ch = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
                ServiceCompat.startForeground(
                    this, NOTIF_ID, build("Đang kết nối @$ch…", paused = false, ongoing = true),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                Graph.refreshMemesAsync()
                if (wakeLock?.isHeld != true) {
                    wakeLock = getSystemService(android.os.PowerManager::class.java)
                        .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "voicelive:live").apply { setReferenceCounted(false); acquire() }
                }
                engine.connect(ch)
                observe()
            }
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            ACTION_STOP -> { engine.disconnect(); stopSelf() }
            else -> { // khởi động lại không có lệnh: không tự chạy lại (tránh vòng lặp khởi động)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observe() {
        observer?.cancel()
        val e = Graph.engine
        observer = scope.launch {
            combine(e.conn, e.paused, e.stats, e.channel) { c, p, s, ch -> arrayOf<Any>(c, p, s, ch) }
                .sample(1000)
                .collect { a ->
                    val c = a[0] as ConnState
                    val paused = a[1] as Boolean
                    val s = a[2] as com.tlv.app.live.Stats
                    val ch = a[3] as String
                    val nm = getSystemService(NotificationManager::class.java)
                    when (c) {
                        is ConnState.Failed, ConnState.Ended, ConnState.Idle -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            if (c is ConnState.Failed || c is ConnState.Ended) {
                                val msg = if (c is ConnState.Failed) "Ngắt kết nối: ${c.message}" else "Livestream @$ch đã kết thúc"
                                nm.notify(NOTIF_ID + 1, build(msg, paused = false, ongoing = false))
                            }
                            stopSelf()
                        }
                        else -> {
                            val state = when (c) {
                                ConnState.Connected -> "LIVE @$ch"
                                is ConnState.Reconnecting -> "Đang kết nối lại (${c.attempt}) @$ch"
                                else -> "Đang kết nối @$ch…"
                            }
                            val reading = if (paused) "Tạm dừng đọc" else "Đang đọc"
                            nm.notify(NOTIF_ID, build("$state · $reading · ${s.spoken}/${s.received} · chờ ${s.queued}", paused, true))
                        }
                    }
                }
        }
    }

    private fun build(text: String, paused: Boolean, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (ongoing && Graph.currentSettings().notifShowControls) {
            b.addAction(0, if (paused) "Tiếp tục" else "Tạm dừng", action(if (paused) ACTION_RESUME else ACTION_PAUSE, 1))
            b.addAction(0, "Dừng", action(ACTION_STOP, 2))
        }
        return b.build()
    }

    private fun action(a: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, LiveService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        Graph.engine.disconnect() // giải phóng TTS và kết nối khi dịch vụ thực sự dừng
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "live"
        const val NOTIF_ID = 42
        const val ACTION_START = "com.tlv.app.START"
        const val ACTION_PAUSE = "com.tlv.app.PAUSE"
        const val ACTION_RESUME = "com.tlv.app.RESUME"
        const val ACTION_STOP = "com.tlv.app.STOP"
        const val EXTRA_CHANNEL = "channel"

        fun start(c: Context, channel: String) {
            c.startForegroundService(
                Intent(c, LiveService::class.java).setAction(ACTION_START).putExtra(EXTRA_CHANNEL, channel)
            )
        }

        fun stop(c: Context) {
            Graph.engine.disconnect()
            c.stopService(Intent(c, LiveService::class.java))
        }
    }
}
