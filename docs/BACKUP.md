# Cài đặt, sao lưu và khôi phục

Mã nguồn: `domain/backup/` (mô hình, quy tắc gộp, Kotlin thuần), `data/backup/` (định dạng JSON, kiểm tra, đọc/ghi tệp), `data/local/dao/BackupDao.kt` (đọc và gộp trong một transaction), `feature/settings/` (`BackupViewModel`, `BackupUi`).

## Cài đặt: lưu ở đâu

Mỗi giá trị chỉ lưu **một nơi**, trong DataStore `user_preferences` (cặp khóa–giá trị nhỏ, không cần truy vấn):

| Cài đặt | Khóa DataStore | Màn hình |
|---|---|---|
| Giao diện sáng / tối / hệ thống | `theme_mode` | Cài đặt |
| Furigana | `show_furigana` | Cài đặt |
| Khoảng ôn (dãy bậc) | `review_intervals` | Cài đặt → Ôn tập |
| Mục tiêu hằng ngày = giới hạn mục mới / lượt ôn | `new_per_day`, `reviews_per_day` | Cài đặt → Ôn tập (mục "Mục tiêu hằng ngày"); Trang chủ dùng làm mục tiêu hôm nay |
| Nhắc học bật/tắt, khung giờ, tần suất | `reminder_enabled`, `reminder_start_hour`, `reminder_end_hour`, `reminder_frequency` | Cài đặt → Nhắc học |
| Hiện cách đọc / nghĩa trên thông báo, riêng tư màn hình khóa | `reminder_show_reading`, `reminder_show_meaning`, `reminder_lock_screen` | Cài đặt → Nhắc học |

Trạng thái phụ (đã xem phần giới thiệu, từ vừa nhắc) cũng ở DataStore nhưng không phải cài đặt và không được sao lưu. Dữ liệu học (tiến độ, lịch sử) ở Room. Dãy bậc và giới hạn mỗi ngày là các tùy chọn ảnh hưởng trực tiếp đến lịch ôn; quy tắc ở [SRS.md](SRS.md).

## Tệp sao lưu

Người dùng chọn nơi lưu và tệp để khôi phục bằng **Storage Access Framework** (`CreateDocument` / `OpenDocument`); app không cần quyền bộ nhớ. Tên gợi ý: `n3sensei-backup-YYYY-MM-DD.json`.

| Có trong tệp | Không có trong tệp (và lý do) |
|---|---|
| `learning_progress` (mọi mục, kể cả mục đã gỡ khỏi nội dung) | Nội dung học (bài, từ, Kanji, ngữ pháp, nguồn): đến từ gói nội dung có giấy phép riêng; nhập lại gói khi cần |
| `review_records` (mọi câu trả lời, kèm `requestId`, `wasNew`) | Bật/tắt nhắc học: quyền thông báo thuộc từng máy |
| `item_marks`, `review_sessions` | Đã xem giới thiệu, từ vừa nhắc, ID dòng nội bộ của database |
| Cài đặt: giao diện, furigana, dãy bậc, giới hạn mỗi ngày, khung giờ/tần suất/nội dung/riêng tư của nhắc học | Thông tin cá nhân, tài khoản (app không có) |
|  | **Dữ liệu Contrast Lab** (`contrast_sessions`, `contrast_session_items`, `contrast_attempts`, `contrast_progress`, `contrast_reviews`): quyết định D6 của [CONTRAST_LAB_ARCHITECTURE.md](CONTRAST_LAB_ARCHITECTURE.md), bản đầu không sửa định dạng sao lưu. Khôi phục chỉ gộp, không xóa nên không đụng tới các bảng này (test `ContrastM8IntegrityTest`). Màn Sao lưu ghi rõ giới hạn này. Chuyển máy trực tiếp của Android (device-transfer) vẫn chép cả database |

Định dạng (JSON, UTF-8, thời điểm là epoch millis UTC):

```json
{
  "format": "n3sensei-backup",
  "formatVersion": 1,
  "createdAt": 1791730772084,
  "appVersion": "0.1.0",
  "includesContent": false,
  "settings": { "themeMode": "SYSTEM", "showFurigana": true,
    "review": { "intervals": "20m,1h,1d,2d,1w,2w,1mo", "newPerDay": 20, "reviewsPerDay": 200 },
    "reminder": { "startHour": 8, "endHour": 21, "frequency": "EVERY_3_HOURS",
      "showReading": true, "showMeaning": true, "lockScreen": "HIDE_MEANING" } },
  "progress": [ { "itemType": "VOCABULARY", "itemId": "n3-l01-v001", "state": "REVIEW", "stepIndex": 3,
    "intervalMinutes": 2880, "dueAt": 0, "lastReviewedAt": 0, "reviewCount": 3, "lapseCount": 0,
    "createdAt": 0, "updatedAt": 0 } ],
  "reviewRecords": [ { "itemType": "VOCABULARY", "itemId": "n3-l01-v001", "rating": "GOOD", "reviewedAt": 0,
    "intervalBeforeMinutes": 0, "intervalAfterMinutes": 60, "dueAtAfter": 0, "requestId": "…", "wasNew": true } ],
  "marks": [ { "itemType": "KANJI", "itemId": "…", "markedAt": 0 } ],
  "reviewSessions": [ { "id": "…", "startedAt": 0, "lastActivityAt": 0, "planned": 5, "answered": 5, "finished": true } ]
}
```

**Phiên bản**: `formatVersion` tăng khi định dạng đổi theo cách app cũ không đọc đúng. App đọc mọi phiên bản trong `SUPPORTED_BACKUP_VERSIONS` (hiện là 1). Tệp có phiên bản mới hơn sẽ bị từ chối với lời nhắc cập nhật app. Tệp mẫu v1 cố định `app/src/androidTest/assets/backup/v1-sample.json` **không bao giờ được sửa**; test bắt buộc các bản app sau vẫn khôi phục được nó. Trường lạ được bỏ qua để tệp mới hơn trong cùng phiên bản vẫn đọc được.

## Khôi phục

1. **Đọc**: dạng stream, từng phần tử một (bộ nhớ tăng theo số bản ghi, không theo kích thước văn bản); tệp tối đa 256 MB (300 000 lượt ôn ≈ 62–75 MB); không đọc được → "Không đọc được tệp". Xuất cũng ghi tuần tự, đọc lịch sử theo trang trong một transaction; lỗi khi xuất thì app thử xóa tệp dở dang.
2. **Kiểm tra toàn tệp** (lỗi thì từ chối cả tệp, không ghi gì): rỗng, JSON hỏng/cắt cụt, không phải `n3sensei-backup`, thiếu/không hỗ trợ `formatVersion`, thiếu `createdAt` hoặc một trong 4 danh sách.
3. **Kiểm tra từng bản ghi** (lỗi thì bỏ bản ghi đó, báo vị trí như `progress[3]` và trường lỗi): loại mục, ID (không rỗng, ≤ 200 ký tự), trạng thái, đánh giá, bậc (0–11), số không âm, thời điểm, `answered ≤ planned`. **ID trùng trong tệp** (cùng mục, cùng `requestId`, cùng phiên) → giữ bản đầu tiên. Cài đặt không hợp lệ → không khôi phục cài đặt, dữ liệu vẫn khôi phục.
4. **Xem trước**: app tính trước sẽ thêm / cập nhật / giữ nguyên bao nhiêu bản ghi mỗi loại, liệt kê lỗi và cảnh báo (vd. mục không có trong nội dung hiện tại). Người dùng chọn có khôi phục cài đặt không, rồi xác nhận hoặc hủy.
5. **Gộp** trong **một transaction** (`BackupDao.merge`), lập lại kế hoạch theo database lúc đó:
   - Tiến độ: giữ bản có `updatedAt` (lần trả lời gần nhất) **mới hơn**; bằng nhau thì giữ bản trên máy.
   - Lịch sử ôn: gộp hai phía; câu trả lời đã có (cùng `requestId`, hoặc cùng mục + thời điểm + đánh giá với bản ghi cũ không có `requestId`) không thêm lại. Mục có câu trả lời từ cả hai phía được tính lại "câu trả lời đầu tiên" (`was_new`) theo câu sớm nhất; mục khác giữ nguyên cờ trong tệp.
   - Đánh dấu, phiên ôn: gộp theo khóa.
   - **Không xóa gì.** Lỗi giữa chừng → rollback toàn bộ, database y như trước, cài đặt chưa bị đụng tới.
6. **Cài đặt** (nếu chọn): ghi sau khi dữ liệu đã khôi phục thành công. Nhắc học giữ trạng thái bật/tắt của máy, áp lại lịch WorkManager. Lỗi ở bước này được báo riêng (dữ liệu học vẫn đã được khôi phục).
7. **Báo cáo**: số bản ghi thêm / cập nhật / giữ nguyên theo loại, cài đặt có được khôi phục không, số bản ghi bị bỏ qua và danh sách lỗi.

Khôi phục cùng một tệp hai lần không đổi gì. Tiến độ của mục không có trong nội dung hiện tại vẫn được lưu (giống tiến độ của mục bị gỡ khỏi gói) và hiện lại khi nhập gói chứa mục đó.

## Kiểm thử

| Nội dung | Test |
|---|---|
| Vòng tròn ghi/đọc định dạng (encode/decode), database rỗng, tệp rỗng/trắng, JSON hỏng/cắt cụt, không phải sao lưu, phiên bản mới hơn/không hỗ trợ, thiếu trường, bản ghi lỗi bị bỏ kèm vị trí, ID trùng, cài đặt lỗi, giá trị ngoài miền, **tệp v1 cố định** | `BackupCodecTest` (JVM) |
| Thêm / cập nhật / giữ nguyên, tiến độ mới hơn thắng, trùng theo `requestId` và theo mục+thời điểm+đánh giá, khôi phục hai lần không đổi gì, khi nào tính lại `was_new` | `BackupMergeTest` (JVM) |
| Luồng xuất / kiểm tra / xem trước / xác nhận / hủy, không làm hai việc cùng lúc | `BackupViewModelTest` (JVM) |
| 300 000 lượt ôn: xuất, kiểm tra, khôi phục không hết bộ nhớ | `LargeDataPerformanceTest` (emulator) |
| Room thật + tệp thật: vòng tròn **khớp từng dòng** (tiến độ, lịch sử gồm `was_new`, đánh dấu, phiên) và cài đặt; không chọn cài đặt; khôi phục hai lần; xung đột giữa hai máy; **lỗi giữa transaction** (trigger SQLite) → rollback; tệp hỏng/rỗng/không tồn tại; ghi tệp lỗi; tệp v1 cố định; database rỗng | `BackupRepositoryTest` (emulator) |
| Giao diện: xuất → xóa dữ liệu → khôi phục qua màn hình Cài đặt (trình chọn tệp được giả lập bằng Espresso-Intents), hủy xem trước, tệp hỏng | `BackupEndToEndTest` (emulator) |

Đã thử tay trên emulator với trình chọn tệp thật của hệ thống (lưu vào Downloads, xóa dữ liệu app, khôi phục).
