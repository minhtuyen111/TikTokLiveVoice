@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tlv.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tlv.app.BuildConfig
import com.tlv.app.Graph
import com.tlv.app.auth.AuthResult
import com.tlv.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun nameOf(ctx: android.content.Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "file"

private fun ago(ms: Long?): String {
    if (ms == null) return "Chưa từng hoạt động"
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 150 -> "Đang online"
        d < 3600 -> "${d / 60} phút trước"
        d < 86400 -> "${d / 3600} giờ trước"
        else -> "${d / 86400} ngày trước"
    }
}

private fun dmy(ms: Long) = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))

/** Khung chứa một nhóm trường: tiêu đề + thẻ, các trường cách nhau rộng rãi. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
    )
}

@Composable
private fun Stat(title: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    GlassCard(modifier, corner = 20.dp, tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Dim(title)
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        }
    }
}

/** Trang quản trị. */
@Composable
fun AdminScreen() {
    val session by Graph.auth.session.collectAsState()
    if (session?.isAdmin != true) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Dim("Chỉ quản trị viên mới truy cập được trang này.") }
        return
    }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Người dùng" to Icons.Filled.Groups, "Gói" to Icons.Filled.Inventory2, "Free" to Icons.Filled.Eco,
        "Âm thanh meme" to Icons.Filled.MusicNote, "Thanh toán" to Icons.Filled.Payments, "Thông báo" to Icons.Filled.Campaign,
        "Hệ thống" to Icons.Filled.Settings,
    )
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Color.Transparent, divider = {}) {
            tabs.forEachIndexed { i, (t, ic) ->
                Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }, icon = { Icon(ic, null, Modifier.size(20.dp)) })
            }
        }
        when (tab) {
            0 -> UsersTab(session?.userId.orEmpty())
            1 -> PlansTab()
            2 -> FreeTab()
            3 -> MemeTab()
            4 -> PayTab()
            5 -> AnnounceTab()
            else -> SystemTab()
        }
    }
}

// ───────────────────────── Người dùng ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersTab(selfId: String) {
    val toast = LocalToaster.current
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<AdminUser>?>(null) }
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var adjust by remember { mutableStateOf<AdminUser?>(null) }
    var grant by remember { mutableStateOf<AdminUser?>(null) }
    LaunchedEffect(tick) { users = Graph.remote.adminUsers() ?: users }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }

    val all = users.orEmpty()
    val now = System.currentTimeMillis()
    val shown = all.filter { u ->
        (q.isBlank() || u.username.contains(q, true) || u.email.contains(q, true)) && when (filter) {
            1 -> u.online(now); 2 -> u.role == "admin"; 3 -> u.status == "locked"; 4 -> u.tier != "free"; else -> true
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Tổng số", all.size, Icons.Filled.Groups, Sky, Modifier.weight(1f))
                    Stat("Đang online", all.count { it.online(now) }, Icons.Filled.Wifi, Ok, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Trả phí", all.count { it.tier != "free" }, Icons.Filled.WorkspacePremium, Warn, Modifier.weight(1f))
                    Stat("Bị khóa", all.count { it.status == "locked" }, Icons.Filled.Lock, Bad, Modifier.weight(1f))
                }
                TlvField(q, { q = it }, "Tìm theo tên hoặc email", leading = Icons.Filled.Search)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tất cả", "Đang online", "Quản trị viên", "Bị khóa", "Trả phí").forEachIndexed { i, t ->
                        FilterChip(filter == i, { filter = i }, label = { Text(t, fontSize = 13.sp) })
                    }
                }
                if (users == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        items(shown, key = { it.id }) { u ->
            val self = u.id == selfId
            val online = u.online(now)
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, tint = if (u.status == "locked") Bad else if (online) Ok else Sky) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                .border(2.dp, if (online) Ok else Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { androidx.compose.material3.Text(u.username.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(u.username, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Dim(u.email)
                        }
                        TierBadge(u.tier)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) Ok else TextDim))
                        Dim(ago(u.lastSeenMs) + if (online && u.watching.isNotEmpty()) " · @${u.watching}" else "")
                        if (u.role == "admin") AssistChip({}, label = { Text("Quản trị", fontSize = 11.sp) })
                        if (u.status == "locked") Text("Bị khóa", color = Bad, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (u.deletion) Text("Có yêu cầu xóa", color = Warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column { Dim("Số dư"); Text(money(u.balance), fontWeight = FontWeight.SemiBold) }
                        if (u.planExpiresMs != null) Column { Dim("Hết hạn"); Text(dmy(u.planExpiresMs), fontWeight = FontWeight.SemiBold) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip({ adjust = u }, label = { Text("Cộng/trừ tiền", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Filled.Payments, null, Modifier.size(18.dp)) })
                        AssistChip({ grant = u }, label = { Text("Cấp gói", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Filled.CardGiftcard, null, Modifier.size(18.dp)) })
                        if (!self) {
                            AssistChip({
                                scope.launch {
                                    val r = Graph.remote.setStatus(u.id, if (u.status == "locked") "active" else "locked")
                                    if (r is AuthResult.Ok) { toast.success(r.message); tick++ } else toast.error((r as AuthResult.Fail).message)
                                }
                            }, label = { Text(if (u.status == "locked") "Mở khóa" else "Khóa", fontSize = 13.sp) },
                                leadingIcon = { Icon(if (u.status == "locked") Icons.Filled.LockOpen else Icons.Filled.Lock, null, Modifier.size(18.dp)) })
                            AssistChip({
                                scope.launch {
                                    val r = Graph.remote.setRole(u.id, if (u.role == "admin") "user" else "admin")
                                    if (r is AuthResult.Ok) { toast.success(r.message); tick++ } else toast.error((r as AuthResult.Fail).message)
                                }
                            }, label = { Text(if (u.role == "admin") "Gỡ quyền admin" else "Cấp quyền admin", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
        }
    }
    adjust?.let { u -> AdjustDialog(u) { changed -> adjust = null; if (changed) tick++ } }
    grant?.let { u -> GrantDialog(u) { changed -> grant = null; if (changed) tick++ } }
}

@Composable
private fun AdjustDialog(u: AdminUser, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val v = amount.toLongOrNull()
    FancyDialog(onDismiss = { onDone(false) }, dismissible = !busy, glow = Ok) {
        androidx.compose.material3.Text("Cộng/trừ tiền", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = PopupText)
        androidx.compose.material3.Text("${u.username} · ${money(u.balance)}", color = PopupDim)
        TlvField(amount, { amount = it.filterIndexed { i, ch -> ch.isDigit() || (i == 0 && ch == '-') }.take(10) }, "Số tiền (âm để trừ)", leading = Icons.Filled.Payments)
        TlvField(note, { note = it.take(80) }, "Ghi chú", leading = Icons.Filled.Description)
        NeonButton("Áp dụng", {
            busy = true
            scope.launch {
                when (val r = Graph.remote.adjustWallet(u.id, v ?: 0, note)) {
                    is AuthResult.Ok -> { toast.success("Đã cập nhật"); onDone(true) }
                    is AuthResult.Fail -> { toast.error(r.message); busy = false }
                }
            }
        }, Modifier.fillMaxWidth(), state = if (busy) BtnState.Loading else BtnState.Idle, enabled = v != null && v != 0L, icon = Icons.Filled.Check)
    }
}

@Composable
private fun GrantDialog(u: AdminUser, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    LaunchedEffect(Unit) { plans = Graph.remote.plans() }
    FancyDialog(onDismiss = { onDone(false) }, glow = Warn) {
        androidx.compose.material3.Text("Cấp gói", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = PopupText)
        androidx.compose.material3.Text(u.username, color = PopupDim)
        if (plans == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plans.orEmpty().forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TierBadge(p.tier)
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.Text(p.title, Modifier.weight(1f), color = PopupText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({
                        scope.launch {
                            when (val r = Graph.remote.adminGrant(u.id, p.id)) {
                                is AuthResult.Ok -> { toast.success("Đã cấp ${p.title}"); onDone(true) }
                                is AuthResult.Fail -> toast.error(r.message)
                            }
                        }
                    }) { androidx.compose.material3.Text("Cấp", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ───────────────────────── Gói ─────────────────────────

@Composable
private fun PlansTab() {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    var edit by remember { mutableStateOf<Plan?>(null) }
    var creating by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { plans = Graph.remote.plans() ?: plans }

    Page {
        NeonButton("Thêm gói", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
        val list = plans
        if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        list.orEmpty().forEach { p ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                PlanCover(p.cover, Modifier.fillMaxWidth().height(84.dp)) {
                    Row(Modifier.align(Alignment.TopStart).padding(12.dp)) { TierBadge(p.tier) }
                    Text(p.title, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (!p.active) Text("Đã ẩn", Modifier.align(Alignment.TopEnd).padding(12.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column { Dim("Giá"); Text(money(p.price), fontWeight = FontWeight.Bold) }
                        Column { Dim("Số ngày"); Text("${p.days}", fontWeight = FontWeight.Bold) }
                        Column { Dim("Bình luận/ngày"); Text(if (p.maxComments <= 0) "∞" else "%,d".format(p.maxComments), fontWeight = FontWeight.Bold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip({ edit = p }, label = { Text("Sửa") }, leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) })
                        AssistChip({
                            scope.launch {
                                val r = Graph.remote.deletePlan(p.id)
                                if (r is AuthResult.Ok) { toast.success(r.message); tick++ } else toast.error((r as AuthResult.Fail).message)
                            }
                        }, label = { Text("Xóa") }, leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)) })
                    }
                }
            }
        }
    }
    if (creating) PlanEditor(null) { changed -> creating = false; if (changed) tick++ }
    edit?.let { p -> PlanEditor(p) { changed -> edit = null; if (changed) tick++ } }
}

@Composable
private fun PlanEditor(plan: Plan?, onClose: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var tier by remember { mutableStateOf(plan?.tier ?: "pro") }
    var title by remember { mutableStateOf(plan?.title.orEmpty()) }
    var days by remember { mutableStateOf(plan?.days?.toString() ?: "7") }
    var price by remember { mutableStateOf(plan?.price?.toString() ?: "") }
    var max by remember { mutableStateOf(plan?.maxComments?.toString() ?: "0") }
    var desc by remember { mutableStateOf(plan?.description.orEmpty()) }
    var perks by remember { mutableStateOf(plan?.perks.orEmpty()) }
    var cover by remember { mutableIntStateOf(plan?.cover ?: 0) }
    var sort by remember { mutableStateOf(plan?.sort?.toString() ?: "0") }
    var active by remember { mutableStateOf(plan?.active ?: true) }
    var state by remember { mutableStateOf(BtnState.Idle) }
    val ok = title.isNotBlank() && (days.toIntOrNull() ?: 0) in 1..3660 && (price.toLongOrNull() ?: 0) >= 1000

    Dialog({ onClose(false) }, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BackButton({ onClose(false) })
                    Spacer(Modifier.width(12.dp))
                    Text(if (plan == null) "Thêm gói" else "Sửa gói", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PlanCover(cover, Modifier.fillMaxWidth().height(110.dp)) {
                        Row(Modifier.align(Alignment.TopStart).padding(12.dp)) { TierBadge(tier) }
                        Text(title.ifBlank { "Tên gói" }, Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Section("Cấp") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(tier == "pro", { tier = "pro" }, label = { Text("Pro") })
                            FilterChip(tier == "promax", { tier = "promax" }, label = { Text("Pro Max") })
                        }
                    }
                    Section("Thông tin") {
                        TlvField(title, { title = it.take(60) }, "Tên gói", leading = Icons.Filled.Title)
                        TlvField(days, { days = it.filter(Char::isDigit).take(4) }, "Số ngày", leading = Icons.Filled.CalendarMonth)
                        TlvField(price, { price = it.filter(Char::isDigit).take(9) }, "Giá (₫)", leading = Icons.Filled.Payments)
                        TlvField(max, { max = it.filter(Char::isDigit).take(7) }, "Bình luận/ngày (0 = ∞)", leading = Icons.Filled.Chat)
                    }
                    Section("Nội dung") {
                        TlvField(desc, { desc = it.take(240) }, "Mô tả gói", leading = Icons.Filled.Description)
                        OutlinedTextField(perks, { perks = it.take(600) }, label = { Text("Quyền lợi (mỗi dòng một mục)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    Section("Ảnh bìa") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (i in 0 until COVER_COUNT) {
                                PlanCover(i, Modifier.size(64.dp).border(if (cover == i) 3.dp else 1.dp, if (cover == i) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF), RoundedCornerShape(20.dp)).clickable { cover = i })
                            }
                        }
                    }
                    Section("Hiển thị") {
                        SwitchRow("Đang bán", "", active) { active = it }
                        TlvField(sort, { sort = it.filter(Char::isDigit).take(4) }, "Thứ tự", leading = Icons.Filled.Pin)
                    }
                    NeonButton("Lưu", {
                        state = BtnState.Loading
                        scope.launch {
                            val p = Plan(
                                plan?.id ?: "plan_${System.currentTimeMillis() / 1000}", tier, title, days.toIntOrNull() ?: 7,
                                price.toLongOrNull() ?: 0, max.toIntOrNull() ?: 0, cover, desc, perks, sort.toIntOrNull() ?: 0, active,
                            )
                            when (val r = Graph.remote.savePlan(p)) {
                                is AuthResult.Ok -> { state = BtnState.Success; toast.success(r.message); delay(500); onClose(true) }
                                is AuthResult.Fail -> { state = BtnState.Failure; toast.error(r.message); delay(900); state = BtnState.Idle }
                            }
                        }
                    }, Modifier.fillMaxWidth(), state = state, enabled = ok, icon = Icons.Filled.Save)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ───────────────────────── Free ─────────────────────────

@Composable
private fun FreeTab() {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var days by remember { mutableStateOf("") }
    var cap by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(BtnState.Idle) }
    LaunchedEffect(Unit) {
        Graph.remote.freeConfig()?.let { (d, c) -> days = d.toString(); cap = c.toString(); loaded = true }
    }
    Page {
        Section("Bản Free") {
            if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            TlvField(days, { days = it.filter(Char::isDigit).take(5) }, "Số ngày dùng thử", leading = Icons.Filled.CalendarMonth)
            TlvField(cap, { cap = it.filter(Char::isDigit).take(7) }, "Bình luận tối đa mỗi ngày (0 = ∞)", leading = Icons.Filled.Chat)
            NeonButton("Lưu", {
                state = BtnState.Loading
                scope.launch {
                    when (val r = Graph.remote.saveFreeConfig(days.toIntOrNull() ?: 0, cap.toIntOrNull() ?: 0)) {
                        is AuthResult.Ok -> { state = BtnState.Success; toast.success(r.message) }
                        is AuthResult.Fail -> { state = BtnState.Failure; toast.error(r.message) }
                    }
                    delay(900); state = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = state, enabled = loaded && days.isNotEmpty() && cap.isNotEmpty(), icon = Icons.Filled.Save)
        }
    }
}

// ───────────────────────── Âm thanh meme ─────────────────────────

@Composable
private fun MemeTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val list by Graph.memeList.collectAsState()
    var event by remember { mutableStateOf("follow") }
    var title by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var upState by remember { mutableStateOf(BtnState.Idle) }
    var toDelete by remember { mutableStateOf<Meme?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) picked = u }
    LaunchedEffect(Unit) { Graph.refreshMemes() }

    Page {
        Section("Thêm âm thanh") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEME_EVENTS.forEach { (ev, label) -> FilterChip(event == ev, { event = ev }, label = { Text(label) }) }
            }
            TlvField(title, { title = it.take(60) }, "Tên âm thanh", leading = Icons.Filled.Title)
            NeonButton(picked?.let { "✓ ${nameOf(ctx, it)}" } ?: "Chọn file âm thanh", { picker.launch("audio/*") },
                Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.AudioFile)
            NeonButton("Tải lên", {
                val u = picked ?: return@NeonButton
                upState = BtnState.Loading
                scope.launch {
                    when (val r = Graph.remote.addMeme(ctx, event, title, u)) {
                        is AuthResult.Ok -> { upState = BtnState.Success; toast.success(r.message); title = ""; picked = null; Graph.refreshMemes() }
                        is AuthResult.Fail -> { upState = BtnState.Failure; toast.error(r.message) }
                    }
                    delay(900); upState = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = upState, enabled = picked != null && title.isNotBlank(), icon = Icons.Filled.CloudUpload)
        }
        Section("Thư viện (${list.size})") {
            if (list.isEmpty()) Dim("Trống")
            list.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x12FFFFFF)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Dim(MEME_EVENTS.firstOrNull { it.first == m.event }?.second ?: m.event)
                    }
                    AnimIconButton(Icons.Filled.PlayArrow, "Nghe thử", { Graph.previewMeme(m) })
                    AnimIconButton(Icons.Filled.Delete, "Xóa", { toDelete = m })
                }
            }
        }
    }
    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Xóa âm thanh?") }, text = { Text("\"${m.title}\"") },
            confirmButton = { TextButton({
                toDelete = null
                scope.launch {
                    when (val r = Graph.remote.deleteMeme(m)) {
                        is AuthResult.Ok -> { toast.success(r.message); Graph.refreshMemes() }
                        is AuthResult.Fail -> toast.error(r.message)
                    }
                }
            }) { Text("Xóa") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Hủy") } },
        )
    }
}

// ───────────────────────── Thanh toán ─────────────────────────

@Composable
private fun PayTab() {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val clipboard = LocalClipboardManager.current
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var bank by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("10000") }
    var max by remember { mutableStateOf("5000000") }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var orders by remember { mutableStateOf<List<AdminOrder>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        Graph.remote.payConfig()?.let { c ->
            enabled = c.enabled; bank = c.bank; account = c.account; name = c.name; min = c.min.toString(); max = c.max.toString()
        }
        loaded = true
    }
    LaunchedEffect(tick) { orders = Graph.remote.adminOrders() ?: orders }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick++ } }
    val hook = "${Graph.auth.base}/functions/v1/payment-webhook"

    Page {
        Section("Cổng thanh toán") {
            SwitchRow("Bật thanh toán", "", enabled) { enabled = it }
            TlvField(bank, { bank = it.trim().take(12) }, "Mã ngân hàng", leading = Icons.Filled.AccountBalance)
            TlvField(account, { account = it.filter(Char::isDigit).take(20) }, "Số tài khoản", leading = Icons.Filled.Pin)
            TlvField(name, { name = it.uppercase().take(40) }, "Tên chủ tài khoản", leading = Icons.Filled.Person)
            TlvField(min, { min = it.filter(Char::isDigit).take(9) }, "Tối thiểu (₫)", leading = Icons.Filled.Payments)
            TlvField(max, { max = it.filter(Char::isDigit).take(9) }, "Tối đa (₫)", leading = Icons.Filled.Payments)
            NeonButton("Lưu", {
                state = BtnState.Loading
                scope.launch {
                    val c = PayConfig(enabled, bank, account, name, min.toLongOrNull() ?: 10_000, max.toLongOrNull() ?: 5_000_000)
                    when (val r = Graph.remote.savePayConfig(c)) {
                        is AuthResult.Ok -> { state = BtnState.Success; toast.success(r.message) }
                        is AuthResult.Fail -> { state = BtnState.Failure; toast.error(r.message) }
                    }
                    delay(900); state = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = state, enabled = loaded && (!enabled || (bank.isNotBlank() && account.isNotBlank() && name.isNotBlank())), icon = Icons.Filled.Save)
        }
        Section("Webhook") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hook, Modifier.weight(1f), fontSize = 12.sp)
                AnimIconButton(Icons.Filled.ContentCopy, "Sao chép", { clipboard.setText(AnnotatedString(hook)); toast.success("Đã sao chép") })
            }
        }
        Section("Giao dịch gần đây") {
            val list = orders
            if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (list.isEmpty()) Dim("Trống")
            else list.forEach { o ->
                val color = when (o.status) { "paid" -> Ok; "underpaid" -> Bad; else -> Warn }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(money(o.amount), fontWeight = FontWeight.SemiBold)
                        Dim("${o.username} · ${o.code}")
                    }
                    Text(when (o.status) { "paid" -> "Đã nạp"; "underpaid" -> "Thiếu tiền"; else -> "Chờ thanh toán" },
                        color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────── Thông báo ─────────────────────────

@Composable
private fun AnnounceTab() {
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var list by remember { mutableStateOf<List<Announcement>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { list = Graph.remote.announcements() ?: list }

    Page {
        Section("Soạn thông báo") {
            TlvField(title, { title = it.take(80) }, "Tiêu đề", leading = Icons.Filled.Title)
            OutlinedTextField(body, { body = it.take(600) }, label = { Text("Nội dung") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            NeonButton("Gửi cho mọi người", {
                state = BtnState.Loading
                scope.launch {
                    when (val r = Graph.remote.sendAnnouncement(title, body)) {
                        is AuthResult.Ok -> { state = BtnState.Success; toast.success(r.message); title = ""; body = ""; tick++ }
                        is AuthResult.Fail -> { state = BtnState.Failure; toast.error(r.message) }
                    }
                    delay(900); state = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = state, enabled = title.isNotBlank() && body.isNotBlank(), icon = Icons.Filled.Campaign)
        }
        Section("Đã gửi") {
            val l = list
            if (l == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (l.isEmpty()) Dim("Trống")
            else l.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x12FFFFFF)).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(a.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Dim(java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(a.createdMs)))
                    }
                    AnimIconButton(Icons.Filled.Delete, "Xóa", {
                        scope.launch {
                            val r = Graph.remote.deleteAnnouncement(a.id)
                            if (r is AuthResult.Ok) { toast.success(r.message); tick++ } else toast.error((r as AuthResult.Fail).message)
                        }
                    })
                }
            }
        }
    }
}

// ───────────────────────── Hệ thống ─────────────────────────

@Composable
private fun SystemTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val settings by Graph.settings.collectAsState()
    var bridge by remember { mutableStateOf(settings.bridgeUrl) }
    var bridgeState by remember { mutableStateOf(BtnState.Idle) }

    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<File?>(null) }
    var apkName by remember { mutableStateOf("") }
    var pubState by remember { mutableStateOf(BtnState.Idle) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val out = File(ctx.cacheDir, "release.apk")
                    ctx.contentResolver.openInputStream(u)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    out
                }.getOrNull()
            }
            val info = f?.let { ctx.packageManager.getPackageArchiveInfo(it.absolutePath, 0) }
            if (f == null || info == null) { toast.error("Không đọc được file APK"); return@launch }
            if (info.packageName != ctx.packageName) { toast.error("Đây không phải APK của ứng dụng này"); return@launch }
            apk = f; apkName = nameOf(ctx, u)
            code = info.longVersionCode.toInt().toString(); name = info.versionName.orEmpty()
        }
    }

    Page {
        Section("Máy chủ kết nối") {
            TlvField(bridge, { bridge = it.trim() }, "Địa chỉ wss://", leading = Icons.Filled.Hub,
                error = if (bridge.isNotEmpty() && !bridge.startsWith("wss://")) "Phải bắt đầu bằng wss://" else null)
            NeonButton("Lưu", {
                bridgeState = BtnState.Loading
                scope.launch {
                    when (val r = Graph.remote.setBridge(bridge)) {
                        is AuthResult.Ok -> { bridgeState = BtnState.Success; toast.success(r.message) }
                        is AuthResult.Fail -> { bridgeState = BtnState.Failure; toast.error(r.message) }
                    }
                    delay(900); bridgeState = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = bridgeState, enabled = bridge.startsWith("wss://"), icon = Icons.Filled.Save)
        }
        Section("Phát hành bản cập nhật") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({}, label = { Text("Đang chạy v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp) })
            }
            NeonButton(if (apk != null) "✓ $apkName" else "Chọn file APK", { picker.launch("*/*") },
                Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.SystemUpdate)
            TlvField(code, { code = it.filter(Char::isDigit).take(9) }, "Mã phiên bản", leading = Icons.Filled.Pin)
            TlvField(name, { name = it.take(20) }, "Tên phiên bản", leading = Icons.Filled.Tag)
            TlvField(notes, { notes = it.take(500) }, "Nội dung cập nhật", leading = Icons.Filled.Description)
            SwitchRow("Bắt buộc cập nhật", "", force) { force = it }
            NeonButton("Phát hành", {
                pubState = BtnState.Loading
                scope.launch {
                    when (val r = Graph.remote.publishUpdate(code.toInt(), name, notes, force, apk, "")) {
                        is AuthResult.Ok -> { pubState = BtnState.Success; toast.success(r.message); Graph.checkUpdate(true) }
                        is AuthResult.Fail -> { pubState = BtnState.Failure; toast.error(r.message) }
                    }
                    delay(900); pubState = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = pubState, enabled = apk != null && code.toIntOrNull() != null, icon = Icons.Filled.CloudUpload)
        }
    }
}
