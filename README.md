# N3 Sensei

Ứng dụng Android native hỗ trợ học tiếng Nhật trình độ JLPT N3 (từ vựng, Kanji, ngữ pháp, ôn tập lặp lại cách quãng). Hoạt động offline-first.

> Trạng thái: module học, nhập nội dung JSON, engine ôn tập cách quãng (Prompt 06), nhắc học qua thông báo và widget màn hình chính (Prompt 07, xem [docs/REMINDERS.md](docs/REMINDERS.md)), thống kê học tập (Prompt 08, xem [docs/ANALYTICS.md](docs/ANALYTICS.md)), bảng điều khiển ở Trang chủ với dự báo khối lượng ôn và tiến độ (Prompt 09, xem [docs/FORECAST.md](docs/FORECAST.md); dự báo là ước tính theo giả định ghi rõ, không dự đoán kết quả thi), sao lưu và khôi phục dữ liệu học ra tệp JSON (Prompt 10, xem [docs/BACKUP.md](docs/BACKUP.md)). Kết quả QA, lỗi đã sửa và số đo hiệu năng: [docs/QA.md](docs/QA.md). UX và trợ năng: [docs/ACCESSIBILITY.md](docs/ACCESSIBILITY.md). Bảo mật, quyền riêng tư, toàn vẹn dữ liệu: [docs/SECURITY.md](docs/SECURITY.md); điểm mở rộng tương lai: [docs/EXTENSIBILITY.md](docs/EXTENSIBILITY.md). App kèm **một bài minh họa tự biên soạn** (30 từ, 27 Kanji, 7 ngữ pháp, chưa qua kiểm duyệt chuyên môn); nội dung N3 đầy đủ cần được nhập từ nguồn có giấy phép. Ôn tập lặp lại cách quãng hoạt động (Prompt 06, xem [docs/SRS.md](docs/SRS.md)); chế độ luyện tập theo bài vẫn không ảnh hưởng lịch ôn.

## Giới hạn và chưa hoàn thành

- **Nội dung**: một bài minh họa tự biên soạn (30 từ, 27 Kanji, 7 ngữ pháp, chưa qua kiểm duyệt chuyên môn) và **880 từ vựng Mimikara N3** (12 bài, chỉ từ vựng; nhập từ bảng do chủ dự án cung cấp, **giấy phép chưa xác minh**, xem [docs/MIMIKARA_N3.md](docs/MIMIKARA_N3.md)). **110 mẫu ngữ pháp N3** theo danh mục Mimikara N3 của VNJPCLUB, nội dung giải thích và ví dụ **tự soạn, chưa kiểm duyệt** (xem [docs/N3_GRAMMAR.md](docs/N3_GRAMMAR.md)). Chưa có Kanji ngoài bài minh họa.
- **Chưa có**: thứ tự nét Kanji; đồng bộ đám mây, tài khoản, AI (chỉ có đề xuất trong [docs/EXTENSIBILITY.md](docs/EXTENSIBILITY.md)); sao lưu định kỳ tự động; nút "Thử lại" ở các màn hình dạng danh sách khi lỗi đọc dữ liệu.
- **Chưa kiểm thử**: thiết bị thật, bản release (chưa cấu hình ký, R8 tắt), Android 8–11, TalkBack với giọng đọc thật, nhắc học định kỳ qua nhiều giờ thực tế.
- **Kết quả nghiệm thu, lỗi đã sửa và lỗi còn theo dõi**: xem [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

## Yêu cầu môi trường

| Thành phần | Phiên bản |
|---|---|
| JDK | 17 |
| Android SDK Platform | 36 (`platforms;android-36`) |
| Gradle | 8.13 (qua wrapper, không cần cài riêng) |
| Thiết bị/emulator | Android 8.0 (API 26) trở lên |

Tạo `local.properties` ở thư mục gốc nếu Android Studio chưa tự tạo:

```properties
sdk.dir=/đường/dẫn/tới/Android/Sdk
```

## Build và kiểm thử

```bash
./gradlew assembleDebug              # APK debug: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest          # unit test (JVM)
./gradlew lintDebug                  # lint: app/build/reports/lint-results-debug.html
./gradlew connectedDebugAndroidTest  # UI test, cần emulator/thiết bị đang kết nối
./gradlew installDebug               # cài lên thiết bị đang kết nối, rồi mở "N3 Sensei"
```

## Cấu hình chính

- `applicationId` / `namespace`: `com.n3sensei.app`
- `minSdk` 26, `compileSdk`/`targetSdk` 36 (Google Play yêu cầu API 36 cho app mới và bản cập nhật từ 31/08/2026).
- Phiên bản thư viện tập trung tại `gradle/libs.versions.toml`.
- Schema Room được export vào `app/schemas/` (commit vào Git để kiểm tra migration).

### Release signing

Chưa cấu hình. Khi cần phát hành, tạo `keystore.properties` (đã nằm trong `.gitignore`) và file keystore **ngoài** repo; không commit mật khẩu hay keystore.

## Kiến trúc

Xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/DATABASE.md](docs/DATABASE.md) và [docs/CONTENT_FORMAT.md](docs/CONTENT_FORMAT.md) (định dạng gói nội dung JSON): sơ đồ lớp, thiết kế database, đồ thị điều hướng, quản lý UI state, dependency injection và quy ước đặt tên/resource.

```
com.n3sensei.app
├── MainActivity, N3SenseiApplication
├── app/            Scaffold, navigation/ (route tập trung), di/ (AppContainer)
├── core/           designsystem/ (theme, icon, component), ui/state/ (UiState)
├── feature/        home, learn, vocabulary, kanji, grammar, review, stats, settings
├── domain/         model, repository interface, furigana, báo cáo import (Kotlin thuần)
└── data/           Room (entity, DAO, migration), DataStore, import JSON, repository
```

Mã test: `app/src/test/` (JVM, gồm test quy tắc kiến trúc) và `app/src/androidTest/` (Compose UI test, điều hướng).

## Thư viện chính

Jetpack Compose (BOM 2024.11.00) + Material 3, Lifecycle/ViewModel 2.8.7, Navigation Compose 2.8.4 (route type-safe), kotlinx-serialization 1.7.3 (route và gói nội dung JSON), Room 2.6.1 (KSP), DataStore Preferences 1.1.1, Kotlin Coroutines 1.9.0, WorkManager 2.10.0, Glance 1.1.1 (widget). Test: JUnit 4, kotlinx-coroutines-test, AndroidX Test (Espresso 3.7.0, Espresso-Intents), Compose UI Test, Navigation Testing, Room Testing, WorkManager Testing.
