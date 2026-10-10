// Cầu nối TikTok LIVE -> WebSocket cho app Android.
// - Chạy sau reverse proxy HTTPS (Caddy/nginx/Cloudflare Tunnel) để app dùng wss://
// - Chỉ cho client có access token Supabase hợp lệ (Authorization: Bearer ... hoặc ?token=...)
// - Không đòi/lưu mật khẩu TikTok. tiktok-live-connector là thư viện KHÔNG chính thức,
//   có thể hỏng khi TikTok đổi giao thức: khi đó cầu nối gửi {"type":"status","state":"error"}.
const { WebSocketServer } = require('ws');
const { WebcastPushConnection } = require('tiktok-live-connector');
const { createClient } = require('@supabase/supabase-js');

const PORT = process.env.PORT || 8080;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;
const MAX_CONN_PER_USER = 2;

if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
  console.error('Thiếu SUPABASE_URL / SUPABASE_ANON_KEY');
  process.exit(1);
}
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, { auth: { persistSession: false } });
const perUser = new Map();

const wss = new WebSocketServer({ port: PORT, maxPayload: 1024 });
const send = (ws, o) => ws.readyState === 1 && ws.send(JSON.stringify(o));

wss.on('connection', async (ws, req) => {
  const url = new URL(req.url, 'http://x');
  const channel = (url.searchParams.get('channel') || '').replace(/^@/, '');
  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : url.searchParams.get('token');

  if (!/^[A-Za-z0-9_.]{2,24}$/.test(channel)) {
    send(ws, { type: 'status', state: 'error', fatal: true, message: 'Tên tài khoản không hợp lệ' });
    return ws.close();
  }
  const { data, error } = token ? await supabase.auth.getUser(token) : { error: true };
  if (error || !data?.user?.email_confirmed_at) {
    send(ws, { type: 'status', state: 'error', fatal: true, message: 'Chưa xác thực hoặc email chưa xác minh' });
    return ws.close();
  }
  const uid = data.user.id;
  if ((perUser.get(uid) || 0) >= MAX_CONN_PER_USER) {
    send(ws, { type: 'status', state: 'error', fatal: true, message: 'Quá số kết nối cho phép' });
    return ws.close();
  }
  perUser.set(uid, (perUser.get(uid) || 0) + 1);

  const live = new WebcastPushConnection(channel);
  let closed = false;
  const cleanup = () => {
    if (closed) return;
    closed = true;
    perUser.set(uid, Math.max(0, (perUser.get(uid) || 1) - 1));
    try { live.disconnect(); } catch (_) {}
  };
  ws.on('close', cleanup);
  ws.on('error', cleanup);

  live.on('chat', (d) => send(ws, { type: 'comment', user: d.uniqueId || d.nickname || '', text: d.comment || '' }));
  live.on('streamEnd', () => { send(ws, { type: 'status', state: 'ended' }); ws.close(); });
  live.on('disconnected', () => { send(ws, { type: 'status', state: 'error', fatal: false, message: 'Mất kết nối TikTok' }); ws.close(); });

  try {
    await live.connect();
    send(ws, { type: 'status', state: 'connected' });
  } catch (e) {
    const msg = String(e && e.message || e);
    const notLive = /offline|not live|LIVE has ended|user_not_found/i.test(msg);
    send(ws, { type: 'status', state: 'error', fatal: notLive,
      message: notLive ? 'Livestream không tồn tại hoặc chưa phát' : 'Không thể truy cập livestream' });
    ws.close();
  }
});

console.log(`Bridge listening on :${PORT}`);
