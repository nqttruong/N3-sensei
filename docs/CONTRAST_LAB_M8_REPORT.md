# Contrast Lab — Báo cáo nghiệm thu M8 (Full regression & release readiness)

Ngày: 2026-11-17. Môi trường: Linux, JDK 17, emulator `Pixel_8` Android 16 (API 36, `emulator-5554`). Không dùng máy thật. Mọi con số dưới đây là kết quả của lệnh đã chạy thật trong M8; mục nào chưa chạy được ghi rõ là chưa chạy.

Checklist phát hành: [CONTRAST_LAB_RELEASE_CHECKLIST.md](CONTRAST_LAB_RELEASE_CHECKLIST.md).

## 1. Tổng quan M0–M8

| Milestone | Nội dung | Tài liệu |
|---|---|---|
| M0 | Kiến trúc, quyết định D1–D6, contract | `CONTRAST_LAB_AUDIT.md`, `CONTRAST_LAB_ARCHITECTURE.md`, `CONTRAST_LAB_DOMAIN_CONTRACT.md` |
| M1 | Domain Kotlin thuần: mô hình, chấm điểm, lập phiên, lịch ôn 1/3/7/14/30/60 ngày, mastery, phân tích lỗi | `CONTRAST_LAB_DOMAIN_CONTRACT.md` |
| M2 | Tooling Python: JSON Schema, validator ngữ nghĩa, fingerprint SHA-256, so sánh bản trước, build gói | `CONTRAST_LAB_CONTENT_SCHEMA.md`, `CONTRAST_LAB_TOOLING.md` |
| M3 | Room v16: 13 bảng Contrast, migration 15→16 | `CONTRAST_LAB_ARCHITECTURE.md`, `DATABASE.md` |
| M4 | Importer: kiểm tra, đối chiếu nguồn với Room, ghi trong một transaction | `CONTRAST_LAB_IMPORTER.md` |
| M5 | Giao diện học: Home, Thư viện, So sánh | `CONTRAST_LAB_UI.md` |
| M6 | Luyện tập: phiên, 4 dạng câu hỏi, phản hồi, tổng kết | `CONTRAST_LAB_PRACTICE.md` |
| M7 | Phân tích lỗi, Ôn tập thông minh, Tiến độ | `CONTRAST_LAB_ANALYTICS_SRS.md` |
| M8 | Hồi quy toàn bộ, toàn vẹn dữ liệu, hiệu năng, ổn định, bảo mật, tài liệu phát hành | tài liệu này |

Cả tám tài liệu Contrast mà M8 yêu cầu đều có. Không có tài liệu kiểm thử riêng cho Contrast; bằng chứng kiểm thử nằm trong mã test và trong báo cáo này.

## 2. Ma trận đối chiếu yêu cầu

| Milestone | Yêu cầu | Implementation | Test | Kết quả | Vấn đề |
|---|---|---|---|---|---|
| M0 | Kiến trúc, quyết định, contract | 8 tài liệu `docs/CONTRAST_LAB_*` | — (tài liệu) | VERIFIED | D6 yêu cầu "ghi rõ trong UI" giới hạn sao lưu nhưng UI chưa ghi → **đã sửa trong M8** |
| M1 | Domain, chấm điểm, lịch ôn, mastery, phân tích | `domain/contrast/*` | `ContrastSchedulerTest`, `ContrastSessionPlannerTest`, `ContrastAnswerCheckerTest`, `ContrastAnalysisTest`, `ContrastUseCasesTest`, `ContrastModelsTest`, `ArchitectureRulesTest` | VERIFIED | — |
| M2 | Schema, validator, fingerprint, fixtures dùng chung, so sánh bản trước | `tools/content/contrast/*`, `docs/contrast/contrast-pack.schema.json` | `test_contrast_tools.py` (25), `ContrastSharedFixturesTest` (Kotlin chạy cùng fixtures) | VERIFIED | — |
| M3 | Room v16, migration, ràng buộc | `ContrastEntities`, `ContrastDao`, `Migrations` | `N3DatabaseMigrationTest`, `ContrastMigrationTest`, `ContrastPersistenceTest`, `ContrastM8IntegrityTest` | VERIFIED | — |
| M4 | Importer, đối chiếu nguồn, rollback, idempotent | `ContrastContentImporter`, `ContrastPackCheck`, `RoomContrastSourceResolver` | `ContrastContentImporterTest` (12), `ContrastBundledInstallTest`, `RoomContrastSourceResolverTest` | VERIFIED | — |
| M5 | Home, Thư viện, So sánh, xem không sinh dữ liệu | `feature/contrast/ContrastHome*`, `ContrastLibrary*`, `ContrastGroup*`, `ContrastLearning*` | `ContrastViewModelsTest`, `ContrastScreensTest`, `ContrastLabEndToEndTest` | VERIFIED | Home nạp mọi attempt vào bộ nhớ → **đã tối ưu trong M8** |
| M6 | Phiên, 4 dạng câu, nộp, bỏ qua, tiếp tục, tổng kết | `ContrastPractice*`, `ContrastSessionSummary*` | `ContrastPracticeScreensTest`, `ContrastPracticeEndToEndTest` (thêm test đổi cấu hình trong M8) | VERIFIED | — |
| M7 | Phân tích lỗi, Ôn tập, Tiến độ | `ContrastAnalytics*`, `ContrastInsights*` | `ContrastInsightsTest`, `ContrastAnalyticsRoomTest`, `ContrastInsightsScreensTest`, `ContrastInsightsEndToEndTest` | VERIFIED | Mở rộng contract `PracticeScope.Review` (đã báo ở M7) |
| — | Điều hướng, DI | `Routes`, `AppScreen`, `N3SenseiNavHost`, `AppContainer` | `AppScreenTest`, `ContrastNavigationTest`, các test E2E | VERIFIED | — |
| — | Backup / Restore | Không đổi (D6) | `BackupRepositoryTest`, `BackupEndToEndTest`, `ContrastM8IntegrityTest.theOldBackup…` | VERIFIED (cho phạm vi D6) | Dữ liệu Contrast **không** có trong tệp sao lưu: giới hạn đã công bố |
| M8 | Hồi quy, toàn vẹn, hiệu năng, ổn định, bảo mật, tài liệu | báo cáo này, checklist, 3 test mới + 4 test bổ sung | xem §3 | xem §13 | — |

### Hành trình người học (bước IV) — test nào phủ bước nào

| Bước | Phủ bởi |
|---|---|
| 1–7 Mở app → tab Học → Contrast → Thư viện → tìm → So sánh → lý thuyết, ví dụ | `ContrastLabEndToEndTest.learnHub_home_library_compare_andBack_readingWritesNothing` |
| 8–15, 19–20 Bắt đầu, làm câu, nộp, phản hồi, bỏ qua, hoàn thành, tổng kết | `ContrastPracticeEndToEndTest.aWholeSession_fromCompareToSummary_schedulesOnce` |
| 9–12 Bốn dạng câu (Multiple Choice, Error Detection, Fill in the Blank, Context Selection) | `ContrastPracticeScreensTest` (mỗi dạng một test), `ContrastInsightsEndToEndTest` (phiên ôn gặp nhiều dạng) |
| 16–18 Thoát giữa phiên, mở lại app, tiếp tục | `ContrastPracticeEndToEndTest.anUnfinishedSession_survivesLeavingAndARestart_andResumesFromHome`; force-stop thật bằng `adb` (§7) |
| 21–24 Phân tích lỗi, hướng nhầm, Tiến độ, mastery | `ContrastInsightsEndToEndTest`, `ContrastInsightsTest` |
| 25–28 Tới thời điểm ôn, Ôn tập, phiên ôn, lịch kế tiếp | `ContrastInsightsEndToEndTest` (khối đã làm cách đây 4 ngày qua use case thật; app dùng đồng hồ thiết bị nên không chỉnh giờ hệ thống), `ContrastUseCasesTest` (đồng hồ `MutableClock`) |
| 29–30 Khởi động lại, dữ liệu còn | `ContrastPersistenceTest.learnerData_survivesARestart`, `ContrastContentImporterTest.aRestart_keepsInstalledContentAndProgress`, `ContrastBundledInstallTest.anUnchangedPackIsNotReinstalled_andARestartKeepsProgress` |

Không có một test duy nhất đi đủ 30 bước liền mạch; các bước được phủ bởi các test nối tiếp nhau trên cùng database thật của app. Bước 25 dùng dữ liệu "trong quá khứ" vì `AppContainer.clock` là đồng hồ thiết bị (không thay được trong test UI toàn app); các test domain dùng đồng hồ điều khiển được.

## 3. Kết quả kiểm thử

| Lệnh | Tổng | Đạt | Thất bại | Bỏ qua |
|---|---|---|---|---|
| `./gradlew :app:testDebugUnitTest` (JVM: domain, ArchitectureRulesTest, chấm điểm, phiên, SRS, mastery, analytics, tiến độ, ViewModel) | 590 | 590 | 0 | 0 |
| `python3 tools/content/contrast/test_contrast_tools.py` (schema, ngữ nghĩa, fingerprint, fixtures, so sánh bản trước, build) | 25 | 25 | 0 | 0 |
| `python3 tools/content/contrast/build_pack.py --check docs/contrast/contrast-pack.sample.json --strict` | — | ĐẠT: 0 ERROR, 5 WARNING đều đã xác nhận, 1 INFO (`LICENSE_UNVERIFIED`) | — | — |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` (toàn bộ: Room, migration, Compose UI, điều hướng, import, hành trình đầy đủ, hồi quy app cũ) | 454 | 451 | 1 | 2 |
| Chạy lại sau khi thêm 2 test importer và sửa chuỗi sao lưu: `ContrastContentImporterTest`, `ContrastM8IntegrityTest`, `BackupEndToEndTest` | 21 | 21 | 0 | 0 |
| `./gradlew :app:assembleDebug` | — | đạt | — | — |
| `./gradlew :app:lintDebug` | — | đạt: 0 lỗi, 29 cảnh báo đều có từ trước, không cảnh báo nào trong mã Contrast (cảnh báo chuỗi Contrast không dùng đã được xóa trong M8) | — | — |
| `./gradlew :app:assembleRelease` | — | đạt: `app-release-unsigned.apk` (chưa ký, vì chưa cấu hình ký; M8 không tạo khóa) | — | — |

- **Thất bại:** `HourPickerReachabilityTest.everyHourOfTheEndMenu_canBeReachedOnScreen` ("could not find any node … '21:00'"). Lỗi này có từ trước Contrast (xem §10) và không phải hồi quy do Contrast.
- **Bỏ qua theo thiết kế:** `MlKitTranslationDeviceTest.downloadsOnce_translatesOnTheDevice_andWorksOffline` và `firstUseOfSeveralPairsAtOnce_doesNotCrash`. Hai test này cần tải mô hình ML Kit qua mạng và chỉ chạy khi bật riêng.
- **Test mới trong M8:** `ContrastM8IntegrityTest` (6), `ContrastM8PerformanceTest` (2), `ContrastPracticeEndToEndTest.aConfigurationChange_duringPractice_keepsTheQuestionAndStoresEachAnswerOnce`, `ContrastContentImporterTest.anUpdateOfSeveralGroups_withOneWrongGroup_writesNoGroup` và `anApprovalWithoutAReviewer_isRefused`.
- **Chưa chạy:** TalkBack bằng tay; máy thật; Android API 26; bản release đã ký hoặc đã bật R8.
- **Kiểm tra APK:** cả bản debug lẫn release không có asset Contrast, PDF hay `.apkg`, và không có chuỗi dạng API key trong dex.

## 4. Data integrity

### Room v16

- So sánh `app/schemas/.../15.json` với `16.json`: 45 bảng cũ còn đủ, câu `CREATE` của từng bảng không đổi; thêm đúng 13 bảng `contrast_*`.
- Không có `fallbackToDestructiveMigration` trong mã nguồn. Migration 1→…→16 được kiểm bằng `N3DatabaseMigrationTest`; 15→16 với dữ liệu cũ bằng `ContrastMigrationTest`.
- Khóa ngoại chỉ có giữa các bảng **nội dung** (pack → group → item/note/example/question → option, `ON DELETE CASCADE`). Năm bảng **người học** (`contrast_sessions`, `contrast_session_items`, `contrast_attempts`, `contrast_progress`, `contrast_reviews`) không có khóa ngoại, nên thay hay gỡ nội dung không xóa lịch sử.

### Attempt, Progress, Review

| Kiểm tra | Bằng chứng |
|---|---|
| Mỗi câu lưu đúng một lần; gửi trùng không tạo attempt thứ hai | `ContrastPersistenceTest.recordAnswer_isIdempotent_andNeverSchedulesTwice`; M8: 12 lần gửi song song cùng request id → 1 attempt, 1 review, `review_count = 1` |
| Lưu revision câu hỏi, snapshot đáp án; cập nhật nội dung không đổi kết quả cũ | `ContrastContentImporterTest.contentUpdates_neverLoseOrRewriteLearnerHistory` (so từng byte bảng người học trước/sau) |
| SKIPPED không thành INCORRECT; PENDING không tạo attempt; UNAVAILABLE không bị tính | `ContrastAnalyticsRoomTest.sqlAggregates_agreeWithTheM1Analyzer_andSkipsPendingUnavailableNeverCountAsWrong`, `ContrastUseCasesTest` |
| Một khối lập lịch một lần mỗi phiên; review count không tăng hai lần | ràng buộc `UNIQUE(session_id, group_id)` + `ContrastPersistenceTest`, M8 test gửi trùng |
| Chỉ xem lý thuyết không tạo progress | `ContrastLabEndToEndTest…readingWritesNothing`, `ContrastViewModelsTest` |
| Cập nhật nội dung không reset progress; nhóm retired giữ lịch sử, không đến hạn | `ContrastContentImporterTest.contentUpdates…`, `ContrastPersistenceTest.aContentUpdate_keeps…RetiredGroupsKeepTheirHistory` |

### Transaction (lỗi chèn có chủ đích)

`ContrastM8IntegrityTest` tạo một trigger SQLite **chỉ trong database test** (`RAISE(ABORT)`) đúng giữa thao tác ghi, rồi so toàn bộ năm bảng người học trước/sau:

| Thao tác | Điểm lỗi | Kết quả |
|---|---|---|
| Bắt đầu phiên mới (bỏ phiên ACTIVE cũ + tạo phiên + câu) | khi chèn `contrast_session_items` | Không đổi gì; phiên cũ vẫn ACTIVE |
| Trả lời câu cuối của khối (attempt + review + progress + cập nhật phiên) | khi chèn `contrast_progress` | Không đổi gì; gửi lại cùng request id thì ghi và lập lịch đúng một lần |
| Trả lời một câu | khi cập nhật `contrast_sessions` | Attempt bị hoàn tác |
| Thêm câu hỏi lại vào phiên | khi cập nhật `contrast_sessions` | Không có câu nào được thêm |

Importer: `ContrastContentImporterTest.aDatabaseFailureInsideTheTransaction_rollsBack`.

## 5. Content validation & versioning; Regression

### 16 trường hợp nội dung bắt buộc

| # | Trường hợp | Bằng chứng |
|---|---|---|
| 1 | Gói hợp lệ | fixture `valid-sample`; `ContrastContentImporterTest.aValidPack_isImported…` |
| 2 | Sai schema | `missing-metadata`, `duplicate-ids`, `unsupported-question-type`; `structuralErrors_unknownFields_andAcceptedAnswers_areRefused` |
| 3 | Sai ngữ nghĩa | `missing-correct-answer`, `conflicting-answer`; `anyError_refusesTheWholePack…` |
| 4, 6 | Fingerprint đổi / Source CHANGED | `source-bad-fingerprint`, `fingerprint-cases.json`; `RoomContrastSourceResolverTest` |
| 5 | Source MISSING | `source-missing`; `RoomContrastSourceResolverTest`; `ContrastBundledInstallTest.aFailingSourcePack_skipsContrast…` |
| 7 | Source AMBIGUOUS | `source-ambiguous`, `sources-ambiguous.json` |
| 8 | Đổi item nhưng giữ group ID | `items-changed-same-group-id`; `revisionsAndItemsAreComparedWithTheInstalledContent` |
| 9 | Đổi nội dung không tăng revision | `revision-not-incremented`; như trên |
| 10 | Import bản cũ hơn | `contentUpdates_neverLoseOrRewriteLearnerHistory` (bị từ chối, không đổi gì) |
| 11 | Import lại cùng phiên bản | `importingTheSameVersionAgain_isUpToDate_andWritesNothing`, `content-version-same-as-previous` |
| 12 | Cập nhật câu hỏi đã có attempt | `answer-changed-same-id`; `contentUpdates_neverLoseOrRewriteLearnerHistory` |
| 13 | Nội dung chưa duyệt | `approvalAndPublishingRules`; `test_build_refuses_unapproved_drafts_and_writes_nothing` |
| 14 | Reviewer không hợp lệ | `test_a_release_needs_a_registered_reviewer` (tooling); M8 thêm `anApprovalWithoutAReviewer_isRefused` (app) |
| 15 | `approvedRevision` không khớp | `approval-for-another-revision`; `approvalAndPublishingRules` |
| 16 | Cập nhật nhiều nhóm, một nhóm lỗi | M8 thêm `anUpdateOfSeveralGroups_withOneWrongGroup_writesNoGroup` |

40 mã lỗi (`ContrastIssueCode`) dùng chung giữa Python và Kotlin; `test_expectations_use_known_codes_only` và `ContrastSharedFixturesTest` chạy cùng 54 tệp fixture.

### SRS & logic học (bước VII)

Đều có trong unit test M1/M6/M7 và chạy lại trong M8: thang 1/3/7/14/30/60 ngày theo ngày lịch ở múi giờ người học, đổi múi giờ, qua nửa đêm, đổi giờ mùa hè (Berlin: 3 ngày lịch = 73 giờ), một câu đúng chưa đủ mastered, nhóm ôn không tính vào giới hạn 3 nhóm mới/ngày, tối đa 10 nhóm/phiên, 3 câu mỗi khối khi đủ câu hợp lệ, không lập lịch hai lần (`ContrastSchedulerTest`, `ContrastSessionPlannerTest`, `ContrastUseCasesTest`, `ContrastInsightsTest`).

### Hồi quy N3 Sensei cũ (bước IX)

Toàn bộ test thiết bị của các chức năng cũ được chạy lại cùng suite (§3): Trang chủ (`DashboardEndToEndTest`), Learn Hub, Từ vựng / Ngữ pháp / Kanji / Flashcard (`EndToEndTest`, `FlashcardAnswerTest`, `FlashcardKanjiWordsTest`, `KanjiN2EndToEndTest`, `LearningUxTest`), Review SRS cũ (`ReviewByTypeUiTest`), Đề thi, lượt làm, sổ lỗi (`feature/exam/*`), Từ của tôi (`BookmarkUiTest`, user content), Thống kê (`StatisticsEndToEndTest`), Cài đặt, Sao lưu (`BackupRepositoryTest`, `BackupEndToEndTest`), Đồng bộ Android (`data/sync/*`), Nhắc học (`app/reminder/*`, `ReminderLessonsUiTest`). Mã của các chức năng này không bị M1–M8 sửa, ngoài điểm vào Contrast ở Learn Hub và chuỗi mô tả phạm vi sao lưu.

## 6. Performance

`ContrastM8PerformanceTest` chạy trên emulator Pixel 8 API 36 với dữ liệu tổng hợp chỉ dùng cho test, nằm trong một tệp database riêng bị xóa sau test:
- Gói nội dung được tạo bằng cách nhân bản 3 nhóm mẫu dưới id mới, rồi nhập qua importer thật, đối chiếu với các gói nguồn thật.
- Attempt được chia thành các phiên 20 câu.
- 60 % số nhóm có lịch ôn, một nửa trong số đó đang đến hạn.
- Thời gian mỗi màn được đo từ lúc tạo ViewModel đến trạng thái tải xong đầu tiên.

| Thao tác (ms) | 99 nhóm / 10 000 attempt | 999 nhóm / 100 000 attempt |
|---|---|---|
| Nhập gói (JSON 539 KB / 5,3 MB) | 698 | 4 040 |
| Contrast Home | 385 | 2 292 |
| Thư viện (mọi dòng) | 293 | 1 696 |
| Tìm kiếm trong Thư viện | 104 | 590 |
| So sánh (chi tiết nhóm + nguồn) | 13 | 4 |
| Phân tích lỗi | 130 | 611 |
| Tiến độ | 168 | 1 154 |
| Danh sách ôn | 191 | 1 442 |
| Bắt đầu phiên | 25 | 56 |
| Nộp một đáp án | 11 | 9 |
| Bộ nhớ heap tăng thêm khi giữ danh sách Thư viện (xấp xỉ) | ~156 KB | ~1,4 MB |

Số đo các truy vấn tổng hợp riêng lẻ của M7 vẫn có trong `ContrastAnalyticsRoomTest`; ở 1 000 nhóm / 100 000 attempt chúng mất 15–271 ms.

**Nhận xét:**
- Bắt đầu phiên và nộp đáp án độc lập với lượng dữ liệu.
- Ở 999 nhóm, Home, Thư viện, Tiến độ và Ôn tập mất 1,2–2,3 s. Thời gian chủ yếu nằm ở bước tính mastery theo quy tắc M1 cho từng nhóm, mỗi nhóm một truy vấn attempt; M8 không đổi quy tắc này.
- Ở quy mô thực tế của gói N3 (dưới vài trăm nhóm), mọi màn tải dưới 0,4 s.
- Không thêm index, không đổi schema.

## 7. Stability

| Kiểm tra | Cách làm | Kết quả |
|---|---|---|
| Không có Internet, chế độ máy bay | `adb shell cmd connectivity airplane-mode enable`, force-stop rồi mở app | Mở bình thường, không crash. Contrast không dùng mạng (rà mã) |
| Force stop và khởi động lại | `am force-stop` rồi `am start` | Mở bình thường. Phiên dở được giữ: `ContrastPracticeEndToEndTest.anUnfinishedSession_survivesLeavingAndARestart…` |
| Process death | Đưa app về nền, `am kill`, rồi mở lại (khởi động lạnh) | Không crash. Bộ lọc Thư viện và Phân tích lỗi được lưu trong `SavedStateHandle`; việc giữ bộ lọc khi tạo lại ViewModel có unit test (`ContrastInsightsTest`) |
| Đổi cấu hình (xoay màn hình, giao diện tối, cỡ chữ) | `ActivityScenario.recreate()` giữa phiên luyện | Câu hỏi được giữ, mỗi câu trả lời lưu đúng một lần (test mới) |
| Thoát khi đang nộp, gửi trùng nhiều lần | Gửi song song cùng request id; lỗi chèn giữa transaction (tương đương bị ngắt khi đang ghi) | Một attempt; transaction hoàn tác trọn vẹn (§4). Chưa thử giết tiến trình đúng lúc đang ghi |
| Ghi database thất bại | Trigger lỗi trong DB test | Không có trạng thái ghi dở (§4) |
| Nhập gói lỗi | Gói hỏng hoặc gói nguồn lỗi | Toàn bộ gói bị từ chối. Các gói khác vẫn cài và app vẫn chạy (`ContrastBundledInstallTest.aFailingSourcePack_skipsContrast…`) |
| Không có câu hỏi hợp lệ; nội dung đã ngừng; nguồn không còn | Unit test và test Room | Không lập lịch; nhóm ngừng không đến hạn; nguồn hiện "không còn" (`ContrastInsightsTest`, `RoomContrastSourceResolverTest`) |
| Không có gói nào | App không mang gói Contrast | Contrast Lab báo "chưa có nội dung đã duyệt", không crash (`ContrastLabEndToEndTest.withoutAReviewedPack_theHomeSaysSo`) |
| Bộ nhớ hạn chế | — | Chưa thử (emulator không cấu hình giới hạn); chỉ đo heap ở §6 |

Logcat sau các thao tác trên: không có `FATAL EXCEPTION`, crash buffer trống.

## 8. Security / Privacy

- **API key, AI:** không có chuỗi dạng khóa (`sk-…`, `AIza…`, `sk-ant-…`) trong `app/src/main`; mã Contrast (domain, data, feature, importer, DAO) không có HTTP, URL, client AI hay `Log.*`.
- **Mạng:** Contrast chỉ đọc/ghi Room. Mã đồng bộ (`data/sync`) không tham chiếu Contrast; dữ liệu Contrast không được gửi đi.
- **Quyền Android:** manifest không đổi so với commit trước Contrast (`e194a44`). Quyền hiện có (`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`) đều có từ trước.
- **Dependencies:** `app/build.gradle.kts` và `gradle/libs.versions.toml` không đổi so với `e194a44`.
- **Sao lưu đám mây:** vẫn tắt bằng `data_extraction_rules.xml` / `backup_rules.xml`; chuyển máy trực tiếp vẫn chép database (kể cả Contrast).
- **Phát hiện ngoài phạm vi (không sửa):** chuỗi `privacy_data_on_device` nói app "không có quyền truy cập Internet", trong khi manifest có `INTERNET` từ trước Contrast (dùng cho đồng bộ và tải mô hình dịch). Câu này cần chủ dự án sửa cho đúng (P2).

## 9. Các lỗi đã sửa trong M8

| # | Lỗi | Mức | Sửa |
|---|---|---|---|
| 1 | Contrast Home nạp **toàn bộ** attempt vào bộ nhớ hai lần (tìm nhóm vừa làm, và bộ phân tích M1 cho "Hay nhầm") | P2 (hiệu năng) | Dùng truy vấn tổng hợp M7 (`lastAnsweredGroup`, `directions`); Home cập nhật cả khi có câu trả lời mới (`observeChanges`). `lastAnsweredGroup` chỉ xét nhóm đang phát hành (như Home đã làm). Không đổi schema |
| 2 | D6 yêu cầu ghi rõ trong UI rằng sao lưu chưa gồm Contrast, nhưng màn Sao lưu không nói | P2 (minh bạch dữ liệu) | Thêm câu vào `backup_scope`; thêm dòng vào `docs/BACKUP.md` |
| 3 | Thiếu test: rollback khi bắt đầu phiên / thêm câu, gửi trùng song song, sao lưu với dữ liệu Contrast, đổi cấu hình giữa phiên, cập nhật nhiều nhóm có một nhóm lỗi, người duyệt rỗng, hiệu năng các màn | — | `ContrastM8IntegrityTest` (6), `ContrastM8PerformanceTest` (2), `ContrastPracticeEndToEndTest` (+1), `ContrastContentImporterTest` (+2) |

## 10. Các lỗi chưa giải quyết

| # | Lỗi | Mức | Lý do chưa sửa |
|---|---|---|---|
| 1 | `HourPickerReachabilityTest.everyHourOfTheEndMenu_canBeReachedOnScreen` thất bại | P2 | Có từ trước Contrast: test và `ReminderSettingsScreen.kt` không đổi kể từ commit `e194a44` (trước M1), và `CONTRAST_LAB_AUDIT.md` đã ghi nhận. Ngoài phạm vi; không nới test |
| 2 | Chuỗi quyền riêng tư nói app không có quyền Internet | P2 | Có từ trước; nội dung chính sách do chủ dự án quyết |
| 3 | Thư viện, Home, Tiến độ, Ôn tập tính mastery từng nhóm (mỗi nhóm một truy vấn attempt) | P3 | Đúng quy tắc M1; 99 nhóm < 0,4 s, 999 nhóm 1,2–2,3 s. Chỉ đáng tối ưu nếu nội dung vượt vài trăm nhóm |

## 11. Release blockers

| Mức | Blocker |
|---|---|
| P0 | Không có |
| P1 | Không có lỗi kỹ thuật P1. **Điều kiện chặn phát hành Contrast:** chưa có gói nội dung được duyệt thật (gói mẫu tự duyệt `reviewer-01`, `aiAssisted: true`, `licenseVerified: false`) |
| P2 | Lỗi HourPicker (cũ); chuỗi quyền riêng tư sai; chưa thử TalkBack bằng tay và trên máy thật |
| P3 | Mastery tính theo từng nhóm khi nội dung rất lớn; chưa cấu hình ký và R8 cho bản phát hành (bước phân phối, không phải lỗi) |

## 12. Hạn chế hiện tại

- Dữ liệu Contrast **không** có trong tệp sao lưu (D6). Đã ghi trong app và `docs/BACKUP.md`; phải ghi trong ghi chú phát hành.
- App không mang gói Contrast nào; Contrast Lab chỉ hiện trạng thái "chưa có nội dung đã duyệt".
- Điền chỗ trống là chọn đáp án (theo contract), không gõ chữ.
- Xu hướng độ chính xác hiện bằng chữ, không có biểu đồ.
- Bộ test UI toàn app dùng đồng hồ thiết bị; các bước "tới ngày ôn" được dựng bằng dữ liệu quá khứ qua use case thật.
- Mọi kiểm thử thiết bị chạy trên một emulator (Pixel 8, API 36); chưa thử API 26 hay máy thật.

## 13. Kết luận nghiệm thu

**M8 COMPLETE — CONTENT APPROVAL PENDING.**

- **Về kỹ thuật:** đạt, có bằng chứng. Các yêu cầu M0–M7 đều VERIFIED.
  - Full integration chạy trên database thật của app.
  - Không mất dữ liệu: migration không phá hủy; các thao tác ghi đều hoàn tác trọn vẹn khi lỗi; khôi phục sao lưu không đụng tới dữ liệu Contrast.
  - Không có hồi quy do Contrast. Lỗi duy nhất trong suite thiết bị (HourPicker) có từ trước và đã ghi nhận.
- **Về nội dung:** chưa sẵn sàng. App không mang gói Contrast nào. Gói mẫu do chính tác giả tự duyệt (`reviewer-01`), có hỗ trợ AI và chưa xác minh giấy phép.
- Vì vậy sản phẩm **chưa** RELEASE READY. M8 không phát hành, không phân phối ứng dụng.
- **Điều kiện chủ dự án cần hoàn tất:** xem `CONTRAST_LAB_RELEASE_CHECKLIST.md` (C1–C3, T10–T11, Q5–Q7, R3, R5).
