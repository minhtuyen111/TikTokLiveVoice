package com.tlv.app.live

import com.tlv.app.network.Http
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.coroutines.resume

sealed interface Outcome {
    data object Ended : Outcome
    data class Fatal(val message: String) : Outcome
    data class Dropped(val message: String) : Outcome
}

/**
 * Nguồn bình luận: WebSocket (wss) tới "cầu nối" do bạn tự triển khai (xem thư mục bridge/,
 * dùng thư viện mã nguồn mở tiktok-live-connector). App không giữ mật khẩu TikTok.
 *
 * Giao thức tin nhắn JSON:
 *  {"type":"comment","user":"abc","text":"xin chào"}
 *  {"type":"event","kind":"follow|share|gift","user":"abc","name":"Rose","count":1}
 *  {"type":"status","state":"connected"}
 *  {"type":"status","state":"ended"}                    -> livestream đã kết thúc
 *  {"type":"status","state":"error","fatal":true,"message":"..."}
 */
class LiveSource {
    fun buildUrl(bridge: String, channel: String): String? {
        if (!bridge.startsWith("wss://")) return null
        val http = ("https://" + bridge.removePrefix("wss://")).toHttpUrlOrNull() ?: return null
        return http.newBuilder().addQueryParameter("channel", channel).build().toString()
    }

    suspend fun run(
        bridge: String,
        channel: String,
        token: String?,
        onConnected: () -> Unit,
        onComment: (user: String, text: String) -> Unit,
        onEvent: (kind: String, user: String, name: String, count: Int) -> Unit = { _, _, _, _ -> },
    ): Outcome {
        val url = buildUrl(bridge, channel)
            ?: return Outcome.Fatal("Dịch vụ kết nối chưa sẵn sàng. Vui lòng thử lại sau hoặc liên hệ quản trị viên")
        return suspendCancellableCoroutine { cont ->
            fun finish(o: Outcome) { if (cont.isActive) cont.resume(o) }

            val ws = Http.wsClient.newWebSocket(
                Request.Builder().url(url).apply { if (token != null) header("Authorization", "Bearer $token") }.build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = onConnected()

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                        when (o.optString("type")) {
                            "comment" -> onComment(o.optString("user").take(64), o.optString("text").take(1000))
                            "event" -> o.optString("kind").takeIf { it == "follow" || it == "share" || it == "gift" }?.let {
                                onEvent(it, o.optString("user").take(64), o.optString("name").take(40), o.optInt("count", 1).coerceIn(1, 9999))
                            }
                            "status" -> when (o.optString("state")) {
                                "connected" -> onConnected()
                                "ended" -> { webSocket.close(1000, null); finish(Outcome.Ended) }
                                "error" -> {
                                    val m = o.optString("message").ifEmpty { "Không thể truy cập livestream" }
                                    if (o.optBoolean("fatal")) { webSocket.close(1000, null); finish(Outcome.Fatal(m)) }
                                    else { webSocket.close(1001, null); finish(Outcome.Dropped(m)) }
                                }
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                        finish(Outcome.Dropped("Kết nối bị đóng"))

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                        finish(Outcome.Dropped("Mất kết nối tới cầu nối"))
                },
            )
            cont.invokeOnCancellation { ws.cancel() }
        }
    }
}
