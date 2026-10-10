# N3 Sensei

Ứng dụng Android native hỗ trợ học tiếng Nhật trình độ JLPT N3 (từ vựng, Kanji, ngữ pháp, ôn tập lặp lại cách quãng). Hoạt động offline-first.

> Trạng thái: kiến trúc và điều hướng (Prompt 02). Chưa có nghiệp vụ học tập.

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
./gradlew installDebug               # cài lên thiết bị đang kết nối
```

## Cấu hình chính

- `applicationId` / `namespace`: `com.n3sensei.app`
- `minSdk` 26, `compileSdk`/`targetSdk` 36 (Google Play yêu cầu API 36 cho app mới và bản cập nhật từ 31/08/2026).
- Phiên bản thư viện tập trung tại `gradle/libs.versions.toml`.
- Schema Room được export vào `app/schemas/` (commit vào Git để kiểm tra migration).

### Release signing

Chưa cấu hình. Khi cần phát hành, tạo `keystore.properties` (đã nằm trong `.gitignore`) và file keystore **ngoài** repo; không commit mật khẩu hay keystore.

## Kiến trúc

Xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): sơ đồ lớp, đồ thị điều hướng, quản lý UI state, dependency injection và quy ước đặt tên/resource.

```
com.n3sensei.app
├── MainActivity, N3SenseiApplication
├── app/            Scaffold, navigation/ (route tập trung), di/ (AppContainer)
├── core/           designsystem/ (theme, icon, component), ui/state/ (UiState)
├── feature/        home, learn, vocabulary, kanji, grammar, review, stats, settings
├── domain/         (Prompt 03+) model, logic thuần Kotlin
└── data/           (Prompt 03+) Room, DataStore, repository
```

Mã test: `app/src/test/` (JVM, gồm test quy tắc kiến trúc) và `app/src/androidTest/` (Compose UI test, điều hướng).

## Thư viện chính

Jetpack Compose (BOM 2024.11.00) + Material 3, Lifecycle/ViewModel 2.8.7, Navigation Compose 2.8.4 (route type-safe, kotlinx-serialization 1.7.3), Room 2.6.1 (KSP), DataStore Preferences 1.1.1, Kotlin Coroutines 1.9.0. Test: JUnit 4, kotlinx-coroutines-test, AndroidX Test (Espresso 3.7.0), Compose UI Test, Navigation Testing.
