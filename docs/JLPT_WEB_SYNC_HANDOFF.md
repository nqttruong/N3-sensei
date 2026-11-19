# Bàn giao: đồng bộ QR giữa N3 Sensei (Android) và JLPT AI Review (Web/Desktop)

Ngày: 2026-11-19. Người nhận: AI/nhóm phụ trách repository Web (`fastapi/`).
Phía Android không sửa repository Web. Mọi thay đổi cần làm trên Web hoặc Docker được ghi ở đây.

Tài liệu này không chứa token, mật khẩu, khóa admin hay dữ liệu học tập.

## 1. Phía Android đã làm gì

Giao thức lấy từ mã nguồn Web, chỉ đọc:
- `backend/app/sync/qr.py`
- `backend/app/sync/router.py`
- `backend/app/sync/schemas.py`
- `backend/app/sync/admin.py`
- `docs/android-qr-sync-protocol.md`
- `frontend/src/features/sync/qr-pairing-panel.tsx`

Android không tự đặt endpoint mới. Nó chỉ gọi các endpoint đã có trong mã nguồn Web.

| Bước | Endpoint (gốc là `{base_url}/api/v1`) | Xác thực |
|---|---|---|
| Nhận phiên QR | `POST /sync/qr-sessions/{id}/claim`, body `{challenge, device_name, device_credential}` | challenge lấy từ QR |
| Báo sẵn sàng | `POST /sync/qr-sessions/{id}/ready` | `X-Claim-Ticket` |
| Hỏi trạng thái, mỗi 3 giây | `GET /sync/qr-sessions/{id}` | `X-Claim-Ticket` |
| Tải thay đổi | `GET /sync/changes?cursor=N&limit=200` | `Bearer <device_credential>` |
| Gửi lịch sử ôn | `POST /sync/push`, `{events:[…]}` | `Bearer <device_credential>` |
| Ngắt kết nối | `POST /sync/disconnect` | `Bearer <device_credential>` |

Ghép nối thủ công cũ (`POST /sync/pair` với `X-Pairing-Key`) vẫn giữ để dùng với Web phiên bản cũ.

### Kiểm tra mã QR

Android kiểm tra QR trước khi gửi bất kỳ request nào (`QrPairingPayload.kt`):
- QR phải là đúng 5 khóa, không thừa không thiếu.
- `version` phải là số `1`.
- `base_url` phải là một HTTPS origin:
  - không chứa user/password, path, query hay fragment;
  - không phải `localhost` hay địa chỉ loopback.
- `session_id` phải là UUID.
- `challenge` gồm 24–100 ký tự `[A-Za-z0-9_-]`.
- `expires_at` phải là ISO 8601 có múi giờ và chưa hết hạn.

### Credential thiết bị

- Android tự tạo credential: 48 byte từ `SecureRandom`, mã hóa Base64 URL-safe thành 64 ký tự.
- Credential được lưu, đã mã hóa bằng AES-GCM với khóa trong Android Keystore, **trước khi** gửi claim.
- Khi claim lại cùng phiên (ví dụ sau lỗi mạng), Android dùng lại đúng credential đó.
- Claim ticket và mã 6 số cũng được lưu đã mã hóa.
- Challenge không được lưu.
- Không có giá trị bí mật nào được ghi vào log hay thông báo lỗi.

### Lịch sử học được gửi lên

Android gửi các bản ghi trong `review_records` (từ vựng/Kanji/ngữ pháp) có `request_id` dạng UUID:
- Mỗi lần gửi tối đa 100 bản ghi.
- Mỗi bản ghi gồm: `item_type`, `item_id`, `rating`, `reviewed_at`, các khoảng ôn và `due_at_after`.
- Con trỏ hàng đợi chỉ tiến lên khi tập `accepted ∪ duplicates` khớp đúng các `request_id` đã gửi.
- Bản ghi không đạt `AndroidReviewEventIn` (ví dụ `request_id` không phải UUID, từ bản sao lưu cũ) bị bỏ qua và được đếm. Chúng vẫn nằm nguyên trên điện thoại.
- Khi ghép nối sang máy chủ khác, checkpoint và con trỏ lịch sử được đặt lại. Lịch sử sẽ được gửi lại; máy chủ trả các bản ghi đã có trong `duplicates`.
- Khi đã ghép nối, WorkManager tự đồng bộ khoảng 6 giờ một lần, chỉ khi có mạng. Việc này dừng khi ngắt kết nối.

Dữ liệu Contrast Lab, phiên thi thử, từ đã lưu và tiến độ SRS **không** được gửi, vì máy chủ chưa có schema cho chúng. Room trên Android không bị ghi đè.

## 2. Trạng thái kiểm thử phía Android

| Hạng mục | Trạng thái |
|---|---|
| Kiểm tra QR (unit, 7 nhóm trường hợp) | Đã chạy, đạt |
| Claim → ready → chờ xác nhận → PAIRED → pull/push bằng Bearer → ngắt kết nối, với máy chủ giả mô phỏng `qr.py`/`router.py` (instrumentation, emulator) | Đã chạy, đạt |
| Giao diện Cài đặt: dán QR → hiện mã → Web xác nhận → đã ghép nối → đồng bộ (Compose test, emulator) | Đã chạy, đạt |
| Trình quét Google Code Scanner mở được từ app (emulator có Play services) | Đã kiểm tra bằng tay |
| **Quét QR thật từ Web → Web xác nhận → đồng bộ thật** | **Chưa làm được: bị chặn ở mạng/backend (xem mục 3)** |

## 3. Việc Web/Docker cần làm để chạy được end-to-end

Đã quan sát trên máy phát triển ngày 2026-11-19:

1. **Chỉ có HTTP trên loopback.**
   - Caddy chỉ publish `127.0.0.1:18080` (HTTP) và `127.0.0.1:18443`.
   - Điện thoại thật trong LAN không kết nối được. Thử `http://<IP LAN>:18080` từ chính máy chủ cũng không có phản hồi.
2. **`PUBLIC_APP_URL` (tức `SYNC_PUBLIC_HTTPS_BASE_URL`) đang là `http://127.0.0.1:18080`.**
   - `_public_url()` trong `qr.py` sẽ trả **503** khi tạo phiên QR, vì URL này không phải HTTPS và là loopback.
   - Vì vậy hiện không tạo được mã QR dùng được.
3. **Đồng bộ thiết bị qua mạng bị chặn khi không dùng HTTPS.**
   - `require_https_for_remote_sync` trả 400 "Đồng bộ từ thiết bị qua mạng chỉ chấp nhận HTTPS" cho client không phải loopback.
   - Đã kiểm tra trên máy chủ bằng `curl http://127.0.0.1:18080/api/v1/sync/qr-sessions/<uuid>`: nhận đúng 400 với thông điệp trên.
4. **Từ emulator, request tới `http://10.0.2.2:18080` nhận được `200` với body rỗng từ Caddy, kể cả với `/api/v1/health`.**
   - Từ máy chủ, cùng request đó nhận JSON health bình thường.
   - Chưa xác định nguyên nhân. Cần kiểm tra cấu hình Caddy/Docker nếu muốn dùng emulator.

Đề xuất cho phía Web (không bắt buộc theo đúng thứ tự):
- **Cấp một hostname HTTPS mà điện thoại tin cậy**, ví dụ chứng chỉ thật qua DNS, hoặc một CA nội bộ đã cài vào điện thoại. Sau đó:
  - đặt `CADDY_SITE_ADDRESS` là hostname đó;
  - đặt `PUBLIC_APP_URL=https://<hostname>`;
  - publish cổng 443 ra giao diện LAN (`PUBLISH_BIND_ADDRESS`) hoặc dùng reverse tunnel có HTTPS.
  - Android không bỏ qua kiểm tra chứng chỉ và không chấp nhận HTTP. Đây là yêu cầu bảo mật, không phải giới hạn tạm thời.
- **Giữ Basic Auth cho trình duyệt.** Caddyfile hiện đã cho `/api/v1/sync/*` đi thẳng tới backend với xác thực riêng của thiết bị. Android **không** cần và **không** lưu mật khẩu Caddy. Không cần tắt xác thực toàn bộ API.
- Cân nhắc trả `device_id` trong phản hồi của claim/ready. Hiện Android chỉ biết `device_id` khi trạng thái là PAIRED; đây không phải lỗi.
- Nếu muốn Web hiển thị lịch sử ôn của Android, dữ liệu đã có trong `android_review_events` qua `/sync/push` (xem `router.py`, nhánh `entity_type == "android_review_event"`).

## 4. Cách kiểm thử end-to-end khi đã có HTTPS

1. Trên Web: vào Cài đặt → Đồng bộ Android → Tạo mã QR. Mã có hạn 5 phút.
2. Trên Android: vào Cài đặt → "Đồng bộ JLPT AI Review" → bấm "Quét mã QR".
3. So sánh tên thiết bị và mã 6 số hiển thị trên điện thoại, rồi nhập mã trên Web.
4. Android tự chuyển sang "Đã ghép nối" trong vòng khoảng 3 giây.
5. Bấm "Đồng bộ ngay". Kiểm tra:
   - trên Web: `last_pull_at` và `last_push_at` của thiết bị;
   - trên Android: số "Lịch sử ôn tập chờ gửi" về 0.
6. Thu hồi thiết bị trên Web. Lần đồng bộ sau, Android phải báo xác thực hết hạn hoặc bị thu hồi (HTTP 401).
7. Thử thêm các tình huống lỗi:
   - QR hết hạn;
   - từ chối trên Web;
   - nhập sai mã 5 lần (phiên chuyển sang FAILED);
   - tạo QR mới khi phiên cũ đang chờ (phiên cũ chuyển sang CANCELLED).
