// Edge Function nhận thông báo biến động số dư từ SePay và cộng tiền vào ví.
// Cài đặt: Supabase > Edge Functions > tên "payment-webhook" > TẮT "Verify JWT" > Secret SEPAY_API_KEY.
import { createClient } from "npm:@supabase/supabase-js@2";

const json = (o: unknown, status = 200) =>
  new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ success: false }, 405);

  // SePay gửi header: Authorization: Apikey <khóa bạn đặt khi tạo webhook>
  const expected = Deno.env.get("SEPAY_API_KEY");
  if (!expected || req.headers.get("authorization") !== `Apikey ${expected}`) {
    return json({ success: false, message: "unauthorized" }, 401);
  }

  let p: any;
  try { p = await req.json(); } catch { return json({ success: false }, 400); }

  // Chỉ xử lý tiền VÀO tài khoản.
  if (p.transferType !== "in") return json({ success: true, ignored: "not incoming" });

  // Tìm mã đơn VLxxxxxxxx trong nội dung chuyển khoản (ngân hàng có thể chèn dấu cách/gạch nối).
  const text = `${p.code ?? ""} ${p.content ?? ""}`.toUpperCase().replace(/[^A-Z0-9]/g, "");
  const m = text.match(/VL[A-HJ-NP-Z2-9]{8}/);
  if (!m) return json({ success: true, ignored: "no order code" });

  const supa = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );
  const { data, error } = await supa.rpc("confirm_payment", {
    p_code: m[0],
    p_amount: Number(p.transferAmount),
    p_txn: String(p.id),
    p_ref: String(p.referenceCode ?? ""),
  });
  if (error) {
    console.error("confirm_payment failed:", error.message);
    return json({ success: false }, 500);
  }
  return json({ success: true, result: data });
});
