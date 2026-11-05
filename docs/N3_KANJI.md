# Gói Kanji N3 (392 chữ Hán, theo N3.pdf)

Gói `n3-kanji` (`app/src/main/assets/content/n3-kanji.json`: 392 Kanji, 1.272 từ minh họa, 0 từ vựng) đi kèm app, tự cài ở lần mở đầu sau các gói minh họa, Mimikara và ngữ pháp. Dựng từ phần Kanji của tệp `N3.pdf` của người dùng (bộ thẻ Anki "N3", 290 trang, nằm ở gốc repo, không đưa vào Git) và KANJIDIC2.

> **Quyền sử dụng chưa xác minh** cho phần lấy từ N3.pdf (`licenseVerified = false`). Chỉ đóng gói dữ kiện ngắn: chữ Hán, âm Hán Việt, chữ viết/cách đọc/nghĩa ngắn của từ. **Không** đóng gói mẹo nhớ và câu ví dụ của thẻ.

## Dựng lại

```
python3 tools/content/n3_kanji/extract_pdf.py N3.pdf local-content/n3-pdf/raw.json   # cần pdfplumber
curl -O https://www.edrdg.org/kanjidic/kanjidic2.xml.gz
python3 -I tools/content/n3_kanji/build_pack.py local-content/n3-pdf/raw.json kanjidic2.xml.gz \
    local-content/n3-kanji/pack.json local-content/n3-kanji/report.json docs/data
cp local-content/n3-kanji/pack.json app/src/main/assets/content/n3-kanji.json
```

- `extract_pdf.py`: dựng hàng bảng từ đường kẻ (thẻ bị cắt qua trang được nối lại). Kết quả khớp số thẻ khai báo ở cả 46 nhóm của PDF.
- `pdf_cards.py`: nhận diện thẻ chữ Hán và các thẻ từ đi sau nó cho tới chữ Hán kế tiếp (một **nhóm**), tách cách đọc / Hán Việt / nghĩa, bỏ dòng mẹo nhớ.
- `meanings_vi.py`, `word_fixes.py`: các chỉnh sửa tay, mỗi mục có lý do.
- Bảng truy vết (đưa vào Git): `docs/data/n3-kanji-meanings.tsv` (392 chữ: trang PDF, Hán Việt, nghĩa, nguồn nghĩa, nghĩa PDF gốc, nghĩa KANJIDIC2, On/Kun, số nét, cờ) và `docs/data/n3-kanji-words.tsv` (1.272 từ: trang, chữ Hán của nhóm, nghĩa, ID ví dụ, cờ).

## Chuẩn hóa ký tự

Lớp chữ của PDF dùng ký tự bộ thủ thay cho chữ Hán ở 641 thẻ (`⾳`→音, `⻑`→長, `⻭`→歯…): chuẩn hóa NFKC + bảng ánh xạ cho khối CJK Radicals Supplement. Bộ thủ thật trong mẹo nhớ (⺡, ⺅…) không bị đụng tới (mẹo nhớ không được đóng gói). Sửa mã chữ: `戶`→`戸` (dạng Nhật), thẻ chữ Hán `贊`→`賛`, `劳`→`労` (từ trong nhóm và mẹo nhớ của chính thẻ dùng dạng Nhật).

## Nghĩa của chữ Hán

| Nguồn | Số chữ | Ghi chú |
|---|---|---|
| N3.pdf | 103 + 2 (bỏ nhãn "Nghĩa gốc:", "family name") | |
| Dịch từ KANJIDIC2 (thẻ PDF không có nghĩa) | 273 | N3 Sensei dịch nghĩa cốt lõi từ tiếng Anh, **chưa kiểm duyệt** |
| Dịch từ KANJIDIC2 thay nghĩa PDF | 14 | PDF ghi nghĩa của một từ ghép: 伝 "tiểu sử" (伝記), 予 "dự định" (予定), 及 "phổ cập" (普及)… — nghĩa PDF vẫn lưu trong bảng |

Âm Hán Việt giữ theo PDF (KANJIDIC2 có vài âm sai rõ: 伝 "Vân", 戦 "Khuyết"). Âm On/Kun và số nét lấy từ KANJIDIC2 (bản 2026-299). Trong app, ghi chú của mỗi chữ cho biết nghĩa theo N3.pdf hay dịch từ KANJIDIC2 (kèm nghĩa tiếng Anh).

**KANJIDIC2 và JMdict**: © EDRDG, giấy phép CC BY-SA 4.0 (https://www.edrdg.org/edrdg/licence.html). Điều kiện: ghi nguồn trên một màn hình riêng của app (vd. "Nguồn"); phần dịch/biên soạn dựa trên dữ liệu này phải chia sẻ theo cùng giấy phép.

## Từ minh họa của nhóm (miền Kanji, không phải Từ vựng)

1.272 thẻ từ (1.270 + 易しい, お湯 nằm trong thẻ chữ Hán; 狼 vừa là thẻ chữ Hán vừa có cách đọc) là **từ minh họa của chữ Hán**, lưu bằng quan hệ có sẵn `kanji_examples` + `example_sentences` (chữ viết, furigana sinh từ cách đọc, nghĩa). Gói `n3-kanji` **không ghi gì vào bảng `vocabulary`** và không trỏ sang gói khác (xem [AUDIT_KANJI_VOCABULARY.md](AUDIT_KANJI_VOCABULARY.md)):

- mỗi thẻ từ một bản ghi `n3k-x-<nhóm>-<thẻ>`; từ PDF lặp ở hai nhóm (48 thẻ) là hai bản ghi, mỗi bản ghi thuộc nhóm của nó;
- một từ có thể đồng thời là từ vựng (vd. 皮 かわ trong Mimikara) và từ minh họa của chữ 皮: hai bản ghi, hai vai trò, không gộp;
- nghĩa: theo PDF; 21 thẻ không có nghĩa được dịch từ JMdict (cờ `meaning-jmdict-<seq>`);
- furigana: 1.271/1.272; お仕事 không có vì PDF ghi cách đọc しごと (cờ `reading-does-not-match-spelling`);
- âm Hán Việt của từ chỉ lưu trong metadata gói và bảng truy vết (không có cột hiển thị).

Màn chi tiết Kanji hiện các từ này ở mục **"Từ minh họa"**, đúng thứ tự trong PDF. Ba chữ không có từ nào trong PDF (亡, 希, 坂) được thêm 7 từ N3 Sensei tự soạn từ phiên bản 3, nguồn riêng `n3-kanji-examples`; xem [EXAMPLES.md](EXAMPLES.md).

Cần kiểm tra: 7 từ không chứa chữ Hán của nhóm, giữ nguyên nhóm theo PDF (cờ `word-without-its-kanji`): 坂 → 木の板, 黒板, 板書, 板前, 看板 (PDF in thẻ 坂 rồi các từ của chữ 板, không có thẻ chữ 板); 伸 → 身長; 州 → 連合.

## Bài học

16 bài `n3k-l01`…`n3k-l16`, lộ trình riêng (không trộn với bài ngữ pháp). Bài 1–10 = nhóm 第01–第10 của PDF (27, 26, 27, 24, 24, 24, 24, 24, 24, 23 chữ). Nhóm 第11–第18 có 15–27 chữ nên được cắt lại theo đúng thứ tự thành 6 bài 25/24/24/24/24/24 chữ. Không nhóm nào bị tách; nhóm gốc của mỗi chữ có trong bảng truy vết. Bài Kanji không có mục từ vựng (cảnh báo "khác mục tiêu 30/27/7" khi nhập là bình thường).
