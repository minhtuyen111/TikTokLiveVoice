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
