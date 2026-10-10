# TikTok Live Voice (Android)

Kotlin · Jetpack Compose · Material 3. Nhận bình luận TikTok LIVE công khai qua một **cầu nối** do bạn tự chạy, rồi đọc bằng Android TextToSpeech. Tài khoản do **Supabase Auth** quản lý.

## Thành phần

| Phần | Việc cần làm |
|---|---|
| `app/` | App Android. Build bằng GitHub Actions. |
| `supabase/schema.sql` | Chạy 1 lần trong Supabase SQL Editor (hồ sơ, tên đăng nhập duy nhất, RLS, yêu cầu xóa tài khoản). |
| `bridge/` | Máy chủ Node (tiktok-live-connector → WebSocket). Cần chạy sau HTTPS (`wss://`). |

## 1. Thiết lập Supabase
1. Tạo project, chạy `supabase/schema.sql`.
2. Authentication → Providers → Email: bật **Confirm email**.
3. Authentication → URL Configuration → Redirect URLs: thêm `tlv://auth` (để link quên mật khẩu mở lại app).
4. Lấy **Project URL** và **anon key** (Settings → API). *Không* dùng `service_role` trong app.

## 2. Đẩy code lên GitHub bằng Termux
```bash
pkg update && pkg install -y git gh
gh auth login            # chọn GitHub.com → HTTPS → đăng nhập bằng trình duyệt/token

cd ~/tiktok-live-voice   # thư mục project (giải nén vào đây)
git init -b main
git add .
git commit -m "Initial commit"
gh repo create tiktok-live-voice --private --source=. --push
```
Cập nhật sau này: `git add . && git commit -m "..." && git push`.

## 3. Cấu hình build trên GitHub
Repo → Settings → Secrets and variables → Actions → tab **Variables** → thêm:
- `SUPABASE_URL` = `https://xxxx.supabase.co`
- `SUPABASE_ANON_KEY` = anon key (khóa công khai theo thiết kế)

(Chưa đặt thì app vẫn build nhưng sẽ báo "Chưa cấu hình backend" ở màn hình đăng nhập.)

## 4. Build và tải APK
Mỗi lần push lên `main`, workflow **Build APK** chạy test + `assembleDebug`.
Actions → lần chạy mới nhất → **Artifacts** → `TikTokLiveVoice-debug-apk` → tải zip về điện thoại, giải nén, cài APK (cho phép "cài từ nguồn không xác định").

Tải artifact từ Termux (tùy chọn):
```bash
gh run download --name TikTokLiveVoice-debug-apk -D ~/storage/downloads   # sau khi: termux-setup-storage
```

> APK debug dùng để thử nghiệm. Bản phát hành cần keystore riêng: lưu vào GitHub **Secrets**, không commit vào repo.

## 5. Chạy cầu nối
```bash
cd bridge && npm install
SUPABASE_URL=... SUPABASE_ANON_KEY=... PORT=8080 node server.js
```
Đặt sau reverse proxy HTTPS (Caddy, nginx, Cloudflare Tunnel) để có `wss://domain`. Nhập địa chỉ đó ở app: *Kết nối TikTok LIVE → Địa chỉ cầu nối*. Cầu nối chỉ nhận người dùng đã đăng nhập và đã xác minh email.

## Giới hạn cần biết
- `tiktok-live-connector` là thư viện **không chính thức**; có thể hỏng khi TikTok đổi giao thức (app hiển thị lỗi rõ ràng).
- Android/hãng máy có thể dừng dịch vụ nền (tiết kiệm pin, thiếu RAM, buộc dừng). App dùng Foreground Service hợp lệ, không tìm cách vượt cơ chế hệ điều hành.
- Supabase không cho client liệt kê từng phiên; app hỗ trợ "đăng xuất mọi thiết bị" để thu hồi.
- Đăng nhập bằng tên dùng hàm `login_email_for` (cho phép dò email theo tên). Xóa hàm này trong `schema.sql` nếu muốn chỉ đăng nhập bằng email.
- Điều khoản/Chính sách trong app là bản mẫu, cần rà soát pháp lý.
- Trang quản trị: làm ở backend/Supabase Dashboard bằng `service_role`, không đặt trong APK.
- Mã này **chưa được biên dịch** trong môi trường tạo ra nó (không có Android SDK). Lần chạy Actions đầu tiên có thể báo lỗi biên dịch cần sửa.

## Danh sách kiểm thử trước phát hành
Đăng ký mới / trùng tên · sai mật khẩu · xác minh email · quên mật khẩu · đăng xuất mọi thiết bị · kết nối LIVE công khai · bình luận liên tục · đọc tiếng Việt · chỉnh tốc độ · chuyển app / tắt màn hình · mất mạng rồi tự kết nối lại · lượng bình luận lớn · pin & RAM · cô lập dữ liệu giữa tài khoản · đăng xuất khi dịch vụ chạy · buộc dừng app · cài đặt sau khi khởi động lại · build sạch trên Actions.


## Liên kết nhà phát triển
Trang **Giới thiệu ứng dụng** có nút mở TikTok `@minhtuyen523` và Discord `discord.gg/S9AFRTJpf`. Logo nhà phát triển chỉ xuất hiện trong trang Giới thiệu; biểu tượng ứng dụng/launcher hiện tại được giữ nguyên.

## Tài khoản quản trị
Không nhúng tên/mật khẩu quản trị vào mã nguồn hoặc APK: người tải APK có thể trích xuất chúng. Hãy đăng ký tài khoản `leminhtuyen` qua màn hình đăng ký sau khi cấu hình Supabase, xác minh email, rồi thiết lập quyền quản trị ở backend/Supabase bằng cơ chế role được kiểm tra phía máy chủ. Không dùng chuỗi `Tuyenpro123@` làm mật khẩu mặc định cho mọi bản cài; nếu đó là mật khẩu thật, nên đổi nếu đã chia sẻ công khai. Bản source hiện tại chưa có bảng điều khiển admin trong ứng dụng; các thao tác quản trị vẫn ở Supabase Dashboard/backend.

## Discord invite không hết hạn
Link được gắn vào app là link bạn cung cấp. Để bảo đảm không hết hạn, tạo lời mời mới trong Discord và chọn thời hạn **Never/Không bao giờ** (đồng thời chọn số lần sử dụng không giới hạn nếu cần), sau đó thay URL trong `AccountScreens.kt`. App không thể tự đổi cấu hình lời mời của máy chủ Discord.
