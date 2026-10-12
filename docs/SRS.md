# Engine ôn tập cách quãng (SRS)

Mã nguồn: `domain/srs/` (Kotlin thuần, không phụ thuộc Android). Room chỉ lưu kết quả; thuật toán không nằm trong DAO hay Composable.

| Thành phần | Vai trò |
|---|---|
| `ReviewInterval` | Một khoảng ôn (số + đơn vị) và cách cộng vào một thời điểm |
| `ReviewLadder` | Dãy bậc khoảng ôn (cấu hình được), kiểm tra hợp lệ |
| `SpacedRepetitionScheduler` | Quy tắc đánh giá → bậc mới, trạng thái, hạn ôn, bản ghi lịch sử |
| `ReviewTime` | Ranh giới ngày theo giờ địa phương; đến hạn / quá hạn |
| `ReviewQueuePlanner`, `DailyLimits` | Lập phiên ôn theo giới hạn mỗi ngày |
| `SubmitReviewUseCase` | Ghi một câu trả lời (gọi scheduler bên trong transaction của repository) |

## Dãy bậc mặc định

| Bậc | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| Khoảng | 20 phút | 1 giờ | 1 ngày | 2 ngày | 1 tuần | 2 tuần | 1 tháng |
| Trạng thái | Đang học | Đang học | Đang ôn | Đang ôn | Đang ôn | Đang ôn | Thành thạo |

Cấu hình trong **Cài đặt → Ôn tập**: 1–12 bậc, tăng dần, mỗi bậc tối đa 1 năm; đơn vị phút, giờ, ngày, tuần, tháng. Lưu trong DataStore dạng `20m,1h,1d,2d,1w,2w,1mo`; giá trị hỏng thì dùng mặc định.

## Quy tắc thời gian

- **Phút, giờ**: thời gian trôi qua thật (1 giờ luôn là 60 phút).
- **Ngày, tuần, tháng**: theo lịch, theo múi giờ hiện tại của máy. "1 ngày" = cùng giờ ngày hôm sau (kể cả ngày đổi giờ mùa hè dài 23/25 giờ).
- **1 tháng là tháng theo lịch**, không phải 30 ngày: cùng ngày tháng sau, kẹp về ngày cuối tháng (31/1 + 1 tháng = 28/2, năm nhuận 29/2).
- Mọi thời điểm lưu dạng UTC (epoch millis). Đổi múi giờ **không** dời hạn ôn đã lưu, chỉ đổi ranh giới "hôm nay".
- Đồng hồ (`DeviceClock`) đọc lại múi giờ ở mỗi lần dùng; màn hình Ôn tập, Trang chủ, Thống kê, chi tiết mục tự tính lại mỗi phút, nên đổi giờ hệ thống hoặc múi giờ có hiệu lực mà không cần khởi động lại.
- Hạn ôn mới luôn tính từ **thời điểm trả lời**. Nếu đồng hồ bị lùi (thời điểm trả lời sớm hơn lần ôn trước), hạn ôn vẫn tính từ thời điểm đó; không có giá trị âm, không mất dữ liệu.

## Quy tắc đánh giá

| Đánh giá | Mục mới | Mục đang ở bậc *s* |
|---|---|---|
| **Quên** | bậc 1 | bậc 1; tính 1 lần quên (`lapse_count`) nếu mục đang ở trạng thái Đang ôn/Thành thạo |
| **Khó** | bậc 1 | giữ bậc *s* |
| **Tốt** | bậc 2 | *s* + 1 |
| **Dễ** | bậc 3 | *s* + 2 |

- Bậc không vượt quá bậc cuối.
- Hạn ôn = khoảng của bậc mới cộng vào thời điểm trả lời.
- Trạng thái: bậc cuối → **Thành thạo**; từ bậc đầu tiên ≥ 1 ngày → **Đang ôn**; còn lại → **Đang học**. Mục chưa có tiến độ → **Mới**.
- "Bậc ôn" hiển thị là số thứ tự bậc (vd. 3/7). Đây là vị trí trên dãy khoảng ôn, không phải phép đo trí nhớ, nên app không gọi nó là "mức ghi nhớ".
- Mục Thành thạo vẫn được ôn khi đến hạn; trả lời Quên sẽ đưa về bậc 1.

Nút đánh giá trong phiên ôn hiển thị trước khoảng ôn sẽ nhận (vd. "Tốt · 1 ngày"), dùng cùng hàm của scheduler.

## Đến hạn, quá hạn và cách đếm

- **Đến hạn**: `due_at ≤ bây giờ`. **Quá hạn**: đến hạn từ trước 0:00 hôm nay (giờ địa phương), là một phần của số đến hạn.
- Số mục đến hạn = tổng các mục (từ vựng + Kanji + ngữ pháp) có tiến độ đến hạn **và nội dung còn tồn tại**; mỗi mục tính 1 lần, kèm phân loại theo kiểu. Tiến độ của mục đã bị gỡ khỏi nội dung được giữ lại nhưng không được đếm.
- **Giới hạn mỗi ngày** (mặc định 20 mục mới, 200 lượt ôn; tính theo ngày địa phương):
  - lần trả lời đầu tiên của một mục mới (`was_new = 1`) tính vào giới hạn mục mới;
  - mọi lần trả lời mục đã có tiến độ (kể cả lặp lại sau Quên) tính 1 lượt ôn.
- **Phiên ôn**: mục đến hạn trước (đến hạn lâu nhất trước, trộn mọi loại), sau đó mục mới theo thứ tự giáo trình (gói → bài → từ vựng, Kanji, ngữ pháp → vị trí), trong phần giới hạn còn lại. Hàng đợi cố định khi bắt đầu phiên; mục đến hạn trong lúc đang ôn (vd. vừa Quên, đến hạn sau 20 phút) sẽ có ở phiên sau.

## Ghi kết quả nhất quán, không ghi trùng

- Mỗi thẻ trong phiên có một `requestId` (UUID) cố định.
- `ProgressDao.applyReview` chạy trong **một transaction**: kiểm tra `requestId` → đọc tiến độ hiện tại → scheduler tính → ghi `review_records` + `learning_progress`. Cùng `requestId` lần hai → không ghi gì (`SubmitOutcome.DUPLICATE`). Chỉ mục `request_id` là UNIQUE để chặn cả khi hai transaction chạy song song.
- ViewModel khóa nút đánh giá trong lúc đang ghi; nếu ghi lỗi, thử lại dùng **cùng** `requestId` nên không thể tạo bản ghi thứ hai.
- Khởi động lại app: tiến độ và lịch sử đọc lại từ Room; câu trả lời đã lưu trước khi app bị tắt không bị ghi lại.

## Đổi cấu hình khoảng ôn

- Hạn ôn đã lên lịch **giữ nguyên**; dãy bậc mới áp dụng từ lần trả lời tiếp theo của từng mục.
- Mục giữ số bậc đã lưu; nếu dãy bậc bị rút ngắn, bậc được kẹp về bậc cuối (khi đó mục ở trạng thái Thành thạo sau lần trả lời kế tiếp nếu không Quên).
- Giới hạn mỗi ngày mới có hiệu lực ngay cho phiên ôn tiếp theo.

## Ví dụ lịch ôn trước/sau

Múi giờ Asia/Ho_Chi_Minh, dãy bậc mặc định. Đây chính là test `SpacedRepetitionSchedulerTest.consecutiveReviews_followTheLadder`.

| Lần | Thời điểm trả lời | Trước (bậc · trạng thái) | Đánh giá | Sau (bậc · khoảng) | Hạn ôn tiếp |
|---|---|---|---|---|---|
| 1 | 11/10/2026 08:00 | Mới | Tốt | 2 · 1 giờ | 11/10 09:00 |
| 2 | 11/10 09:05 | 2 · Đang học | Tốt | 3 · 1 ngày | 12/10 09:05 |
| 3 | 12/10 09:30 | 3 · Đang ôn | Tốt | 4 · 2 ngày | 14/10 09:30 |
| 4 | 14/10 20:00 | 4 · Đang ôn | Khó | 4 · 2 ngày | 16/10 20:00 |
| 5 | 16/10 21:00 | 4 · Đang ôn | Dễ | 6 · 2 tuần | 30/10 21:00 |
| 6 | 30/10 22:00 | 6 · Đang ôn | Quên (1 lần quên) | 1 · 20 phút | 30/10 22:20 |
| 7 | 30/10 22:25 | 1 · Đang học | Tốt | 2 · 1 giờ | 30/10 23:25 |
| 8 | 30/10 23:30 | 2 · Đang học | Tốt | 3 · 1 ngày | 31/10 23:30 |
| 9 | 01/11 07:00 | 3 · Đang ôn | Dễ | 5 · 1 tuần | 08/11 07:00 |
| 10 | 08/11 07:10 | 5 · Đang ôn | Tốt | 6 · 2 tuần | 22/11 07:10 |
| 11 | 22/11 07:20 | 6 · Đang ôn | Tốt | 7 · 1 tháng | 22/12 07:20 (Thành thạo) |
| 12 | 22/12 07:30 | 7 · Thành thạo | Tốt | 7 · 1 tháng | 22/01/2027 07:30 |

(Bậc ghi theo số thứ tự 1–7; trong code `stepIndex` bắt đầu từ 0.)

Ví dụ khác (cũng có test): mục Thành thạo trả lời ngày 31/01/2027 10:00 → hạn 28/02/2027 10:00; ở Berlin, trả lời 24/10/2026 09:00 với khoảng 1 ngày → hạn 25/10 09:00 (25 giờ sau, do đổi giờ).
