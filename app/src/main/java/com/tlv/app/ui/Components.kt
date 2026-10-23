package com.tlv.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Thẻ kính mờ có kiểm soát: nền bán trong suốt + viền mảnh, không blur. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // Bàn chải được nhớ lại, không đổ bóng: thẻ vẽ rẻ hơn nên cuộn mượt, máy không nóng.
    val brush = remember(surface, tint) {
        Brush.linearGradient(listOf(surface.copy(alpha = 0.95f), tint.copy(alpha = 0.07f).compositeOver(surface)))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.background(brush).padding(16.dp),
            content = content,
        )
    }
}

enum class BtnState { Idle, Loading, Success, Failure }

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: BtnState = BtnState.Idle,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    outlined: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduce = LocalMotion.current.reduce
    val scaleState = animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, tween(90), label = "press")
    val press = Modifier.graphicsLayer { scaleX = scaleState.value; scaleY = scaleState.value }
    val pop = animateFloatAsState(if (pressed && !reduce) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "pop")
    val base = MaterialTheme.colorScheme.primary
    val target = when (state) {
        BtnState.Success -> Ok
        BtnState.Failure -> Bad
        else -> base
    }
    val container by animateColorAsState(target, tween(250), label = "btn")
    val canClick = enabled && state != BtnState.Loading
    if (outlined) {
        OutlinedButton(
            onClick = onClick, enabled = canClick, interactionSource = src,
            modifier = modifier.heightIn(min = 52.dp).then(press),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, container.copy(alpha = if (canClick) 0.8f else 0.3f)),
        ) { BtnContent(text, state, icon, container, pop) }
    } else {
        Button(
            onClick = { if (canClick) onClick() }, enabled = canClick || state == BtnState.Loading, interactionSource = src,
            modifier = modifier.heightIn(min = 52.dp).then(press),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = container, contentColor = Color.White,
                disabledContainerColor = container.copy(alpha = if (state == BtnState.Loading) 0.7f else 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            ),
        ) { BtnContent(text, state, icon, Color.White, pop) }
    }
}

@Composable
private fun BtnContent(text: String, state: BtnState, icon: ImageVector?, tint: Color, pop: State<Float>) {
    when (state) {
        BtnState.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
        else -> {
            if (icon != null) {
                // Biểu tượng nảy và nghiêng khi nhấn
                Icon(icon, null, Modifier.size(18.dp).graphicsLayer { val v = pop.value; rotationZ = 16f * v; scaleX = 1f + 0.3f * v; scaleY = 1f + 0.3f * v }, tint = tint)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when (state) { BtnState.Success -> "Thành công"; BtnState.Failure -> "Thất bại"; else -> text },
                fontWeight = FontWeight.SemiBold, color = tint,
            )
        }
    }
}

@Composable
fun TlvField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    password: Boolean = false,
    error: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), enabled = enabled,
        singleLine = singleLine, isError = error != null,
        leadingIcon = if (leading != null) { { Icon(leading, null) } } else null,
        trailingIcon = if (password) {
            { IconButton({ visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu")
            } }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        supportingText = if (error != null) { { Text(error) } } else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
    )
}

@Composable
fun StrengthBar(level: Int, modifier: Modifier = Modifier) {
    val colors = listOf(Bad, Bad, Warn, Ok, Ok)
    val labels = listOf("Rất yếu", "Yếu", "Trung bình", "Mạnh", "Rất mạnh")
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                val c by animateColorAsState(
                    if (i < level) colors[level] else MaterialTheme.colorScheme.outlineVariant, label = "s$i")
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c))
            }
        }
        Text(labels[level], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** Chấm LIVE: nhấp nháy bằng graphicsLayer (không tái dựng giao diện mỗi khung hình). */
@Composable
fun LiveDot(active: Boolean, color: Color = Bad, size: Dp = 10.dp) {
    val reduce = LocalMotion.current.reduce
    val pulse = if (active && !reduce)
        rememberInfiniteTransition(label = "dot").animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    else null
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(size).graphicsLayer { alpha = if (active) (pulse?.value ?: 1f) else 0.4f }
        .clip(CircleShape).background(if (active) color else off))
}

/** Sóng âm khi TTS đọc: một bộ hoạt ảnh duy nhất, chỉ đổi scaleY trong lớp đồ họa. Đứng yên khi không đọc. */
@Composable
fun WaveBars(active: Boolean, modifier: Modifier = Modifier, bars: Int = 5, height: Dp = 28.dp) {
    val reduce = LocalMotion.current.reduce
    val phase = if (active && !reduce)
        rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "p")
    else null
    val brush = remember(MaterialTheme.colorScheme.primary) { Brush.verticalGradient(listOf(Sky, Neon)) }
    Row(modifier.height(height), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(bars) { i ->
            Box(Modifier.width(4.dp).fillMaxHeight().graphicsLayer {
                val p = phase?.value
                scaleY = when {
                    p != null -> 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin((p * 2f + i * 0.2f) * Math.PI.toFloat()))
                    active -> 0.7f
                    else -> 0.25f
                }
            }.clip(CircleShape).background(brush))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 8.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold)
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String,
              onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Thông báo thành công/lỗi nhất quán toàn app. */
class Toaster(private val scope: CoroutineScope, val host: SnackbarHostState) {
    var isError by mutableStateOf(false); private set
    fun success(msg: String) { isError = false; scope.launch { host.currentSnackbarData?.dismiss(); host.showSnackbar(msg) } }
    fun error(msg: String) { isError = true; scope.launch { host.currentSnackbarData?.dismiss(); host.showSnackbar(msg) } }
}

val LocalToaster = staticCompositionLocalOf<Toaster> { error("Toaster missing") }

@Composable
fun ToastHost(t: Toaster) {
    SnackbarHost(t.host) { data ->
        Snackbar(
            data,
            containerColor = if (t.isError) Bad.copy(alpha = 0.95f) else Ok.copy(alpha = 0.95f),
            contentColor = Color(0xFF0B0F1A),
            shape = RoundedCornerShape(14.dp),
        )
    }
}

@Composable
fun Dim(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Biểu tượng nảy lên khi được chọn (thanh điều hướng). Chỉ đổi scale trong lớp đồ họa. */
@Composable
fun BounceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String?, selected: Boolean) {
    val reduce = LocalMotion.current.reduce
    val sc = animateFloatAsState(
        if (selected && !reduce) 1.22f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "bounce",
    )
    androidx.compose.material3.Icon(icon, desc, Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value })
}

/** Logo (TikTok/Discord…) lơ lửng nhẹ và xoay rất chậm: sống động nhưng gần như không tốn máy. */
@Composable
fun FloatingLogo(res: Int, size: Dp = 28.dp, phase: Int = 0) {
    val reduce = LocalMotion.current.reduce
    val t = if (reduce) null else rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400 + phase * 300, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(res), null,
        Modifier.size(size).graphicsLayer {
            val v = t?.value ?: 0f
            translationY = -3f * kotlin.math.sin(v * 2f * Math.PI.toFloat())
            rotationZ = 4f * kotlin.math.sin((v + 0.25f) * 2f * Math.PI.toFloat())
        }.clip(CircleShape),
    )
}

/** Biểu tượng nhịp đập (chấm thông báo, trạng thái LIVE…). */
@Composable
fun PulseIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: Dp = 22.dp) {
    val reduce = LocalMotion.current.reduce
    val t = if (reduce) null else rememberInfiniteTransition(label = "pulse").animateFloat(
        0.9f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p",
    )
    androidx.compose.material3.Icon(icon, null, Modifier.size(size).graphicsLayer {
        val v = t?.value ?: 1f; scaleX = v; scaleY = v
    }, tint = tint)
}

/** IconButton có chuyển động khi bấm: nảy lên và lắc nhẹ. */
@Composable
fun AnimIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val reduce = LocalMotion.current.reduce
    val scope = rememberCoroutineScope()
    val rot = remember { Animatable(0f) }
    val sc = remember { Animatable(1f) }
    IconButton(
        onClick = {
            if (!reduce) {
                scope.launch { sc.animateTo(1.35f, tween(80)); sc.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
                scope.launch { rot.animateTo(-16f, tween(70)); rot.animateTo(0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow)) }
            }
            onClick()
        },
        modifier = modifier,
    ) { androidx.compose.material3.Icon(icon, desc, Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value; rotationZ = rot.value }, tint = tint) }
}

/** Huy hiệu gói của tài khoản: FREE, PRO, PRO MAX, mỗi loại một logo riêng. */
@Composable
fun TierBadge(tier: String, modifier: Modifier = Modifier) {
    val (label, colors, icon) = when (tier) {
        "promax" -> Triple("PRO MAX", listOf(Color(0xFFFFD166), Color(0xFFFF8A3D)), androidx.compose.material.icons.Icons.Filled.WorkspacePremium)
        "pro" -> Triple("PRO", listOf(Color(0xFF60A5FA), Color(0xFF7C3AED)), androidx.compose.material.icons.Icons.Filled.Bolt)
        else -> Triple("FREE", listOf(Color(0xFF64748B), Color(0xFF475569)), androidx.compose.material.icons.Icons.Filled.Eco)
    }
    val fg = if (tier == "promax") Color(0xFF3B1D00) else Color.White
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(colors)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (tier == "free") androidx.compose.material3.Icon(icon, null, Modifier.size(14.dp), tint = fg)
        else PulseIcon(icon, fg, 14.dp)
        androidx.compose.material3.Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
}

private val COVERS = listOf(
    listOf(Color(0xFF6D28D9), Color(0xFF2563EB), Color(0xFF06B6D4)),
    listOf(Color(0xFFF97316), Color(0xFFEC4899), Color(0xFF8B5CF6)),
    listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF22C55E)),
    listOf(Color(0xFF22D3EE), Color(0xFFA855F7), Color(0xFFF472B6)),
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF7C2D12)),
    listOf(Color(0xFFEC4899), Color(0xFFF43F5E), Color(0xFF7E22CE)),
)
val COVER_COUNT get() = COVERS.size

/** Ảnh bìa hoạt ảnh của gói: nền chuyển màu, quầng sáng trôi và hạt sáng bay. Chỉ vẽ lại ở bước draw nên nhẹ máy. */
@Composable
fun PlanCover(style: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val cols = COVERS[((style % COVERS.size) + COVERS.size) % COVERS.size]
    val reduce = LocalMotion.current.reduce
    val t = if (reduce) null else rememberInfiniteTransition(label = "cover").animateFloat(
        0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "c",
    )
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).drawBehind {
            val p = t?.value ?: 0.3f
            drawRect(Brush.linearGradient(cols, start = Offset(size.width * p * 0.3f, 0f), end = Offset(size.width, size.height)))
            val tau = (2 * Math.PI).toFloat()
            for (i in 0 until 4) {
                val a = (p + i * 0.25f) * tau
                val c = Offset(
                    size.width * (0.15f + 0.7f * (0.5f + 0.5f * kotlin.math.sin(a + i))),
                    size.height * (0.2f + 0.6f * (0.5f + 0.5f * kotlin.math.cos(a * 0.8f + i))),
                )
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), center = c, radius = size.height * 0.8f),
                    radius = size.height * 0.8f, center = c)
            }
            for (i in 0 until 16) {
                val x = ((i * 37) % 100) / 100f * size.width
                val y = ((((i * 53) % 100) / 100f) + p * (0.2f + (i % 3) * 0.12f)) % 1f * size.height
                drawCircle(Color.White.copy(alpha = 0.55f), radius = 1.2.dp.toPx() + (i % 3).dp.toPx() * 0.6f, center = Offset(x, y))
            }
        },
        content = content,
    )
}

/** Nút quay lại nổi bật: tròn, nền gradient sáng, viền trắng, mũi tên trắng; nảy nhẹ khi bấm. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalMotion.current.reduce
    val scope = rememberCoroutineScope()
    val sc = remember { Animatable(1f) }
    Box(
        modifier.size(42.dp)
            .graphicsLayer { scaleX = sc.value; scaleY = sc.value }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Neon, Sky)))
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .clickable {
                if (!reduce) scope.launch { sc.animateTo(0.82f, tween(70)); sc.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, "Quay lại", Modifier.size(24.dp), tint = Color.White) }
}
