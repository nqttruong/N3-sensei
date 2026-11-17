# Contrast Lab: content contract và JSON schema

Trạng thái: **đặc tả** (2026-11-17), là căn cứ cho M2 (công cụ `tools/content/contrast/` + gói mẫu) và cho validator trong app ở M4.

| File | Vai trò |
|------|---------|
| [contrast/contrast-pack.schema.json](contrast/contrast-pack.schema.json) | JSON Schema (draft 2020-12) cho **cấu trúc** |
| [contrast/contrast-pack.sample.json](contrast/contrast-pack.sample.json) | **Gói mẫu hoàn chỉnh, là bản chuẩn**: 1 nhóm ngữ pháp đủ 4 dạng bài, 1 nhóm từ vựng và 1 nhóm Kanji rút gọn |
| Tài liệu này | Ý nghĩa từng trường, quy tắc chéo mà JSON Schema không diễn đạt được, vòng đời, validation, versioning |

Gói mẫu đã được kiểm tra ngày 2026-11-17 bằng một script tạm (ngoài repo):
- **0 lỗi schema**.
- **0 lỗi ngữ nghĩa**: id duy nhất, furigana khớp văn bản, đáp án thuộc options, các đoạn của câu tìm lỗi ghép lại đúng bằng câu, cảnh báo nào cũng đã được xác nhận.
- **Fingerprint và snapshot khớp dữ liệu nguồn thật** của `n3-grammar` v4, `mimikara-n3-vocab` v2, `n3-kanji` v3.
- 14 biến thể sai cố ý đều bị schema từ chối: thiếu `approval` khi PUBLISHED, điền chỗ trống không có `＿＿＿`, tìm lỗi không có `correction`, một option, dạng bài lạ, fingerprint sai, nhóm một mục, thiếu giải thích, `contentVersion` = 0, `schemaVersion` = 2, v.v.

---

## 1. Cấu trúc tổng thể

```text
{
  "format": "n3sensei-contrast-pack",      // khác "n3sensei-content-pack" của gói học hiện có
  "schemaVersion": 1,
  "pack":        { id, title, description?, jlptLevel, contentVersion, publishedAt, license{text, licenseVerified} },
  "sourcePacks": [ { id, version } ],       // gói nguồn đã dùng để kiểm tra tham chiếu lúc build
  "groups":      [ Group, … ],              // ≥ 1
  "retiredGroups": [ { id, retiredInVersion, reasonVi, replacedBy? } ]   // tùy chọn
}
```

`$schema` được phép xuất hiện (để trình soạn thảo gợi ý); ngoài ra không cho phép trường lạ (`additionalProperties: false` ở mọi cấp), để lỗi gõ tên trường bị phát hiện ngay.

## 2. Metadata

### 2.1. Cấp gói (`pack`)

| Trường | Bắt buộc | Lý do cần |
|--------|----------|-----------|
| `id` | ✓ | Khóa của gói đã cài; mọi attempt ghi `contentPackId` |
| `title` | ✓ | Hiển thị trong báo cáo import và màn thông tin |
| `description` | | Mô tả cho người duyệt và người học |
| `jlptLevel` | ✓ | Lọc theo cấp |
| `contentVersion` | ✓ | Số nguyên ≥ 1, tăng mỗi lần phát hành; mọi attempt và review ghi lại |
| `publishedAt` | ✓ | Ngày phát hành (YYYY-MM-DD) |
| `license.text`, `license.licenseVerified` | ✓ | Như gói hiện có: không được khẳng định bản quyền khi chưa kiểm tra |

`createdAt` và `updatedAt` cấp gói **không có**: `publishedAt` và `contentVersion` đã đủ, và lịch sử chi tiết nằm trong git của công cụ. `contentType` cũng không đặt ở cấp gói, vì một gói chứa cả ba loại; loại được ghi ở từng nhóm.

### 2.2. Cấp nhóm

| Trường | Bắt buộc | Ghi chú |
|--------|----------|---------|
| `id` | ✓ | `^[a-z0-9][a-z0-9-]{1,79}$`; đề xuất tiền tố `cg-g-`, `cg-v-`, `cg-k-` |
| `revision` | ✓ | ≥ 1 |
| `status` | ✓ | Mục 6; gói phát hành chỉ chứa `PUBLISHED` |
| `contentType` | ✓ | GRAMMAR, VOCABULARY, KANJI |
| `jlptLevel` | ✓ | |
| `title`, `summaryVi` | ✓ | |
| `updatedAt` | ✓ | Ngày của revision này |
| `approval` | ✓ khi APPROVED/PUBLISHED | `authoredBy` (mã người soạn), `approvedBy` (mã người duyệt; **không phải tên thật hay email**), `approvedAt`, `approvedRevision` (phải bằng `revision` của nhóm, nếu không: MISSING_METADATA tại `approval.approvedRevision`), `aiAssisted`, `acknowledgedWarnings[]`. `authoredBy = approvedBy` là **tự duyệt**: được phép (quyết định D1, 2026-11-17) và luôn được báo cáo công khai. |
| `items` | ✓ | 2–4 mục |
| `theory` | ✓ | ≥ 1 đoạn |
| `comparison`, `examples`, `memoryTips`, `commonMistakes` | ✓ (mảng, có thể rỗng) | Rỗng thì WARNING: người duyệt phải xác nhận là cố ý |
| `questions` | ✓ | ≥ 1 |

`sourceReferences` không đặt ở cấp nhóm: tham chiếu thuộc về **từng mục** (`items[].source`), vì mỗi mục trỏ tới một bản ghi khác nhau.

## 3. Mục so sánh (`items[]`)

Mỗi mục có `id`, `label`, `labelFurigana?`, `source` và **đúng một** khối chi tiết khớp với `contentType` của nhóm:

| Loại | Khối | Trường |
|------|------|--------|
| GRAMMAR | `grammar` | `meaningVi`, `connection` (cách kết hợp), `nuanceVi` (sắc thái, khi nào dùng) |
| VOCABULARY | `vocabulary` | `reading` (chỉ kana), `meaningVi`, `usageVi`, `collocations[]` (`japanese`, `furigana?`, `meaningVi`) |
| KANJI | `kanji` | `meaningVi`, `hanViet?`, `onReadings` (katakana), `kunReadings` (hiragana, cho phép `.` và `-` như gói Kanji hiện có), `radical?` (`character`, `nameVi`), `shapeNoteVi` (điểm phân biệt hình dạng) |

Khối chi tiết là nội dung **Contrast tự viết** cho mục đích so sánh, nên có thể khác cách diễn đạt của nguồn. Ví dụ, gói mẫu chỉ liệt kê 2 âm kun thường dùng của 積, trong khi nguồn có 4. Phần đối chiếu với nguồn nằm ở `source`.

## 4. Lý thuyết, so sánh, ví dụ, mẹo, lỗi thường gặp

- `theory[]`: `id`, `titleVi`, `bodyVi`.
- `comparison[]`: `aspectVi` và `values` (ánh xạ itemId → văn bản, tối thiểu 2 khóa). Khóa phải là id mục của nhóm.
- `examples[]`: `id`, `itemId`, `japanese`, `furigana`, `translationVi`. Furigana **bắt buộc** khi câu có Kanji.
- `memoryTips[]`: `id`, `textVi`.
- `commonMistakes[]`: `id`, `situationVi`, `wrong{japanese, furigana}`, `correct{japanese, furigana}`, `explanationVi`, và cặp tùy chọn `expectedItemId` + `confusedWithItemId` (cái này bắt buộc cái kia, theo `dependentRequired`).

**Furigana** dùng cùng cú pháp với [CONTENT_FORMAT.md](CONTENT_FORMAT.md): `{chữ gốc|cách đọc}` xen văn bản thường. Bỏ ngoặc và cách đọc đi thì phải còn lại **đúng** văn bản tương ứng. Ký hiệu chỗ trống `＿＿＿` phải có mặt nguyên văn trong cả `japanese` lẫn `furigana`.

## 5. Tham chiếu nguồn (`items[].source`)

| Trường | Bắt buộc | Ghi chú |
|--------|----------|---------|
| `sourceType` | ✓ | Phải bằng `contentType` của nhóm |
| `sourceContentId` | ✓ | Id dòng trong gói nguồn, ví dụ `n3g-g001`, `mimikara-n3-v211`, `n3k-k07a4d` |
| `sourcePackId` | ✓ | Phải có trong `sourcePacks` |
| `sourceSchemaVersion` | ✓ | Hiện chỉ có `1` |
| `sourceSnapshot` | ✓ | Đúng bộ trường của loại (JSON Schema bắt buộc đủ và không thừa) |
| `sourceFingerprint` | ✓ | `sha256:` + 64 ký tự hex thường |

**Cách tính fingerprint, `sourceSchemaVersion = 1`:**

```text
FIELDS = GRAMMAR:    pattern, structure, meaningVi
         VOCABULARY: expression, reading, meaningVi
         KANJI:      character, onReadings, kunReadings, meaningVi
norm(chuỗi)      = Unicode NFC, bỏ khoảng trắng đầu/cuối; null → ""
norm(danh sách)  = các phần tử norm(), nối bằng U+001E
canonical        = sourceType + U+001F + "1" + U+001F + norm(field_1) + U+001F + … + norm(field_n)
fingerprint      = "sha256:" + hex_lowercase(SHA-256(UTF-8(canonical)))
```

- Tính trên **giá trị của gói nguồn như importer đã lưu**. Dòng trong DB chứa giá trị đã hợp nhất với chỉnh sửa của người học, nên app dựng lại giá trị gói bằng `ContentEdits.base` (từ `user_content_edits.base_value`) trước khi tính (đính chính ở M4).
- Thứ tự phần tử trong danh sách âm đọc có ý nghĩa.
- Đổi bộ trường hay cách chuẩn hóa thì phải tăng `sourceSchemaVersion`. App gặp version chưa biết thì báo trạng thái `UNVERIFIABLE`, không báo lỗi.
- **Đã xác minh ở M4:** importer cũ trim chuỗi và bỏ phần tử rỗng của danh sách âm đọc (giữ `.` và `-`); `fingerprint.py` mô phỏng đúng việc này (`stored_value`). Fingerprint Kotlin của cả 1.959 dòng nguồn đóng sẵn trong Room bằng fingerprint của công cụ (test thiết bị).

Hành vi khi tham chiếu không khớp ở ba thời điểm: xem [ARCHITECTURE mục 7](CONTRAST_LAB_ARCHITECTURE.md#7-tham-chiếu-nguồn).

## 6. Câu hỏi và lựa chọn

### 6.1. `questions[]`

| Trường | Bắt buộc | Ghi chú |
|--------|----------|---------|
| `id`, `revision` | ✓ | |
| `type` | ✓ | MULTIPLE_CHOICE, ERROR_DETECTION, FILL_IN_THE_BLANK, CONTEXT_SELECTION |
| `difficulty` | ✓ | 1–3; tác giả tự gán, người duyệt kiểm tra |
| `targetItemId` | ✓ | Mục được kiểm tra cách dùng đúng |
| `instructionVi` | ✓ | |
| `prompt{japanese, furigana?, translationVi?}` | FILL, ERROR | FILL: `japanese` chứa đúng một `＿＿＿` |
| `context{descriptionVi, japanese?, furigana?}` | CONTEXT | |
| `options[]` | ✓ | 2–6; ERROR ≥ 3 |
| `correctOptionId` | ✓ | |
| `correction{text, furigana?, itemId?}` | ERROR | Đoạn đúng thay cho đoạn sai |
| `explanationVi` | ✓ | |

### 6.2. `options[]`

`id` (`^[a-z0-9]{1,8}$`, duy nhất trong câu), `text` (không trùng trong câu), `furigana?`, `itemId?`, `feedbackVi?`.

### 6.3. Quy tắc chéo theo dạng

| Dạng | Quy tắc |
|------|---------|
| MULTIPLE_CHOICE, CONTEXT_SELECTION, FILL_IN_THE_BLANK | `itemId` của option đúng phải bằng `targetItemId` |
| ERROR_DETECTION | Các `options[].text` ghép theo thứ tự phải bằng đúng `prompt.japanese`; option đúng là đoạn sai và nên có `itemId` (dạng bị dùng nhầm); `correction.itemId` phải bằng `targetItemId` |
| MULTIPLE_CHOICE, CONTEXT_SELECTION | Chỉ có 2 option thì WARNING (đoán đúng 50%) |
| Mọi dạng | Mọi `itemId` phải thuộc nhóm |

## 7. Vòng đời nội dung

```text
            AI tool                  tác giả                người duyệt                  build tool
 (trống) ─────────▶ AI_GENERATED ──▶ DRAFT ─────────▶ REVIEWING ─────────▶ APPROVED ─────────▶ PUBLISHED
                        │  ▲          ▲  │                 │ trả lại             │                 │
                        └──┼──────────┘  └─────────────────┘◀────────────────────┘ (sửa sau duyệt) │
                           │                                                                        │
                           └──── sửa nội dung đã PUBLISHED: revision+1 → REVIEWING (bản cũ vẫn phát hành) ◀┘
                                                                         bất kỳ ──▶ ARCHIVED (đưa vào retiredGroups)
```

| Trạng thái | Ai đặt | Điều kiện |
|-----------|--------|-----------|
| `AI_GENERATED` | Công cụ `draft_with_ai.py`, chạy ngoài app | Bản nháp có `aiAssisted: true` |
| `DRAFT` | Tác giả | Đang sửa tay, hoặc soạn không có AI |
| `REVIEWING` | Tác giả | `build_pack.py --check` không còn ERROR |
| `APPROVED` | **Người duyệt**, có thể chính là tác giả (D1: tự duyệt được phép, ghi `authoredBy` = `approvedBy`, báo cáo công khai) | Không ERROR; mọi WARNING đã được ghi vào `acknowledgedWarnings`; người duyệt đã đọc từng câu hỏi và đáp án; có `approval` |
| `PUBLISHED` | **Chỉ công cụ build**, khi đưa nhóm vào một gói phát hành có `contentVersion` mới; `approvedBy` phải có trong danh sách người duyệt thật (`--reviewers`), nên mã minh họa như `reviewer-01` không phát hành được | Đang APPROVED; tham chiếu nguồn khớp gói nguồn hiện tại; qua kiểm tra so với gói phát hành trước (`--previous`) |
| `ARCHIVED` | Người duyệt | Không phát hành nữa; build thêm vào `retiredGroups` với lý do và `replacedBy` nếu có |

- **Sửa nội dung đã PUBLISHED:** tạo bản sửa với `revision + 1` ở trạng thái REVIEWING. Bản PUBLISHED cũ vẫn nằm trong các gói phát hành cho đến khi bản mới được APPROVED. Đổi ý nghĩa đáp án thì phải dùng id câu hỏi mới. Đổi tập mục thì phải dùng id nhóm mới, và nhóm cũ chuyển sang ARCHIVED.
- **Lịch sử phiên bản:** bản nháp nằm ở `tools/content/contrast/drafts/<groupId>.json`, một file mỗi nhóm, có trạng thái, quản lý bằng git; diff của git chính là lịch sử. Mỗi gói phát hành được lưu thêm bản sao `tools/content/contrast/releases/contrast-n3.v<contentVersion>.json` để làm `--previous` cho lần sau. App chỉ biết gói phát hành.
- **App chỉ nhập `PUBLISHED`.** Nhóm có trạng thái khác trong gói là ERROR, và cả gói bị từ chối.

## 8. Validation

40 mã (FURIGANA_MISSING có hai dòng). Mức độ:
- **ERROR**: không được nhập. App từ chối **cả gói**, không ghi dòng nào.
- **WARNING**: được nhập, nhưng người duyệt phải xác nhận trong `approval.acknowledgedWarnings` (`code` + `target`). Lúc build, WARNING chưa được xác nhận là ERROR.
- **INFO**: chỉ để biết.

| Mã | Mức | Kiểm tra ở | Điều kiện |
|----|-----|-----------|-----------|
| `INVALID_JSON` | ERROR | build, import | JSON không đọc được |
| `SCHEMA_VIOLATION` | ERROR | build, import | Sai JSON Schema: thiếu trường, sai kiểu, trường lạ, sai mẫu id |
| `UNSUPPORTED_FORMAT` | ERROR | build, import | `format` khác, hoặc `schemaVersion` app không hỗ trợ |
| `MISSING_METADATA` | ERROR | build, import | Thiếu trường bắt buộc của `pack` hoặc nhóm (gồm `approval` khi PUBLISHED) |
| `INVALID_CONTENT_VERSION` | ERROR | build, import | `contentVersion` không phải số nguyên ≥ 1; hoặc lúc build không lớn hơn `--previous` |
| `OLDER_OR_SAME_VERSION` | INFO | import | `contentVersion` ≤ bản đã cài: `UP_TO_DATE`, không ghi |
| `STATUS_NOT_PUBLISHED` | ERROR | build (gói phát hành), import | Nhóm không ở PUBLISHED |
| `DUPLICATE_GROUP_ID` | ERROR | build, import | Id nhóm trùng (kể cả trùng với `retiredGroups`) |
| `DUPLICATE_ITEM_ID` | ERROR | build, import | Id mục trùng trong gói |
| `DUPLICATE_ID` | ERROR | build, import | Id trùng giữa ví dụ, câu hỏi, lý thuyết, mẹo, lỗi thường gặp |
| `DUPLICATE_SOURCE_IN_GROUP` | ERROR | build, import | Hai mục của một nhóm trỏ cùng một nguồn |
| `UNKNOWN_ITEM_REFERENCE` | ERROR | build, import | `itemId`, `targetItemId`, khóa của `comparison` hay `expectedItemId` không thuộc nhóm |
| `ITEM_TYPE_MISMATCH` | ERROR | build, import | `sourceType` hoặc khối chi tiết khác `contentType` |
| `SOURCE_NOT_FOUND` | ERROR (build) / WARNING (import) | build, import | Không có dòng nguồn với id này |
| `SOURCE_CHANGED` | ERROR (build) / WARNING (import) | build, import | Fingerprint hoặc snapshot lệch nguồn |
| `SOURCE_AMBIGUOUS` | ERROR (build) / WARNING (import) | build, import | Tra phụ theo snapshot ra từ 2 dòng trở lên |
| `SOURCE_PACK_NOT_LISTED` | ERROR | build, import | `sourcePackId` không có trong `sourcePacks` |
| `SOURCE_SCHEMA_UNSUPPORTED` | ERROR (build) / WARNING (import) | build, import | `sourceSchemaVersion` chưa biết; app gán `UNVERIFIABLE` |
| `MISSING_CORRECT_ANSWER` | ERROR | build, import | Thiếu `correctOptionId` |
| `CORRECT_OPTION_NOT_IN_OPTIONS` | ERROR | build, import | `correctOptionId` không thuộc `options` |
| `TOO_FEW_OPTIONS` | ERROR | build, import | < 2 option (ERROR_DETECTION < 3) |
| `DUPLICATE_OPTION` | ERROR | build, import | Trùng id hoặc trùng `text` trong một câu |
| `UNSUPPORTED_QUESTION_TYPE` | ERROR | build, import | `type` ngoài 4 dạng |
| `TARGET_NOT_CORRECT_OPTION` | ERROR | build, import | Quy tắc chéo 6.3 |
| `BLANK_MARKER_INVALID` | ERROR | build, import | FILL không có, hoặc có hơn một `＿＿＿` |
| `SEGMENTS_NOT_SENTENCE` | ERROR | build, import | Các đoạn của ERROR_DETECTION không ghép thành câu |
| `MISSING_EXPLANATION` | ERROR | build, import | Câu hỏi thiếu `explanationVi`, hoặc nhóm thiếu `theory` |
| `NO_QUESTIONS` | ERROR | build, import | Nhóm không có câu hỏi |
| `TOO_FEW_ITEMS` | ERROR | build, import | Nhóm có ít hơn 2 mục |
| `FURIGANA_INVALID` | ERROR | build, import | Sai cú pháp `{…|…}` |
| `FURIGANA_MISMATCH` | ERROR | build, import | Phần gốc ghép lại khác văn bản |
| `FURIGANA_MISSING` (trường bắt buộc) | ERROR | build, import | `examples`, `commonMistakes`, `collocations` hoặc `prompt` có Kanji mà thiếu furigana. (`labelFurigana` luôn tùy chọn: nhãn của nhóm Kanji là chính chữ đó) |
| `FURIGANA_MISSING` (option, context) | WARNING | build, import | Option hoặc context có Kanji mà không có furigana (có thể cố ý) |
| `ANSWER_CHANGED_SAME_ID` | ERROR | build (`--previous`) | Cùng id câu hỏi nhưng đáp án hoặc tập option đổi ý nghĩa |
| `GROUP_DISAPPEARED` | ERROR (build) / WARNING (import) | build (`--previous`), import (so với bản đã cài) | Nhóm có trong bản trước nhưng không có mặt và không nằm trong `retiredGroups` |
| `FEW_OPTIONS` | WARNING | build, import | MULTIPLE_CHOICE hoặc CONTEXT_SELECTION chỉ có 2 option |
| `EMPTY_SECTION` | WARNING | build, import | `examples`, `memoryTips` hoặc `commonMistakes` rỗng |
| `QUESTION_TYPES_INCOMPLETE` | WARNING | build, import | Nhóm chưa có đủ 4 dạng bài |
| `LICENSE_UNVERIFIED` | INFO | build, import | `licenseVerified = false` |
| `ITEMS_CHANGED_SAME_GROUP_ID` | ERROR | build (`--previous`), import (so với nội dung đã cài) | Nhóm giữ id nhưng tập id mục thay đổi: nhóm có mục khác phải có id mới (nhóm cũ vào `retiredGroups`) |
| `REVISION_NOT_INCREMENTED` | ERROR | build (`--previous`), import (so với nội dung đã cài) | Nội dung thuộc `revision` của nhóm (mọi trường trừ `id`, `revision`, `status`, `approval`, `updatedAt`, `questions`) hoặc của câu hỏi (mọi trường trừ `id`, `revision`) thay đổi mà `revision` không tăng; hoặc `revision` giảm |

**Toàn vẹn khi import (app):**
1. Đọc và parse toàn bộ gói vào bộ nhớ, rồi chạy validator domain. Có ERROR nào thì `FAILED`, không mở transaction ghi.
2. Ghi trong **một transaction Room**: xóa dòng nội dung của gói cũ (CASCADE từ `contrast_packs`), chèn toàn bộ dòng mới.
3. Lỗi DB giữa chừng thì rollback, và gói cũ còn nguyên.
4. Bảng người học không có FK nên không bị ảnh hưởng. Test M4 phải chứng minh điều này bằng cách giả lập lỗi ở dòng cuối cùng.
5. Báo cáo gồm outcome (`IMPORTED`, `UP_TO_DATE`, `FAILED`), danh sách issue có mức độ và vị trí (`groups[0].questions[3] (id=…)`), và trạng thái tham chiếu từng mục.

### 8.1. Quy tắc dùng chung chính xác (Python ↔ Kotlin)

Cài đặt: `tools/content/contrast/validator.py` và `domain/contrast/validation/ContrastPackValidator.kt`; cùng được kiểm bằng `tools/content/contrast/fixtures/cases/*.json` (xem [CONTRAST_LAB_TOOLING.md](CONTRAST_LAB_TOOLING.md)).

- **Đường dẫn**: `groups[0].questions[3].options[1].furigana`; trường bị thiếu báo tại chính đường dẫn của trường; nhóm biến mất: `groups[id=<groupId>]`.
- **Thiếu = rỗng**: trường metadata và `explanationVi` rỗng hoặc chỉ có khoảng trắng được coi như thiếu (MISSING_METADATA / MISSING_EXPLANATION).
- **Chữ Hán** (cho FURIGANA_MISSING): U+3400–U+4DBF, U+4E00–U+9FFF, U+F900–U+FAFF và 々.
- **Furigana của `correction`**: như option (thiếu → WARNING, khóa xác nhận = id câu hỏi). `labelFurigana` luôn tùy chọn.
- **Chỗ trống**: đếm số lần xuất hiện không chồng nhau của `＿＿＿` trong `prompt.japanese`, phải đúng 1.
- **So sánh văn bản** (trùng option, ghép đoạn, furigana khớp): đúng từng ký tự, không chuẩn hóa (chỉ fingerprint mới NFC + trim).
- **Khóa xác nhận WARNING**: FURIGANA_MISSING của option `<questionId>/<optionId>`, của context/correction `<questionId>`; FEW_OPTIONS `<questionId>`; EMPTY_SECTION và QUESTION_TYPES_INCOMPLETE `<groupId>`. Xác nhận nằm ở `approval` của nhóm chứa cảnh báo. Cảnh báo nguồn khi import không cần (không thể) xác nhận.
- **`--strict` / build**: WARNING chưa xác nhận làm thất bại nhưng **vẫn giữ mức WARNING** trong báo cáo.
- **Định dạng không hỗ trợ** (`format`, `schemaVersion`): chỉ báo UNSUPPORTED_FORMAT, không kiểm tra gì thêm.
- **Phạm vi**: Python (build) = JSON Schema + quy tắc ngữ nghĩa + so với bản trước (INVALID_CONTENT_VERSION, ANSWER_CHANGED_SAME_ID, GROUP_DISAPPEARED). Kotlin (import, M1) = quy tắc ngữ nghĩa + so với gói đã cài (OLDER_OR_SAME_VERSION, GROUP_DISAPPEARED); INVALID_JSON và các SCHEMA_VIOLATION về cấu trúc JSON (trường lạ, sai kiểu, sai mẫu id) là việc của parser M4. Một vài SCHEMA_VIOLATION cấu trúc mà model Kotlin biểu diễn được (thiếu `prompt`/`correction`/`context` theo dạng bài, thiếu mảng bắt buộc, `difficulty` ngoài 1–3, trạng thái/loại lạ, quá 4 mục/6 lựa chọn) được cả hai bên báo tại cùng đường dẫn.
- **ITEMS_CHANGED_SAME_GROUP_ID / REVISION_NOT_INCREMENTED**: cả hai phía, khi có bản trước (build: `--previous`; app: nội dung đang cài). Bỏ qua khác biệt giữa `null` và trường vắng mặt. Nhóm không chuyển được thành model (đã có lỗi khác) thì không so.
- **ANSWER_CHANGED_SAME_ID**: đổi khi `type`, `targetItemId` hoặc **văn bản của lựa chọn đúng** khác bản trước. Đổi lựa chọn nhiễu chỉ hiện trong báo cáo thay đổi.
- **Nguồn khi build**: dòng nguồn được đọc như importer lưu (trim chuỗi, bỏ phần tử rỗng trong danh sách âm đọc). `sourcePacks` do build ghi, sắp theo id.

## 9. Versioning

| Trường | Ý nghĩa | Khi tăng |
|--------|---------|----------|
| `schemaVersion` | Định dạng JSON | Đổi cấu trúc không tương thích ngược. App cũ từ chối gói mới (ERROR), nên bản app mới phải ra trước hoặc cùng lúc |
| `pack.contentVersion` | Phiên bản nội dung phát hành | Mỗi lần phát hành |
| `groups[].revision` | Bản sửa của nhóm | Nội dung nhóm đổi mà vẫn giữ tập mục |
| `questions[].revision` | Bản sửa của câu hỏi | Sửa chữ hoặc giải thích, đáp án giữ nguyên ý nghĩa |
| `sourceSchemaVersion` | Bộ trường và cách tính fingerprint | Đổi bộ trường hay cách chuẩn hóa |
| `sourcePacks[].version` | Phiên bản gói nguồn lúc build | Do công cụ ghi |

Cách app xử lý dữ liệu người học khi gói thay đổi: xem [DOMAIN_CONTRACT mục 5](CONTRAST_LAB_DOMAIN_CONTRACT.md#5-versioning-và-cập-nhật-gói).

## 10. Gói mẫu

[contrast/contrast-pack.sample.json](contrast/contrast-pack.sample.json) là bản chuẩn. Tài liệu không chép lại, để tránh hai bản lệch nhau. Nội dung:

| Nhóm | Mục (nguồn thật) | Nội dung |
|------|------------------|----------|
| `cg-g-koto-ni-shiteiru-naru` (GRAMMAR) | 〜ことにしている (`n3g-g001`), 〜ことになっている (`n3g-g002`) | 2 đoạn lý thuyết, 3 dòng so sánh, 4 ví dụ có furigana và bản dịch, mẹo nhớ, 2 lỗi thường gặp (một theo mỗi hướng nhầm), **5 câu hỏi đủ 4 dạng** (2 câu điền chỗ trống cùng độ khó, để minh họa việc chọn câu mới tương đương khi ôn trì hoãn) |
| `cg-v-sameru-cold-awake` (VOCABULARY) | 冷める (`mimikara-n3-v211`), 覚める (`mimikara-n3-v505`) | Cách đọc, nghĩa, cách dùng, collocation; 2 câu hỏi; minh họa WARNING `QUESTION_TYPES_INCOMPLETE` đã được xác nhận |
| `cg-k-seki-accumulate-achieve` (KANJI) | 積 (`n3k-k07a4d`), 績 (`n3k-k07e3e`) | Nghĩa, On/Kun, bộ thủ, điểm phân biệt hình dạng; 1 câu chọn chữ, trong đó option cố ý không có furigana (WARNING `FURIGANA_MISSING` đã được xác nhận theo từng option) |

Nội dung ngữ pháp, từ vựng và Kanji trong gói mẫu do tôi soạn để đặc tả định dạng. Nó **chưa qua người kiểm duyệt nội dung thật**: `approvedBy: "reviewer-01"` chỉ là mã minh họa. Không được đóng gói vào app trước khi được duyệt thật.
