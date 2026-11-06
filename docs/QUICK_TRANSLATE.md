# Dịch nhanh khi bôi đen

Bôi đen chữ trong ứng dụng khác → **Dịch nhanh** → hộp thoại N3 Sensei. Mã nguồn:
- `app/translate/`: Activity, bật/tắt mục trong menu.
- `feature/translate/`: giao diện, ViewModel.
- `domain/translate/`: văn bản, nhận diện ngôn ngữ, mô hình dịch, bộ điều phối.
- `data/translate/`: từ điển nội dung, ML Kit.

## Trạng thái theo PR

| PR | Nội dung | Trạng thái |
|---|---|---|
| 1 | `ACTION_PROCESS_TEXT`, Activity dạng hộp thoại | Xong |
| 2 | Cài đặt (bật/tắt, Anh/Nhật/Cả hai), nhận diện ngôn ngữ nguồn | Xong |
| 3 | Bộ máy dịch: từ điển trước, ML Kit sau | Xong; thiếu nguồn IPA, romaji, ví dụ ngoài nội dung app |
| 4 | Giao diện kết quả, tải gói ngôn ngữ khi người dùng đồng ý, "Ví dụ và cách dùng" | Xong |
| 5 | Phát âm bằng Android TextToSpeech (một loa mỗi ngôn ngữ, không đọc giọng sai) | Xong |
| 6 | Một bookmark lưu chung vào "Từ của tôi" ở trạng thái "Đã lưu", ngoài mọi luồng học tự động (schema v14) | Xong; xem [USER_CONTENT.md](USER_CONTENT.md) |
| 7 | Kiểm thử E2E trên emulator (Chrome), hồi quy, nâng cấp APK 13 → 14, đo hiệu năng; sửa các lỗi tìm thấy | Xong; xem "Kiểm thử PR7" |

## Cách dùng

1. Trong ứng dụng khác (Chrome, ghi chú…), bôi đen chữ (tối đa 200 ký tự) → menu chọn chữ → **Dịch nhanh**. Trong Chrome, mục này nằm trong nút **⋮** của thanh chọn chữ (có thể phải cuộn menu).
2. Hộp thoại hiện văn bản, ngôn ngữ nhận diện được (đổi được: Việt / Anh / Nhật) và chế độ dịch (Anh / Nhật / Cả hai). Lựa chọn trong hộp thoại chỉ áp dụng cho lần này; Cài đặt không đổi.
3. Kết quả ghi rõ nguồn: **Từ điển N3 Sensei** hoặc **Dịch máy trên thiết bị**. Từ có trong nội dung app có thêm cách đọc, "Ví dụ và cách dùng" (mở ra khi bấm).
4. Loa cạnh văn bản và cạnh bản dịch Anh/Nhật: đọc bằng giọng đúng ngôn ngữ.
5. Nút bookmark: lưu vào "Từ của tôi". Nút X, Back hoặc chạm ra ngoài: đóng, trở về ứng dụng cũ. Văn bản ở ứng dụng cũ không bị thay đổi (kể cả ô nhập liệu).

## Cài đặt (Cài đặt → Dịch nhanh)

- **Bật/tắt**: tắt thì mục "Dịch nhanh" biến khỏi menu chọn chữ của mọi ứng dụng (component bị vô hiệu hoá).
- **Chế độ**: Anh, Nhật hoặc Cả hai. Văn bản tiếng Anh/Nhật luôn có nghĩa tiếng Việt.
- Cài đặt này **không** có trong tệp sao lưu (xem "Sao lưu").

## Gói ngôn ngữ (dịch ngoại tuyến)

- Lần đầu cần dịch máy, hộp thoại báo cần tải gói và dung lượng (Việt khoảng 36 MB, Nhật khoảng 48 MB). **Chỉ tải khi người dùng bấm "Tải gói ngôn ngữ"**.
- Mọi gói cần cho lần dịch đó được bắt đầu tải **cùng lúc**; đóng hộp thoại giữa chừng thì việc tải vẫn tiếp tục và gói có sẵn ở lần sau.
- Mất mạng khi chưa có gói: báo "không có mạng" và có nút **Thử lại** (giữ quyền tải người dùng đã cho).
- Có gói rồi thì dịch hoàn toàn không cần mạng. Gói nằm trong dữ liệu riêng của app (`no_backup/`), mất khi gỡ app hoặc xoá dữ liệu.

## Phát âm

- Dùng bộ đọc văn bản (TTS) của máy. Thiếu giọng của một ngôn ngữ: báo "Thiết bị chưa có giọng đọc…" kèm nút **Cài giọng đọc** (mở màn hình tải giọng của bộ đọc). Không bao giờ đọc bằng giọng ngôn ngữ khác.
- Một số giọng của Google TTS là giọng **qua mạng** (`…-server`): bộ đọc vẫn báo "có giọng", nhưng khi mất mạng có thể đọc lỗi. Khi đó hộp thoại báo "Không đọc được bằng giọng …; giọng này có thể cần mạng hoặc chưa được tải về máy" và gợi ý cài giọng. Trên emulator (Google TTS) quan sát được: tiếng Anh dùng giọng trên máy; tiếng Việt và tiếng Nhật dùng giọng qua mạng, rồi bộ đọc tự tải giọng tiếng Nhật trên máy sau vài lần dùng.

## Lưu vào "Từ của tôi"

- Một lần bấm lưu một mục chung (cả bản dịch Anh và Nhật). Trạng thái **Đã lưu** (`SAVED`): có trong "Từ của tôi", tìm được theo chữ, nghĩa và bản dịch, nhưng **không** vào ôn tập, luyện tập, mục mới hằng ngày, nhắc học, widget hay thống kê. Từ tự thêm bằng tay vẫn là **Đang học** (`STUDYING`) như trước.
- Một mục được nhận diện theo (ngôn ngữ nguồn, văn bản đã chuẩn hoá [+ cách đọc nếu biết]): lưu lại cùng chữ không tạo bản sao, chỉ thêm phần còn thiếu. Lưu "experience" (tiếng Anh) và "経験" (tiếng Nhật) là **hai mục** khác nhau dù cùng nghĩa.
- Chọn chữ tiếng Anh có bản dịch tiếng Nhật: mục đứng tên bằng từ tiếng Nhật, chữ tiếng Anh được giữ trong "Bản dịch" (thấy và tìm được).
- Sửa: mục **Đã lưu** không cần cách đọc (app không bịa cách đọc cho từ do máy dịch); từ đang học vẫn bắt buộc có kana. Xoá: xoá cả bản dịch của mục, không đụng tới gói nội dung.

## Giới hạn của `ACTION_PROCESS_TEXT`

- Chỉ có trong ứng dụng có menu chọn chữ chuẩn của Android (Chrome, phần lớn ô nhập liệu). Ứng dụng tự vẽ menu riêng, trò chơi, PDF dạng ảnh: không có.
- Tối đa 200 ký tự (đếm theo code point): dài hơn thì báo độ dài, không cắt.
- Văn bản chỉ được đọc; N3 Sensei không trả văn bản thay thế về ứng dụng gốc.

## Nguồn dữ liệu

| Nguồn | Dùng cho | Trạng thái |
|---|---|---|
| Nội dung N3 Sensei: từ vựng mọi gói và "Từ của tôi", Kanji, từ minh họa của Kanji | Nhật ↔ Việt: nghĩa, cách đọc kana, ví dụ thật, ghi chú (từ loại, Hán Việt, On/Kun) | Đã tích hợp, chỉ **khớp chính xác** cả chuỗi. Truy vấn SQL chỉ lọc ứng viên; kiểm tra cuối so trọn một dạng chữ, một cách đọc hoặc một nghĩa |
| Google ML Kit Translation 17.0.3 (dịch trên máy) | Câu, cụm từ và mọi trường từ điển không có (tất cả chiều Việt/Anh/Nhật) | Đã tích hợp. Mô hình tải từ Google một lần; dịch hoàn toàn trên máy |
| Từ điển phát âm tiếng Anh (IPA) | `englishPronunciation` | **Chưa có**: trường luôn là `Unavailable` |
| Romaji | `japaneseRomaji` | **Chưa có**: trường luôn là `Unavailable` |
| Từ điển Nhật–Anh có cách đọc (JMdict, EDRDG, CC BY-SA 4.0; dự án đã dùng cho Kanji) | Từ tiếng Anh ↔ Nhật kèm kana | **Chưa đóng gói**: cần chủ dự án duyệt (dung lượng, điều kiện chia sẻ lại CC BY-SA, màn hình ghi nguồn) |
| Câu ví dụ (Tatoeba, CC BY 2.0 FR) | Ví dụ cho từ ngoài nội dung app | **Chưa đóng gói**: cần duyệt |

## ML Kit: số đo thực tế

- **Thư viện**: `libtranslate_jni.so` khoảng 16 MB (arm64-v8a), 11,6 MB (armeabi-v7a), 17 MB (x86, x86_64).
  - APK debug gộp đủ 4 ABI: 15,5 MB → **80,2 MB**.
  - Phát hành bằng App Bundle: máy arm64 tải thêm khoảng 16 MB.
- **Mô hình** (theo `translate_models_metadata.json` trong thư viện):

| Mô hình | Dung lượng tải | Trên máy |
|---|---|---|
| Anh–Nhật | 48 MB | 63 MB |
| Anh–Việt | 35,5 MB | 46 MB |

- **Đi vòng qua tiếng Anh**: mô hình ML Kit luôn ghép với tiếng Anh, nên Việt ↔ Nhật là Việt → Anh → Nhật bên trong ML Kit.
- **Quyền mới**: `INTERNET` và `ACCESS_NETWORK_STATE`, chỉ để tải mô hình và phân biệt "mất mạng" với "tải lỗi". Trước đây manifest gỡ `ACCESS_NETWORK_STATE`.
- **Quyền riêng tư**:
  - Văn bản được dịch trên máy, không gửi đi để dịch.
  - Thư viện ML Kit kèm thành phần gửi thống kê sử dụng ẩn danh về Google (Firebase transport), không có API công khai để tắt. Cần nêu trong chính sách quyền riêng tư khi phát hành.
  - App không ghi văn bản người dùng chọn vào log hay analytics.
- **Không có tiến độ tải**: ML Kit không cung cấp; giao diện chỉ có thể hiện trạng thái "đang tải".

## Bộ điều phối (`TranslationOrchestrator`)

- Ngôn ngữ đích theo `TranslationMode.targetsFor`. Văn bản tiếng Anh hoặc Nhật luôn có nghĩa tiếng Việt; không dịch một ngôn ngữ sang chính nó.
- Trường nào từ điển có thì dùng từ điển. Dịch máy chỉ điền trường còn trống và được đánh dấu `MACHINE_TRANSLATION`; không bao giờ ghi đè dữ liệu từ điển.
- Từ tiếng Nhật do máy dịch chỉ có cách đọc khi từ điển có **đúng từ đó**.
- Mỗi trường có trạng thái `Available`, `NotRequested`, `Unavailable`, `NeedsModel(ngôn ngữ)` hoặc `Failed(OFFLINE | MODEL_DOWNLOAD_FAILED | TRANSLATION_FAILED)`. Không bao giờ dùng chuỗi rỗng.
- Mô hình chỉ được tải khi người gọi cho phép (`allowModelDownload`). Hai yêu cầu cùng lúc chỉ dẫn tới một lần tải. Huỷ yêu cầu thì ngừng chờ, nhưng việc tải vẫn tiếp tục.
- Ba trường (Việt, Anh, Nhật) chạy song song, nên mọi gói được phép tải bắt đầu ngay (trước PR7, gói Nhật chờ gói Việt; đóng hộp thoại khi đang tải gói Việt thì gói Nhật không bao giờ được tải).
- `MlKitMachineTranslator` chỉ chạy **một** lệnh dịch ML Kit tại một thời điểm, khoá giữ đến khi tác vụ ML Kit kết thúc (kể cả khi người gọi đã huỷ). Lý do: hai cặp ngôn ngữ nạp lần đầu cùng lúc làm tiến trình bị huỷ trong mã native của ML Kit (`init_google.cc Check failed`), tái hiện 2/6 lần trên emulator; sau khi sửa 10/10 lần không lỗi.
- **Cache** trong bộ nhớ (100 kết quả), khoá gồm:
  - văn bản đã chuẩn hoá;
  - ngôn ngữ nguồn;
  - các ngôn ngữ đích;
  - phiên bản bộ máy và phiên bản nội dung (gói, từ của tôi, chỉnh sửa).

  Chỉ lưu kết quả không còn trường nào chờ mô hình hoặc lỗi.

## Nguồn và giấy phép

- Nội dung N3 Sensei (gói từ vựng, Kanji, ví dụ): như phần còn lại của app.
- Google ML Kit Translation: thư viện của Google, dùng theo điều khoản ML Kit / Google APIs. Mô hình tải từ máy chủ Google. Kết quả dịch máy luôn được ghi "Dịch máy trên thiết bị".
- Bộ đọc văn bản: của hệ thống (thường là Google TTS), không đóng gói trong app.
- JMdict, Tatoeba, từ điển IPA: **chưa** đóng gói (xem bảng "Nguồn dữ liệu").

## Sao lưu

- Tệp sao lưu (Cài đặt → Sao lưu) **chưa** chứa "Từ của tôi" (kể cả bookmark và bản dịch), cài đặt Dịch nhanh và gói ngôn ngữ. Các dữ liệu này còn khi cập nhật app nhưng **mất khi gỡ app** hoặc xoá dữ liệu. Sao lưu đám mây bị tắt có chủ đích (xem SECURITY.md).

## Kiểm thử PR7 (emulator Pixel 8, Android 16, x86_64, bản debug)

Số đo trên emulator, chỉ để so sánh tương đối; máy thật sẽ khác.

| Hạng mục | Kết quả |
|---|---|
| Khởi động app (lạnh / ấm) | 619–935 ms / 24–31 ms |
| Mở hộp thoại (tiến trình chưa chạy / đang chạy) | 504–515 ms / 28 ms |
| Tra từ điển (trung vị, 20 lần) | Nhật 1,0 ms; Việt 1,6 ms |
| ML Kit, lần đầu → trung vị | Anh→Việt (từ) 254 → 11 ms; Anh→Nhật (câu) 90 → 35 ms; Việt→Nhật (qua tiếng Anh) 142 → 44 ms |
| Cả bộ điều phối, câu tiếng Anh, Cả hai (trung vị) | 58 ms |
| Khởi tạo TTS | 488–796 ms |
| Lưu bookmark (trung vị, 20 lần) | 1,8 ms |
| Tải gói Việt + Nhật | khoảng 15–20 giây (mạng của emulator) |
| Bộ nhớ khi chỉ mở hộp thoại | PSS khoảng 107–117 MB |
| Rò rỉ | Mở/đóng hộp thoại 5 lần, sau GC: 0 Activity, 0 ViewRootImpl |
| APK | debug 81,7 MB; release (chưa ký, chưa thu gọn) 75,3 MB, trong đó `libtranslate_jni.so` 4 ABI 59,8 MB |
| Nâng cấp APK ea42efb (schema 13) → bản mới (14) | Tiến độ, lịch sử ôn, đánh dấu, phiên ôn giống hệt từng dòng; thống kê giống hệt; giao diện tối giữ nguyên; `integrity_check` ok |

Lỗi tìm thấy và đã sửa trong PR7 (mỗi lỗi có test): không có nút Thử lại sau lỗi mất mạng; loa của văn bản nguồn bị đẩy khỏi hộp thoại khi câu xuống dòng; gói Nhật không được tải khi đóng hộp thoại giữa chừng; crash native khi hai cặp ngôn ngữ nạp cùng lúc; bookmark từ tiếng Anh mất chữ tiếng Anh; không sửa được mục đã lưu thiếu cách đọc; lỗi đọc của TTS không được báo.

