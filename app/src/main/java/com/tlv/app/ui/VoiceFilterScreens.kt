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
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Graph.tts.ensureInit() }
    DisposableEffect(Unit) { onDispose { if (!Graph.engine.active) Graph.tts.shutdown() } }

    val langs = remember(ttsReady) { if (ttsReady) Graph.tts.languages() else emptyList() }
    val voices = remember(ttsReady, s.ttsLang) {
        if (ttsReady) Graph.tts.voices().filter { it.locale.toLanguageTag() == s.ttsLang } else emptyList()
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
                    Dim(if (ttsReady) "Engine sẵn sàng" else "Đang khởi tạo engine…")
                }
            }
            Spacer(Modifier.height(12.dp))
            NeonButton("Nghe thử", {
                busy = true
                scope.launch {
                    val err = Graph.engine.preview("Xin chào, đây là giọng đọc thử nghiệm.")
                    busy = false
                    if (err != null) toast.error(err)
                }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow,
                state = if (busy) BtnState.Loading else BtnState.Idle, enabled = ttsReady)
        }
        if (ttsErr != null) {
            GlassCard(Modifier.fillMaxWidth(), tint = Bad) { Text(ttsErr.orEmpty(), color = Bad) }
        }

        SectionTitle("Ngôn ngữ và giọng")
        Dropdown("Ngôn ngữ", s.ttsLang, langs.ifEmpty { listOf(s.ttsLang) }) { l -> Graph.update { copy(ttsLang = l, ttsVoice = "") } }
        Dropdown("Giọng đọc", s.ttsVoice.ifEmpty { "Mặc định" }, listOf("Mặc định") + voices.map { it.name }) { v ->
            Graph.update { copy(ttsVoice = if (v == "Mặc định") "" else v) }
        }

        SectionTitle("Âm thanh")
        SliderRow("Tốc độ đọc", s.ttsRate, 0.5f..2.5f, { "%.1fx".format(it) }) { v -> Graph.update { copy(ttsRate = (v * 10).roundToInt() / 10f) } }
        SliderRow("Cao độ", s.ttsPitch, 0.5f..2f, { "%.1f".format(it) }) { v -> Graph.update { copy(ttsPitch = (v * 10).roundToInt() / 10f) } }
        Dim("Âm lượng dùng âm lượng nội dung của thiết bị.")

        SectionTitle("Nội dung đọc")
        SwitchRow("Đọc tên người dùng", "Đọc \"tên nói nội dung\" thay vì chỉ nội dung", s.readUsername) { v -> Graph.update { copy(readUsername = v) } }
        SwitchRow("Đọc emoji", "Tắt: bỏ emoji khỏi phần đọc", s.readEmoji) { v -> Graph.update { copy(readEmoji = v) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value, {}, readOnly = true, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(o) }, { onPick(o); open = false }) }
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
