# Database N3 Sensei

Room 2.6.1, file `n3sensei.db`, schema version **4**. Schema export: `app/schemas/com.n3sensei.app.data.local.N3Database/<version>.json` (commit vào Git).

## Hai nhóm dữ liệu

| Nhóm | Bảng | Ghi bởi | Khi cập nhật nội dung |
|---|---|---|---|
| **Nội dung chuẩn** (dùng chung) | `content_sources`, `content_packs`, `lessons`, `vocabulary`, `kanji`, `grammar`, `grammar_exercises`, `example_sentences`, `*_examples`, `tags`, `vocabulary_tags` | `ContentPackDao.replacePack` (một transaction) | Xóa và ghi lại cả gói |
| **Dữ liệu người học** | `learning_progress`, `review_records`, `item_marks` (đánh dấu cần chú ý), `review_sessions` (lịch sử phiên ôn) | `ProgressDao`, `ReviewSessionDao` | Giữ nguyên |

Bản ghi nội dung không có cột tiến độ. Tiến độ tham chiếu nội dung bằng `(item_type, item_id)`, **không** dùng foreign key, vì nội dung được thay thế khi cập nhật gói. ID nội dung là chuỗi ổn định do gói cung cấp (vd. `n3-l01-v001`), nên tiến độ vẫn khớp sau khi import lại. Truy vấn mục đến hạn chỉ trả về tiến độ có nội dung tồn tại; tiến độ của mục đã bị gỡ được giữ lại (không mất lịch sử) nhưng không hiện ra.

**Cài đặt ứng dụng không lưu trong Room** — sẽ dùng DataStore (Preferences) vì là cặp khóa–giá trị nhỏ, không cần truy vấn hay quan hệ.

## Sơ đồ quan hệ

```
content_sources ◀─RESTRICT─ content_packs ◀─CASCADE─ lessons ◀─CASCADE─ vocabulary ─┐
      ▲  ▲  ▲                     ▲                       ◀─CASCADE─ kanji ──────┤
      │  │  └────── RESTRICT ─────┼───────────────────────◀─CASCADE─ grammar ────┤
      │  │                        └─CASCADE─ example_sentences                   │
      │  └─ RESTRICT (source_id của vocabulary/kanji/grammar/example)            │
                                                                                 │
vocabulary_examples (vocabulary_id, example_id)  ── CASCADE cả hai phía ─────────┤
kanji_examples      (kanji_id, example_id)                                       │
grammar_examples    (grammar_id, example_id)     một câu ví dụ dùng được cho     │
vocabulary_tags     (vocabulary_id, tag_id)      nhiều mục                        │
tags (id, name UNIQUE)                                                           │
                                                                                 │
learning_progress PK(item_type, item_id) ···· tham chiếu logic, không FK ········┘
item_marks        PK(item_type, item_id) ···· (như trên)
grammar_exercises (grammar_id ─CASCADE─▶ grammar), UNIQUE(grammar_id, sort_order)
review_records    id AUTOINCREMENT, (item_type, item_id), reviewed_at, request_id UNIQUE, was_new
review_sessions   id (UUID), started_at, last_activity_at, planned, answered, finished
```

- **RESTRICT** ở `source_id`: không xóa được nguồn còn đang được tham chiếu, nên thông tin nguồn và giấy phép không bị mất.
- **CASCADE** từ gói: xóa gói thì xóa toàn bộ bài, mục, câu ví dụ và liên kết của gói đó.

## Ràng buộc và index

| Bảng | Unique | Index tra cứu |
|---|---|---|
| `lessons` | `(pack_id, number)` | |
| `vocabulary` | `(lesson_id, sort_order)` | `expression`, `reading`, `source_id` |
| `kanji` | `(lesson_id, sort_order)`, `(lesson_id, character)` | `character`, `source_id` |
| `grammar` | `(lesson_id, sort_order)` | `pattern`, `source_id` |
| `tags` | `name` | |
| `learning_progress` | PK `(item_type, item_id)` | `due_at` |
| `review_records` | `request_id` | `(item_type, item_id)`, `reviewed_at` |
| `review_sessions` | PK `id` | `started_at` |

Các cột FK đều có index. Room bật `PRAGMA foreign_keys = ON` khi mở database (đã có test xác nhận vi phạm FK bị từ chối).

## Dữ liệu tiếng Nhật

| Loại | Trường |
|---|---|
| Từ vựng | `expression` (cách viết), `reading` (kana), `furigana`, `meaning_vi`, `part_of_speech`, `jlpt_level`, bài, thẻ, câu ví dụ, nguồn |
| Kanji | `character`, `on_readings`, `kun_readings` (danh sách), `han_viet` (âm Hán Việt), `meaning_vi`, `stroke_count`, ví dụ, bài, nguồn |
| Ngữ pháp | `pattern`, `structure` (cách nối), `meaning_vi`, `usage`, `caution_note` (lưu ý dễ nhầm), ví dụ kèm bản dịch, bài tập trắc nghiệm (`grammar_exercises`), nguồn |
| Câu ví dụ | `japanese`, `furigana`, `translation_vi`, nguồn |
| Nguồn | `name`, `url`, `license`, `attribution`, `license_verified` |

**Định dạng furigana**: `{chữ gốc|cách đọc}` xen với văn bản thường, vd. `{日本|にほん}に{来|き}たばかりです。` Bộ đọc: `domain/furigana/Furigana.kt`. Từ chỉ có kana thì `furigana = NULL`.

**Danh sách** (âm On/Kun) lưu trong một cột, phân tách bằng ký tự ASCII Unit Separator (`U+001F`).

**`license_verified`** chỉ đặt `true` khi quyền phân phối đã được kiểm tra thật. Gói seed là `false`.

## Transaction

- `ContentPackDao.replacePack`: xóa gói cũ rồi ghi gói mới; một dòng lỗi thì toàn bộ thay đổi bị hủy và nội dung cũ được giữ nguyên.
- `ProgressDao.applyReview`: kiểm tra `request_id` → đọc tiến độ → scheduler (domain) tính → ghi lịch sử + tiến độ, trong một transaction; trả lời trùng không ghi gì. Xem [SRS.md](SRS.md).
- `ContentDao.get*Detail`: đọc mục, câu ví dụ và nguồn trong một transaction để dữ liệu nhất quán.

## Tìm kiếm

`vocabulary`, `kanji`, `grammar` có cột `search_text` (do `SearchText` sinh ra khi ghi, không lấy từ gói): chữ thường, bỏ dấu tiếng Việt (kể cả đ → d), katakana → hiragana, **giữ nguyên dấu kana** (が ≠ か). Truy vấn được chuẩn hóa giống hệt rồi so khớp `LIKE '%…%'`. Nhờ vậy "kinh nghiem", "TĂNG", "きょう" (khớp キョウ) đều tìm được.

## Duyệt danh sách

`ContentDao.observe{Vocabulary,Kanji,Grammar}Items` kết hợp bộ lọc bài, chủ đề (từ vựng), trạng thái học (NEW = chưa có dòng `learning_progress`), "đã đánh dấu" và văn bản; trả về mục kèm trạng thái và cờ đánh dấu. Là `Flow` nên danh sách tự cập nhật khi người học đánh dấu.

## Giới hạn kết quả

Danh sách không có văn bản tìm kiếm trả về đầy đủ. Có văn bản thì giới hạn 100 kết quả (giao diện báo khi chạm giới hạn); hàm `search*` một lần dùng `LIMIT/OFFSET` (mặc định 30, tối đa 100). Ký tự `%` và `_` được escape. `LIKE '%…%'` không dùng được index; với vài nghìn mục mỗi gói là đủ nhanh, khi cần có thể chuyển sang FTS.

## Migration

- Không bao giờ dùng `fallbackToDestructiveMigration`.
- Quy trình đổi schema: tăng `N3Database.VERSION`, build để export JSON mới, thêm `Migration` vào `N3Migrations.ALL`, rồi commit JSON.
- `N3DatabaseMigrationTest` chạy thử migration từ **mọi** version đã export lên version hiện tại, kiểm tra khớp schema, và kiểm tra dữ liệu ghi bằng schema v1 vẫn đọc được qua Room.
- **1 → 2** (`N3Migrations.MIGRATION_1_2`): tạo `item_marks`, `grammar_exercises`; thêm cột `search_text` vào `vocabulary`, `kanji`, `grammar` và **tính lại cho mọi dòng đã có** bằng chính `SearchText` của app. Đã kiểm tra bằng test: dữ liệu ghi theo schema v1 (nội dung + tiến độ) còn nguyên sau migration, tìm kiếm không dấu tìm thấy dòng cũ, bảng đánh dấu dùng được; tắt bước tính lại thì test thất bại.
- **2 → 3** (`MIGRATION_2_3`): `review_records` thêm `request_id` (UNIQUE, NULL cho dòng cũ) và `was_new` (tính lại: bản ghi sớm nhất của mỗi mục = 1). Đã kiểm tra bằng test với lịch sử ghi theo schema v2.
- **3 → 4** (`MIGRATION_3_4`): tạo bảng `review_sessions` (chỉ thêm, không đổi bảng cũ). Đã kiểm tra bằng test: tiến độ ghi theo schema v3 còn nguyên, ghi và đọc phiên qua Room được.
- Lưu ý: nếu sửa entity mà quên tăng version, Room sẽ ghi đè JSON của version hiện tại. Khi review cần xem diff thư mục `app/schemas/`.

## Nhập nội dung

Xem [CONTENT_FORMAT.md](CONTENT_FORMAT.md). `ContentPackValidator` (Kotlin thuần) chuyển JSON thành các entity và báo cáo; `ContentPackImporter` ghi bằng `replacePack` (một transaction).

## Dữ liệu seed

Chỉ nằm trong mã test (`app/src/androidTest/.../testing/SeedData.kt`) và không có trong app. Gói `seed-test` có `is_seed = 1`, nguồn ghi rõ là dữ liệu tự soạn để kiểm thử.
