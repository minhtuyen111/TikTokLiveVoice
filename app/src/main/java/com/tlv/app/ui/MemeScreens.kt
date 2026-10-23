@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tlv.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tlv.app.Graph
import com.tlv.app.auth.AuthResult
import com.tlv.app.data.MEME_EVENTS
import com.tlv.app.data.Meme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Mọi người dùng: chọn bật/tắt hoặc tiếng cho từng sự kiện. Không thể thêm âm thanh mới. */
@Composable
fun MemeScreen() {
    val s by Graph.settings.collectAsState()
    val list by Graph.memeList.collectAsState()
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { loading = true; Graph.refreshMemes(); loading = false }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dim("Tiếng phát khi có người theo dõi, chia sẻ hoặc tặng quà trong LIVE. Thư viện âm thanh do quản trị viên cung cấp; bạn chỉ chọn tiếng cho từng sự kiện.")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        MEME_EVENTS.forEach { (ev, label) ->
            val pool = list.filter { it.event == ev }
            val sel = when (ev) { "follow" -> s.memeFollow; "share" -> s.memeShare; else -> s.memeGift }
            SectionTitle(label)
            val options = listOf("off" to "Tắt", "random" to "Ngẫu nhiên") + pool.map { it.id to it.title }
            Dropdown("Tiếng khi $label", options.firstOrNull { it.first == sel }?.second ?: "Ngẫu nhiên", options) { v ->
                Graph.update {
                    when (ev) { "follow" -> copy(memeFollow = v); "share" -> copy(memeShare = v); else -> copy(memeGift = v) }
                }
            }
            if (pool.isEmpty() && !loading) Dim("Quản trị viên chưa thêm âm thanh cho sự kiện này.")
            else if (sel != "off") {
                TextButton({ (pool.firstOrNull { it.id == sel } ?: pool.randomOrNull())?.let { Graph.previewMeme(it) } }, enabled = pool.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Nghe thử")
                }
            }
        }
        SectionTitle("Thông báo bằng giọng nói")
        SwitchRow("Đọc tên người tương tác", "Đọc \"tên đã theo dõi / chia sẻ / tặng quà\" bằng giọng đọc", s.announceEvents) { v ->
            Graph.update { copy(announceEvents = v) }
        }
    }
}
