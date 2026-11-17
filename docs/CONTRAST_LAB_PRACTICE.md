# Contrast Lab: luyện tập (M6)

Trạng thái: đã triển khai (2026-11-17). Kết quả test ở mục 11.

## 1. Kiến trúc

```
ContrastPracticeScreen ── ContrastPracticeViewModel ── StartOrResumePractice (M1)  ── ContrastPracticeRepository / ContrastProgressRepository (Room, M3)
                                                    └─ SubmitContrastAnswer (M1)  ── recordAnswer (@Transaction, M3)
ContrastSessionSummaryScreen ── ContrastSessionSummaryViewModel ── GetPracticeResult, GetContrastProgress (M1)
```

- ViewModel không tự chấm, không tự lập lịch và không giữ điểm. Mọi kết quả đến từ domain (`ContrastAnswerChecker`, `ContrastScheduler`) và từ attempt đã lưu.
- Composable không chạm DAO; ViewModel không giữ NavController.
- Hai màn hình mới thuộc tab Học:
  - `ContrastPracticeRoute(groupId?, resume)`: phiên tập trung, ẩn thanh dưới như màn làm đề.
  - `ContrastSummaryRoute(sessionId)`.
- Không có tab mới.

## 2. Điểm vào và luồng phiên

- **Contrast Home:**
  - "Bắt đầu luyện tập": `PracticeScope.Due`, tức các nhóm đến hạn trước, rồi nhóm mới trong giới hạn mới/ngày.
  - "Tiếp tục phiên dang dở (còn n câu)": hiện khi có phiên ACTIVE còn trong hạn tiếp tục (24 giờ) và còn câu chưa làm.
- **So sánh:** "Luyện tập nhóm này" (`PracticeScope.Group`), chỉ hiện khi nhóm có câu hỏi.
- **Mở phiên:**
  - Có phiên ACTIVE khác phạm vi, còn câu → màn hình **hỏi**: "Tiếp tục phiên cũ" hoặc "Bắt đầu phiên mới". Chọn phiên mới thì phiên cũ thành ABANDONED theo contract, các câu đã trả lời vẫn được giữ. Không bao giờ âm thầm thay phiên.
  - Ngược lại, gọi `StartOrResumePractice`: tiếp tục phiên cùng phạm vi, hoặc tạo phiên mới.
- Câu hiển thị là vị trí đầu tiên chưa có attempt và câu hỏi vẫn còn trong gói. Câu đã làm không bao giờ hiện lại như câu mới.
- **Cấu hình `ContrastConfig`:**
  - 3 câu mỗi khối, 3 nhóm mới mỗi ngày (chỉ tính nhóm mới), 10 nhóm mỗi phiên, thang 1/3/7/14/30/60 ngày.
  - Composable không đọc cấu hình này.
  - Không đủ câu thì khối ngắn hơn; không có câu nào thì báo "Không có nhóm nào để luyện lúc này". Không bao giờ nhân bản câu.

## 3. Bốn dạng bài

Cùng một màn điều phối (đầu trang, nội dung, thanh hành động), mỗi dạng một component.

| Dạng | Hiển thị | Chọn |
|---|---|---|
| Chọn đáp án | hướng dẫn, câu tiếng Nhật (furigana) nếu có | lựa chọn dạng radio |
| Điền chỗ trống | câu có `＿＿＿`; dạng đang chọn được điền ngay vào chỗ trống và tô đậm | lựa chọn dạng radio |
| Tìm lỗi sai | các **đoạn của câu theo gói** (id của option), không tự tách chuỗi | chạm vào đoạn; mỗi đoạn là một phần tử chọn được cho TalkBack ("Đoạn n: …") |
| Chọn theo ngữ cảnh | thẻ ngữ cảnh: mô tả và các dòng tiếng Nhật giữ đúng xuống dòng của gói | câu hoàn chỉnh dạng radio |

- Bản dịch câu chỉ hiện sau khi trả lời, để không lộ đáp án.
- **Điền chỗ trống là chọn, không gõ phím.** Contract M1/M2 định nghĩa dạng này bằng lựa chọn; schema không có `acceptedAnswers`, và cả validator Python lẫn parser Kotlin đều từ chối trường đó (fixture `accepted-answers-not-in-contract`). Gõ phím tiếng Nhật đòi hỏi quy tắc chuẩn hóa và danh sách đáp án chấp nhận; chưa có contract nào cho việc này, nên M6 không tự đặt ra.

## 4. Nộp, bỏ qua, phản hồi

- "Kiểm tra" chỉ bật khi đã chọn. "Bỏ qua" ghi **SKIPPED**, không bao giờ là INCORRECT.
- Thoát (nút "Thoát (đã lưu)" hoặc Back) không ghi gì: câu đang xem vẫn **PENDING**.
- **Sau khi lưu xong:**
  - "Đúng", "Sai" hoặc "Đã bỏ qua (không tính là sai)", luôn có biểu tượng và chữ đi kèm.
  - Đáp án đúng; với dạng tìm lỗi, kèm "Sửa thành: …".
  - Giải thích đã biên tập và ghi chú của lựa chọn sai (`feedbackVi`).
  - "Bạn đã nhầm A thành B": hướng nhầm do domain tính, lưu trong attempt.
  - Báo có câu hỏi lại ở cuối phiên, và lịch ôn tiếp theo khi khối vừa được lập lịch.
- Mỗi lựa chọn mang trạng thái "Đáp án đúng" / "Bạn chọn" trong semantics, không chỉ thể hiện bằng màu.
- Khi bỏ qua thì không có hướng nhầm.

## 5. Ghi câu trả lời, idempotency, lỗi

- Đường đi: `SubmitContrastAnswer` → `ContrastPracticeRepository.recordAnswer`. Một transaction ghi attempt, review và progress (khi khối hoàn tất) cùng hoạt động của phiên.
- **Request id:** mỗi vị trí nhận một id ở lần nộp đầu, lưu trong `SavedStateHandle`, và giữ cho tới khi biết kết quả.
  - Thử lại sau lỗi, chạm hai lần hay tiến trình bị hủy đều dùng lại id đó, nên repository ghi tối đa một lần (khóa chính `request_id`).
  - Đã có kết quả (`Recorded`, `Duplicate`, `ALREADY_ANSWERED`) thì xóa id.
- **Trong lúc lưu:** trạng thái `Submitting`, các nút bị khóa; câu tiếp theo chỉ hiện khi đã biết kết quả.
- **Lưu thất bại:** vẫn ở câu đó, giữ lựa chọn, hiện "Không lưu được câu trả lời…"; thử lại dùng cùng id.
- **`Duplicate` hoặc `ALREADY_ANSWERED`:** hiện đúng attempt **đã lưu**, kể cả khi lựa chọn mới khác, và không chấm lại.

## 6. Snapshot và revision

- Chấm theo phiên bản câu hỏi **hiện hành**. Contract bảo đảm một revision mới không đổi ý nghĩa đáp án (`ANSWER_CHANGED_SAME_ID` khi build). Attempt ghi `questionRevision` và `contentVersion` đã dùng.
- **Gói đổi lựa chọn giữa chừng** (`INVALID_OPTION`): hiện câu hỏi mới với thông báo "Câu hỏi vừa được cập nhật… Hãy chọn lại"; không ghi gì.
- **Câu hỏi bị gỡ** (`QUESTION_UNAVAILABLE`): trạng thái "Câu hỏi này không còn trong gói… không được tính là sai". Vị trí đó là UNAVAILABLE, không chặn khối, không chặn việc kết thúc phiên.
- Lịch sử ở màn tổng kết đọc từ snapshot của attempt (`chosenText`, `correctText`), nên vẫn đúng sau khi gói cập nhật.

## 7. Tạm dừng và tiếp tục

- Phiên nằm trong Room. Đóng màn hình, đưa app xuống nền hay khởi động lại thì phiên ACTIVE vẫn còn và tiếp tục từ câu đầu tiên chưa làm.
- Lựa chọn chưa nộp là bản nháp trong `SavedStateHandle`: còn lại khi tiến trình bị hủy rồi khôi phục, nhưng không bao giờ thành attempt.
- Câu còn lại toàn là UNAVAILABLE thì phiên được đóng (COMPLETED) và chuyển sang tổng kết.

## 8. Tiến độ và SRS

- Chỉ dùng `ContrastScheduler` của M1 qua `SubmitContrastAnswer`: khối hoàn tất được quy ra rating, rồi tính bậc trên thang Contrast.
- Mỗi khối được lập lịch **đúng một lần** (UNIQUE `session_id, group_id`); câu hỏi lại ở cuối phiên không lập lịch lại.
- Không đụng `learning_progress` hay `review_records` (test end-to-end so số dòng trước và sau). Đọc lý thuyết không tạo tiến độ (M5).

## 9. Tổng kết phiên

- Số câu đúng, sai, bỏ qua; chưa làm và không còn khả dụng chỉ hiện khi có, và không bao giờ tính là sai.
- Số câu đã xử lý; độ chính xác = đúng / (đúng + sai). Chưa trả lời câu nào thì ghi "chưa có câu nào được trả lời", không hiện 0%.
- Mỗi nhóm: trạng thái học và lịch ôn tiếp theo.
- Danh sách câu đã làm từ snapshot: dạng bài, kết quả, "Bạn chọn", "Đáp án".
- Nút "Về Contrast Lab" (Home được tạo lại, số liệu mới) và "Mở thư viện".
- Không có XP, streak, thời gian làm bài hay bảng xếp hạng.

## 10. UI state

`PracticeUiState`:
- `Loading`, `NothingToPractice`, `ConfirmReplace`;
- `Ready(item, selectedOptionId, saveFailed, contentChanged)`, `Submitting`, `Feedback(item, feedback)`;
- `QuestionUnavailable`, `Completed(sessionId)` (màn hình chuyển sang tổng kết), `Failed` (thử lại).

Mỗi lúc chỉ có một trạng thái. `select` / `submit` / `skip` chỉ có tác dụng ở `Ready`; `next` chỉ có tác dụng ở `Feedback` / `QuestionUnavailable`.

## 11. Test

| Nhóm | Test | Nơi chạy |
|---|---|---|
| ViewModel | `ContrastPracticeViewModelTest` (15 test): bắt đầu không ghi gì; đúng / sai / bỏ qua; thoát giữ nháp; tiến trình bị hủy; chạm hai lần; lưu lỗi rồi thử lại cùng id; câu đã lưu không chấm lại; câu bị gỡ; revision mới; lựa chọn đổi; hoàn thành, lập lịch một lần và tổng kết; bỏ qua không tính sai, không có độ chính xác; hỏi trước khi thay phiên; tiếp tục; không có gì để luyện; giới hạn mới/ngày | JVM |
| Điều hướng | `ContrastNavigationTest`, `AppScreenTest` (thêm 2 route) | JVM |
| Màn hình | `ContrastPracticeScreensTest` (7 test): 4 dạng bài, chọn, nộp, phản hồi bằng chữ và semantics, đoạn chọn được cho TalkBack, ngữ cảnh nhiều dòng, bỏ qua, đang lưu, lỗi lưu, câu bị gỡ, không có gì để luyện, hỏi thay phiên, chế độ tối + cỡ chữ 2x, tổng kết | thiết bị |
| End-to-end | `ContrastPracticeEndToEndTest`: Room thật, nguồn đóng sẵn thật, **bản sao test** của gói mẫu; một phiên đầy đủ từ So sánh (sai → có câu hỏi lại, đúng, đúng, bỏ qua) → tổng kết (4 câu, 67%), 4 attempt, 1 review, 1 progress, SRS cũ không đổi; phiên dở qua thoát và khởi động lại, tiếp tục từ Home | thiết bị |

## 12. Giới hạn của M6, phần dành cho M7

- (Cập nhật M7) Phân tích lỗi, Ôn tập (nút "Ôn tập ngay" mở màn này với `PracticeScope.Review`) và Tiến độ: xem [CONTRAST_LAB_ANALYTICS_SRS.md](CONTRAST_LAB_ANALYTICS_SRS.md).
- Chưa có dạng gõ đáp án: cần quyết định contract trước (mục 3).
- Màn hình ngang dùng bố cục cuộn dọc chung, chưa tối ưu riêng.
- Chưa có gói phát hành: người dùng thật thấy "Không có nhóm nào để luyện…" cho tới khi có gói đã duyệt.
