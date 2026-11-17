# Contrast Lab: importer và xác minh nguồn (M4)

Trạng thái: đã triển khai (2026-11-17). Kết quả test ở mục 11.

Tài liệu liên quan: [ARCHITECTURE](CONTRAST_LAB_ARCHITECTURE.md), [DOMAIN_CONTRACT](CONTRAST_LAB_DOMAIN_CONTRACT.md), [CONTENT_SCHEMA](CONTRAST_LAB_CONTENT_SCHEMA.md), [TOOLING](CONTRAST_LAB_TOOLING.md).

## 1. Kiến trúc

```
JSON (text)
  └─ ContrastPackParser            data/importing  cấu trúc theo schema + mô hình thô "mềm"
  └─ ContrastPackValidator         domain          40 mã ngữ nghĩa (giống validator.py)
       ├─ SourceLookup ← RoomContrastSourceResolver.lookupFor()   data/repository  đọc dòng nguồn trong Room
       ├─ InstalledContrastPack + previousGroups ← nội dung Contrast đang cài (Room)
  └─ ContrastPackCheck             data/importing  gộp kết quả cấu trúc + ngữ nghĩa (một issue cho mỗi mã + đường dẫn)
  └─ ContrastContentImporter       data/importing  quyết định → một transaction → đọc lại
       └─ OfflineContrastContentRepository.replaceInstalledPack → ContrastDao.replacePack (@Transaction)
AndroidContentImportRepository.installBundledContent()  gói học trước, rồi mới đến gói Contrast
```

Importer cũ (`ContentPackImporter`) không được dùng cho Contrast, vì hai bên có định dạng khác nhau.

## 2. Luồng kiểm tra

1. **Parse**: JSON hỏng → `INVALID_JSON`; gốc không phải object → `SCHEMA_VIOLATION` tại `""`.
2. **Cấu trúc**: cùng quy tắc với `contrast-pack.schema.json`, gồm trường bắt buộc, trường lạ, kiểu, enum, mẫu id / optionId / fingerprint / người duyệt / ngày (ngày phải tồn tại), kana của cách đọc, khoảng giá trị và yêu cầu theo dạng bài. Mỗi phát hiện được ánh xạ sang mã giống `_map_schema_error` của Python: mã riêng nếu có, nếu không thì `SCHEMA_VIOLATION`, tại cùng đường dẫn. Ví dụ, `acceptedAnswers` là trường lạ, nên bị báo `SCHEMA_VIOLATION`.
3. **Ngữ nghĩa**: `ContrastPackValidator` ở chế độ IMPORT. Dù cấu trúc có lỗi, bước này vẫn chạy trên mô hình thô (giá trị thiếu đọc thành null / "" / 0, như `.get` của Python), để báo đủ lỗi trong một lần.
4. **Nguồn**: xem mục 3.
5. **So với nội dung đang cài**: `OLDER_OR_SAME_VERSION`, `GROUP_DISAPPEARED` (WARNING), `ITEMS_CHANGED_SAME_GROUP_ID` và `REVISION_NOT_INCREMENTED` (ERROR). `previousGroups` được đọc lại từ Room thành model domain.
6. **Duyệt nội dung**: xem mục 5.

**Bằng chứng tương thích Python ↔ Kotlin:** 51 fixture dùng chung (`tools/content/contrast/fixtures/cases`). Phía Python đi qua `validate(..., "import")`, gồm schema và ngữ nghĩa. Phía Kotlin đi qua `ContrastPackCheck` với **chuỗi JSON** (parser thật). Hai bên khớp kết quả viết tay của cả 51 case, trong đó 12 case kiểm tra cấu trúc: trường lạ, sai kiểu, sai enum, thiếu trường, sai mẫu id, fingerprint sai mẫu, ngày không tồn tại, thiếu trường duyệt, cách đọc không phải kana, revision bằng 0, không có nhóm, `acceptedAnswers`.

## 3. Xác minh nguồn

`RoomContrastSourceResolver`:
- Đọc dòng `grammar` / `vocabulary` / `kanji` qua `ContrastDao`, rồi **dựng lại giá trị của gói** bằng `ContentEdits.base(row, edits)`. Dòng nội dung chứa chỉnh sửa của người học đã hợp nhất (docs/USER_CONTENT.md), còn giá trị gói nằm ở `user_content_edits.base_value`. Điều này được phát hiện khi test thiết bị đầu tiên báo `SOURCE_CHANGED` sau khi người học tự sửa nghĩa; tài liệu cũ đã giả định sai và đã được đính chính.
- Tra phụ theo pattern hoặc cách đọc cũng xét `base_value` (trường người học được phép sửa).
- Tra theo id; id nào mất thì tra phụ theo snapshot (pattern; expression + reading; character), rồi so khớp đúng từng trường.
- Trạng thái do `ContrastSourceVerifier` quyết định:

| Tên trong yêu cầu M4 | Tên trong contract (code) | Khi import | Khi hiển thị |
|---|---|---|---|
| MATCHED | `OK` | — | bình thường |
| CHANGED | `SOURCE_CHANGED` | WARNING `SOURCE_CHANGED` | cảnh báo "nguồn đã thay đổi" |
| MISSING | `SOURCE_MISSING` | WARNING `SOURCE_NOT_FOUND` (có ứng viên nếu đúng 1 dòng khớp snapshot; không tự nối) | snapshot + "mục gốc không còn" |
| AMBIGUOUS | `AMBIGUOUS` | WARNING `SOURCE_AMBIGUOUS` (không tự chọn) | như MISSING + "cần kiểm tra" |
| – | `UNVERIFIABLE` | WARNING `SOURCE_SCHEMA_UNSUPPORTED` | hiện nội dung Contrast |

- Mức độ giữ đúng đặc tả: khi build là ERROR, khi import là WARNING (gói nguồn có thể được cập nhật độc lập). Không trạng thái nào xóa nội dung hay lịch sử.
- **Fingerprint trên dữ liệu thật**:
  - Importer cũ trim chuỗi và bỏ phần tử rỗng của danh sách âm đọc. `fingerprint.py` đọc nguồn theo đúng cách đó (`stored_value`).
  - Test thiết bị `everyBundledSourceRow_hasTheToolsFingerprintInRoom` so fingerprint Kotlin của **mọi dòng nguồn trong Room** (1.959 dòng: 181 ngữ pháp, 910 từ vựng, 868 Kanji) với file xuất từ Python (`app/src/androidTest/assets/contrast/source-fingerprints.json`, có test Python chặn file cũ).
  - Một test khác nạp dòng có khoảng trắng thừa và âm đọc rỗng, rồi chứng minh fingerprint của Room bằng fingerprint của công cụ, và khác fingerprint tính trên giá trị thô.
- Thuật toán fingerprint không đổi.

## 4. Kết quả import

`ContrastImportReport.outcome`:
- `IMPORTED`: đã ghi trong một transaction và đọc lại khớp.
- `UP_TO_DATE`: có `OLDER_OR_SAME_VERSION`, không ghi gì.
- `FAILED`, với `failure`:
  - `INVALID`: có ERROR.
  - `UNACKNOWLEDGED_WARNING`: còn WARNING biên tập chưa xác nhận; gói phát hành không được mang loại này (mức độ vẫn là WARNING).
  - `DATABASE`: ghi lỗi, đã rollback.
  - `NOT_VERIFIED`: đọc lại không khớp.

Thứ tự quyết định: ERROR → FAILED; rồi phiên bản không mới hơn → UP_TO_DATE; rồi WARNING chưa xác nhận → FAILED; còn lại thì ghi. Báo cáo còn có danh sách `approvals` (ai soạn, ai duyệt, revision; `selfApproved`).

## 5. Duyệt nội dung (đúng phạm vi)

1. **Cấu trúc `approval`** (parser): đủ `authoredBy`, `approvedBy`, `approvedAt`, `approvedRevision`, `aiAssisted`, `acknowledgedWarnings`, đúng mẫu.
2. **Metadata** (validator): có `approval` khi PUBLISHED; `approvedRevision` phải bằng `revision`.
3. **Người duyệt thật**: chỉ công cụ phát hành kiểm (`build_pack.py --reviewers`). App không có danh sách người duyệt, và một chuỗi mã trong JSON không phải bằng chứng duyệt (không có chữ ký số).
4. **Điều kiện import**: mọi nhóm là `PUBLISHED`.

Tự duyệt được phép và luôn hiện trong báo cáo.

## 6. Transaction, rollback, idempotency

- `ContrastDao.replacePack` (`@Transaction`): xóa nội dung của cùng pack id (CASCADE chỉ trong bảng nội dung) và các nhóm cùng id, rồi chèn toàn bộ. Lỗi ở bất kỳ dòng nào thì rollback, nội dung cũ còn nguyên. Đã kiểm bằng trigger SQLite cố ý làm hỏng lệnh chèn cuối cùng.
- Bảng người học không có FK, nên không bao giờ bị ghi hay xóa bởi import.
- Import lại cùng phiên bản trả về `UP_TO_DATE` và không ghi gì, kể cả `installed_at`. Bản cũ hơn có nội dung khác bị từ chối (revision giảm), bản cũ hơn y hệt thì `UP_TO_DATE`; cả hai trường hợp đều không ghi.

## 7. Cập nhật nội dung và bảo toàn lịch sử

Test `contentUpdates_neverLoseOrRewriteLearnerHistory` (trên nguồn thật):
- Bản 1 → học (có câu sai, khối được lập lịch).
- Bản 2 gồm: sửa lý thuyết (revision nhóm 2), thêm câu hỏi, đổi đáp án bằng câu hỏi id mới (id cũ bị bỏ), lưu trữ nhóm từ vựng và thay bằng nhóm id mới.
- Sau khi nhập bản 2:
  - 5 bảng người học giống từng byte.
  - Attempt của câu hỏi đã bỏ vẫn còn snapshot và kết quả chấm gốc (`contentVersion` = 1).
  - Nhóm đã lưu trữ giữ tiến độ, không còn đến hạn, đọc được lý do.
  - Nhóm thay thế không nhận tiến độ.
- Nhập lại bản 2 cho `UP_TO_DATE`; nhập lại bản 1 bị từ chối; không có gì thay đổi.

## 8. Cài gói đóng sẵn

`AndroidContentImportRepository.installBundledContent()`:
1. Cài các gói học như trước; danh sách kết quả trả về không đổi.
2. Chỉ khi **không gói học nào FAILED** mới cài `CONTRAST_BUNDLED_PACK_ASSETS`, theo đúng thứ tự.
3. Lỗi Contrast không bao giờ ném ra ngoài và không làm hỏng chức năng học. Lỗi được ghi log và lưu vào `lastContrastReports`, không vào hộp thoại lỗi của gói học. Lần khởi động sau thử lại, vì chưa có gì được ghi.
- Gói chưa đổi trả về `UP_TO_DATE`.
- Không gọi mạng.
- **`CONTRAST_BUNDLED_PACK_ASSETS` đang rỗng**: chưa có gói nào được duyệt thật. Gói mẫu không nằm trong assets của app (có test cả ở Python lẫn trên thiết bị).
- Test dùng một **bản sao chỉ có trong APK test**: `app/src/androidTest/assets/contrast/test-pack-sample-copy.json`, có test Python bảo đảm giống hệt gói mẫu.
- Chưa có giao diện báo lỗi Contrast (M5).

## 9. Phiên bản tiến độ (`progressVersion`)

- `CONTRAST_PROGRESS_VERSION` = 1 (M1) chưa được lưu trong DB. M3 ngầm định nó theo Room v16.
- **Đủ để diễn giải hiện tại:**
  - Mỗi review lưu rating đã quy đổi, bậc trước và sau, `dueAtAfter` tuyệt đối, và `contentVersion`.
  - Mỗi attempt lưu kết quả chấm gốc và snapshot.
  - Đổi ngưỡng rating hay thành thạo không làm sai lịch sử, vì mức thành thạo được tính lại từ attempt và review.
- **Thiếu:**
  - `contrast_progress.step_index` là vị trí trên **thang hiện hành**. Nếu sau này thang Contrast đổi, không có metadata cho biết bậc cũ thuộc thang nào. `due_at` vẫn đúng vì là thời điểm tuyệt đối; bậc thì được kẹp như SRS cũ.
  - Review cũng không ghi thang hay phiên bản quy tắc đã dùng.
- **Đề xuất, chưa làm, cần quyết định:** khi lần đầu đổi thang hoặc quy tắc, thêm migration v17 với cột `progress_version` (hoặc `ladder_code`) trên `contrast_progress` và `contrast_reviews`, giá trị mặc định 1 cho dòng cũ. Không cần làm trước khi có thay đổi, nên M4 không sửa Room v16.

## 10. Giới hạn hiện tại

- Chưa có gói Contrast phát hành; cần `reviewers.json` thật và duyệt thật nội dung.
- Chưa có giao diện (M5): lỗi cài Contrast chỉ ghi log và lưu trong `lastContrastReports`.
- Kiểu số nguyên viết dạng `1.0` bị parser từ chối, trong khi JSON Schema chấp nhận; đây là khác biệt nhỏ, theo hướng chặt hơn.
- Trường sai kiểu có thể sinh thêm lỗi ngữ nghĩa ở một phía so với phía kia. Cả hai phía đều có ERROR, nên gói luôn bị từ chối như nhau.
- Chỉ kiểm trên emulator API 36.

## 11. Test

| Nhóm | Test | Nơi chạy |
|---|---|---|
| Parser + ngữ nghĩa | `ContrastSharedFixturesTest` (51 fixture qua parser thật, JSON hỏng, gốc không phải object) | JVM |
| Fixture, fingerprint, CLI, file test | `tools/content/contrast/test_contrast_tools.py` (25 test) | Python |
| Resolver | `RoomContrastSourceResolverTest` (OK / CHANGED / MISSING / AMBIGUOUS / UNVERIFIABLE, trim, âm đọc rỗng, người học tự sửa, gói nguồn cập nhật, 1.959 dòng nguồn thật) | thiết bị |
| Importer | `ContrastContentImporterTest` (thành công, lặp, lỗi, cấu trúc, warning, duyệt, revision, tập mục, rollback, bảo toàn lịch sử, khởi động lại) | thiết bị |
| Cài gói đóng sẵn | `ContrastBundledInstallTest` (thứ tự, gói nguồn lỗi, không cài lại, giữ tiến độ, app không mang gói Contrast) | thiết bị |
