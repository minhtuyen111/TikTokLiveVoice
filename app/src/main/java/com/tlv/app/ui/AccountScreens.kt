package com.tlv.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlv.app.BuildConfig
import com.tlv.app.Graph
import com.tlv.app.auth.AuthResult
import com.tlv.app.auth.Validators
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Lịch sử ----------
@Composable
fun HistoryScreen() {
    val owner = Graph.auth.session.collectAsState().value?.userId.orEmpty()
    val list by remember(owner) { Graph.db.sessions().observe(owner) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var confirm by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dim("Chỉ lưu số liệu tổng hợp (tối đa 100 phiên), không lưu nội dung bình luận.", Modifier.weight(1f))
            IconButton({ confirm = true }, enabled = list.isNotEmpty()) { Icon(Icons.Filled.DeleteSweep, "Xóa lịch sử") }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Dim("Chưa có phiên nào.") }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list.size, key = { list[it].id }) { i ->
                    val s = list[i]
                    GlassCard(Modifier.fillMaxWidth(), corner = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@${s.channel}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(formatDuration((s.endedAt ?: System.currentTimeMillis()) - s.startedAt), color = MaterialTheme.colorScheme.primary)
                        }
                        Dim("${fmt.format(Date(s.startedAt))}" + (s.endedAt?.let { " → ${fmt.format(Date(it))}" } ?: " (đang chạy hoặc bị dừng đột ngột)"))
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Nhận ${s.received}", fontSize = 13.sp)
                            Text("Đọc ${s.spoken}", fontSize = 13.sp, color = Ok)
                        }
                        if (s.lastState.isNotEmpty()) Dim("Trạng thái cuối: ${s.lastState}", Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Xóa toàn bộ lịch sử?") }, text = { Text("Không thể hoàn tác.") },
        confirmButton = { TextButton({ confirm = false; scope.launch { Graph.db.sessions().clear(owner); toast.success("Đã xóa lịch sử") } }) { Text("Xóa") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Hủy") } },
    )
}

// ---------- Tài khoản ----------
@Composable
fun AccountScreen(onLogout: () -> Unit, onSecurity: () -> Unit) {
    val s by Graph.auth.session.collectAsState()
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var state by remember { mutableStateOf(BtnState.Idle) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Neon, Sky))), contentAlignment = Alignment.Center,
        ) { Text((s?.username?.firstOrNull() ?: 'U').uppercase(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        Spacer(Modifier.height(12.dp))
        Text(s?.username.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Dim(s?.email.orEmpty())
        Spacer(Modifier.height(10.dp))
        AssistChip(
            onClick = {},
            label = { Text(if (s?.emailVerified == true) "Email đã xác minh" else "Email chưa xác minh") },
            leadingIcon = { Icon(if (s?.emailVerified == true) Icons.Filled.Verified else Icons.Filled.WarningAmber, null,
                tint = if (s?.emailVerified == true) Ok else Warn) },
        )
        Spacer(Modifier.height(20.dp))
        NeonButton("Làm mới trạng thái", {
            state = BtnState.Loading
            scope.launch { Graph.auth.refreshProfile(); state = BtnState.Success; delay(700); state = BtnState.Idle }
        }, Modifier.fillMaxWidth(), state = state, outlined = true, icon = Icons.Filled.Refresh)
        Spacer(Modifier.height(10.dp))
        NeonButton("Bảo mật tài khoản", onSecurity, Modifier.fillMaxWidth(), icon = Icons.Filled.Security)
        Spacer(Modifier.height(10.dp))
        NeonButton("Đăng xuất", onLogout, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.Logout)
    }
}

// ---------- Bảo mật ----------
@Composable
fun SecurityScreen(onLoggedOut: () -> Unit) {
    val s by Graph.auth.session.collectAsState()
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var cur by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var pwState by remember { mutableStateOf(BtnState.Idle) }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var delPw by remember { mutableStateOf("") }
    val eNew = Validators.password(n1)
    val eMatch = if (n1 != n2) "Hai mật khẩu chưa khớp" else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(Modifier.fillMaxWidth(), tint = if (s?.emailVerified == true) Ok else Warn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s?.emailVerified == true) Icons.Filled.Verified else Icons.Filled.MarkEmailUnread, null,
                    tint = if (s?.emailVerified == true) Ok else Warn)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s?.emailVerified == true) "Email đã xác minh" else "Email chưa xác minh", fontWeight = FontWeight.SemiBold)
                    Dim(s?.email.orEmpty())
                }
                TextButton({ scope.launch { Graph.auth.refreshProfile(); toast.success("Đã cập nhật") } }) { Text("Kiểm tra") }
            }
        }

        SectionTitle("Đổi mật khẩu")
        TlvField(cur, { cur = it }, "Mật khẩu hiện tại", leading = Icons.Filled.Lock, password = true)
        TlvField(n1, { n1 = it }, "Mật khẩu mới", leading = Icons.Filled.Key, password = true, error = if (n1.isNotEmpty()) eNew else null)
        if (n1.isNotEmpty()) StrengthBar(Validators.strength(n1))
        TlvField(n2, { n2 = it }, "Xác nhận mật khẩu mới", leading = Icons.Filled.LockReset, password = true, error = if (n2.isNotEmpty()) eMatch else null)
        NeonButton("Đổi mật khẩu", {
            pwState = BtnState.Loading
            scope.launch {
                when (val r = Graph.auth.changePassword(cur, n1)) {
                    is AuthResult.Ok -> { pwState = BtnState.Success; toast.success("Đã đổi mật khẩu"); cur = ""; n1 = ""; n2 = "" }
                    is AuthResult.Fail -> { pwState = BtnState.Failure; toast.error(r.message) }
                }
                delay(1000); pwState = BtnState.Idle
            }
        }, Modifier.fillMaxWidth(), state = pwState, enabled = cur.isNotEmpty() && eNew == null && eMatch == null)

        SectionTitle("Phiên đăng nhập và thiết bị")
        GlassCard(Modifier.fillMaxWidth(), corner = 18.dp) {
            Text("Thiết bị này", fontWeight = FontWeight.SemiBold)
            Dim("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
            Dim("Phiên được mã hóa bằng Android Keystore.", Modifier.padding(top = 2.dp))
        }
        Dim("Supabase không cung cấp danh sách phiên cho ứng dụng khách. Để thu hồi, hãy đăng xuất khỏi mọi thiết bị.")
        NeonButton("Đăng xuất khỏi mọi thiết bị", { confirmAll = true }, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.DevicesOther)

        SectionTitle("Vùng nguy hiểm")
        NeonButton("Yêu cầu xóa tài khoản", { confirmDel = true }, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.DeleteForever)
    }

    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Đăng xuất mọi thiết bị?") }, text = { Text("Mọi phiên đăng nhập sẽ bị thu hồi, kể cả thiết bị này.") },
        confirmButton = { TextButton({ confirmAll = false; scope.launch { Graph.logout(global = true); onLoggedOut() } }) { Text("Đăng xuất") } },
        dismissButton = { TextButton({ confirmAll = false }) { Text("Hủy") } },
    )
    if (confirmDel) AlertDialog(
        onDismissRequest = { confirmDel = false },
        title = { Text("Yêu cầu xóa tài khoản") },
        text = { Column {
            Text("Nhập mật khẩu để xác nhận. Yêu cầu sẽ được quản trị xử lý.")
            Spacer(Modifier.height(8.dp))
            TlvField(delPw, { delPw = it }, "Mật khẩu", password = true)
        } },
        confirmButton = { TextButton({
            scope.launch {
                if (!Graph.auth.reauth(delPw)) { toast.error("Mật khẩu không đúng"); return@launch }
                when (val r = Graph.auth.requestDeletion()) {
                    is AuthResult.Ok -> { toast.success(r.message); confirmDel = false; delPw = "" }
                    is AuthResult.Fail -> toast.error(r.message)
                }
            }
        }, enabled = delPw.isNotEmpty()) { Text("Gửi yêu cầu") } },
        dismissButton = { TextButton({ confirmDel = false; delPw = "" }) { Text("Hủy") } },
    )
}

// ---------- Giao diện ----------
@Composable
fun AppearanceScreen() {
    val s by Graph.settings.collectAsState()
    val accentNames = listOf("Tím neon", "Xanh tím", "Xanh dương", "Hồng", "Xanh lá", "Cam")
    val bgNames = listOf("Đêm", "Đen AMOLED", "Đại dương", "Rừng", "Hoàng hôn", "Xám đá")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Ngôn ngữ ứng dụng")
        APP_LANGS.forEach { (code, name) ->
            val on = s.appLang == code
            GlassCard(Modifier.fillMaxWidth().clickable { Graph.update { copy(appLang = code) } }, corner = 16.dp,
                tint = if (on) MaterialTheme.colorScheme.primary else Sky) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (on) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        SwitchRow("Giao diện tối", "Tắt để dùng giao diện sáng", s.darkTheme) { v -> Graph.update { copy(darkTheme = v) } }
        SectionTitle("Màu nền ứng dụng")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BG_PALETTES.forEachIndexed { i, p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { Graph.update { copy(bgStyle = i) } }) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(p.bg, p.surfaceHi)))
                            .border(if (s.bgStyle == i) 3.dp else 1.dp, if (s.bgStyle == i) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (s.bgStyle == i) Icon(Icons.Filled.Check, "Đang chọn", tint = Color.White) }
                    Text(bgNames[i], fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        SectionTitle("Màu nhấn")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ACCENTS.forEachIndexed { i, c ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { Graph.update { copy(accent = i) } }) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                        if (s.accent == i) Icon(Icons.Filled.Check, "Đang chọn", tint = Color.White)
                    }
                    Text(accentNames[i], fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        SwitchRow("Giảm chuyển động", "Tắt hiệu ứng lặp liên tục (sóng âm, chấm LIVE)", s.reduceMotion) { v -> Graph.update { copy(reduceMotion = v) } }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- Thông báo ----------
@Composable
fun NotificationsScreen() {
    val s by Graph.settings.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow("Nút điều khiển trong thông báo", "Tạm dừng / Tiếp tục / Dừng ngay trên thông báo", s.notifShowControls) { v -> Graph.update { copy(notifShowControls = v) } }
        NeonButton("Cài đặt thông báo của hệ thống", {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.Notifications)
        SectionTitle("Chạy nền ổn định")
        Dim("Một số hãng máy (Xiaomi, Oppo, Samsung…) dừng ứng dụng nền khi tiết kiệm pin. Bạn có thể tự chọn cho app chạy không hạn chế trong cài đặt pin của hệ thống.")
        NeonButton("Mở cài đặt tối ưu hóa pin", {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.BatteryChargingFull)
    }
}

// ---------- Hub cài đặt ----------
@Composable
fun SettingsHub(go: (String) -> Unit) {
    data class HubItem(val route: String, val title: String, val icon: ImageVector, val sub: String)
    val rows = listOf(
        HubItem("voice", "Giọng đọc", Icons.Filled.RecordVoiceOver, "Giọng, tốc độ, cao độ"),
        HubItem("memes", "Âm thanh meme", Icons.Filled.MusicNote, "Tiếng theo dõi, chia sẻ, tặng quà"),
        HubItem("filters", "Bộ lọc bình luận", Icons.Filled.FilterAlt, "Từ khóa, chống spam, hàng đợi"),
        HubItem("appearance", "Giao diện", Icons.Filled.Palette, "Sáng/tối, màu nhấn, giảm chuyển động"),
        HubItem("notifications", "Thông báo", Icons.Filled.Notifications, "Điều khiển nền, tối ưu pin"),
        HubItem("security", "Bảo mật", Icons.Filled.Security, "Mật khẩu, phiên, xóa tài khoản"),
        HubItem("account", "Quản lý tài khoản", Icons.Filled.AccountCircle, "Hồ sơ và đăng xuất"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        rows.forEach { r ->
            ListItem(
                headlineContent = { Text(r.title, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(r.sub) },
                leadingContent = { Icon(r.icon, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { go(r.route) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Dim("Phiên bản ${BuildConfig.VERSION_NAME}", Modifier.padding(16.dp))
    }
}

// ---------- Trang tĩnh ----------
@Composable
private fun TextPage(vararg parts: Pair<String, String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEach { (h, b) ->
            if (h.isNotEmpty()) Text(h, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
            Text(b, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun HelpScreen() = TextPage(
    "Cách dùng nhanh" to "1) Đăng ký và xác minh email. 2) Vào Kết nối TikTok LIVE, nhập tên kênh đang LIVE (ví dụ @tenkenh). 3) Bấm Kết nối. Bình luận sẽ hiện ra và được đọc lần lượt, không cần cài đặt thêm.",
    "Âm thanh meme" to "Vào Âm thanh meme để bật, tắt hoặc chọn tiếng cho sự kiện theo dõi, chia sẻ, tặng quà. Thư viện âm thanh do quản trị viên cung cấp.",
    "Không nghe thấy tiếng?" to "Kiểm tra âm lượng nội dung, vào Giọng đọc và bấm Nghe thử. Nếu báo thiếu dữ liệu, cài giọng tiếng Việt trong Cài đặt Android > Chuyển văn bản thành giọng nói.",
    "Bị ngắt khi tắt màn hình?" to "Vào Thông báo > Cài đặt tối ưu hóa pin và cho phép app chạy nền. Android vẫn có thể dừng dịch vụ khi thiếu RAM; hãy kết nối lại nếu cần.",
    "Không có bình luận?" to "Livestream phải đang phát công khai. Nếu nhiều kênh cùng lỗi, dịch vụ nhận bình luận có thể đang bảo trì; hãy thử lại sau hoặc báo qua Discord.",
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo thương hiệu ứng dụng hiện tại vẫn giữ nguyên; ảnh creator chỉ nằm trong trang Giới thiệu.
        androidx.compose.foundation.Image(
            painter = painterResource(id = com.tlv.app.R.drawable.creator_logo),
            contentDescription = "Logo nhà phát triển",
            modifier = Modifier.size(148.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(16.dp))
        Text("Voice Live TikTok", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Dim("Phiên bản ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 4.dp))
        Dim("Biến bình luận LIVE công khai thành giọng nói.", Modifier.padding(top = 12.dp))
        Dim("Không liên kết chính thức với TikTok hoặc Discord. Tên và biểu tượng thuộc về chủ sở hữu tương ứng.", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { openLink("https://www.tiktok.com/@minhtuyen523?_r=1&_t=ZS-9AQqF302TUC") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            FloatingLogo(com.tlv.app.R.drawable.logo_tiktok, 28.dp, 0)
            Spacer(Modifier.width(10.dp))
            Text("TikTok của nhà phát triển")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { openLink("https://discord.gg/S9AFRTJpf") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            FloatingLogo(com.tlv.app.R.drawable.logo_discord, 28.dp, 1)
            Spacer(Modifier.width(10.dp))
            Text("Tham gia Discord")
        }
        Spacer(Modifier.height(14.dp))
        Dim("Liên hệ và cộng đồng chính thức của ứng dụng.", Modifier.padding(top = 2.dp))
    }
}
