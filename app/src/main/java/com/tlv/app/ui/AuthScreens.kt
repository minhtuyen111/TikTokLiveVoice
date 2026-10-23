package com.tlv.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlv.app.Graph
import com.tlv.app.auth.AuthResult
import com.tlv.app.auth.Validators
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp = 72.dp) {
    val reduce = LocalMotion.current.reduce
    val hs = listOf(0.35f, 0.65f, 1f, 0.55f, 0.8f)
    val t = if (reduce) null else rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "t",
    )
    val brush = remember { Brush.verticalGradient(listOf(Sky, Neon)) }
    Row(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(listOf(Neon.copy(alpha = 0.28f), Sky.copy(alpha = 0.28f))))
            .padding(size / 5),
        horizontalArrangement = Arrangement.spacedBy(size / 18, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hs.forEachIndexed { i, h ->
            Box(Modifier.width(size / 11).fillMaxHeight(h).graphicsLayer {
                val v = t?.value
                scaleY = if (v == null) 1f else 0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin((v * 2f + i * 0.22f) * Math.PI.toFloat()))
            }.clip(CircleShape).background(brush))
        }
    }
}

/** Nền đăng nhập: hai quầng sáng màu trôi chậm (vẽ bằng gradient, không dùng blur nên nhẹ máy). */
@Composable
private fun AuthBackdrop(content: @Composable ColumnScope.() -> Unit) {
    val reduce = LocalMotion.current.reduce
    val primary = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val drift = if (reduce) null else rememberInfiniteTransition(label = "drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "d",
    )
    Box(
        Modifier.fillMaxSize().background(bg).drawBehind {
            val d = drift?.value ?: 0.5f
            drawCircle(
                Brush.radialGradient(listOf(primary.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * (0.15f + 0.5f * d), size.height * 0.12f), radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = Offset(size.width * (0.15f + 0.5f * d), size.height * 0.12f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Sky.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * (0.95f - 0.5f * d), size.height * 0.85f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width * (0.95f - 0.5f * d), size.height * 0.85f),
            )
        },
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = {
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
            },
        )
    }
}

@Composable
fun SplashScreen(onDone: (loggedIn: Boolean) -> Unit) {
    var status by remember { mutableStateOf("Đang khởi tạo ứng dụng…") }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        Graph.settingsLoaded.first { it }
        status = "Kiểm tra phiên đăng nhập…"
        val ok = runCatching { Graph.auth.restore() }.getOrDefault(false)
        delay(250)
        onDone(ok)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(a.value).scale(0.85f + 0.15f * a.value)) {
            LogoMark(96.dp)
            Spacer(Modifier.height(20.dp))
            Text("Voice Live TikTok", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Dim(status)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(Modifier.width(120.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val configured = Graph.auth.configured

    fun submit() {
        if (state == BtnState.Loading) return
        if (id.isBlank() || pw.isEmpty()) { error = "Vui lòng nhập đầy đủ thông tin"; return }
        error = null; state = BtnState.Loading
        scope.launch {
            when (val r = Graph.auth.signIn(id, pw, keep)) {
                is AuthResult.Ok -> { state = BtnState.Success; delay(450); onLoggedIn() }
                is AuthResult.Fail -> { error = r.message; state = BtnState.Failure; delay(1100); state = BtnState.Idle }
            }
        }
    }

    AuthBackdrop {
        Spacer(Modifier.height(24.dp))
        LogoMark()
        Spacer(Modifier.height(16.dp))
        Text("Chào mừng trở lại", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Dim("Đăng nhập để biến bình luận LIVE thành giọng nói", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(28.dp))
        if (!configured) {
            GlassCard(Modifier.fillMaxWidth(), tint = Warn) {
                Text("Chưa cấu hình backend", fontWeight = FontWeight.SemiBold, color = Warn)
                Dim("Đặt SUPABASE_URL và SUPABASE_ANON_KEY khi build (xem README).")
            }
            Spacer(Modifier.height(16.dp))
        }
        GlassCard(Modifier.fillMaxWidth(), corner = 26.dp) {
            TlvField(id, { id = it }, "Tên đăng nhập hoặc email", leading = Icons.Filled.Person)
            Spacer(Modifier.height(8.dp))
            TlvField(pw, { pw = it }, "Mật khẩu", leading = Icons.Filled.Lock, password = true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(keep, { keep = it })
                Text("Ghi nhớ phiên đăng nhập", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onForgot) { Text("Quên mật khẩu?") }
            }
            AnimatedVisibility(error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center)
            }
            NeonButton("Đăng nhập", ::submit, Modifier.fillMaxWidth(), state = state, enabled = configured)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dim("Chưa có tài khoản?")
            TextButton(onRegister) { Text("Đăng ký", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            FloatingLogo(com.tlv.app.R.drawable.logo_tiktok, 38.dp, 0)
            FloatingLogo(com.tlv.app.R.drawable.logo_discord, 38.dp, 2)
        }
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val eUser = Validators.username(user)
    val eMail = Validators.email(email)
    val ePw = Validators.password(pw)
    val ePw2 = if (pw != pw2) "Hai mật khẩu chưa khớp" else null
    val valid = eUser == null && eMail == null && ePw == null && ePw2 == null && agree

    fun submit() {
        touched = true
        if (!valid || state == BtnState.Loading) return
        error = null; state = BtnState.Loading
        scope.launch {
            when (val r = Graph.auth.signUp(user, email, pw)) {
                is AuthResult.Ok -> { state = BtnState.Success; delay(400); done = r.message }
                is AuthResult.Fail -> { error = r.message; state = BtnState.Failure; delay(1100); state = BtnState.Idle }
            }
        }
    }

    AuthBackdrop {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Quay lại") }
            Text("Tạo tài khoản", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(done, label = "reg") { d ->
            if (d != null) {
                GlassCard(Modifier.fillMaxWidth(), tint = Ok) {
                    Icon(Icons.Filled.MarkEmailRead, null, tint = Ok, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Kiểm tra email của bạn", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Dim(d, Modifier.padding(top = 4.dp, bottom = 16.dp))
                    NeonButton("Về đăng nhập", onBack, Modifier.fillMaxWidth())
                }
            } else {
                Column {
                    TlvField(user, { user = it.trim() }, "Tên đăng nhập", leading = Icons.Filled.Person,
                        error = if (touched || user.isNotEmpty()) eUser else null)
                    Spacer(Modifier.height(6.dp))
                    TlvField(email, { email = it }, "Email", leading = Icons.Filled.Email,
                        keyboard = androidx.compose.ui.text.input.KeyboardType.Email,
                        error = if (touched || email.isNotEmpty()) eMail else null)
                    Spacer(Modifier.height(6.dp))
                    TlvField(pw, { pw = it }, "Mật khẩu", leading = Icons.Filled.Lock, password = true,
                        error = if (touched) ePw else null)
                    if (pw.isNotEmpty()) StrengthBar(Validators.strength(pw), Modifier.padding(top = 2.dp, bottom = 6.dp))
                    TlvField(pw2, { pw2 = it }, "Xác nhận mật khẩu", leading = Icons.Filled.LockReset, password = true,
                        error = if (touched || pw2.isNotEmpty()) ePw2 else null)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(agree, { agree = it })
                        Column {
                            Text("Tôi đồng ý với", style = MaterialTheme.typography.bodyMedium)
                            Row {
                                TextButton(onTerms, contentPadding = PaddingValues(0.dp)) { Text("Điều khoản sử dụng") }
                                Text(" và ", Modifier.align(Alignment.CenterVertically))
                                TextButton(onPrivacy, contentPadding = PaddingValues(0.dp)) { Text("Chính sách quyền riêng tư") }
                            }
                        }
                    }
                    AnimatedVisibility(error != null) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    NeonButton("Đăng ký", ::submit, Modifier.fillMaxWidth(), state = state, enabled = valid && Graph.auth.configured)
                }
            }
        }
    }
}

@Composable
fun ForgotScreen(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val eMail = Validators.email(email)
    AuthBackdrop {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Quay lại") }
            Text("Quên mật khẩu", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Dim("Nhập email đăng ký. Liên kết đặt lại có thời hạn và chỉ dùng được một lần.", Modifier.padding(vertical = 12.dp))
        TlvField(email, { email = it }, "Email", leading = Icons.Filled.Email,
            keyboard = androidx.compose.ui.text.input.KeyboardType.Email,
            error = if (email.isNotEmpty()) eMail else null)
        AnimatedVisibility(msg != null) {
            GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp), tint = Ok) { Text(msg.orEmpty()) }
        }
        AnimatedVisibility(err != null) {
            Text(err.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        NeonButton("Gửi liên kết khôi phục", {
            err = null; msg = null; state = BtnState.Loading
            scope.launch {
                when (val r = Graph.auth.requestReset(email)) {
                    is AuthResult.Ok -> { msg = r.message; state = BtnState.Success; delay(900); state = BtnState.Idle }
                    is AuthResult.Fail -> { err = r.message; state = BtnState.Failure; delay(1100); state = BtnState.Idle }
                }
            }
        }, Modifier.fillMaxWidth(), state = state, enabled = eMail == null && Graph.auth.configured)
    }
}

@Composable
fun ResetScreen(token: String, onDone: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(BtnState.Idle) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ePw = Validators.password(pw)
    val ePw2 = if (pw != pw2) "Hai mật khẩu chưa khớp" else null
    AuthBackdrop {
        Spacer(Modifier.height(24.dp))
        Text("Đặt mật khẩu mới", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        TlvField(pw, { pw = it }, "Mật khẩu mới", leading = Icons.Filled.Lock, password = true,
            error = if (pw.isNotEmpty()) ePw else null)
        if (pw.isNotEmpty()) StrengthBar(Validators.strength(pw), Modifier.padding(vertical = 4.dp))
        TlvField(pw2, { pw2 = it }, "Xác nhận mật khẩu", leading = Icons.Filled.LockReset, password = true,
            error = if (pw2.isNotEmpty()) ePw2 else null)
        AnimatedVisibility(err != null) {
            Text(err.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        NeonButton("Đổi mật khẩu", {
            err = null; state = BtnState.Loading
            scope.launch {
                when (val r = Graph.auth.setPassword(token, pw)) {
                    is AuthResult.Ok -> { state = BtnState.Success; delay(600); Graph.recoveryToken.value = null; onDone() }
                    is AuthResult.Fail -> { err = r.message; state = BtnState.Failure; delay(1100); state = BtnState.Idle }
                }
            }
        }, Modifier.fillMaxWidth(), state = state, enabled = ePw == null && ePw2 == null)
        TextButton({ Graph.recoveryToken.value = null; onDone() }) { Text("Hủy") }
    }
}
