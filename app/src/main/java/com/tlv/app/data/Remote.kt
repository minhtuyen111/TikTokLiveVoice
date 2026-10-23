package com.tlv.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.tlv.app.BuildConfig
import com.tlv.app.auth.AuthRepo
import com.tlv.app.auth.AuthResult
import com.tlv.app.network.Http
import com.tlv.app.network.await
import com.tlv.app.security.Obf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class Meme(val id: String, val event: String, val title: String, val path: String)

data class AdminUser(
    val id: String, val username: String, val email: String, val role: String, val status: String,
    val lastSeenMs: Long?, val watching: String, val deletion: Boolean, val balance: Long = 0,
    val tier: String = "free", val planExpiresMs: Long? = null,
) {
    fun online(now: Long = System.currentTimeMillis()) = lastSeenMs != null && now - lastSeenMs < 150_000
}

data class PayConfig(
    val enabled: Boolean, val bank: String, val account: String, val name: String, val min: Long, val max: Long,
) {
    val ready get() = enabled && bank.isNotBlank() && account.isNotBlank()
}
data class OrderRow(val id: String, val code: String, val amount: Long, val status: String, val createdMs: Long)
data class LedgerRow(val delta: Long, val reason: String, val createdMs: Long, val after: Long)
data class AdminOrder(
    val username: String, val email: String, val code: String, val amount: Long, val status: String,
    val paid: Long, val createdMs: Long,
)

data class Plan(
    val id: String, val tier: String, val title: String, val days: Int, val price: Long, val maxComments: Int,
    val cover: Int, val description: String, val perks: String, val sort: Int, val active: Boolean,
)
data class Entitlement(
    val tier: String, val expiresMs: Long?, val dailyLimit: Int, val usedToday: Int, val blocked: Boolean, val trialDays: Int,
    val planId: String = "",
)

data class Announcement(val id: String, val title: String, val body: String, val createdMs: Long)

data class UpdateInfo(val code: Int, val name: String, val url: String, val notes: String, val minCode: Int) {
    fun force(current: Int) = minCode > current
}

val MEME_EVENTS = listOf("follow" to "Theo dõi", "share" to "Chia sẻ", "gift" to "Tặng quà")

/** Dữ liệu dùng chung từ Supabase: địa chỉ máy chủ kết nối (tự động) và thư viện âm thanh meme (admin quản lý). */
class Remote(private val auth: AuthRepo, private val cachedBridge: () -> String, private val saveBridge: (String) -> Unit) {
    private val json = "application/json".toMediaType()
    @Volatile private var bridgeAt = 0L
    @Volatile private var bridge = ""

    fun publicUrl(path: String) = "${auth.base}/storage/v1/object/public/memes/$path"

    /** Địa chỉ wss:// của máy chủ kết nối: lấy từ Supabase (do admin đặt), có cache; rơi về giá trị lúc build. */
    suspend fun bridgeUrl(): String {
        val now = System.currentTimeMillis()
        if (bridge.isNotEmpty() && now - bridgeAt < 300_000) return bridge
        val fetched = runCatching {
            val t = auth.validToken()
            val r = Http.client.newCall(auth.req("/rest/v1/app_config?select=value&key=eq.bridge_url", t).get().build()).await()
            if (r.ok) JSONArray(r.body).optJSONObject(0)?.optString("value").orEmpty().trim() else ""
        }.getOrDefault("")
        val url = fetched.ifEmpty { cachedBridge().ifEmpty { Obf.dec(BuildConfig.BRIDGE_E) } }
        if (fetched.isNotEmpty() && fetched != cachedBridge()) saveBridge(fetched)
        bridge = url; bridgeAt = now
        return url
    }

    suspend fun setBridge(url: String): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        if (!url.startsWith("wss://")) return AuthResult.Fail("Địa chỉ phải bắt đầu bằng wss://")
        return try {
            val b = JSONObject().put("key", "bridge_url").put("value", url).toString().toRequestBody(json)
            val r = Http.client.newCall(
                auth.req("/rest/v1/app_config?on_conflict=key", t)
                    .header("Prefer", "resolution=merge-duplicates,return=minimal").post(b).build()
            ).await()
            if (r.ok) { bridge = url; bridgeAt = System.currentTimeMillis(); saveBridge(url); AuthResult.Ok("Đã lưu") }
            else AuthResult.Fail(if (r.code == 401 || r.code == 403) "Bạn không có quyền quản trị" else "Không thể lưu")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun memes(): List<Meme>? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/meme_sounds?select=id,event,title,path&order=created_at.desc", t).get().build()).await()
        if (!r.ok) null else JSONArray(r.body).let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { o -> Meme(o.optString("id"), o.optString("event"), o.optString("title"), o.optString("path")) }
            }
        }
    } catch (e: Exception) { null }

    /** Chỉ admin: máy chủ từ chối (RLS) với mọi tài khoản khác dù app có bị chỉnh sửa. */
    suspend fun addMeme(ctx: Context, event: String, title: String, uri: Uri): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        val mime = ctx.contentResolver.getType(uri).orEmpty().ifEmpty { "audio/mpeg" }
        if (!mime.startsWith("audio/")) return AuthResult.Fail("Chỉ nhận file âm thanh")
        val size = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        } ?: -1L
        if (size > MAX_BYTES) return AuthResult.Fail("File quá lớn (tối đa 2 MB)")
        val bytes = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = s.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return AuthResult.Fail("File quá lớn (tối đa 2 MB)")
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return AuthResult.Fail("Không đọc được file")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
        val path = "$event/${UUID.randomUUID()}.$ext"
        return try {
            val up = Http.client.newCall(
                auth.req("/storage/v1/object/memes/$path", t).header("Content-Type", mime).post(bytes.toRequestBody(mime.toMediaType())).build()
            ).await()
            if (!up.ok) return AuthResult.Fail(if (up.code == 401 || up.code == 403) "Bạn không có quyền quản trị" else "Tải lên thất bại")
            val row = JSONObject().put("event", event).put("title", title.trim().take(60)).put("path", path).toString().toRequestBody(json)
            val ins = Http.client.newCall(auth.req("/rest/v1/meme_sounds", t).header("Prefer", "return=minimal").post(row).build()).await()
            if (ins.ok) AuthResult.Ok("Đã thêm âm thanh")
            else {
                Http.client.newCall(auth.req("/storage/v1/object/memes/$path", t).delete().build()).await()
                AuthResult.Fail("Không thể lưu âm thanh")
            }
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun deleteMeme(m: Meme): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val r = Http.client.newCall(auth.req("/rest/v1/meme_sounds?id=eq.${m.id}", t).delete().build()).await()
            if (!r.ok) return AuthResult.Fail("Không thể xóa")
            Http.client.newCall(auth.req("/storage/v1/object/memes/${m.path}", t).delete().build()).await()
            AuthResult.Ok("Đã xóa")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    // ---------- Theo dõi hoạt động & quản lý người dùng ----------
    /** Ghi dấu "đang dùng app" và kênh đang theo dõi (nếu có). Lỗi mạng thì bỏ qua. */
    suspend fun touch(watching: String?) {
        val t = auth.validToken() ?: return
        runCatching {
            val b = JSONObject().put("p_watching", watching ?: JSONObject.NULL).toString().toRequestBody(json)
            Http.client.newCall(auth.req("/rest/v1/rpc/touch_presence", t).post(b).build()).await()
        }
    }

    suspend fun adminUsers(): List<AdminUser>? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/rpc/admin_list_users", t).post("{}".toRequestBody(json)).build()).await()
        if (!r.ok) null else JSONArray(r.body).let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { o ->
                    AdminUser(
                        o.optString("id"), o.optString("username"), o.optString("email"), o.optString("role"), o.optString("status"),
                        o.optString("last_seen").takeIf { it.isNotEmpty() && it != "null" }
                            ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
                        o.optString("watching").takeIf { it != "null" }.orEmpty(), o.optBoolean("deletion_requested"), o.optLong("balance"),
                        o.optString("tier", "free").ifEmpty { "free" }, o.optString("plan_expires").takeIf { it.isNotEmpty() && it != "null" }?.let { x -> runCatching { java.time.OffsetDateTime.parse(x).toInstant().toEpochMilli() }.getOrNull() },
                    )
                }
            }
        }
    } catch (e: Exception) { null }

    private suspend fun rpc(name: String, body: JSONObject): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val r = Http.client.newCall(auth.req("/rest/v1/rpc/$name", t).post(body.toString().toRequestBody(json)).build()).await()
            if (r.ok) AuthResult.Ok("Đã cập nhật") else AuthResult.Fail(if (r.code == 401 || r.code == 403 || r.body.contains("forbidden")) "Bạn không có quyền quản trị" else "Không thể thực hiện")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun setRole(id: String, role: String) = rpc("admin_set_role", JSONObject().put("target", id).put("new_role", role))
    suspend fun setStatus(id: String, status: String) = rpc("admin_set_status", JSONObject().put("target", id).put("new_status", status))

    // ---------- Cập nhật bản mới ----------
    /** Đọc thông tin bản mới nhất (không cần đăng nhập). null nếu chưa có hoặc lỗi. */
    suspend fun updateInfo(): UpdateInfo? = try {
        val r = Http.client.newCall(
            auth.req("/rest/v1/app_config?select=key,value&key=in.(latest_code,latest_name,update_url,update_notes,min_code)", null).get().build()
        ).await()
        if (!r.ok) null else {
            val a = JSONArray(r.body)
            val m = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optString("key") to it.optString("value") }
            val code = m["latest_code"]?.toIntOrNull()
            if (code == null || (m["update_url"].orEmpty()).isBlank()) null
            else UpdateInfo(code, m["latest_name"].orEmpty(), m["update_url"].orEmpty(), m["update_notes"].orEmpty(), m["min_code"]?.toIntOrNull() ?: 0)
        }
    } catch (e: Exception) { null }

    /** Chỉ admin: tải APK lên kho `apk` (nếu có file) rồi ghi thông tin bản mới vào cấu hình. */
    suspend fun publishUpdate(code: Int, name: String, notes: String, force: Boolean, apk: File?, url: String): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            var link = url.trim()
            if (apk != null) {
                val path = "voicelive-$code.apk"
                val c = Http.client.newBuilder().writeTimeout(5, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()
                val up = c.newCall(
                    auth.req("/storage/v1/object/apk/$path", t).header("x-upsert", "true")
                        .post(apk.asRequestBody("application/vnd.android.package-archive".toMediaType())).build()
                ).await()
                if (!up.ok) return AuthResult.Fail(if (up.code == 401 || up.code == 403) "Bạn không có quyền quản trị" else "Tải APK lên thất bại (tối đa 50 MB)")
                link = "${auth.base}/storage/v1/object/public/apk/$path"
            }
            if (!link.startsWith("https://")) return AuthResult.Fail("Thiếu đường dẫn tải hợp lệ")
            val rows = JSONArray()
                .put(JSONObject().put("key", "latest_code").put("value", code.toString()))
                .put(JSONObject().put("key", "latest_name").put("value", name.ifBlank { code.toString() }))
                .put(JSONObject().put("key", "update_url").put("value", link))
                .put(JSONObject().put("key", "update_notes").put("value", notes.take(500)))
                .put(JSONObject().put("key", "min_code").put("value", if (force) code.toString() else "0"))
            val r = Http.client.newCall(
                auth.req("/rest/v1/app_config?on_conflict=key", t).header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(rows.toString().toRequestBody(json)).build()
            ).await()
            if (r.ok) AuthResult.Ok("Đã phát hành bản cập nhật") else AuthResult.Fail("Không thể lưu thông tin bản mới")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    // ---------- Ví & thanh toán (mọi thao tác cộng tiền diễn ra ở máy chủ) ----------
    private fun ms(s: String?): Long = s?.takeIf { it.isNotEmpty() && it != "null" }
        ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

    suspend fun payConfig(): PayConfig? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(
            auth.req("/rest/v1/app_config?select=key,value&key=in.(pay_enabled,pay_bank,pay_account,pay_name,pay_min,pay_max)", t).get().build()
        ).await()
        if (!r.ok) null else {
            val a = JSONArray(r.body)
            val m = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optString("key") to it.optString("value") }
            PayConfig(
                m["pay_enabled"] == "1", m["pay_bank"].orEmpty(), m["pay_account"].orEmpty(), m["pay_name"].orEmpty(),
                m["pay_min"]?.toLongOrNull() ?: 10_000, m["pay_max"]?.toLongOrNull() ?: 5_000_000,
            )
        }
    } catch (e: Exception) { null }

    suspend fun savePayConfig(c: PayConfig): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val rows = JSONArray()
                .put(JSONObject().put("key", "pay_enabled").put("value", if (c.enabled) "1" else "0"))
                .put(JSONObject().put("key", "pay_bank").put("value", c.bank.trim()))
                .put(JSONObject().put("key", "pay_account").put("value", c.account.trim()))
                .put(JSONObject().put("key", "pay_name").put("value", c.name.trim()))
                .put(JSONObject().put("key", "pay_min").put("value", c.min.toString()))
                .put(JSONObject().put("key", "pay_max").put("value", c.max.toString()))
            val r = Http.client.newCall(
                auth.req("/rest/v1/app_config?on_conflict=key", t).header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(rows.toString().toRequestBody(json)).build()
            ).await()
            if (r.ok) AuthResult.Ok("Đã lưu cấu hình") else AuthResult.Fail(if (r.code == 401 || r.code == 403) "Bạn không có quyền quản trị" else "Không thể lưu")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun balance(): Long? = try {
        val t = auth.validToken()
        val uid = auth.session.value?.userId.orEmpty()
        val r = Http.client.newCall(auth.req("/rest/v1/wallets?select=balance&user_id=eq.$uid", t).get().build()).await()
        if (!r.ok) null else JSONArray(r.body).optJSONObject(0)?.optLong("balance") ?: 0L
    } catch (e: Exception) { null }

    /** Tạo đơn nạp tiền. Trả (id, mã đơn) hoặc thông báo lỗi. */
    suspend fun createOrder(amount: Long): Pair<Pair<String, String>?, String?> = try {
        val t = auth.validToken() ?: return Pair(null, "Chưa đăng nhập")
        val b = JSONObject().put("p_amount", amount).toString().toRequestBody(json)
        val r = Http.client.newCall(auth.req("/rest/v1/rpc/create_order", t).post(b).build()).await()
        if (r.ok) {
            val o = JSONArray(r.body).optJSONObject(0)
            if (o != null) Pair(Pair(o.optString("o_id"), o.optString("o_code")), null) else Pair(null, "Không thể tạo đơn")
        } else Pair(null, when {
            r.body.contains("payment_disabled") -> "Thanh toán đang tắt"
            r.body.contains("amount_out_of_range") -> "Số tiền ngoài giới hạn cho phép"
            r.body.contains("rate_limited") -> "Bạn tạo đơn quá nhanh, thử lại sau"
            else -> "Không thể tạo đơn"
        })
    } catch (e: IOException) { Pair(null, "Lỗi mạng, kiểm tra kết nối") }

    suspend fun orderStatus(id: String): String? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/payment_orders?select=status&id=eq.$id", t).get().build()).await()
        if (r.ok) JSONArray(r.body).optJSONObject(0)?.optString("status") else null
    } catch (e: Exception) { null }

    suspend fun myOrders(): List<OrderRow> = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/payment_orders?select=id,code,amount,status,created_at&order=created_at.desc&limit=20", t).get().build()).await()
        if (!r.ok) emptyList() else JSONArray(r.body).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                OrderRow(it.optString("id"), it.optString("code"), it.optLong("amount"), it.optString("status"), ms(it.optString("created_at")))
            }
        }
    } catch (e: Exception) { emptyList() }

    suspend fun myLedger(): List<LedgerRow> = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/wallet_ledger?select=delta,reason,created_at,balance_after&order=created_at.desc&limit=30", t).get().build()).await()
        if (!r.ok) emptyList() else JSONArray(r.body).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                LedgerRow(it.optLong("delta"), it.optString("reason"), ms(it.optString("created_at")), it.optLong("balance_after"))
            }
        }
    } catch (e: Exception) { emptyList() }

    suspend fun adminOrders(): List<AdminOrder>? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/rpc/admin_list_orders", t).post("{}".toRequestBody(json)).build()).await()
        if (!r.ok) null else JSONArray(r.body).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminOrder(it.optString("username"), it.optString("email"), it.optString("code"), it.optLong("amount"),
                    it.optString("status"), it.optLong("paid_amount"), ms(it.optString("created_at")))
            }
        }
    } catch (e: Exception) { null }

    suspend fun adjustWallet(target: String, amount: Long, note: String) =
        rpc("admin_adjust_wallet", JSONObject().put("target", target).put("amount", amount).put("note", note))

    /** Ảnh QR chuyển khoản VietQR (số tiền + nội dung đã điền sẵn). */
    fun qrUrl(c: PayConfig, amount: Long, code: String): String {
        fun e(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        return "https://img.vietqr.io/image/${e(c.bank)}-${e(c.account)}-compact2.png?amount=$amount&addInfo=${e(code)}&accountName=${e(c.name)}"
    }

    suspend fun fetchBytes(url: String): ByteArray? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            Http.client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        }.getOrNull()
    }

    // ---------- Gói dịch vụ ----------
    suspend fun plans(): List<Plan>? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/plans?select=*&order=sort.asc,price.asc", t).get().build()).await()
        if (!r.ok) null else JSONArray(r.body).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                Plan(it.optString("id"), it.optString("tier"), it.optString("title"), it.optInt("days"), it.optLong("price"),
                    it.optInt("max_comments"), it.optInt("cover"), it.optString("description"), it.optString("perks"),
                    it.optInt("sort"), it.optBoolean("active", true))
            }
        }
    } catch (e: Exception) { null }

    suspend fun savePlan(p: Plan): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val o = JSONObject().put("id", p.id).put("tier", p.tier).put("title", p.title.trim()).put("days", p.days)
                .put("price", p.price).put("max_comments", p.maxComments).put("cover", p.cover)
                .put("description", p.description.trim()).put("perks", p.perks.trim()).put("sort", p.sort).put("active", p.active)
            val r = Http.client.newCall(
                auth.req("/rest/v1/plans?on_conflict=id", t).header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(o.toString().toRequestBody(json)).build()
            ).await()
            if (r.ok) AuthResult.Ok("Đã lưu") else AuthResult.Fail(if (r.code == 401 || r.code == 403) "Bạn không có quyền quản trị" else "Giá trị chưa hợp lệ")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun deletePlan(id: String): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val r = Http.client.newCall(auth.req("/rest/v1/plans?id=eq.$id", t).delete().build()).await()
            if (r.ok) AuthResult.Ok("Đã xóa") else AuthResult.Fail("Không thể xóa")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun entitlement(): Entitlement? = try {
        val t = auth.validToken() ?: return null
        val r = Http.client.newCall(auth.req("/rest/v1/rpc/get_entitlement", t).post("{}".toRequestBody(json)).build()).await()
        if (!r.ok) null else JSONObject(r.body).let { o ->
            Entitlement(o.optString("tier", "free"), ms(o.optString("expires_at")).takeIf { it > 0 }, o.optInt("daily_limit"),
                o.optInt("used_today"), o.optBoolean("blocked"), o.optInt("trial_days"), o.optString("plan_id"))
        }
    } catch (e: Exception) { null }

    /** Mua gói bằng số dư ví. Trả (ngày hết hạn mới, lỗi). */
    suspend fun purchase(planId: String): Pair<Long?, String?> = try {
        val t = auth.validToken() ?: return Pair(null, "Chưa đăng nhập")
        val r = Http.client.newCall(auth.req("/rest/v1/rpc/purchase_plan", t).post(JSONObject().put("p_plan", planId).toString().toRequestBody(json)).build()).await()
        if (r.ok) Pair(ms(r.body.trim().trim('"')), null)
        else Pair(null, when {
            r.body.contains("insufficient_balance") -> "Số dư không đủ"
            r.body.contains("plan_not_found") -> "Gói không còn bán"
            r.body.contains("plan_active") -> "Bạn đang dùng một gói khác. Hãy đợi gói hiện tại hết hạn rồi mua gói mới"
            else -> "Không thể mua gói"
        })
    } catch (e: IOException) { Pair(null, "Lỗi mạng, kiểm tra kết nối") }

    suspend fun adminGrant(target: String, planId: String) = rpc("admin_grant", JSONObject().put("target", target).put("p_plan", planId))

    suspend fun freeConfig(): Pair<Int, Int>? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/app_config?select=key,value&key=in.(free_trial_days,free_daily_comments)", t).get().build()).await()
        if (!r.ok) null else {
            val a = JSONArray(r.body)
            val m = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optString("key") to it.optString("value") }
            Pair(m["free_trial_days"]?.toIntOrNull() ?: 3, m["free_daily_comments"]?.toIntOrNull() ?: 200)
        }
    } catch (e: Exception) { null }

    suspend fun saveFreeConfig(days: Int, comments: Int): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val rows = JSONArray()
                .put(JSONObject().put("key", "free_trial_days").put("value", days.toString()))
                .put(JSONObject().put("key", "free_daily_comments").put("value", comments.toString()))
            val r = Http.client.newCall(
                auth.req("/rest/v1/app_config?on_conflict=key", t).header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(rows.toString().toRequestBody(json)).build()
            ).await()
            if (r.ok) AuthResult.Ok("Đã lưu") else AuthResult.Fail(if (r.code == 401 || r.code == 403) "Bạn không có quyền quản trị" else "Không thể lưu")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    // ---------- Thông báo của quản trị ----------
    private fun parseAnn(o: JSONObject) = Announcement(o.optString("id"), o.optString("title"), o.optString("body"), ms(o.optString("created_at")))

    suspend fun announcements(limit: Int = 20): List<Announcement>? = try {
        val t = auth.validToken()
        val r = Http.client.newCall(auth.req("/rest/v1/announcements?select=id,title,body,created_at&order=created_at.desc&limit=$limit", t).get().build()).await()
        if (!r.ok) null else JSONArray(r.body).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map(::parseAnn) }
    } catch (e: Exception) { null }

    suspend fun latestAnnouncement(): Announcement? = announcements(1)?.firstOrNull()

    suspend fun sendAnnouncement(title: String, body: String): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val o = JSONObject().put("title", title.trim().take(80)).put("body", body.trim().take(600))
            val r = Http.client.newCall(auth.req("/rest/v1/announcements", t).header("Prefer", "return=minimal").post(o.toString().toRequestBody(json)).build()).await()
            if (r.ok) AuthResult.Ok("Đã gửi thông báo") else AuthResult.Fail(if (r.code == 401 || r.code == 403) "Bạn không có quyền quản trị" else "Không thể gửi")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    suspend fun deleteAnnouncement(id: String): AuthResult {
        val t = auth.validToken() ?: return AuthResult.Fail("Chưa đăng nhập")
        return try {
            val r = Http.client.newCall(auth.req("/rest/v1/announcements?id=eq.$id", t).delete().build()).await()
            if (r.ok) AuthResult.Ok("Đã xóa") else AuthResult.Fail("Không thể xóa")
        } catch (e: IOException) { AuthResult.Fail("Lỗi mạng, kiểm tra kết nối") }
    }

    companion object { const val MAX_BYTES = 2L * 1024 * 1024 }
}
