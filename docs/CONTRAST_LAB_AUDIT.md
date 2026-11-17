# Contrast Lab: báo cáo khảo sát và kế hoạch

Ngày khảo sát: 2026-11-17, trên nhánh `main` (HEAD `07f0d0b`, kèm phần Kanji N2 chưa commit).
Đây chỉ là khảo sát và kế hoạch: không có source code, schema hay cấu hình Gradle nào bị sửa.

---

## Tóm tắt các xung đột với yêu cầu

| # | Yêu cầu | Thực tế trong code | Đề xuất an toàn nhất |
|---|---------|--------------------|----------------------|
| 1 | Tab riêng ở thanh điều hướng dưới | Thanh dưới đã có **5 tab** (`TopLevelDestination`: HOME, LEARN, REVIEW, STATS, SETTINGS). Code ghi rõ "Phones get at most five, per Material guidance". Tab thứ 6 sẽ làm nhãn bị cắt trên điện thoại nhỏ và làm hỏng các test điều hướng hiện có. | **A (khuyến nghị):** đặt Contrast Lab làm mục đầu tiên trong tab Học (`LearnHubScreen`), giống Đề thi và Sổ lỗi sai, không đụng thanh dưới. **B:** nếu bắt buộc phải có tab, thay tab Thống kê bằng Contrast Lab và chuyển Thống kê vào Trang chủ hoặc Học. Cách này sửa Stats/Home, trái quy tắc "không sửa", nên cần bạn quyết định. |
| 2 | "AI-assisted authoring" | App **không có client AI nào**. Mọi nội dung đều là gói JSON tạo offline bằng `tools/content/*` rồi đóng gói trong `assets/content`. | Phần AI chạy **ngoài app**: thêm công cụ `tools/content/contrast/` tạo bản nháp, người duyệt và đánh dấu `approved`, và chỉ bản đã duyệt mới được đóng gói. App chỉ nhập gói đã duyệt. Không thêm API key hay gọi mạng vào app. |
| 3 | Tham chiếu nội dung có sẵn | Nội dung bị **xóa rồi chèn lại** mỗi khi gói có phiên bản mới (`lessons`/`vocabulary`/... có `ON DELETE CASCADE` từ `content_packs`). Id ngữ pháp (`n3g-g001`) và từ vựng (`mimikara-n3-v001`) đánh số theo thứ tự, còn id Kanji thì theo ký tự (`n3k-k0610f`). | Bảng Contrast **không dùng foreign key** tới bảng nội dung (đúng như `learning_progress` đang làm), mà tham chiếu bằng `(item_type, item_id)`, kèm ảnh chụp văn bản để phát hiện id bị lệch. Xem mục G. |
| 4 | "SRS độc lập" | `ItemType` là enum dùng chung cho `learning_progress`, `review_records`, backup, thống kê và widget. | **Không thêm giá trị vào `ItemType`** và không ghi vào `learning_progress`/`review_records`. Contrast có bảng tiến độ riêng. |
| 5 | Lịch sử và phân tích lỗi | Đã có Sổ lỗi sai cho đề thi (`error_records`, `review_items`, `MistakeAnalysisManager`), gắn chặt với `question_id` của đề thi. | Không dùng lại các bảng này, vì sẽ lẫn vào Sổ lỗi sai. Chỉ dùng lại mẫu thiết kế và các component UI. |
| 6 | Sao lưu | `BackupDao`/`BackupRepository` chỉ xuất progress, records, marks và sessions. | Ở bản đầu, tiến độ Contrast **không vào backup** (ghi rõ trong UI và tài liệu). Việc đưa vào backup là một milestone riêng, có tăng phiên bản định dạng backup và test tương thích ngược. |

---

## A. Kiến trúc hiện có

- **Một module Gradle** `:app` (`settings.gradle.kts`). compileSdk/targetSdk 36, minSdk 26, KSP cho Room, kotlinx.serialization, Navigation Compose, DataStore, WorkManager, Glance.
- **Các tầng** nằm trong `app/src/main/java/com/n3sensei/app/` và được `ArchitectureRulesTest` kiểm tra tự động:
  - `domain/`: Kotlin thuần, cấm `android.*`, `androidx.*` và `data.*`. Gồm `model`, `repository` (interface), `srs`, `course`, `practice`, `exam`, `mistakes`...
  - `data/`: `local` (Room: `N3Database`, `dao`, `entity`, `model`, `N3Migrations`), `repository` (`Offline*Repository`), `importing` (`ContentPackImporter`, `AndroidContentImportRepository`), `preferences` (DataStore), `backup`, `sync`. Không được phụ thuộc UI.
  - `core/ui` (`content`, `state`) và `core/designsystem` (`theme`, `component`, `icon`): không được import Room, `data.*` hay `app.di`.
  - `feature/<tên>/`: Screen + ViewModel. **Các feature không được import lẫn nhau**, và cũng không được import `androidx.navigation` hay `app.*`.
  - `app/`: `di/AppContainer.kt` (DI thủ công, `by lazy`), `di/AppViewModel.kt`, `navigation/`, `reminder/`, `widget/`.
- **DI:** `AppContainer` (interface) và `DefaultAppContainer`; ViewModel được tạo trong `N3SenseiNavHost.kt` qua factory.

## B. Database hiện có

- `data/local/N3Database.kt`: **`VERSION = 15`**, `exportSchema = true`, schema JSON 1..15 nằm trong `app/schemas/`.
- Migration nằm trong `data/local/N3Migrations.kt`, từ `MIGRATION_1_2` đến `MIGRATION_14_15`. **Không bao giờ dùng destructive fallback.** `N3DatabaseMigrationTest` kiểm tra từng bước. File bị hỏng được `CorruptionAwareOpenHelperFactory` xử lý.
- **Nội dung** (`entity/ContentEntities.kt`):
  - Chuỗi quan hệ: `content_sources` ← `content_packs` ← `lessons` (`jlpt_level`, unique `pack_id, number`) ← `vocabulary` / `kanji` / `grammar` (CASCADE theo lesson, RESTRICT theo source).
  - Bảng phụ: `grammar_exercises`, `example_sentences`, các bảng nối `vocabulary_examples`, `kanji_examples`, `grammar_examples`, cùng `tags` và `vocabulary_tags`.
- **Dữ liệu người học** (`entity/ProgressEntities.kt`), **cố ý không có FK tới nội dung**:
  - `learning_progress`: PK `(item_type, item_id)`, index `due_at`.
  - `review_records`: unique `request_id`.
  - `item_marks`, `review_sessions`.
- **Người dùng:** `UserLinkEntities.kt` gồm `user_words` ("Từ của tôi", gói riêng không bao giờ bị thay), `user_word_translations`, `user_kanji_examples`, `vocabulary_kanji_links`. Ngoài ra có `user_content_edits`.
- **Đề thi và lỗi sai** (`ExamEntities.kt`): `exam_*`, `error_records`, `error_review_log`, `review_items` (SRS riêng của Sổ lỗi sai).
- **Đồng bộ** (v15): các bảng `synced_*` và `sync_checkpoints`. Đây là tiền lệ gần nhất của một "khu vực độc lập có SRS riêng" (`synced_review_items.local_step_index`/`local_due_at`).
- **DAO:** `ContentDao`, `ContentPackDao`, `ProgressDao`, `AnalyticsDao`, `ReviewSessionDao`, `BackupDao`, `UserContentDao`, `ReminderHistoryDao`, `ExamDao`, `ErrorDao`, `SyncDao`.

## C. SRS hiện có

- Mã nằm trong `domain/srs/`. `SpacedRepetitionScheduler` dùng kiểu bậc thang (Leitner) trên `ReviewLadder`. Mặc định có 7 bậc: 20 phút, 1 giờ, 1 ngày, 2 ngày, 1 tuần, 2 tuần, 1 tháng; tối đa 12 bậc, tối đa 1 năm mỗi bậc.
- Đáp án Quên/Khó/Tốt/Dễ đưa thẻ về bậc 0, giữ bậc, lên +1 hoặc lên +2. Trạng thái là LEARNING, REVIEW (từ bậc ≥ 1 ngày) hoặc MASTERED (bậc cuối). Quên khi đang ở REVIEW tính là một lapse.
- `SubmitReviewUseCase` gọi `ProgressRepository` để ghi `learning_progress` và `review_records` trong cùng một transaction, chặn trùng bằng `request_id`.
- `ReviewSettings` (thang, `DailyLimits` riêng cho Từ vựng/Kanji/Ngữ pháp) lưu trong DataStore. Ngoài ra có `ReviewQueuePlanner`, `ReviewScope`, `KanjiReviewLessons` (chọn bài Kanji N2).
- **Cách Contrast SRS tồn tại song song mà không ảnh hưởng nhau:**
  - Bảng riêng `contrast_progress` và `contrast_attempts`.
  - Không dùng `ItemType`, không gọi `ProgressRepository`, không đọc `ReviewSettings.dailyLimits`.
  - **Dùng lại lớp thuần `SpacedRepetitionScheduler` + `ReviewLadder`** với thang riêng (`ContrastSettings.ladder`, mặc định bằng `ReviewLadder.DEFAULT`). Lớp này không có trạng thái và không đọc DB, nên dùng lại không ảnh hưởng gì. Nếu không muốn phụ thuộc vào nó, có thể viết một bộ lập lịch nhỏ riêng; tôi khuyên dùng lại để giữ hành vi nhất quán.
  - Số "đến hạn" của Contrast không hiện trong tab Ôn tập, Trang chủ, nhắc nhở hay widget, trừ khi sau này có yêu cầu riêng.

## D. Tích hợp điều hướng

Các file liên quan, tất cả trong `app/navigation/`:
- `TopLevelDestination.kt`: enum 5 tab.
- `Routes.kt`: route kiểu `@Serializable`, Learn là nested graph `LearnGraphRoute` → `LearnHubRoute`.
- `AppScreen.kt`: ánh xạ route → tiêu đề/tab/`isFocusSession`, được `AppScreenTest` kiểm tra.
- `N3SenseiNavHost.kt` (593 dòng): `composable<…>` và factory ViewModel.
- `NavigationActions.kt`: `navigateToTopLevel`, `selectTab`.
- `app/N3SenseiApp.kt`: `N3NavigationBar` duyệt `TopLevelDestination.entries`.
- `feature/learn/LearnHubScreen.kt`: danh sách `LearnSectionItem`.

**Cách an toàn nhất (phương án A):**
- Thêm `ContrastGraphRoute` (nested) cùng các route con trong `Routes.kt`, các mục mới trong `AppScreen` với `tab = LEARN`, và một block `navigation<ContrastGraphRoute>` trong NavHost.
- Thêm một `LearnSectionItem("比", …)` vào `LearnHubScreen`, với tham số `onOpenContrast` có giá trị mặc định `{}` để các lời gọi cũ không phải đổi.
- Phiên luyện tập đặt `isFocusSession = true` như phiên thi.
- Cả nhóm route nằm trong một nested graph, nên sau này nếu chọn phương án B thì chỉ cần đổi nơi graph được gắn vào.

## E. Kiến trúc Contrast Lab đề xuất

Đề xuất theo đúng các tầng hiện có:

```
domain/contrast/
  ContrastModels.kt          // ContrastGroup, ContrastMember(ref, role), Explanation, Example, Exercise (sealed: MultipleChoice, ErrorDetection, FillBlank, ContextSelection)
  ContrastAnswerChecker.kt   // chấm điểm thuần, theo từng loại bài
  ContrastScheduler.kt       // bọc SpacedRepetitionScheduler với thang riêng
  ContrastSessionPlanner.kt  // chọn bài đến hạn/mới, giới hạn mới/ngày riêng
  ContrastMistakeAnalyzer.kt // nhầm A↔B, theo loại bài
  ContrastSettings.kt
domain/repository/ContrastRepository.kt, ContrastContentResolver.kt (interface)
data/local/entity/ContrastEntities.kt
data/local/dao/ContrastDao.kt
data/repository/OfflineContrastRepository.kt
data/repository/RoomContrastContentResolver.kt   // adapter đọc nội dung gốc qua ContentDao, chỉ đọc
data/importing/ContrastPackImporter.kt + ContrastPackDto.kt
data/preferences/ (thêm khóa contrast_* vào DataStoreUserPreferencesRepository, hoặc một DataStore riêng; xem mục H)
feature/contrast/
  ContrastHomeScreen/ViewModel        // danh sách nhóm, lọc theo loại, tiến độ
  ContrastGroupScreen/ViewModel       // so sánh + giải thích + ví dụ
  ContrastPracticeScreen/ViewModel    // 4 dạng bài
  ContrastResultScreen, ContrastMistakesScreen/ViewModel
tools/content/contrast/              // soạn nháp bằng AI → người duyệt → build_pack.py --strict
app/src/main/assets/content/contrast-n3.json
```

## F. Thiết kế database (v15 → v16)

Tất cả là bảng mới. Không có `ALTER` hay `DROP` bảng cũ. Không có FK tới bảng nội dung hay tiến độ hiện có.

| Bảng | Cột chính | Khóa / index |
|------|-----------|--------------|
| `contrast_packs` | `id`, `version`, `title`, `imported_at`, `license_verified` | PK `id` |
| `contrast_groups` | `id`, `pack_id`, `kind` (GRAMMAR/VOCABULARY/KANJI), `jlpt_level`, `title`, `summary_vi`, `sort_order`, `approved` | PK `id`; FK `pack_id` → `contrast_packs` CASCADE; index `(kind, sort_order)` |
| `contrast_members` | `group_id`, `position`, `item_type`, `item_id`, `label_snapshot` (văn bản lúc soạn), `note_vi` | PK `(group_id, position)`; FK group CASCADE; index `(item_type, item_id)` |
| `contrast_explanations` | `id`, `group_id`, `member_position?`, `body_vi`, `sort_order` | FK group CASCADE |
| `contrast_examples` | `id`, `group_id`, `member_position`, `japanese`, `furigana`, `meaning_vi`, `is_counter_example` | FK group CASCADE; index `group_id` |
| `contrast_exercises` | `id`, `group_id`, `type` (MC/ERROR/FILL/CONTEXT), `prompt`, `choices_json`, `answer`, `explanation_vi`, `sort_order` | FK group CASCADE; index `(group_id, type)` |
| `contrast_progress` | `group_id`, `step_index`, `interval_minutes`, `due_at`, `state`, `review_count`, `lapse_count`, `created_at`, `updated_at` | PK `group_id`; **không FK** (giữ được khi gói Contrast được thay); index `due_at` |
| `contrast_attempts` | `id` auto, `request_id` (unique), `group_id`, `exercise_id`, `type`, `chosen`, `correct` (bool), `confused_with_position?`, `answered_at`, `duration_ms` | **không FK**; index `(group_id, answered_at)`, `exercise_id` |

- Migration `MIGRATION_15_16` chỉ gồm `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`, chép đúng từ `schemas/16.json`.
- Thêm bước 15→16 vào `N3DatabaseMigrationTest`, kiểm tra rằng mọi hàng của v15 (nội dung, progress, records, marks, user words, exam, synced) vẫn y nguyên.
- Phần soạn nội dung (pack, group, member, explanation, example, exercise) được thay mỗi khi gói Contrast lên phiên bản, giống cơ chế gói hiện có. Phần của người học (`contrast_progress`, `contrast_attempts`) không bao giờ bị xóa khi cập nhật. Id nhóm và id bài tập phải ổn định qua các phiên bản (do công cụ đảm bảo).

## G. Tích hợp nội dung

- `contrast_members` chỉ lưu tham chiếu `(item_type, item_id)` tới `vocabulary`/`kanji`/`grammar`, giống `ItemRef`, và **không chép nội dung gốc**.
  - Màn so sánh đọc chi tiết qua `ContrastContentResolver`. Bản trên Room dùng các truy vấn sẵn có của `ContentRepository` (`getKanjiDetail`, chi tiết từ vựng/ngữ pháp). Nhờ vậy chỉnh sửa của người dùng (`user_content_edits`) và "Từ của tôi" vẫn được áp dụng.
  - Nhóm có thể mở chi tiết mục gốc qua các route sẵn có (Kanji/Từ vựng/Ngữ pháp). Thêm các entry trong NavHost là đủ, không phải sửa feature cũ.
- Tham chiếu có thể bị hỏng vì id ngữ pháp và từ vựng đánh số theo thứ tự. Phòng tránh:
  1. Khi build, công cụ kiểm tra mọi `item_id` có tồn tại trong gói nguồn đúng phiên bản, và `label_snapshot` khớp `pattern`/`expression`/`character`. Có `--strict`.
  2. Khi import, app kiểm tra lại: member không tìm thấy hoặc snapshot lệch thì nhóm bị ẩn và ghi vào `ImportReport`, không làm crash.
  3. Khi hiển thị, nếu resolver không tìm thấy mục (ví dụ gói nguồn đã đổi) thì hiện `label_snapshot` kèm nhãn "nội dung gốc không còn", không làm hỏng màn hình.
- Câu ví dụ và bài tập là nội dung riêng của Contrast (không tồn tại trong nội dung gốc) nên lưu trong bảng Contrast. Ví dụ gốc của mục vẫn được đọc qua resolver chứ không chép lại.

## H. Rủi ro

- **Tương thích**
  - Thêm tab thứ 6: xem xung đột 1.
  - Thêm `ItemType`: cấm, xem xung đột 4.
  - `ArchitectureRulesTest` cấm `feature/contrast` import `feature/kanji`... Mở mục gốc phải đi qua callback từ NavHost.
  - `UserPreferences` là data class dùng chung. Thêm field mới (có giá trị mặc định) buộc phải sửa các fake trong test. Có thể tránh bằng một DataStore riêng `contrast_prefs`.
- **Toàn vẹn dữ liệu**
  - Id nội dung lệch giữa các phiên bản gói: xem mục G.
  - Migration v16 phải là thao tác thuần cộng thêm, có test.
  - Gói Contrast được thay mà không mất tiến độ (progress không có FK).
  - Chặn trùng câu trả lời bằng `request_id` unique.
  - Phải xác định thứ tự cài gói: gói Contrast cài sau các gói nguồn trong `AndroidContentImportRepository`.
- **Nội dung AI:** có thể sai ngữ pháp hoặc giải thích. Chỉ đóng gói nhóm `approved=true` có tên người duyệt, đánh dấu `licenseVerified=false` cho đến khi xác minh, và không bao giờ sinh nội dung lúc chạy app.
- **Hiệu năng:** vài trăm nhóm, mỗi nhóm khoảng 10 bài tập, nhỏ hơn nhiều so với 868 kanji và 1.811 ví dụ hiện có. Cần index `due_at`, `(group_id, answered_at)` và lấy dữ liệu theo trang cho lịch sử. Việc resolve member nên gom trong một truy vấn `IN (...)` thay vì từng mục. Nên có một test hiệu năng giống `LargeDataPerformanceTest`.
- **Hồi quy**
  - Thứ tự nạp gói lúc khởi động (thêm một gói làm lần mở đầu tiên chậm hơn).
  - Các test đếm số gói, số bảng hay số mục trên Học (`AppScreenTest`, `LearnHub`/`EndToEndTest`, `BundledCourseTest`) phải được cập nhật một cách có chủ ý.
  - Backup không gồm dữ liệu Contrast, nên khôi phục backup sẽ không có tiến độ Contrast. Phải nói rõ điều này.
  - Thống kê không đếm Contrast.

## I. Lộ trình triển khai

| M | Nội dung | Phụ thuộc |
|---|----------|-----------|
| M0 | Chốt quyết định: chỗ đặt điều hướng (A/B), có đưa vào backup không, định dạng gói, danh sách nhóm mẫu đầu tiên | – |
| M1 | Domain thuần: models, `ContrastAnswerChecker` (4 dạng), `ContrastScheduler`, `ContrastSessionPlanner`, `ContrastMistakeAnalyzer`, kèm unit test | M0 |
| M2 | Định dạng gói + công cụ `tools/content/contrast/` (nháp AI → duyệt → `build_pack.py --strict`, kiểm tra tham chiếu bằng gói nguồn), test Python, 5–10 nhóm mẫu đã duyệt | M0 |
| M3 | Room v16: entities, `ContrastDao`, `MIGRATION_15_16`, schema 16.json, test migration và test DAO | M1 |
| M4 | Import: `ContrastPackImporter`, `RoomContrastContentResolver`, `OfflineContrastRepository`, đăng ký gói trong bundled assets, test import trên thiết bị | M2, M3 |
| M5 | UI xem: danh sách nhóm + màn so sánh (giải thích, ví dụ, mở mục gốc), điều hướng, mục trong Học | M4 |
| M6 | Luyện tập: phiên 4 dạng bài, ghi `contrast_attempts`, lập lịch `contrast_progress`, màn kết quả | M5 |
| M7 | Lịch sử và phân tích lỗi + màn tiến độ Contrast | M6 |
| M8 | Cài đặt Contrast (mới/ngày, thang), nằm trong màn Contrast chứ không trong Settings cũ | M6 |
| M9 | (Tùy chọn) Đưa vào backup: tăng phiên bản định dạng, test khôi phục backup cũ/mới | M6, quyết định ở M0 |
| M10 | Hồi quy toàn bộ: unit, lint, `assembleDebug`, toàn bộ test trên emulator-5554 | tất cả |

M1 và M2 có thể làm song song. M3 cần M1. M4 cần M2 và M3. Từ M5 đến M8 làm tuần tự.

## J. Tiêu chí nghiệm thu

- **M1:** 100% nhánh chấm điểm của 4 dạng bài có unit test (đúng, sai, bỏ trống, chọn nhầm thành viên khác → `confused_with`). Bộ lập lịch cho cùng kết quả với `SpacedRepetitionScheduler` trên cùng thang. Planner tôn trọng giới hạn mới/ngày. `ArchitectureRulesTest` đạt.
- **M2:** `build_pack.py --strict` dừng khi có nhóm chưa duyệt, `item_id` không tồn tại hoặc snapshot lệch. Chạy hai lần cho kết quả giống hệt byte. Gói có ít nhất 1 nhóm cho mỗi loại GRAMMAR/VOCABULARY/KANJI. Test Python đạt.
- **M3:** test migration 15→16 đạt, và mọi bảng v15 có checksum hàng y nguyên. `schemas/16.json` được commit. Không có `fallbackToDestructiveMigration`.
- **M4:**
  - Import gói lần hai: `UP_TO_DATE`, không ghi gì.
  - Import phiên bản mới: id giữ nguyên, `contrast_progress` và `contrast_attempts` giữ nguyên.
  - Member hỏng: nhóm bị ẩn và có trong report.
  - Số hàng của `vocabulary`/`kanji`/`grammar`/`learning_progress`/`review_records` không đổi.
- **M5:** test UI: mở Học → Contrast Lab → nhóm → thấy đủ thành viên lấy từ nội dung gốc (kể cả chỉnh sửa của người dùng) → mở được chi tiết mục gốc. Thanh dưới vẫn đúng 5 tab (phương án A).
- **M6:**
  - Mỗi dạng bài có test UI trả lời đúng và sai.
  - `contrast_attempts` có đúng một hàng cho mỗi câu (gửi lặp `request_id` thì bị từ chối).
  - `due_at` đúng theo thang.
  - **`learning_progress` và `review_records` không đổi** sau một phiên Contrast.
  - Số đếm ở tab Ôn tập và Trang chủ không đổi.
- **M7:** phân tích chỉ ra cặp hay nhầm nhất và tỉ lệ đúng theo loại bài, khớp với dữ liệu fixture. Lịch sử 10.000 lần trả lời tải dưới ngưỡng do test hiệu năng đặt.
- **M8:** cài đặt Contrast lưu qua lần khởi động lại. Đổi thang Contrast không đổi `ReviewSettings` và ngược lại.
- **M9:** khôi phục backup cũ (không có Contrast) vẫn thành công. Backup mới khôi phục đủ tiến độ Contrast.
- **M10:** toàn bộ unit test, lint và `assembleDebug` đạt. Toàn bộ test trên emulator đạt, trừ các lỗi đã tồn tại từ trước và được ghi rõ (hiện tại: `HourPickerReachabilityTest.everyHourOfTheEndMenu_canBeReachedOnScreen`).

---

## 1. File cần tạo

- `domain/contrast/` gồm `ContrastModels.kt`, `ContrastAnswerChecker.kt`, `ContrastScheduler.kt`, `ContrastSessionPlanner.kt`, `ContrastMistakeAnalyzer.kt`, `ContrastSettings.kt`.
- `domain/repository/ContrastRepository.kt`, `domain/repository/ContrastContentResolver.kt`.
- `data/local/entity/ContrastEntities.kt`, `data/local/dao/ContrastDao.kt`.
- `data/repository/OfflineContrastRepository.kt`, `data/repository/RoomContrastContentResolver.kt`.
- `data/importing/ContrastPackImporter.kt`, `data/importing/ContrastPackDto.kt`.
- `data/preferences/DataStoreContrastSettingsRepository.kt` (nếu dùng DataStore riêng).
- `feature/contrast/` gồm các Screen/ViewModel: Home, Group, Practice, Result, Mistakes.
- `app/src/main/assets/content/contrast-n3.json`, `app/schemas/.../16.json` (do Room sinh).
- `tools/content/contrast/` gồm `draft_with_ai.py` (chạy ngoài app), `build_pack.py`, `test_build_pack.py`, `drafts/`; thêm `docs/CONTRAST_LAB.md`.
- Test unit: `test/.../domain/contrast/*Test.kt`, `test/.../feature/contrast/*ViewModelTest.kt`.
- Test thiết bị: `androidTest/.../data/local/ContrastMigrationTest` (hoặc thêm vào `N3DatabaseMigrationTest`), `data/importing/ContrastImportTest.kt`, `data/repository/ContrastIsolationTest.kt`, `feature/ContrastLabEndToEndTest.kt`.

## 2. File hiện có cần sửa tối thiểu

| File | Thay đổi |
|------|----------|
| `data/local/N3Database.kt` | Thêm entities và `contrastDao()`, `VERSION = 16`. |
| `data/local/N3Migrations.kt` | Thêm `MIGRATION_15_16` và đưa vào `ALL`. |
| `app/di/AppContainer.kt` | Thêm `contrastRepository` (lazy). |
| `data/importing/AndroidContentImportRepository.kt` | Đăng ký gói Contrast, cài sau các gói nguồn. |
| `app/navigation/Routes.kt`, `AppScreen.kt`, `N3SenseiNavHost.kt` | Route, mục màn hình, nested graph. |
| `feature/learn/LearnHubScreen.kt` | Thêm một `LearnSectionItem` (phương án A). |
| `res/values/strings.xml` | Chuỗi `contrast_*`. |
| `androidTest/.../N3DatabaseMigrationTest.kt`, `test/.../AppScreenTest.kt` | Bước 15→16 và route mới. |
| `androidTest/.../testing/AppTestState.kt`, `TestDoubles.kt` | Reset và fake cho Contrast. |
| (Chỉ ở M9) `data/backup/*`, `BackupDao.kt` | Đưa dữ liệu Contrast vào backup. |

## 3. File phải giữ nguyên

- **SRS và ôn tập:** `domain/srs/*`. `SpacedRepetitionScheduler`/`ReviewLadder` chỉ được dùng lại, không sửa. Cùng với `SubmitReviewUseCase.kt` và `ReviewQueuePlanner.kt`.
- **Tiến độ:** `ProgressDao.kt`, `OfflineProgressRepository.kt`, `ProgressEntities.kt`.
- **Nội dung:** `ContentEntities.kt`, `ContentDao.kt`, `ContentPackImporter.kt`, `domain/model/ContentModels.kt` (gồm `ItemType`) và mọi gói trong `assets/content` hiện có.
- **Màn hình học:** `feature/grammar/*`, `feature/vocabulary/*`, `feature/kanji/*`, `feature/lessons/*`, `feature/practice/*`, `feature/review/*`, `feature/reviewsession/*`, `core/ui/content/Flashcard.kt`.
- **Từ của tôi:** `UserLinkEntities.kt`, `UserContentDao.kt`, `UserContentRepository`.
- **Nhắc nhở và widget:** `app/reminder/*`, `domain/reminder/*`, `app/widget/*`, `domain/widget/*`.
- **Cài đặt:** `feature/settings/*`, `UserPreferences.kt` (nếu dùng DataStore riêng cho Contrast).
- **Đề thi, lỗi sai, đồng bộ:** `ExamEntities.kt`, `ErrorDao.kt`, `domain/mistakes/*`, `data/sync/*`, `SyncedContentEntities.kt`.
- **Điều hướng:** `app/navigation/TopLevelDestination.kt`, `app/N3SenseiApp.kt` (phương án A).
- **Build:** `build.gradle.kts`, `gradle/libs.versions.toml`, vì không cần thư viện mới.

## 4. Kế hoạch và phụ thuộc giữa các việc

```
M0 quyết định ──┬──> M1 domain ──> M3 Room v16 ──┐
                └──> M2 công cụ + gói mẫu ───────┴──> M4 import/repository ──> M5 UI xem ──> M6 luyện tập ──┬──> M7 phân tích
                                                                                                         ├──> M8 cài đặt
                                                                                                         └──> M9 backup (tùy chọn)
                                                                               tất cả ──> M10 hồi quy
```

Mỗi milestone kết thúc bằng: unit test, `lintDebug`, `assembleDebug` và các test thiết bị liên quan trên `emulator-5554`. Chỉ commit khi bạn cho phép.
