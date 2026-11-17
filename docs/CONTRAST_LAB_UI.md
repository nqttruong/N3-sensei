# Contrast Lab: giao diện học (M5)

Trạng thái: đã triển khai (2026-11-17), gồm 3 màn hình đọc dữ liệu thật từ Room (M3) qua các use case (M1). Kết quả test ở mục 9.

## 1. Màn hình và điều hướng

```
Tab Học (LearnHub) ── "Contrast Lab — Mới" (mục cuối danh sách, ký hiệu 比)
  └─ ContrastHomeRoute      Contrast Lab       ContrastHomeScreen / ContrastHomeViewModel
       └─ ContrastLibraryRoute   Thư viện Contrast   ContrastLibraryScreen / ContrastLibraryViewModel
            └─ ContrastGroupRoute(groupId)   So sánh   ContrastGroupScreen / ContrastLearningViewModel
                 └─ (Xem mục gốc) GrammarDetailRoute / VocabularyDetailRoute / KanjiDetailRoute  (màn hình sẵn có)
```

- Cả 3 route đều thuộc `TopLevelDestination.LEARN` (`isTabRoot = false`, không phải phiên tập trung). Thanh dưới vẫn **5 tab**.
- Code điều hướng nằm ở `Routes.kt`, `AppScreen.kt`, `N3SenseiNavHost.kt` và `LearnHubScreen.kt` (thêm tham số `onOpenContrast`, mặc định `{}`).
- Back: So sánh → Thư viện (giữ truy vấn và bộ lọc) → Contrast Home → Tab Học. Mỗi lần mở là một entry; không có `popUpTo` đặc biệt.
- Từ Home cũng mở được So sánh trực tiếp (Tiếp tục học, Đến hạn ôn, Hay nhầm).

### Lối vào từ bài học và trang kiến thức (C6)

- **Vị trí:**
  - Học → Bài học → chi tiết bài: "Phân biệt kiến thức dễ nhầm".
  - Trang Ngữ pháp, Từ vựng, Kanji: "Xem các kiến thức dễ nhầm".
- **Dữ liệu:** `ContrastLinksViewModel` đọc `ContrastContentRepository.observeGroupsForSources`, tức là các nhóm đã phát hành có mục trỏ tới id nguồn của bài hoặc trang. Đây chỉ là truy vấn, không đổi schema.
- **Hiển thị:** một hàng chip cuộn ngang (`ContrastLinkTags`). Bấm vào mở màn So sánh.
- **Hành vi:** không ghi gì. Khi không có gói được phát hành thì không hiện. Bài học vẫn theo CoursePlan, không đổi.

## 2. ViewModel và UI State

Kiến trúc như phần còn lại của app: Screen → ViewModel → use case / repository → Room. Composable không truy cập DAO; ViewModel không giữ NavController. Mọi luồng được thu bằng `collectAsStateWithLifecycle`.

| ViewModel | Nguồn | State |
|---|---|---|
| `ContrastHomeViewModel` | `observeGroups()` → `GetContrastProgress.ofGroup` (mastery), `ContrastAnalyticsRepository.lastAnsweredGroup` (nhóm đang phát hành trả lời gần nhất), `ContrastAnalyticsRepository.directions` (hay nhầm, truy vấn tổng hợp từ M8); tự cập nhật khi có câu trả lời mới (`observeChanges`) | `UiState<ContrastHomeUiState>`: Loading / Empty (chưa có gói) / Success / Error (+ `retry()`) |
| `ContrastLibraryViewModel` | `observeGroups(ContrastFilter(query, type))` + mastery của từng nhóm | `UiState<ContrastLibraryUiState>`: Empty = chưa có gói; Success có `rows` (đã lọc) và `totalGroups` (phân biệt "không có nội dung" với "không khớp") |
| `ContrastLearningViewModel` | `GetContrastGroupDetail` (nội dung + resolver Room) và `GetContrastProgress` | `UiState<ContrastLearningContent>`: `Group(detail, mastery, isDue)`, `Retired(retired)`, NotFound, Error |

Truy vấn và hai bộ lọc của Thư viện nằm trong `SavedStateHandle`, nên vẫn còn khi quay lại từ So sánh, kể cả sau khi tiến trình app bị hủy. Repository Contrast được nối vào `AppContainer` (4 thuộc tính `lazy`, không đổi gì khác).

## 3. Trạng thái học hiển thị

Mức thành thạo là của domain (`ContrastMastery`: NEW, LEARNING, REVIEWING, MASTERED). Nó được tính từ câu trả lời, nên đọc lý thuyết không làm đổi gì. Bộ lọc trạng thái là quy tắc trên mức thành thạo đó (`ContrastStatusMapper`, có unit test):

| Bộ lọc | Quy tắc |
|---|---|
| Chưa học | NEW |
| Đang học | LEARNING hoặc REVIEWING |
| Cần củng cố | đã có lịch và đang đến hạn (mọi mức trừ NEW) |
| Đã nắm vững | MASTERED |

Một nhóm có thể khớp hai bộ lọc (ví dụ đang học và đến hạn). Trên thẻ nhóm hiện: loại, cấp JLPT, nhãn mức thành thạo (REVIEWING hiện là "Đang ôn dài hạn"), và tag "Đến hạn ôn" nếu có.

**Đọc lý thuyết không ghi gì:** cả 3 ViewModel chỉ đọc. Unit test kiểm số dòng attempt / progress / review / session trước và sau. Test end-to-end kiểm 4 bảng người học vẫn là 0 dòng sau khi đi hết luồng.

## 4. Màn hình So sánh

- **Tổng quan:** tiêu đề, loại / cấp, trạng thái học (kèm ghi chú "đọc lý thuyết không được tính"), `summaryVi`, các đoạn lý thuyết.
- **Các mục so sánh:** mỗi mục một thẻ xếp dọc, số mục bất kỳ (≥ 2); test có nhóm 3 mục.
  - Ngữ pháp: cấu trúc (cách kết hợp), ý nghĩa, sắc thái.
  - Từ vựng: cách đọc, ý nghĩa, cách dùng, kết hợp thường gặp (có furigana).
  - Kanji: Hán Việt, ý nghĩa, âm On / Kun, bộ thủ, điểm phân biệt hình dạng.
  - Trường rỗng không hiện. Số nét không có trong dữ liệu Contrast nên không hiện; không có giá trị giả.
- **So sánh chi tiết:** mỗi khía cạnh một thẻ, giá trị của từng mục xếp dọc (không cần cuộn ngang).
- Tiếp theo là **ví dụ** (`FuriganaText`, bản dịch, mục minh họa), **mẹo nhớ** và **lỗi thường gặp** (câu sai / đúng có furigana, giải thích, "Nhầm A thành B"), cuối cùng là ghi chú bản quyền chưa xác minh nếu có.
- **Nút luyện tập** (M6): "Luyện tập nhóm này", chỉ hiện khi nhóm có câu hỏi.

## 5. Xác minh nguồn khi hiển thị

Dùng `RoomContrastSourceResolver` của M4, tính lại mỗi lần mở nhóm:

| Trạng thái (code) | Hiển thị |
|---|---|
| OK | bình thường; nút "Xem mục gốc" mở màn hình chi tiết sẵn có |
| SOURCE_CHANGED | banner "nguồn đã thay đổi…, phần so sánh vẫn là bản đã được kiểm duyệt"; vẫn có "Xem mục gốc" |
| SOURCE_MISSING | banner "Mục gốc không còn… Bản chụp lúc soạn: …" (snapshot); không có liên kết |
| AMBIGUOUS | banner "Không xác định được mục gốc (nhiều mục khớp)… Bản chụp: …"; không tự chọn |
| UNVERIFIABLE | không hiện gì |

Nội dung Contrast đã duyệt luôn hiện. Không trạng thái nào xóa nhóm hay tiến độ.

## 6. Empty, error, nhóm đã ngừng

- Chưa có gói được phát hành: Home và Thư viện hiện "Chưa có nhóm kiến thức Contrast Lab. Nội dung sẽ xuất hiện khi có gói học đã được kiểm duyệt." Hiện app không mang gói nào, nên đây là trạng thái mà người dùng thật đang thấy.
- Lỗi đọc dữ liệu: `ErrorContent` của app, có nút "Thử lại".
- Nhóm đã ngừng (`retiredGroups`): "Nhóm này đã ngừng sử dụng: <lý do>. Lịch sử học của bạn vẫn được giữ."
- Nhóm không tồn tại: thông báo "không tìm thấy" chung.

## 7. Khả năng tiếp cận và giao diện

- Dùng lại Material 3, `N3Card`, `Tag`, `SectionHeader`, `InfoBanner`, `FilterChipRow`, `SearchField`, `FuriganaText`, `UiStateContent` và `N3Spacing`.
- Tiêu đề thẻ có `heading()`. Ô số liệu ở Home được gộp thành một mô tả cho TalkBack ("Nhóm có thể học: 3"). Tiếng Nhật đọc bằng giọng Nhật qua `FuriganaText`.
- Mọi màn hình cuộn được. Test chạy ở chế độ tối kết hợp cỡ chữ 2x, và mọi phần vẫn cuộn tới và hiển thị được.

## 8. Tìm kiếm

- Tìm trên `contrast_groups.search_text` (M3), được chuẩn hóa bằng `SearchText` của app: không phân biệt dấu tiếng Việt, hoa thường, hay katakana / hiragana.
- Phạm vi gồm: tiêu đề, tóm tắt, nhãn các mục, cách đọc (từ vựng, âm On / Kun), nghĩa và Hán Việt.
- Test end-to-end tìm bằng tiếng Nhật ("冷める") và tiếng Việt không dấu ("thoi quen").
- Contract không có trường "từ khóa biên tập", nên không có loại tìm kiếm này.
- Logic tìm kiếm của các chức năng khác không đổi.

## 9. Test

| Nhóm | Test | Nơi chạy |
|---|---|---|
| ViewModel + bộ ánh xạ trạng thái | `ContrastViewModelsTest` (10 test): Empty, Success, Error, lọc, tìm, lưu trạng thái, trạng thái sau luyện tập, nguồn OK / CHANGED / MISSING / AMBIGUOUS, NotFound, Retired, 3 mục, không ghi gì | JVM |
| Điều hướng | `ContrastNavigationTest` (5 tab, mọi màn Contrast thuộc LEARN); `AppScreenTest` (danh mục màn hình có thêm 3 route) | JVM |
| Màn hình | `ContrastScreensTest` (7 test): Home, Thư viện, So sánh, banner nguồn, 3 mục, Kanji, Retired, NotFound, Empty, Error, chế độ tối + cỡ chữ 2x | thiết bị |
| End-to-end | `ContrastLabEndToEndTest`: Room thật, nguồn đóng sẵn thật và **bản sao test** của gói mẫu; Học → Home → Thư viện → lọc / tìm → So sánh → Back giữ tìm kiếm → Học; khởi động lại vẫn đọc được; 0 dòng người học; trạng thái rỗng khi không có gói | thiết bị |
| Hồi quy điều hướng | `N3SenseiNavigationTest` (11 test có sẵn, không sửa) | thiết bị |

## 10. Giới hạn của M5, phần dành cho M6 và M7

- (Cập nhật M6) Luyện tập đã có: Home có "Bắt đầu luyện tập" / "Tiếp tục phiên dang dở", màn So sánh có "Luyện tập nhóm này". Xem [CONTRAST_LAB_PRACTICE.md](CONTRAST_LAB_PRACTICE.md).
- "Tiếp tục học" chỉ xuất hiện sau khi có câu trả lời, tức là sau M6.
- (Cập nhật M7) Home có thêm "Ôn tập", "Phân tích lỗi", "Tiến độ". Xem [CONTRAST_LAB_ANALYTICS_SRS.md](CONTRAST_LAB_ANALYTICS_SRS.md).
- Home tính mức thành thạo cho từng nhóm và đọc toàn bộ attempt. Với vài trăm nhóm thì đủ nhanh; nếu kho nội dung lớn hơn nhiều, nên thêm truy vấn tổng hợp trong SQL (M7).
- Lỗi cài gói Contrast vẫn chỉ ghi log (M4); chưa có giao diện báo lỗi.
