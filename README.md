# N3 Sensei

Ứng dụng Android native hỗ trợ học tiếng Nhật trình độ JLPT N3 (từ vựng, Kanji, ngữ pháp, ôn tập lặp lại cách quãng). Hoạt động offline-first.

> Trạng thái: khung project (Prompt 01). Chưa có nghiệp vụ học tập.

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

## Cấu trúc package

```
com.n3sensei.app
├── MainActivity, N3SenseiApplication   điểm vào ứng dụng
├── app/                                 AppContainer (DI thủ công), N3SenseiApp (Scaffold)
│   └── navigation/                      TopLevelDestination, NavHost
├── core/
│   ├── designsystem/theme/              màu, typography, theme sáng/tối Material 3
│   └── ui/                              composable dùng chung
├── data/                                (Prompt 02+) Room entity, DAO, repository, DataStore
├── domain/                              (Prompt 02+) model và logic thuần Kotlin, ví dụ lịch ôn tập
└── feature/
    ├── learn/  review/  stats/  settings/   mỗi tab một package
```

Quy tắc phụ thuộc: `feature` → `domain` ← `data`; Composable không truy cập database trực tiếp mà qua ViewModel và Repository. Các package `data` và `domain` sẽ được tạo cùng code thật ở giai đoạn tiếp theo.

Mã test:

- `app/src/test/` — unit test JVM; `testing/` chứa tiện ích dùng chung (`MainDispatcherRule`).
- `app/src/androidTest/` — UI test Compose chạy trên thiết bị.

## Thư viện chính

Jetpack Compose (BOM 2024.11.00) + Material 3, Lifecycle/ViewModel 2.8.7, Navigation Compose 2.8.4, Room 2.6.1 (KSP), DataStore Preferences 1.1.1, Kotlin Coroutines 1.9.0. Test: JUnit 4, kotlinx-coroutines-test, AndroidX Test (Espresso 3.7.0), Compose UI Test.
