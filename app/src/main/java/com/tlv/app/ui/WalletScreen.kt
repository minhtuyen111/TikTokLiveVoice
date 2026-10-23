@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tlv.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlv.app.Graph
import com.tlv.app.data.LedgerRow
import com.tlv.app.data.OrderRow
import com.tlv.app.data.PayConfig
import com.tlv.app.security.Guard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun money(n: Long): String = NumberFormat.getInstance(Locale("vi", "VN")).format(n) + " ₫"
private fun time(ms: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))
private const val ORDER_TTL = 30 * 60_000L

private class ActiveOrder(val id: String, val code: String, val amount: Long, val createdMs: Long)

@Composable
private fun statusLabel(o: OrderRow): Pair<String, Color> = when {
    o.status == "paid" -> "Đã nạp" to Ok
    o.status == "underpaid" -> "Thiếu tiền, liên hệ quản trị" to Bad
    o.status == "pending" && System.currentTimeMillis() - o.createdMs > ORDER_TTL -> "Hết hạn" to TextDim
    else -> "Chờ thanh toán" to Warn
}

/**
 * Ví & nạp tiền. App chỉ hiển thị mã QR và số dư; tiền chỉ được cộng khi máy chủ nhận thông báo
 * biến động số dư từ ngân hàng (webhook). Sửa app hay dùng Lucky Patcher không thể tự cộng tiền.
 */
@Composable
fun WalletScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val clipboard = LocalClipboardManager.current
    val cheats = remember { Guard.cheatTools(ctx) }

    var cfg by remember { mutableStateOf<PayConfig?>(null) }
    var balance by remember { mutableStateOf<Long?>(null) }
    var orders by remember { mutableStateOf<List<OrderRow>>(emptyList()) }
    var ledger by remember { mutableStateOf<List<LedgerRow>>(emptyList()) }
    var amountText by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var active by remember { mutableStateOf<ActiveOrder?>(null) }
    var qr by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var topped by remember { mutableStateOf<Long?>(null) }

    suspend fun reload() {
        cfg = Graph.remote.payConfig() ?: cfg
        balance = Graph.remote.balance() ?: balance
        orders = Graph.remote.myOrders()
        ledger = Graph.remote.myLedger()
    }
    LaunchedEffect(Unit) { reload() }

    // Khi đang hiện QR: hỏi trạng thái đơn mỗi 3 giây tới khi tiền về (hoặc quá 35 phút).
    LaunchedEffect(active?.id) {
        val a = active ?: return@LaunchedEffect
        while (true) {
            delay(3000)
            now = System.currentTimeMillis()
            val st = Graph.remote.orderStatus(a.id)
            if (st == "paid") { topped = a.amount; active = null; reload(); return@LaunchedEffect }
            if (st == "underpaid") { toast.error("Số tiền chuyển thiếu, vui lòng liên hệ quản trị"); active = null; reload(); return@LaunchedEffect }
            if (now - a.createdMs > ORDER_TTL + 5 * 60_000) { active = null; reload(); return@LaunchedEffect }
        }
    }
    LaunchedEffect(active?.id, cfg) {
        val a = active; val c = cfg
        qr = null
        if (a != null && c != null) {
            val bytes = Graph.remote.fetchBytes(Graph.remote.qrUrl(c, a.amount, a.code))
            qr = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    }

    val amount = amountText.filter(Char::isDigit).toLongOrNull()
    val c = cfg
    val inRange = amount != null && c != null && amount >= c.min && amount <= c.max

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth(), tint = Ok) {
            Dim("Số dư")
            Text(balance?.let { money(it) } ?: "…", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }

        if (cheats.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), tint = Bad) {
                Text("Phát hiện ứng dụng can thiệp (Lucky Patcher…). Hãy gỡ chúng để dùng chức năng thanh toán.", color = Bad)
            }
        }

        if (c == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (!c.ready) GlassCard(Modifier.fillMaxWidth(), tint = Warn) { Text("Thanh toán đang tắt") }
        else if (active == null) {
            SectionTitle("Nạp tiền")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20_000L, 50_000L, 100_000L, 200_000L, 500_000L).filter { it in c.min..c.max }.forEach { v ->
                    FilterChip(amount == v, { amountText = v.toString() }, label = { Text(money(v)) })
                }
            }
            TlvField(amountText, { amountText = it.filter(Char::isDigit).take(10) }, "Số tiền (₫)", leading = Icons.Filled.Payments,
                error = if (amount != null && !inRange) "Từ ${money(c.min)} đến ${money(c.max)}" else null)
            NeonButton("Thanh toán", {
                state = BtnState.Loading
                scope.launch {
                    val (ok, err) = Graph.remote.createOrder(amount ?: 0)
                    if (ok != null) {
                        active = ActiveOrder(ok.first, ok.second, amount ?: 0, System.currentTimeMillis()); state = BtnState.Success
                    } else { toast.error(err ?: "Không thể tạo đơn"); state = BtnState.Failure }
                    delay(700); state = BtnState.Idle
                }
            }, Modifier.fillMaxWidth(), state = state, enabled = inRange && cheats.isEmpty(), icon = Icons.Filled.QrCode2)
        } else {
            val a = active!!
            val left = (a.createdMs + ORDER_TTL - now).coerceAtLeast(0)
            GlassCard(Modifier.fillMaxWidth(), tint = Sky) {
                Text("Quét mã QR bằng app ngân hàng", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val bmp = qr
                    if (bmp != null) Image(bmp, null, Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(6.dp))
                    else Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                Spacer(Modifier.height(10.dp))
                Text(money(a.amount), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Dim("Ngân hàng: ${c.bank} · ${c.name}")
                InfoRow("Số tài khoản", c.account) { clipboard.setText(AnnotatedString(c.account)); toast.success("Đã sao chép") }
                InfoRow("Nội dung chuyển khoản", a.code) { clipboard.setText(AnnotatedString(a.code)); toast.success("Đã sao chép") }
                Spacer(Modifier.height(6.dp))
                Dim(if (left > 0) "Còn ${left / 60000}:${"%02d".format((left / 1000) % 60)}" else "Hết hạn")
                Dim("Giữ nguyên nội dung chuyển khoản. Tiền được cộng tự động sau khi ngân hàng báo có, thường dưới 1 phút.")
                Spacer(Modifier.height(8.dp))
                NeonButton("Đóng", { active = null; scope.launch { reload() } }, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.Close)
            }
        }

        if (orders.isNotEmpty()) {
            SectionTitle("Lịch sử nạp tiền")
            orders.forEach { o ->
                val (label, color) = statusLabel(o)
                GlassCard(Modifier.fillMaxWidth(), corner = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(money(o.amount), fontWeight = FontWeight.SemiBold)
                            Dim("${o.code} · ${time(o.createdMs)}")
                        }
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (ledger.isNotEmpty()) {
            SectionTitle("Biến động số dư")
            ledger.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Dim("${time(l.createdMs)} · ${l.reason}") }
                    Text((if (l.delta > 0) "+" else "") + money(l.delta), color = if (l.delta > 0) Ok else Bad, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    topped?.let { v -> SuccessDialog(title = "Nạp tiền thành công", subtitle = "Số dư đã được cộng", detail = "+${money(v)}", onClose = { topped = null }) }
}

@Composable
private fun InfoRow(label: String, value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Dim(label)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        IconButton(onCopy) { Icon(Icons.Filled.ContentCopy, "Sao chép") }
    }
}
