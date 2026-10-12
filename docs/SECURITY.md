# Bảo mật, quyền riêng tư và toàn vẹn dữ liệu (Prompt 13)

Rà soát trước bản thử nghiệm, trên mã nguồn và emulator Pixel 8 (Android 16, API 36). Những gì **chưa xác minh được** ghi riêng ở cuối.

## Dữ liệu trên thiết bị

| Dữ liệu | Nơi lưu | Rời khỏi máy khi nào |
|---|---|---|
| Nội dung học (bài, từ, Kanji, ngữ pháp, nguồn) | Room `databases/n3sensei.db` | Không |
| Tiến độ, lịch sử ôn, đánh dấu, phiên ôn | Room (cùng tệp) | Chỉ khi người dùng **tự xuất** tệp sao lưu, hoặc chuyển trực tiếp sang máy mới |
| Cài đặt, trạng thái nhắc học | DataStore `files/datastore/user_preferences.preferences_pb` | Như trên (cài đặt có trong tệp sao lưu; trạng thái nhắc không có) |
| Lịch nhắc | Database của WorkManager | Không |
| Bản sao database hỏng (nếu từng xảy ra) | `no_backup/database-recovery/` (tối đa 2 bản) | Không |

- App **không có quyền Internet**, không có tài khoản, không SDK phân tích/quảng cáo/crash report, không gửi dữ liệu đi đâu.
- **Mã hóa**: app **không** tự mã hóa dữ liệu hay tệp sao lưu. Dữ liệu trong thư mục riêng của app được Android mã hóa theo cơ chế mã hóa bộ nhớ của hệ thống (bắt buộc trên máy ra mắt từ Android 10). Tệp sao lưu là JSON đọc được: người dùng chọn nơi lưu và cần tự giữ cẩn thận. Điều này được ghi trong Cài đặt → Sao lưu và khôi phục.
- **Sao lưu tự động của Android**: trước Prompt 13, `allowBackup="true"` với luật rỗng nghĩa là Android sao lưu toàn bộ database lên Google Drive mà tài liệu và phần giới thiệu ("hoạt động hoàn toàn ngoại tuyến") không nói tới. Nay:
  - Android 12+ (`data_extraction_rules.xml`): **không** đưa gì lên đám mây; **chuyển trực tiếp giữa hai máy** (cáp/Wi-Fi khi thiết lập máy mới) vẫn chép toàn bộ.
  - Android 8–11 (`backup_rules.xml`): hệ thống không tách được hai kiểu nên **không** sao lưu tự động.
  - Cách giữ tiến độ khi đổi máy: chuyển trực tiếp (Android 12+) hoặc tệp sao lưu tự xuất.

## Quyền Android

| Quyền | Vì sao | Ghi chú |
|---|---|---|
| `POST_NOTIFICATIONS` | Nhắc học | Xin lúc chạy, chỉ khi người dùng bật nhắc học |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` | WorkManager lên lịch lại sau khi khởi động máy và chạy công việc | Do WorkManager thêm; cần cho nhắc học |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX tự thêm, chỉ app này dùng | Không phải quyền người dùng |
| ~~`ACCESS_NETWORK_STATE`~~, ~~`FOREGROUND_SERVICE`~~ | WorkManager tự thêm nhưng app không dùng ràng buộc mạng hay foreground service | **Đã gỡ** (`tools:node="remove"`). Đã kiểm: gói cài đặt không còn hai quyền này; công việc nhắc học vẫn được JobScheduler lên lịch; thông báo thật vẫn hiện; test nhắc học qua WorkManager TestDriver vẫn qua |

Không có quyền bộ nhớ ngoài: nhập/xuất tệp dùng Storage Access Framework (người dùng chọn tệp), không đọc thư mục tùy ý.

## Thành phần được export, intent và deep link (bản release)

| Thành phần | Export | Đánh giá |
|---|---|---|
| `MainActivity` | Có (launcher) | App khác gửi được intent tường minh kèm deep link `n3sensei://vocabulary/<id>` → chỉ mở thẻ từ (chỉ đọc). Đã thử bằng adb: id đúng, id không tồn tại, rỗng, có ký tự SQL/`%00`, dài 5 000 ký tự, scheme khác → không crash, id lạ hiện "không tìm thấy"; truy vấn Room dùng tham số. Không có intent filter cho deep link nên chúng không xuất hiện như liên kết trong app khác |
| `StudyWordWidgetReceiver` | Có | Bắt buộc cho widget; chỉ làm mới widget |
| `ReminderActionReceiver` | **Không** | Chỉ nhận qua `PendingIntent` của app |
| `SystemJobService`, `GlanceRemoteViewsService`, `DiagnosticsReceiver`, `ProfileInstallReceiver` | Có, nhưng yêu cầu quyền hệ thống (`BIND_JOB_SERVICE`, `BIND_REMOTEVIEWS`, `DUMP`) | Của thư viện, đúng chuẩn |

- Mọi `PendingIntent` đều `FLAG_IMMUTABLE` và trỏ tới thành phần cụ thể.
- `ComponentActivity`/`PreviewActivity` export chỉ có trong bản **debug** (thư viện test/preview), không có trong bản release (đã kiểm manifest gộp).

## Log và bí mật

- Không có API key, mật khẩu, token hay URL dịch vụ trong mã nguồn. `keystore.properties`, `*.jks`, `*.keystore`, `local.properties` nằm trong `.gitignore`.
- Chỉ có **một** `Log.e` (gói nội dung đi kèm không cài được, kèm mã lỗi; không có dữ liệu người học). StrictMode chỉ ghi log ở bản debug.

## Dữ liệu từ ngoài (tệp sao lưu, gói nội dung)

| Kiểm tra | Kết quả |
|---|---|
| Kích thước | Gói nội dung ≤ 10 MB; tệp sao lưu ≤ 256 MB, đọc dạng stream |
| JSON hỏng, rỗng, sai định dạng, phiên bản lạ | Từ chối, không ghi gì (Prompt 10) |
| Bản ghi sai kiểu/thiếu trường/ID trùng/giá trị ngoài miền/ID rỗng hoặc > 200 ký tự | Bỏ bản ghi, báo vị trí |
| **JSON lồng rất sâu** (100 000 tầng) | **Lỗi tìm thấy**: tệp sao lưu như vậy làm `StackOverflowError` (là `Error`, không bắt được) → app sẽ crash khi kiểm tra tệp. **Đã sửa**: luồng đọc giới hạn 32 tầng (tệp thật sâu 4 tầng) → báo "tệp hỏng". Gói nội dung đã an toàn (đọc theo kiểu, bỏ qua trường lạ không đệ quy). Test: `HostileInputTest` |
| Chuỗi 1 000 000 ký tự, ký tự `\u0000`, ký tự đảo chiều | Bản ghi bị bỏ, không crash |
| Ghi vào database | Chỉ qua Room với tham số; sao lưu không tạo đường dẫn tệp từ nội dung tệp |

## Toàn vẹn dữ liệu

| Hạng mục | Bằng chứng |
|---|---|
| Foreign key, unique, restrict, cascade | `ContentPackDaoTest` (vi phạm bị từ chối), `PRAGMA foreign_key_check` sạch trong `DataIntegrityTest` |
| Transaction | Ghi câu trả lời (`applyReview`), thay gói, khôi phục sao lưu đều một transaction; test rollback khi lỗi giữa chừng (Prompt 6, 10) |
| Migration | `N3DatabaseMigrationTest`: mọi phiên bản 1 → 4, dữ liệu ghi bằng schema v1, v2, v3 còn nguyên |
| Không cập nhật lịch ôn hai lần | `DataIntegrityTest`: 20 lần gửi **đồng thời** cùng một câu trả lời trên database tệp thật (WAL) → đúng 1 lần áp dụng, 19 lần báo trùng; 20 câu trả lời khác nhau song song cho cùng một mục → không mất câu nào (đếm và bậc đúng) |
| App bị dừng giữa lúc lưu | Thử thật: 15 lần chạm "Tốt" rồi `am force-stop` sau 0–400 ms. Sau đó: `integrity_check` = ok, không vi phạm khóa ngoại, `review_count` khớp số câu trả lời ở mọi mục, không `request_id` trùng, mỗi mục đúng một câu trả lời đầu tiên. SQLite bảo đảm transaction nguyên tử; thử nghiệm không chắc đã cắt **đúng giữa** transaction |
| **Tệp database bị hỏng** | **Lỗi tìm thấy**: hệ thống xóa tệp và tạo database trống **không báo gì** — mất toàn bộ tiến độ, không còn bản nào để cứu. **Đã sửa**: trước khi xóa, app chép tệp hỏng vào `no_backup/database-recovery/` và hiện hộp thoại giải thích + hướng dẫn khôi phục từ tệp sao lưu (thông báo còn sau khi khởi động lại cho tới khi bấm "Đã hiểu"). Test: `CorruptDatabaseTest` (thất bại với mã cũ), `DatabaseCorruptionRecorderTest`; thử tay trên emulator |
| Sao lưu/khôi phục dữ liệu lớn | 300 000 câu trả lời (Prompt 11) |
| Cập nhật nội dung không xóa tiến độ | Tiến độ tham chiếu ID ổn định, không FK tới nội dung; thay gói giữ tiến độ/đánh dấu (test); mục bị gỡ được giữ tiến độ và hiện lại khi quay lại; thay gói đang cài cần xác nhận (Prompt 12) |

## Danh sách rủi ro

| Mức | Rủi ro | Trạng thái |
|---|---|---|
| Cao | Database hỏng bị xóa im lặng, mất toàn bộ tiến độ | **Đã giảm**: giữ bản hỏng + báo người dùng. Dữ liệu vẫn mất khỏi app nếu không có tệp sao lưu; app chưa tự cứu dữ liệu từ bản hỏng |
| Cao | Tệp sao lưu độc hại (JSON lồng sâu) làm crash app | **Đã sửa** + test |
| Trung bình | Sao lưu tự động lên Google Drive không được công bố | **Đã sửa** (tắt; giữ chuyển trực tiếp). Luật chưa kiểm được trên thiết bị (xem dưới) |
| Trung bình | Tệp sao lưu không mã hóa, người dùng có thể lưu vào nơi công khai | Chấp nhận cho MVP; đã ghi rõ trong app và tài liệu |
| Trung bình | Không có bản sao lưu định kỳ tự động → database hỏng/mất máy là mất tiến độ | Mở. Hướng: nhắc xuất sao lưu định kỳ (không cần mạng) |
| Trung bình | Chưa cấu hình ký bản release, R8 tắt; bản release chưa được build/test | Mở, cần trước khi phát hành |
| Trung bình | Thư viện cũ hơn bản mới (AGP, Kotlin, Room…), có thể thiếu bản vá | Mở; nâng cấp ở nhiệm vụ riêng |
| Thấp | App khác mở được thẻ từ qua intent tường minh tới `MainActivity` | Chấp nhận (chỉ đọc, đã thử dữ liệu lạ) |
| Thấp | Hai quyền thừa của WorkManager | **Đã gỡ** |
| Thấp | Nội dung nhắc học trên màn hình khóa | Người dùng chọn chế độ riêng tư (Prompt 07) |
| Thấp | Doc comment sai ("app khác không dùng được deep link") | **Đã sửa** |

## Chưa xác minh được

- **Luật sao lưu tự động trên thiết bị**: emulator từ chối sao lưu qua transport cục bộ và không cho quyền root, nên chưa xác nhận bằng thực nghiệm rằng không có gì lên đám mây và chuyển trực tiếp vẫn chép đủ. Đã kiểm: build và lint chấp nhận cấu hình, cú pháp theo tài liệu Android. Cần thử trên máy thật: Cài đặt → Google → Sao lưu, và thiết lập máy mới bằng cáp.
- Mã hóa bộ nhớ thực tế trên từng máy (do Android quản lý).
- Cắt điện/dừng tiến trình **đúng giữa** transaction (thời điểm không điều khiển được; dựa vào bảo đảm của SQLite).
- Hành vi trên Android 8–11 (chỉ chạy emulator Android 16).
- Bản release ký thật, Play pre-launch report.
