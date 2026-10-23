@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tlv.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Màu riêng của hộp thoại: tím chàm sáng hơn mọi màu nền của app nên luôn tách biệt khỏi giao diện phía sau. */
val PopupTop = Color(0xFF2A3170)
val PopupBottom = Color(0xFF161B45)
val PopupText = Color(0xFFF4F6FF)
val PopupDim = Color(0xFFB9C2EC)

/**
 * Hộp thoại đẹp: nền tím chàm, viền gradient sáng, bóng phát sáng, vào bằng hiệu ứng nảy và nút đóng nổi bật.
 * `header` là phần trên cùng (ảnh bìa/biểu tượng); `content` là phần nội dung.
 */
@Composable
fun FancyDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    glow: Color = Neon,
    header: (@Composable BoxScope.() -> Unit)? = null,
    headerHeight: Dp = 128.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false),
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val sc = animateFloatAsState(if (shown) 1f else 0.82f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "dlgScale")
        val al = animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "dlgAlpha")
        val shape = RoundedCornerShape(30.dp)
        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = al.value }) {
            Column(
                Modifier.fillMaxWidth()
                    .shadow(28.dp, shape, ambientColor = glow, spotColor = glow)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(PopupTop, PopupBottom)))
                    .border(2.dp, Brush.linearGradient(listOf(Neon, Sky, Color(0xFFF472B6))), shape),
            ) {
                CompositionLocalProvider(LocalContentColor provides PopupText) {
                    if (header != null) Box(Modifier.fillMaxWidth().height(headerHeight), content = header)
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                }
            }
            if (dismissible) Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(Icons.Filled.Close, "Đóng", Modifier.size(20.dp), tint = Color.White) }
        }
    }
}

/** Dấu tích xanh: vòng tròn vẽ dần, nảy lên, rồi nét tích được vẽ theo từng đoạn. */
@Composable
fun AnimatedCheck(size: Dp = 112.dp, color: Color = Ok) {
    val reduce = LocalMotion.current.reduce
    val ring = remember { Animatable(if (reduce) 1f else 0f) }
    val tick = remember { Animatable(if (reduce) 1f else 0f) }
    val pop = remember { Animatable(if (reduce) 1f else 0.55f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        ring.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        tick.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        val w = this.size.width; val h = this.size.height
        val stroke = w * 0.07f
        drawCircle(color.copy(alpha = 0.20f * ring.value), radius = w / 2)
        drawArc(
            color, startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(w - stroke, h - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val path = Path().apply { moveTo(w * 0.28f, h * 0.53f); lineTo(w * 0.44f, h * 0.69f); lineTo(w * 0.74f, h * 0.36f) }
        val seg = Path()
        val pm = PathMeasure().apply { setPath(path, false) }
        pm.getSegment(0f, pm.length * tick.value, seg, true)
        drawPath(seg, color, style = Stroke(stroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private class Piece(val angle: Float, val speed: Float, val color: Color, val w: Float, val h: Float, val spin: Float)

/** Pháo giấy nổ ra từ giữa phía trên rồi rơi xuống. Vẽ ở bước draw nên không làm giao diện phải dựng lại. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val reduce = LocalMotion.current.reduce
    val pieces = remember {
        val r = Random(7)
        val cols = listOf(Color(0xFFFFD166), Color(0xFFF472B6), Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFA78BFA), Color(0xFFFB923C))
        List(46) {
            Piece(
                angle = (-Math.PI.toFloat()) * (0.1f + 0.8f * r.nextFloat()), speed = 0.35f + 0.75f * r.nextFloat(),
                color = cols[r.nextInt(cols.size)], w = 6f + 6f * r.nextFloat(), h = 10f + 8f * r.nextFloat(), spin = 220f * (r.nextFloat() - 0.5f),
            )
        }
    }
    val t = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) t.animateTo(1f, tween(2600, easing = LinearOutSlowInEasing)) }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val ox = size.width / 2; val oy = size.height * 0.30f
        pieces.forEach { c ->
            val x = ox + cos(c.angle) * c.speed * size.width * p * 1.15f
            val y = oy + sin(c.angle) * c.speed * size.width * p * 0.9f + 0.9f * size.height * p * p
            val a = (1f - p * p).coerceIn(0f, 1f)
            rotate(c.spin * p * 6f, Offset(x, y)) {
                drawRect(c.color.copy(alpha = a), Offset(x - c.w / 2, y - c.h / 2), Size(c.w, c.h))
            }
        }
    }
}

/** Hộp thoại chúc mừng: pháo giấy, dấu tích có hiệu ứng, tiêu đề phóng lên. */
@Composable
fun SuccessDialog(title: String, subtitle: String, detail: String? = null, tier: String? = null, onClose: () -> Unit) {
    val reduce = LocalMotion.current.reduce
    var show by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { if (!reduce) { kotlinx.coroutines.delay(650); show = true } }
    val ts = animateFloatAsState(if (show) 1f else 0.5f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "t")
    val ta = animateFloatAsState(if (show) 1f else 0f, tween(300), label = "ta")
    FancyDialog(onDismiss = onClose, glow = Ok) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(10.dp))
                AnimatedCheck(112.dp)
                Column(
                    Modifier.graphicsLayer { scaleX = ts.value; scaleY = ts.value; alpha = ta.value },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = PopupText, textAlign = TextAlign.Center)
                    androidx.compose.material3.Text(subtitle, fontSize = 15.sp, color = PopupDim, textAlign = TextAlign.Center)
                    if (tier != null) TierBadge(tier)
                    if (detail != null) androidx.compose.material3.Text(detail, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD166), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                NeonButton("Tuyệt vời", onClose, Modifier.fillMaxWidth())
            }
            Confetti(Modifier.matchParentSize())
        }
    }
}

/** Hộp thoại thông báo của quản trị: chuông lắc, tiêu đề, nội dung cuộn được. */
@Composable
fun AnnouncementDialog(title: String, body: String, onClose: () -> Unit) {
    val reduce = LocalMotion.current.reduce
    val swing = if (reduce) null else rememberInfiniteTransition(label = "bell").animateFloat(
        -14f, 14f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s",
    )
    FancyDialog(
        onDismiss = onClose, glow = Sky, headerHeight = 112.dp,
        header = {
            PlanCover(3, Modifier.matchParentSize()) {
                Box(Modifier.align(Alignment.Center)) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Campaign, null, Modifier.size(58.dp).graphicsLayer { rotationZ = swing?.value ?: 0f }, tint = Color.White,
                    )
                }
            }
        },
    ) {
        androidx.compose.material3.Text(title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = PopupText)
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            androidx.compose.material3.Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = PopupDim)
        }
        NeonButton("Đã hiểu", onClose, Modifier.fillMaxWidth())
    }
}
