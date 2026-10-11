# Kiến trúc N3 Sensei

Một module Gradle (`:app`), phân lớp theo package. Khi số feature hoặc thời gian build tăng đáng kể mới tách module; ranh giới package hiện tại đã được giữ bằng test (`ArchitectureRulesTest`) để việc tách sau này không phải sửa phụ thuộc.

## Sơ đồ (trạng thái thực tế sau Prompt 08)

```
┌──────────────────────────────────────────────────────────────────┐
│ MainActivity ─▶ N3SenseiRoot (MainViewModel: theme, onboarding,  │
│                 furigana) ─▶ OnboardingScreen | N3SenseiApp      │
│                                   ├─ N3TopAppBar  (title, Up)    │
│                                   ├─ N3NavigationBar (5 tab)     │
│                                   └─ N3SenseiNavHost             │
│ app/navigation: Routes · AppScreen · TopLevelDestination ·       │
│                 navigateToTopLevel                               │
│ app/di: AppContainer (Application scope) · appViewModel { }      │
│         └─ N3Database (lazy) · Content/Progress/UserPreferences  │
│            repositories · Clock                                  │
└───────────────┬──────────────────────────────────────────────────┘
                │ callback điều hướng (không truyền NavController)
┌───────────────▼──────────────────────────────────────────────────┐
│ feature/  onboarding · home · learn · lessons · vocabulary ·     │
│           kanji · grammar · practice · review · stats · settings │
│   XxxScreen(viewModel, callbacks) ◀─ XxxViewModel                │
│                                       StateFlow<UiState<T>>      │
└───────────────┬──────────────────────────────────────────────────┘
                │ dùng chung
┌───────────────▼──────────────────────────────────────────────────┐
│ core/designsystem: theme (+ japanese()) · N3Icons · FuriganaText │
│                    SearchField · FilterChipRow · InfoBanner · …  │
│ core/ui/content:   VocabularyCard · KanjiCard · GrammarCard ·    │
│                    LessonCard · ProgressSummaryCard · …          │
│ core/ui/state:     UiState · DetailViewModel ·                   │
│                    TrackedDetailViewModel · ItemBrowserViewModel │
└──────────────────────────────────────────────────────────────────┘


┌──────────────────────────────────────────────────────────────────┐
│ domain (Kotlin thuần)                                            │
│   model: Vocabulary · Kanji · GrammarPoint · LessonSummary ·     │
│          LearningProgress · ReviewRecord · ItemRef · ...         │
│   repository: Content · Progress · ContentImport · UserPrefs     │
│   importing: ImportReport (mã lỗi, số bản ghi)                   │
│   furigana: Furigana (định dạng {漢字|かんじ})                    │
│   practice: PracticeSession (luyện tập, không ghi lịch ôn)       │
│   reminder: ReminderRunner · ReminderPolicy (xem REMINDERS.md)   │
│   analytics: LearningAnalytics · StatsPeriod (xem ANALYTICS.md)  │
│   srs: SpacedRepetitionScheduler · ReviewLadder · ReviewTime ·   │
│        ReviewQueuePlanner · SubmitReviewUseCase  (xem SRS.md)    │
└───────────────▲──────────────────────────────────────────────────┘
                │ cài đặt interface
┌───────────────┴──────────────────────────────────────────────────┐
│ data   (preferences: DataStoreUserPreferencesRepository)         │
│   importing: ContentPackJson · ContentPackValidator ·            │
│              ContentPackImporter · AndroidContentImportRepo.     │
│   repository: OfflineContentRepository · OfflineProgressRepo.   │
│               (entity ⇄ domain mapper)                           │
│   local: N3Database (v2) · ContentDao · ContentPackDao ·         │
│          ProgressDao · entity/ · N3Migrations · SearchText       │
└──────────────────────────────────────────────────────────────────┘
```

Đồ thị điều hướng (OnboardingScreen nằm ngoài graph, hiện trước khi người dùng hoàn tất giới thiệu):

```
NavHost (start = HomeRoute)
├── HomeRoute                          tab Trang chủ
├── LearnGraphRoute                    tab Học (nested graph)
│   ├── LearnHubRoute   (start)
│   ├── LessonListRoute ─▶ LessonDetailRoute(lessonId)
│   │                        ├─▶ Vocabulary/Kanji/GrammarDetailRoute(id)
│   │                        └─▶ PracticeRoute(lessonId?, markedOnly) ─▶ PracticeResultRoute(...)
│   │                              (kết quả thay thế phiên; Back về chi tiết bài)
│   ├── VocabularyRoute ─▶ VocabularyDetailRoute(id)
│   ├── KanjiRoute      ─▶ KanjiDetailRoute(id)
│   └── GrammarRoute    ─▶ GrammarDetailRoute(id)
├── ReviewRoute                        tab Ôn tập
│   └─▶ ReviewSessionRoute ─▶ ReviewResultRoute (thay thế phiên)
├── StatsRoute                         tab Thống kê
└── SettingsRoute                      tab Cài đặt
    ├─▶ ReviewSettingsRoute
    └─▶ ReminderSettingsRoute
```

## Màn hình và nguồn dữ liệu

| Màn hình | Nguồn dữ liệu | Ghi chú |
|---|---|---|
| Giới thiệu | DataStore (`onboarding_completed`) | Bỏ qua được; xem lại từ Cài đặt |
| Trang chủ | Room: tổng nội dung, tóm tắt tiến độ | Báo rõ khi chưa nạp gói nội dung |
| Danh sách / chi tiết bài học | Room | Tab Từ vựng / Kanji / Ngữ pháp |
| Từ vựng, Kanji, Ngữ pháp (danh sách) | Room: Flow kèm trạng thái học và đánh dấu | Tìm kiếm không dấu (debounce 300 ms, tối đa 100), lọc theo bài, trạng thái, đã đánh dấu, chủ đề (từ vựng); luyện tập mục đã đánh dấu |
| Thẻ từ, chi tiết Kanji, ngữ pháp | Room (đọc trong transaction) + `item_marks` | Furigana, ví dụ, nguồn và giấy phép, trạng thái học, đánh dấu cần chú ý, luyện tập bài chứa mục; ngữ pháp có bài tập ngắn; Kanji ghi rõ chưa có thứ tự nét |
| Luyện tập + kết quả | Room (nội dung bài hoặc mục đã đánh dấu) | Đánh giá chỉ dùng cho kết quả phiên, **không** ghi vào lịch ôn |
| Ôn tập | Room: đến hạn theo loại, quá hạn, mục mới, số đã làm hôm nay; DataStore: giới hạn | Bắt đầu phiên ôn theo lịch; luyện tập theo bài / mục đã đánh dấu |
| Phiên ôn + kết quả | Room (hàng đợi, tiến độ) | Mỗi câu trả lời lưu ngay (idempotent), nút hiện khoảng ôn kế tiếp |
| Cài đặt nhắc học | DataStore + WorkManager | Bật/tắt (xin quyền), khung giờ, tần suất, nội dung, riêng tư màn hình khóa, gửi thử, ghim widget |
| Cài đặt ôn tập | DataStore | Sửa dãy bậc, giới hạn mỗi ngày, giải thích quy tắc |
| Thống kê | Room (`AnalyticsDao`, loại seed/test và mục đã gỡ) | Lượt vs mục, tỷ lệ nhớ, theo ngày, dự báo (ước tính), chuỗi ngày học, trạng thái theo loại, hay quên; bộ lọc thời gian; liên kết sang danh sách/chi tiết |
| Cài đặt | DataStore (theme, furigana) + Room (gói, nguồn nội dung) + nhập JSON | Khoảng ôn, nhắc học, sao lưu: hiển thị "Chưa triển khai", không có hành động |

Không màn hình nào dùng dữ liệu cứng. `PreviewContent` chỉ dùng cho `@Preview`; dữ liệu seed chỉ có trong mã test.

## Quy tắc phụ thuộc

| Package | Được phụ thuộc vào | Không được |
|---|---|---|
| `feature/*` | `core/*`, `domain` | `data`, Room, `androidx.navigation`, `app.*`, feature khác |
| `core/ui`, `core/designsystem` | Compose, `R` | `data`, Room, `app.di` |
| `domain` | Kotlin, Coroutines | `android.*`, `androidx.*`, `data`, `feature` |
| `data` | `domain`, Room, DataStore | `feature`, `core/ui`, `app`, Compose |
| `app` | tất cả | — |

Tất cả các dòng trên (trừ `app`) được kiểm tra tự động bởi `ArchitectureRulesTest` bằng cách quét import.

Chi tiết database: [DATABASE.md](DATABASE.md).

## Điều hướng

- Route type-safe (`@Serializable data object`) khai báo **duy nhất** trong `app/navigation/Routes.kt`.
- `AppScreen` đăng ký mọi màn hình lá: route, tiêu đề top bar, tab sở hữu, có phải gốc tab hay không. Test bảo đảm graph và registry khớp 1-1.
- `TopLevelDestination`: 5 tab (tối đa theo Material cho điện thoại). Từ vựng/Kanji/Ngữ pháp nằm trong tab Học vì 7 khu vực không vừa thanh điều hướng.
- Chuyển tab qua `navigateToTopLevel`: mỗi tab một bản trên back stack, giữ trạng thái stack con, Back ở gốc tab về Trang chủ, Back ở Trang chủ thoát app.
- Màn hình feature nhận callback (`onOpenKanji`), không nhận `NavController`.

## UI state

```kotlin
sealed interface UiState<out T> { Loading; Empty; NotFound; Success(data); Error(cause) }

// ViewModel
val state = repository.observeX().asListUiState()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

// Composable
UiStateContent(state, emptyMessage = stringResource(R.string.x_empty), onRetry = viewModel::retry) { data -> ... }
```

- `Empty` là kết quả thật "không có gì" (không mục đến hạn, không kết quả tìm kiếm), khác `Loading`.
- `Error` chỉ hiện nút Thử lại khi có `onRetry`.
- Chức năng chưa triển khai được báo bằng `InfoBanner` hoặc mục "Chưa triển khai" không có hành động; không có nút giả.
- Màn hình chi tiết dùng `UiState.NotFound` khi mục không tồn tại (vd. đã bị gỡ khi cập nhật nội dung).

## Dependency injection

**Chọn: DI thủ công** — `AppContainer` tạo một lần trong `N3SenseiApplication` (phạm vi Application, không phải `object` toàn cục). ViewModel được tạo bằng `appViewModel { XxxViewModel(repo) }` ở tầng navigation rồi truyền vào màn hình.

Lý do: một module, ít dependency; tránh thêm Hilt (plugin Gradle, annotation processing, thời gian build) khi chưa cần. Mọi lớp nhận dependency qua constructor nên chuyển sang Hilt sau này chỉ cần thêm annotation, không phải viết lại.

## Quy ước

### Đặt tên code
- Màn hình: `XxxScreen` (stateless, nhận state + callback). Khi có ViewModel: `XxxRoute` (stateful) gọi `XxxScreen`.
- ViewModel `XxxViewModel`, state `XxxUiState`, repository `XxxRepository` (interface ở `domain`) / `OfflineXxxRepository` (impl ở `data`).
- Route `XxxRoute`; graph lồng `XxxGraphRoute`.
- Test: `ClassUnderTestTest`; tên hàm mô tả hành vi (`systemBack_fromAnyTabRoot_returnsToHome`).

### Resource
- Chuỗi: tất cả text hiển thị ở `res/values/strings.xml`, tên `<khu vực>_<phần tử>`: `app_*`, `nav_*`, `common_*`, `<feature>_*` (vd. `kanji_title`, `home_review_card_action`). Không hardcode chuỗi trong Composable.
- Màu: chỉ dùng `MaterialTheme.colorScheme`; giá trị màu khai báo ở `core/designsystem/theme/Color.kt`. `res/values/colors.xml` chỉ cho icon launcher.
- Icon: lấy qua `N3Icons`; drawable đặt tên `ic_<tên>`.
- Kích thước: bội số 4dp (4, 8, 12, 16, 24).

### Hiển thị tiếng Nhật
- Mọi `TextStyle` dùng cho chữ Nhật gọi `.japanese()` (locale `ja-JP`) để hiển thị đúng dạng chữ Kanji của Nhật.
- Furigana: `FuriganaText(markup)`; bật/tắt bằng cài đặt qua `LocalShowFurigana`.
- Thẻ luyện tập và màn hình chi tiết đã được kiểm tra trên màn hình 360×640dp với cỡ chữ hệ thống 130%.

### Accessibility
- Tiêu đề màn hình đánh dấu `heading()`.
- Icon trang trí `contentDescription = null` khi đã có nhãn chữ; icon hành động phải có mô tả.
- Vùng chạm tối thiểu 48dp (component Material 3 mặc định đáp ứng).
