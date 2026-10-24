-- Chạy trong Supabase SQL Editor.
-- Bật "Confirm email" ở Authentication > Providers > Email.
-- Thêm tlv://auth vào Authentication > URL Configuration > Redirect URLs
-- (để liên kết khôi phục mật khẩu mở lại ứng dụng).

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null,
  status text not null default 'active' check (status in ('active','locked')),
  created_at timestamptz not null default now()
);
create unique index if not exists profiles_username_ci on public.profiles (lower(username));

alter table public.profiles enable row level security;

-- Người dùng chỉ đọc hồ sơ của chính mình; không được tự sửa status/quyền.
drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);

-- Tạo hồ sơ tự động khi đăng ký. Vi phạm unique => đăng ký thất bại (chống trùng tên ở server).
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, username)
  values (new.id, coalesce(new.raw_user_meta_data->>'username', 'user_' || substr(new.id::text, 1, 8)));
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Kiểm tra tên còn trống (chỉ trả true/false).
create or replace function public.username_available(p_username text) returns boolean
language sql security definer set search_path = public stable as $$
  select not exists (select 1 from public.profiles where lower(username) = lower(p_username));
$$;

-- Đăng nhập bằng tên: trả email tương ứng.
-- LƯU Ý quyền riêng tư: hàm này cho phép dò email theo tên đăng nhập.
-- Nếu không cần đăng nhập bằng tên, hãy xóa hàm này; app vẫn đăng nhập được bằng email.
create or replace function public.login_email_for(p_username text) returns text
language sql security definer set search_path = public, auth stable as $$
  select u.email from public.profiles p join auth.users u on u.id = p.id
  where lower(p.username) = lower(p_username) and p.status = 'active' limit 1;
$$;

revoke all on function public.username_available(text) from public;
revoke all on function public.login_email_for(text) from public;
grant execute on function public.username_available(text) to anon, authenticated;
grant execute on function public.login_email_for(text) to anon, authenticated;

-- Yêu cầu xóa tài khoản (xử lý bởi quản trị ở backend).
create table if not exists public.deletion_requests (
  user_id uuid primary key references auth.users(id) on delete cascade,
  requested_at timestamptz not null default now()
);
alter table public.deletion_requests enable row level security;
drop policy if exists "del_insert_own" on public.deletion_requests;
create policy "del_insert_own" on public.deletion_requests
  for insert with check (auth.uid() = user_id);
drop policy if exists "del_select_own" on public.deletion_requests;
create policy "del_select_own" on public.deletion_requests
  for select using (auth.uid() = user_id);

-- Quản trị: làm trong Supabase Dashboard hoặc backend riêng dùng service_role
-- (KHÔNG đặt service_role key trong APK). Khóa tài khoản: update profiles set status='locked' ...

-- ============================================================
-- v2: vai trò quản trị, cấu hình máy chủ kết nối, âm thanh meme
-- ============================================================
alter table public.profiles add column if not exists role text not null default 'user'
  check (role in ('user','admin'));

-- Người dùng KHÔNG có policy update/insert trên profiles => không tự nâng quyền được.
create or replace function public.is_admin() returns boolean
language sql security definer set search_path = public stable as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'admin' and status = 'active');
$$;
revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

-- Cấu hình dùng chung (địa chỉ máy chủ kết nối). Mọi người dùng đã đăng nhập đọc được; chỉ admin ghi.
create table if not exists public.app_config (
  key text primary key,
  value text not null,
  updated_at timestamptz not null default now()
);
alter table public.app_config enable row level security;
drop policy if exists "cfg_read" on public.app_config;
create policy "cfg_read" on public.app_config for select to authenticated using (true);
drop policy if exists "cfg_admin_ins" on public.app_config;
create policy "cfg_admin_ins" on public.app_config for insert to authenticated with check (public.is_admin());
drop policy if exists "cfg_admin_upd" on public.app_config;
create policy "cfg_admin_upd" on public.app_config for update to authenticated
  using (public.is_admin()) with check (public.is_admin());
insert into public.app_config (key, value) values ('bridge_url', '') on conflict (key) do nothing;

-- Âm thanh meme cho sự kiện theo dõi / chia sẻ / tặng quà. Chỉ admin thêm, sửa, xóa.
create table if not exists public.meme_sounds (
  id uuid primary key default gen_random_uuid(),
  event text not null check (event in ('follow','share','gift')),
  title text not null check (char_length(title) between 1 and 60),
  path text not null,
  created_by uuid default auth.uid() references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);
-- Cho phép thêm loại 'idle' (âm thanh đợi, phát khi hết hàng chờ). Chạy lại an toàn.
alter table public.meme_sounds drop constraint if exists meme_sounds_event_check;
alter table public.meme_sounds add constraint meme_sounds_event_check check (event in ('follow','share','gift','idle'));
alter table public.meme_sounds enable row level security;
drop policy if exists "meme_read" on public.meme_sounds;
create policy "meme_read" on public.meme_sounds for select to authenticated using (true);
drop policy if exists "meme_admin_ins" on public.meme_sounds;
create policy "meme_admin_ins" on public.meme_sounds for insert to authenticated with check (public.is_admin());
drop policy if exists "meme_admin_upd" on public.meme_sounds;
create policy "meme_admin_upd" on public.meme_sounds for update to authenticated
  using (public.is_admin()) with check (public.is_admin());
drop policy if exists "meme_admin_del" on public.meme_sounds;
create policy "meme_admin_del" on public.meme_sounds for delete to authenticated using (public.is_admin());

-- Kho lưu file âm thanh: đọc công khai, ghi/xóa chỉ admin, tối đa 2 MB/file.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('memes', 'memes', true, 2097152,
  array['audio/mpeg','audio/mp3','audio/mp4','audio/aac','audio/ogg','audio/wav','audio/x-wav','audio/webm','audio/3gpp'])
on conflict (id) do update set public = true, file_size_limit = 2097152,
  allowed_mime_types = excluded.allowed_mime_types;
drop policy if exists "memes_admin_ins" on storage.objects;
create policy "memes_admin_ins" on storage.objects for insert to authenticated
  with check (bucket_id = 'memes' and public.is_admin());
drop policy if exists "memes_admin_upd" on storage.objects;
create policy "memes_admin_upd" on storage.objects for update to authenticated
  using (bucket_id = 'memes' and public.is_admin());
drop policy if exists "memes_admin_del" on storage.objects;
create policy "memes_admin_del" on storage.objects for delete to authenticated
  using (bucket_id = 'memes' and public.is_admin());

-- Cấp quyền admin: đăng ký tài khoản trước, xác minh email, rồi chạy dòng dưới trong SQL Editor.
-- (Không tự cấp qua trigger để người đăng ký trước không chiếm được quyền.)
update public.profiles set role = 'admin' where lower(username) = 'leminhtuyen';

-- ============================================================
-- v3: theo dõi người dùng đang dùng app, quản lý tài khoản, cập nhật bản mới
-- ============================================================
alter table public.profiles add column if not exists last_seen timestamptz;
alter table public.profiles add column if not exists watching text;

-- App gọi mỗi ~60 giây khi đang dùng: ghi lần hoạt động cuối và kênh đang theo dõi.
create or replace function public.touch_presence(p_watching text default null) returns void
language sql security definer set search_path = public as $$
  update public.profiles
     set last_seen = now(), watching = nullif(left(coalesce(p_watching, ''), 32), '')
   where id = auth.uid();
$$;
revoke all on function public.touch_presence(text) from public;
grant execute on function public.touch_presence(text) to authenticated;

-- Chỉ admin: danh sách người dùng kèm email, vai trò, trạng thái, hoạt động cuối.
drop function if exists public.admin_list_users();
create or replace function public.admin_list_users()
returns table (id uuid, username text, email text, role text, status text,
               created_at timestamptz, last_seen timestamptz, watching text, deletion_requested boolean)
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
    select p.id, p.username, u.email::text, p.role, p.status, p.created_at, p.last_seen, p.watching,
           exists (select 1 from public.deletion_requests d where d.user_id = p.id)
      from public.profiles p join auth.users u on u.id = p.id
     order by p.last_seen desc nulls last, p.created_at desc;
end $$;
revoke all on function public.admin_list_users() from public;
grant execute on function public.admin_list_users() to authenticated;

create or replace function public.admin_set_role(target uuid, new_role text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if new_role not in ('user', 'admin') then raise exception 'bad role'; end if;
  if target = auth.uid() then raise exception 'cannot change self'; end if;
  update public.profiles set role = new_role where id = target;
end $$;
revoke all on function public.admin_set_role(uuid, text) from public;
grant execute on function public.admin_set_role(uuid, text) to authenticated;

create or replace function public.admin_set_status(target uuid, new_status text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if new_status not in ('active', 'locked') then raise exception 'bad status'; end if;
  if target = auth.uid() then raise exception 'cannot change self'; end if;
  update public.profiles set status = new_status where id = target;
end $$;
revoke all on function public.admin_set_status(uuid, text) from public;
grant execute on function public.admin_set_status(uuid, text) to authenticated;

-- Thông tin phiên bản mới: ai cũng đọc được (kể cả chưa đăng nhập) để báo cập nhật.
drop policy if exists "cfg_public_update" on public.app_config;
create policy "cfg_public_update" on public.app_config for select to anon
  using (key in ('latest_code', 'latest_name', 'update_url', 'update_notes', 'min_code'));

-- Kho chứa file APK phát hành (admin tải lên trong app, tối đa 50 MB).
insert into storage.buckets (id, name, public, file_size_limit)
values ('apk', 'apk', true, 52428800)
on conflict (id) do update set public = true, file_size_limit = 52428800, allowed_mime_types = null;
drop policy if exists "apk_admin_ins" on storage.objects;
create policy "apk_admin_ins" on storage.objects for insert to authenticated
  with check (bucket_id = 'apk' and public.is_admin());
drop policy if exists "apk_admin_upd" on storage.objects;
create policy "apk_admin_upd" on storage.objects for update to authenticated
  using (bucket_id = 'apk' and public.is_admin());
drop policy if exists "apk_admin_del" on storage.objects;
create policy "apk_admin_del" on storage.objects for delete to authenticated
  using (bucket_id = 'apk' and public.is_admin());

-- ============================================================
-- v4: ví tiền & nạp tiền bằng chuyển khoản (QR). Cộng tiền CHỈ ở phía máy chủ.
-- ============================================================
create table if not exists public.wallets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  balance bigint not null default 0 check (balance >= 0),
  updated_at timestamptz not null default now()
);
create table if not exists public.payment_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  code text not null unique,
  amount bigint not null check (amount between 1000 and 100000000),
  status text not null default 'pending' check (status in ('pending','paid','expired','underpaid')),
  bank_txn_id text unique,
  paid_amount bigint,
  created_at timestamptz not null default now(),
  paid_at timestamptz
);
create table if not exists public.wallet_ledger (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  delta bigint not null,
  reason text not null,
  ref text,
  balance_after bigint,
  created_at timestamptz not null default now()
);
create index if not exists payment_orders_user_idx on public.payment_orders (user_id, created_at desc);
create index if not exists wallet_ledger_user_idx on public.wallet_ledger (user_id, created_at desc);

-- Người dùng chỉ ĐỌC dữ liệu của mình. Không có policy insert/update/delete => app (hay Lucky Patcher) không tự cộng tiền được.
alter table public.wallets enable row level security;
alter table public.payment_orders enable row level security;
alter table public.wallet_ledger enable row level security;
drop policy if exists "wallet_own" on public.wallets;
create policy "wallet_own" on public.wallets for select to authenticated using (user_id = auth.uid() or public.is_admin());
drop policy if exists "orders_own" on public.payment_orders;
create policy "orders_own" on public.payment_orders for select to authenticated using (user_id = auth.uid() or public.is_admin());
drop policy if exists "ledger_own" on public.wallet_ledger;
create policy "ledger_own" on public.wallet_ledger for select to authenticated using (user_id = auth.uid() or public.is_admin());

-- Cấu hình cổng thanh toán (admin sửa trong app). KHÔNG đặt bí mật vào bảng này.
insert into public.app_config (key, value) values
  ('pay_enabled', '0'), ('pay_bank', ''), ('pay_account', ''), ('pay_name', ''),
  ('pay_min', '10000'), ('pay_max', '5000000')
on conflict (key) do nothing;

-- Tạo đơn nạp: mã đơn ngẫu nhiên dạng VLXXXXXXXX (bỏ ký tự dễ nhầm), giới hạn số tiền và tốc độ.
create or replace function public.create_order(p_amount bigint)
returns table (o_id uuid, o_code text)
language plpgsql security definer set search_path = public as $$
declare
  en text; mn bigint; mx bigint; c text; i int;
  alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
  if auth.uid() is null then raise exception 'unauthenticated'; end if;
  select value into en from public.app_config where key = 'pay_enabled';
  if coalesce(en, '0') <> '1' then raise exception 'payment_disabled'; end if;
  mn := coalesce((select value::bigint from public.app_config where key = 'pay_min'), 10000);
  mx := coalesce((select value::bigint from public.app_config where key = 'pay_max'), 5000000);
  if p_amount < mn or p_amount > mx then raise exception 'amount_out_of_range'; end if;
  if (select count(*) from public.payment_orders x where x.user_id = auth.uid() and x.created_at > now() - interval '1 hour') >= 10
    then raise exception 'rate_limited'; end if;
  loop
    c := 'VL';
    for i in 1..8 loop c := c || substr(alphabet, 1 + floor(random() * 32)::int, 1); end loop;
    begin
      insert into public.payment_orders (user_id, code, amount) values (auth.uid(), c, p_amount) returning id into o_id;
      o_code := c;
      return next;
      return;
    exception when unique_violation then null;
    end;
  end loop;
end $$;
revoke all on function public.create_order(bigint) from public, anon;
grant execute on function public.create_order(bigint) to authenticated;

-- Xác nhận đã nhận tiền: CHỈ service_role (Edge Function nhận webhook từ ngân hàng/SePay) gọi được.
create or replace function public.confirm_payment(p_code text, p_amount bigint, p_txn text, p_ref text default null)
returns text
language plpgsql security definer set search_path = public as $$
declare o public.payment_orders; nb bigint;
begin
  if exists (select 1 from public.payment_orders where bank_txn_id = p_txn) then return 'duplicate'; end if;
  select * into o from public.payment_orders where code = p_code for update;
  if not found then return 'unknown_code'; end if;
  if o.status not in ('pending', 'expired') then return o.status; end if;
  if p_amount < o.amount then
    update public.payment_orders set status = 'underpaid', bank_txn_id = p_txn, paid_amount = p_amount, paid_at = now() where id = o.id;
    return 'underpaid';
  end if;
  update public.payment_orders set status = 'paid', bank_txn_id = p_txn, paid_amount = p_amount, paid_at = now() where id = o.id;
  insert into public.wallets (user_id, balance) values (o.user_id, p_amount)
    on conflict (user_id) do update set balance = public.wallets.balance + excluded.balance, updated_at = now()
    returning balance into nb;
  insert into public.wallet_ledger (user_id, delta, reason, ref, balance_after) values (o.user_id, p_amount, 'topup', p_txn, nb);
  return 'ok';
end $$;
revoke all on function public.confirm_payment(text, bigint, text, text) from public, anon, authenticated;
grant execute on function public.confirm_payment(text, bigint, text, text) to service_role;

-- Admin: danh sách người dùng (thêm số dư), giao dịch gần đây, cộng/trừ tiền thủ công.
drop function if exists public.admin_list_users();
create or replace function public.admin_list_users()
returns table (id uuid, username text, email text, role text, status text, created_at timestamptz,
               last_seen timestamptz, watching text, deletion_requested boolean, balance bigint)
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
    select p.id, p.username, u.email::text, p.role, p.status, p.created_at, p.last_seen, p.watching,
           exists (select 1 from public.deletion_requests d where d.user_id = p.id),
           coalesce((select w.balance from public.wallets w where w.user_id = p.id), 0)
      from public.profiles p join auth.users u on u.id = p.id
     order by p.last_seen desc nulls last, p.created_at desc;
end $$;
revoke all on function public.admin_list_users() from public;
grant execute on function public.admin_list_users() to authenticated;

create or replace function public.admin_list_orders()
returns table (id uuid, username text, email text, code text, amount bigint, status text,
               paid_amount bigint, created_at timestamptz, paid_at timestamptz)
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
    select o.id, p.username, u.email::text, o.code, o.amount, o.status, o.paid_amount, o.created_at, o.paid_at
      from public.payment_orders o
      join public.profiles p on p.id = o.user_id
      join auth.users u on u.id = o.user_id
     order by o.created_at desc limit 100;
end $$;
revoke all on function public.admin_list_orders() from public;
grant execute on function public.admin_list_orders() to authenticated;

create or replace function public.admin_adjust_wallet(target uuid, amount bigint, note text default '')
returns bigint
language plpgsql security definer set search_path = public as $$
declare nb bigint;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if amount = 0 or abs(amount) > 100000000 then raise exception 'bad amount'; end if;
  insert into public.wallets (user_id) values (target) on conflict (user_id) do nothing;
  update public.wallets set balance = balance + amount, updated_at = now() where user_id = target returning balance into nb;
  insert into public.wallet_ledger (user_id, delta, reason, ref, balance_after)
    values (target, amount, 'admin', left(coalesce(note, ''), 80), nb);
  return nb;
end $$;
revoke all on function public.admin_adjust_wallet(uuid, bigint, text) from public;
grant execute on function public.admin_adjust_wallet(uuid, bigint, text) to authenticated;

-- ============================================================
-- v5: gói dịch vụ (Pro / Pro Max), dùng thử miễn phí, giới hạn bình luận mỗi ngày
-- ============================================================
create table if not exists public.plans (
  id text primary key check (id ~ '^[a-z0-9_]{3,30}$'),
  tier text not null check (tier in ('pro', 'promax')),
  title text not null check (char_length(title) between 1 and 60),
  days int not null check (days between 1 and 3660),
  price bigint not null check (price between 1000 and 100000000),
  max_comments int not null default 0 check (max_comments >= 0),
  cover int not null default 0,
  description text not null default '',
  perks text not null default '',
  sort int not null default 0,
  active boolean not null default true
);
create table if not exists public.subscriptions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  tier text not null check (tier in ('pro', 'promax')),
  plan_id text,
  expires_at timestamptz not null
);
create table if not exists public.daily_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  comments int not null default 0,
  primary key (user_id, day)
);

alter table public.plans enable row level security;
alter table public.subscriptions enable row level security;
alter table public.daily_usage enable row level security;
drop policy if exists "plans_read" on public.plans;
create policy "plans_read" on public.plans for select to authenticated using (active or public.is_admin());
drop policy if exists "plans_admin_ins" on public.plans;
create policy "plans_admin_ins" on public.plans for insert to authenticated with check (public.is_admin());
drop policy if exists "plans_admin_upd" on public.plans;
create policy "plans_admin_upd" on public.plans for update to authenticated using (public.is_admin()) with check (public.is_admin());
drop policy if exists "plans_admin_del" on public.plans;
create policy "plans_admin_del" on public.plans for delete to authenticated using (public.is_admin());
drop policy if exists "subs_own" on public.subscriptions;
create policy "subs_own" on public.subscriptions for select to authenticated using (user_id = auth.uid() or public.is_admin());
drop policy if exists "usage_own" on public.daily_usage;
create policy "usage_own" on public.daily_usage for select to authenticated using (user_id = auth.uid() or public.is_admin());

-- Giới hạn bản Free (admin sửa trong app).
insert into public.app_config (key, value) values ('free_trial_days', '3'), ('free_daily_comments', '200')
on conflict (key) do nothing;

-- Gói mẫu (sửa hoặc xóa trong app).
insert into public.plans (id, tier, title, days, price, max_comments, cover, description, perks, sort) values
  ('pro_week', 'pro', 'Pro 1 tuần', 7, 20000, 3000, 0,
   'Dùng đầy đủ giọng đọc, bộ lọc và âm thanh meme trong 7 ngày.', E'Đọc bình luận LIVE bằng giọng nói\nBộ lọc và chống spam\nÂm thanh meme theo sự kiện', 1),
  ('pro_month', 'pro', 'Pro 1 tháng', 30, 59000, 3000, 0,
   'Gói Pro dùng liên tục 30 ngày, tiết kiệm hơn mua theo tuần.', E'Đọc bình luận LIVE bằng giọng nói\nBộ lọc và chống spam\nÂm thanh meme theo sự kiện', 2),
  ('promax_week', 'promax', 'Pro Max 1 tuần', 7, 39000, 0, 4,
   'Không giới hạn số bình luận đọc mỗi ngày trong 7 ngày.', E'Mọi tính năng của Pro\nĐọc không giới hạn bình luận mỗi ngày', 3),
  ('promax_month', 'promax', 'Pro Max 1 tháng', 30, 129000, 0, 4,
   'Gói cao nhất dùng liên tục 30 ngày, không giới hạn bình luận.', E'Mọi tính năng của Pro\nĐọc không giới hạn bình luận mỗi ngày', 4)
on conflict (id) do nothing;

-- Quyền sử dụng hiện tại của người gọi. Admin không bị giới hạn.
create or replace function public.get_entitlement() returns json
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  p public.profiles;
  s public.subscriptions;
  td int; cap int; used int;
  tier text; exp timestamptz; lim int; blocked boolean := false; why text := ''; pid text := '';
begin
  if uid is null then raise exception 'unauthenticated'; end if;
  select * into p from public.profiles where id = uid;
  td := coalesce((select value::int from public.app_config where key = 'free_trial_days'), 3);
  cap := coalesce((select value::int from public.app_config where key = 'free_daily_comments'), 200);
  select coalesce(sum(comments), 0) into used from public.daily_usage
    where user_id = uid and day = (now() at time zone 'Asia/Ho_Chi_Minh')::date;
  if p.role = 'admin' then
    tier := 'promax'; lim := 0; exp := null;
  else
    select * into s from public.subscriptions where user_id = uid and expires_at > now();
    if found then
      tier := s.tier; exp := s.expires_at; pid := coalesce(s.plan_id, '');
      lim := coalesce((select max_comments from public.plans where id = s.plan_id), 0);
    else
      tier := 'free'; exp := p.created_at + make_interval(days => td);
      if now() < exp then lim := cap;
      else lim := 0; blocked := true; why := 'trial_expired';
      end if;
    end if;
  end if;
  return json_build_object('tier', tier, 'expires_at', exp, 'daily_limit', lim, 'used_today', used,
                           'blocked', blocked, 'reason', why, 'trial_days', td, 'plan_id', pid);
end $$;
revoke all on function public.get_entitlement() from public, anon;
grant execute on function public.get_entitlement() to authenticated;

-- Máy chủ kết nối ghi số bình luận đã gửi cho người dùng (tối đa 500 mỗi lần gọi).
create or replace function public.add_usage(p_n int) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or p_n is null or p_n <= 0 then return; end if;
  insert into public.daily_usage (user_id, day, comments)
    values (auth.uid(), (now() at time zone 'Asia/Ho_Chi_Minh')::date, least(p_n, 500))
  on conflict (user_id, day) do update set comments = public.daily_usage.comments + least(p_n, 500);
end $$;
revoke all on function public.add_usage(int) from public, anon;
grant execute on function public.add_usage(int) to authenticated;

-- Mua gói bằng số dư ví: trừ tiền và cộng thời hạn trong một giao dịch duy nhất.
create or replace function public.purchase_plan(p_plan text) returns timestamptz
language plpgsql security definer set search_path = public as $$
declare pl public.plans; w bigint; nb bigint; s public.subscriptions; has_sub boolean; newexp timestamptz;
begin
  if auth.uid() is null then raise exception 'unauthenticated'; end if;
  select * into pl from public.plans where id = p_plan and active;
  if not found then raise exception 'plan_not_found'; end if;
  select * into s from public.subscriptions where user_id = auth.uid() for update;
  has_sub := found and s.expires_at > now();
  -- Đang có gói còn hạn: chỉ được gia hạn đúng gói đó, không mua gói khác cho tới khi hết hạn.
  if has_sub and s.plan_id is distinct from pl.id then raise exception 'plan_active'; end if;
  select balance into w from public.wallets where user_id = auth.uid() for update;
  if coalesce(w, 0) < pl.price then raise exception 'insufficient_balance'; end if;
  update public.wallets set balance = balance - pl.price, updated_at = now()
    where user_id = auth.uid() returning balance into nb;
  if has_sub then newexp := s.expires_at + make_interval(days => pl.days);
  else newexp := now() + make_interval(days => pl.days);
  end if;
  insert into public.subscriptions (user_id, tier, plan_id, expires_at)
    values (auth.uid(), pl.tier, pl.id, newexp)
  on conflict (user_id) do update set tier = excluded.tier, plan_id = excluded.plan_id, expires_at = excluded.expires_at;
  insert into public.wallet_ledger (user_id, delta, reason, ref, balance_after)
    values (auth.uid(), -pl.price, 'plan', pl.id, nb);
  return newexp;
end $$;
revoke all on function public.purchase_plan(text) from public, anon;
grant execute on function public.purchase_plan(text) to authenticated;

-- Admin cấp gói miễn phí cho một người dùng.
create or replace function public.admin_grant(target uuid, p_plan text) returns timestamptz
language plpgsql security definer set search_path = public as $$
declare pl public.plans; s public.subscriptions; newexp timestamptz;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  select * into pl from public.plans where id = p_plan;
  if not found then raise exception 'plan_not_found'; end if;
  select * into s from public.subscriptions where user_id = target for update;
  if found and s.tier = pl.tier and s.expires_at > now()
    then newexp := s.expires_at + make_interval(days => pl.days);
    else newexp := now() + make_interval(days => pl.days);
  end if;
  insert into public.subscriptions (user_id, tier, plan_id, expires_at) values (target, pl.tier, pl.id, newexp)
  on conflict (user_id) do update set tier = excluded.tier, plan_id = excluded.plan_id, expires_at = excluded.expires_at;
  return newexp;
end $$;
revoke all on function public.admin_grant(uuid, text) from public, anon;
grant execute on function public.admin_grant(uuid, text) to authenticated;

-- Danh sách người dùng cho admin: thêm gói đang dùng và ngày hết hạn.
drop function if exists public.admin_list_users();
create or replace function public.admin_list_users()
returns table (id uuid, username text, email text, role text, status text, created_at timestamptz,
               last_seen timestamptz, watching text, deletion_requested boolean, balance bigint,
               tier text, plan_expires timestamptz)
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
    select p.id, p.username, u.email::text, p.role, p.status, p.created_at, p.last_seen, p.watching,
           exists (select 1 from public.deletion_requests d where d.user_id = p.id),
           coalesce((select w.balance from public.wallets w where w.user_id = p.id), 0),
           coalesce((select s.tier from public.subscriptions s where s.user_id = p.id and s.expires_at > now()), 'free'),
           (select s.expires_at from public.subscriptions s where s.user_id = p.id and s.expires_at > now())
      from public.profiles p join auth.users u on u.id = p.id
     order by p.last_seen desc nulls last, p.created_at desc;
end $$;
revoke all on function public.admin_list_users() from public;
grant execute on function public.admin_list_users() to authenticated;

-- ============================================================
-- v6: thông báo của quản trị gửi cho mọi người dùng app
-- ============================================================
create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 80),
  body text not null check (char_length(body) between 1 and 600),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid() references auth.users(id) on delete set null
);
alter table public.announcements enable row level security;
drop policy if exists "ann_read" on public.announcements;
create policy "ann_read" on public.announcements for select to authenticated using (true);
drop policy if exists "ann_admin_ins" on public.announcements;
create policy "ann_admin_ins" on public.announcements for insert to authenticated with check (public.is_admin());
drop policy if exists "ann_admin_del" on public.announcements;
create policy "ann_admin_del" on public.announcements for delete to authenticated using (public.is_admin());
create index if not exists announcements_created_idx on public.announcements (created_at desc);
