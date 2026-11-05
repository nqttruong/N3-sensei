# Ví dụ bổ sung cho Từ vựng, Kanji và Ngữ pháp (11/2026)

Ví dụ được thêm **chỉ cho các mục chưa có ví dụ** trong ba gói đi kèm app. Không mục nào bị sửa: chữ, cách đọc, furigana, nghĩa, lý thuyết, ID và các ví dụ có sẵn giữ nguyên. Mọi câu mới do N3 Sensei tự soạn theo nghĩa và cách dùng đang có trong app. Câu mới có nguồn riêng `<pack id>-examples` ("N3 Sensei tự soạn · câu ví dụ bổ sung", `licenseVerified = false`), **chưa qua kiểm duyệt chuyên môn**.

## Thống kê

Đếm theo dữ liệu trong `app/src/main/assets/content/`. "Từ của tôi" là nội dung riêng của người học, không đếm và không bị đụng tới.

| | Tổng | Có ví dụ (trước) | Chưa có (trước) | Có ví dụ (sau) | Chưa có (sau) | Ví dụ mới |
|---|---|---|---|---|---|---|
| Từ vựng (Mimikara 880 + minh họa 30) | 910 | 30 | 880 | 900 | 10 (cần kiểm tra) | 870 câu, 1 câu/từ |
| Kanji (N3 393 + minh họa 27) | 420 | 417 | 3 (亡, 希, 坂) | 420 | 0 | 7 từ minh họa |
| Ngữ pháp (N3 174 + minh họa 7) | 181 | 117 | 64 (n3g-g111…g174) | 181 | 0 | 140 câu, 2–3 câu/mẫu |

Phiên bản gói: `mimikara-n3-vocab` 1 → 2, `n3-grammar` 3 → 4, `n3-kanji` 2 → 3. App cài bản mới ở lần mở tiếp theo, như mọi lần cập nhật gói.

## Cần kiểm tra (không tạo ví dụ)

Có 10 từ mà chữ, cách đọc và nghĩa trong bảng gốc mâu thuẫn nhau, như thể là hai từ khác nhau. Câu ví dụ cho những từ này sẽ sai với nghĩa đang hiển thị. Danh sách nằm trong `NEEDS_REVIEW` của `tools/content/examples/common.py`.

| ID | Mục | Vấn đề |
|---|---|---|
| v358 | 自身 じしん "tự tin" | 自身 = bản thân; "tự tin" là 自信 |
| v360 | 感心 かんしん "quan tâm" | 感心 = khâm phục; "quan tâm" là 関心 |
| v419 | 合う あう "gặp" | 合う = hợp, khớp; "gặp" là 会う |
| v445 | だまる "lừa" | だまる = im lặng; "lừa" là だます |
| v580 | 敵とな てきとな "phù hợp" | Có vẻ là lỗi gõ của 適当な てきとうな |
| v598 | 大分 ずいぶん | 大分 đọc だいぶ; ずいぶん là 随分 |
| v657 | 鏡面 ひょうめん "bề mặt" | 鏡面 đọc きょうめん; "bề mặt" là 表面 |
| v664 | 記録 "ghi âm" | 記録 = ghi chép, kỷ lục; "ghi âm" là 録音 |
| v682 | 協調 "nhấn mạnh" | 協調 = hợp tác; "nhấn mạnh" là 強調 |
| v739 | 臨む "ước" | 臨む = đối mặt, tham dự; "ước" là 望む |

Có 3 từ mà cách đọc trong bảng bị gõ sai nhưng chữ thì rõ ràng: 迷惑 (bảng ghi めわく), すれ違う (すれちあう), 翻訳 (ほにゃく). Furigana trong câu ví dụ dùng cách đọc đúng (めいわく, すれちがう, ほんやく). Cách đọc của chính mục **không sửa** (`READING_FIXES`).

Một số nghĩa trong bảng chưa sát, nhưng ví dụ vẫn dùng đúng từ. Các nghĩa này **không sửa**, ghi lại để chủ dự án quyết định:

| Mục | Nghĩa trong bảng | Gợi ý |
|---|---|---|
| 整理 | chỉnh sửa | sắp xếp |
| 読書 | đọc viết | đọc sách |
| 課題 | chủ đề | bài tập, vấn đề |
| 混雑 | hỗn tạp | đông đúc |
| 大変な | tồi tệ | vất vả |
| 注目 | chú trọng | chú ý |

Ngoài ra, 220 ví dụ ngữ pháp có sẵn không có furigana. Phần này **chưa bổ sung**, vì là dữ liệu cũ; có thể làm bằng cùng công cụ nếu chủ dự án đồng ý.

## Quy trình (tái lập được, xử lý theo ID)

```bash
python3 -m venv /tmp/venv && /tmp/venv/bin/pip install janome      # chỉ để soạn, không vào app
/tmp/venv/bin/python tools/content/examples/annotate.py tools/content/examples/data/*.tsv
python3 -I tools/content/examples/apply.py --check     # kiểm tra, không ghi
python3 -I tools/content/examples/apply.py             # ghi vào app/src/main/assets/content/*.json
python3 -I tools/content/examples/apply.py --status    # mục còn thiếu ví dụ
```

- **Dữ liệu**: `tools/content/examples/data/`, mỗi bài một tệp (`vocabulary-l01.tsv` … `l12`, `grammar.tsv`, `kanji.tsv`). Cột: `item_id`, câu, furigana, dịch. Mỗi tệp là một đợt; `--status` cho biết mục nào chưa có. Chạy lại không tạo bản sao vì ID ví dụ cố định: `<item id>-ex<n>`, từ Kanji `<item id>-w<n>`.
- **Furigana**:
  - `annotate.py` dùng bộ phân tích janome để điền furigana.
  - Từ đang học luôn lấy cách đọc của chính mục, nên không gán cách đọc khác.
  - Toàn bộ furigana đã được **soát tay**. Các lỗi đọc của janome đã sửa (日本→にほん, 間→あいだ, 一人, 十分おき→じゅっぷん, 三日, 九時, 角→かど…) được ghi trong `FIXES`, áp dụng lại mỗi lần chạy.
  - Từ Kanji ghi furigana tay. Cách đọc ứng với âm On/Kun của chữ: 死亡 (ボウ), 亡くなる (な), 希望, 希少 (キ), 坂, 坂道, 上り坂 (さか/ざか).
- **`apply.py`**:
  - Gỡ phần đã thêm ở lần trước, rồi thêm lại.
  - Từ chối mục đã có ví dụ riêng, mục cần kiểm tra, câu không chứa từ của mục, từ Kanji không chứa chữ, câu trùng, furigana không khớp câu.
  - Dừng nếu kết quả thay đổi bất cứ gì ngoài phần thêm vào.
  - Phiên bản gói = bản trước khi thêm (`examplesBaseVersion`) + `REVISION`. Phải tăng `REVISION` khi dữ liệu ví dụ đổi sau khi phát hành.
- **Dựng lại các gói từ nguồn** (Mimikara, ngữ pháp, Kanji): chạy `apply.py` sau bước sao chép gói vào assets.

## Bảo đảm dữ liệu cũ

- Đã đối chiếu với bản trong Git trước khi thêm: gói mới bỏ phần thêm vào thì **bằng đúng** gói cũ. ID và thứ tự mục, mọi trường, ví dụ cũ (ID, chữ, dịch, thứ tự) đều như cũ.
- Cài gói mới dùng cơ chế thay gói có sẵn, trong một transaction. Tiến độ, lịch ôn, đánh dấu, lịch sử ôn và chỉnh sửa của người học nằm ở bảng riêng nên không bị đụng tới. Không đổi schema, không migration.
- Kiểm thử:
  - `BundledExamplesTest`, `N3GrammarPackTest`, `N3KanjiPackTest`, `MimikaraPackTest` (JVM): số lượng, nguồn, không trùng, mỗi ví dụ thuộc đúng một mục và chứa từ/chữ/mẫu của mục, cách đọc của từ trong câu.
  - `ExamplesUpgradeTest` (thiết bị): cài đúng bản cũ đã commit (`androidTest/assets/content-history/`), có tiến độ, đánh dấu, lịch sử ôn và chỉnh sửa, rồi cài bản mới. Kết quả: mọi thứ còn nguyên, ví dụ cũ không đổi, mở app lần nữa không cài thêm.

## Hiển thị

- Ở màn chi tiết Từ vựng, Ngữ pháp ("Ví dụ") và Kanji ("Từ minh họa"):
  - Khu vực ví dụ nằm dưới phần lý thuyết, tiêu đề kèm số lượng.
  - Chạm tiêu đề để thu gọn hoặc mở.
  - Câu tiếng Nhật có furigana; bản dịch ở dòng dưới.
  - **Không có khu vực nào** khi mục chưa có ví dụ.
- Phần được tô đậm theo màu chủ đạo (`ExampleHighlight`):
  - Từ vựng: từ đang học, kể cả dạng đã chia (働いた, 寂しかった).
  - Kanji: chữ Kanji.
  - Ngữ pháp: các phần cố định của mẫu (といえば, ため, そう…).
  - Học → Bài học → mẫu ngữ pháp cũng tô mẫu trong ví dụ.
- Nội dung chữ không đổi; TalkBack vẫn đọc câu bằng giọng tiếng Nhật.
