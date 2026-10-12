# Trải nghiệm học và trợ năng (Prompt 12)

Kiểm tra trên emulator Pixel 8 (Android 16), bản debug: màn hình mặc định 1080×2400 @ 420 dpi và màn hình nhỏ 720×1280 @ 320 dpi (360 dp ngang); cỡ chữ 1,0 / 1,3 / 1,5 / 2,0; sáng và tối. **Chưa thử với TalkBack bật thật trên thiết bị**: phần TalkBack được kiểm bằng cây trợ năng (`AccessibilityNodeInfo`) mà TalkBack nhận.

## Vấn đề tìm thấy và đã sửa

| # | Vấn đề (cách tái hiện) | Nguyên nhân | Sửa | Test hồi quy |
|---|---|---|---|---|
| 1 | Cỡ chữ 2,0, màn hình nhỏ: nhãn tab ngắt giữa từ ("Tran / g chủ"), thanh tab chiếm ≈ ¼ màn hình, nút chính của tab Ôn tập bị đẩy xuống dưới | Nhãn 5 tab tự xuống dòng không giới hạn | Nhãn tab một dòng, phóng theo cỡ chữ tới 1,3× rồi dừng, cắt bằng "…"; TalkBack vẫn đọc đủ tên | Ảnh chụp trước/sau |
| 2 | Cỡ chữ 2,0: trong phiên ôn **không thấy từ cần học** (thẻ bị ép về gần 0), nhãn 4 nút đánh giá bị cắt ("Quên 20 ph…") | Thẻ chỉ nhận phần chiều cao còn lại sau banner, tiến độ và nút; 4 nút luôn chung một hàng | Phần trên nút cuộn được, thẻ có chiều cao tối thiểu; nút đánh giá cố định ở đáy và chuyển sang 2 × 2 khi một nút hẹp hơn nhãn cần | `LearningUxTest.ratingButtons_*` (2 hàng ở 2,0×, 1 hàng ở 1,0×) |
| 3 | TalkBack đọc từ tiếng Nhật (経験) bằng giọng tiếng Việt | Ngôn ngữ chỉ gắn ở kiểu chữ (`TextStyle.localeList`), không đi vào văn bản trợ năng | `japaneseText()` gắn `LocaleSpan` ja vào chính văn bản tiếng Nhật (thẻ, chi tiết, ví dụ có furigana, danh sách, Trang chủ, Thống kê) | `AccessibilityTest` (đọc `AccessibilityNodeInfo` thật: 経験 có locale `ja`, "kinh nghiệm" không). Đã xác nhận test thất bại với mã cũ |
| 4 | Một cụm furigana rộng hơn màn hình (từ dài ghi một cách đọc, chữ lớn) bị **cắt ở mép màn hình** | Mỗi cụm bị ép một dòng (`softWrap = false`) | Cụm vẫn giữ nguyên khi vừa một dòng; cụm quá rộng xuống dòng trong ô của nó | `FuriganaTextTest` (trước: cao bằng 1 dòng = bị cắt; sau: xuống dòng) |
| 5 | Nhập gói nội dung **thay thế ngay** bản đang cài, có thể gỡ nhiều mục mà không hỏi | Nhập ghi thẳng sau khi chọn tệp | Kiểm tra trước (không ghi); gói thay thế bản đang cài cần xác nhận, hộp thoại nêu số mục bị gỡ và số mục đã học trong đó | `SettingsViewModelTest` (chờ xác nhận, hủy không ghi), `ContentPackImporterTest.check_*` (Room thật, không ghi khi kiểm tra) |

## Cải tiến trải nghiệm học

- **Bắt đầu nhanh hơn**: nút ở Trang chủ ghi số thẻ ("Bắt đầu ôn tập (7)") và vào thẳng phiên ôn (1 chạm thay vì 2). Khi không có gì để ôn, nút thành "Xem mục Ôn tập". Số thẻ tính cùng quy tắc với hàng đợi phiên ôn (`DailyGoal.sessionSize`).
- **Vì sao mục này đến hạn**: mỗi thẻ trong phiên ôn có một dòng "Mục mới: lần đầu học mục này", "Đến hạn lúc 08:00 12/10 · bậc 3/7" hoặc "Quá hạn từ … · bậc …", kèm "đã quên N lần" nếu có (`CardReason`, có test).
- **Tập trung khi học**: ẩn thanh tab trong phiên ôn/luyện tập; ghi chú "câu trả lời được lưu ngay" chỉ hiện ở thẻ đầu; app không có hiệu ứng động hay rung trong lúc học.
- **Không gây hiểu nhầm về trí nhớ**: "Mức ghi nhớ 2/7" đổi thành "Bậc ôn 2/7" (là vị trí trên dãy khoảng ôn, không đo trí nhớ). Thống kê và dự báo đã ghi nhãn Thực tế/Ước tính từ Prompt 09; app không có điểm, huy hiệu hay xếp hạng.
- **Mục tiêu học**: đã có (Cài đặt → Ôn tập → Mục tiêu hằng ngày).
- **Tìm kiếm**: dòng "Tìm thấy N kết quả" được TalkBack đọc khi kết quả đổi (live region); phím Tìm trên bàn phím ẩn bàn phím; nút xóa có mô tả.

## Đã kiểm tra, không cần sửa

| Hạng mục | Kết quả |
|---|---|
| Vùng chạm | Dò cây giao diện 11 màn hình (giới thiệu, Trang chủ, Học, danh sách và chi tiết từ, chi tiết Kanji, Ôn tập, Thống kê, Cài đặt, Cài đặt ôn tập, Nhắc học): không có thành phần tương tác nào < 48 dp hay không có nhãn (trừ phần tử bị cắt ở mép khi cuộn) |
| Độ tương phản | `ColorContrastTest`: 12 cặp chữ/nền đang dùng, cả theme sáng và tối, đều ≥ 4,5:1 (WCAG AA) |
| Tiêu đề màn hình, thẻ trên Trang chủ | Đánh dấu heading; biểu đồ có mô tả bằng chữ; icon tab không mô tả riêng (nhãn tab đã được đọc) |
| Loading / empty / error | Mọi màn hình dùng `UiStateContent`; lỗi được đọc qua live region; phiên ôn có nút Thử lại |
| Chế độ tối | Ảnh chụp: thẻ, Trang chủ, phiên ôn, chi tiết từ |
| Nội dung dài | Nghĩa tiếng Việt dài, câu ví dụ tiếng Nhật dài có furigana, mẫu ngữ pháp dài ở cỡ chữ 1,5×: xuống dòng, không chồng lấn |
| Hiệu ứng | Không có animation trong lúc học (chỉ cuộn trang ở phần giới thiệu) |
| Xác nhận hành động lớn | Khôi phục sao lưu (có xem trước), thay gói nội dung (mới). Các thay đổi cài đặt ôn tập không xóa dữ liệu và áp dụng từ lần trả lời sau |

## Còn cần kiểm tra trên thiết bị thật

1. **TalkBack thật**: đọc từ tiếng Nhật bằng giọng Nhật (cần cài giọng tiếng Nhật), thứ tự đọc trên Trang chủ, thông báo "Tìm thấy N kết quả", hộp thoại khôi phục/thay gói.
2. **Switch Access / bàn phím ngoài**: thứ tự focus trong phiên ôn và các hộp thoại.
3. Cỡ chữ và cỡ hiển thị lớn nhất của từng hãng (Samsung, Xiaomi có giới hạn khác), màn hình ≤ 360 dp, máy gập/tablet.
4. Font tiếng Nhật của hãng (một số máy thiếu Noto Sans CJK hoặc dùng font khác ảnh hưởng chiều cao furigana).
5. Màn hình xoay ngang trong phiên ôn (bố cục đã cuộn được nhưng chưa tối ưu cho ngang).

## Chưa làm (ghi nhận)

- Màn hình dùng luồng dữ liệu (Trang chủ, danh sách, Thống kê) báo lỗi nhưng không có nút Thử lại; lỗi đọc Room hiếm khi xảy ra và chưa tái hiện được. Có thể thêm cơ chế đăng ký lại luồng ở một nhiệm vụ sau.
- Nhãn tab bị cắt bằng "…" ở cỡ chữ rất lớn trên màn hình hẹp (đánh đổi để giữ nội dung đủ chỗ); TalkBack vẫn đọc đủ.
