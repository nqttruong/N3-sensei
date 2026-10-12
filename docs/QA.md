# QA, debugging và hiệu năng (Prompt 11)

Môi trường: emulator Pixel 8 (AVD, Android 16, API 36, heap app 192 MB, kết xuất đồ họa bằng phần mềm), bản **debug**. Chưa thử trên thiết bị thật.

## Lệnh kiểm tra

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
./gradlew :app:dependencies --configuration debugRuntimeClasspath     # xung đột phiên bản
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.n3sensei.app.performance   # dữ liệu lớn
adb logcat -s N3Perf                                                    # số đo của test hiệu năng
adb logcat | grep StrictMode                                            # vi phạm main thread (bản debug)
adb shell dumpsys gfxinfo com.n3sensei.app                              # khung hình khi cuộn
adb shell dumpsys meminfo com.n3sensei.app
```

Test hiệu năng giữ lại database lớn để phân tích nếu chạy với `-e keepDb true` (qua `adb shell am instrument`), sau đó `adb exec-out run-as com.n3sensei.app cat databases/perf-test.db > perf.db` và dùng `sqlite3` (có trong `platform-tools`) với `EXPLAIN QUERY PLAN`.

## Static checks

- Build, unit test, lint: không lỗi; không cảnh báo của trình biên dịch Kotlin hay cảnh báo deprecation của Gradle.
- Lint: 0 lỗi, 25 cảnh báo — đều là "có phiên bản mới hơn" (AGP, Kotlin, Compose BOM, Room, Navigation, Lifecycle, WorkManager, DataStore, Glance, coroutines, serialization). **Chưa nâng cấp** (xem Rủi ro).
- Hai chỗ `tools:ignore` có sẵn và có lý do: `PluralsCandidate` (tiếng Việt không chia số nhiều), `UnusedAttribute` (thuộc tính widget cho API mới hơn minSdk).
- Dependency: chỉ có nâng phiên bản bắc cầu thông thường (vd. `annotation 1.1.0 -> 1.8.1`), không có hạ cấp hay lỗi phân giải.
- Không có test hay quy tắc lint nào bị tắt.

## Lỗi tìm thấy và đã sửa

### 1. Sao lưu lịch sử lớn làm app hết bộ nhớ

- **Tái hiện**: database 300 000 lượt ôn (≈ 2 năm × 410/ngày) → Xuất dữ liệu → `OutOfMemoryError` (heap 192 MB). Khôi phục tệp cỡ đó cũng không thể (đọc cả tệp vào `ByteArray` rồi `String`, giới hạn 64 MB < 62–75 MB của tệp).
- **Nguyên nhân gốc**: lúc xuất, cả lịch sử nằm trong bộ nhớ ba lần (entity Room, model domain, cây `JsonElement` ≈ 1 KB/bản ghi); lúc nhập, toàn bộ văn bản và cây JSON cũng vậy.
- **Sửa**:
  - Xuất: `BackupDao.export` đọc lịch sử theo trang 1 000 dòng (theo `id`) trong một transaction đọc; `BackupCodec.Writer` ghi JSON tuần tự ra stream.
  - Nhập: `BackupCodec.decode(InputStream)` đọc stream bằng deserializer riêng, chuyển và kiểm tra **từng phần tử** rồi bỏ JSON tạm; dùng chung một `ItemRef` cho mỗi mục; giới hạn tệp 256 MB đếm trên stream.
  - Gộp: chỉ nạp `request_id` và khóa của bản ghi cũ không có `request_id` (thay vì mọi khóa), ghi theo lô 1 000.
  - Lỗi khi xuất: tách lỗi ghi tệp và lỗi database; thử xóa tệp dở dang.
- **Test hồi quy**: `LargeDataPerformanceTest.backupOfLargeHistory_exportsAndRestoresWithinMemory` (xuất 300 000 lượt ra tệp thật, kiểm tra, khôi phục vào database trống). Trước khi sửa: OOM; sau: qua — tệp 62 MB, xuất 2,9 s, kiểm tra 7,6 s, khôi phục 6,4 s, heap đo tại các mốc tối đa 35 MB. Các test sao lưu cũ (codec, merge, Room, giao diện) vẫn qua.

### 2. Truy vấn thống kê quét toàn bộ lịch sử, kể cả "Hôm nay"

- **Tái hiện**: cùng database lớn; Thống kê/Trang chủ → mỗi truy vấn theo khoảng thời gian mất 390–810 ms (nhiều truy vấn chạy cùng lúc, lặp lại mỗi phút).
- **Nguyên nhân gốc** (`EXPLAIN QUERY PLAN`): điều kiện "mục còn trong nội dung" viết dạng `(item_type = 'VOCABULARY' AND EXISTS …) OR (…KANJI…) OR (…GRAMMAR…)` khiến SQLite chọn `MULTI-INDEX OR` trên chỉ mục `item_type` và duyệt **mọi** bản ghi, bỏ qua chỉ mục `reviewed_at`. Thêm vào đó `(:from IS NULL OR reviewed_at >= :from)` làm mất cận dưới của khoảng.
- **Sửa**: điều kiện thành một tập `item_type || ':' || item_id IN (SELECT … UNION ALL …)` (SQLite tính một lần, có bloom filter), khoảng thời gian luôn có cận dưới (`Long.MIN_VALUE` cho "Tất cả"). Kết quả giống hệt (đã so trên database lớn và bằng các test đếm tay cũ).
- **Kết quả** (emulator, 300 000 lượt):

  | Truy vấn | Trước | Sau |
  |---|---|---|
  | Hoạt động hôm nay | 390 ms | 5 ms |
  | Hoạt động toàn bộ | 813 ms | 275 ms |
  | Theo ngày 30 ngày | 425 ms | 12 ms |
  | Chuỗi ngày học | 709 ms | 132 ms |
  | Theo loại 7 ngày | 405 ms | 12 ms |
  | Hay quên hôm nay | 411 ms | 5 ms |
- **Test hồi quy**: `LargeDataPerformanceTest.heavyQueriesUseIndexes` kiểm tra kế hoạch truy vấn thật (dùng chính hằng SQL của `AnalyticsDao`): phải là `SEARCH … index_review_records_reviewed_at` và không có `MULTI-INDEX OR`. `AnalyticsRepositoryTest`, `StatisticsEndToEndTest`, `DashboardEndToEndTest` vẫn qua.

### 3. Biểu tượng thanh trạng thái không nhìn thấy khi giao diện app khác hệ thống

- **Tái hiện**: hệ thống ở chế độ tối, Cài đặt → Giao diện → Sáng → giờ, pin, sóng màu trắng trên nền sáng (ngược lại với hệ thống sáng + app Tối).
- **Nguyên nhân gốc**: `enableEdgeToEdge()` chọn màu biểu tượng theo chế độ tối **của hệ thống**; lựa chọn giao diện trong app không được áp cho thanh hệ thống.
- **Sửa**: `N3SenseiTheme` đặt `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` theo giao diện app đang dùng.
- **Test hồi quy**: `SystemBarAppearanceTest` (đổi chế độ tối của hệ thống bằng `cmd uimode`). Đã xác nhận: với `Theme.kt` cũ, 2/3 test thất bại; với bản sửa, cả 3 qua.

## Đã kiểm tra, không thấy lỗi

| Hạng mục | Cách kiểm tra |
|---|---|
| Dữ liệu học (tạo/đọc/cập nhật/xóa tiến độ, đánh dấu, lịch sử) | Test Room + E2E có sẵn (`ReviewSchedulingRepositoryTest`, `OfflineProgressRepositoryTest`, `EndToEndTest`) |
| Nhập gói nội dung | `ContentPackImporterTest` (Room), `ContentPackValidatorTest`, E2E cài gói minh họa khi mở app lần đầu |
| Tiến độ, lịch ôn, bốn mức đánh giá | `SpacedRepetitionSchedulerTest` (JVM), `ReviewSessionViewModelTest`, E2E phiên ôn; thử tay 6 thẻ, kiểm tra DB |
| Thông báo | `ReminderInstrumentedTest`, `ReminderRunnerTest` |
| Thống kê và dự báo | Test đếm tay (JVM + Room + E2E) |
| Sao lưu/khôi phục | Test Prompt 10 + dữ liệu lớn |
| Cài đặt sau khởi động lại | Thử tay: đổi giao diện Sáng + tắt furigana → `am force-stop` (tiến trình bị dừng) → mở lại: vẫn giữ |
| Migration | `N3DatabaseMigrationTest` (1→4, dữ liệu v1/v2/v3) |
| Điều hướng, Back | `N3SenseiNavigationTest`, E2E |
| Tìm kiếm, trạng thái rỗng/lỗi | E2E có sẵn |
| Tái tạo Activity (như xoay màn hình) | **Mới** `ConfigurationChangeTest`: phiên ôn giữa chừng (đúng thẻ, không ghi trùng), ô tìm kiếm và kết quả, back stack, hộp thoại xem trước khôi phục |
| Tối, màn hình nhỏ, chữ 1,3×, tiếng Nhật/Việt | Ảnh chụp 720×1280 @ 320 dpi: không cắt chữ; nhãn thanh điều hướng xuống 2 dòng ("Trang / chủ") — đọc được |
| Truy vấn trên main thread | Room cấm sẵn; bật **StrictMode** (bản debug, chỉ ghi log) và đi qua giới thiệu, ôn tập, Trang chủ, Thống kê, Cài đặt, danh sách: 0 vi phạm (đã thử StrictMode bắt được một lần đọc đĩa cố ý) |
| Danh sách lazy có `key` | Đọc mã: mọi `items(...)` đều có key |
| Tải toàn bộ lịch sử chỉ để lấy tổng | Mọi tổng đếm trong SQL; chuỗi ngày học trả về các khối 15 phút khác nhau (≤ 96/ngày, 70 080 dòng với 2 năm học dày) |
| Chỉ mục | Kế hoạch truy vấn: lịch sử theo mục, đến hạn, `request_id`, khoảng thời gian đều dùng chỉ mục |

Số đo khác (300 000 lượt, 4 300 mục): hàng đợi ôn, mục mới, tổng quan ôn tập ≤ 5 ms; danh sách 3 000 từ 36 ms; tìm kiếm 5 ms. Cuộn danh sách 3 000 từ trên emulator (bản debug): 6,8 % khung hình giật, p50 27 ms, p90 32 ms; bộ nhớ app 119 MB PSS, Java heap 23 MB.

## Kiểm thử thủ công còn nên làm (trên thiết bị thật)

1. Bản **release** (R8) trên máy tầm trung: cuộn danh sách dài, mở Thống kê với lịch sử lớn; đo bằng `dumpsys gfxinfo`.
2. Xoay màn hình thật trong phiên ôn, trong hộp thoại khôi phục, trong ô tìm kiếm.
3. Đổi chế độ tối của hệ thống khi app đang mở với từng lựa chọn giao diện; xem thanh trạng thái và thanh điều hướng.
4. Nhắc học: bật, chờ ít nhất một chu kỳ thật, khóa màn hình với 4 chế độ riêng tư; khởi động lại máy.
5. Sao lưu ra Google Drive / thẻ nhớ qua trình chọn tệp; khôi phục trên máy khác; khôi phục khi đang ôn dở.
6. Cỡ chữ hệ thống lớn nhất và màn hình rất hẹp (≤ 360 dp).

## Rủi ro và việc còn tồn tại

- Thư viện cũ hơn bản mới nhất (AGP 8.13 → 9.x, Kotlin 2.0 → 2.4, Room 2.6 → 2.8…). Nâng cấp là thay đổi lớn, cần một nhiệm vụ riêng có chạy lại toàn bộ test.
- Số đo hiệu năng trên emulator và bản debug; chưa có đo trên thiết bị thật hay bản release, chưa có Macrobenchmark/Baseline Profile.
- Truy vấn "toàn bộ thời gian" và chuỗi ngày học vẫn phải duyệt cả lịch sử (275 ms / 132 ms với 300 000 lượt) và chạy lại mỗi phút khi màn hình Thống kê mở.
- Kiểm tra tệp sao lưu 300 000 lượt mất ≈ 7,6 s (có vòng xoay chờ). Bản xem trước giữ dữ liệu đã đọc trong bộ nhớ đến khi xác nhận.
- Nếu ứng dụng chọn tệp không cho xóa, tệp sao lưu dở dang (khi xuất lỗi) có thể còn lại; app báo lỗi rõ.
- `ProgressRepository.observeSummary`, `observeRatingCounts`, `observeDueCount`, `observeDue` không còn được giao diện dùng (từ khi Trang chủ thành bảng điều khiển). Không ảnh hưởng chạy, nên dọn ở một nhiệm vụ sau.
