package com.tlv.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tlv.app.Graph
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.tlv.app.tts.VOICE_LANGS
import com.tlv.app.tts.voiceLangOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen() {
    val s by Graph.settings.collectAsState()
    val speaking by Graph.engine.speaking.collectAsState()
    val ttsReady by Graph.tts.ready.collectAsState()
    val ttsErr by Graph.tts.error.collectAsState()
    val toast = LocalToaster.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Graph.tts.ensureInit() }
    DisposableEffect(Unit) { onDispose { if (!Graph.engine.active) Graph.tts.shutdown() } }

    val cur = voiceLangOf(s.ttsLang)
    val voices = remember(ttsReady, cur.tag) {
        if (ttsReady) Graph.tts.voices().filter { it.locale.language == cur.tag.substringBefore('-') }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name })) else emptyList()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth(), tint = Sky) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WaveBars(speaking, height = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nghe thử giọng", fontWeight = FontWeight.Bold)
                    Dim(if (ttsReady) (if (s.ttsViOffline && cur.tag.startsWith("vi")) "Giọng Việt tích hợp sẵn sàng (không cần mạng)" else if (s.ttsCloud) "Giọng online sẵn sàng" else "Engine sẵn sàng") else "Đang khởi tạo engine…")
                }
            }
            Spacer(Modifier.height(12.dp))
            NeonButton("Nghe thử", {
                busy = true
                scope.launch {
                    val err = Graph.engine.preview(cur.sample)
                    busy = false
                    if (err != null) toast.error(err)
                }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow,
                state = if (busy) BtnState.Loading else BtnState.Idle, enabled = ttsReady)
        }
        if (ttsErr != null && !s.ttsCloud && !(s.ttsViOffline && cur.tag.startsWith("vi"))) {
            GlassCard(Modifier.fillMaxWidth(), tint = Bad) {
                Text(ttsErr.orEmpty(), color = Bad)
                Spacer(Modifier.height(8.dp))
                NeonButton("Cài thêm dữ liệu giọng", {
                    runCatching {
                        ctx.startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { toast.error("Không mở được trang cài giọng") }
                }, Modifier.fillMaxWidth(), outlined = true, icon = Icons.Filled.Download)
            }
        }

        SectionTitle("Giọng nam / nữ")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("female" to "Giọng nữ", "male" to "Giọng nam").forEach { (id, label) ->
                val on = s.ttsGender == id
                GlassCard(
                    Modifier.weight(1f).clickable { Graph.update { copy(ttsGender = id) } },
                    tint = if (on) MaterialTheme.colorScheme.primary else Sky, corner = 16.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        SwitchRow("Giọng Việt tích hợp trong app", "Tiếng Việt đọc bằng giọng có sẵn trong app: chạy trên mọi ROM (kể cả ROM nội địa/không có giọng hệ thống), không cần mạng. Tắt để ưu tiên giọng online nam/nữ.", s.ttsViOffline) { v ->
            Graph.update { copy(ttsViOffline = v) }
        }
        SwitchRow("Giọng chất lượng cao (online)", "Giọng online chất lượng cao cần mạng; khi lỗi, tiếng Việt có giọng offline tích hợp, các ngôn ngữ khác dùng giọng hệ thống.", s.ttsCloud) { v ->
            Graph.update { copy(ttsCloud = v) }
        }

        SectionTitle("Ngôn ngữ giọng đọc")
        VOICE_LANGS.forEach { l ->
            val on = l.tag == cur.tag
            GlassCard(
                Modifier.fillMaxWidth().clickable { Graph.update { copy(ttsLang = l.tag, ttsVoice = "") } },
                tint = if (on) MaterialTheme.colorScheme.primary else Sky, corner = 16.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.native, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Dim(l.tag)
                    }
                    if (on) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (!s.ttsCloud) Dropdown("Giọng đọc", voices.firstOrNull { it.name == s.ttsVoice }?.let { voiceLabel(it) } ?: "Mặc định",
            listOf("" to "Mặc định") + voices.map { it.name to voiceLabel(it) }) { v -> Graph.update { copy(ttsVoice = v) } }
        SwitchRow("Tự nhận ngôn ngữ bình luận", "Bình luận tiếng Anh đọc giọng Anh, tiếng Trung đọc giọng Trung, tiếng Nhật, Hàn… Chữ Latin không dấu mơ hồ sẽ dùng ngôn ngữ đã chọn.", s.ttsAuto) { v ->
            Graph.update { copy(ttsAuto = v) }
        }
        if (!s.ttsCloud) Dim("Nếu thiếu giọng của một ngôn ngữ, cài thêm trong Cài đặt Android > Chuyển văn bản thành giọng nói.")

        SectionTitle("Âm thanh")
        SliderRow("Âm lượng đọc", s.ttsVolume, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> Graph.update { copy(ttsVolume = (v * 20).roundToInt() / 20f) } }
        SliderRow("Tốc độ đọc", s.ttsRate, 0.5f..2.5f, { "%.1fx".format(it) }) { v -> Graph.update { copy(ttsRate = (v * 10).roundToInt() / 10f) } }
        SliderRow("Cao độ", s.ttsPitch, 0.5f..2f, { "%.1f".format(it) }) { v -> Graph.update { copy(ttsPitch = (v * 10).roundToInt() / 10f) } }
        Dim("Âm lượng đọc là mức tương đối so với âm lượng nội dung của máy: chỉnh nút âm lượng cho to hơn.")

        SectionTitle("Nội dung đọc")
        SwitchRow("Đọc tên người dùng", "Đọc \"tên nói nội dung\" thay vì chỉ nội dung", s.readUsername) { v -> Graph.update { copy(readUsername = v) } }
        SwitchRow("Đọc emoji", "Tắt: bỏ emoji khỏi phần đọc", s.readEmoji) { v -> Graph.update { copy(readEmoji = v) } }
        SwitchRow("Đọc ký hiệu", "Đọc dấu câu và biểu tượng cảm xúc như . ! ? :))) < >", s.readSymbols) { v -> Graph.update { copy(readSymbols = v) } }
    }
}

private fun voiceLabel(v: android.speech.tts.Voice) =
    v.name + if (v.isNetworkConnectionRequired) " · online" else " · offline"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Dropdown(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value, {}, readOnly = true, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem({ Text(text) }, { onPick(id); open = false }) }
        }
    }
}

@Composable
fun FilterScreen() {
    val s by Graph.settings.collectAsState()
    var block by remember { mutableStateOf(s.blockWords.joinToString("\n")) }
    var prio by remember { mutableStateOf(s.priorityWords.joinToString("\n")) }
    fun words(t: String) = t.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dim("Bộ lọc chỉ ảnh hưởng việc hiển thị và đọc bình luận trong app, không thực hiện hành động nào trên tài khoản TikTok.")
        SectionTitle("Từ khóa")
        OutlinedTextField(block, { block = it; Graph.update { copy(blockWords = words(it)) } },
            Modifier.fillMaxWidth(), label = { Text("Từ khóa bỏ qua (mỗi dòng một từ)") }, minLines = 3, maxLines = 6)
        OutlinedTextField(prio, { prio = it; Graph.update { copy(priorityWords = words(it)) } },
            Modifier.fillMaxWidth(), label = { Text("Từ khóa ưu tiên (đọc trước)") }, minLines = 3, maxLines = 6)

        SectionTitle("Chống spam")
        SwitchRow("Bỏ bình luận trùng", "Bỏ qua nội dung giống bình luận gần đây", s.skipDuplicates) { v -> Graph.update { copy(skipDuplicates = v) } }
        SliderRow("Độ dài tối đa", s.maxLength.toFloat(), 20f..500f, { "${it.roundToInt()} ký tự" }) { v -> Graph.update { copy(maxLength = v.roundToInt()) } }
        SliderRow("Đọc tối đa mỗi phút", s.maxPerMinute.toFloat(), 0f..120f, { if (it < 1f) "Không giới hạn" else "${it.roundToInt()}" }) { v ->
            Graph.update { copy(maxPerMinute = v.roundToInt()) }
        }
        SliderRow("Hàng đợi tối đa", s.queueMax.toFloat(), 5f..200f, { "${it.roundToInt()} mục" }) { v -> Graph.update { copy(queueMax = v.roundToInt()) } }
        SwitchRow("Đọc emoji", null, s.readEmoji) { v -> Graph.update { copy(readEmoji = v) } }
        SwitchRow("Đọc tên người dùng", null, s.readUsername) { v -> Graph.update { copy(readUsername = v) } }
    }
}
