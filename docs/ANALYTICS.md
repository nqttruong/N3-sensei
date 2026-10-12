# Thống kê học tập

Mã nguồn: `domain/analytics/` (quy tắc, Kotlin thuần), `data/local/dao/AnalyticsDao.kt` (đếm bằng SQL), `feature/stats/`. Bảng điều khiển ở Trang chủ và các dự báo dùng cùng quy tắc: xem [FORECAST.md](FORECAST.md).

## Dữ liệu nguồn và loại trừ

- Nguồn: `review_records` (mỗi câu trả lời trong **phiên ôn tập**) và `learning_progress`.
- **Không có** trong lịch sử nên **không** được tính: xem màn hình chi tiết, luyện tập theo bài/mục đã đánh dấu (chế độ luyện tập không ghi lịch sử), nhắc học. App không tính "mục đã xem".
- **Loại trừ** khỏi mọi chỉ số: bản ghi của gói nội dung seed/test (`content_packs.is_seed = 1`) và của mục không còn trong nội dung (bị gỡ khi cập nhật gói, hoặc không được nhập vì lỗi). Tiến độ/lịch sử của các mục đó vẫn được giữ trong database.

## Đơn vị: lượt và mục

| Chỉ số | Đơn vị | Định nghĩa |
|---|---|---|
| Mục đã học | mục | Số mục khác nhau có ít nhất một câu trả lời trong khoảng |
| Mục mới | mục | Mục có **câu trả lời đầu tiên** (`was_new`) trong khoảng; mỗi mục chỉ có một câu trả lời đầu tiên |
| Lượt ôn | lượt | Câu trả lời cho mục **đã có tiến độ** từ trước (`was_new = 0`) |
| Mục được ôn | mục | Số mục khác nhau trong các lượt ôn |
| Tổng lượt trả lời | lượt | Mọi câu trả lời trong khoảng (= lượt ôn + mục mới) |
| Đến hạn / quá hạn | mục | Tại thời điểm hiện tại: `due_at ≤ bây giờ`; quá hạn = đến hạn từ trước 0:00 hôm nay. Không phụ thuộc bộ lọc thời gian |
| Tỷ lệ nhớ | % lượt | (lượt ôn đánh giá Khó + Tốt + Dễ) ÷ (tổng lượt ôn) trong khoảng. Không tính câu trả lời đầu tiên của mục mới (chưa phải là nhớ lại). Không có lượt ôn → “—”, không phải 0 % |
| Hay quên / thấy khó | lượt | Top 10 mục theo số lượt Quên + Khó trong khoảng (gồm cả câu trả lời đầu tiên); hòa thì nhiều Quên hơn, rồi gần đây hơn |
| Phân bố trạng thái | mục | Theo loại: Mới (chưa trả lời), Đang học (bậc < 1 ngày), Đang ôn (≥ 1 ngày), Thành thạo (bậc cuối). Trạng thái hiện tại, không phụ thuộc bộ lọc |
| Dự báo đến hạn | mục, **ước tính** | Số mục có `due_at` rơi vào từng ngày của 7 ngày tới; hôm nay cộng thêm số đang đến hạn. Giả định không ôn trước; mỗi lần ôn sẽ đổi lịch |
| Chuỗi ngày học | ngày | Ngày học = ngày địa phương có ≥ 1 câu trả lời. Chuỗi hiện tại = số ngày học liên tiếp kết thúc hôm nay, hoặc hôm qua nếu hôm nay chưa học (chưa mất chuỗi cho tới khi bỏ trọn một ngày) |

## Ngày và múi giờ

- Một ngày = từ **0:00 giờ địa phương** (múi giờ hiện tại của máy) đến 0:00 hôm sau. Ngày đổi giờ mùa hè dài 23/25 giờ.
- Bộ lọc: Hôm nay, 7 ngày, 30 ngày (gồm hôm nay), Tất cả. Khoảng = [0:00 của ngày đầu, 0:00 ngày mai).
- Biểu đồ theo ngày: 7 ngày (Hôm nay, 7 ngày), 30 ngày (30 ngày, Tất cả).
- Đổi múi giờ: thời điểm lưu dạng UTC không đổi; các mốc ngày được tính lại theo múi giờ mới (màn hình tự cập nhật mỗi phút).

## Hiệu năng

Mọi phép đếm chạy trong SQLite (`COUNT`, `COUNT(DISTINCT …)`, `SUM(CASE …)`, `GROUP BY`) trên chỉ mục `review_records(reviewed_at)`; không tải lịch sử lên bộ nhớ. Điều kiện "mục còn trong nội dung" là một tập `item_type:item_id IN (…)` để SQLite giữ dùng chỉ mục thời gian, và khoảng thời gian luôn có cận dưới ("Tất cả" = `Long.MIN_VALUE`); xem [QA.md](QA.md) cho số đo với 300 000 lượt. Theo ngày và chuỗi ngày: SQL gom câu trả lời theo **khối 15 phút căn theo UTC** (`reviewed_at / 900000`), Kotlin quy các khối ra ngày địa phương. Vì mọi độ lệch múi giờ thực tế là bội số của 15 phút (kể cả +05:30, +05:45), một khối không bao giờ vắt qua nửa đêm địa phương. Số dòng trả về tối đa 96 mỗi ngày có học, thay vì một dòng mỗi câu trả lời.

## Liên kết

- "Ôn ngay" → tab Ôn tập.
- Tên loại → danh sách từ vựng/Kanji/ngữ pháp; chip trạng thái → danh sách đó đã lọc sẵn trạng thái.
- Mục hay quên → màn hình chi tiết của mục.

## Kiểm thử

- `LearningAnalyticsTest` (JVM): tỷ lệ nhớ (kể cả không có dữ liệu), khoảng thời gian (kể cả ngày đổi giờ), quy khối 15 phút ra ngày (kể cả +05:30/+05:45), lấp ngày trống, chuỗi ngày học, dự báo.
- `StatsViewModelTest` (JVM): database rỗng, đổi bộ lọc → đúng khoảng, các giá trị suy ra.
- `AnalyticsRepositoryTest` (Room thật): bộ dữ liệu `AnalyticsFixture` có số liệu **đếm tay** ghi trong chú thích — lượt vs mục, mục mới, tỷ lệ nhớ 2/3, theo ngày, chuỗi, top hay quên, trạng thái, đến hạn/quá hạn, dự báo, loại trừ seed và mục đã gỡ, database rỗng.
- `StatisticsEndToEndTest` (emulator): màn hình hiển thị đúng các số đếm tay cho "Hôm nay" và "7 ngày", chip trạng thái mở danh sách đã lọc, các trạng thái không có dữ liệu.
