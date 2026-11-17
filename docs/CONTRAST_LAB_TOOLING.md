# Contrast Lab: công cụ nội dung (M2)

Thư mục: `tools/content/contrast/`, chạy ngoài app. Công cụ không gọi AI và không cần mạng; app chỉ nhận gói JSON đã PUBLISHED.

Cần `python3` ≥ 3.10 và gói `jsonschema`. Chạy bằng `python3`, **không** dùng `python3 -I` nếu `jsonschema` được cài trong user site.

| File | Vai trò |
|------|---------|
| `build_pack.py` | CLI kiểm tra / tạo gói |
| `validator.py` | 40 mã validation: lớp ngữ nghĩa dùng chung với app, JSON Schema, so với bản trước |
| `fingerprint.py` | Fingerprint nguồn (`sourceSchemaVersion` 1), đọc dòng nguồn như importer lưu, xác minh tham chiếu |
| `furigana_markup.py` | Cú pháp `{gốc|đọc}`, cùng quy tắc với `domain/furigana/Furigana.kt` |
| `fixture_cases.py` | Đọc fixture dùng chung (gói gốc + JSON Patch + kỳ vọng) |
| `fixtures/cases/*.json` | 40 case, mỗi case có kỳ vọng `build` (Python) và `import` (Python + Kotlin) viết tay |
| `fixtures/fingerprint-cases.json` | 9 case fingerprint; hash kỳ vọng tính bằng `sha256sum` độc lập |
| `fixtures/sources.json`, `sources-ambiguous.json` | 6 dòng nguồn thật (n3-grammar v4, mimikara-n3-vocab v2, n3-kanji v3) để fixture không phụ thuộc gói nguồn thay đổi |
| `test_contrast_tools.py` | Test Python (unittest) |

## Kiểm tra một gói (không bao giờ sửa file đầu vào)

```bash
python3 tools/content/contrast/build_pack.py --check docs/contrast/contrast-pack.sample.json --strict
python3 tools/content/contrast/build_pack.py --check PACK.json --previous releases/contrast-n3.v1.json --report report.json
python3 tools/content/contrast/build_pack.py --check PACK.json --mode import     # mức độ như app báo
```

- `--sources`: mặc định `app/src/main/assets/content`; cũng nhận một file records (fixture).
- `--strict`: thất bại cả khi còn WARNING biên tập chưa được xác nhận (mức độ vẫn là WARNING).
- `--previous`: kiểm `contentVersion` tăng, câu hỏi đổi đáp án mà giữ id, nhóm biến mất không có trong `retiredGroups`, nhóm đổi tập mục mà giữ id (ITEMS_CHANGED_SAME_GROUP_ID), nội dung đổi mà không tăng `revision` (REVISION_NOT_INCREMENTED). In báo cáo thay đổi: nhóm thêm, bỏ hoặc lưu trữ, mục đổi, fingerprint đổi, trạng thái và revision đổi. Công cụ **không chuyển tiến độ**, chỉ báo cáo.
- Mã thoát: `0` đạt, `1` không đạt, `2` lỗi đầu vào (thiếu file, metadata hỏng).

## Tạo gói phát hành từ bản nháp

```bash
python3 tools/content/contrast/build_pack.py --drafts tools/content/contrast/drafts \
    --meta tools/content/contrast/release.json --output app/src/main/assets/content/contrast-n3.json \
    --previous tools/content/contrast/releases/contrast-n3.v1.json --reviewers tools/content/contrast/reviewers.json
```

- `reviewers.json`: `{"reviewers": ["<mã>", ...]}`, mã của những người duyệt thật (chưa có file này: chủ dự án tạo khi có người duyệt). Bắt buộc khi build; nhóm được duyệt bởi mã ngoài danh sách → MISSING_METADATA tại `approval.approvedBy`, không ghi file.
- Báo cáo luôn in ai soạn, ai duyệt, revision nào; tự duyệt được đánh dấu "TỰ DUYỆT".

- `drafts/<groupId>.json`: mỗi file một nhóm, có `status`.
- `release.json`: `{"pack": {...}, "groupOrder": [groupId, ...], "retiredGroups": [...]}`.
- Nhóm APPROVED hoặc PUBLISHED được ghi thành PUBLISHED; nhóm ở trạng thái khác làm build thất bại (STATUS_NOT_PUBLISHED).
- Build luôn ở chế độ strict. Chỉ ghi file khi không có ERROR và mọi WARNING biên tập đã được xác nhận.
- Kết quả xác định: cùng đầu vào cho ra cùng byte (không chèn thời gian, `sourcePacks` sắp theo id).
- Hiện **chưa có** thư mục `drafts/` và `releases/`, và **chưa có gói nào được đưa vào app**: gói mẫu chưa được người thật duyệt.

## Test

```bash
python3 tools/content/contrast/test_contrast_tools.py
./gradlew :app:testDebugUnitTest --tests 'com.n3sensei.app.domain.contrast.*'
```

Cùng một bộ fixture được kiểm ở cả hai phía. Kotlin dùng `ContrastSharedFixturesTest`; riêng case `accepted-answers-not-in-contract` không có kỳ vọng phía Kotlin, vì đó là việc của parser JSON ở M4.
