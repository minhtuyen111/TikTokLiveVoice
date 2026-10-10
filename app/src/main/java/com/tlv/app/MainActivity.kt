package com.tlv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tlv.app.navigation.AppRoot
import com.tlv.app.ui.TlvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLink(intent)
        setContent {
            val s by Graph.settings.collectAsState()
            TlvTheme(dark = s.darkTheme, accent = s.accent, reduceMotion = s.reduceMotion) { AppRoot() }
        }
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
