# Gói Kanji N2 (448 chữ Hán, theo bộ thẻ Anki "N2::Kanji N2")

Gói `n2-kanji` (`app/src/main/assets/content/n2-kanji.json`: 448 Kanji, 1.811 từ ví dụ, 0 từ vựng, 18 bài) đi kèm app. Nó tự cài sau các gói N3 bằng bộ cài gói có sẵn (`installBundledContent`, `onlyIfNewer`), nên mở app lại không cài lần hai. Gói được dựng thẳng từ tệp `N2__Kanji N2.apkg` của người dùng, nằm ở gốc repo và không đưa vào Git. Âm On/Kun và số nét lấy từ KANJIDIC2.

> **Quyền dùng lại chưa xác minh** cho nội dung lấy từ bộ thẻ (`licenseVerified = false`).

## Dựng lại

```
curl -o local-content/n2-kanji/kanjidic2.xml.gz https://www.edrdg.org/kanjidic/kanjidic2.xml.gz
python3 -I tools/content/n2_kanji/build_pack.py --strict "N2__Kanji N2.apkg" local-content/n2-kanji/kanjidic2.xml.gz \
    app/src/main/assets/content/n3-kanji.json local-content/n2-kanji/pack.json local-content/n2-kanji/report.json docs/data
cp local-content/n2-kanji/pack.json app/src/main/assets/content/n2-kanji.json
```

Cần Python 3.10+. Để giải nén zstd, công cụ dùng module `zstandard` nếu có, không thì dùng lệnh `zstd`. Cùng đầu vào thì cho ra cùng tệp gói. Khi đổi nội dung gói, tăng `PACK_VERSION` để máy đã cài nhận bản mới; tiến độ vẫn giữ vì id không đổi.

- `apkg.py`: đọc gói Anki mà không cần Anki, chỉ đọc chứ không ghi.
  - Định dạng mới: `collection.anki21b` và media nén zstd, chỉ mục media dạng protobuf.
  - Định dạng cũ: `collection.anki21` / `collection.anki2`, chỉ mục media dạng JSON.
- `build_pack.py`: từ thẻ Anki sang gói.
- `fixes.py`: phần chép tay từ ảnh và các chỗ sửa lỗi gõ, mỗi mục có lý do.
- Bảng truy vết (đưa vào Git):
  - `docs/data/n2-kanji.tsv`: 448 chữ, gồm bài, deck, id note, Hán Việt, nghĩa, nguồn nghĩa, câu ghi nhớ, On/Kun, số nét.
  - `docs/data/n2-kanji-words.tsv`: 1.811 từ, gồm chữ Hán của nhóm, id note, cách đọc, nghĩa, Hán Việt, id ví dụ, cờ.

## Cấu trúc bộ thẻ

| | |
|---|---|
| Định dạng | `anki21b` (Anki ≥ 2.1.50), 2.280 note, 2.280 thẻ (một thẻ một note, đều là thẻ mới, không có lịch sử ôn) |
| Deck | `N2::Kanji N2::第01` … `第18` (18 deck con, mỗi deck một bài) |
| Note type | "Cơ bản", 2 trường: `Mặt trước`, `Mặt sau`; không có tag |
| Media | 152 ảnh PNG minh họa (khoảng 2,4 MB mỗi ảnh), không có âm thanh |

Trong mỗi deck, thẻ đi theo thứ tự của deck (cột `due`): một **thẻ chữ Hán**, sau đó là các **thẻ từ** của chữ đó.

- Thẻ chữ Hán: mặt trước là 1 chữ Hán. Mặt sau là Hán Việt VIẾT HOA, nghĩa (có thể trong ngoặc), có thể có dòng `=> mẹo nhớ` và ảnh.
- Thẻ từ: mặt trước là từ. Mặt sau là Hán Việt, cách đọc kana, nghĩa (có thể có ảnh).

Bộ thẻ **không có** trường On/Kun, số nét hay câu ví dụ riêng. Từ của chữ Hán đóng vai trò ví dụ, như ở gói N3.

## Ánh xạ sang app

| Bộ thẻ | App |
|---|---|
| Deck `第NN` | bài `n2k-lNN` (`lessons.jlpt_level = 'N2'`), số bài NN, giữ thứ tự deck |
| Thẻ chữ Hán | Kanji `n2k-k<mã Unicode>`: chữ, Hán Việt, nghĩa (`meaning_vi`), `note` = nguồn nghĩa + "Ghi nhớ: …" |
| On/Kun, số nét | KANJIDIC2 |
| Thẻ từ | ví dụ `n2k-x-<id note>` của chữ Hán đó (`example_sentences` + `kanji_examples`): từ, furigana dựng từ cách đọc, nghĩa |

Kanji N2 và N3 phân biệt bằng cấp độ của bài (`lessons.jlpt_level`) và gói (`pack_id`). Vì vậy không cần đổi schema Room hay viết migration.

## Những gì không đưa vào, hoặc đã sửa

- **5 chữ đã có ở N3** (祭 調 殺 寝 脂): không tạo bản ghi N2 trùng, chữ vẫn là một bản ghi N3 và tiến độ dùng chung. 17 thẻ từ của chúng trong bộ N2 không được đưa vào, vì ví dụ của một chữ phải nằm cùng gói với chữ đó. Danh sách nằm ở `leftOut` trong `report.json`.
- **152 ảnh**: không import, vì app không hiển thị ảnh cho Kanji và tổng dung lượng khoảng 360 MB.
  - 65 thẻ chỉ có cách đọc/nghĩa trong ảnh (16 thẻ chữ Hán, 49 thẻ từ). Phần này đã được chép tay từ đề mục của ảnh (`fixes.py`), và các thẻ đó có cờ `from-image`.
  - Riêng 融資を受ける, 写真の額縁 và 寮生活が楽しい: nghĩa dựa trên phần giải thích của ảnh, có ghi rõ.
- **Hán Việt của từ**: chỉ giữ trong `origin.hanViet` của gói và trong bảng TSV, vì app chưa có chỗ hiển thị (gói N3 cũng vậy).
- **8 từ** có Hán Việt chính là nghĩa (命令 "Mệnh lệnh"…): lấy Hán Việt làm nghĩa, cờ `meaning-is-han-viet`.
- **13 chỗ sửa lỗi gõ**: sai trợ từ trong cách đọc, cách đọc còn chữ Hán, 祝いを断る → 誘いを断る… Tất cả liệt kê ở `fixed` trong `report.json`.
- **2 thẻ đặc biệt**:
  - 凍 là thẻ chữ Hán nhưng Hán Việt không viết hoa.
  - 瓦 chỉ có thẻ từ: được thành một chữ Hán, và thẻ đó vẫn là từ của chính nó.

## Học và ôn

- **Học → Kanji**: có tab **JLPT N3 / JLPT N2**. Mỗi lần chỉ hiện một cấp độ, mặc định N3. Chi tiết, flashcard, chỉnh sửa dùng chung màn hình với N3.
- **Học → Bài học**: chỉ có N3, như trước.
- **Ôn tập**: dùng chung thuật toán, lịch, giới hạn "Kanji mới mỗi ngày" và màn hình với Kanji N3.
  - Cài đặt ôn tập → **Bài Kanji ôn tập** chọn những bài mà phiên ôn lấy Kanji **mới**.
  - Mặc định: mọi bài N3 (như trước), chưa bài N2 nào. Vì vậy cài gói không tự đưa Kanji N2 vào lịch ôn.
  - Kanji đã có tiến độ vẫn đến hạn và được ôn dù bài có được chọn hay không. Bỏ chọn không xóa gì.
