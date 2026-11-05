# Audit giao diện N3 Sensei (2026-10-28)

Phạm vi: toàn bộ UI Compose trong `app/src/main/java/com/n3sensei/app` tại commit `f79338e`.
Mục tiêu của đợt thiết kế lại: phong cách "Japanese Editorial + Study Dashboard": nền trung
tính, chữ rõ, xanh rêu làm màu nhấn, sáng/tối, animation ngắn có mục đích.

Không thay đổi trong đợt này: database, ID nội dung, thuật toán SRS, tiến độ, quyết định
nghiệp vụ của Flashcard và Bài học (24/30/7, chuyển tab tự do, Luyện tập không ghi lịch).

## 1. Màn hình và điều hướng

Khung: `N3SenseiApp` = `Scaffold` + `CenterAlignedTopAppBar` (tiêu đề theo `AppScreen`, nút Up
khi không phải màn gốc của tab) + `NavigationBar` 5 tab (ẩn trong phiên học `isFocusSession`).
`N3SenseiRoot` áp dụng theme theo cài đặt, hiển thị Onboarding lần đầu và các hộp thoại hệ thống.

| Tab | Màn hình (route) | File |
|---|---|---|
| Trang chủ | Dashboard (`HomeRoute`) | `feature/home/HomeScreen.kt` |
| Học | Trung tâm Học (`LearnHubRoute`) | `feature/learn/LearnHubScreen.kt` |
| | Bài học (`LessonListRoute`) → Chi tiết bài (`LessonDetailRoute`, 3 tab) | `feature/lessons/*` |
| | Từ vựng / Kanji / Ngữ pháp (danh sách, chi tiết, sửa) | `feature/vocabulary`, `kanji`, `grammar`, `edit` |
| | Từ của tôi (= danh sách từ vựng lọc theo bài `user-words-l1`), Thêm từ | `VocabularyListScreen`, `ContentEditScreen` |
| | Luyện tập (phiên tập trung) → Kết quả | `feature/practice/*` |
| Ôn tập | Tổng quan ôn (`ReviewRoute`) → Phiên ôn (tập trung) → Kết quả | `feature/review`, `reviewsession` |
| Thống kê | Thống kê (`StatsRoute`) | `feature/stats/StatsScreen.kt` |
| Cài đặt | Cài đặt, Lịch ôn, Nhắc học, Sao lưu (dialog), Nhập gói (dialog) | `feature/settings/*` |
| (ngoài tab) | Onboarding 3 trang, Widget "từ để học" (Glance) | `feature/onboarding`, `app/widget` |

24 màn hình lá, đăng ký trong `AppScreen` (tiêu đề, tab sở hữu, màn gốc, phiên tập trung).

## 2. Theme, màu, chữ, component dùng chung

- **Màu** (`core/designsystem/theme`): primary chàm (Indigo), secondary đỏ son (Vermilion),
  tertiary matcha; nền gần trắng hơi tím (`#FEFBFF`). Chỉ khai báo ~19 vai trò màu; các vai trò
  `surfaceContainerLow/High/Highest`, `surfaceDim/Bright`, `outline`, `outlineVariant`,
  `inverse*` **rơi về mặc định Material 3 (tông tím)**: thẻ kanji đi kèm trên flashcard
  (`surfaceContainerHighest`), dòng từ trong bài (`surfaceContainerHigh`), thẻ `ElevatedCard`
  (`surfaceContainerLow`) đang mang sắc tím lạc tông với chàm/son.
- **Chữ**: `Typography()` mặc định; mọi cấp đều sans. Không có font riêng; `japanese()` gắn
  locale `ja-JP` để chọn đúng tự hình Nhật (giữ nguyên, rất quan trọng).
- **Hình khối**: `Shapes` mặc định; không có token spacing (16/8/12/4 dp được viết tay ở >150 chỗ).
- **Dark theme**: có, tự viết; `dynamicColor` tắt. Test `ColorContrastTest` kiểm 12 cặp màu ≥ 4.5:1.
- **Component dùng chung** (`core/designsystem/component`): `SectionHeader`, `DetailSection`/
  `DetailText`, `FilterChipRow`, `SearchField`, `InfoBanner`, `FuriganaText`, `DailyBarChart`,
  `StackedBar`, icon `N3Icons`.
  (`core/ui/content`): `FlashcardSession`, `KanjiCard`, `VocabularyCard`, `GrammarCard`,
  `ItemBrowser`, `ItemStatusBadge`, `ItemActions`, `StatItem`, `ProgressSummaryCard`,
  `ExampleSentenceItem`/`SourceNote`, `ContentTotalsRow`, `UiStateContent` (loading/empty/error).

## 3. Vấn đề phân cấp, khoảng cách, khả năng đọc, nhất quán

1. **Ba kiểu thẻ lẫn lộn**: `ElevatedCard` (Trang chủ, Thống kê, Ôn tập, Kết quả, Flashcard),
   `OutlinedCard` (danh sách, Bài học), `Surface` tô màu (ô kanji, dòng từ). Không có quy tắc khi
   nào nổi, khi nào viền.
2. **Hai bản sao của cùng một thẻ có tiêu đề**: `DashboardCard` (Trang chủ) và `StatsCard`
   (Thống kê); hai bản `Note`; huy hiệu "Thực tế/Ước tính" chỉ có ở Trang chủ.
3. **Trang chủ** dài 7 thẻ cùng trọng lượng; hành động chính (bắt đầu phiên) nằm trong thẻ đầu
   nhưng không nổi bật hơn các thẻ số liệu. Nhiều dòng ghi chú `bodySmall` xám xếp liền nhau.
4. **Thống kê**: 8 thẻ cùng kiểu; tiêu đề thẻ dùng `SectionHeader` màu primary cỡ `titleSmall`
   nên phân cấp yếu so với số liệu bên trong.
5. **Flashcard**: thanh tiến độ + nhãn + banner + phụ đề chiếm nhiều chỗ phía trên thẻ; mặt sau
   xuất hiện đột ngột (không chuyển tiếp); thẻ mới thay thẻ cũ không có dấu hiệu chuyển thẻ;
   chữ kanji `displayLarge` sans nên thiếu chất "sách".
6. **Bài học**: tiêu đề bài `titleLarge` và 3 thanh tiến độ mặc định cùng màu; nút "Mở bài" lặp
   lại hành động của cả thẻ (thẻ đã bấm được).
7. **Cài đặt**: danh sách phẳng dài, tiêu đề nhóm nhỏ; danh sách gói nội dung là các dòng text
   rời, không thẳng hàng với `ListItem`.
8. **Ô kanji** dùng `primaryContainer` với kích thước khác nhau ở 3 nơi (48, 64, 112 dp) và kiểu
   bo góc khác nhau.
9. **Màn hình rộng/ngang**: mọi màn hình kéo giãn hết chiều ngang; trên tablet dòng chữ quá dài,
   flashcard rộng bất thường.
10. **Màu đánh giá/trạng thái** được chọn tại chỗ ở 3 nơi (Flashcard, Thống kê × 2) — không có
    một nguồn duy nhất.

Điểm tốt cần giữ: semantics/TalkBack (heading, mergeDescendants, liveRegion, locale Nhật cho
TalkBack), co giãn theo cỡ chữ (nút đánh giá 2 hàng, nhãn tab bar), cuộn vùng thẻ để nút luôn ở
dưới, trạng thái loading/empty/error thống nhất.

## 4. Component có thể tái sử dụng (đề xuất)

| Component | Thay cho |
|---|---|
| `N3Card` (phẳng, viền mảnh, `surfaceContainerLow`) + biến thể nổi bật | `ElevatedCard`/`OutlinedCard` tự chọn |
| `SectionCard(title, tag, content)` | `DashboardCard`, `StatsCard` |
| `Tag` (nhãn viên thuốc nhỏ, tông trung tính/nhấn) | `Badge` Trang chủ, nhãn trạng thái |
| `Caption` | 2 bản `Note` |
| `N3ProgressBar` (bo tròn, có animation, giảm chuyển động) | `LinearProgressIndicator` ×5 nơi |
| `GlyphTile` (ô chữ Hán, 3 cỡ) | ô kanji ở LearnHub, KanjiCard, KanjiDetail, Bài học |
| `StudyColors` (màu đánh giá, trạng thái từ `colorScheme`) | màu chọn tại chỗ |
| `N3Spacing`, `N3Motion` (token) | số dp/ms viết tay |

## 5. Animation

Hiện có: `animateContentSize` khi mở rộng Kanji/Ngữ pháp trong Bài học; cuộn trang Onboarding;
chuyển màn hình mặc định của Navigation Compose (**fade 700 ms**, chậm so với nhịp học).

Cần bổ sung (ngắn, có mục đích, tôn trọng "Xóa hiệu ứng động"):
- Chuyển màn hình: fade + trượt nhẹ ~220 ms (thay 700 ms).
- Flashcard: lật/hiện mặt sau, chuyển sang thẻ kế, nút "Hiện đáp án" → 4 nút đánh giá, tiến độ chạy mượt.
- Thanh tiến độ (Trang chủ, Bài học, Thống kê) chạy tới giá trị mới thay vì nhảy.
- Mở rộng/thu gọn trong Bài học: mũi tên xoay.
Không thêm: hiệu ứng lặp vô hạn, parallax, confetti, rung lắc.

Compose tự nhân thời lượng animation theo "Animator duration scale" của hệ thống (0 = tắt); thêm
`LocalReduceMotion` để bỏ cả chuyển động không gian (trượt, lật) khi người dùng tắt hiệu ứng.

## 6. Thay đổi chỉ ở UI (không ảnh hưởng nghiệp vụ)

Bảng màu, typography, shape, spacing, kiểu thẻ, bố cục từng màn hình, giới hạn bề rộng nội dung
trên màn hình lớn, animation, gom component trùng. Tất cả chuỗi hiển thị, callback, ViewModel,
testTag giữ nguyên để test end-to-end vẫn đúng.

## 7. Kế hoạch theo giai đoạn

| Giai đoạn | Nội dung | Kiểm tra |
|---|---|---|
| 1. Design System | Bảng màu rêu/giấy/mực đủ vai trò M3 (sáng+tối), typography editorial (serif cho display/headline), shapes, spacing, motion + giảm chuyển động, `N3Card`/`SectionCard`/`Tag`/`Caption`/`N3ProgressBar`/`GlyphTile`/`StudyColors`; áp dụng vào khung app (top bar, tab bar, chuyển màn hình, bề rộng tối đa) và thay các bản sao | build, unit (gồm test tương phản mở rộng), lint, test thiết bị |
| 2. Flashcard | Bố cục thẻ, animation lật/chuyển thẻ/nút, tiến độ gọn | + test thiết bị flashcard |
| 3. Trang chủ | Khối hành động chính, nhóm số liệu, ghi chú gọn | |
| 4. Học → Bài học | Thẻ bài, tiến độ 3 miền, chi tiết bài | |
| 5. Thống kê | Phân cấp thẻ, biểu đồ, chú giải | |
| 6. Cài đặt | Nhóm thẻ, dòng gói nội dung | |

Mỗi giai đoạn: `assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` trên emulator.
