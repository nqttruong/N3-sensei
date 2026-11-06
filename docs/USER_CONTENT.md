# Chỉnh sửa của người dùng và "Từ của tôi" (schema v5)

## Hai loại thay đổi

| Thao tác | Lưu ở đâu | Khi gói được cập nhật |
|---|---|---|
| Sửa một Kanji hoặc một từ **của gói** | `user_content_edits` (mỗi trường đã sửa một dòng) | Gói ghi lại nội dung gốc, rồi chỉnh sửa được áp lại trong cùng transaction |
| Thêm từ | Gói riêng `user-words` "Từ của tôi" (bài `user-words-l1`, ID `user-w-<UUID>`) | Không bị đụng tới: không gói nào được dùng `pack.id` này |
| Sửa / xóa từ của tôi | Sửa trực tiếp bản ghi đó (không qua `user_content_edits`) | — |

Tệp JSON của gói không bao giờ bị sửa.

## Bảng `user_content_edits` (migration 4 → 5, chỉ tạo bảng mới)

| Cột | Ý nghĩa |
|---|---|
| `item_type`, `item_id` | Mục được sửa, cùng khóa với tiến độ học (`KANJI`/`VOCABULARY` + ID của gói) |
| `field` | Trường: Kanji `meaningVi`, `hanViet`, `onReadings`, `kunReadings`, `strokeCount`, `note`; từ `reading`, `meaningVi`, `partOfSpeech`, `note`, `furigana` (ẩn) |
| `value` | Giá trị người dùng. `""` = **chủ động để trống**; không có dòng = **chưa sửa** |
| `base_value` | Giá trị của gói lúc áp chỉnh sửa gần nhất (để khôi phục và đối chiếu) |
| `created_at`, `updated_at` | Thời điểm |

Khóa chính `(item_type, item_id, field)`: lưu lại cùng giá trị không tạo dòng mới; đặt lại đúng giá trị của gói thì xóa dòng.

Cách áp: bản ghi nội dung (`kanji`, `vocabulary`) giữ giá trị đã hợp nhất, nên danh sách, tìm kiếm, ôn tập, nhắc học đọc đúng giá trị mới mà không đổi truy vấn. `ContentPackDao.replacePack` sau khi ghi gói: với mỗi chỉnh sửa của mục trong gói, cập nhật `base_value` theo gói mới rồi ghi `value` lên bản ghi. Không sửa được chữ Hán của Kanji hay chữ viết của từ trong gói (đó là danh tính của mục). Đổi cách đọc một từ của gói sẽ ẩn furigana của gói (không còn khớp); khôi phục sẽ đưa lại.

Mục biến mất khỏi gói mới: chỉnh sửa **được giữ** (mồ côi, `countOrphanEdits()`), không lỗi, không hiện; nếu mục quay lại ở bản sau, chỉnh sửa áp lại.

## Giao diện

- Chi tiết Kanji / Từ vựng → **Sửa**: form theo các trường trên, kiểm tra dữ liệu (bắt buộc, kana, số nét 1–40, độ dài), hiện "Bản gốc: …" cho trường đã sửa, **Khôi phục bản gốc** (có hỏi lại). Lỗi lưu hiện thông báo, dữ liệu cũ giữ nguyên.
- Học → Từ vựng → **Thêm từ của tôi**: từ, cách đọc, nghĩa, từ loại, ghi chú. Từ trùng chữ viết + cách đọc với một từ đã có (mọi gói) bị từ chối, có nêu tên gói. Từ của tôi có nút **Xóa từ này** (xóa cả lịch ôn và đánh dấu; lịch sử ôn giữ lại).

## Bookmark của "Dịch nhanh" (schema v14)

- Bấm bookmark trong hộp thoại Dịch nhanh lưu **một** từ vào "Từ của tôi" cho cả mục, kèm mọi bản dịch đang có (Anh và Nhật cùng một mục ở chế độ "Cả hai"). Các bản dịch nằm ở `user_word_translations`.
- **Định danh** (`user_words.source_language` + `source_key`, UNIQUE): văn bản đã chuẩn hóa, thêm cách đọc khi từ tiếng Nhật có đúng một cách đọc (生 なま ≠ 生 せい).
  - Lưu lại chỉ bổ sung bản dịch, nghĩa hoặc ví dụ còn thiếu; không tạo từ thứ hai, không ghi đè chỉnh sửa của người dùng, không xóa.
  - Ghi trong một transaction: lỗi giữa chừng thì không còn gì sót lại.
- **Không gán giá trị giả**: từ tiếng Anh/Việt có `reading` rỗng (không kana giả), `jlpt_level` rỗng (không gán "N3"). Form sửa chỉ bắt buộc kana cho từ có chữ Nhật **đang học**; mục "Đã lưu" (ví dụ 実験 do máy dịch, không biết cách đọc) sửa được mà không cần kana.
- **Chữ đã chọn luôn còn**: bookmark một từ tiếng Anh có bản dịch Nhật thì mục đứng tên bằng chữ Nhật, còn chữ tiếng Anh được lưu thành bản dịch tiếng Anh (thấy ở chi tiết, tìm được).
- **Đã lưu (`SAVED`) và Đang học (`STUDYING`)**:
  - Từ thêm tay, và mọi từ có trước v14, là `STUDYING`, giữ nguyên hành vi cũ.
  - Bookmark là `SAVED`: có trong danh sách và tìm kiếm "Từ của tôi" (nhãn "Đã lưu"), xem, sửa và xóa được.
  - Từ `SAVED` **không** có trong:
    - thẻ mới của phiên ôn (`ProgressDao.getNewRefs`), số "còn mới" (`observeNewAvailable`), thống kê theo loại (`AnalyticsDao.observeStatusByType`);
    - nhắc học (`ReminderLessonCatalog`), widget, luyện tập/flashcard (các truy vấn `ItemQuery` mặc định `includeSaved = false`).
  - Màn chi tiết của từ `SAVED` không có "Cần học lại" và "Luyện bài này". Bookmark không tạo tiến độ, đánh dấu, lịch sử ôn hay lịch sử nhắc học.
  - Chức năng "Đưa vào học" (`SAVED` → `STUDYING`) chưa có: cần đặc tả riêng. Dữ liệu không có ràng buộc nào cản trở việc đó.

## Giới hạn hiện tại

- Từ người dùng thêm **không liên kết được với một Kanji**: schema không có quan hệ Từ vựng ↔ Kanji (cần thêm một bảng nếu muốn).
- Sao lưu/khôi phục (Cài đặt) **chưa chứa** chỉnh sửa và "Từ của tôi" (kể cả bookmark và bản dịch của chúng): các dữ liệu này còn khi cập nhật app nhưng mất nếu gỡ app.
