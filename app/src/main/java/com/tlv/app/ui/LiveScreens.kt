package com.tlv.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tlv.app.Graph
import com.tlv.app.live.ChannelParser
import com.tlv.app.live.ConnState
import com.tlv.app.service.LiveService
import kotlinx.coroutines.delay

/** Trả về hàm bắt đầu phiên LIVE: kiểm tra điều kiện, xin quyền thông báo rồi chạy Foreground Service. */
@Composable
fun rememberStartLive(): (String) -> Unit {
    val ctx = LocalContext.current
    val toast = LocalToaster.current
    var pending by remember { mutableStateOf<String?>(null) }

    fun go(ch: String) {
        Graph.update { copy(lastChannel = ch) }
        LiveService.start(ctx, ch)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast.error("Chưa cấp quyền thông báo: dịch vụ vẫn chạy nhưng không hiện thông báo điều khiển")
        pending?.let { go(it) }; pending = null
    }

    return start@{ raw ->
        val ch = ChannelParser.parse(raw)
        val s = Graph.auth.session.value
        when {
            ch == null -> toast.error("Tên tài khoản hoặc đường dẫn LIVE không hợp lệ")
            s == null -> toast.error("Bạn chưa đăng nhập")
            !s.emailVerified -> toast.error("Hãy xác minh email trước khi dùng chức năng LIVE")
            !Graph.currentSettings().bridgeUrl.startsWith("wss://") ->
                toast.error("Chưa cấu hình địa chỉ cầu nối (wss://) trong trang Kết nối")
            else -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) { pending = ch; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } else go(ch)
            }
        }
    }
}

@Composable
private fun connLabel(c: ConnState): Pair<String, androidx.compose.ui.graphics.Color> = when (c) {
    ConnState.Idle -> "Chưa kết nối" to TextDim
    ConnState.Connecting -> "Đang kết nối…" to Warn
    ConnState.Connected -> "Đang LIVE" to Ok
    is ConnState.Reconnecting -> "Kết nối lại lần ${c.attempt}" to Warn
    is ConnState.Failed -> "Lỗi: ${c.message}" to Bad
    ConnState.Ended -> "Livestream đã kết thúc" to TextDim
}

@Composable
fun HomeScreen(onConnect: () -> Unit, onComments: () -> Unit, onVoice: () -> Unit) {
    val e = Graph.engine
    val conn by e.conn.collectAsState()
    val stats by e.stats.collectAsState()
    val paused by e.paused.collectAsState()
    val speaking by e.speaking.collectAsState()
    val current by e.current.collectAsState()
    val channel by e.channel.collectAsState()
    val started by e.startedAt.collectAsState()
    val session by Graph.auth.session.collectAsState()
    val settings by Graph.settings.collectAsState()
    val startLive = rememberStartLive()
    val active = e.active

    val now by produceState(System.currentTimeMillis(), active) {
        while (active) { value = System.currentTimeMillis(); delay(1000) }
    }
    val (label, color) = connLabel(conn)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Xin chào, ${session?.username?.ifEmpty { "bạn" } ?: "bạn"} 👋", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(conn == ConnState.Connected, color = Bad)
                Spacer(Modifier.width(8.dp))
                Text(label, color = color, fontWeight = FontWeight.SemiBold)
            }
            AnimatedVisibility(active || channel.isNotEmpty()) {
                Column(Modifier.padding(top = 6.dp)) {
                    Dim("Đang theo dõi: @${channel.ifEmpty { settings.lastChannel }}")
                    if (active) Dim("Thời gian phiên: ${formatDuration(now - started)}")
                }
            }
        }

        // Thống kê bố cục bất đối xứng
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            GlassCard(Modifier.weight(1.3f).fillMaxHeight(), tint = Sky) {
                Dim("Bình luận nhận")
                AnimatedContent(stats.received, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "rc") {
                    Text("$it", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Dim("Đã lọc: ${stats.filtered}")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, tint = Ok) {
                    Dim("Đã đọc"); Text("${stats.spoken}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, tint = Warn) {
                    Dim("Đang chờ"); Text("${stats.queued}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bộ đọc giọng nói
        GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WaveBars(speaking && !paused)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when { !active -> "Bộ đọc chưa chạy"; paused -> "Tạm dừng đọc"; speaking -> "Đang đọc"; else -> "Sẵn sàng đọc" },
                        fontWeight = FontWeight.SemiBold,
                    )
                    val c = current
                    Dim(if (c != null) "${c.user}: ${c.text}" else "Chưa có bình luận đang đọc",
                        Modifier.padding(top = 2.dp))
                }
            }
        }

        // Nút chính
        if (!active) {
            NeonButton(
                if (settings.lastChannel.isNotEmpty()) "Bắt đầu đọc @${settings.lastChannel}" else "Kết nối LIVE",
                { if (settings.lastChannel.isNotEmpty()) startLive(settings.lastChannel) else onConnect() },
                Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow,
            )
            NeonButton("Kết nối LIVE khác", onConnect, Modifier.fillMaxWidth(), icon = Icons.Filled.Link, outlined = true)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (paused) NeonButton("Tiếp tục đọc", { e.resume() }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                else NeonButton("Tạm dừng đọc", { e.pause() }, Modifier.weight(1f), icon = Icons.Filled.Pause)
                NeonButton("Ngắt kết nối", { LiveService.stop(Graph.app) }, Modifier.weight(1f),
                    icon = Icons.Filled.LinkOff, outlined = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton("Bình luận", onComments, Modifier.weight(1f), icon = Icons.Filled.Forum, outlined = true)
            NeonButton("Giọng đọc", onVoice, Modifier.weight(1f), icon = Icons.Filled.RecordVoiceOver, outlined = true)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ConnectScreen() {
    val conn by Graph.engine.conn.collectAsState()
    val settings by Graph.settings.collectAsState()
    val session by Graph.auth.session.collectAsState()
    val startLive = rememberStartLive()
    var input by remember { mutableStateOf(settings.lastChannel) }
    var bridge by remember { mutableStateOf(settings.bridgeUrl) }
    val parsed = ChannelParser.parse(input)
    val (label, color) = connLabel(conn)
    val active = Graph.engine.active

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Trạng thái lớn ở đầu trang
        GlassCard(Modifier.fillMaxWidth(), tint = color) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(conn == ConnState.Connected, size = 14.dp)
                Spacer(Modifier.width(10.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (conn is ConnState.Reconnecting) Dim("Tự động thử lại với thời gian chờ tăng dần", Modifier.padding(top = 4.dp))
        }

        if (session?.emailVerified == false) {
            GlassCard(Modifier.fillMaxWidth(), tint = Warn) {
                Text("Email chưa được xác minh", fontWeight = FontWeight.SemiBold, color = Warn)
                Dim("Xác minh email (trang Bảo mật) để dùng chức năng LIVE.")
            }
        }

        SectionTitle("Livestream")
        TlvField(input, { input = it }, "Tên tài khoản (@name) hoặc đường dẫn LIVE",
            leading = Icons.Filled.AlternateEmail, enabled = !active,
            error = if (input.isNotBlank() && parsed == null) "Không nhận dạng được tài khoản" else null)

        SectionTitle("Cầu nối bình luận")
        TlvField(bridge, { bridge = it.trim(); Graph.update { copy(bridgeUrl = it.trim()) } },
            "Địa chỉ cầu nối (wss://…)", leading = Icons.Filled.Hub, enabled = !active,
            error = if (bridge.isNotEmpty() && !bridge.startsWith("wss://")) "Phải bắt đầu bằng wss://" else null)
        Dim("TikTok không có API công khai để đọc bình luận LIVE. App nhận bình luận từ một máy chủ cầu nối do bạn tự triển khai " +
            "(thư mục bridge/ trong repo, dùng thư viện mã nguồn mở tiktok-live-connector). App không hỏi hay lưu mật khẩu TikTok.")

        if (!active) {
            NeonButton("Kết nối", { startLive(input) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Link,
                state = if (conn == ConnState.Connecting) BtnState.Loading else BtnState.Idle,
                enabled = parsed != null)
        } else {
            NeonButton("Ngắt kết nối", { LiveService.stop(Graph.app) }, Modifier.fillMaxWidth(), icon = Icons.Filled.LinkOff, outlined = true)
        }
        Dim("Android có thể dừng dịch vụ nền khi tiết kiệm pin hoặc thiếu RAM. Nếu bị dừng, hãy kết nối lại.")
    }
}
