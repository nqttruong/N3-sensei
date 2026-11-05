# Audit thông báo nhắc học (2026-10-28)

Phạm vi: `app/reminder/*`, `domain/reminder/*`, `feature/settings/Reminder*`,
`data/preferences/DataStore*`, `MainViewModel`, `AndroidBackupRepository`, `AndroidManifest.xml`.
Chưa có log từ máy thật của người dùng: các kết luận dưới đây dựa trên code, trừ khi ghi khác.

## Cơ chế hiện có

- **Lên lịch:** một `PeriodicWorkRequest` WorkManager duy nhất tên `study_reminder`
  (`enqueueUniquePeriodicWork(…, UPDATE, …)`). Chu kỳ = tần suất (1, 2, 3, 4, 6 giờ hoặc
  1 lần/ngày), flex 30 phút, ràng buộc "pin không yếu". Không dùng AlarmManager và không có
  BOOT receiver riêng (WorkManager tự khôi phục lịch sau khi khởi động lại máy).
- **Áp dụng lịch:** mỗi lần mở app (`MainViewModel`), khi đổi bật/tắt hoặc tần suất
  (`ReminderSettingsViewModel`, chỉ khi `scheduleKey` đổi) và sau khi khôi phục sao lưu
  (`AndroidBackupRepository`). Cả ba nơi đều dùng cùng tên unique work.
- **Mỗi lần chạy** (`ReminderRunner.runScheduled`): kiểm tra bật/tắt → khung giờ →
  khoảng cách tối thiểu (3/4 chu kỳ kể từ lần hiện gần nhất) → quyền thông báo → chọn từ →
  `notify(1001, …)`, một ID cố định nên thông báo mới **thay** thông báo cũ.
- **Worker** luôn trả về `Result.success()`, không retry.
- **Trạng thái gửi** (DataStore): chỉ lưu `lastShownAt` và `lastVocabularyId` (một từ).

## Nguyên nhân từ bị lặp

| # | Nguyên nhân | Mức độ |
|---|---|---|
| 1 | **Chỉ nhớ một từ gần nhất.** `nextMessage` lấy nhóm đầu tiên còn từ (đến hạn → đã đánh dấu → mới) theo thứ tự cố định, rồi chỉ né từ của lần trước. Trong một nhóm có từ hai từ trở lên, thông báo luân phiên giữa hai từ đứng đầu: A, B, A, B… | **Xác nhận bằng code** |
| 2 | **Nhóm "đến hạn" và "đã đánh dấu" không đổi khi chỉ xem thông báo.** Từ chỉ rời nhóm đến hạn khi được ôn trong tab Ôn tập. Nút "Cần học lại" trên thông báo lại đánh dấu từ, khiến từ đó vào nhóm "đã đánh dấu" và được chọn lại. | **Xác nhận bằng code** |
| 3 | **Từ mới theo thứ tự khóa học.** `getNewItems` trả về theo thứ tự bài, nên khi chưa học gì, hai từ mới đầu tiên luân phiên mãi. | **Xác nhận bằng code** |
| 4 | Widget dùng `nextMessage` không né từ nào, nên luôn hiện đúng từ đứng đầu. | Xác nhận bằng code (widget, không phải thông báo) |
| 5 | Nhiều lịch chạy trùng nhau | **Loại trừ:** unique work + `UPDATE` ở mọi nơi gọi |
| 6 | Retry tạo thông báo trùng | **Loại trừ:** worker không bao giờ trả `retry()` |
| 7 | Reboot hoặc mở app tạo thêm lịch | **Loại trừ:** dùng cùng unique work; WorkManager tự khôi phục |
| 8 | Hai lần chạy sát nhau (WorkManager chạy bù sau Doze, hoặc đổi chu kỳ bằng `UPDATE`) | **Giả thuyết, đã có chặn:** khoảng cách tối thiểu 3/4 chu kỳ; nếu có xảy ra thì cũng chỉ thay thông báo cũ (ID cố định) |
| 9 | Đổi múi giờ hoặc đổi giờ máy | **Giả thuyết, phần lớn đã xử lý:** khung giờ tính theo giờ địa phương mỗi lần chạy; `lastShownAt` ở tương lai không chặn |

**Tần suất không đủ dày:** tối thiểu 1 giờ (người dùng muốn 15–120 phút). Không có giới
hạn số thông báo mỗi ngày. Chỉ có từ vựng. Nghĩa hiện mặc định (`showMeaning = true`).

## Giới hạn Android (sẽ ghi rõ trong Cài đặt)

- **Chu kỳ tối thiểu:** WorkManager chạy định kỳ tối thiểu 15 phút. Thời điểm chạy không chính
  xác: hệ thống có thể dời hoặc gộp lần chạy (Doze, App Standby buckets, tiết kiệm pin, giới
  hạn riêng của hãng máy). Muốn chính xác thì phải dùng exact alarm, cần quyền
  `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`; quyền này không phù hợp với ứng dụng nhắc học
  nên không dùng.
- **Quyền thông báo:** Android 13+ cần `POST_NOTIFICATIONS`. Người dùng cũng có thể tắt kênh
  thông báo; khi đó lần chạy ghi nhận `NOTIFICATIONS_BLOCKED`, không giả vờ đã gửi.
- **Force stop:** sau khi bị "Buộc dừng", WorkManager không chạy cho đến lần mở app tiếp theo.

## Hướng sửa

1. **Lịch sử gửi theo từng mục:** một bảng Room nhỏ ghi thời điểm gửi gần nhất của từng mục
   (không đụng tới tiến độ ôn). Cách chọn:
   - Bỏ qua mục đã gửi trong khoảng "nghỉ" (mặc định 24 giờ). Hết khoảng nghỉ thì mục được
     gửi lại, nghĩa là không cấm lặp vĩnh viễn.
   - Ưu tiên theo thứ tự: đến hạn → đã đánh dấu → mới. Trong mỗi nhóm, mục chưa từng gửi hoặc
     gửi lâu nhất lên trước.
   - Khi mọi mục đều đang trong khoảng nghỉ, chọn mục gửi lâu nhất.
2. **Tần suất 15/30/60/120 phút:** tên enum cũ được giữ để đọc cài đặt đã lưu. Thêm giới hạn
   số thông báo mỗi ngày, phạm vi nội dung (Từ vựng / Kanji / Ngữ pháp / Chỉ mục đến hạn) và
   tùy chọn mặc định ẩn nghĩa.
3. **Đổi lịch:** vẫn dùng một unique work, đổi chu kỳ bằng `UPDATE`, tắt bằng
   `cancelUniqueWork`. Có test chứng minh không phát sinh lịch thứ hai.
