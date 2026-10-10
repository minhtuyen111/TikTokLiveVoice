package com.tlv.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlv.app.Graph
import com.tlv.app.live.Comment
import com.tlv.app.live.CommentStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String) { ALL("Tất cả"), PENDING("Chờ đọc"), SPOKEN("Đã đọc"), FILTERED("Bị lọc") }

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen() {
    val all by Graph.engine.comments.collectAsState()
    val stats by Graph.engine.stats.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.ALL) }
    var auto by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val toast = LocalToaster.current
    val state = rememberLazyListState()

    val shown = remember(all, query, tab) {
        val q = query.trim().lowercase()
        all.filter { c ->
            (q.isEmpty() || c.text.lowercase().contains(q) || c.user.lowercase().contains(q)) &&
                when (tab) {
                    Tab.ALL -> true
                    Tab.PENDING -> c.status == CommentStatus.PENDING || c.status == CommentStatus.SPEAKING
                    Tab.SPOKEN -> c.status == CommentStatus.SPOKEN
                    Tab.FILTERED -> c.status == CommentStatus.FILTERED
                }
        }
    }

    LaunchedEffect(shown.lastOrNull()?.id, shown.size, auto) {
        if (auto && shown.isNotEmpty()) state.scrollToItem(shown.lastIndex)
    }
    // Người dùng tự cuộn lên: tạm dừng tự cuộn
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress && state.canScrollForward) auto = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Tìm theo nội dung hoặc tên") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Xóa") } },
                shape = RoundedCornerShape(16.dp),
            )
            IconButton({ confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Xóa lịch sử phiên") }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                FilterChip(tab == t, { tab = t }, label = { Text(t.label, fontSize = 12.sp) })
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Dim("${shown.size} mục · chờ ${stats.queued}", Modifier.weight(1f))
            if (stats.queued > 0) TextButton({ Graph.engine.clearQueue(); toast.success("Đã xóa hàng đợi") }) { Text("Xóa hàng đợi") }
            Text("Tự cuộn", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(auto, { auto = it }, Modifier.padding(start = 6.dp))
        }

        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Forum, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Dim(if (all.isEmpty()) "Chưa có bình luận. Hãy kết nối LIVE." else "Không có mục phù hợp.", Modifier.padding(top = 8.dp))
                }
            } else {
                LazyColumn(
                    state = state, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { c ->
                        CommentRow(c, Modifier.animateItem())
                    }
                }
            }
            if (!auto && shown.isNotEmpty()) {
                SmallFloatingActionButton(
                    { auto = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White,
                ) { Icon(Icons.Filled.KeyboardDoubleArrowDown, "Cuộn tới mới nhất") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xóa danh sách phiên hiện tại?") },
            text = { Text("Các bình luận đang hiển thị và hàng đợi sẽ bị xóa khỏi bộ nhớ.") },
            confirmButton = { TextButton({ Graph.engine.clearComments(); confirmClear = false; toast.success("Đã xóa") }) { Text("Xóa") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun CommentRow(c: Comment, modifier: Modifier = Modifier) {
    val (statusText, statusColor) = when (c.status) {
        CommentStatus.PENDING -> (if (c.filterNote == "prio") "Ưu tiên" else "Chờ đọc") to Warn
        CommentStatus.SPEAKING -> "Đang đọc" to Sky
        CommentStatus.SPOKEN -> "Đã đọc" to Ok
        CommentStatus.FILTERED -> (c.filterNote.ifEmpty { "Bị lọc" }) to Bad
    }
    val dim = c.status == CommentStatus.FILTERED
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (dim) 0.5f else 1f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (c.status == CommentStatus.SPEAKING) Sky.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.user.ifEmpty { "Ẩn danh" }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f), maxLines = 1)
                Text(timeFmt.format(Date(c.time)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(c.text, Modifier.padding(vertical = 4.dp))
            Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
        }
    }
}
