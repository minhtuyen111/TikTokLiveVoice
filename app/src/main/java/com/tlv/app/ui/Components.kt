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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(surface.copy(alpha = 0.95f), tint.copy(alpha = 0.07f).compositeOver(surface))))
                .padding(16.dp),
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
    val scale by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, tween(90), label = "press")
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
            modifier = modifier.heightIn(min = 52.dp).scale(scale),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, container.copy(alpha = if (canClick) 0.8f else 0.3f)),
        ) { BtnContent(text, state, icon, container) }
    } else {
        Button(
            onClick = { if (canClick) onClick() }, enabled = canClick || state == BtnState.Loading, interactionSource = src,
            modifier = modifier.heightIn(min = 52.dp).scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = container, contentColor = Color.White,
                disabledContainerColor = container.copy(alpha = if (state == BtnState.Loading) 0.7f else 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            ),
        ) { BtnContent(text, state, icon, Color.White) }
    }
}

@Composable
private fun BtnContent(text: String, state: BtnState, icon: ImageVector?, tint: Color) {
    when (state) {
        BtnState.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
        else -> {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = tint); Spacer(Modifier.width(8.dp)) }
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

/** Chấm LIVE: chỉ chuyển động khi đang hoạt động và không bật giảm chuyển động. */
@Composable
fun LiveDot(active: Boolean, color: Color = Bad, size: Dp = 10.dp) {
    val reduce = LocalMotion.current.reduce
    val a = if (active && !reduce) {
        val t = rememberInfiniteTransition(label = "dot")
        t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(size).alpha(if (active) a else 0.4f).clip(CircleShape)
        .background(if (active) color else MaterialTheme.colorScheme.onSurfaceVariant))
}

/** Sóng âm khi TTS đang đọc. Đứng yên khi không đọc. */
@Composable
fun WaveBars(active: Boolean, modifier: Modifier = Modifier, bars: Int = 5, height: Dp = 28.dp) {
    val reduce = LocalMotion.current.reduce
    Row(modifier.height(height), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(bars) { i ->
            val h = if (active && !reduce) {
                val t = rememberInfiniteTransition(label = "w$i")
                t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420 + i * 90), RepeatMode.Reverse), label = "h").value
            } else if (active) 0.7f else 0.25f
            Box(Modifier.width(4.dp).fillMaxHeight(h).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Sky, MaterialTheme.colorScheme.primary))))
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
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
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
