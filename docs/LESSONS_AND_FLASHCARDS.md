# Bài học tổng hợp và thẻ ghi nhớ

## Học → Bài học

Không lưu gì mới, không đổi schema: `CourseBuilder` (domain/course) dựng các bài từ nội dung hiện có, mỗi lần nội dung đổi.

- Ba danh sách theo đúng thứ tự của Học → Kanji / Từ vựng / Ngữ pháp (gói, bài, vị trí).
- Bài n = Kanji thứ 24(n−1)+1…24n, từ vựng thứ 30(n−1)+1…30n, ngữ pháp thứ 7(n−1)+1…7n. Bài tiếp tục chừng nào còn một danh sách chưa hết; bài cuối của mỗi miền có thể ít hơn; không tạo mục giả, không bỏ mục, không trùng.
- Không ghép theo chủ đề; không sắp xếp lại.
- "Từ của tôi" không vào bài học (thay đổi bất cứ lúc nào, sẽ làm lệch mọi bài sau).
- Từ đi kèm Kanji nằm trong phần Kanji (mở rộng từng chữ), không tính vào 30 từ vựng.
- Tiến độ, lịch ôn gắn với ID của từng mục; bài chỉ là cách nhóm.

Với các gói đi kèm hiện tại: 420 Kanji (393 + 27 minh họa), 910 từ (880 + 30), 181 mẫu (174 + 7) → **31 bài**: Kanji 24 × 17 + 12 (bài 18), từ vựng 30 × 30 + 10 (bài 31), ngữ pháp 7 × 25 + 6 (bài 26).

**Nhóm con từ vựng**: nhóm nguồn (bài của gói từ vựng) trên 30 từ chia theo thứ tự thành n.1 (1–30), n.2 (31–60)…; nhóm ≤ 30 từ giữ nhãn "n". Một bài có thể có hai nhóm con (vd. "3.3" 18 từ rồi "4.1" 12 từ). Đây chỉ là nhãn hiển thị, không phải ID.

**Chi tiết bài**: ba tab Kanji / Từ vựng / Ngữ pháp (số lượng thật), chuyển tự do, mỗi tab giữ vị trí cuộn; mở rộng/thu gọn không ghi gì. "Luyện tập bài này" luyện đúng các mục của bài.

Màn Học → Kanji / Từ vựng / Ngữ pháp và chi tiết từng mục không đổi.

## Thẻ ghi nhớ

- **Kanji**: mặt sau có "Từ đi kèm (n)" — mọi từ minh họa của chữ (`kanji_examples`) rồi mọi từ vựng người dùng liên kết (`vocabulary_kanji_links`), không giới hạn số lượng. Phần thẻ cuộn được, nút đánh giá luôn ở dưới.
- **Thời gian trên nút**: tên mức đánh giá và khoảng ôn (dòng nhỏ). Tính bằng `SpacedRepetitionScheduler.preview`, cùng hàm mà phiên Ôn tập dùng để lưu (`schedule`), từ tiến độ đã lưu của mục và chuỗi khoảng ôn trong cài đặt (đổi cài đặt → nhãn đổi). Luyện tập vẫn không ghi lịch ôn; nhãn là lịch mà mục sẽ có nếu trả lời như vậy trong Ôn tập.
