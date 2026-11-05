# Kiểm thử tích hợp và rà soát hồi quy (2026-10-28)

Phạm vi: mọi thay đổi chưa commit kể từ `f79338e`:
- Design System và Flashcard;
- Cài đặt / "Từ của tôi";
- Flashcard Kanji và ví dụ;
- nhóm từ vựng;
- vị trí học;
- thông báo nhắc học;
- widget;
- Trang chủ.

Toàn bộ test chạy trên emulator `Pixel_8` (Android 16). Điện thoại thật không được dùng.

## 1. Danh sách xác minh

| Mục | Kết quả | Bằng chứng |
|---|---|---|
| Cài đặt không còn khu vực "Nguồn nội dung" | Đạt | `EndToEndTest.settings_themeAndFuriganaArePersisted` khẳng định không còn dòng nguồn; gói "Từ của tôi" không còn nằm trong danh sách gói (`SettingsViewModelTest`). Nguồn và giấy phép vẫn hiện trên trang chi tiết từng mục. |
| "Từ của tôi" chỉ chứa từ người dùng thêm | Đạt | Tiêu chí: thuộc bài `user-words-l1` **và** ID do thao tác thêm tạo (`user-w-…`) **và** có dòng `user_words`. Test: `UserContentRepositoryTest.myWordsList_showsOnlyWordsTheLearnerAdded`, `VocabularyListViewModelTest`. Nâng cấp thật v6→v8: chỉ còn đúng từ đã thêm. |
| Flashcard Kanji: mặt trước đủ từ đi kèm, không lộ furigana/nghĩa | Đạt | `FlashcardKanjiWordsTest` (0/1/2/30/42 từ, mặt trước không có nghĩa, cách đọc hay Hán Việt). |
| Chữ flashcard đủ lớn, không tràn | Đạt (emulator) | Kanji 104sp, từ đi kèm 24sp. Vùng thẻ cuộn được, nút luôn cố định; test 42 từ cuộn tới từ cuối. Chưa thử trên máy màn hình nhỏ thật. |
| Sửa Kanji: thêm/sửa/xóa ví dụ | Đạt | `EndToEndTest.kanjiExamples_…` (thêm → Lưu → mở lại → xóa → Hủy → xóa → Lưu), `UserContentRepositoryTest.kanjiExamples_…`, test validator. Ví dụ của gói chỉ xem, không sửa được. |
| Nhóm từ vựng ≤ 30 từ, đúng thứ tự, `1.1`, `1.2` | Đạt | `VocabularyGroupsTest` (0/1/30/31/60/61/125 từ), `BundledCourseTest` (Mimikara 4,4,3,4,4,2,2,2,3,3,2,2 nhóm), `EndToEndTest.vocabularyGroups_…` (mở, luyện tập, tiến độ còn sau khi mở lại app, không nhân bản: 910 từ). |
| Thẻ hiển thị đúng bài/mục | Đạt | `ItemLocationsTest` (một từ có ở nhiều nơi, Từ của tôi, chưa thuộc bài), `BundledCourseTest` (mọi mục của gói có vị trí), `EndToEndTest.cardsAndDetails_…`. |
| Thông báo không lên lịch trùng; tần suất và khung giờ chỉnh được | Đạt | `ReminderInstrumentedTest.scheduler_neverKeepsMoreThanOneSchedule` (mở app 3 lần, đổi 15/30/60/120 phút, tắt/bật, khôi phục: luôn đúng 1 lịch). `ReminderRunnerTest`: không còn luân phiên A/B, nghỉ 24 giờ, giới hạn mỗi ngày. |
| Widget 1×1 và 2×2 thêm được thật lên màn hình chính | Đạt (emulator) | Đã ghim cả hai qua hộp thoại của Pixel Launcher; `dumpsys appwidget` liệt kê 2 provider; `WidgetProvidersTest`. |
| Widget hiển thị nội dung người dùng chọn | Đạt (emulator) | Kiểm tay: chế độ "Một bài học → Bài 03" hiện 戻 với nhãn "Bài 03 · Kanji"; "Đổi" đổi mục; nhấn mở đúng thẻ; sáng/tối. `WidgetContentPickerTest`. |
| Trang chủ chỉ còn châm ngôn, Mục tiêu hôm nay, Cần ôn | Đạt | `DashboardEndToEndTest.realNumbers_matchHandCount` (Trang chủ không còn dự báo), `N3SenseiNavigationTest.homeShortcuts_openTheirTabs` (thẻ Cần ôn mở tab Ôn tập). |
| Các chỉ số còn lại chuyển sang Thống kê | Đạt | Dự báo, xu hướng (tuần/tháng), khối lượng 7 ngày, ước tính tiến độ, phiên ôn: các con số đã kiểm bằng tay nay được kiểm trong tab Thống kê. |

## 2. Dữ liệu

- **Bản cài mới:** 910 từ vựng (30 + 880), 420 Kanji (27 + 393), 181 ngữ pháp. Không có bản ghi nhân bản; nhóm từ vựng, Bài học và vị trí đều tính khi hiển thị, không lưu thêm gì.
- **Nâng cấp thật trên emulator:**
  1. Cài APK của `f79338e` (schema 6).
  2. Tạo dữ liệu: ôn 4 thẻ, đánh dấu 1 Kanji, sửa nghĩa 1 Kanji, thêm 1 "Từ của tôi" theo đúng cấu trúc v6.
  3. Cài đè bản mới (schema 8).

  Trước và sau đều có: 911 từ vựng, 420 Kanji, 181 ngữ pháp, 4 tiến độ, 4 lượt ôn, 1 đánh dấu, 1 chỉnh sửa, 1 từ của tôi. So từng dòng, tiến độ, lượt ôn, từ vựng và Kanji khớp hoàn toàn; `integrity_check` = ok.
- **Migration:** schema 6 → 7 (`user_kanji_examples`) và 7 → 8 (`reminder_history`), mỗi bước có test dữ liệu riêng. `N3DatabaseMigrationTest` kiểm mọi phiên bản 1…8 lên bản hiện tại.

## 3. Kết quả build và test (lần chạy cuối)

`./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`, `ANDROID_SERIAL=emulator-5554`:

- **Build debug:** thành công.
- **Unit test:** 295/295.
- **Test thiết bị:** 214/214, không bỏ qua test nào.
- **Lint:** 0 lỗi, 25 cảnh báo (bằng số trước đợt này).

Lỗi đã gặp và đã sửa trong đợt này:
- Nút "Đổi" của widget không đổi mục, vì Glance không gọi lại `provideGlance` khi phiên còn chạy.
- Widget hiện chữ Hán theo kiểu Trung Quốc (thêm `LocaleSpan` ja-JP).
- `updateUserWord` có thể biến một từ không phải của người học thành "Từ của tôi".
- `ON CONFLICT … DO UPDATE` không chạy được trên Android 8–10.
- Hai biểu đồ ở Thống kê có cùng một câu thông báo rỗng.

Test chập chờn đã gặp và đạt khi chạy lại riêng:
- `reviewSettings_validatesAndSaves`;
- `SystemBarAppearanceTest` (một lần emulator bị dự án khác dùng chung).

## 4. Vấn đề còn tồn tại

- **Sao lưu chưa đủ:** chưa gồm chỉnh sửa nội dung, "Từ của tôi", liên kết Kanji, ví dụ Kanji tự thêm và lịch sử nhắc học (việc "sao lưu v2" còn treo từ trước). Cài đặt nhắc học và widget mới thì đã có trong bản sao lưu.
- **Thời điểm nhắc học:** phụ thuộc WorkManager, không đúng giờ tuyệt đối (Doze, tiết kiệm pin, giới hạn của hãng máy). Chưa thử trên điện thoại thật.
- **Widget:**
  - Chỉ thử trên Pixel Launcher; launcher của các hãng khác có thể cấp kích thước ô khác.
  - Widget làm mới khi đổi cài đặt, khi bấm "Đổi", khi rời ứng dụng và sau mỗi lần nhắc học. Không có cập nhật định kỳ riêng.
- **Câu nói hôm nay:** bản dịch và giải thích tiếng Việt do ứng dụng biên soạn, chưa có người bản ngữ duyệt.
- **Chưa kiểm thử:** giảm chuyển động ("Xóa hiệu ứng động") chưa thử trên thiết bị; giao diện cũng chưa thử với cỡ chữ hệ thống lớn trên máy thật.
- **Hai cách đánh số trong nhãn vị trí:** từ vựng dùng số nhóm của Học → Từ vựng ("Bài 1.2"), Kanji và ngữ pháp dùng số của Học → Bài học ("Bài 04"). Trang chi tiết có ghi rõ cả hai.
- **Có từ trước đợt này:** thanh "Lượt ôn: 0 / 0" trên Mục tiêu hôm nay hiện đầy.

## 5. Kiểm thử thủ công trên điện thoại

Trước khi cài, nên xuất sao lưu trong Cài đặt. Cài đè bằng `adb install -r` để giữ dữ liệu; không gỡ app.

1. **Dữ liệu sau khi cập nhật:** Thống kê vẫn hiện tiến độ cũ; Học → Từ của tôi chỉ có từ bạn tự thêm; các chỉnh sửa Kanji/ngữ pháp vẫn còn.
2. **Cài đặt:** không còn khu vực "Nguồn nội dung"; có mục "Thông báo học tập" và "Widget".
3. **Flashcard Kanji:** Học → Bài học → Bài 01 → Luyện tập. Mặt trước có chữ Kanji lớn và đủ từ đi kèm, không có furigana hay nghĩa. Bấm "Hiện đáp án": thẻ lật, hiện cách đọc và nghĩa từng từ.
4. **Ví dụ Kanji:** mở một Kanji → Sửa → Thêm ví dụ.
   - Thử nhập câu không chứa chữ đó: phải báo lỗi.
   - Thêm ví dụ đúng → Lưu: ví dụ hiện trên trang Kanji và trên flashcard.
   - Xóa → Hủy thì ví dụ còn; Xóa → Lưu thì mất.
5. **Nhóm từ vựng:** Học → Từ vựng hiện Bài 1.1–1.4 (30 từ mỗi nhóm). Mở 1.2 → Luyện tập nhóm này: 30 thẻ. "Tất cả từ vựng" vẫn tìm kiếm như cũ.
6. **Vị trí học:** thẻ trong "Tất cả từ vựng" và danh sách Kanji/Ngữ pháp có nhãn "Bài … · …"; trang chi tiết có mục "Vị trí học".
7. **Thông báo:**
   - Bật thông báo, chọn 15 phút, khung giờ hiện tại, tối đa 4 mỗi ngày.
   - Bấm "Gửi thử" vài lần: mỗi lần một mục khác, nghĩa ẩn.
   - Để máy nghỉ khoảng một giờ: số thông báo không vượt quá giới hạn và không lặp lại mục vừa gửi.
8. **Widget:**
   - Nhấn giữ màn hình chính → Tiện ích → N3 Sensei → thêm cả "Nhỏ (1×1)" và "Lớn (2×2)".
   - Cài đặt → Widget → "Một bài học" → chọn Bài 03: cả hai widget chuyển sang mục của Bài 03.
   - Bấm "Đổi" thì sang mục kế; nhấn vào widget thì mở đúng thẻ.
   - Đổi máy sang chế độ tối và kiểm tra lại.
9. **Trang chủ:** chỉ có Câu nói hôm nay (bấm "Câu khác" để đổi), Mục tiêu hôm nay và Cần ôn (nhấn thẻ để sang Ôn tập). Các biểu đồ và số liệu khác nằm trong Thống kê.
10. **Khởi động lại máy:** thông báo vẫn đến theo tần suất đã chọn và widget vẫn còn trên màn hình chính.
