# Database N3 Sensei

Room 2.6.1, file `n3sensei.db`, schema version **1**. Schema export: `app/schemas/com.n3sensei.app.data.local.N3Database/<version>.json` (commit vào Git).

## Hai nhóm dữ liệu

| Nhóm | Bảng | Ghi bởi | Khi cập nhật nội dung |
|---|---|---|---|
| **Nội dung chuẩn** (dùng chung) | `content_sources`, `content_packs`, `lessons`, `vocabulary`, `kanji`, `grammar`, `example_sentences`, `*_examples`, `tags`, `vocabulary_tags` | `ContentPackDao.replacePack` (một transaction) | Xóa và ghi lại cả gói |
| **Dữ liệu người học** | `learning_progress`, `review_records` | `ProgressDao` | Giữ nguyên |

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
review_records    id AUTOINCREMENT, (item_type, item_id), reviewed_at
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
| `review_records` | | `(item_type, item_id)`, `reviewed_at` |

Các cột FK đều có index. Room bật `PRAGMA foreign_keys = ON` khi mở database (đã có test xác nhận vi phạm FK bị từ chối).

## Dữ liệu tiếng Nhật

| Loại | Trường |
|---|---|
| Từ vựng | `expression` (cách viết), `reading` (kana), `furigana`, `meaning_vi`, `part_of_speech`, `jlpt_level`, bài, thẻ, câu ví dụ, nguồn |
| Kanji | `character`, `on_readings`, `kun_readings` (danh sách), `han_viet` (âm Hán Việt), `meaning_vi`, `stroke_count`, ví dụ, bài, nguồn |
| Ngữ pháp | `pattern`, `structure` (cách nối), `meaning_vi`, `usage`, `caution_note` (lưu ý dễ nhầm), ví dụ kèm bản dịch, nguồn |
| Câu ví dụ | `japanese`, `furigana`, `translation_vi`, nguồn |
| Nguồn | `name`, `url`, `license`, `attribution`, `license_verified` |

**Định dạng furigana**: `{chữ gốc|cách đọc}` xen với văn bản thường, vd. `{日本|にほん}に{来|き}たばかりです。` Bộ đọc: `domain/furigana/Furigana.kt`. Từ chỉ có kana thì `furigana = NULL`.

**Danh sách** (âm On/Kun) lưu trong một cột, phân tách bằng ký tự ASCII Unit Separator (`U+001F`).

**`license_verified`** chỉ đặt `true` khi quyền phân phối đã được kiểm tra thật. Gói seed là `false`.

## Transaction

- `ContentPackDao.replacePack`: xóa gói cũ rồi ghi gói mới; một dòng lỗi thì toàn bộ thay đổi bị hủy và nội dung cũ được giữ nguyên.
- `ProgressDao.recordReview`: cập nhật tiến độ và ghi lịch sử ôn tập cùng thành công hoặc cùng thất bại.
- `ContentDao.get*Detail`: đọc mục, câu ví dụ và nguồn trong một transaction để dữ liệu nhất quán.

## Giới hạn kết quả

Danh sách theo bài (khoảng 30 mục) trả về đầy đủ qua `Flow`. Tìm kiếm dùng `LIMIT/OFFSET` (mặc định 30, tối đa 100); chuỗi rỗng thì trả về rỗng mà không truy vấn; ký tự `%` và `_` được escape. `LIKE` của SQLite chỉ bỏ phân biệt hoa thường với ký tự ASCII, nên tìm "Tăng" sẽ không khớp "TĂNG". Có thể bổ sung chuẩn hóa hoặc FTS khi xây dựng chức năng tìm kiếm.

## Migration

- Không bao giờ dùng `fallbackToDestructiveMigration`.
- Quy trình đổi schema: tăng `N3Database.VERSION`, build để export JSON mới, thêm `Migration` vào `N3Migrations.ALL`, rồi commit JSON.
- `N3DatabaseMigrationTest` chạy thử migration từ **mọi** version đã export lên version hiện tại, kiểm tra khớp schema, và kiểm tra dữ liệu ghi bằng schema v1 vẫn đọc được qua Room.
- Hiện chỉ có version 1, chưa có bước migration nào. Test hiện mới xác nhận schema v1 khớp với entity và dữ liệu v1 mở được; từng bước migration thật sẽ được kiểm tra khi có version 2.
- Lưu ý: nếu sửa entity mà quên tăng version, Room sẽ ghi đè `1.json`. Khi review cần xem diff thư mục `app/schemas/`.

## Dữ liệu seed

Chỉ nằm trong mã test (`app/src/androidTest/.../testing/SeedData.kt`) và không có trong app. Gói `seed-test` có `is_seed = 1`, nguồn ghi rõ là dữ liệu tự soạn để kiểm thử.
