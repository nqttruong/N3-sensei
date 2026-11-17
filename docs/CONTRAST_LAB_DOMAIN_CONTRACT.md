# Contrast Lab: domain contract

Trạng thái: **đặc tả, chưa có Kotlin class nào** (2026-11-17). Kiến trúc tổng thể xem [CONTRAST_LAB_ARCHITECTURE.md](CONTRAST_LAB_ARCHITECTURE.md), định dạng nội dung xem [CONTRAST_LAB_CONTENT_SCHEMA.md](CONTRAST_LAB_CONTENT_SCHEMA.md).

Quy ước:
- Kiểu dữ liệu ghi theo Kotlin dự kiến. `?` là tùy chọn (nullable); không có `?` là bắt buộc.
- Thời điểm dùng `Instant` (lưu epoch millis UTC, như SRS hiện có).
- Id nội dung là `String` ổn định giữa các phiên bản gói.
- Mọi model thuộc `domain/contrast` (Kotlin thuần).

---

## 1. Domain models

### 1.1. Nội dung (chỉ đọc, đến từ gói đã PUBLISHED)

#### `ContrastGroup`: một nhóm cần phân biệt

Dùng cho danh sách và tìm kiếm; đây là phần tóm tắt của `ContrastDetail`.

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `id` | String | ✓ | Duy nhất trong mọi gói Contrast |
| `packId` | String | ✓ | |
| `contentType` | `ContrastContentType` (GRAMMAR, VOCABULARY, KANJI) | ✓ | Enum riêng của Contrast, **không phải `ItemType`** |
| `jlptLevel` | String | ✓ | "N3", "N2", … |
| `title` | String | ✓ | |
| `summaryVi` | String | ✓ | |
| `revision` | Int | ✓ | ≥ 1 |
| `itemLabels` | List<String> | ✓ | 2–4 nhãn, theo thứ tự trong gói |
| `questionCount` | Int | ✓ | ≥ 1 |

- **Validation:** 2–4 mục, có ít nhất 1 câu hỏi, có ít nhất 1 đoạn lý thuyết (validator gói kiểm tra).
- **Versioning:** `revision` tăng khi nội dung nhóm đổi mà các mục vẫn giữ nguyên. Đổi tập mục thì phải tạo nhóm mới (id mới) và đưa id cũ vào `retiredGroups`.

#### `ContrastItem`: một mục trong nhóm

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `id` | String | ✓ | Duy nhất trong gói |
| `label` | String | ✓ | |
| `labelFurigana` | String? | | Cú pháp `{gốc|đọc}` |
| `source` | `SourceReference` | ✓ | |
| `details` | `ItemDetails` (sealed: `Grammar`, `Vocabulary`, `Kanji`) | ✓ | Phải cùng loại với `contentType` của nhóm |

Các loại `ItemDetails`:
- `Grammar(meaningVi, connection, nuanceVi)`.
- `Vocabulary(reading, meaningVi, usageVi, collocations: List<Collocation>)`, với `Collocation(japanese, furigana?, meaningVi)`.
- `Kanji(meaningVi, hanViet?, onReadings, kunReadings, radical: Radical?, shapeNoteVi)`, với `Radical(character, nameVi)`.

`SourceReference` gồm:
- `sourceType: ContrastContentType`, `sourceContentId: String`, `sourcePackId: String`;
- `sourceSchemaVersion: Int`;
- `sourceSnapshot: Map<String, SnapshotValue>`, trong đó `SnapshotValue` là String hoặc List<String>;
- `sourceFingerprint: String`.

`ResolvedSource` là kết quả của resolver, **không lưu**:
- `status: SourceRefStatus` (OK, SOURCE_CHANGED, SOURCE_MISSING, AMBIGUOUS, UNVERIFIABLE);
- `currentId: String?`, chỉ có khi mục gốc còn và mở được.

Nội dung trong `details` là phần Contrast tự viết, nên có thể chi tiết hơn nguồn. Còn `sourceSnapshot` là bản chép nguyên văn từ nguồn để đối chiếu, **không hiển thị** khi trạng thái là OK.

#### `ContrastDetail`: toàn bộ nội dung để học một nhóm

| Trường | Kiểu | Bắt buộc |
|--------|------|----------|
| `group` | ContrastGroup | ✓ |
| `items` | List<ContrastItem> (2–4) | ✓ |
| `sources` | Map<itemId, ResolvedSource> | ✓ (tính lúc đọc) |
| `theory` | List<TheoryBlock(id, titleVi, bodyVi)> (≥ 1) | ✓ |
| `comparison` | List<ComparisonRow(aspectVi, values: Map<itemId, String>)> | ✓ (có thể rỗng) |
| `examples` | List<ContrastExample> | ✓ (có thể rỗng; rỗng thì WARNING lúc validate) |
| `memoryTips` | List<MemoryTip(id, textVi)> | ✓ (có thể rỗng; rỗng thì WARNING) |
| `commonMistakes` | List<CommonMistake> | ✓ (có thể rỗng; rỗng thì WARNING) |
| `license` | String + `licenseVerified: Boolean` | ✓ |

- `CommonMistake` gồm `id`, `situationVi`, `wrong: Sentence`, `correct: Sentence`, `explanationVi`, và cặp tùy chọn `expectedItemId?` + `confusedWithItemId?` (phải có cả hai hoặc không có cái nào).
- `Sentence` gồm `japanese` và `furigana?`.
- **Không có câu hỏi** trong `ContrastDetail`: câu hỏi chỉ vào phiên luyện, để màn lý thuyết không lộ đáp án.

#### `ContrastExample`

| Trường | Kiểu | Bắt buộc | Validation |
|--------|------|----------|------------|
| `id` | String | ✓ | duy nhất |
| `itemId` | String | ✓ | thuộc nhóm |
| `japanese` | String | ✓ | |
| `furigana` | String? | ✓ nếu `japanese` có Kanji | phần gốc ghép lại phải bằng `japanese` |
| `translationVi` | String | ✓ | |

#### `ContrastQuestion`

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `id` | String | ✓ | duy nhất trong gói |
| `groupId` | String | ✓ | |
| `revision` | Int | ✓ | |
| `type` | `ContrastQuestionType` (MULTIPLE_CHOICE, ERROR_DETECTION, FILL_IN_THE_BLANK, CONTEXT_SELECTION) | ✓ | |
| `difficulty` | Int 1–3 | ✓ | Planner dùng để chọn câu tương đương |
| `targetItemId` | String | ✓ | Mục mà câu này kiểm tra cách dùng đúng |
| `instructionVi` | String | ✓ | |
| `prompt` | Prompt(japanese, furigana?, translationVi?)? | FILL và ERROR bắt buộc | |
| `context` | Context(descriptionVi, japanese?, furigana?)? | CONTEXT bắt buộc | |
| `options` | List<ContrastOption> (2–6) | ✓ | ERROR_DETECTION cần ≥ 3 |
| `correctOptionId` | String | ✓ | thuộc `options` |
| `correction` | Correction(text, furigana?, itemId?)? | ERROR bắt buộc | |
| `explanationVi` | String | ✓ | |

Ý nghĩa theo từng dạng:

| Dạng | Người học làm gì | `options` | Đáp án |
|------|------------------|-----------|--------|
| MULTIPLE_CHOICE | Trả lời câu hỏi về nghĩa, cách dùng hoặc cách viết | Câu hoặc từ | Option đúng đại diện `targetItemId` |
| FILL_IN_THE_BLANK | Chọn dạng điền vào `＿＿＿` (đúng một chỗ trống) | Các dạng cần phân biệt | Option đúng đại diện `targetItemId` |
| ERROR_DETECTION | Chọn đoạn sai trong câu | Các đoạn của câu; ghép lại phải bằng đúng `prompt.japanese` | Đoạn sai; `correction.itemId` = `targetItemId` |
| CONTEXT_SELECTION | Đọc tình huống, chọn câu tự nhiên nhất | Câu hoàn chỉnh | Option đúng đại diện `targetItemId` |

Bản đầu không nhập đáp án bằng bàn phím. Lý do: IME tiếng Nhật và nhiều cách viết đúng (漢字 hay かな) khiến việc chấm tự động không tin cậy.

#### `ContrastOption`

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `id` | String (1–8 ký tự a–z0–9) | ✓ | duy nhất trong câu hỏi |
| `text` | String | ✓ | không trùng trong câu hỏi |
| `furigana` | String? | | Thiếu khi `text` có Kanji thì là WARNING, cần người duyệt xác nhận. Ví dụ hợp lệ: câu hỏi chọn chữ Kanji, nơi furigana làm lộ đáp án |
| `itemId` | String? | | Mục của nhóm mà option này đại diện; null cho option nhiễu |
| `feedbackVi` | String? | | Giải thích vì sao option sai |

### 1.2. Dữ liệu người học (ghi trong app, không bao giờ bị xóa khi cập nhật gói)

#### `ContrastAttempt`: một lần trả lời, bất biến

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `requestId` | String (UUID) | ✓ | Khóa chống ghi trùng, như `review_records.request_id` |
| `sessionId` | String | ✓ | |
| `position` | Int | ✓ | `(sessionId, position)` là duy nhất |
| `groupId`, `groupRevision` | String, Int | ✓ | |
| `questionId`, `questionRevision`, `questionType` | String, Int, enum | ✓ | |
| `contentPackId`, `contentVersion` | String, Int | ✓ | **Phiên bản nội dung đã dùng** |
| `outcome` | `AnswerOutcome` (CORRECT, INCORRECT, SKIPPED) | ✓ | Câu chưa trả lời thì **không có attempt** |
| `chosenOptionId` | String? | ✓ nếu không SKIPPED | |
| `correctOptionId` | String | ✓ | |
| `expectedItemId` | String? | | `targetItemId` lúc trả lời |
| `chosenItemId` | String? | | `itemId` của option đã chọn. Với ERROR_DETECTION khi không tìm ra đoạn sai: `itemId` của đoạn sai (dạng đúng bị thay bằng dạng sai mà người học không nhận ra) |
| `chosenText`, `correctText` | String?, String | ✓ | Bản chụp văn bản để lịch sử vẫn đọc được khi câu hỏi đổi hoặc bị bỏ |
| `answeredAt` | Instant | ✓ | |

**Không lưu thời gian làm bài**, vì theo quy tắc R6 nó không được dùng làm chỉ số thành thạo.

#### `ContrastProgress`: một dòng cho mỗi nhóm, gồm cả lịch ôn

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `groupId` | String | ✓ | |
| `stepIndex` | Int | ✓ | Vị trí trên thang Contrast; bị kẹp khi thang ngắn lại (như SRS cũ) |
| `dueAt` | Instant | ✓ | |
| `lastReviewedAt` | Instant | ✓ | |
| `reviewCount`, `lapseCount` | Int | ✓ | lapse = khối AGAIN khi đang ở bậc ≥ 7 ngày |
| `createdAt`, `updatedAt` | Instant | ✓ | |

`mastery: ContrastMastery` (NEW, LEARNING, REVIEWING, MASTERED) **không lưu**. Nó được tính từ attempts và reviews theo `MasteryPolicy` (mục 4).

Một nhóm **chỉ có `ContrastProgress` sau khối hoàn tất đầu tiên**; chỉ đọc lý thuyết không tạo dòng nào.

#### `ContrastReview`: một lần lập lịch, bất biến

| Trường | Kiểu | Bắt buộc |
|--------|------|----------|
| `id` | Long | ✓ |
| `groupId`, `sessionId` | String | ✓ |
| `reviewedAt` | Instant | ✓ |
| `answered`, `correct`, `incorrect`, `skipped` | Int | ✓ |
| `rating` | `ReviewRating` (dùng enum có sẵn trong domain; chỉ AGAIN, HARD, GOOD) | ✓ |
| `stepBefore` | Int? | null cho nhóm mới |
| `stepAfter` | Int | ✓ |
| `dueAtAfter` | Instant | ✓ |
| `contentVersion` | Int | ✓ |

Rating đã quy ra được lưu lại, nên nếu quy tắc quy đổi đổi về sau thì lịch sử cũ không bị diễn giải lại.

#### `ContrastSession`: cần có, để tiếp tục phiên và phân biệt "chưa hoàn thành"

| Trường | Kiểu | Bắt buộc | Ghi chú |
|--------|------|----------|---------|
| `id` | String | ✓ | |
| `scope` | `PracticeScope` (DUE, GROUP(groupId), TYPE(contentType)) | ✓ | |
| `status` | ACTIVE, COMPLETED, ABANDONED | ✓ | Mỗi lúc chỉ có tối đa một ACTIVE |
| `startedAt`, `lastActivityAt` | Instant | ✓ | |
| `contentVersion` | Int | ✓ | |
| `items` | List<SessionItem(position, groupId, questionId, questionRevision, blockIndex)> | ✓ | Thứ tự đã lên kế hoạch |

Trạng thái từng vị trí được suy ra: có attempt thì lấy `outcome`; không có attempt thì `PENDING` (chưa hoàn thành).

## 2. Repository contracts (trong `domain/repository`)

> Đã triển khai ở M1 với vài chỗ khác bản phác thảo dưới đây; **chữ ký thực tế xem mục 8**.

```text
interface ContrastContentRepository            // CHỈ ĐỌC nội dung Contrast
  observeGroups(filter: ContrastFilter): Flow<List<ContrastGroup>>
      // ContrastFilter(query: String?, contentType?, jlptLevel?)
  getDetail(groupId): ContrastDetail?               // chưa resolve source
  getQuestions(groupId): List<ContrastQuestion>
  getQuestion(questionId): ContrastQuestion?
  installedPack(): InstalledContrastPack?           // id, contentVersion, licenseVerified
  retiredGroup(groupId): RetiredGroup?

interface ContrastSourceResolver                // CHỈ ĐỌC bảng nội dung hiện có
  resolve(items: List<ContrastItem>): Map<String, ResolvedSource>   // 1 truy vấn cho mỗi loại

interface ContrastPracticeRepository            // phiên + attempt
  activeSession(): ContrastSession?
  createSession(session: ContrastSession)           // ACTIVE cũ (nếu có) → ABANDONED trong cùng transaction
  appendItems(sessionId, items)                     // hỏi lại câu sai ở cuối phiên
  recordAnswer(attempt: ContrastAttempt, review: PendingReview?): SubmitResult
      // ghi attempt; nếu khối hoàn tất thì ghi review + progress trong CÙNG transaction
      // (cài đặt ủy quyền phần progress cho ContrastProgressRepository trong cùng DB transaction)
      // requestId trùng → DUPLICATE, không ghi gì
  finishSession(sessionId, status)
  attempts(sessionId): List<ContrastAttempt>
  answeredQuestionIds(groupId): Map<questionId, lastAnsweredAt>   // cho planner
  stats(range: TimeRange?): ContrastStatsRows      // tổng hợp bằng SQL cho analyzer
  history(groupId?, page: PageRequest): List<ContrastAttempt>

interface ContrastProgressRepository            // tiến độ + lịch ôn + lịch sử lập lịch
  progress(groupId): ContrastProgress?
  observeOverview(now): Flow<ContrastOverview>     // đến hạn, mới, theo loại
  dueGroups(now, limit): List<String>              // quá hạn trước
  newGroups(limit, filter): List<String>           // nhóm chưa có progress, theo thứ tự gói
  newGroupsIntroducedOn(dayStart, dayEnd): Int     // cho giới hạn mới/ngày
  reviews(groupId): List<ContrastReview>
```

| Câu hỏi | Repository |
|---------|------------|
| Đọc content | `ContrastContentRepository`; nguồn gốc qua `ContrastSourceResolver` |
| Lưu attempt | `ContrastPracticeRepository.recordAnswer` |
| Quản lý progress | `ContrastProgressRepository` (được ghi trong transaction của `recordAnswer`) |
| Quản lý lịch ôn | `ContrastProgressRepository` (cùng dòng với progress) |
| Xử lý phiên học | `ContrastPracticeRepository` |
| Import gói | `ContrastPackImporter` (data/importing), gọi từ `installBundledContent()`; không phải repository học |

## 3. Use cases

| Use case | Đầu vào | Đầu ra | Trách nhiệm |
|----------|---------|--------|-------------|
| `ObserveContrastGroups` | `ContrastFilter` | `Flow<List<GroupListEntry>>` (nhóm + mastery + dueAt?) | Danh sách, lọc và tìm kiếm. Tìm không dấu theo tiêu đề, nhãn mục, cách đọc và nghĩa |
| `GetContrastGroupDetail` | groupId | `ContrastDetail?` với `sources` đã resolve; hoặc `Retired(reason, replacedBy)` | Màn lý thuyết. Không ghi gì |
| `StartOrResumePractice` | `PracticeScope`, now | `ContrastSession` | Nếu có phiên ACTIVE cùng scope và chưa quá hạn tiếp tục (mặc định 24 giờ, thử nghiệm) thì trả về phiên đó; vị trí có câu hỏi không còn thì đánh dấu UNAVAILABLE. Ngược lại thì lên kế hoạch phiên mới (mục 4, R9) |
| `SubmitContrastAnswer` | sessionId, position, `AnswerInput` (Chosen(optionId) hoặc Skipped), requestId, now, zone | `SubmitResult(outcome, explanation, blockCompleted, review?)` | Chấm điểm, ghi attempt; nếu khối hoàn tất thì quy ra rating, tính bậc và hạn ôn, ghi review + progress (một transaction) |
| `GetPracticeResult` | sessionId | `PracticeResult` (đúng/sai/bỏ qua/chưa làm, theo nhóm, cặp nhầm của phiên, lịch ôn mới) | Màn kết quả |
| `AnalyzeContrastMistakes` | `TimeRange?`, contentType? | `MistakeReport` (mục 4, R10) | Phân tích lỗi |
| `GetContrastProgress` | groupId? | Tổng quan hoặc chi tiết một nhóm: mastery, accuracy trì hoãn, lịch ôn | Màn tiến độ |
| `GetDueContrastReviews` | now | `DueSummary(dueCount, newAvailableToday, nextDueAt?)` | Màn chính Contrast |

Thay đổi so với danh sách đề xuất, kèm lý do:
- `GetContrastGroups` + `SearchContrastContent` → **`ObserveContrastGroups`**: tìm kiếm chỉ là một bộ lọc của cùng danh sách. Hai use case sẽ trùng logic sắp xếp và mastery.
- `GeneratePractice` + `ResumeContrastSession` → **`StartOrResumePractice`**: quyết định tạo mới hay tiếp tục phải nằm ở một chỗ, để không bao giờ có hai phiên ACTIVE.
- `ScheduleContrastReview` và `UpdateContrastProgress` → **gộp vào `SubmitContrastAnswer`**, phần tính toán nằm trong lớp thuần `ContrastScheduler`. Lập lịch chỉ xảy ra khi một khối hoàn tất, và phải cùng transaction với attempt cuối. Nếu để thành use case công khai riêng, UI có thể gọi lệch hoặc gọi hai lần.

## 4. Domain rules

| # | Quy tắc |
|---|---------|
| R1 | **Đọc lý thuyết không phải là thành thạo.** Mở `ContrastDetail` không tạo progress, attempt hay review. Mastery chỉ tính từ attempts |
| R2 | **Bỏ dở không phải là sai.** Thoát giữa phiên thì các vị trí không có attempt là `PENDING`, không tính vào accuracy hay lịch. Phiên chuyển sang ABANDONED khi bắt đầu phiên khác hoặc quá hạn tiếp tục |
| R3 | **Ba trạng thái tách biệt:** INCORRECT (đã chọn, sai), SKIPPED (người học bấm "Bỏ qua", tức là bỏ trống), PENDING (chưa trả lời). Accuracy = CORRECT / (CORRECT + INCORRECT). SKIPPED được đếm riêng. PENDING không xuất hiện trong thống kê |
| R4 | **Luôn lưu lịch sử.** Attempt và review là bất biến, không có thao tác xóa trong repository. Cập nhật gói không xóa chúng |
| R5 | **Nhầm có hướng.** Một cặp nhầm là `(expectedItemId → chosenItemId)`, nên A → B khác B → A. Option nhiễu (`itemId = null`) được tính là "sai khác", không tạo cặp nhầm |
| R6 | **Không dùng số lần mở app, thời gian dùng hay tốc độ trả lời làm chỉ số thành thạo.** Không lưu thời lượng |
| R7 | **Không XP, streak, huy hiệu, đếm ngược hay thông báo thúc giục.** Màn chính chỉ hiện số nhóm đến hạn và nhóm mới có thể học |
| R8 | **Khối và lịch:** mỗi nhóm trong phiên có một khối k câu (mặc định 3). Khi mọi câu của khối có outcome thì quy ra rating theo `ContrastRatingPolicy` (ARCHITECTURE mục 8). Bỏ qua từ một nửa số câu trở lên thì không cập nhật lịch |
| R9 | **Chọn câu (planner):** nhóm đến hạn trước, sau đó nhóm mới (trong giới hạn mới/ngày). Trong một khối: ưu tiên dạng bài khác nhau; ưu tiên câu chưa từng làm có `difficulty` bằng mức của lần trước; nếu đã làm hết thì lấy câu làm lâu nhất; không lặp một câu trong cùng phiên. Câu sai được hỏi lại ở cuối phiên bằng **một câu khác** của cùng nhóm, nếu còn; câu hỏi lại không tính vào khối |
| R10 | **Phân tích lỗi** (ARCHITECTURE mục 9). Chỉ số chính là độ chính xác trì hoãn ≥ 7 ngày; ưu tiên tập câu chưa từng làm |
| R11 | **Mastery (thử nghiệm, `MasteryPolicy` cấu hình được, không phải chuẩn khoa học).** `MASTERED` khi đủ cả: ≥ 12 câu đã trả lời (trừ SKIPPED); ≥ 3 phiên ở 3 ngày khác nhau; ≥ 4 câu trì hoãn với accuracy trì hoãn ≥ 85%; các câu trì hoãn thuộc ≥ 2 dạng bài; khối trì hoãn gần nhất không phải AGAIN. Mất MASTERED khi một khối trì hoãn sau đó là AGAIN. `REVIEWING` khi đã có bậc ≥ 7 ngày. `LEARNING` khi đã có progress. `NEW` khi chưa có progress |
| R12 | **Độc lập với SRS cũ:** không đọc hay ghi `learning_progress`, `review_records`, `ReviewSettings`, `DailyLimits` |

## 5. Versioning và cập nhật gói

`progressVersion = 1` cho định dạng ở mục 1.2. Chỉ tăng khi ý nghĩa dữ liệu đã lưu đổi, ví dụ thêm outcome mới. Khi đó phải kèm chuyển đổi có test. Đổi ngưỡng thử nghiệm **không** làm tăng `progressVersion`, vì rating đã quy ra được lưu trong review.

Khi một gói mới được nhập:

| Tình huống | Hành vi |
|------------|---------|
| Câu hỏi cùng id, cùng revision | Không có gì |
| Câu hỏi cùng id, revision tăng (sửa chữ, giải thích; đáp án giữ nguyên ý nghĩa) | Attempt cũ giữ nguyên và vẫn tính vào thống kê; lịch sử hiện `chosenText`/`correctText` đã chụp |
| Đáp án đúng hoặc tập option đổi ý nghĩa | **Bắt buộc id mới**. Công cụ phát hiện khi so với gói đã phát hành trước (`--previous`) và báo ERROR `ANSWER_CHANGED_SAME_ID`. Id cũ biến mất khỏi gói; attempt cũ giữ nguyên, hiện kèm nhãn "câu hỏi đã được thay" |
| Câu hỏi bị bỏ | Attempt giữ nguyên, nhãn "câu hỏi không còn". Planner không chọn nữa. Phiên ACTIVE: vị trí đó UNAVAILABLE (không tính sai) |
| Nhóm có `revision` tăng | Progress, lịch ôn và attempt giữ nguyên, không reset |
| Nhóm bị bỏ, có trong `retiredGroups` | Ẩn khỏi danh sách; lịch sử và phân tích vẫn hiện (kèm lý do; có `replacedBy` thì liên kết sang nhóm mới). Progress giữ nguyên nhưng không đến hạn nữa. **Không tự chuyển tiến độ sang nhóm mới** (xem vấn đề mở) |
| Nhóm biến mất, không có trong `retiredGroups` | Lúc build: ERROR. Nếu vẫn lọt tới app: WARNING `GROUP_DISAPPEARED`, xử lý như nhóm bị bỏ không rõ lý do. Không xóa gì |
| `contentVersion` không lớn hơn bản đã cài | `UP_TO_DATE`, không ghi gì |

## 6. Kiểm chứng độc lập (bắt buộc ở các milestone sau)

- Sau một phiên Contrast đầy đủ, các bảng `learning_progress`, `review_records`, `item_marks`, `review_sessions`, `error_records` và `review_items` phải không đổi một byte. Kiểm tra bằng checksum từng dòng.
- `observeReviewOverview` của SRS cũ, số liệu Trang chủ và Thống kê phải bằng trước phiên.
- Nhập, cập nhật hoặc xóa gói Contrast không đổi số dòng của `grammar`, `vocabulary`, `kanji`.

## 7. Validation rules (domain)

Các quy tắc validate **gói** nằm ở CONTENT_SCHEMA mục 8. Domain còn tự kiểm tra lúc chạy:
- `SubmitContrastAnswer`:
  - `Chosen(optionId)` phải thuộc câu hỏi; nếu không thì từ chối, không ghi.
  - Vị trí phải thuộc phiên ACTIVE và chưa có attempt; nếu đã có thì `ALREADY_ANSWERED`, không ghi.
- Thang Contrast phải qua `ReviewLadder.validate`; thang lưu trong cài đặt bị hỏng thì quay về mặc định (như `ReviewLadder.decodeOrDefault`).
- Cấu hình thử nghiệm (k, các ngưỡng rating, `MasteryPolicy`, giới hạn mới/ngày, hạn tiếp tục phiên) có giá trị mặc định và miền hợp lệ. Giá trị ngoài miền thì dùng mặc định.

## 8. Triển khai M1 (2026-11-17) và các điểm khác bản đặc tả

Code: `app/src/main/java/com/n3sensei/app/domain/contrast/` và `domain/repository/Contrast*.kt`. Kotlin thuần, không Room/Android/Compose.

| Bản đặc tả | Đã triển khai | Lý do |
|------------|---------------|-------|
| `CommonMistake.expectedItemId?` + `confusedWithItemId?` | `CommonMistake.confusion: Confusion?` (`expectedItemId`, `chosenItemId`) | Hai trường luôn đi cùng nhau; một kiểu chung cho mọi hướng nhầm |
| `ResolvedSource(status, currentId)` | thêm `candidateIds` | Báo cáo "có ứng viên" khi id mất (không bao giờ tự nối lại) |
| `SessionItem.blockIndex: Int` | `Int?`, null = câu hỏi lại cuối phiên | Câu hỏi lại không thuộc khối nào (R9) |
| Trạng thái chưa làm | `PositionState.PENDING` (+ `UNAVAILABLE` khi câu hỏi rời gói) | Đúng tên trong contract; yêu cầu M1 gọi là UNANSWERED |
| `answeredQuestionIds(groupId)` | `lastAnsweredAt(groupId): Map<questionId, Instant>` | Planner cần thời điểm, không chỉ id |
| `stats(range): ContrastStatsRows` (chưa định nghĩa) | `attemptsBetween(from, to)` + `attemptsOfGroup(groupId)` | Phân tích trì hoãn cần từng câu trả lời; tối ưu SQL để M4 |
| `history(groupId?, page)` | `attemptsOfGroup(groupId)` | Như trên; phân trang để M4 khi có UI lịch sử |
| – | `session(sessionId)` | `GetPracticeResult` đọc cả phiên đã xong |
| `newGroupsIntroducedOn(dayStart, dayEnd)` | `newGroupsIntroducedBetween(start, end)` | Cùng nghĩa, tên rõ khoảng nửa mở |
| – | `allProgress()` | Tổng quan tiến độ |
| `recordAnswer(...): SubmitResult` | `RecordAnswerResult` (RECORDED/DUPLICATE/ALREADY_ANSWERED); use case trả `SubmitOutcome` | Tách kết quả lưu trữ khỏi kết quả nghiệp vụ |
| Kích thước phiên | `ContrastConfig.maxGroupsPerSession = 10` (THỬ NGHIỆM, **thêm mới**) | Contract không giới hạn số nhóm mỗi phiên |

Thang, ngưỡng rating, ngưỡng thành thạo, k, giới hạn mới/ngày, hạn tiếp tục phiên, khoảng trì hoãn: tất cả trong `ContrastConfig` (đánh dấu THỬ NGHIỆM, chưa phải quyết định sản phẩm).

Chấm điểm: so sánh **id lựa chọn** với `correctOptionId`; không có nhập tự do nên không có chuẩn hóa hay danh sách đáp án chấp nhận (contract không có `acceptedAnswers`). ERROR_DETECTION: người học chọn đoạn sai; `correction` được kiểm tra khi validate gói (`correction.itemId == targetItemId`) và hiện sau khi trả lời; `correctText` của attempt là "đoạn sai → sửa".

Lập lịch: chỉ dùng `SpacedRepetitionScheduler.nextStep()` trên thang Contrast và `ReviewInterval.addTo()`; không gọi `schedule()` (gắn `ItemType`/`LearningProgress`). Khi gói cập nhật: vị trí có câu hỏi đã rời gói là UNAVAILABLE, không chặn khối, không tính sai.

### 8.1. Quyết định 2026-11-17 (D1–D5) đã áp dụng

- **D1 tự duyệt**: `approval` có thêm `authoredBy` và `approvedRevision` (contract cũ không lưu được revision được duyệt và không phân biệt tự duyệt). Bản phát hành cần `approvedBy` trong danh sách người duyệt của công cụ.
- **D2**: giữ `ContrastConfig` (1/3/7/14/30/60 ngày, 3 câu/khối, 3 nhóm mới/ngày chỉ tính nhóm mới, 10 nhóm/phiên); có test.
- **D3**: thêm ITEMS_CHANGED_SAME_GROUP_ID và REVISION_NOT_INCREMENTED (ERROR), tổng 40 mã. `ContrastPackValidator.validate(..., previousGroups)` nhận nội dung đang cài (M4 đọc từ Room).
- **D4**: không chuyển tiến độ giữa nhóm khác id; không xóa attempt/progress/review/session (bảng người học không có FK, xem M3).
- **D5**: giữ nguyên fingerprint.
