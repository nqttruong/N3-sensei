# Kiểm toán Kanji – Vocabulary (27/10/2026)

Phạm vi: 4 gói JSON trong `app/src/main/assets/content/`, các script `tools/content/*`, `N3.pdf`, Entity/DAO/Repository/ViewModel/màn hình Kanji và Từ vựng, Room schema (`N3Database.VERSION = 4`, migration 1→2→3→4, schema JSON 1–4).

## Số liệu trước khi sửa

| Gói | Trạng thái Git | Từ vựng | Kanji | Ngữ pháp | Câu ví dụ |
|---|---|---|---|---|---|
| `n3sensei-illustration` | đã commit | 30 | 27 | 7 | 44 |
| `mimikara-n3-vocab` | đã commit | 880 | 0 | 0 | 0 |
| `n3-grammar` v2 | chưa commit | 0 | 0 | 174 | 220 |
| `n3-kanji` | **chưa commit** | **1.080** | 392 | 0 | 0 |

Từ đi kèm các nhóm Kanji trong PDF: 1.272 (1.080 thành bản ghi từ vựng mới của gói `n3-kanji`, 144 gắn vào bản ghi Mimikara có sẵn, 48 gắn vào bản ghi đã tạo ở nhóm trước).

ID: 0 ID trùng trong cùng miền, 0 ID trùng giữa các miền/gói. Tiến độ, lịch sử ôn và đánh dấu khóa theo `(item_type, item_id)` nên một ID giống nhau ở hai miền cũng không thể lẫn tiến độ.

## Phát hiện

| # | Phát hiện | Mức chắc chắn | Nguồn gây ra |
|---|---|---|---|
| 1 | **Từ minh họa Kanji bị ghi vào miền Từ vựng**: 1.080 từ của các nhóm Kanji là bản ghi `vocabulary` (gói `n3-kanji`), nên xuất hiện ở Học → Từ vựng, tìm kiếm từ vựng, ôn tập SRS dạng từ vựng, nhắc học/widget. Đây là lựa chọn thiết kế ở bước trước (chưa commit, chưa phát hành), không phải lỗi bộ nhập. | Chắc chắn | `tools/content/n3_kanji/build_pack.py` (`lesson["vocabulary"]`) |
| 2 | **Nhóm Kanji phụ thuộc bản ghi Từ vựng**: 144 từ của nhóm Kanji không có bản ghi riêng mà trỏ tới bản ghi Mimikara (cùng chữ viết + cách đọc), 9 trong đó nghĩa diễn đạt khác. | Chắc chắn | `build_pack.py` (khối `existing` / `LINK_REVIEWED`) |
| 3 | **Màn Kanji đọc bảng Từ vựng**: mục "Từ vựng có chữ này" tìm theo chuỗi con trong bảng `vocabulary` của mọi gói. Có chủ đích, nhưng là truy vấn chéo miền. | Chắc chắn | `ContentDao.getVocabularyContaining`, `KanjiDetailScreen` (chưa commit) |
| 4 | Bộ nhập: mỗi loại ghi vào bảng của nó; cài gói chỉ xóa và ghi lại chính gói đó (`ContentPackDao.replacePack`). Không thấy đường nào ghi Kanji vào bảng từ vựng hay ngược lại. | Chắc chắn | — (không lỗi) |
| 5 | Phần đã commit (`illustration`, `mimikara`): không có dữ liệu Kanji trong gói Mimikara (`note`, `partOfSpeech` đều trống), không có nghĩa chữ Hán nào được bổ sung vào từ vựng. Giả thuyết "bổ sung nghĩa chữ Hán trong phần từ vựng" không có dấu vết trong dữ liệu. | Chắc chắn | — (không lỗi) |
| 6 | 84 từ vựng chỉ có 1 chữ Hán (57 trong `n3-kanji`, 27 trong Mimikara, vd. 皮 かわ, 波 なみ): đều là từ thật có cách đọc kun và nghĩa của từ, không phải bản ghi Kanji. | Chắc chắn | — (hợp lệ) |
| 7 | 32 chữ Hán có nghĩa trùng nghĩa một từ ghép chứa nó (泳 "bơi" / 泳ぐ, 禁 "cấm" / 禁止…): đã rà từng chữ, nghĩa cốt lõi trùng thật; 28 được dịch độc lập từ KANJIDIC2. 14 nghĩa PDF là nghĩa từ ghép đã được thay ở bước trước (伝 "tiểu sử"…). | Cao | — (hợp lệ) |
| 8 | Cùng một chữ Hán ở hai gói (vd. 決, 続 có ở gói minh họa và `n3-kanji`): hai bản ghi khác ID của hai gói. | Chắc chắn | — (trùng hợp lệ) |

## Từ nằm sai nhóm Kanji (7 từ)

| Nhóm (trang) | Từ | Phân loại |
|---|---|---|
| 坂 第11#12–16 (tr. 205) | 木の板, 黒板, 板書, 板前, 看板 | **Lỗi có sẵn trong PDF**: ảnh trang 205 in thẻ 坂 (PHẢN, con dốc) rồi các từ của chữ 板 (BẢN); PDF không có thẻ chữ 板 |
| 伸 第10#2 (tr. 197) | 身長 | **Có sẵn trong PDF** (thứ tự bảng); 身長 cũng có ở nhóm 身 第07#78. Không rõ ý người soạn |
| 州 第16#56 (tr. 238) | 連合 | **Có sẵn trong PDF**; chưa đủ căn cứ để biết nhóm đúng |

Không có trường hợp nào do trích xuất: các hàng được dựng từ đường kẻ bảng và khớp số thẻ ở cả 46 nhóm. Các lỗi trích xuất đã gặp trước đây (狼 bị coi là từ, 贊/劳/戶, ký tự bộ thủ) đã được sửa và có ghi trong `docs/N3_KANJI.md`. Theo yêu cầu, 7 từ trên **giữ nguyên nhóm theo PDF**, có cờ `word-without-its-kanji`, chờ người dùng xác nhận.

## Đề xuất phân tách (không cần thêm bảng)

Schema đã có quan hệ Kanji → câu ví dụ: `kanji_examples(kanji_id, example_id, sort_order)` + `example_sentences(japanese, furigana, translation_vi)`, và màn chi tiết Kanji đã hiển thị phần này. Mỗi từ minh họa của nhóm sẽ là **một mục ví dụ của Kanji** (chữ viết + furigana từ cách đọc + nghĩa), không còn là bản ghi `vocabulary`:

- gói `n3-kanji`: 392 Kanji, 0 từ vựng, 1.272 mục ví dụ (một mục cho mỗi thẻ từ của PDF, ID `n3k-x-<nhóm>-<thẻ>`), không trỏ sang gói khác;
- một từ có thể vừa là từ vựng Mimikara vừa là từ minh họa Kanji (hai bản ghi, hai vai trò);
- bỏ truy vấn chéo miền ở màn Kanji.

**Tác động ID/tiến độ**: 1.080 ID `n3k-w-*` chỉ tồn tại trong bản làm việc chưa commit, chưa từng được phát hành, nên không có tiến độ thật nào gắn với chúng; bỏ đi không ảnh hưởng người học. ID Kanji `n3k-k<mã Unicode>`, ID Mimikara, minh họa, ngữ pháp giữ nguyên.

## Phần F (phương án A)

Schema đang ở **bản 4** → migration **4→5** chỉ thêm bảng `user_content_edits`. "Từ của tôi" dùng các bảng có sẵn (một gói riêng `user-words`), không cần bảng mới. Từ người dùng thêm **không** liên kết được với Kanji vì schema không có quan hệ Từ vựng ↔ Kanji; muốn có cần thêm một bảng quan hệ (chưa làm).

## Sau khi sửa

- `n3-kanji`: 392 Kanji, **0 từ vựng**, 1.272 từ minh họa (`kanji_examples`); không còn liên kết sang Mimikara; ID `n3k-w-*` bỏ (chưa từng phát hành).
- Bỏ truy vấn `getVocabularyContaining` và mục "Từ vựng có chữ này"; màn Kanji chỉ đọc dữ liệu Kanji (mục "Từ minh họa").
- Bảng từ vựng: 30 (minh họa) + 880 (Mimikara) + từ người dùng tự thêm.
- Test: `N3KanjiPackTest`, `N3KanjiImportTest` (nhập Kanji không ghi Từ vựng, nhập Từ vựng không đụng Kanji, từ có ở cả hai miền, nhóm sai được gắn cờ và giữ nguyên, nhập lại không trùng), `EndToEndTest`.
