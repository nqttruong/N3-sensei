# Gói từ vựng Mimikara N3 (880 từ)

Gói `mimikara-n3-vocab` (tệp `app/src/main/assets/content/mimikara-n3-vocab.json`) được đóng gói cùng app và tự cài ở lần mở đầu tiên, cạnh gói minh họa. Dữ liệu lấy từ tệp do chủ dự án cung cấp ngày 13/10/2026: `[VTI Mirai share] 140 - 880 TỪ VỰNG MIMIKARA N3 MINATO.xlsx - TU VUNG MIMIKARA N3.pdf`.

> **Giấy phép chưa xác minh.** Bảng này được chia sẻ lại, theo giáo trình thương mại *Mimikara Oboeru N3*. Gói ghi rõ nguồn và để `licenseVerified = false`; app hiển thị điều đó ở mỗi thẻ từ. Việc đóng gói vào app là quyết định của chủ dự án; trước khi phát hành công khai cần xác minh quyền phân phối (xem [CONTENT_FORMAT.md](CONTENT_FORMAT.md#bản-quyền-nội-dung)).

## Quy trình (tái lập được)

```bash
pdftotext -bbox  "<tệp>.pdf" bbox.html
pdftotext -raw   "<tệp>.pdf" raw.txt
python3 -I tools/content/mimikara_parse_pdf.py bbox.html raw.txt parsed.json
python3 -I tools/content/mimikara_to_pack.py parsed.json local-content/mimikara-n3
cp local-content/mimikara-n3/pack-candidate.json app/src/main/assets/content/mimikara-n3-vocab.json
```

- **Trích xuất**: PDF 1 trang xuất từ Google Sheets, có lớp chữ (không phải ảnh scan). Bảng có 19 khối cột cạnh nhau (STT, Kanji, Hiragana, Meaning; bài 6 không có cột Hiragana) và 12 bài. Chữ được gán vào cột theo tọa độ, dòng theo vị trí số STT (không tách theo dấu cách vì nghĩa tiếng Việt có dấu cách). Kiểm chứng bằng ảnh dựng PDF ở 1200 dpi cho các dòng mẫu và các dòng lỗi.
- **Chuẩn hóa**: chỉ cắt/gộp khoảng trắng và chuẩn Unicode NFC (không dòng nào thay đổi vì NFC); không dịch, không đổi nghĩa, không thêm furigana, từ loại hay câu ví dụ.
- `local-content/` (dữ liệu trung gian: `normalized.csv` 880 dòng kèm trạng thái, `report.json`) không đưa vào Git.

## Ánh xạ

| Bảng | Gói nội dung |
|---|---|
| Bài N + nhóm (名詞, 動詞, 連用名詞, 形容詞, カタカナ, 副詞, … ) | lesson `mimikara-n3-lNN`, số N, tiêu đề `Mimikara N3 · <nhóm>` |
| STT | id `mimikara-n3-vNNN` (ổn định, tiến độ học gắn vào đây) và thứ tự hiển thị |
| Kanji | `expression` (giữ nguyên, kể cả dạng nhiều cách viết `代わる・替わる・換わる`) |
| Hiragana | `reading` |
| Meaning | `meaningVi` |
| — | `furigana`, `partOfSpeech`, chủ đề: để trống (bảng không có) |
| — | ví dụ: bảng không có; từ phiên bản 2 có 870 câu ví dụ N3 Sensei tự soạn, nguồn riêng, xem [EXAMPLES.md](EXAMPLES.md) |
| tên tệp | `jlptLevel` = N3; nguồn có tên tệp gốc, `licenseVerified = false` |

## Báo cáo

| | Số dòng |
|---|---|
| Trong tệp | 880 (STT 1–880 liên tục, không trùng số) |
| Nhập vào app | **880** |
| Hiệu chỉnh lỗi của bảng (chủ dự án quyết định) | 3 |
| Cách đọc suy từ chính từ (không có trong bảng) | 41 |
| Cùng từ và cách đọc, khác nghĩa — giữ cả hai | 2 (1 cặp) |
| Thiếu từ hoặc nghĩa | 0 |
| Không nhập được | 0 |

Số lượng ghi trên hàng tiêu đề của bảng (tổng 869) ít hơn số dòng thực tế 1 ở mỗi bài 2–12; dùng số dòng thực tế (880, khớp tên tệp). Số từ mỗi bài: 120, 100, 78, 112, 100, 40, 40, 45, 80, 80, 50, 35.

### Hiệu chỉnh và quyết định

| STT | Bảng gốc | Trong gói | Lý do |
|---|---|---|---|
| 90 | 作者 / cách đọc `作者` | cách đọc `さくしゃ` | Ô cách đọc ghi chữ Hán |
| 750 | 済ませる・済ます / `すませる・済ます` | `すませる・すます` | Dạng đọc thứ hai ghi chữ Hán |
| 796 | `パートナ` / `パートナー` | từ `パートナー` | Từ thiếu trường âm |
| 627 | もしかすると・もしかしたら・もしかして / (trống) | cách đọc = chính từ | Từ chỉ có kana |
| 511–550 (bài 6) | từ katakana / (không có cột) | cách đọc = chính từ | Bài không có cột cách đọc |
| 600, 848 | しっかり (toàn bộ) và しっかり (kiên quyết, chắc chắn, đáng tin cậy) | cả hai | Khác nghĩa, khác bài. Quy tắc kiểm tra gói đổi: chỉ coi là trùng khi giống cả từ, cách đọc **và** nghĩa |

Các hiệu chỉnh nằm trong `CORRECTIONS` của `tools/content/mimikara_to_pack.py`; script dừng nếu ô gốc không còn đúng như ghi nhận.

## Phương án nhập

**Bộ dữ liệu đi kèm app** (phương án ưu tiên): nội dung cố định, cần dùng ngay sau khi cài, không cần mạng. Không dùng callback tạo database: các gói được cài **ở nền** khi app mở (`MainViewModel.installBundledContent`), mỗi gói một transaction (`ContentPackDao.replacePack`).

- **Phiên bản**: `pack.version` (hiện 1) so với bản đã cài trong bảng `content_packs`; cùng hoặc cũ hơn → không ghi gì. Mở app lần sau không nạp lại (đã kiểm: `imported_at` không đổi). Muốn cập nhật nội dung: tăng `version`.
- **Thời gian**: cài cả 2 gói (910 từ) ≈ 0,4 s trên emulator, trong lúc người dùng còn ở màn giới thiệu; các màn hình đọc Room qua `Flow` nên tự hiện dữ liệu khi cài xong.
- **Lỗi**: tệp asset thiếu/hỏng hoặc gói không hợp lệ → báo cáo FAILED cho gói đó (gói khác vẫn cài), hộp thoại "Không cài được nội dung đi kèm" nêu tên gói, nút **Thử lại**; dữ liệu học không bị đụng tới (transaction rollback).
- Người dùng vẫn có thể nhập một phiên bản khác qua **Cài đặt → Nhập gói nội dung** (kiểm tra trước, xác nhận khi thay gói đang cài).

## Quy tắc trùng và nhập lại

- Khóa ổn định của mục: `id` (`mimikara-n3-vNNN`, từ STT). Nhập lại cùng gói **thay thế** nội dung của gói `mimikara-n3-vocab` trong một transaction; không bao giờ tạo bản sao.
- Trong một gói: cùng `id` → bỏ bản sau; cùng từ + cách đọc + nghĩa → bỏ bản sau; cùng từ + cách đọc nhưng **khác nghĩa** → hai mục (không dựa riêng vào nghĩa).
- Tiến độ, lịch ôn và đánh dấu nằm ở bảng riêng, gắn với `(VOCABULARY, id)`: không bị ghi đè khi cập nhật nội dung.

## Cách kiểm tra dữ liệu đã nhập

- Trong app: **Học → Bài học** (12 bài "Mimikara N3 · …", bài 1 có 120 từ) hoặc **Học → Từ vựng** (chip lọc theo bài, tìm "tac gia" → 作者 · さくしゃ).
- Trên emulator/thiết bị debug: `adb exec-out run-as com.n3sensei.app cat databases/n3sensei.db > n3.db` (cùng tệp `-wal`), rồi `sqlite3 n3.db "SELECT COUNT(*) FROM vocabulary v JOIN lessons l ON l.id = v.lesson_id WHERE l.pack_id = 'mimikara-n3-vocab'"` → 880.

## Trong app

- Cùng tồn tại với gói minh họa (15 từ có ở cả hai gói, hiện cả hai). Khi có nhiều gói, chip lọc theo bài dùng tiêu đề bài.
- Danh sách, tìm kiếm (không dấu, kana), chi tiết, luyện tập theo bài, đánh dấu, lịch ôn, thống kê dùng dữ liệu này như mọi gói (tiến độ không nằm trong bản ghi nội dung).
- Danh sách tải toàn bộ mục của bộ lọc qua `LazyColumn` (chỉ dựng phần đang hiện); truy vấn 3 000 từ ≈ 36 ms (xem [QA.md](QA.md)); tìm kiếm giới hạn 100 kết quả.

## Kiểm thử

| Test | Nội dung |
|---|---|
| `MimikaraPackTest` (JVM) | 880 từ, 12 bài, số từ mỗi bài, thứ tự, giữ nguyên chữ, các hiệu chỉnh, không bịa trường, nguồn và `licenseVerified = false` |
| `ContentPackValidatorTest` (JVM) | cùng từ + cách đọc khác nghĩa là hai mục; trùng hoàn toàn bị bỏ; thiếu trường, sai định dạng, ID trùng (có sẵn) |
| `MainViewModelTest` (JVM) | gói cài lỗi được nêu tên, Thử lại, exception không làm crash |
| `MimikaraImportTest` (Room, tệp database thật) | nhập 880 từ, tiếng Nhật/Việt nguyên vẹn; danh sách, tìm kiếm (không dấu, kana), lọc theo bài; nhập lại không tạo bản sao và giữ tiến độ/đánh dấu; lỗi giữa chừng (trigger ở từ thứ 500) giữ nguyên bản cũ và tiến độ; tệp rỗng/cắt cụt/sai định dạng không ghi gì; đóng mở lại database; asset thiếu được báo, gói kia vẫn cài |
| `EndToEndTest` (emulator) | mở app lần đầu: cả hai gói được cài, bài Mimikara 1 có 120 từ, tổng 910 từ |
