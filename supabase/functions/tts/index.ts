// Edge Function "tts": đọc văn bản bằng giọng thần kinh Azure (nam/nữ, 5 ngôn ngữ), trả về MP3.
// Cài đặt: Supabase > Edge Functions > "tts" > GIỮ BẬT "Verify JWT" > Secrets: AZURE_SPEECH_KEY, AZURE_SPEECH_REGION (vd: southeastasia).
import { createClient } from "npm:@supabase/supabase-js@2";

const VOICES: Record<string, { female: string; male: string }> = {
  "vi-VN": { female: "vi-VN-HoaiMyNeural", male: "vi-VN-NamMinhNeural" },
  "en-US": { female: "en-US-JennyNeural", male: "en-US-GuyNeural" },
  "zh-CN": { female: "zh-CN-XiaoxiaoNeural", male: "zh-CN-YunxiNeural" },
  "ja-JP": { female: "ja-JP-NanamiNeural", male: "ja-JP-KeitaNeural" },
  "ko-KR": { female: "ko-KR-SunHiNeural", male: "ko-KR-InJoonNeural" },
};

const err = (status: number, message: string) =>
  new Response(JSON.stringify({ error: message }), { status, headers: { "content-type": "application/json" } });

const esc = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");

const pct = (n: number) => `${n >= 0 ? "+" : ""}${n}%`;
const clamp = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n));

// Giới hạn mềm: tối đa 60 yêu cầu/phút mỗi tài khoản (bộ nhớ của instance, đủ chặn lạm dụng đơn giản).
const hits = new Map<string, number[]>();
function limited(uid: string) {
  const now = Date.now();
  const arr = (hits.get(uid) ?? []).filter((t) => now - t < 60_000);
  arr.push(now);
  hits.set(uid, arr);
  return arr.length > 60;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return err(405, "method");

  // Chỉ tài khoản đã đăng nhập (token người dùng, không phải khóa anon).
  const supa = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false },
  });
  const { data, error } = await supa.auth.getUser();
  if (error || !data.user) return err(401, "unauthorized");
  if (limited(data.user.id)) return err(429, "rate");

  const key = Deno.env.get("AZURE_SPEECH_KEY");
  const region = Deno.env.get("AZURE_SPEECH_REGION");
  if (!key || !region) return err(500, "not configured");

  let b: any;
  try { b = await req.json(); } catch { return err(400, "json"); }
  const text = String(b.text ?? "").trim().slice(0, 400);
  if (!text) return err(400, "empty");
  const lang = VOICES[b.lang] ? b.lang : "vi-VN";
  const voice = b.gender === "male" ? VOICES[lang].male : VOICES[lang].female;
  const rate = clamp(Math.round((Number(b.rate) || 1) * 100 - 100), -50, 100);
  const pitch = clamp(Math.round((Number(b.pitch) || 1) * 100 - 100), -50, 50);

  const ssml =
    `<speak version='1.0' xml:lang='${lang}' xmlns='http://www.w3.org/2001/10/synthesis'>` +
    `<voice name='${voice}'><prosody rate='${pct(rate)}' pitch='${pct(pitch)}'>${esc(text)}</prosody></voice></speak>`;

  const r = await fetch(`https://${region}.tts.speech.microsoft.com/cognitiveservices/v1`, {
    method: "POST",
    headers: {
      "Ocp-Apim-Subscription-Key": key,
      "Content-Type": "application/ssml+xml",
      "X-Microsoft-OutputFormat": "audio-24khz-48kbitrate-mono-mp3",
      "User-Agent": "voicelive",
    },
    body: ssml,
  });
  if (!r.ok) {
    console.error("azure tts failed:", r.status, await r.text().catch(() => ""));
    return err(502, "upstream");
  }
  return new Response(await r.arrayBuffer(), { headers: { "content-type": "audio/mpeg" } });
});
