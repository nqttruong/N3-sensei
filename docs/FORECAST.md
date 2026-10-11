# Bảng điều khiển và dự báo

Mã nguồn: `domain/forecast/` (Kotlin thuần, không ML, không Internet), `feature/home/` (Trang chủ = bảng điều khiển), `data/local/dao/AnalyticsDao.kt` và `ReviewSessionDao.kt`.

Mỗi thẻ trên Trang chủ có nhãn **Thực tế** (đọc thẳng từ Room) hoặc **Ước tính** (tính từ giả định đã công bố). Các quy tắc loại trừ và cách tính ngày giống [ANALYTICS.md](ANALYTICS.md): bỏ gói seed/test và mục đã gỡ, ngày từ 0:00 giờ địa phương.

## Thực tế hay ước tính

| Thẻ | Chỉ số | Loại | Nguồn / cách tính |
|---|---|---|---|
| Mục tiêu hôm nay | Mục mới x / y | Thực tế | x = mục có câu trả lời đầu tiên hôm nay; y = giới hạn mục mới mỗi ngày, nhưng không vượt x + số mục chưa học |
| | Lượt ôn x / y | Thực tế | x = lượt ôn hôm nay; y = x + số mục đang đến hạn, tối đa giới hạn lượt ôn mỗi ngày. y có thể tăng trong ngày (vd. sau "Quên") |
| Cần ôn | Đến hạn, quá hạn | Thực tế | Như màn hình Thống kê |
| | Ưu tiên ôn (tối đa 5) | Thực tế | Mục đang đến hạn: đến hạn sớm nhất trước, cùng thời điểm thì mục quên nhiều lần hơn trước |
| Dự báo khối lượng ôn | Đã lên lịch | **Thực tế** | Số mục có `due_at` đã lưu nằm trong khoảng; mỗi mục tính 1 lần |
| | Lượt ôn dự kiến, mục mới dự kiến | **Ước tính** | Mô phỏng theo giả định bên dưới |
| Xu hướng học | Lượt trả lời theo ngày (14) / tuần (8, bắt đầu thứ Hai) / tháng (6) | Thực tế | Khối 15 phút từ SQL, quy ra ngày địa phương rồi gộp; khoảng trống = 0 |
| Khối lượng 7 ngày qua | Lượt trả lời theo loại | Thực tế | `GROUP BY item_type` trong 7 ngày gần nhất (gồm hôm nay) |
| Dự báo tiến độ | Số ngày để bắt đầu hết các mục chưa học | **Ước tính** | Xem "Dự báo tiến độ" |
| Phiên ôn gần đây | Đã trả lời / dự kiến (%) | Thực tế | Bảng `review_sessions` (5 phiên gần nhất) |

## Dự báo khối lượng (ngày mai, 7, 14, 30 ngày)

- **Khoảng**: từ **0:00 ngày mai** đến 0:00 của ngày thứ N+1 (giờ địa phương). "Ngày mai" = 1 ngày. Mục đang đến hạn hôm nay **không** nằm trong "Đã lên lịch" (app ghi rõ số này bên dưới bảng).
- **Đã lên lịch** chỉ đếm lịch đang lưu, không giả định gì. Ôn hay đổi dãy bậc sẽ làm lịch, và do đó con số, thay đổi.
- **Giả định của phần ước tính** (hiện trong app):
  1. Mọi mục được ôn đúng thời điểm đến hạn; mục quá hạn được ôn ngay bây giờ.
  2. Mỗi lần ôn đều chọn **Tốt**, nên mục lên một bậc theo **dãy bậc hiện tại** của người dùng (bậc cuối giữ nguyên).
  3. Mỗi ngày học đúng *giới hạn mục mới* (tính cả hôm nay, bất kể hôm nay đã học bao nhiêu) cho tới khi hết mục chưa học; mục mới được trả lời Tốt (bậc 2) rồi theo dãy bậc.
  4. Không tính giới hạn lượt ôn mỗi ngày, không tính giờ trong ngày.
- Vì vậy ước tính là **mức trần hợp lý khi học đều**, không phải dự đoán hành vi. Quên/Khó làm số lượt ôn thực tế cao hơn; bỏ ngày làm dồn việc.

## Dự báo tiến độ

- Chỉ ước tính thời gian **bắt đầu học** (trả lời lần đầu) hết các mục còn lại, **không** phải thành thạo và **không** dự đoán kết quả thi JLPT N3.
- Tốc độ = tổng mục mới trong **28 ngày** gần nhất ÷ 28 (tính cả ngày nghỉ).
- Cần **≥ 7 ngày học** và **≥ 20 mục mới** trong 28 ngày; thiếu thì báo "Chưa đủ dữ liệu" kèm số hiện có, và chỉ hiện phép tính theo cài đặt: ⌈còn lại ÷ giới hạn mục mới⌉ ngày.
- Khoảng dao động: theo tuần nhanh nhất và chậm nhất trong 4 tuần; nếu có tuần không học mục mới nào thì cận trên là "không xác định".
- Độ tin cậy: Cao (≥ 20 ngày học và hệ số biến thiên theo tuần ≤ 0,25), Trung bình (≥ 12 ngày và ≤ 0,6), còn lại Thấp.
- Không còn mục chưa học → "Bạn đã bắt đầu học tất cả các mục hiện có".

## Lịch sử phiên ôn

Bảng `review_sessions` (database v4): khi bắt đầu phiên ôn theo lịch, app ghi số thẻ dự kiến; mỗi câu trả lời cập nhật số thẻ đã trả lời (lấy `MAX`, nên ghi lặp không làm tăng); hết hàng đợi thì đánh dấu hoàn thành. Phiên bỏ dở vẫn giữ tỷ lệ đã làm. Lỗi ghi lịch sử phiên không ảnh hưởng việc ghi câu trả lời. Chế độ luyện tập không tạo phiên.

## Kiểm thử

| Nội dung | Test |
|---|---|
| Lịch đã biết cho ngày mai / 7 / 14 / 30 ngày (đếm tay), database rỗng, lịch bị đổi (Quên), đổi dãy bậc, ranh giới nửa đêm, cuối tháng và đổi giờ mùa hè | `WorkloadForecasterTest` (JVM) |
| Không còn mục, ít dữ liệu, tốc độ đều/không đều, tuần bắt đầu thứ Hai, theo tháng, mục tiêu hôm nay | `ProgressAndTrendTest` (JVM) |
| ViewModel: database rỗng, số liệu từ dữ liệu thật và cài đặt, đủ lịch sử → ước tính, đổi đơn vị xu hướng | `HomeViewModelTest` (JVM) |
| Ghi phiên: dự kiến, đã trả lời, hoàn thành, bỏ dở | `ReviewSessionViewModelTest` (JVM) |
| Truy vấn mới trên Room thật: theo loại, lịch, ưu tiên (thứ tự, cùng hạn), loại seed | `AnalyticsRepositoryTest` (emulator) |
| Migration 3 → 4 giữ dữ liệu, ghi phiên được | `N3DatabaseMigrationTest` (emulator) |
| Màn hình hiển thị đúng số đếm tay của `AnalyticsFixture`, đổi Ngày → Tuần, lịch sử phiên, không có lịch sử | `DashboardEndToEndTest` (emulator) |
