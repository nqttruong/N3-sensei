# Contrast Lab: phân tích lỗi, ôn tập thông minh, tiến độ (M7)

Trạng thái: đã triển khai (2026-11-17). Kết quả test ở mục 11.

## 1. Kiến trúc

```
ContrastErrorsScreen   ── ContrastErrorsViewModel   ── GetContrastErrorAnalysis  ──┐
ContrastReviewScreen   ── ContrastReviewViewModel   ── GetContrastReviewQueue    ──┼─ ContrastAnalyticsRepository (SQL tổng hợp, chỉ đọc)
ContrastProgressScreen ── ContrastProgressViewModel ── GetContrastProgressReport ──┘  ContrastProgressRepository / ContrastPracticeRepository (M3)
                                                        └─ GetContrastProgress / ContrastMasteryEvaluator (M1, không đổi)
```

- **Repository mới:** `ContrastAnalyticsRepository`, chỉ đọc. Bản Room là `OfflineContrastAnalyticsRepository`: mỗi chỉ số là một truy vấn SQL trên `contrast_attempts`.
- **Schema Room v16 không đổi:** không có bảng, cột, index hay migration mới; chỉ thêm truy vấn trong `ContrastDao`.
- **Ngưỡng:** nằm trong `ContrastAnalyticsPolicy` (EXPERIMENTAL), tách khỏi `ContrastConfig`. `ContrastConfig`, thang ôn và ngưỡng mastery giữ nguyên.
- **Đọc không ghi:** ba màn hình chỉ đọc. Mở chúng không tạo phiên, attempt hay lịch ôn (có unit test).
- **Cập nhật tự động:** các ViewModel lắng nghe `observeChanges()` (Flow của Room trên `contrast_attempts`) cùng `observeGroups()`, nên số liệu tự cập nhật sau một phiên M6.
- **Phạm vi dữ liệu:** không ghi vào `learning_progress` / `review_records`, không ghi vào sổ lỗi đề thi, không đổi `ItemType`, không dùng AI.

## 2. Dữ liệu dùng (đã có từ M3, đủ, không cần migration)

| Câu hỏi | Trả lời |
|---|---|
| Hướng nhầm | `expected_item_id` → `chosen_item_id` trong `contrast_attempts`. Có hướng nhầm khi `outcome = INCORRECT`, cả hai id đều có và khác nhau (cùng quy tắc với `ContrastAttempt.confusion`). |
| Dạng câu hỏi, revision | `question_type`, `question_revision`, `content_version`, cùng snapshot văn bản `chosen_text` / `correct_text`. |
| Đúng / sai / bỏ qua | `outcome` CORRECT / INCORRECT / SKIPPED. |
| Chưa làm / không khả dụng | Không có attempt nên không bao giờ được đếm. Trạng thái tính từ phiên (`GetPracticeResult`). |
| Khi nào lập lịch | Khi một khối hoàn tất và có rating (`SubmitContrastAnswer` → `ContrastScheduler`). Ghi trong cùng transaction với attempt, tối đa một lần mỗi phiên cho mỗi nhóm (UNIQUE `session_id, group_id`). |
| `dueAt` | `ladder[step].addTo(now, zone)`: cộng theo ngày lịch ở múi giờ của người học (đổi giờ mùa hè vẫn đúng giờ địa phương), lưu dưới dạng instant. |
| Mastery | Không lưu; được domain tính (`ContrastMasteryEvaluator`). |
| Nhóm mới / nhóm ôn | Mới = chưa có dòng `contrast_progress`; ôn = có dòng và `due_at ≤ now`. |

## 3. Chỉ số và công thức

Mọi số đếm đều trên các dòng attempt. Khóa chính `request_id` bảo đảm một lần thử lại idempotent không bao giờ được đếm hai lần.

| Chỉ số | Định nghĩa |
|---|---|
| TotalAnswered | đúng + sai + bỏ qua (số attempt) |
| Correct / Incorrect / Skipped | số attempt theo `outcome` |
| **Accuracy** | `correct / (correct + incorrect)`; bỏ qua **không** nằm trong mẫu số. Mẫu số = 0 thì giá trị là `null`, hiển thị "chưa có câu nào được trả lời". |
| Tỷ lệ "đủ tin cậy" | Chỉ hiện % khi `correct + incorrect ≥ minAnsweredForRate` (5). Dưới mức đó hiện "chưa đủ dữ liệu (n câu)". |
| ErrorCountByGroup | số attempt INCORRECT theo `group_id`, kèm accuracy của nhóm |
| ErrorCountByQuestionType | như trên, theo `question_type` |
| ErrorCountByDirection | số attempt INCORRECT theo bộ ba (`group_id`, expected, chosen); kèm số phiên khác nhau và thời điểm gần nhất |
| Lỗi chọn phương án nhiễu | Incorrect − tổng các hướng nhầm: chọn đáp án nhiễu, không phải nhầm giữa các mục của nhóm |
| RecentAccuracy | accuracy trên `recentSessions` (5) phiên gần nhất (theo câu trả lời đầu tiên của phiên); so với accuracy của mọi câu trước đó. Mỗi bên phải đủ tin cậy, nếu không thì hiện "chưa đủ dữ liệu". |
| Xu hướng theo phiên | danh sách đúng / sai / bỏ qua của các phiên gần nhất, dạng văn bản (không vẽ biểu đồ) |

**Bộ lọc** (`AttemptFilter`): loại kiến thức, dạng câu hỏi, nhóm.
- Lọc theo loại kiến thức chỉ giữ các nhóm **đang phát hành** của loại đó, giống quy tắc của `AnalyzeContrastMistakes` (M1).
- Nhóm đã ngừng không còn loại, nên chỉ xuất hiện khi không lọc theo loại.

## 4. Hướng nhầm

- **Định danh:** id mục ổn định, không bao giờ dùng nhãn hay vị trí hiển thị A/B/C.
- **Có chiều:** A→B, B→A và A→C là ba dòng khác nhau. Nhóm có từ 3 mục trở lên được hỗ trợ.
- **Ví dụ:** lấy attempt gần nhất của hướng đó (`latestExample`), tức snapshot đã lưu, không bao giờ chấm lại.
  - Nếu revision của câu hỏi hiện hành khác với revision đã trả lời (hoặc câu hỏi không còn), màn hình ghi "Câu hỏi này thuộc phiên bản nội dung cũ".
- **Nhóm đã ngừng:** lỗi vẫn hiện dưới id của chính nhóm đó, với tiêu đề "Nhóm đã ngừng sử dụng (id)". Không có liên kết sang So sánh, không chuyển sang id thay thế, không xóa lịch sử.

## 5. Lỗi lặp lại (repeated mistakes)

Quy tắc kiểm thử được, ngưỡng nằm trong `ContrastAnalyticsPolicy`:

- `sessions` = số phiên khác nhau có hướng nhầm đó.
- `sessionsSince` = số phiên **sau** lần mắc gần nhất mà nhóm có ít nhất một câu không bỏ qua.
  - Không tính phiên chứa chính lần mắc gần nhất.
  - Phiên chỉ có câu bỏ qua không phải bằng chứng.

| Trạng thái | Điều kiện (mặc định) |
|---|---|
| SINGLE | `sessions < repeatedMinSessions` (2) |
| TO_REINFORCE ("cần củng cố") | `sessions ≥ 2` và `sessionsSince < settledAfterSessions` (2) |
| SETTLING ("n phiên sau đó không lặp lại") | `sessions ≥ 2` và `sessionsSince ≥ 2` |

Một câu đúng ngay sau đó không đủ để đổi trạng thái. SETTLING cũng **không** phải "thành thạo": mastery là quy tắc riêng của M1.

## 6. Ôn tập thông minh

- **Lịch:** chỉ dùng SRS Contrast của M1 (`ContrastScheduler`, thang 1/3/7/14/30/60 ngày); không dùng SRS cũ gắn với `ItemType`.
- **Hàng đợi** (`GetContrastReviewQueue`):
  - Các nhóm đang phát hành có dòng `contrast_progress`.
  - **Đến hạn:** `ContrastScheduler.isDue` (`dueAt ≤ now`), quá hạn lâu nhất trước, rồi theo thứ tự trong gói.
  - **Sắp tới:** sớm nhất trước.
  - Mỗi nhóm hiện: tiêu đề, loại, trạng thái học, ngày đến hạn (theo múi giờ thiết bị), bậc trên thang ("Bậc n/6 (x ngày)").
- **Không thể ôn:** dòng tiến độ của nhóm không còn phát hành. Lịch sử vẫn được giữ, nhóm không bao giờ đến hạn, và không có nội dung thay thế được tự chọn.
- **Nhóm có lỗi lặp lại:** nhóm đang phát hành có ít nhất một hướng ở trạng thái TO_REINFORCE.
- **"Ôn tập ngay":** mở màn luyện tập M6 với `ContrastPracticeRoute(review = true)`, tức `PracticeScope.Review`.
  - `StartOrResumePractice` lấy `dueGroups(now, maxGroupsPerSession = 10)` thuộc các nhóm đang phát hành, **không** lấy nhóm mới. Vì vậy phiên ôn không dùng hạn mức 3 nhóm mới/ngày.
  - Vẫn qua một điểm quyết định duy nhất: không bao giờ có hai phiên ACTIVE, và phiên cùng phạm vi được tiếp tục.
- **Không có nhóm nào đến hạn:** "Chưa có nhóm nào đến hạn ôn"; không có nút bắt đầu và không phiên nào được tạo.
- **Câu hỏi không còn trong gói:** bị từ chối là `QUESTION_UNAVAILABLE`, không tính là sai, không lập lịch và không tăng `reviewCount`. Phiên được đóng mà không chấm gì.
- **Thay đổi contract:** thêm `PracticeScope.Review`. Giá trị này được lưu là `"REVIEW"` trong cột `scope_kind` đã có (TEXT), nên không đổi schema. Các giá trị DUE / GROUP / TYPE không đổi.

## 7. Mastery và trạng thái tiến độ

- Mastery chỉ do `ContrastMasteryEvaluator` (M1) tính. Các điều kiện đã chốt trong `MasteryPolicy`:
  - ≥ 12 câu trả lời;
  - luyện tập trên ≥ 3 ngày khác nhau;
  - ôn trễ (≥ 7 ngày sau phiên trước) ≥ 4 câu, độ chính xác ≥ 85%, trên ≥ 2 dạng câu hỏi;
  - khối ôn trễ gần nhất không phải AGAIN.
- Một nhóm chưa có lịch luôn là NEW theo quy tắc này, nên báo cáo chỉ đánh giá các nhóm đã có lịch (cùng kết quả, ít truy vấn hơn).

| Hiển thị | Quy tắc (giống bộ lọc M5, không tạo trạng thái mới) |
|---|---|
| Chưa học | NEW |
| Đang học | LEARNING hoặc REVIEWING |
| Cần củng cố | đã có lịch và đang đến hạn; **có thể trùng** với đang học / đã thành thạo (màn hình ghi rõ) |
| Đã thành thạo | MASTERED |

## 8. Tiến độ (`GetContrastProgressReport`)

- **Số nhóm:** chỉ đếm nhóm **đang phát hành** (mẫu số của mọi số nhóm).
- **Câu trả lời:** đếm **mọi** attempt, kể cả của nhóm đã ngừng. Phần thuộc nhóm đã ngừng được ghi riêng (`historical`), nên không cộng trùng và không mất dấu.
- **Theo loại** (Grammar / Vocabulary / Kanji): tổng số nhóm, đã bắt đầu, thành thạo, cần củng cố.
- **Độ chính xác:** tổng, theo nhóm và theo dạng câu hỏi; chỉ hiện % khi đủ tin cậy.
- **Ôn tập:** lịch ôn gần nhất sắp tới và lối vào màn Ôn tập.
- Không đưa dữ liệu Contrast vào tab Thống kê; không có XP, streak, thời gian học hay bảng xếp hạng.

## 9. Điều hướng và UI state

```
Contrast Home ── Ôn tập   → ContrastReviewRoute   ── Ôn tập ngay → ContrastPracticeRoute(review = true) (M6) → Tổng kết
              │                                    └─ nhóm → So sánh
              ├─ Phân tích lỗi → ContrastErrorsRoute   ── Học lại nhóm này → So sánh
              └─ Tiến độ  → ContrastProgressRoute ── Mở Ôn tập / nhóm → So sánh
```

- Cả 3 route thuộc tab Học (`isTabRoot = false`, không phải phiên tập trung). Thanh dưới vẫn 5 tab.
- Ba bộ lọc của màn Phân tích lỗi nằm trong `SavedStateHandle`, nên vẫn còn khi quay lại từ So sánh và sau khi tiến trình bị hủy.
- **UI state:** `UiState<…>` của app: Loading / Empty (chưa có gói, cho Ôn tập và Tiến độ) / Success / Error kèm `retry()`.
  - Phân tích lỗi khi chưa có câu sai: "Chưa có lỗi sai Contrast Lab được ghi nhận." và không có tỷ lệ.
- **Khả năng tiếp cận:**
  - Danh sách là `LazyColumn` (danh sách dài vẫn mượt).
  - Ô số liệu được gộp thành một mô tả cho TalkBack ("Nhóm hiện có: 200").
  - Tiêu đề có `heading()`; trạng thái được nói bằng chữ, không chỉ bằng màu.

## 10. Hiệu năng (đo, không ước lượng)

`ContrastAnalyticsRoomTest.aggregatesOnAThousandGroupsAndAHundredThousandAttempts`:
- Room trên file thật, emulator Pixel 8 API 36.
- Dữ liệu tải giả lập, chỉ có trong test: 1.003 nhóm, 100.000 attempt trong 5.000 phiên, 600 nhóm có lịch. Số đo ghi vào logcat với tag `ContrastPerf`.

| Thao tác | Thời gian |
|---|---|
| totals | 16 ms |
| byGroup | 119 ms |
| byQuestionType | 44 ms |
| directions + sessionsSince | 271 ms |
| totals lọc GRAMMAR | 22 ms |
| recentSessions / recentMistakes | 22 / 15 ms |
| `GetContrastErrorAnalysis` (tất cả) | 509 ms |
| `GetContrastProgressReport` | 1.396 ms |
| `GetContrastReviewQueue` | 1.260 ms |
| So sánh: `AnalyzeContrastMistakes` của M1 (nạp mọi attempt) | 2.836 ms |

- Truy vấn dùng các index sẵn có (`group_id, answered_at`, `answered_at`, `session_id, position`). **Không cần index mới**, nên không có migration.
- **Báo cáo tiến độ và hàng đợi ôn** tốn thời gian chủ yếu ở bước tính mastery: mỗi nhóm đã có lịch đọc attempt của chính nó, vì quy tắc ôn trễ cần từng câu trả lời.
  - Nếu kho nội dung vượt nhiều nghìn nhóm đã học, có thể lưu đệm kết quả mastery theo `observeChanges()`.
- **Home (M5)**: từ M8 dùng truy vấn tổng hợp (`lastAnsweredGroup`, `directions`), không còn nạp toàn bộ attempt. Số đo M8 ở `CONTRAST_LAB_M8_REPORT.md` §6.

## 11. Test

| Nhóm | Test | Nơi chạy |
|---|---|---|
| Phân tích, SRS, tiến độ, ViewModel | `ContrastInsightsTest` (16 test):<br>• A→B / B→A / A→C, nhóm 3 mục, nhiễu, bỏ qua không tính sai<br>• không chia cho 0<br>• lỗi lặp lại qua nhiều phiên, ngưỡng từ policy<br>• chưa làm / không khả dụng / gửi lại idempotent<br>• trạng thái rỗng<br>• nhãn theo id, snapshot cũ khi gói sửa câu hỏi, bộ lọc còn sau khi tạo lại ViewModel<br>• nhóm đã ngừng giữ lịch sử và id<br>• đến hạn / chưa đến hạn / bậc thang; xem không ghi gì<br>• nửa đêm, đổi múi giờ, đổi giờ mùa hè (Berlin: 3 ngày lịch = 73 giờ)<br>• phiên ôn chỉ nhóm đến hạn, hạn mức nhóm mới không đổi, `reviewCount` +1 đúng một lần, tiếp tục phiên dở<br>• không lập lịch khi không có câu hợp lệ; nút ôn khi không có gì để ôn<br>• chưa học / đang học / cần củng cố / theo loại<br>• MASTERED đúng quy tắc M1 (100% đúng nhưng khối trễ AGAIN thì chưa)<br>• tiến độ tự cập nhật sau phiên luyện tập<br>• khớp với `AnalyzeContrastMistakes` | JVM |
| Điều hướng | `ContrastNavigationTest`, `AppScreenTest` (thêm 3 route) | JVM |
| Room | `ContrastAnalyticsRoomTest` (4 test):<br>• SQL khớp với bộ phân tích M1 trên dữ liệu ngẫu nhiên, gồm bộ lọc, phiên gần nhất, snapshot, gửi trùng<br>• `sessionsSince`<br>• phạm vi REVIEW lưu và đọc lại đúng, Flow phát khi có câu trả lời<br>• hiệu năng | thiết bị |
| Màn hình | `ContrastInsightsScreensTest` (5 test):<br>• hướng nhầm, ví dụ phiên bản cũ, nhóm đã ngừng không có liên kết, bộ lọc, quay lại So sánh<br>• rỗng / đang tải / lỗi, không có "100%"<br>• Ôn tập ngay, tiếp tục, lỗi lặp lại, không thể ôn, danh sách 60 nhóm<br>• không có nhóm đến hạn<br>• Tiến độ: mô tả cho TalkBack, tỷ lệ "chưa đủ dữ liệu", danh sách 200 nhóm, chế độ tối + cỡ chữ 2x | thiết bị |
| End-to-end | `ContrastInsightsEndToEndTest`:<br>• Room thật, nguồn đóng sẵn thật, bản sao test của gói mẫu; một khối 4 ngày trước qua use case thật<br>• Phân tích lỗi (hướng nhầm, 1 câu sai) → Ôn tập (1 nhóm đến hạn, mở màn không tạo phiên) → Ôn tập ngay (M6) → Tổng kết → Tiến độ (số câu cập nhật)<br>• DB: 1 phiên REVIEW đã xong, 2 review, `reviewCount = 2` với hạn mới trong tương lai, không có nhóm mới trong phiên ôn, SRS cũ không đổi | thiết bị |

## 12. Giới hạn hiện tại

- Các ngưỡng (`ContrastAnalyticsPolicy`, `MasteryPolicy`, thang ôn) vẫn là đề xuất thử nghiệm, chờ chủ dự án quyết định.
- Xu hướng hiển thị bằng văn bản, chưa có biểu đồ (theo yêu cầu: không vẽ khi dữ liệu chưa đủ tin cậy).
- (Đã xử lý ở M8) Home tính "Hay nhầm" bằng truy vấn tổng hợp.
- App chưa mang gói Contrast nào đã duyệt: người dùng thật thấy trạng thái rỗng.
