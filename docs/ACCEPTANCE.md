# Nghiệm thu cuối (Prompt 14)

Nghiệm thu độc lập trên commit `7de1b03`, ngày 13/10/2026. Không dựa vào kết luận của các giai đoạn trước: build lại từ bản clone sạch theo README, cài mới hoàn toàn trên emulator, đi tay từng luồng và đối chiếu với database kéo về máy (`sqlite3`).

**Môi trường**: Ubuntu, JDK 17, Android SDK 36; emulator Pixel 8 (AVD, Android 16 / API 36, bản Google Play, không root), bản **debug**. **Không có thiết bị thật.**

Ký hiệu cột bằng chứng: **[T]** test tự động · **[E]** đã chạy tay trên emulator · **[—]** chưa thể kiểm thử trong môi trường hiện tại.

## Kết quả lệnh

| Lệnh | Kết quả |
|---|---|
| `git clone` + `local.properties` + `./gradlew assembleDebug testDebugUnitTest lintDebug` (bản sạch, theo README) | BUILD SUCCESSFUL; 214 unit test đạt; lint 0 lỗi, 25 cảnh báo (đều là "có phiên bản mới hơn") |
| `./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` (cây làm việc) | Lần 1: instrumented bị **ngắt** sau 8/156 test (xem lỗi L2). Chạy lại bộ điều hướng: đạt; chạy lại toàn bộ: **156/156 đạt** |

## Bảng nghiệm thu

| # | Chức năng | Trạng thái | Bằng chứng | Lỗi còn lại | Ưu tiên |
|---|---|---|---|---|---|
| 1 | Cài đặt, giới thiệu, vào Trang chủ | Đạt | [E] cài mới APK từ bản clone sạch; 3 trang giới thiệu → Trang chủ "Mục mới 0/20", gói minh họa tự cài. [T] `EndToEndTest` (bỏ qua/nhớ/xem lại giới thiệu) | — | — |
| 2 | Bài học, từ vựng, Kanji, ngữ pháp | Đạt | [E] Bài 1 (30 từ · 27 Kanji · 7 ngữ pháp), chi tiết 会議, 経 (On/Kun, Hán Việt, ví dụ), 〜たばかり. [T] `EndToEndTest`, `N3SenseiNavigationTest` | L1 — đã sửa sau nghiệm thu | — |
| 3 | Tìm kiếm | Đạt | [E] "kinh nghiem" → 1 kết quả 経験, có dòng "Tìm thấy 1 kết quả". [T] tìm theo cách đọc kana, không dấu, không có kết quả, lọc theo bài. [—] gõ kana qua adb không được (chỉ có test tự động) | — | — |
| 4 | Phiên học đầu tiên, 4 mức đánh giá | Đạt | [E] 20 thẻ, 5 Quên / 5 Khó / 5 Tốt / 5 Dễ; DB: Quên/Khó → bậc 1 (20 phút), Tốt → bậc 2 (1 giờ), Dễ → bậc 3 (1 ngày, Đang ôn); 20 bản ghi `was_new`=1; phiên 20/20 hoàn thành. [T] `SpacedRepetitionSchedulerTest`, `ReviewSessionViewModelTest`, `DataIntegrityTest` | — | — |
| 5 | Ôn tập mục đến hạn, lịch ôn tiếp theo | Đạt | [E] đặt đồng hồ +25 phút → "Đến hạn: 10", dòng lý do "Đến hạn lúc 20:01 · bậc 1/7"; DB khớp quy tắc (Quên ở Đang học không tính lần quên, Khó giữ bậc, Tốt +1, Dễ +2). +25 giờ → "Đến hạn 20, quá hạn 13" (khớp DB); Quên 7 mục đang ở trạng thái Đang ôn → `lapse_count`=1 đúng 7 mục | — | — |
| 6 | Thoát và mở lại | Đạt | [E] `am force-stop` → mở lại: bảng tiến độ và lịch sử **giống hệt** trước (diff); "Ôn tiếp 20:30" khớp `due_at`. [T] E2E tiến độ sau khởi động lại | — | — |
| 7 | Thông báo | Đạt | [E] hộp thoại quyền thật → Cho phép; WorkManager đăng thông báo thật "相談 (そうだん) – trao đổi, bàn bạc" ngay khi bật; chạm → thẻ 相談, Back → Học; tắt → job bị hủy (0), bật lại → 1 job; thu hồi quyền → màn hình báo "không được phép gửi", "Gửi thử" báo bị chặn. [T] `ReminderInstrumentedTest`, `ReminderRunnerTest`, `ReminderPolicyTest`. [—] chu kỳ định kỳ thật qua nhiều giờ, khởi động lại máy thật | M1 — đã sửa sau nghiệm thu | — |
| 8 | Dashboard và thống kê | Đạt | [E] đối chiếu với DB: Hôm nay 20 lượt / 20 mục / 0 mới / 20 lượt ôn / nhớ 0/20; 7 ngày 50 / 20 / 20 / 30 / nhớ 7/30 = 23 %, Quên 23 · Khó 3 · Tốt 2 · Dễ 2 — **khớp hết**; xu hướng theo ngày 30 và 20 (sau đó 40); lịch sử phiên 20/20, 10/10, 20/40 "dừng giữa chừng". [T] test đếm tay JVM + Room + E2E | — | — |
| 9 | Dự báo 1/7/14/30 ngày | Đạt | [E] trước: "Đã lên lịch" 0/0/0/0 (cả 20 mục đến hạn hôm nay); trả lời Dễ cho 20 mục → 20/20/20/20, khớp DB (20 mục đến hạn ngày mai). "Lượt ôn dự kiến" không đổi (≈60/152/216/280) — đã kiểm: cả hai trường hợp mục đều tới bậc 1 ngày vào ngày mai nên chuỗi mô phỏng giống nhau. [T] `WorkloadForecasterTest` (lịch biết trước, đổi lịch, đổi dãy bậc) | — | — |
| 10 | Sao lưu | Đạt | [E] trình chọn tệp thật → `Download/n3sensei-backup-2026-10-15.json` (22 KB), JSON hợp lệ: format v1, `includesContent=false`, 20 tiến độ, 70 lượt ôn, 4 phiên, cài đặt (Tối, 08–22, mỗi giờ) — khớp DB. [T] `BackupRepositoryTest`, `BackupCodecTest`, dữ liệu lớn | — | — |
| 11 | Khôi phục | Đạt | [E] xóa dữ liệu app (`pm clear`) → tệp cắt cụt và tệp `formatVersion` 9: "Không thể khôi phục… Dữ liệu trong app không thay đổi"; tệp thật: xem trước "thêm 20/70/0/4" → khôi phục → tiến độ và lịch sử (cả `request_id`, `was_new`) **giống hệt** trước khi xóa; cài đặt khôi phục (Tối, mỗi giờ). [T] vòng tròn khớp từng dòng, rollback giữa transaction, tệp v1 cố định | — | — |
| 12 | Migration | Đạt | [E] **nâng cấp thật**: cài APK Prompt 08 (database v3), học 5 thẻ, cài APK hiện tại đè lên → `user_version`=4, có `review_sessions`, `integrity_check`=ok, không vi phạm khóa ngoại, tiến độ và lịch sử giống hệt; Trang chủ "Mục mới 5/20". [T] `N3DatabaseMigrationTest` (mọi phiên bản 1 → 4, dữ liệu v1/v2/v3) | — | — |
| 13 | Toàn vẹn dữ liệu, database hỏng, dữ liệu ngoài độc hại | Đạt | [T] `DataIntegrityTest`, `CorruptDatabaseTest`, `HostileInputTest`; [E] Prompt 13 (dừng app giữa lúc lưu ×15, làm hỏng database) | Mất dữ liệu khi database hỏng nếu không có tệp sao lưu (đã có cảnh báo) | Trung bình |
| 14 | Trợ năng, cỡ chữ lớn, chế độ tối | Đạt | [T] `AccessibilityTest`, `ColorContrastTest`, `LearningUxTest`, `FuriganaTextTest`; [E] Prompt 12. [—] TalkBack thật | — | — |
| 15 | Hiệu năng dữ liệu lớn | Đạt | [T] `LargeDataPerformanceTest` (300 000 lượt ôn); số đo trong [QA.md](QA.md). [—] thiết bị thật, bản release | — | — |
| 16 | README build/chạy/kiểm thử | Đạt | Làm theo README trên bản clone sạch: build và test đạt. Đã bổ sung mục "Giới hạn và chưa hoàn thành" | — | — |

## Lỗi còn lại

| Mã | Lỗi | Cách tái hiện | Ưu tiên |
|---|---|---|---|
| M1 — **đã sửa** | Menu chọn giờ nhắc không chọn được 23:00 | Cài đặt → Nhắc học → ô "Đến" → cuộn menu tới cuối: hiện tới 22:00, mục 23:00 nằm dưới mép màn hình (uiautomator: bounds `[0,0][0,0]`), cuộn tiếp không đổi. Pixel 8, cỡ chữ mặc định. Không đặt được khung giờ kết thúc lúc 23:00; ô "Từ" chưa kiểm lại được trong lần nghiệm thu này | Trung bình |
| L1 — **đã sửa** | Chạm lại tab đang mở (vd. "Học" khi đang ở chi tiết ngữ pháp) không đưa về màn gốc của tab | Học → Bài 1 → Ngữ pháp → 〜たばかり → chạm tab "Học": vẫn ở chi tiết | Thấp |
| L2 | Một lần chạy instrumented bị ngắt sau 8/156 test | Xảy ra ngay sau khi đồng hồ emulator bị kéo lùi ≈ 38 giờ và app cũ được cài đè; không có crash của app trong log; chạy lại bộ điều hướng và toàn bộ 156 test đều đạt. Chưa tái hiện được | Thấp (theo dõi) |

Không có lỗi crash nghiêm trọng nào đã biết mà chưa sửa.

## Chưa thể kiểm thử trong môi trường hiện tại

- **Thiết bị thật** (mọi luồng), các hãng khác nhau, Android 8–11.
- **Bản release**: chưa cấu hình ký, R8 tắt, chưa build/test bản release.
- Nhắc học định kỳ theo thời gian thật qua nhiều giờ, Doze, khởi động lại máy thật (WorkManager từ chối chạy sớm khi buộc chạy; đã có test qua TestDriver).
- Luật sao lưu tự động của Android (emulator không cho kiểm), TalkBack bằng giọng đọc thật, gõ tiếng Nhật bằng bàn phím IME.

## Kết luận

**Chưa sẵn sàng phát hành công khai.** Lý do:
1. Nội dung học chỉ là **bài minh họa tự biên soạn** (30 từ, 27 Kanji, 7 ngữ pháp), chưa qua kiểm duyệt chuyên môn, chưa phải giáo trình N3 đầy đủ; nội dung có giấy phép cần được nhập.
2. Bản release chưa được build, ký và kiểm thử; chưa chạy trên thiết bị thật.
3. ~~Còn lỗi M1 (mức trung bình)~~ — đã sửa sau nghiệm thu (xem mục cuối).

Phần mềm ở trạng thái **dùng được cho thử nghiệm nội bộ trên bản debug**: các luồng chính chạy với dữ liệu thật, thuật toán ôn tập, thống kê, dự báo, sao lưu/khôi phục và migration đều có test tự động và đã được đối chiếu tay với database.

## Sửa lỗi sau nghiệm thu

| Lỗi | Nguyên nhân gốc (bằng chứng) | Sửa | Test hồi quy |
|---|---|---|---|
| M1 | `dumpsys window`: popup của `DropdownMenu` xin cửa sổ cao 2399 px bắt đầu từ y = −97 (tính theo cả cửa sổ edge-to-edge); hệ thống kẹp khung vào [132, 2337] giữa thanh trạng thái và thanh điều hướng → phần đáy bị cắt, và menu tưởng mọi mục đã hiện nên không cuộn tiếp. 23:00 nằm ở y = 2421–2474 > 2400 | `ReminderSettingsScreen.HourPicker`: menu giới hạn cao 320 dp (`heightIn`), cuộn bên trong. Sau sửa: popup 840 px, nằm trong vùng hiển thị; thử tay chọn được 23:00 | `HourPickerReachabilityTest` (cuộn menu tới cuối rồi hỏi cây trợ năng thật). Không có bản sửa: thất bại (`visibleToUser=false`, y 2421–2474); có bản sửa: đạt |
| L1 | `navigateToTopLevel` lưu rồi khôi phục chính ngăn xếp của tab đang mở (`saveState`/`restoreState`) → không đổi gì | `NavigationActions.selectTab`: tab khác → như cũ; tab đang mở → `popBackStack` về màn đầu của tab. Thanh tab dùng `selectTab` | `N3SenseiNavigationTest.reselectingCurrentTab_returnsToItsFirstScreen` (trước sửa: `expected LEARN_HUB but was KANJI`); `returningToLearnTab_restoresItsSubScreen` vẫn đạt |

Sau khi sửa: `./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` → BUILD SUCCESSFUL; 214 unit test, 158 instrumented test đạt; lint 0 lỗi.

