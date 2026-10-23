package com.tlv.app.auth

import com.tlv.app.BuildConfig
import com.tlv.app.security.Obf
import com.tlv.app.network.Http
import com.tlv.app.network.await
import com.tlv.app.security.Session
import com.tlv.app.security.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

sealed class AuthResult {
    data class Ok(val message: String = "") : AuthResult()
    data class Fail(val message: String) : AuthResult()
}

/**
 * Giao tiếp với Supabase Auth (GoTrue) + PostgREST qua HTTPS.
 * Chỉ dùng anon key (công khai). Mọi quyền truy cập được kiểm soát bởi RLS ở backend.
 */
class AuthRepo(private val store: SessionStore) {
    internal val base: String by lazy { Obf.dec(BuildConfig.SB_U).trimEnd('/') }
    private val anon: String by lazy { Obf.dec(BuildConfig.SB_K) }
    private val json = "application/json".toMediaType()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    val configured get() = base.startsWith("https://") && !base.contains("YOUR-PROJECT") &&
        anon.length > 20 && !anon.startsWith("YOUR_")

    internal fun req(path: String, token: String? = null) =
        Request.Builder().url("$base$path")
            .header("apikey", anon)
            .header("Authorization", "Bearer ${token ?: anon}")

    private fun body(vararg p: Pair<String, Any?>) =
        JSONObject().also { o -> p.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) } }
            .toString().toRequestBody(json)

    private fun err(code: Int, text: String): String {
        val raw = runCatching {
            JSONObject(text).let { it.optString("msg").ifEmpty { it.optString("error_description").ifEmpty { it.optString("message") } } }
        }.getOrDefault("").lowercase()
        return when {
            code == 429 || "rate limit" in raw -> "Thao tác quá nhanh, vui lòng thử lại sau ít phút"
            "invalid login" in raw || "invalid_credentials" in raw -> "Sai thông tin đăng nhập"
            "email not confirmed" in raw -> "Email chưa được xác minh. Hãy kiểm tra hộp thư"
            "already" in raw || "duplicate" in raw || "database error" in raw ->
                "Email hoặc tên đăng nhập đã được sử dụng"
            "password" in raw -> "Mật khẩu không đáp ứng chính sách bảo mật"
            code in 500..599 -> "Máy chủ đang bận, thử lại sau"
            else -> "Không thể hoàn tất yêu cầu"
        }
    }

    private suspend inline fun <T> net(block: () -> T, onErr: (String) -> T): T =
        try { block() } catch (e: IOException) { onErr("Lỗi mạng, kiểm tra kết nối") }

    private fun parseSession(text: String, usernameFallback: String = "", role: String = "user"): Session? = runCatching {
        val o = JSONObject(text)
        val u = o.getJSONObject("user")
        val meta = u.optJSONObject("user_metadata")
        Session(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAtMs = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
            userId = u.getString("id"),
            email = u.optString("email"),
            username = meta?.optString("username").orEmpty().ifEmpty { usernameFallback },
            emailVerified = !u.isNull("email_confirmed_at") && u.optString("email_confirmed_at").isNotEmpty(),
            role = role,
        )
    }.getOrNull()

    /** Vai trò do máy chủ quyết định (bảng profiles, RLS chỉ cho đọc hồ sơ của chính mình). */
    private suspend fun fetchRole(uid: String, token: String, fallback: String): String = runCatching {
        val r = Http.client.newCall(req("/rest/v1/profiles?select=role,status&id=eq.$uid", token).get().build()).await()
        if (r.ok) {
            val o = JSONArray(r.body).optJSONObject(0)
            if (o?.optString("status") == "locked") "locked"
            else o?.optString("role")?.takeIf { it == "admin" || it == "user" } ?: fallback
        } else fallback
    }.getOrDefault(fallback)

    /** Khôi phục phiên đã lưu lúc khởi động. Trả về true nếu còn hợp lệ. */
    suspend fun restore(): Boolean {
        val s = store.load() ?: return false
        _session.value = s
        if (s.expiresAtMs - System.currentTimeMillis() > 60_000) return true
        return refresh()
    }

    suspend fun refresh(): Boolean {
        val s = store.load() ?: return false
        return net({
            val r = Http.client.newCall(
                req("/auth/v1/token?grant_type=refresh_token")
                    .post(body("refresh_token" to s.refreshToken)).build()
            ).await()
            if (r.ok) {
                val n = parseSession(r.body, s.username, s.role)
                if (n != null) { store.save(n, store.remembered()); _session.value = n; true } else false
            } else if (r.code == 400 || r.code == 401) { localLogout(); false }
            else true // lỗi tạm thời: giữ phiên, thử lại sau
        }, { true })
    }

    /** Token hợp lệ, tự làm mới nếu sắp hết hạn. */
    suspend fun validToken(): String? {
        val s = _session.value ?: return null
        if (s.expiresAtMs - System.currentTimeMillis() < 60_000) refresh()
        return _session.value?.accessToken
    }

    suspend fun signUp(username: String, email: String, password: String): AuthResult = net({
        // Kiểm tra trùng tên trước (RPC công khai chỉ trả true/false)
        val avail = Http.client.newCall(
            req("/rest/v1/rpc/username_available").post(body("p_username" to username)).build()
        ).await()
        if (avail.ok && avail.body.trim() == "false") return@net AuthResult.Fail("Tên đăng nhập đã được sử dụng")
        val r = Http.client.newCall(
            req("/auth/v1/signup").post(
                body("email" to email.trim(), "password" to password,
                    "data" to JSONObject().put("username", username))
            ).build()
        ).await()
        if (r.ok) AuthResult.Ok("Đã gửi email xác minh. Hãy kiểm tra hộp thư để kích hoạt tài khoản")
        else AuthResult.Fail(err(r.code, r.body))
    }, { AuthResult.Fail(it) })

    suspend fun signIn(identifier: String, password: String, remember: Boolean): AuthResult = net({
        var email = identifier.trim()
        if ('@' !in email) {
            val r = Http.client.newCall(
                req("/rest/v1/rpc/login_email_for").post(body("p_username" to email)).build()
            ).await()
            val found = if (r.ok) r.body.trim().trim('"') else ""
            if (found.isEmpty() || found == "null") return@net AuthResult.Fail("Sai thông tin đăng nhập")
            email = found
        }
        val r = Http.client.newCall(
            req("/auth/v1/token?grant_type=password").post(body("email" to email, "password" to password)).build()
        ).await()
        if (!r.ok) return@net AuthResult.Fail(err(r.code, r.body))
        val s0 = parseSession(r.body, identifier) ?: return@net AuthResult.Fail("Phản hồi không hợp lệ")
        val role0 = fetchRole(s0.userId, s0.accessToken, "user")
        if (role0 == "locked") return@net AuthResult.Fail("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên")
        val s = s0.copy(role = role0)
        store.save(s, remember); _session.value = s
        AuthResult.Ok()
    }, { AuthResult.Fail(it) })

    /** Luôn trả thông báo chung, không tiết lộ email có tồn tại hay không. */
    suspend fun requestReset(email: String): AuthResult = net({
        val r = Http.client.newCall(
            req("/auth/v1/recover").post(body("email" to email.trim())).build()
        ).await()
        if (r.code == 429) AuthResult.Fail(err(429, r.body))
        else AuthResult.Ok("Nếu email tồn tại, chúng tôi đã gửi liên kết đặt lại mật khẩu")
    }, { AuthResult.Fail(it) })

    /** Đặt mật khẩu mới bằng access token từ liên kết khôi phục (dùng một lần, có hạn). */
    suspend fun setPassword(token: String, newPassword: String): AuthResult = net({
        val r = Http.client.newCall(
            req("/auth/v1/user", token).put(body("password" to newPassword)).build()
        ).await()
        if (r.ok) AuthResult.Ok("Đã đổi mật khẩu") else AuthResult.Fail(err(r.code, r.body))
    }, { AuthResult.Fail(it) })

    /** Đổi mật khẩu có xác thực lại bằng mật khẩu hiện tại. */
    suspend fun changePassword(current: String, newPassword: String): AuthResult {
        val s = _session.value ?: return AuthResult.Fail("Chưa đăng nhập")
        val re = signInRaw(s.email, current) ?: return AuthResult.Fail("Mật khẩu hiện tại không đúng")
        return setPassword(re, newPassword)
    }

    /** Xác thực lại bằng mật khẩu trước thao tác nhạy cảm. */
    suspend fun reauth(password: String): Boolean {
        val s = _session.value ?: return false
        return signInRaw(s.email, password) != null
    }

    private suspend fun signInRaw(email: String, password: String): String? = net({
        val r = Http.client.newCall(
            req("/auth/v1/token?grant_type=password").post(body("email" to email, "password" to password)).build()
        ).await()
        if (r.ok) runCatching { JSONObject(r.body).getString("access_token") }.getOrNull() else null
    }, { null })

    /** Làm mới trạng thái xác minh email. */
    suspend fun refreshProfile(): Session? {
        val t = validToken() ?: return null
        val s = _session.value ?: return null
        return net({
            val r = Http.client.newCall(req("/auth/v1/user", t).get().build()).await()
            if (r.ok) {
                val u = JSONObject(r.body)
                val n = s.copy(
                    emailVerified = !u.isNull("email_confirmed_at") && u.optString("email_confirmed_at").isNotEmpty(),
                    role = fetchRole(s.userId, t, s.role),
                )
                if (n.role == "locked") { signOut(false); return@net null }
                store.save(n, store.remembered()); _session.value = n; n
            } else s
        }, { s })
    }

    /** scope: "local" (thiết bị này) hoặc "global" (mọi thiết bị, thu hồi mọi phiên). */
    suspend fun signOut(global: Boolean = false) {
        val t = _session.value?.accessToken
        if (t != null) {
            runCatching {
                Http.client.newCall(
                    req("/auth/v1/logout?scope=${if (global) "global" else "local"}", t)
                        .post(ByteArray(0).toRequestBody(null)).build()
                ).await()
            }
        }
        localLogout()
    }

    private fun localLogout() { store.clear(); _session.value = null }

    suspend fun requestDeletion(): AuthResult {
        val t = validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        val s = _session.value!!
        return net({
            val r = Http.client.newCall(
                req("/rest/v1/deletion_requests", t).header("Prefer", "return=minimal")
                    .post(body("user_id" to s.userId)).build()
            ).await()
            if (r.ok || r.code == 409) AuthResult.Ok("Đã ghi nhận yêu cầu xóa tài khoản")
            else AuthResult.Fail("Không thể gửi yêu cầu")
        }, { AuthResult.Fail(it) })
    }
}
