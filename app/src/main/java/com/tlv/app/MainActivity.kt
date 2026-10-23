package com.tlv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlv.app.security.Verdict
import androidx.compose.runtime.getValue
import com.tlv.app.navigation.AppRoot
import com.tlv.app.ui.AppDialogs
import com.tlv.app.ui.TlvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLink(intent)
        setContent {
            val s by Graph.settings.collectAsState()
            val v by Graph.verdict.collectAsState()
            TlvTheme(dark = s.darkTheme, accent = s.accent, reduceMotion = s.reduceMotion, bgStyle = s.bgStyle, lang = s.appLang) {
                if (v == Verdict.OK) { AppRoot(); AppDialogs() }
                else Blocked { finishAffinity(); Process.killProcess(Process.myPid()) }
            }
        }
    }

    override fun onStart() { super.onStart(); Graph.foreground = true }
    override fun onStop() { super.onStop(); Graph.foreground = false }

    override fun onResume() {
        super.onResume()
        Graph.refreshAnnouncementAsync()
        Graph.resumeTick.value++
        Graph.checkUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** tlv://auth#access_token=...&type=recovery */
    private fun handleLink(i: Intent?) {
        val uri: Uri = i?.data ?: return
        if (uri.scheme != "tlv") return
        val params = uri.fragment.orEmpty().split('&').mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2) p[0] to Uri.decode(p[1]) else null
        }.toMap()
        if (params["type"] == "recovery") params["access_token"]?.let { Graph.recoveryToken.value = it }
        setIntent(Intent(this, MainActivity::class.java)) // không giữ token trong intent
    }
}

/** Hiện khi phát hiện môi trường bị can thiệp (bản release): dừng toàn bộ phiên và thoát. */
@Composable
private fun Blocked(onExit: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Không thể tiếp tục", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Ứng dụng phát hiện môi trường chạy không an toàn hoặc bản cài đặt đã bị chỉnh sửa. " +
                    "Hãy gỡ bản này và cài lại bản chính thức từ nhà phát triển.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onExit) { Text("Thoát") }
        }
    }
}
