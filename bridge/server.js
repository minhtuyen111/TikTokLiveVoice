// Máy chủ kết nối TikTok LIVE -> WebSocket cho app Voice Live TikTok.
// - Chạy sau HTTPS (Caddy/nginx/Cloudflare Tunnel) để app dùng wss://
// - Chỉ nhận người dùng có access token Supabase hợp lệ và đã xác minh email
// - Mỗi kênh chỉ mở MỘT kết nối tới TikTok, chia sẻ cho mọi người dùng cùng xem (nhẹ máy chủ, ít bị chặn)
// - Không đòi/lưu mật khẩu TikTok. tiktok-live-connector là thư viện KHÔNG chính thức.
// Giao thức gửi xuống app:
//   {"type":"comment","user":"..","text":".."}
//   {"type":"event","kind":"follow|share|gift","user":"..","name":"..","count":1}
//   {"type":"status","state":"connected|ended|error","fatal":bool,"message":".."}
const { WebSocketServer } = require('ws');
const { TikTokLiveConnection } = require('tiktok-live-connector');
const { createClient } = require('@supabase/supabase-js');

const PORT = process.env.PORT || 8080;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;
const MAX_CONN_PER_USER = Number(process.env.MAX_CONN_PER_USER || 2);
const MAX_HUBS = Number(process.env.MAX_HUBS || 200);
const IDLE_MS = 15000;
const EULER_KEY = process.env.EULER_API_KEY || '';

if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
  console.error('Thiếu SUPABASE_URL / SUPABASE_ANON_KEY');
  process.exit(1);
}
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, { auth: { persistSession: false } });
const perUser = new Map();
const hubs = new Map();

const wss = new WebSocketServer({ port: PORT, maxPayload: 1024 });
const send = (ws, o) => ws.readyState === 1 && ws.send(JSON.stringify(o));
const who = (d) => String((d.user && (d.user.uniqueId || d.user.nickname)) || d.uniqueId || d.nickname || '').slice(0, 64);

// Trạng thái từng kết nối: giới hạn bình luận/ngày và số đã gửi chưa ghi vào CSDL.
const info = new Map();
const LIMIT_MSG = 'Đã đạt giới hạn bình luận hôm nay của gói. Hãy mua gói để đọc nhiều hơn';

async function flush(ws) {
  const i = info.get(ws);
  if (!i || i.pending <= 0) return;
  const n = i.pending; i.pending = 0;
  try { await i.db.rpc('add_usage', { p_n: n }); } catch (_) {}
}

function broadcastComment(h, o) {
  const m = JSON.stringify(o);
  for (const c of h.clients) {
    if (c.readyState !== 1) continue;
    const i = info.get(c);
    if (i && i.limit > 0 && i.used >= i.limit) {
      send(c, { type: 'status', state: 'error', fatal: true, message: LIMIT_MSG });
      flush(c).finally(() => { try { c.close(); } catch (_) {} });
      continue;
    }
    c.send(m);
    if (i) { i.used++; i.pending++; if (i.pending >= 20) flush(c); }
  }
}

function broadcast(h, o) {
  const m = JSON.stringify(o);
  for (const c of h.clients) if (c.readyState === 1) c.send(m);
}

function closeHub(h) {
  if (hubs.get(h.channel) === h) hubs.delete(h.channel);
  clearTimeout(h.timer);
  try { h.live.disconnect(); } catch (_) {}
  for (const c of h.clients) { try { c.close(); } catch (_) {} }
  h.clients.clear();
}

async function startHub(h) {
  const live = h.live;
  live.on('chat', (d) => broadcastComment(h, { type: 'comment', user: who(d), text: String(d.comment || d.content || d.text || '').slice(0, 1000) }));
  live.on('follow', (d) => broadcast(h, { type: 'event', kind: 'follow', user: who(d) }));
  live.on('share', (d) => broadcast(h, { type: 'event', kind: 'share', user: who(d) }));
  live.on('gift', (d) => {
    const gt = d.giftDetails ? d.giftDetails.giftType : d.giftType;
    if (gt === 1 && !d.repeatEnd) return; // quà liên tiếp: chỉ báo khi kết thúc chuỗi
    const gn = (d.giftDetails && d.giftDetails.giftName) || d.giftName || '';
    broadcast(h, { type: 'event', kind: 'gift', user: who(d), name: String(gn).slice(0, 40), count: d.repeatCount || 1 });
  });
  live.on('streamEnd', () => { broadcast(h, { type: 'status', state: 'ended' }); closeHub(h); });
  live.on('disconnected', () => {
    broadcast(h, { type: 'status', state: 'error', fatal: false, message: 'Mất kết nối TikTok' });
    closeHub(h);
  });
  try {
    await live.connect();
    h.state = 'connected';
    broadcast(h, { type: 'status', state: 'connected' });
  } catch (e) {
    const msg = String((e && e.message) || e);
    console.log('connect fail', h.channel, msg.slice(0, 300));
    const notLive = /offline|not live|isn't online|has ended|user_not_found|UserOffline/i.test(msg);
    broadcast(h, { type: 'status', state: 'error', fatal: notLive,
      message: notLive ? 'Livestream không tồn tại hoặc chưa phát' : 'Không thể truy cập livestream' });
    closeHub(h);
  }
}

function joinHub(channel, ws) {
  let h = hubs.get(channel);
  if (!h) {
    if (hubs.size >= MAX_HUBS) return null;
    h = { channel, clients: new Set(), live: new TikTokLiveConnection(channel, EULER_KEY ? { signApiKey: EULER_KEY } : {}), state: 'connecting', timer: null };
    hubs.set(channel, h);
    h.clients.add(ws);
    startHub(h);
    return h;
  }
  clearTimeout(h.timer);
  h.clients.add(ws);
  if (h.state === 'connected') send(ws, { type: 'status', state: 'connected' });
  return h;
}

wss.on('connection', async (ws, req) => {
  const url = new URL(req.url, 'http://x');
  const channel = (url.searchParams.get('channel') || '').replace(/^@/, '').toLowerCase();
  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  const fail = (message) => { send(ws, { type: 'status', state: 'error', fatal: true, message }); ws.close(); };

  if (!/^[a-z0-9_.]{2,24}$/.test(channel)) return fail('Tên tài khoản không hợp lệ');
  const { data, error } = token ? await supabase.auth.getUser(token) : { error: true };
  if (error || !data?.user?.email_confirmed_at) return fail('Chưa xác thực hoặc email chưa xác minh');
  const uid = data.user.id;
  const asUser = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: { persistSession: false }, global: { headers: { Authorization: `Bearer ${token}` } } });
  const prof = await asUser.from('profiles').select('status').eq('id', uid).maybeSingle();
  if (prof.data && prof.data.status !== 'active') return fail('Tài khoản đã bị khóa');
  if ((perUser.get(uid) || 0) >= MAX_CONN_PER_USER) return fail('Quá số kết nối cho phép');
  perUser.set(uid, (perUser.get(uid) || 0) + 1);

  // Kiểm tra gói: hết dùng thử thì từ chối; còn thì áp giới hạn bình luận/ngày ngay tại máy chủ.
  const ent = await asUser.rpc('get_entitlement');
  if (ent.error || !ent.data) return fail('Không kiểm tra được gói dịch vụ');
  if (ent.data.blocked) return fail('Đã hết thời gian dùng thử. Hãy mua gói để tiếp tục');
  info.set(ws, { db: asUser, limit: Number(ent.data.daily_limit) || 0, used: Number(ent.data.used_today) || 0, pending: 0 });

  const hub = joinHub(channel, ws);
  let left = false;
  const leave = () => {
    if (left) return;
    left = true;
    flush(ws).finally(() => info.delete(ws));
    perUser.set(uid, Math.max(0, (perUser.get(uid) || 1) - 1));
    if (!hub) return;
    hub.clients.delete(ws);
    if (hub.clients.size === 0) hub.timer = setTimeout(() => closeHub(hub), IDLE_MS);
  };
  ws.on('close', leave);
  ws.on('error', leave);
  if (!hub) { fail('Máy chủ đang quá tải, thử lại sau'); }
});

console.log(`Bridge listening on :${PORT}`);
