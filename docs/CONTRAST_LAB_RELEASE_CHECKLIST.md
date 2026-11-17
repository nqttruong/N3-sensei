# Contrast Lab — Checklist phát hành

Kết quả nghiệm thu chi tiết ở [CONTRAST_LAB_M8_REPORT.md](CONTRAST_LAB_M8_REPORT.md). Cột "Trạng thái" ghi đúng tình trạng tại thời điểm M8 (2026-11-17), không ghi theo mong muốn.

Ký hiệu: ✅ đạt, có bằng chứng · ⏳ chưa xong, cần chủ dự án · ❌ chưa đạt · — không áp dụng.

## 1. Technical readiness

| # | Điều kiện | Trạng thái | Bằng chứng |
|---|---|---|---|
| T1 | Room v16, migration 1→…→16 không phá hủy, 45 bảng cũ giữ nguyên, 13 bảng Contrast | ✅ | `N3DatabaseMigrationTest`, `ContrastMigrationTest`, so sánh `schemas/15.json` ↔ `16.json` |
| T2 | Bảng dữ liệu người học Contrast không có khóa ngoại (cập nhật nội dung không xóa lịch sử) | ✅ | `schemas/16.json`; `ContrastContentImporterTest.contentUpdates_neverLoseOrRewriteLearnerHistory` |
| T3 | Mọi ghi dữ liệu người học là một transaction, lỗi giữa chừng không để lại trạng thái dở | ✅ | `ContrastM8IntegrityTest` (lỗi chèn bằng trigger), `ContrastPersistenceTest` |
| T4 | Gửi trùng không tạo attempt hay lịch ôn thứ hai | ✅ | `ContrastM8IntegrityTest.theSameAnswerSubmittedConcurrently…` (12 lần song song) |
| T5 | Năm tab chính, Contrast nằm trong tab Học | ✅ | `AppScreenTest`, `ContrastNavigationTest`, các test E2E |
| T6 | SRS cũ, sổ lỗi đề thi, `ItemType` không bị đụng | ✅ | E2E so sánh `learning_progress` / `review_records`; `ArchitectureRulesTest` |
| T7 | Không có AI client, API key, quyền Android mới | ✅ | Rà mã và manifest (báo cáo §8) |
| T8 | `assembleDebug`, `lintDebug`, unit test, test thiết bị | ✅ (trừ 1 lỗi cũ) | Báo cáo §3 |
| T9 | Bản release build được (chưa ký) | ✅ | `assembleRelease` → APK unsigned (báo cáo §3) |
| T10 | Ký phát hành (keystore, cấu hình signing) | ⏳ | Chưa cấu hình, cố ý (README). Chủ dự án tạo và giữ khóa |
| T11 | R8/minify cho bản release | ⏳ | `isMinifyEnabled = false`. Nếu bật, phải chạy lại toàn bộ test trên bản đã minify |

## 2. Content readiness

| # | Điều kiện | Trạng thái | Ghi chú |
|---|---|---|---|
| C1 | Có gói Contrast cần phát hành | ❌ | App hiện **không** mang gói Contrast nào (`ContrastBundledInstallTest`). Gói mẫu chỉ có ở tài liệu và assets của test |
| C2 | Gói được duyệt bởi người duyệt thật, đã đăng ký, khác tác giả | ❌ | Gói mẫu: `authoredBy = approvedBy = reviewer-01` (tự duyệt), nội dung có `aiAssisted: true` |
| C3 | Giấy phép nội dung đã xác minh | ❌ | `license.licenseVerified: false` |
| C4 | `build_pack.py --check … --strict` đạt, không còn cảnh báo chưa xác nhận | ✅ cho gói mẫu | Cần chạy lại trên gói phát hành thật, kèm `--previous` khi đã có bản trước |
| C5 | Dấu vân tay nguồn (SHA-256) khớp gói nguồn đang phát hành | ✅ cho gói mẫu | `test_sample_fingerprints_match_the_real_source_packs` |

Không được đổi `reviewer-01` thành tên người thật, không được đặt `licenseVerified: true` khi chưa có người duyệt và giấy phép thật.

## 3. Testing readiness

| # | Điều kiện | Trạng thái |
|---|---|---|
| Q1 | Unit test (JVM) | ✅ |
| Q2 | Test Python (tooling, schema, fixtures dùng chung) | ✅ |
| Q3 | Test thiết bị trên emulator | ✅, trừ `HourPickerReachabilityTest` (lỗi cũ, ngoài Contrast) và 2 test ML Kit bỏ qua theo thiết kế |
| Q4 | Hiệu năng 100 / 1 000 nhóm, 10 000 / 100 000 attempt | ✅ đã đo (báo cáo §6) |
| Q5 | TalkBack thử bằng tay trên máy thật | ⏳ | Chỉ có kiểm tra bằng cây ngữ nghĩa Compose, chưa thử tay |
| Q6 | Thử trên máy thật (nhiều kích thước màn hình, Android cũ API 26) | ⏳ | M8 chỉ chạy trên emulator Pixel 8 API 36 |
| Q7 | Nâng cấp từ bản đã cài thật (dữ liệu thật v15 → v16) | ⏳ | Đã có test migration bằng dữ liệu tổng hợp; nên thử một lần với bản cài của người dùng thử |

## 4. Release readiness

| # | Điều kiện | Trạng thái |
|---|---|---|
| R1 | Technical readiness đạt | ✅ (T10, T11 là bước phân phối) |
| R2 | Content readiness đạt | ❌ (C1–C3) |
| R3 | Giới hạn sao lưu được công bố | ✅ trong app (màn Sao lưu) và `docs/BACKUP.md`; ⏳ cần đưa vào ghi chú phát hành |
| R4 | Không còn release blocker P0/P1 | ✅ về kỹ thuật; nội dung chưa duyệt là điều kiện chặn của bản phát hành có Contrast |
| R5 | Chủ dự án phê duyệt phát hành | ⏳ |

### Ghi chú phát hành bắt buộc (khi phát hành có Contrast)

> Dữ liệu Contrast Lab (phiên luyện, câu trả lời, lịch ôn, tiến độ) **chưa có trong tệp sao lưu** của Cài đặt → Sao lưu và khôi phục. Khôi phục một tệp sao lưu không xóa và không thay đổi dữ liệu Contrast đang có. Khi chuyển máy trực tiếp (Android device-transfer) dữ liệu vẫn được chép kèm.

### Phương án phát hành không có Contrast

Vì app chưa mang gói Contrast, một bản phát hành ngay bây giờ chỉ hiện Contrast Lab ở trạng thái "chưa có nội dung đã duyệt" (`ContrastLabEndToEndTest.withoutAReviewedPack_theHomeSaysSo`). Chủ dự án cần quyết định có muốn hiện mục này khi chưa có nội dung hay không; M8 không tự ẩn hay thêm cờ tính năng.
