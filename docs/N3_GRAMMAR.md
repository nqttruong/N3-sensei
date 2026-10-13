# Gói ngữ pháp N3 (110 mẫu, tự soạn theo danh mục Mimikara N3)

Gói `n3-grammar` (`app/src/main/assets/content/n3-grammar.json`) được đóng gói cùng app, tự cài ở lần mở đầu (cạnh gói minh họa và gói từ vựng Mimikara N3). Nguồn danh mục: <https://www.vnjpclub.com/mimi-kara-n3-bunpo/>.

> **Phần lớn nội dung là tự soạn.** Chỉ *thứ tự* và *tên mẫu* (cùng URL từng mục) lấy từ trang danh mục công khai. Nghĩa, cấu trúc, cách dùng, lưu ý và 220 câu ví dụ kèm bản dịch do N3 Sensei viết mới, **chưa qua kiểm duyệt chuyên môn**; không có câu nào chép từ nguồn. Mỗi mục ghi nguồn đối chiếu (URL trang của mục đó) và `licenseVerified = false`.

## Trang đã truy cập (14/10/2026, `curl`, mỗi trang một lần)

| URL | Kết quả |
|---|---|
| `/robots.txt` | 200. Cho phép `/mimi-kara-n3-bunpo/`; chặn URL có `?`, `/wp-json/`, `/wp-admin/` (gồm `admin-ajax.php`), `/feed/` |
| `/mimi-kara-n3-bunpo/` | 200. Danh mục: 110 mục (H2 + liên kết `np-1.html` … `np-110.html`), số thứ tự khớp; **không** chia bài/nhóm |
| `np-1.html`, `np-38.html`, `np-110.html` (mẫu) | 200, nhưng **thân bài không có trong HTML**: chỉ có tiêu đề, thẻ meta mô tả ngắn và nút "Nâng cấp VIP"; nội dung được tải sau bằng JavaScript qua `admin-ajax.php` (bị `robots.txt` chặn, nhiều khả năng cần tài khoản VIP) |

Không gọi `admin-ajax.php`, không đăng nhập, không tải 107 trang chi tiết còn lại (không có thêm dữ liệu công khai nào ngoài mô tả ngắn); không sao chép thẻ meta mô tả.

## Dữ liệu thu được và cách dựng

- `tools/content/n3_grammar/vnjpclub-index.json`: 110 mục `{order, title, url}` (metadata).
- `tools/content/n3_grammar/part1.py` … `part4.py`: nội dung tự soạn cho mục 1–110 (mẫu, cấu trúc, nghĩa, cách dùng, lưu ý, 2 ví dụ + dịch).
- `python3 -I tools/content/n3_grammar/build_pack.py <out.json> [report.json]`: kiểm tra (đủ 110 mục khớp danh mục, đủ trường, 2 ví dụ, câu tiếng Nhật kết thúc đúng, không trùng câu) rồi tạo gói; sao chép vào `app/src/main/assets/content/n3-grammar.json`.

## Ánh xạ sang Room (không đổi schema, không migration)

| Gói | Bảng / cột | Nguồn |
|---|---|---|
| lesson `n3g-lNN`, "Ngữ pháp N3 · mục 1–10"… | `lessons` | 10 mục/bài theo thứ tự danh mục (nguồn không chia bài — đây là cách nhóm của app) |
| grammar `n3g-gNNN` | `grammar.id`, `sort_order` | thứ tự danh mục (ID ổn định cho tiến độ) |
| `pattern` | `grammar.pattern` | tên mẫu trong danh mục, chuẩn hóa (`～`→`〜`, bỏ số thứ tự); 2 mục sửa chính tả, xem dưới |
| `structure`, `meaningVi`, `usage`, `cautionNote` | cột cùng tên | **tự soạn** |
| examples `n3g-gNNN-exK` | `example_sentences` + `grammar_examples` | **tự soạn**; `furigana` để trống |
| source `vnjp-np-NNN` | `content_sources` (`url` = trang của mục) | đối chiếu; `licenseVerified = false` |
| — | `grammar_exercises` | chưa có bài tập |

Tiến độ, lịch ôn, đánh dấu ở bảng riêng, gắn `(GRAMMAR, n3g-gNNN)`: không bị ghi đè khi cập nhật gói.

## Trùng lặp, biến thể, cần xác nhận

| Mục | Danh mục nguồn | Trong app | Ghi chú |
|---|---|---|---|
| 25 / 34 | `～に対し（て）` (hai lần) | hai mục: "đối với…" / "trái với…" | Cùng tên, khác cách dùng: giữ cả hai |
| 61 / 63 | `～ほど` (hai lần) | `〜ほど` "đến mức…" / `〜ほど…ない／〜ほど…はない` "không… bằng…" | Như trên |
| 73 | `～っぱい` | `〜っぽい` | **Cần xác nhận**: nhiều khả năng lỗi chính tả của nguồn; lưu ý trong app ghi rõ |
| 92 | `～に返し（て）` | `〜に反し（て）` | **Cần xác nhận**: như trên |
| 11, 12, 15 | chủ đề (thể sai khiến, tự động từ, dạng rút gọn) | mục chủ đề với vài dạng tiêu biểu | Không phải một mẫu đơn |
| 10, 48, 59… | nhiều dạng gộp | giữ nguyên trong một mục | Không tách |

Báo cáo: 110 mục trong danh mục → 110 mục nhập; 0 thiếu trường; 0 lỗi; 2 cặp trùng tên (giữ cả hai); 2 mục cần xác nhận chính tả; 220 ví dụ.

## Trong app

- **Học → Ngữ pháp**: danh sách từ Room; tìm theo mẫu, cấu trúc, nghĩa (không dấu: "so voi" → 〜に比べ（て）, 〜わりに（は）); chip lọc theo bài chỉ hiện các bài có ngữ pháp (lỗi cũ: hiện cả bài từ vựng — đã sửa, có test).
- **Chi tiết**: cấu trúc, ý nghĩa, cách dùng, lưu ý, ví dụ + dịch, nguồn đối chiếu. Đánh dấu, luyện tập theo bài (flashcard), lịch ôn: dùng chung cơ chế với mọi mục.
- **Lịch ôn**: mục mới vào phiên ôn theo thứ tự giáo trình (gói → bài → từ vựng, Kanji, ngữ pháp); vì gói xếp theo `id`, ngữ pháp `n3-grammar` đến sau 880 từ Mimikara. Học trước bằng **Luyện tập bài này**.

## Kiểm thử

| Test | Nội dung |
|---|---|
| `N3GrammarPackTest` (JVM) | 110 mẫu / 11 bài / 220 ví dụ, không lỗi; thứ tự; hai cặp trùng tên giữ cả hai; 73, 92 có ghi chú; tiếng Nhật/Việt nguyên vẹn; nguồn từng mục, nhãn tự soạn, `licenseVerified = false` |
| `LessonChipsTest` (JVM) | mỗi danh sách chỉ hiện bài có loại mục của nó (thất bại khi bỏ bản sửa) |
| `N3GrammarImportTest` (Room, tệp thật) | nhập, chi tiết + ví dụ + nguồn; danh sách, tìm kiếm (mẫu, nghĩa không dấu), lọc bài; nhập lại giữ tiến độ/đánh dấu, không bản sao; lỗi giữa chừng rollback; đóng/mở lại |
| `EndToEndTest.bundledGrammarPack_isInstalledOnFirstLaunch_andShownInGrammarList` | lần mở đầu: 7 + 110 mẫu, mở chi tiết thấy ví dụ và bản dịch |

## Còn thiếu

Bài tập trắc nghiệm, furigana cho câu ví dụ, kiểm duyệt chuyên môn nội dung tự soạn, cách chia bài đúng theo giáo trình (nguồn không cho biết).
