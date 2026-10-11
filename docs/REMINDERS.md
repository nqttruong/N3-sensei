# Nhắc học, màn hình khóa và widget

## Ba bề mặt khác nhau

| Bề mặt | Có gì trong app | Giới hạn |
|---|---|---|
| **Thông báo** | Một thông báo nhắc học (kênh `study_reminders`), có nút "Mở thẻ" và "Cần học lại" | Cần quyền `POST_NOTIFICATIONS` (Android 13+); người dùng có thể tắt kênh hoặc cả ứng dụng |
| **Màn hình khóa** | Chính thông báo trên, với mức hiển thị theo cài đặt riêng tư | Android quyết định cách hiện; app chỉ gắn nhãn visibility |
| **Widget** | Widget màn hình chính "Từ cần học" (Glance) | Điện thoại Android nói chung **không** cho ứng dụng bên thứ ba đặt widget lên màn hình khóa; app không tuyên bố làm được điều đó |

## Lập lịch nền và giới hạn của Android

- `WorkManagerReminderScheduler`: một công việc định kỳ duy nhất (`study_reminder`) theo tần suất đã chọn (1, 2, 3, 4, 6 giờ hoặc 1 lần/ngày), flex 30 phút, ràng buộc **pin không yếu**. Bật/tắt hoặc đổi tần suất thì lịch được cập nhật ngay (`ExistingPeriodicWorkPolicy.UPDATE`, tắt → `cancelUniqueWork`). Đổi khung giờ hay nội dung không cần lên lịch lại vì được đọc ở mỗi lần chạy.
- WorkManager chạy **không chính xác**: hệ thống có thể trì hoãn hoặc gộp (Doze, App Standby bucket, tiết kiệm pin). App **không** dùng `SCHEDULE_EXACT_ALARM`/báo thức chính xác và không tự đánh thức máy; nhắc học không cần chính xác đến phút.
- Lịch nằm trong cơ sở dữ liệu của WorkManager: còn sau khi đóng app; sau khởi động lại máy, WorkManager lên lịch lại khi nhận `BOOT_COMPLETED` — tức **sau lần mở khóa đầu tiên** nếu máy có PIN/mật khẩu (đã kiểm tra trên emulator). Khi mở app, `MainViewModel` áp lại lịch theo cài đặt (phòng trường hợp cập nhật app hay khôi phục dữ liệu).
- Mỗi lần chạy, `ReminderRunner` (domain, Kotlin thuần) quyết định:
  1. tắt → không làm gì;
  2. ngoài khung giờ (giờ địa phương hiện tại; khung qua nửa đêm và "cả ngày" được hỗ trợ) → không;
  3. chưa đủ **3/4 chu kỳ** kể từ lần nhắc trước → không (hấp thụ độ trễ của hệ thống, không bao giờ nhắc dồn); nếu thời điểm lần trước nằm ở tương lai (đồng hồ bị lùi) thì không chặn;
  4. không có quyền / thông báo bị tắt → không;
  5. không có từ (database rỗng) → không;
  6. còn lại → đăng **một** thông báo với ID cố định: thông báo mới **thay** thông báo cũ, không chồng lên.

## Chọn từ (từ Room, không có danh sách cứng)

Từ vựng đến hạn ôn lâu nhất → nếu không có, từ đã đánh dấu "cần chú ý" → nếu không có, từ mới tiếp theo theo thứ tự giáo trình. Trong nhóm được chọn, bỏ qua từ vừa nhắc lần trước nếu còn từ khác. Mục đã bị gỡ khỏi nội dung được bỏ qua. Thông báo kèm số mục đang đến hạn (mọi loại). Widget dùng cùng quy tắc.

## Thao tác trên thông báo

- **Chạm / "Mở thẻ"**: deep link `n3sensei://vocabulary/{id}` (Navigation type-safe `navDeepLink`), mở task mới với back stack Trang chủ → Học → Thẻ từ. Intent tường minh tới `MainActivity`; không có intent-filter công khai nên app khác không gọi được.
- **"Cần học lại"**: `ReminderActionReceiver` (không export) chỉ **đánh dấu** từ cần chú ý, thay thông báo bằng dòng xác nhận im lặng (tự ẩn sau 8 giây). **Lịch ôn không thay đổi**; mọi thay đổi lịch chỉ xảy ra khi người dùng trả lời trong phiên ôn.

## Riêng tư

| Cài đặt | Cơ chế | Trên màn hình khóa có bảo mật |
|---|---|---|
| Hiện nghĩa = tắt | Nghĩa không bao giờ được đưa vào thông báo hay widget | Không có nghĩa (ở mọi nơi) |
| Hiện đầy đủ | `VISIBILITY_PUBLIC` | Hiện đầy đủ |
| Chỉ hiện từ, ẩn nghĩa | `VISIBILITY_PRIVATE` + public version (từ, cách đọc, "Chạm để xem…") | Bản rút gọn **khi** cài đặt hệ thống ẩn nội dung nhạy cảm; nếu hệ thống cho hiện nội dung nhạy cảm thì hiện đầy đủ |
| Ẩn hoàn toàn | `VISIBILITY_SECRET` | Không hiện |

## Quyền và trạng thái lỗi

- Bật nhắc học trên Android 13+ → hộp thoại quyền của hệ thống; từ chối → công tắc vẫn tắt, hiện giải thích và nút mở cài đặt thông báo của app.
- Quyền bị thu hồi sau khi đã bật (hệ thống dừng tiến trình app) → lần mở sau, màn hình báo "đang bật nhưng không được phép gửi"; công việc nền vẫn chạy nhưng không đăng gì; nút "Gửi thử" báo bị chặn.
- Trạng thái quyền được đọc lại mỗi khi màn hình cài đặt hiện lại (người dùng có thể đổi trong cài đặt hệ thống).

## Kiểm thử

| Nội dung | Unit test (JVM) | Instrumented (emulator Pixel 8, Android 16) | Thử tay trên emulator |
|---|---|---|---|
| Khung giờ, qua nửa đêm, múi giờ, khoảng cách, đồng hồ lùi | `ReminderPolicyTest` | | |
| Chọn từ, tránh lặp, DB rỗng, bị chặn, không đổi lịch ôn | `ReminderRunnerTest` | | |
| Bật/tắt, xin quyền trước, lên lịch lại khi cần | `ReminderSettingsViewModelTest` | | Từ chối rồi cho phép quyền |
| Thông báo thật: nội dung, kênh, nút, visibility, public version | | `ReminderInstrumentedTest` | Ảnh thanh thông báo |
| Không dồn thông báo | `ReminderRunnerTest` | 1 thông báo sau nhiều lần chạy | |
| WorkManager: đăng ký, đổi chu kỳ, hủy; chạy định kỳ qua TestDriver | | ✓ | Job có trong JobScheduler (pin không yếu) |
| Deep link | | ✓ (Back về Học) | Chạm thông báo và widget |
| "Cần học lại" | | ✓ (không ghi lịch sử ôn) | ✓ |
| Màn hình khóa 4 chế độ | | | ✓ với PIN, cả hai trạng thái "nội dung nhạy cảm" |
| Thu hồi quyền | | (thu hồi làm chết tiến trình test) | ✓ |
| Khởi động lại máy | | | ✓ lịch được tạo lại sau lần mở khóa đầu |
| Widget | | | ✓ ghim qua hộp thoại hệ thống, dữ liệu thật, mở thẻ |

Chưa thử trên **thiết bị thật**; mọi kiểm tra trên thiết bị là emulator Pixel 8 API 36. Chưa kiểm tra thời điểm chạy thực của job định kỳ (cần chờ hàng giờ); đã kiểm tra việc lên lịch và đường chạy qua WorkManager TestDriver.
