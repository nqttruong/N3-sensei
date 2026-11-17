# Contrast Lab: kiến trúc

Trạng thái: **đặc tả, chưa triển khai** (2026-11-17). Đây là căn cứ để làm M1 (domain) và M2 (công cụ tạo nội dung + gói mẫu).

Tài liệu liên quan:
- [CONTRAST_LAB_AUDIT.md](CONTRAST_LAB_AUDIT.md): khảo sát gốc, giữ nguyên để đối chiếu.
- [CONTRAST_LAB_DOMAIN_CONTRACT.md](CONTRAST_LAB_DOMAIN_CONTRACT.md): models, repository, use case, quy tắc domain.
- [CONTRAST_LAB_CONTENT_SCHEMA.md](CONTRAST_LAB_CONTENT_SCHEMA.md): định dạng JSON, vòng đời nội dung, validation.
- Schema máy đọc: [contrast/contrast-pack.schema.json](contrast/contrast-pack.schema.json).
- Gói mẫu: [contrast/contrast-pack.sample.json](contrast/contrast-pack.sample.json).

---

## 1. Tổng quan và phạm vi

Contrast Lab giúp người học phân biệt các mục dễ nhầm. Có ba loại nhóm: **ngữ pháp**, **từ vựng** và **Kanji**. Mỗi nhóm so sánh 2–4 mục.

Mỗi nhóm có:
- lý thuyết và bảng so sánh;
- ví dụ có furigana và bản dịch tiếng Việt;
- mẹo nhớ và lỗi thường gặp;
- bài luyện thuộc bốn dạng: chọn đáp án, tìm lỗi sai, điền chỗ trống, chọn theo ngữ cảnh.

Kết quả luyện được dùng để lập lịch ôn riêng và phân tích hướng nhầm lẫn.

**Thuộc phạm vi bản đầu:**
- Gói nội dung đóng sẵn trong APK. Chỉ nội dung PUBLISHED được dùng.
- Xem nhóm, luyện tập, tiếp tục phiên dở dang.
- Lịch ôn riêng, lịch sử trả lời, phân tích lỗi, tiến độ.
- Chạy hoàn toàn offline.

**Ngoài phạm vi bản đầu:**
- AI trong app.
- Người học tự nhập gói Contrast (sẽ là một quyết định riêng).
- Contrast trong backup (milestone riêng).
- Hiện Contrast ở Trang chủ, Ôn tập, nhắc nhở hay widget.
- Nhập đáp án bằng bàn phím.

## 2. Quyết định đã chốt (không thay đổi)

| # | Quyết định | Hệ quả thiết kế |
|---|------------|-----------------|
| D1 | Contrast Lab nằm **trong tab Học**, không có tab thứ 6, và 5 tab hiện tại giữ nguyên | Một mục mới trong `LearnHubScreen`; mọi màn hình Contrast có `tab = LEARN` |
| D2 | AI chỉ dùng **ngoài app**: AI tạo nháp → người duyệt → APPROVED → PUBLISHED → gói JSON → import | APK không có AI client, không có API key, không gọi mạng. App chỉ nhận PUBLISHED |
| D3 | **Không FK** tới `grammar`/`vocabulary`/`kanji`; tham chiếu bằng `sourceType`, `sourceContentId`, `sourceSnapshot`, `sourceFingerprint`, `sourceSchemaVersion` | Xác minh tham chiếu ở 3 thời điểm (mục 7). Không bao giờ tự xóa nhóm hay tiến độ |
| D4 | SRS riêng, **không** thêm vào `ItemType`, không đổi SRS cũ | Có bảng progress/review riêng. Chỉ dùng lại các hàm thuần (mục 8) |
| D5 | Sổ lỗi riêng, không dùng `error_records`/`review_items` của đề thi | Có bảng attempt riêng và phân tích riêng |
| D6 | Bản đầu **không** đưa vào backup, không sửa backup hiện có | Ghi rõ trong UI. Backup Contrast là milestone riêng |

## 3. Đối chiếu tài liệu với code thực tế

Khi tài liệu và code khác nhau, tài liệu này theo code.

| Chủ đề | Tài liệu cũ nói | Code thực tế (đã kiểm tra) | Xử lý |
|--------|-----------------|----------------------------|-------|
| Dùng lại scheduler | Audit: "dùng lại `SpacedRepetitionScheduler`" | `schedule()` nhận `ItemRef(type: ItemType, …)` và trả về `LearningProgress`, tức là gắn với `ItemType` | Chỉ dùng lại `nextStep(currentStep, rating)`, `ReviewLadder` và `ReviewInterval.addTo(now, zone)`. Đây là các hàm thuần, không phụ thuộc `ItemType`. **Không gọi `schedule()`** |
| Cài gói đóng sẵn | Audit: đăng ký gói Contrast vào `BUNDLED_PACK_ASSETS` | Danh sách đó chỉ chứa gói định dạng `n3sensei-content-pack`, nhập bằng `ContentPackImporter`. Gói Contrast có định dạng khác | Dùng importer riêng. `AndroidContentImportRepository.installBundledContent()` gọi thêm importer Contrast **sau** các gói nguồn, để dùng chung hộp thoại lỗi `BundledContentFailedDialog` (sửa tối thiểu ở M4) |
| Mức độ lỗi import | `ImportReport` hiện có: `FATAL` / `ERROR` (bỏ qua bản ghi, nhập phần còn lại) / `WARNING` | Contrast cần ERROR / WARNING / INFO, và ERROR thì không được nhập | Contrast có kiểu báo cáo riêng. **Có ERROR thì cả gói bị từ chối**, không nhập từng phần (chặt hơn importer cũ). Không dùng lại `ImportRecordType` |
| Bản chụp nguồn | Audit: một cột `label_snapshot` | Yêu cầu mới: snapshot + fingerprint + schema version | Theo mục 7 và CONTENT_SCHEMA |
| Bảng Room | Audit đề xuất 8 bảng | Cần thêm option, phiên học, lịch sử lập lịch | Thiết kế lại ở mục 10 (chỉ ở mức thiết kế) |
| `docs/ARCHITECTURE.md` | "trạng thái thực tế sau Prompt 12", danh sách feature cũ | Code còn có `exam`, `translate`, `edit`, `data/sync` | Chỉ ghi nhận, không sửa trong giai đoạn này |
| `docs/DATABASE.md` | Mục Migration chưa có bước 14 → 15 | `N3Database.VERSION = 15`, có `MIGRATION_14_15` (bảng `synced_*`) | Chỉ ghi nhận. Contrast sẽ là v16 |
| Chỉnh sửa của người dùng | – | **Đính chính (M4, có bằng chứng):** bảng `kanji`/`vocabulary`/`grammar` lưu **giá trị đã hợp nhất** (chỉnh sửa của người học ghi đè lên dòng); giá trị của gói nằm ở `user_content_edits.base_value` (docs/USER_CONTENT.md). Áp cho Kanji, từ vựng **và ngữ pháp**. Nhận định cũ "dòng gốc giữ nguyên" là sai | Resolver dựng lại giá trị gói bằng `ContentEdits.base(row, edits)` (hàm khôi phục sẵn có của app) rồi mới tính fingerprint; test `aLearnersOwnMeaning_doesNotChangeTheSourceFingerprint` (xem mục 7.3) |

## 4. Điều hướng

- `feature/learn/LearnHubScreen.kt` thêm một `LearnSectionItem` (ký hiệu đề xuất "比"), có callback `onOpenContrast` với giá trị mặc định `{}` để các lời gọi cũ không phải đổi.
- `app/navigation/Routes.kt`:
  - `ContrastGraphRoute` là nested graph bên trong `LearnGraphRoute`.
  - Route con: `ContrastHomeRoute`, `ContrastGroupRoute(groupId)`, `ContrastPracticeRoute(scope…)`, `ContrastResultRoute(sessionId)`, `ContrastMistakesRoute`.
- `AppScreen.kt`: mỗi route một mục, `tab = TopLevelDestination.LEARN`. Riêng phiên luyện có `isFocusSession = true`, giống `EXAM_SESSION`.
- `N3SenseiNavHost.kt`: block `navigation<ContrastGraphRoute>`. Từ nhóm Contrast có thể mở màn chi tiết sẵn có qua `GrammarDetailRoute(grammarId)`, `VocabularyDetailRoute(vocabularyId)` và `KanjiDetailRoute(kanjiId)`, thông qua callback do NavHost truyền vào.
- **Không sửa** `TopLevelDestination.kt`, `N3SenseiApp.kt` hay `NavigationActions.kt`.

## 5. Ranh giới tầng

Bắt buộc theo `ArchitectureRulesTest`.

```
feature/contrast/          Screen + ViewModel. Không import feature khác, androidx.navigation, app.*, data.*, androidx.room
      │ (ViewModel gọi use case / repository interface)
domain/contrast/           Kotlin thuần: models, rules, planner, scheduler policy, analyzer, validator
domain/repository/         ContrastContentRepository, ContrastSourceResolver, ContrastPracticeRepository, ContrastProgressRepository
      ▲ (cài đặt)
data/local/…               Entities + ContrastDao (v16)
data/repository/           OfflineContrast*Repository, RoomContrastSourceResolver (CHỈ ĐỌC bảng nội dung hiện có qua ContentDao)
data/importing/            ContrastPackImporter (parse JSON → domain validator → ghi trong 1 transaction)
app/di/AppContainer.kt     các repository Contrast (by lazy)
app/navigation/            routes + NavHost
tools/content/contrast/    (ngoài APK) AI nháp → duyệt → build_pack.py --strict
```

- **Validator nằm ở domain** (`domain/contrast/validation`), là Kotlin thuần, để unit test được và tách khỏi JSON. Data parse JSON thành DTO rồi chuyển thành model để validate.
- Công cụ Python và validator Kotlin cài cùng một bộ quy tắc. Để tránh lệch nhau, hai bên chạy chung **bộ fixture** `tools/content/contrast/fixtures/{valid,invalid}/*.json`, mỗi file kèm danh sách mã lỗi mong đợi, bằng cả pytest lẫn JUnit.
- Feature Contrast không đọc `ProgressRepository`, `ReviewSettings` hay `UserPreferences`. Cài đặt Contrast có DataStore riêng (`contrast_settings`) để không phải thêm field vào `UserPreferences` (data class dùng chung, có nhiều fake trong test).

## 6. Repository và use case

Xem chi tiết trong DOMAIN_CONTRACT. Tóm tắt:

| Repository | Đọc / ghi | Trách nhiệm |
|------------|-----------|-------------|
| `ContrastContentRepository` | chỉ đọc | Nhóm, mục, lý thuyết, ví dụ, câu hỏi; tìm kiếm; thông tin gói đã cài |
| `ContrastSourceResolver` | chỉ đọc | Đọc dòng gốc của grammar/vocabulary/kanji, tính trạng thái tham chiếu |
| `ContrastPracticeRepository` | đọc/ghi | Phiên luyện (tạo, tiếp tục, bỏ dở), ghi attempt (chống trùng bằng `requestId`), truy vấn lịch sử/thống kê |
| `ContrastProgressRepository` | đọc/ghi | Tiến độ và lịch ôn của từng nhóm, lịch sử lập lịch (review), danh sách đến hạn |

Đề xuất gốc có `ContrastReviewRepository` riêng; tôi gộp nó vào `ContrastProgressRepository`. Lý do: tiến độ và lịch ôn là cùng một dòng (giống `learning_progress`), và lần lập lịch phải được ghi cùng transaction với attempt cuối của khối câu hỏi. Tách làm hai repository sẽ phải điều phối transaction xuyên repository.

Use case sau khi gộp (lý do ở DOMAIN_CONTRACT mục 3):
`ObserveContrastGroups` (kiêm tìm kiếm), `GetContrastGroupDetail`, `StartOrResumePractice`, `SubmitContrastAnswer` (kiêm lập lịch và cập nhật tiến độ), `GetPracticeResult`, `AnalyzeContrastMistakes`, `GetContrastProgress`, `GetDueContrastReviews`.

## 7. Tham chiếu nguồn

### 7.1. Định danh và fingerprint

Mỗi mục so sánh có:
- `sourceType` (GRAMMAR/VOCABULARY/KANJI), `sourceContentId` (id dòng hiện có) và `sourcePackId`;
- `sourceSchemaVersion`: phiên bản bộ trường dùng cho snapshot và fingerprint;
- `sourceSnapshot`: các trường đó, chép nguyên văn từ gói nguồn;
- `sourceFingerprint`: `sha256:` + hex của chuỗi chuẩn hóa. Cách tính nằm ở CONTENT_SCHEMA mục 5.

Bộ trường của `sourceSchemaVersion = 1`:
- GRAMMAR: `pattern`, `structure`, `meaningVi`
- VOCABULARY: `expression`, `reading`, `meaningVi`
- KANJI: `character`, `onReadings`, `kunReadings`, `meaningVi`

Đây là các trường quyết định "đây có còn là mục đó không"; không đưa ví dụ, ghi chú hay thứ tự vào.

### 7.2. Ba thời điểm xác minh

| Thời điểm | Ai | Đối chiếu với | Kết quả |
|-----------|----|---------------|---------|
| Tạo gói | `build_pack.py --strict` | File gói nguồn trong `app/src/main/assets/content/*.json`, đúng phiên bản ghi trong `sourcePacks` | Mọi lệch đều là **ERROR**: id không có, snapshot hay fingerprint lệch, nhiều ứng viên, sai loại. Gói không được tạo |
| Import | `ContrastPackImporter` | Bảng nội dung trong DB (dòng gốc) | Lệch là **WARNING** và vẫn nhập; trạng thái từng mục ghi vào báo cáo. Lý do: gói nguồn có thể được cập nhật độc lập, nên Contrast không được chặn |
| Hiển thị | `ContrastSourceResolver` (gom một truy vấn `IN (…)` cho mỗi nhóm) | Bảng nội dung lúc đó | Tính lại mỗi lần đọc, không lưu cache, vì gói nguồn có thể đổi sau khi Contrast đã nhập |

### 7.3. Trạng thái tham chiếu và hành vi

`SourceRefStatus` là phép đo **tham chiếu nguồn**, tách hẳn khỏi lỗi nội dung Contrast (là kết quả validation của gói, mục 9).

| Trường hợp | Trạng thái | Hiển thị cho người học | Luyện tập / tiến độ |
|------------|-----------|------------------------|---------------------|
| Id có, fingerprint khớp | `OK` | Bình thường; có liên kết mở chi tiết mục gốc | Bình thường |
| Id có, fingerprint khác | `SOURCE_CHANGED` | Hiện nội dung của Contrast kèm cảnh báo "Nội dung nguồn đã thay đổi sau khi nhóm này được soạn". Vẫn mở được mục gốc | Bình thường |
| Id không tồn tại | `SOURCE_MISSING` | Hiện `sourceSnapshot` kèm nhãn "Mục gốc không còn trong dữ liệu học"; không có liên kết | Bình thường; **không xóa gì** |
| Nhiều bản ghi phù hợp | `AMBIGUOUS` | Như `SOURCE_MISSING`, kèm "cần kiểm tra"; **không tự chọn** | Bình thường |
| `sourceSchemaVersion` app không hỗ trợ | `UNVERIFIABLE` | Hiện nội dung Contrast, không cảnh báo người học; ghi vào báo cáo import | Bình thường |

- Tra cứu chính theo khóa chính `(sourceType, sourceContentId)`, nên một id không thể khớp nhiều dòng. `AMBIGUOUS` chỉ xảy ra khi tra phụ theo snapshot (lúc build, hoặc lúc import khi id không còn) cho ra từ 2 dòng trở lên. Ngay cả khi tra phụ chỉ ra 1 dòng, **app cũng không tự nối lại**, mà chỉ ghi "có ứng viên `<id>`" vào báo cáo. Việc nối lại chỉ làm trong công cụ, khi người duyệt sửa `sourceContentId` và phát hành phiên bản mới.
- Chỉnh sửa của người dùng **không** làm đổi fingerprint: resolver tính trên giá trị của gói, dựng lại bằng `ContentEdits.base` từ `user_content_edits.base_value` (vì dòng nội dung chứa giá trị đã hợp nhất). Màn chi tiết mục gốc vẫn hiện bản đã chỉnh, như hiện nay.
- Nội dung học của nhóm (lý thuyết, ví dụ, câu hỏi) **tự đủ** trong gói Contrast, nên tham chiếu nguồn hỏng không bao giờ chặn việc học.

## 8. SRS riêng

- Đơn vị lập lịch là **nhóm** (`groupId`), không phải câu hỏi. Mục tiêu là phân biệt được cả nhóm; câu hỏi chỉ là phương tiện đo.
- Một **khối** là k câu của cùng một nhóm trong một phiên (mặc định k = 3, thử nghiệm). Lịch chỉ được cập nhật khi khối hoàn tất.
- Kết quả khối được quy ra mức đánh giá **tự động**, người học không tự chấm:
  - Đúng 100%, không bỏ qua câu nào → `GOOD`.
  - Từ 60% trở lên → `HARD`.
  - Dưới 60% → `AGAIN`.
  - Bỏ qua từ một nửa số câu trở lên → **không cập nhật lịch** (chưa đủ bằng chứng), nhóm vẫn đến hạn.
  - Không dùng `EASY`.
  - Các ngưỡng này là cấu hình thử nghiệm.
- Bậc tiếp theo tính bằng `SpacedRepetitionScheduler(ladder).nextStep(step, rating)`, và hạn ôn bằng `ladder[step].addTo(now, zone)`. Đây là đúng các hàm của SRS cũ, chạy trên **thang riêng** do Contrast truyền vào; không ghi gì vào bảng SRS cũ.
- **Thang mặc định đề xuất (thử nghiệm):** 1 ngày, 3 ngày, 7 ngày, 14 ngày, 30 ngày, 60 ngày.
  - Không dùng các bậc 20 phút và 1 giờ của thang cũ. Thang cũ dành cho flashcard tự chấm, cần nhắc lại ngắn hạn; còn khối Contrast nhiều câu thì việc ôn ngắn hạn đã xảy ra trong phiên (câu sai được hỏi lại ở cuối phiên bằng một câu khác).
  - Bậc 7 ngày bảo đảm thu được chỉ số chính là **độ chính xác sau 7 ngày**.
  - Thang này hợp lệ với `ReviewLadder.validate` (tăng dần, ≤ 12 bậc, ≤ 366 ngày).
- Trạng thái thành thạo của Contrast (`NEW`, `LEARNING`, `REVIEWING`, `MASTERED`) **tính từ lịch sử trả lời**, không lấy từ `ProgressState` của thang. Xem DOMAIN_CONTRACT mục 4.
- Giới hạn nhóm mới mỗi ngày là của riêng Contrast (mặc định 3, thử nghiệm), không đụng `DailyLimits`.

## 9. Phân tích lỗi

Tính từ `contrast_attempts`, bằng truy vấn gộp trong SQL theo trang; domain chỉ diễn giải kết quả. Các chỉ số:
- tổng số câu đã làm; đúng, sai, bỏ qua; accuracy = đúng / (đúng + sai), không tính câu bỏ qua;
- nhóm hay nhầm nhất;
- cặp nhầm **có hướng** A → B (đáp án là A, người học chọn B);
- dạng bài hay sai;
- lỗi lặp lại: cùng cặp nhầm, hoặc cùng câu bị sai ở từ 2 phiên khác nhau trở lên;
- lần sai gần nhất;
- kết quả ôn trì hoãn.

**Chỉ số chính: độ chính xác sau 7 ngày.**
- Tính trên các câu trả lời mà lần trả lời trước đó cho cùng nhóm cách ít nhất 7 ngày.
- Ưu tiên tập "trì hoãn + câu mới": câu hỏi người học chưa từng làm, cùng `difficulty`. Planner ưu tiên chọn những câu như vậy khi nhóm đến hạn.
- Báo cáo riêng hai con số: tất cả câu trì hoãn, và câu trì hoãn chưa từng làm.

## 10. Database v16 (chỉ thiết kế)

Chỉ tạo bảng mới. Không `ALTER` hay `DROP` bảng v15. Không FK tới bảng nội dung hay SRS cũ.

| Nhóm | Bảng | Ghi chú |
|------|------|---------|
| Nội dung (thay theo gói) | `contrast_packs` | id, content_version, schema_version, title, published_at, license_text, license_verified, installed_at |
| | `contrast_groups` | FK pack CASCADE; content_type, jlpt_level, title, summary_vi, revision, updated_at, sort_order, search_text |
| | `contrast_items` | FK group CASCADE; label, label_furigana, source_type, source_content_id, source_pack_id, source_schema_version, source_snapshot_json, source_fingerprint, details_json (chi tiết theo loại), sort_order. Index `(source_type, source_content_id)` |
| | `contrast_notes` | FK group CASCADE; kind (THEORY/COMPARISON/TIP/MISTAKE), sort_order, payload_json. Đây là nội dung chỉ để hiển thị, không cần truy vấn theo trường |
| | `contrast_examples` | FK group CASCADE; item_id, japanese, furigana, translation_vi |
| | `contrast_questions` | FK group CASCADE; revision, type, difficulty, target_item_id, instruction_vi, prompt_json, context_json, correction_json, correct_option_id, explanation_vi. Index `(group_id, type)` |
| | `contrast_options` | FK question CASCADE; PK `(question_id, option_id)`; text, furigana, item_id, feedback_vi, sort_order |
| | `contrast_retired_groups` | FK pack CASCADE; group_id, retired_in_version, reason_vi, replaced_by |
| Người học (không bao giờ bị xóa khi cập nhật gói; không FK) | `contrast_progress` | PK group_id; step_index, due_at, last_reviewed_at, review_count, lapse_count, created_at, updated_at. Index `due_at` |
| | `contrast_reviews` | id auto; group_id, session_id, reviewed_at, answered, correct, incorrect, skipped, rating, step_before, step_after, due_at_after, content_version. Index `(group_id, reviewed_at)` |
| | `contrast_sessions` | PK id; scope, status (ACTIVE/COMPLETED/ABANDONED), started_at, last_activity_at, content_version |
| | `contrast_session_items` | PK `(session_id, position)`; group_id, question_id, question_revision, block_index |
| | `contrast_attempts` | PK request_id; session_id, position, group_id, group_revision, question_id, question_revision, question_type, content_pack_id, content_version, outcome, chosen_option_id, correct_option_id, chosen_item_id, expected_item_id, chosen_text, correct_text, answered_at. Index `(group_id, answered_at)`, `(question_id)`, `(session_id, position)` UNIQUE |
| | `contrast_meta` | key/value; `progress_version` |

- Migration `MIGRATION_15_16` chỉ gồm `CREATE TABLE/INDEX IF NOT EXISTS`, chép từ `schemas/16.json`.
- `N3DatabaseMigrationTest` phải chứng minh mọi dòng v15 còn nguyên.
- `progress_version` khởi tạo bằng 1 (xem DOMAIN_CONTRACT mục 5).

## 11. Versioning

| Khái niệm | Ở đâu | Quy tắc |
|-----------|-------|---------|
| `schemaVersion` | gói JSON | Định dạng JSON. App hỗ trợ một tập cố định (hiện là `{1}`); gói có version khác bị **ERROR** |
| `contentVersion` | gói JSON, `contrast_packs`, mỗi attempt/review | Số nguyên tăng dần mỗi lần phát hành gói. Gói đóng sẵn chỉ được nhập khi lớn hơn bản đã cài (bằng thì `UP_TO_DATE`, không ghi gì) |
| `revision` của nhóm / câu hỏi | gói JSON, mỗi attempt | Tăng khi nội dung đổi mà vẫn giữ ý nghĩa. Đổi ý nghĩa thì phải dùng id mới (CONTENT_SCHEMA mục 7) |
| `sourceSchemaVersion` | mỗi mục | Bộ trường và cách tính fingerprint |
| `progressVersion` | `contrast_meta` | Định dạng và ý nghĩa của dữ liệu người học Contrast. Chỉ tăng khi cách diễn giải dữ liệu đã lưu thay đổi, kèm migration hoặc chuyển đổi có test. Backup Contrast sau này sẽ dùng nó |
| Room `VERSION` | `N3Database` | 15 → 16 cho Contrast |

Khi gói Contrast được cập nhật: **không xóa** attempt, progress, review hay session, và **không reset** lịch ôn. Cách xử lý câu hỏi đã đổi hoặc bị bỏ nằm ở DOMAIN_CONTRACT mục 5.

## 12. Quy tắc tương thích codebase

1. Một module `:app`; không thêm dependency Gradle (JSON dùng `kotlinx.serialization` có sẵn, hash dùng `java.security.MessageDigest`).
2. DI thủ công: chỉ thêm các thuộc tính `by lazy` vào `AppContainer`/`DefaultAppContainer`.
3. Giữ `ArchitectureRulesTest` xanh (mục 5).
4. Room 15 → 16, không dùng destructive migration.
5. Không sửa `ItemType`, `learning_progress`, `review_records`, `item_marks`, `review_sessions`, `domain/srs/*`.
6. Không sửa `TopLevelDestination`, `N3SenseiApp`, backup (`data/backup/*`, `BackupDao`), `UserPreferences`.
7. Contrast không làm thay đổi bất kỳ con số nào ở Trang chủ, Ôn tập, Thống kê, nhắc nhở hay widget. Điều này được kiểm tra bằng test (DOMAIN_CONTRACT mục 6).

## 13. Triển khai M3: Room v16 (2026-11-17)

- `N3Database.VERSION = 16`, `N3Migrations.MIGRATION_15_16` chỉ gồm 13 `CREATE TABLE` và 16 `CREATE INDEX`, chép từ `app/schemas/.../16.json`. Đã so sánh 15.json và 16.json: 45 bảng cũ giống hệt nhau, chỉ thêm 13 bảng `contrast_*`.
- Entity: `data/local/entity/ContrastEntities.kt`. DAO: `data/local/dao/ContrastDao.kt`. Mapping: `data/repository/ContrastMappers.kt`. Repository Room: `data/repository/OfflineContrastRepositories.kt` (chưa nối vào `AppContainer`; việc đó thuộc M4/M5).
- **Nội dung** (`contrast_packs` → `groups` → `items`, `notes`, `examples`, `questions` → `options`; `retired_groups`): CASCADE chỉ bên trong các bảng nội dung; `replacePack` thay cả gói trong một transaction.
- **Người học** (`contrast_sessions`, `contrast_session_items`, `contrast_attempts`, `contrast_progress`, `contrast_reviews`): **không có FK nào**, nên cài, cập nhật hay lưu trữ nội dung không bao giờ xóa lịch sử (D4).
- **Bất biến lưu trữ**:
  - `contrast_attempts`: PK `request_id` (idempotency) và UNIQUE(`session_id`, `position`).
  - `contrast_reviews`: UNIQUE(`session_id`, `group_id`), mỗi khối chỉ được lập lịch một lần.
  - `recordAnswer` là một `@Transaction`: ghi attempt, review, progress và hoạt động của phiên cùng lúc; lỗi ở bất kỳ bước nào thì rollback toàn bộ.
- Khác thiết kế ở §10:
  - Bảng `contrast_meta` không được tạo. `progressVersion` hiện bằng 1 và được ngầm định theo Room v16; sẽ thêm bảng khi lần đầu cần đổi phiên bản này (tránh bảng dự phòng).
  - `contrast_examples` có thêm `sort_order`.
  - Thông tin duyệt (`approval`) không lưu trong DB vì model domain không dùng; nó vẫn nằm trong gói JSON và báo cáo build.

## 14. Triển khai M5: giao diện học (2026-11-17)

Chi tiết: [CONTRAST_LAB_UI.md](CONTRAST_LAB_UI.md). Điểm tích hợp thực tế, khác dự kiến ở §4:
- Không dùng nested graph: 3 route `ContrastHomeRoute`, `ContrastLibraryRoute`, `ContrastGroupRoute(groupId)` nằm trực tiếp trong graph của tab Học, giống các màn hình Học khác. Chưa có route luyện tập hay kết quả (M6/M7).
- Mục "Contrast Lab — Mới" đặt ở cuối LearnHub, để không đẩy các mục cũ xuống dưới.
- `AppContainer` có thêm 4 repository Contrast (`lazy`).
- Package giao diện là `feature/contrast`.
