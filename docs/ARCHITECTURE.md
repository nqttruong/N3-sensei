# Kiến trúc N3 Sensei

Một module Gradle (`:app`), phân lớp theo package. Khi số feature hoặc thời gian build tăng đáng kể mới tách module; ranh giới package hiện tại đã được giữ bằng test (`ArchitectureRulesTest`) để việc tách sau này không phải sửa phụ thuộc.

## Sơ đồ (trạng thái thực tế sau Prompt 03)

```
┌──────────────────────────────────────────────────────────────────┐
│ MainActivity ─▶ N3SenseiTheme ─▶ N3SenseiApp (Scaffold)          │
│                                   ├─ N3TopAppBar  (title, Up)    │
│                                   ├─ N3NavigationBar (5 tab)     │
│                                   └─ N3SenseiNavHost             │
│ app/navigation: Routes · AppScreen · TopLevelDestination ·       │
│                 navigateToTopLevel                               │
│ app/di: AppContainer (Application scope) · appViewModel { }      │
│         └─ N3Database (lazy) · ContentRepository · ProgressRepo. │
└───────────────┬──────────────────────────────────────────────────┘
                │ callback điều hướng (không truyền NavController)
┌───────────────▼──────────────────────────────────────────────────┐
│ feature/  home · learn · vocabulary · kanji · grammar ·          │
│           review · stats · settings          (Composable)        │
│   └─ (sắp tới) XxxViewModel ─ StateFlow<UiState<T>>              │
└───────────────┬──────────────────────────────────────────────────┘
                │ dùng chung
┌───────────────▼──────────────────────────────────────────────────┐
│ core/designsystem: theme · N3Icons · FeaturePendingContent       │
│ core/ui/state:     UiState · asUiState() · UiStateContent        │
└──────────────────────────────────────────────────────────────────┘


┌──────────────────────────────────────────────────────────────────┐
│ domain (Kotlin thuần)                                            │
│   model: Vocabulary · Kanji · GrammarPoint · LessonSummary ·     │
│          LearningProgress · ReviewRecord · ItemRef · ...         │
│   repository: ContentRepository · ProgressRepository (interface) │
│   furigana: Furigana (định dạng {漢字|かんじ})                    │
└───────────────▲──────────────────────────────────────────────────┘
                │ cài đặt interface
┌───────────────┴──────────────────────────────────────────────────┐
│ data                                                             │
│   repository: OfflineContentRepository · OfflineProgressRepo.   │
│               (entity ⇄ domain mapper)                           │
│   local: N3Database · ContentDao · ContentPackDao · ProgressDao  │
│          entity/ · N3Migrations · Converters                     │
└──────────────────────────────────────────────────────────────────┘
```

Đồ thị điều hướng:

```
NavHost (start = HomeRoute)
├── HomeRoute                     tab Trang chủ
├── LearnGraphRoute               tab Học (nested graph)
│   ├── LearnHubRoute   (start)
│   ├── VocabularyRoute           Up/Back ─▶ LearnHub
│   ├── KanjiRoute                Up/Back ─▶ LearnHub
│   └── GrammarRoute              Up/Back ─▶ LearnHub
├── ReviewRoute                   tab Ôn tập
├── StatsRoute                    tab Thống kê
└── SettingsRoute                 tab Cài đặt
```

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
sealed interface UiState<out T> { Loading; Empty; Success(data); Error(cause) }

// ViewModel
val state = repository.observeX().asListUiState()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

// Composable
UiStateContent(state, emptyMessage = stringResource(R.string.x_empty), onRetry = viewModel::retry) { data -> ... }
```

- `Empty` là kết quả thật "không có gì" (không mục đến hạn, không kết quả tìm kiếm), khác `Loading`.
- `Error` chỉ hiện nút Thử lại khi có `onRetry`.
- Màn hình chưa có chức năng dùng `FeaturePendingContent`: ghi rõ "Đang phát triển", không có nút giả.

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

### Accessibility
- Tiêu đề màn hình đánh dấu `heading()`.
- Icon trang trí `contentDescription = null` khi đã có nhãn chữ; icon hành động phải có mô tả.
- Vùng chạm tối thiểu 48dp (component Material 3 mặc định đáp ứng).
