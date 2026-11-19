# Contrast Lab — Báo cáo thiết kế lại giao diện (U1–U5)

Ngày: 2026-11-18. Phạm vi chỉ gồm UI/UX, typography, màu sắc, khả năng tiếp cận và chuyển động. Không đổi domain, Room v16, SRS, cách chấm điểm, content pack hay backup.

Mọi ảnh được chụp thật trên emulator Pixel 8 (API 36), với dữ liệu xem trước của bản debug và lịch sử học tạo riêng cho test. Ảnh trước/sau nằm ở `docs/contrast/ui-redesign/` (bên trái là trước, bên phải là sau). Để chụp lại:

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb -s emulator-5554 shell am instrument -w -e class com.n3sensei.app.feature.contrast.ContrastScreenshotTour \
    -e screenshots after com.n3sensei.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 pull /sdcard/Android/data/com.n3sensei.app/files/screenshots/ .
```

`ContrastScreenshotTour` đi qua đủ 9 màn Contrast, cả bốn dạng câu hỏi và dark mode. Test này chỉ chạy khi truyền `-e screenshots <tiền tố>`; trong suite thường nó được báo là bỏ qua.

## Chẩn đoán giao diện trước khi sửa (ảnh "TRƯỚC")

| Vấn đề | Ở đâu |
|---|---|
| Phương án Kanji (鳥 / 島 / 烏) cỡ chữ thân bài, gần như không phân biệt được hình chữ | Luyện tập |
| Chữ Nhật trong ví dụ, câu hỏi, đoạn tìm lỗi chỉ ~16 sp, nhỏ ngang phần giải thích | So sánh, Luyện tập |
| Bốn ô số liệu xếp dọc chiếm nửa màn hình; nút hành động nhỏ, không rõ đâu là hành động chính | Home, Tổng kết |
| Ba loại nhóm (ngữ pháp, từ vựng, Kanji) trông y hệt nhau; mọi thứ cùng một màu xanh rêu | Thư viện, Home |
| Màn So sánh là một cột văn bản dài, các mục A/B cùng màu, không có điểm nhấn ở ví dụ | So sánh |
| Băng "nội dung thử nghiệm" hai dòng chiếm nhiều chỗ ở mọi màn | Mọi màn |
| Chỉ có hiệu ứng mờ/trồi chung, không có chuyển cảnh riêng cho luồng học | Điều hướng |

## U1 — Typography: COMPLETE

- **File:** `feature/contrast/ContrastDesign.kt` (`ContrastText`).
- **Thang chữ:** đơn vị sp, nên theo font scale của hệ thống. Các màn chỉ dùng token, không đặt cỡ tùy tiện.

| Token | Cỡ / line height | Dùng cho |
|---|---|---|
| `screenTitle` | 28 / 36 sp, Bold | Hero |
| `groupTitle` | 24 / 32 sp | Tiêu đề nhóm |
| `heroKanji` | 52 / 64 sp | Kanji được so sánh |
| `heroPattern` | 28 / 40 sp | Mẫu ngữ pháp, từ vựng |
| `itemLabel` | 22 / 32 sp (Kanji 30 sp) | "A / B" trong danh sách |
| `exampleJp` | 22 / 36 sp | Câu ví dụ, câu hỏi (dư chỗ cho furigana) |
| `body` | 17 / 26 sp | Giải thích tiếng Việt |
| `bodySecondary` | 16 / 24 sp | Bản dịch, ghi chú |
| `answer` | 20 / 30 sp | Đáp án |
| `answerKanji` | 40 / 48 sp | Đáp án là chữ Kanji ngắn |
| `question` | 20 / 28 sp | Đề bài |
| `label`, `caption` | 14 / 20 sp | Nhãn, chú thích |
| `figure` | 30 / 36 sp | Con số dashboard |

- **Chữ Nhật:** luôn lớn hơn phần giải thích bên cạnh, và được đánh dấu locale `ja-JP` để hiển thị đúng tự dạng Nhật và để TalkBack đọc bằng giọng Nhật.
- **Kiểm tra font scaling:**
  - Tour chụp ảnh chạy lại ở `font_scale 1.3` và trên màn hình 720×1440 @ 320 dpi: không cắt Kanji hay furigana, không tràn ngang.
  - Các test Compose sẵn có ở fontScale 2.0 trong dark mode đều đạt.
  - Nhãn bị cắt ở thanh tab dưới cùng là thanh điều hướng chung của app, ngoài phạm vi.

## U2 — Màu sắc và design system: COMPLETE

- **File:** `feature/contrast/ContrastDesign.kt` (`ContrastTones`, `Tone`, các component).
- **Bảng màu:** mỗi loại có accent / container / chữ trên container / gradient, cho cả sáng và tối.

| Vai trò | Sáng | Tối |
|---|---|---|
| Ngữ pháp | Indigo `#4338CA`, gradient indigo → violet | `#A5B4FC` |
| Từ vựng | Teal `#0F766E`, gradient teal → emerald | `#5EEAD4` |
| Kanji | Amber `#B45309`, gradient amber → cam | `#FCD34D` |
| Luyện tập | Blue `#1D4ED8` → violet | `#93C5FD` |
| Đúng | Green `#15803D` | `#86EFAC` |
| Sai | Rose `#BE123C` | `#FDA4AF` |
| Ôn tập | Cyan `#0E7490` → indigo | `#67E8F9` |
| Các mục trong một nhóm (A, B, C, D) | Indigo, fuchsia, sky, cam | Bản sáng tương ứng |

- **Độ tương phản:** đã tính tỉ lệ WCAG cho mọi cặp, kể cả chữ trắng trên gradient sáng và tối. Tất cả ≥ 4,5:1 (AA cho chữ thường).
- **Component:**
  - `ContrastCard`: góc 20 dp, viền mảnh, tint theo vai trò, padding 18 dp. Khi bấm co 0,98; cả thẻ là một nút cho TalkBack.
  - `GradientHeader`: hero gradient nhẹ, có họa tiết sóng seigaiha vẽ bằng Canvas, không tải ảnh.
  - `GradientButton`: hành động chính, cao tối thiểu 56 dp.
  - `CategoryBadge`: 文 / 語 / 漢 kèm tên loại.
  - Các phần nhỏ: `TonePill`, `SectionTitle`, `FigureTile`.
  - Biểu đồ: `ProgressRing` và `ToneBar` (Canvas, có animation).
  - `ExpandableCard`: mở bằng `animateContentSize`, giữ trạng thái qua xoay màn hình.
- **Nguyên tắc dùng màu:** mỗi màn có một màu chủ đạo và tối đa hai màu phụ. Gradient chỉ dùng ở hero, nút chính và màn Tổng kết; không đặt sau đoạn văn dài.

## U3 — Thiết kế lại các màn hình: COMPLETE

| Màn | Thay đổi chính | Ảnh |
|---|---|---|
| Home | Hero "Contrast Lab"; 4 số liệu thành lưới 2×2 gọn; nút "Bắt đầu luyện tập" gradient; thẻ "Tiếp tục học"; **3 thẻ loại** (mở Thư viện đã lọc sẵn qua `ContrastLibraryRoute(type)`); thẻ Ôn tập với vòng số nhóm đến hạn; thẻ Tiến độ (vòng nắm vững) và Phân tích lỗi | `home.jpg` |
| Thư viện | Chip lọc mang màu loại/trạng thái, đổi màu có animation; đếm "N nhóm"; thẻ nhóm có dải màu loại, badge, các mục **mỗi mục một màu** ("A / B"), tóm tắt 2 dòng; danh sách có `animateItem` khi lọc | `library.jpg` |
| So sánh | Hero theo màu loại; nút luyện tập ngay dưới hero và ở cuối; Tổng quan, Lý thuyết đánh số, Các mục (thẻ A/B/C màu riêng, Kanji 52 sp), Điểm khác biệt (từng khía cạnh, chấm màu theo mục), Ví dụ (dải màu theo mục, **tô cấu trúc trọng tâm** bằng màu của mục, không đổi văn bản), Mẹo nhớ; "Lỗi thường gặp" và "Giải thích sâu hơn" thu gọn được (nội dung cốt lõi không bị thu gọn) | `compare-grammar.jpg`, `compare-vocabulary.jpg`, `compare-kanji.jpg` |
| Luyện tập | Thanh tiến độ màu có animation; nhãn dạng bài; đề 20 sp; câu Nhật 22 sp trong thẻ; đáp án là thẻ lớn (≥ 64 dp) có chữ A/B/C; **Kanji 40 sp**; chọn thì viền + tint + scale 1,02; tìm lỗi là các đoạn lớn (≥ 56 dp); thanh hành động nổi, có `imePadding` | `practice-*.jpg` |
| Feedback | Thẻ màu theo kết quả, có icon tròn và tiêu đề 22 sp (Đúng: xanh và ✓; Sai: rose và ✕; Bỏ qua: trung tính và ⓘ); mỗi đáp án ghi rõ "Đáp án đúng" / "Bạn chọn" bằng chữ và icon, không chỉ bằng màu; tự cuộn tới phần giải thích | `feedback-incorrect.jpg` |
| Tổng kết | Hero theo kết quả (≥ 80 % "Làm rất tốt!", ≥ 50 % "Tiến bộ rồi đấy", còn lại "Ôn thêm một chút nhé"); vòng độ chính xác; 3 ô đúng/sai/bỏ qua; lịch ôn tiếp; danh sách câu đã làm có màu theo kết quả; không có điểm thưởng, XP hay streak | `summary.jpg` |
| Phân tích lỗi | Hero số câu sai; hướng nhầm A → B dạng pill xanh → rose kèm thanh tần suất; nhóm hay sai có thanh tần suất; bộ lọc màu | `errors.jpg` |
| Ôn tập | Hero cyan; nút "Ôn tập ngay" gradient; ba mục có màu và glyph riêng: Đến hạn (今, cyan), Cần củng cố (強, amber), Sắp tới (次); thanh bậc lịch ôn | `review.jpg` |
| Tiến độ | Vòng nắm vững kèm chú giải đang học / đến hạn / chưa học; ô số liệu; thanh tiến độ theo loại; danh sách nhóm có badge loại và trạng thái | `progress.jpg` |
| Dark mode | Mọi màu có bản tối; card tách rõ khỏi nền | `dark-compare.jpg` |

- **Băng "NỘI DUNG THỬ NGHIỆM"** (chỉ có ở bản debug) nay chỉ còn một dòng. Phần giải thích chỉ hiện ở Home.
- **Dữ liệu trên màn hình:** mọi số liệu và biểu đồ dùng dữ liệu thật đã có trong UI state. Có một trường mới là `ContrastHomeUiState.byType` (số nhóm theo loại), tính từ danh sách nhóm sẵn có.

## U4 — Chuyển động: COMPLETE

| Hiệu ứng | Cách làm | Khi hệ thống giảm chuyển động |
|---|---|---|
| Chuyển màn trong Contrast (Home → Thư viện → So sánh → Luyện tập → Tổng kết, Home → Ôn tập / Tiến độ / Lỗi) | Mờ dần kết hợp trượt ngang, 260 ms vào / 180 ms ra; quay lại thì đảo chiều | Chỉ mờ dần |
| Chuyển câu hỏi | `AnimatedContent` theo id câu: câu mới trượt vào, câu cũ rời đi mà không mang theo lựa chọn hay đáp án | Đổi ngay |
| Chọn đáp án / đoạn | Màu nền chuyển (`animateColorAsState`), scale 1,02–1,04 (spring) | Không scale |
| Feedback | `AnimatedVisibility` (mờ + mở theo chiều dọc), rồi tự cuộn tới | Hiện ngay |
| Mở / thu gọn | `animateContentSize` và mũi tên xoay | Đổi ngay |
| Bấm thẻ / nút | Co 0,97–0,98 | Không co |
| Vòng / thanh tiến độ | Tăng dần 600–700 ms | Đổi ngay |
| Hoàn thành phiên | Confetti nhẹ, 40 mảnh, chạy một lần trong 1,4 s, chỉ khi độ chính xác ≥ 80 %; không chặn chạm, không che kết quả | Không có |

- Không dùng thư viện animation mới, cũng không có animation lặp vô hạn.
- Đúng/sai luôn có chữ và icon, nên không phụ thuộc vào animation. "Giảm chuyển động" đọc từ `LocalReduceMotion` có sẵn (thang animator = 0).

## U5 — QA và hồi quy

**Trạng thái: COMPLETE.** Riêng TalkBack thử bằng tay và landscape chưa làm (xem mục cuối).

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:testDebugUnitTest` (gồm `ArchitectureRulesTest`, test điều hướng, ViewModel) | 597/597 đạt |
| Test thiết bị gói `feature.contrast` (Compose UI cho 9 màn, E2E trên database thật) | 29 đạt, 1 bỏ qua (tour chụp ảnh) |
| Toàn bộ test thiết bị trên emulator-5554 | **467 test: 463 đạt, 1 thất bại, 3 bỏ qua** |
| `assembleDebug`, `assembleRelease` | Đạt; APK release không chứa gói xem trước |
| `lintDebug` | 0 lỗi, 29 cảnh báo đều có từ trước, không cảnh báo nào trong mã Contrast |

- **Thất bại duy nhất:** `HourPickerReachabilityTest`, có từ trước Contrast, ngoài phạm vi.
- **Bỏ qua:**
  - 2 test ML Kit (phải bật riêng).
  - `ContrastScreenshotTour`: chỉ chạy khi truyền tham số; đã chạy riêng 4 lần (before, after, font 1.3×, màn nhỏ), mỗi lần 28 ảnh.
- **Lỗi tìm ra và đã sửa trong lúc QA:**
  1. `ContrastCard` đặt hành động bấm ở `Column` bên trong, nên node mang test tag không có OnClick. Đã chuyển hành động bấm lên chính `Surface`, để TalkBack thấy cả thẻ là một nút.
  2. Hai test Compose bấm hoặc kiểm tra một phần tử đã nằm ngoài màn hình vì bố cục mới dài hơn. Đã thêm bước cuộn tới phần tử; mọi điều kiện kiểm tra giữ nguyên.
  3. Lint báo chuỗi "Đến hạn ôn" không còn được dùng. Đã đưa tiêu đề này trở lại Home.
  4. Ở bản đầu, băng nội dung thử nghiệm quá cao, nút Thoát còn màu cũ, phần giải thích sau khi chấm nằm dưới nếp gấp, bộ lọc Phân tích lỗi dùng kiểu chip cũ. Cả bốn đã sửa sau khi xem ảnh.
- **Hồi quy:**
  - Năm tab chính giữ nguyên.
  - Room vẫn v16 (`schemas/16.json`), không có migration.
  - Không đổi SRS cũ, cách chấm điểm, Attempt/Progress hay content pack.
  - Thay đổi điều hướng duy nhất là `ContrastLibraryRoute` nhận thêm tham số tùy chọn `type`.
- **Kiểm tra bằng mắt** trên ảnh thật (light, dark, font 1.3×, màn nhỏ):
  - Chữ Nhật và furigana không bị cắt, không tràn ngang.
  - Hành động chính luôn là nút gradient lớn, dễ thấy.
  - Mỗi màn chỉ có một màu chủ đạo.


## Những gì chưa làm hoặc còn hạn chế

- **Chưa thử TalkBack bằng tay.** Semantics đã được kiểm qua test Compose: heading, radio, stateDescription, cả thẻ là một nút.
- **Chưa kiểm tra landscape riêng.** App không khóa hướng màn hình, nên bố cục dọc sẽ kéo giãn theo chiều ngang.
- **Màn hình nhỏ** (720×1440 @ 320 dpi) và font 1.3× đã thử bằng tour chụp ảnh. Ở fontScale 2.0 chỉ có test Compose, không có ảnh.
- **Màu ngoài Contrast:** thanh tiêu đề và thanh tab dưới cùng thuộc theme chung của app (xanh rêu, serif), được giữ nguyên để không ảnh hưởng các chức năng khác.
