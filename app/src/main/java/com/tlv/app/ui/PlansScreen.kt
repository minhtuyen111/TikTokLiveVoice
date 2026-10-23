@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tlv.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlv.app.Graph
import com.tlv.app.data.Entitlement
import com.tlv.app.data.Plan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun day(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

fun tierName(t: String) = when (t) { "promax" -> "Pro Max"; "pro" -> "Pro"; else -> "Free" }

/** Dòng giới hạn bình luận được sinh tự động từ cấu hình thật của gói (0 = không giới hạn). */
fun limitText(max: Int) = if (max <= 0) "Không giới hạn bình luận mỗi ngày" else "Tối đa ${"%,d".format(max)} bình luận mỗi ngày"

@Composable
private fun CurrentPlan(e: Entitlement?) {
    GlassCard(Modifier.fillMaxWidth(), tint = when (e?.tier) { "promax" -> Warn; "pro" -> Sky; else -> TextDim }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gói hiện tại", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TierBadge(e?.tier ?: "free")
        }
        if (e != null) {
            Spacer(Modifier.height(10.dp))
            val exp = e.expiresMs
            if (e.tier == "free") {
                if (e.blocked) Text("Đã hết thời gian dùng thử", color = Bad, fontWeight = FontWeight.SemiBold)
                else if (exp != null) Text("Dùng thử đến ${day(exp)}", fontWeight = FontWeight.SemiBold)
            } else if (exp != null) Text("Hết hạn ${day(exp)}", fontWeight = FontWeight.SemiBold)
            else Text("Không giới hạn thời gian", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            val lim = e.dailyLimit
            Dim(if (lim <= 0) "Hôm nay đã đọc ${e.usedToday} bình luận" else "Hôm nay đã đọc ${e.usedToday} / $lim bình luận")
            if (lim > 0) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))) {
                    Box(Modifier.fillMaxWidth((e.usedToday.toFloat() / lim).coerceIn(0.02f, 1f)).fillMaxHeight()
                        .background(if (e.usedToday >= lim) Bad else Ok))
                }
            }
        } else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

@Composable
private fun PlanCard(p: Plan, balance: Long?, ent: Entitlement?, onBuy: () -> Unit, onTopUp: () -> Unit) {
    val enough = balance != null && balance >= p.price
    val hasPlan = ent != null && ent.tier != "free" && ent.planId.isNotEmpty() && (ent.expiresMs ?: 0L) > System.currentTimeMillis()
    val isCurrent = hasPlan && ent!!.planId == p.id
    val blocked = hasPlan && !isCurrent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        PlanCover(p.cover, Modifier.fillMaxWidth().height(132.dp)) {
            Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TierBadge(p.tier)
                if (isCurrent) Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    androidx.compose.material3.Text("Đang dùng", color = Color(0xFF14532D), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(p.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)) {
                PulseIcon(if (p.tier == "promax") Icons.Filled.WorkspacePremium else Icons.Filled.Bolt, Color.White.copy(alpha = 0.9f), 52.dp)
            }
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.description.isNotBlank()) Text(p.description, lineHeight = 21.sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("Thời hạn ${p.days} ngày", limitText(p.maxComments)) + p.perks.lines().map { it.trim() }.filter { it.isNotEmpty() }).forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp).padding(top = 2.dp), tint = Ok)
                        Spacer(Modifier.width(8.dp))
                        Text(line, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money(p.price), Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("${p.days} ngày", fontSize = 13.sp, color = TextDim)
            }
            when {
                blocked -> {
                    NeonButton("Đang dùng gói khác", {}, Modifier.fillMaxWidth(), outlined = true, enabled = false, icon = Icons.Filled.Lock)
                    ent?.expiresMs?.let { Dim("Mua lại sau khi gói hiện tại hết hạn (${day(it)})") }
                }
                !enough -> NeonButton("Nạp thêm", onTopUp, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.AccountBalanceWallet)
                isCurrent -> NeonButton("Gia hạn", onBuy, Modifier.fillMaxWidth(), icon = Icons.Filled.Autorenew)
                else -> NeonButton("Mua gói", onBuy, Modifier.fillMaxWidth(), icon = Icons.Filled.ShoppingCart)
            }
        }
    }
}

/** Hộp thoại xác nhận mua: ảnh bìa động của gói, chi tiết giá và số dư trước/sau khi mua. */
@Composable
private fun PurchaseDialog(p: Plan, balance: Long, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    FancyDialog(onDismiss = onCancel, dismissible = !busy, glow = if (p.tier == "promax") Warn else Sky, header = {
        PlanCover(p.cover, Modifier.matchParentSize()) {
            Row(Modifier.align(Alignment.TopStart).padding(14.dp)) { TierBadge(p.tier) }
            androidx.compose.material3.Text(p.title, Modifier.align(Alignment.BottomStart).padding(16.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
    }) {
        @Composable
        fun line(label: String, value: String, color: Color = PopupText, bold: Boolean = false) = Row(Modifier.fillMaxWidth()) {
            androidx.compose.material3.Text(label, Modifier.weight(1f), color = PopupDim, fontSize = 14.sp)
            androidx.compose.material3.Text(value, color = color, fontSize = if (bold) 17.sp else 14.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
        }
        line("Thời hạn", "${p.days} ngày")
        line("Giá", money(p.price), Color(0xFFFFD166), bold = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        line("Số dư hiện tại", money(balance))
        line("Số dư sau khi mua", money(balance - p.price), Ok, bold = true)
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton("Hủy", onCancel, Modifier.weight(1f), outlined = true, enabled = !busy)
            NeonButton("Xác nhận mua", onConfirm, Modifier.weight(1.4f), state = if (busy) BtnState.Loading else BtnState.Idle, icon = Icons.Filled.ShoppingCart)
        }
    }
}

/** Gói dịch vụ: xem gói đang dùng, bản Free và các gói có thể mua bằng số dư ví. */
@Composable
fun PlansScreen(onTopUp: () -> Unit) {
    val ent by Graph.entitlement.collectAsState()
    val toast = LocalToaster.current
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    var balance by remember { mutableStateOf<Long?>(null) }
    var free by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirm by remember { mutableStateOf<Plan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Triple<Plan, Long, Unit?>?>(null) }

    suspend fun load() {
        Graph.refreshEntitlement()
        plans = Graph.remote.plans()?.filter { it.active } ?: plans
        balance = Graph.remote.balance() ?: balance
        free = Graph.remote.freeConfig() ?: free
    }
    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CurrentPlan(ent)
        GlassCard(Modifier.fillMaxWidth(), tint = Ok) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Dim("Số dư")
                    Text(balance?.let { money(it) } ?: "…", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                NeonButton("Nạp tiền", onTopUp, icon = Icons.Filled.AddCard)
            }
        }

        free?.let { (days, cap) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bản miễn phí", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TierBadge("free")
                }
                Row { Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = TextDim); Spacer(Modifier.width(8.dp)); Text("Dùng thử $days ngày kể từ khi đăng ký", fontSize = 14.sp) }
                Row { Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = TextDim); Spacer(Modifier.width(8.dp)); Text(limitText(cap), fontSize = 14.sp) }
            }
        }

        SectionTitle("Gói dịch vụ")
        val list = plans
        if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (list.isEmpty()) Dim("Chưa có gói nào.")
        else list.forEach { p -> PlanCard(p, balance, ent, { confirm = p }, onTopUp) }
        Spacer(Modifier.height(24.dp))
    }

    confirm?.let { p ->
        PurchaseDialog(p, balance ?: 0L, busy, onConfirm = {
            busy = true
            scope.launch {
                val (exp, err) = Graph.remote.purchase(p.id)
                busy = false; confirm = null
                if (exp != null) { done = Triple(p, exp, null); load() } else toast.error(err ?: "Không thể mua gói")
            }
        }, onCancel = { confirm = null })
    }
    done?.let { (p, exp, _) ->
        SuccessDialog(
            title = "Chúc mừng!", subtitle = "Bạn đã kích hoạt ${p.title}",
            detail = if (exp > 0) "Hết hạn ${day(exp)}" else null, tier = p.tier, onClose = { done = null },
        )
    }
}
