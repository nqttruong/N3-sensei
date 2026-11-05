# Báo cáo QA — Module JLPT Exam (N3 Sensei)

Ngày: 05/11/2026. Phạm vi: PROMPT 02–10 (dữ liệu thi, engine, giao diện làm bài, chấm điểm, phân tích lỗi, sổ lỗi sai, ôn lỗi theo lịch, dashboard).

Mọi kết quả dưới đây là từ lần chạy thật trên **emulator Pixel 8 (API 36)**. Ứng dụng **chưa** được thử trên điện thoại thật trong vòng này: test thiết bị gỡ cài đặt app và xóa dữ liệu người học, nên không chạy trên máy thật.

## 1. Kết quả build và test (lần chạy cuối)

Lệnh: `ANDROID_SERIAL=emulator-5554 ./gradlew clean assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`

| Hạng mục | Kết quả |
|---|---|
| Build debug | Thành công |
| Unit test (JVM) | **358/358** đạt |
| Test thiết bị (emulator) | **290/290** đạt, 0 bỏ qua (49 class) |
| Lint | 0 lỗi, 25 cảnh báo (không có cảnh báo mới; cảnh báo cũ có từ trước module thi) |

Test thiết bị gồm toàn bộ test cũ (từ vựng, flashcard, kanji, ngữ pháp, tìm kiếm, thống kê, nhắc học, widget, sao lưu, migration…), nên các chức năng cũ được kiểm lại cùng lúc.

## 2. Chức năng đã hoàn thành và test chứng minh

| # | Luồng | Test kiểm tra |
|---|---|---|
| 1 | Mở danh sách đề, lọc N5–N1 / đề tổng hợp / kỹ năng | `ExamUiEndToEndTest`, `ExamEngineTest`, `ExamSkillTest` |
| 2 | Xem thông tin đề, quy định | `ExamUiEndToEndTest` |
| 3 | Bắt đầu thi | `ExamUiEndToEndTest`, `ExamSessionViewModelTest` |
| 4 | Chọn và đổi đáp án | `ExamEngineTest`, `ExamSessionLayoutTest`, `ExamUiEndToEndTest` |
| 5 | Đánh dấu câu cần xem lại | như trên |
| 6 | Chuyển câu (trước/sau, danh sách câu, bảng câu) | như trên |
| 7 | Thoát và khôi phục (xoay màn hình, tái tạo Activity, tiến trình bị hủy) | `ExamSessionViewModelTest`, `ExamUiEndToEndTest` |
| 8 | Hết giờ tự nộp (cả khi app đang đóng) | `ExamEngineTest`, `ExamSessionViewModelTest`, `ExamUiEndToEndTest`, `ExamTimerTest` |
| 9 | Nộp tay, xác nhận, cảnh báo câu bỏ trống; nộp nhiều lần chỉ ra một kết quả | `ExamEngineTest` (8 lệnh nộp đồng thời), `ExamUiEndToEndTest` |
| 10 | Kết quả: điểm luyện tập, theo kỹ năng, so sánh lần trước, lịch sử | `ExamResultTest`, `ExamScoringTest`, `ExamUiEndToEndTest` |
| 11 | Phân tích lỗi (loại lỗi, căn cứ, kiến thức, khắc phục) | `ErrorAnalyzerTest`, `MistakeAnalysisManagerTest`, `ExamUiEndToEndTest` |
| 12 | Sổ lỗi sai: tìm kiếm, lọc, chi tiết, ghi chú, đánh dấu, "đã hiểu" | `ErrorNotebookTest`, `ErrorRepositoryTest`, `ErrorNotebookUiTest` |
| 13 | Ôn lỗi theo lịch (1-3-7-14-30 ngày), câu cũ / câu khác cùng kiến thức | `ErrorReviewTest`, `ErrorReviewSessionManagerTest`, `ErrorNotebookUiTest` |
| 14 | Cập nhật mức thành thạo (lên khi đúng đúng hạn, tụt 2 bậc khi sai, cần câu khác để thành thạo) | `ErrorReviewTest`, `ErrorRepositoryTest`, `ErrorNotebookUiTest` |
| 15 | Dashboard: tổng quan, kỹ năng, phân tích lỗi, tiến bộ, kế hoạch hôm nay | `ExamStatisticsTest`, `DailyPlannerTest`, `ExamDashboardManagerTest`, `ExamDashboardUiTest` |
| — | Nhắc ôn lỗi (tối đa 1 thông báo/ngày, không trùng, tự gỡ, mở thẳng phiên ôn) | `ErrorReviewReminderTest`, `ErrorReminderInstrumentedTest`, `ExamStatesUiTest` (deep link) |

## 3. Kiểm thử dữ liệu

| Yêu cầu | Cách kiểm | Kết quả |
|---|---|---|
| Không mất lịch sử thi | Sửa và xóa đề sau khi thi, đóng và mở lại database (`ExamRepositoryTest`, `ExamResultTest`) | Đạt |
| Không ghi đè lần thi cũ | `ExamDataIntegrityTest`: kết quả từng câu của mỗi lần thi được chụp lại ngay sau khi nộp và so khớp sau **mọi** bước tiếp theo | Đạt |
| Không tạo lỗi trùng | Chỉ mục unique trên `question_id`; integrity test kiểm "một mục mỗi câu" và "số lần sai = số lần thi sai câu đó" | Đạt |
| Không có ReviewItem mồ côi | Khóa ngoại CASCADE; integrity test chạy `PRAGMA foreign_key_check` và LEFT JOIN tìm bản ghi mồ côi sau mỗi bước | Đạt |
| Không tính điểm sai | Integrity test: điểm = số câu đúng đã chấm, tổng câu = số câu đã chấm, mọi đáp án được chấm lại khớp kết quả lưu | Đạt |
| Không tạo lịch ôn không hợp lệ | Integrity test: khoảng ôn nằm trên thang, bậc trong 0–5, số lần đúng ≤ số lần ôn, mức thành thạo = bậc, trạng thái thành thạo khớp giữa hai bảng, lỗi đang mở không đến hạn trước lần sai cuối | Đạt |
| Migration | `N3DatabaseMigrationTest` (14 test): mọi bước 1→13, kèm dữ liệu cho từng bước 8→9 … 12→13 và kiểm tra khóa ngoại | Đạt |

`ExamDataIntegrityTest` chạy 3 chuỗi × 40 thao tác ngẫu nhiên: làm đề, bỏ trống, nộp tay, hết giờ, bỏ dở, nộp hai lần, ôn đúng hoặc sai, sớm hoặc trễ, lưu lại đề, xóa lỗi. Tất cả bất biến được kiểm tra sau từng bước.

## 4. Kiểm thử UI/UX

| Hạng mục | Kiểm | Ghi chú |
|---|---|---|
| Màn hình nhỏ/lớn | `ExamSessionLayoutTest`: 320×568, 360×640, 411×820 dp, chữ phóng 1,5 lần | Header, đồng hồ, nút nộp, lựa chọn, nút trước/sau luôn nằm trong màn hình |
| Dark Mode | Test sáng/tối ở trên; `ExamUiEndToEndTest` hết giờ ở chế độ tối; `ExamDashboardUiTest` chế độ tối | Ngoài ra đã chụp màn hình thật trên emulator ở cả hai chế độ để kiểm tra bằng mắt |
| Điều hướng | Các test end-to-end đi qua Back nhiều lần | Lỗi tab không sáng ở dashboard đã sửa (mục 6) |
| Loading / Empty / Error | `ExamStatesUiTest` (danh sách đề, sổ lỗi sai, thẻ Ôn tập, phiên ôn khi không có gì đến hạn); `ExamErrorStateUiTest` (lỗi đọc dữ liệu → thông báo lỗi và nút "Thử lại", không crash) | Mọi màn hình thi dùng chung `UiStateContent` |
| Nút và thao tác | Mỗi nút chính được bấm trong ít nhất một test end-to-end | — |
| App bị đóng đột ngột | `ExamSessionViewModelTest`, `ExamEngineTest`: đóng/mở lại database, giữ `SavedStateHandle`, hết giờ khi app đóng | — |

## 5. Kiểm thử hiệu năng

`ExamModulePerformanceTest`: 200 đề × 30 câu (6 000 câu, 1 500 mục kiến thức), 300 lần thi đã nộp (9 000 câu đã chấm), sổ lỗi khoảng vài nghìn mục. Đây là dữ liệu lớn hơn nhiều so với một người học thật. Thời gian đo trên emulator:

| Thao tác | Trước khi sửa | Sau khi sửa |
|---|---|---|
| Danh sách 200 đề | 22 ms | 23 ms |
| Lịch sử 9 000 câu đã chấm | 285 ms | 260–290 ms |
| Sổ lỗi sai (toàn bộ) | 99 ms | 92 ms |
| Tìm kiếm trong sổ lỗi | 158 ms | 151 ms |
| Nhóm lỗi phổ biến | **3 914 ms** | **5 ms** |
| Phiên ôn lỗi đến hạn | **31 443 ms** | **443–470 ms** |
| Dashboard | (test dừng trước khi đo) | 509–517 ms |
| Độ trễ lớn nhất của main thread khi chạy dashboard, phiên ôn và tìm kiếm | (chưa đo) | 156 ms (ngưỡng 250 ms) |

Truy vấn nặng dùng index (`EXPLAIN QUERY PLAN` được kiểm trong test). Chuyển câu chỉ là cập nhật trạng thái trong bộ nhớ; việc ghi vào database chạy nền. Đồng hồ đếm ngược tính lại từ giờ hệ thống mỗi giây, không cộng dồn.

## 6. Lỗi đã phát hiện và sửa trong vòng QA

| # | Lỗi | Nguyên nhân | Sửa (tầng) | Test hồi quy |
|---|---|---|---|---|
| 1 | Mở phiên ôn mất 31 giây với ngân hàng câu lớn | Với mỗi lỗi, duyệt toàn bộ ngân hàng câu hỏi | Lập chỉ mục ngân hàng theo mục kiến thức (`ReviewQuestionPicker.PoolIndex`, domain) | `ErrorReviewTest.theIndexedPool_givesTheSameCandidatesAsCheckingEveryQuestion` (so với cách cũ trên dữ liệu ngẫu nhiên) + `ExamModulePerformanceTest` |
| 2 | Thống kê nhóm lỗi mất 3,9 giây | Chạy toàn bộ bộ phân tích (gồm phần lỗi lặp, O(n²)) cho từng câu sai | Tách hàm `categorize` chỉ cần số lần đúng trước đó; tính một lượt (domain) | `ExamStatisticsTest.categories_matchTheFullAnalyzer_onRandomHistories` + test hiệu năng |
| 3 | Tính toán nặng chạy trên main thread (nguy cơ giật/ANR khi dữ liệu lớn) | ViewModel gọi các hàm tính toán trên main dispatcher | Giải mã JSON ở tầng data (`Dispatchers.Default`, theo quy ước có sẵn); tính toán của dashboard, phiên ôn, phân tích chuyển sang `Dispatchers.Default` ở domain; lọc sổ lỗi `flowOn(Default)` | `ExamModulePerformanceTest.heavyWork_startedOnTheMainThread_doesNotBlockIt` |
| 4 | Ô tìm kiếm sổ lỗi đôi khi mất chữ vừa gõ (do sửa lỗi #3 gây ra; test bắt được, khoảng 3/5 lần) | Ô nhập lấy giá trị từ trạng thái được tính bất đồng bộ nên bị kéo về giá trị cũ | ViewModel đưa từ khóa ra thành luồng riêng, đồng bộ, nối thẳng vào ô nhập (feature) | `ErrorNotebookUiTest` đạt 5/5 lần chạy liên tiếp và trong lượt chạy đầy đủ |

Lỗi đã sửa ở các prompt trước (để tham khảo):
- Dashboard nằm ngoài graph tab Học nên không tab nào sáng.
- Thẻ trong sổ lỗi bị tô màu hàng loạt.
- Nút "Lưu ghi chú" bị bàn phím che (lỗi trong test).
- Các chuỗi không dùng mà lint báo.
- Test chập chờn khi chờ tab bài học.

## 7. Lỗi còn tồn tại

Hiện **không có lỗi nào đã biết** sau lượt chạy cuối. Một số điểm cần lưu ý:
- Đồng hồ thi dựa trên giờ của máy, nên người dùng chỉnh giờ máy có thể ảnh hưởng. Thời gian còn lại không bao giờ vượt giới hạn.
- Kế hoạch học gom lỗi theo kỹ năng. Với lỗi không xác định được kỹ năng, nút "Bắt đầu" mở phiên gồm N lỗi ưu tiên nhất trong mọi kỹ năng, không đúng hoàn toàn tập lỗi của nhiệm vụ.

## 8. Phần chưa hoàn thiện

1. **Chưa có đề thi trong app, chưa có chức năng nhập gói đề.** Mọi đề trong test là dữ liệu kiểm thử. Với người dùng thật, danh sách đề, sổ lỗi sai và dashboard sẽ trống. Nguồn đề cần bạn quyết định (lưu ý bản quyền đề JLPT chính thức).
2. **Audio:** chưa phát được. Câu nghe hiện thông báo rõ, và kế hoạch học không giao bài nghe.
3. **Hình ảnh trong câu hỏi:** schema chưa có trường hình ảnh.
4. **Sao lưu/khôi phục chưa gồm dữ liệu thi** (lần thi, sổ lỗi sai, lịch ôn). Khôi phục bản sao lưu không xóa dữ liệu thi (chỉ gộp), nhưng đổi máy thì dữ liệu thi không đi theo.
5. Giờ riêng cho từng phần thi đã lưu nhưng chưa dùng; chỉ có một đồng hồ cho cả đề.
6. Thời gian làm từng câu chỉ được tính khi chọn đáp án.
7. Lỗi chỉ được "thành thạo" khi đúng ít nhất một câu khác về cùng kiến thức. Khi ngân hàng đề chưa có câu như vậy, lỗi sẽ không bao giờ thành thạo; màn hình nói rõ điều này.
8. Chưa thử trên điện thoại thật; chưa kiểm thử riêng tablet hoặc màn hình ngang (bố cục giới hạn chiều rộng 720 dp như toàn app).
9. Accessibility: nhãn TalkBack cho đồng hồ, ô số câu, biểu đồ đã có, nhưng chưa có test TalkBack tự động riêng cho các màn thi.

## 9. Đánh giá khả năng phát hành

- **Về kỹ thuật:** module ổn định trên emulator. Toàn bộ 358 unit test và 290 test thiết bị đạt; dữ liệu được bảo toàn qua migration và qua các chuỗi thao tác ngẫu nhiên; hiệu năng chấp nhận được với dữ liệu lớn hơn nhiều so với thực tế.
- **Về sản phẩm:** **chưa nên phát hành module thi cho người dùng** cho tới khi có nguồn đề hợp lệ và chức năng nhập gói đề (mục 8.1). Không có đề thì cả module trống. Nên có thêm sao lưu dữ liệu thi (mục 8.4) và một vòng thử trên điện thoại thật trước khi phát hành.
- Phát hành bản hiện tại thì phần còn lại của app (học từ vựng, Kanji, ngữ pháp, ôn tập, thống kê) không bị ảnh hưởng: toàn bộ test cũ vẫn đạt. Các màn thi hiện trạng thái rỗng rõ ràng, không có đề giả.
