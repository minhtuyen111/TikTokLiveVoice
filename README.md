# Voice Live TikTok (Android)

Kotlin · Jetpack Compose. Đọc bình luận TikTok LIVE công khai bằng giọng nói, phát tiếng meme khi có theo dõi / chia sẻ / tặng quà. Tài khoản do **Supabase Auth** quản lý.

| Thư mục | Việc cần làm |
|---|---|
| `app/` | App Android (build bằng GitHub Actions → `voicelive-release.apk`, cài đặt tên **Voice Live TikTok**) |
| `supabase/schema.sql` | Chạy 1 lần trong Supabase SQL Editor (hồ sơ, vai trò admin, cấu hình, âm thanh meme, kho `memes`) |
| `bridge/` | Máy chủ Node nhận LIVE → WebSocket. Chạy 1 lần, mọi người dùng dùng chung |

## Cách hoạt động
Người dùng chỉ nhập `@tên_kênh`. Địa chỉ máy chủ kết nối được app tự lấy từ Supabase (bảng `app_config`), **không ai phải dán link**.
TikTok không có API công khai nên app không thể tự nối thẳng vào LIVE; máy chủ `bridge/` làm việc đó và gộp mọi người xem cùng một kênh vào một kết nối.

## 1. Supabase
1. Chạy `supabase/schema.sql`. Authentication → Email: bật **Confirm email**; thêm Redirect URL `tlv://auth`.
2. Đăng ký tài khoản `leminhtuyen` trong app, xác minh email, rồi chạy lại dòng cuối của `schema.sql` (`update public.profiles set role='admin' ...`). Quyền admin nằm ở máy chủ, **không** có mật khẩu/quyền nào trong APK.
3. Lấy **Project URL** và **anon key** (không dùng `service_role`).

## 2. Máy chủ kết nối
```bash
cd bridge && npm install
SUPABASE_URL=... SUPABASE_ANON_KEY=... PORT=8080 node server.js
```
Đặt sau HTTPS (Caddy/nginx/Cloudflare Tunnel) để có `wss://domain`. Mở app bằng tài khoản admin → menu **Quản trị** → dán `wss://domain` → Lưu. Từ đó mọi người dùng tự kết nối.

## 3. GitHub Actions
Repo → Settings → Variables: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (tùy chọn `BRIDGE_URL`, `APK_SIG_SHA256`).
Push lên `main` → tải artifact `voicelive-apk`.

**Khóa ký (nên làm một lần để cập nhật đè được):** trong Termux
```bash
keytool -genkeypair -keystore voicelive.jks -alias voicelive -keyalg RSA -keysize 2048 -validity 9125
base64 -w0 voicelive.jks    # dán vào Secret KEYSTORE_B64
```
Thêm Secrets `KEYSTORE_PASSWORD` (đặt cùng mật khẩu store và key) và `KEY_ALIAS`. Không có secret, mỗi lần build dùng khóa tạm nên phải gỡ bản cũ trước khi cài bản mới.
Chống đóng gói lại: lấy SHA-256 chứng chỉ (`keytool -list -v -keystore voicelive.jks`), bỏ dấu `:` và chữ thường, đặt vào Variable `APK_SIG_SHA256`.

## Âm thanh meme
Chỉ admin (menu **Quản trị**) thêm / xóa âm thanh cho Theo dõi, Chia sẻ, Tặng quà (≤ 2 MB). Người dùng thường chỉ chọn Tắt / Ngẫu nhiên / một tiếng cụ thể trong **Âm thanh meme**. Quyền được ép bằng RLS của Supabase nên sửa APK cũng không vượt được.

## Bảo vệ mã nguồn (nhiều lớp, không tuyệt đối)
R8 làm rối + gộp gói + xóa log + bỏ số dòng · chuỗi cấu hình mã hóa XOR/Base64 · phát hiện debugger, Frida/Xposed, APK bị đóng gói lại (chữ ký) và thoát an toàn · phiên đăng nhập mã hóa bằng Keystore · `allowBackup=false`. Không có cách chặn dịch ngược 100%; thứ quan trọng nhất vẫn được bảo vệ ở máy chủ (RLS, kiểm tra admin phía server).

## Phiên bản 2.x: tính năng mới
- **Giọng đọc 5 ngôn ngữ:** Việt (đầu danh sách), Anh, Trung, Nhật, Hàn; mỗi ngôn ngữ tự chọn giọng đúng ngôn ngữ; tùy chọn tự nhận ngôn ngữ từng bình luận; chỉnh âm lượng, tốc độ, cao độ.
- **Ngôn ngữ ứng dụng:** Việt, English, 中文, 日本語, 한국어 (Cài đặt → Giao diện). Các màn hình chính đã dịch; điều khoản/chính sách và vài đoạn trợ giúp dài còn tiếng Việt.
- **Giao diện:** 6 màu nền, 6 màu nhấn, logo/biểu tượng động, màn hình đăng nhập mới.
- **Quản trị (chỉ admin):** xem ai đang online và đang xem kênh nào, khóa/mở khóa tài khoản, cấp/gỡ quyền admin, quản lý âm thanh meme, địa chỉ máy chủ kết nối, phát hành bản cập nhật.
- **Chạy nền không ngắt:** dịch vụ nền + khóa CPU khi đang LIVE; app xin bỏ tối ưu hóa pin, đã cấp thì không hỏi nữa.
- **Cập nhật bản mới:** admin vào Quản trị → Hệ thống → chọn APK mới → Phát hành. Người dùng mở app sẽ thấy "Có bản cập nhật mới".
  Cài đè được bản cũ khi **cùng khóa ký** (Secret `KEYSTORE_B64` bên dưới) và mã phiên bản tăng (tự động theo số lần chạy workflow).

## Khóa ký cố định (bắt buộc để cập nhật đè)
```bash
pkg install -y openjdk-17
PW=$(head -c 12 /dev/urandom | base64 | tr -dc A-Za-z0-9)
keytool -genkeypair -keystore ~/voicelive.jks -alias voicelive -keyalg RSA -keysize 2048 -validity 9125 -storepass "$PW" -keypass "$PW" -dname "CN=Voice Live TikTok"
cd ~/neon-live
base64 -w0 ~/voicelive.jks | gh secret set KEYSTORE_B64
echo -n "$PW" | gh secret set KEYSTORE_PASSWORD
echo -n voicelive | gh secret set KEY_ALIAS
echo "$PW"
```
Sao lưu `voicelive.jks` và mật khẩu ở nơi an toàn: mất khóa là không cập nhật đè được nữa.

## Thanh toán (nạp tiền vào ví bằng QR chuyển khoản)
Luồng: người dùng chọn số tiền → app tạo đơn trên Supabase (mã `VLxxxxxxxx`) → hiện mã QR VietQR đã điền sẵn số tiền + nội dung → chuyển khoản → ngân hàng báo có → **SePay** gọi webhook → **Edge Function** `payment-webhook` gọi `confirm_payment()` → cộng ví. Chỉ máy chủ cộng tiền; app chỉ đọc số dư.
1. Chạy lại `supabase/schema.sql` (phần v4: ví, đơn, hàm RPC, RLS).
2. Đăng ký https://my.sepay.vn, liên kết tài khoản ngân hàng nhận tiền.
3. Supabase → Edge Functions → tạo function `payment-webhook`, dán `supabase/functions/payment-webhook/index.ts`, **tắt Verify JWT**, rồi Deploy. Edge Functions → Secrets: thêm `SEPAY_API_KEY` (chuỗi bạn tự đặt, dài và ngẫu nhiên).
4. SePay → WebHooks → Thêm: URL `https://<project>.supabase.co/functions/v1/payment-webhook`, kiểu chứng thực **API Key** (cùng giá trị `SEPAY_API_KEY`), Content-Type JSON.
5. Trong app: Quản trị → Thanh toán: nhập mã ngân hàng VietQR (MB, VCB, TCB… hoặc BIN 6 số), số tài khoản, tên chủ tài khoản, bật thanh toán, Lưu.
6. Thử nạp số nhỏ (10.000 ₫) trước khi mở cho mọi người.

Chống can thiệp (Lucky Patcher…): số dư nằm ở máy chủ và chỉ tăng khi có giao dịch ngân hàng thật, nên không thể tự cộng tiền bằng cách sửa app. Phía app thêm: phát hiện công cụ can thiệp (chặn nút thanh toán), kiểm tra chữ ký APK (đặt `APK_SIG_SHA256`), phát hiện Frida/Xposed/debugger. Mọi chức năng tính phí (nếu thêm sau) phải kiểm tra số dư ở máy chủ, đừng kiểm tra ở app.

## Gói dịch vụ (Free / Pro / Pro Max)
- Admin quản lý trong **Quản trị → Gói** (tên, cấp, số ngày, giá, bình luận/ngày, mô tả, quyền lợi, ảnh bìa động, bật/tắt bán) và **Quản trị → Free** (số ngày dùng thử, bình luận tối đa mỗi ngày).
- Người dùng mua bằng số dư ví trong **Gói dịch vụ**. Mua cùng cấp khi còn hạn thì cộng dồn ngày; mua cấp khác thì thay gói và tính từ bây giờ.
- Đang có gói còn hạn thì **không mua được gói khác** (chỉ gia hạn đúng gói đang dùng); máy chủ kiểm tra, admin vẫn cấp gói tay được.
- **Thông báo:** Quản trị → Thông báo gửi tiêu đề + nội dung; người dùng thấy hộp thoại khi mở app (và thông báo hệ thống nếu app đang ở nền, cần quyền thông báo).
- Bản Free: dùng thử N ngày kể từ lúc đăng ký, hết hạn thì cần mua gói. Admin không bị giới hạn.
- Giới hạn bình luận/ngày được **máy chủ kết nối (bridge)** áp dụng và ghi nhận qua `get_entitlement()` / `add_usage()`, nên sửa app không vượt được. Cần deploy lại `bridge/` sau khi cập nhật.
- Muốn giữ mọi tài khoản cũ dùng miễn phí khi mới bật tính năng: `update public.app_config set value = '36500' where key = 'free_trial_days';`

## Giới hạn
- `tiktok-live-connector` không chính thức; có thể hỏng khi TikTok đổi giao thức.
- Danh sách ngôn ngữ giọng đọc là những gì engine TTS trên máy hỗ trợ (tiếng Việt luôn đứng đầu).
- Mã **chưa được biên dịch** ở môi trường tạo ra nó; lần chạy Actions đầu có thể báo lỗi biên dịch cần sửa.
- Điều khoản/Chính sách đã viết đầy đủ nhưng nên nhờ luật sư rà soát trước khi phát hành rộng.


## Giọng Việt tích hợp (chạy mọi ROM) và hàng chờ sự kiện
- Tiếng Việt mặc định đọc bằng giọng **nằm trong APK** (sherpa-onnx + Piper), không cần engine TTS của ROM, không cần mạng. Dữ liệu `espeak-ng-data` được chép ra `filesDir` ở lần chạy đầu; có "cầu chì": nếu native từng làm sập app 2 lần, giọng này tự tắt và rơi về online/hệ thống thay vì văng lặp lại.
- CI **fail build** nếu thiếu `espeak-ng-data`/model, không phát hành APK lỗi.
- Theo dõi / chia sẻ / tặng quà vào **hàng chờ riêng**, đọc trước bình luận: phát âm meme (chờ xong) rồi đọc "Tên đã theo dõi/chia sẻ/tặng …". Quà liên tiếp cùng người được gộp số lượng.
- Hết hàng chờ: phát **âm đợi** (loại `idle`, admin thêm trong Quản trị → Âm thanh meme) và lặp lại sau 8–60 giây cho tới khi có người mới. **Chạy lại `supabase/schema.sql`** (hoặc 2 dòng `alter table ... meme_sounds_event_check`) để cho phép loại `idle`.

## Giọng đọc online (nam/nữ, Azure Neural TTS)

App đọc bằng giọng online (HoaiMy/NamMinh cho tiếng Việt; Anh, Trung, Nhật, Hàn cũng có nam/nữ) qua Edge Function `tts`, khóa Azure chỉ nằm trên Supabase. Mất mạng hoặc lỗi thì tự rơi về engine đọc của hệ thống.

1. Azure Portal → tạo tài nguyên **Speech** (gói F0 miễn phí khoảng 500.000 ký tự/tháng) → lấy **Key** và **Region** (vd `southeastasia`).
2. Supabase → Edge Functions → tạo function `tts`, dán `supabase/functions/tts/index.ts`, **giữ bật Verify JWT**, Deploy.
3. Edge Functions → Secrets: thêm `AZURE_SPEECH_KEY` và `AZURE_SPEECH_REGION`.
4. Build APK mới (giữ nguyên keystore, `VERSION_CODE` cao hơn bản cũ) rồi cài đè.

Lưu ý chi phí: vượt gói miễn phí Azure tính phí theo ký tự. Người dùng có thể tắt "Giọng chất lượng cao" để dùng giọng hệ thống.
