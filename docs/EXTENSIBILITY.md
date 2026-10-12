# Điểm mở rộng cho tương lai (Prompt 13)

**Không triển khai trong MVP**: không có mã chạy, quyền Internet, SDK hay dependency mới cho các mục dưới đây. Tài liệu này chỉ ghi điểm gắn vào kiến trúc hiện có và các quy tắc cần giữ, để khi làm không phải viết lại.

## Nền tảng đã có

- **Domain thuần Kotlin, repository là interface** (`domain/repository`, `domain/backup`, `domain/forecast`, `domain/reminder`, `domain/integrity`); `ArchitectureRulesTest` chặn domain/UI phụ thuộc Android hay tầng data. Cài đặt thật nằm trong `data/` và được tạo ở một chỗ (`AppContainer`).
- **ID ổn định**: nội dung dùng ID chuỗi do gói cung cấp; tiến độ tham chiếu `(item_type, item_id)`, không dùng ID dòng.
- **Lịch sử là nhật ký chỉ thêm**: mỗi câu trả lời có `request_id` (UUID) duy nhất toàn cục → dùng lại được làm khóa đồng bộ, chống trùng.
- **Quy tắc gộp đã có và được test**: `BackupMerge` (tiến độ mới hơn thắng theo `updatedAt`; lịch sử/đánh dấu/phiên gộp theo khóa; không xóa). Đây chính là chiến lược đồng bộ đơn giản nhất.
- **Định dạng trao đổi có phiên bản**: tệp sao lưu `formatVersion`, gói nội dung `formatVersion`.

## Cloud sync

- **Điểm gắn**: một `SyncRepository` mới trong domain, cài đặt trong `data/sync/`, chạy bằng WorkManager (đã có) khi người dùng bật.
  ```kotlin
  // Đề xuất, chưa có trong mã.
  interface SyncRepository {
      suspend fun push(since: Long): SyncResult      // gửi bản ghi mới (request_id) và tiến độ đổi
      suspend fun pull(): SyncResult                 // nhận về, gộp bằng BackupMerge trong một transaction
  }
  ```
- **Cần thêm khi làm**: tombstone cho việc **bỏ đánh dấu** (hiện là xóa dòng `item_marks`, đồng bộ sẽ không biết đã xóa); `updated_at` cho đánh dấu; một ID thiết bị; quyền Internet, chính sách quyền riêng tư, mã hóa khi truyền.
- **Giữ**: app vẫn chạy đầy đủ khi không đăng nhập/không mạng; Room là nguồn sự thật.

## Đăng nhập

- **Điểm gắn**: `AccountRepository` (trạng thái đăng nhập dạng `Flow`) chỉ dùng bởi sync; không màn hình học nào phụ thuộc vào nó.
- Không lưu mật khẩu; token trong bộ nhớ được bảo vệ (Android Keystore), không trong DataStore thường, không vào tệp sao lưu.

## AI giải thích ngữ pháp

- **Điểm gắn**: interface `GrammarExplainer` (domain) nhận `GrammarPoint` + câu hỏi, trả lời văn bản; ViewModel chi tiết ngữ pháp gọi qua interface, mặc định không có cài đặt nào → nút không hiện (không có nút giả).
- **Quy tắc**: câu trả lời AI hiển thị **tách khỏi** nội dung đã kiểm duyệt, ghi nhãn "do AI tạo, chưa kiểm chứng"; **không ghi vào bảng nội dung**; không gửi dữ liệu người học nếu chưa có đồng ý.

## AI tạo câu hỏi luyện tập

- **Điểm gắn**: phiên luyện tập đang lấy `PracticeItem` từ `ContentRepository`. Thêm một nguồn câu hỏi riêng (`PracticeQuestionSource`) cho chế độ luyện tập; chế độ luyện tập vốn **không ghi vào lịch ôn**, nên câu hỏi AI không làm sai lịch SRS.
- Muốn đưa vào lịch ôn thì phải qua quy trình kiểm duyệt và nhập như gói nội dung (có nguồn, giấy phép).

## Phân tích điểm yếu nâng cao

- **Không cần AI hay mạng**: dữ liệu đã có (`review_records` với đánh giá, thời điểm, `was_new`; `lapse_count`; trạng thái theo loại). Điểm gắn: hàm thuần trong `domain/analytics` (như `LearningAnalytics`, `ProgressEstimator`) + truy vấn tổng hợp mới trong `AnalyticsDao` (giữ quy tắc: đếm trong SQL, dùng chỉ mục thời gian, loại dữ liệu seed — xem [QA.md](QA.md)).
- Ví dụ: tỷ lệ quên theo loại/bài/thẻ chủ đề, mục hay nhầm với nhau (cần dữ liệu luyện tập trắc nghiệm có lưu lựa chọn sai — hiện chưa lưu).
- Giữ nguyên tắc Prompt 09: ghi rõ thực tế/ước tính, không suy diễn khả năng đỗ JLPT.

## Không làm bây giờ

Không thêm interface rỗng hay lớp chưa dùng vào mã nguồn: mã không có người dùng sẽ lỗi thời trước khi được dùng. Các interface trên được tạo cùng lúc với cài đặt đầu tiên của chúng.
