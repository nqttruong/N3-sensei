# Định dạng gói nội dung (`n3sensei-content-pack` v1)

Nội dung học (bài, từ vựng, Kanji, ngữ pháp, câu ví dụ, bài tập) được nạp vào Room từ tệp JSON UTF-8 theo định dạng dưới đây. App dùng cùng bộ kiểm tra và bộ nhập cho:

- gói minh họa đi kèm app: `app/src/main/assets/content/n3sensei-illustration.json`, tự cài ở lần chạy đầu và khi có phiên bản mới hơn;
- tệp người dùng chọn trong **Cài đặt → Nhập gói nội dung (JSON)**.

## Cấu trúc

```jsonc
{
  "format": "n3sensei-content-pack",   // bắt buộc, đúng chuỗi này
  "formatVersion": 1,                   // bắt buộc
  "pack": {
    "id": "my-pack",                    // bắt buộc, ổn định giữa các phiên bản
    "title": "Tên gói",                 // bắt buộc
    "version": 1,                       // bắt buộc, số nguyên ≥ 1, tăng khi cập nhật
    "jlptLevel": "N3",                  // mặc định "N3"
    "defaultSourceId": "src"            // nguồn mặc định; bỏ trống nếu chỉ có 1 nguồn
  },
  "sources": [                          // ít nhất 1 nguồn hợp lệ
    { "id": "src", "name": "Tên nguồn", "url": null,
      "license": "CC BY-SA 4.0", "attribution": null,
      "licenseVerified": false }        // chỉ đặt true khi quyền phân phối đã được xác minh
  ],
  "tags": [ { "id": "topic:work", "name": "Công việc" } ],
  "examples": [                         // câu ví dụ dùng chung, tham chiếu bằng id
    { "id": "ex1", "japanese": "日本に来たばかりです。",
      "furigana": "{日本|にほん}に{来|き}たばかりです。",
      "translationVi": "Tôi vừa mới đến Nhật.", "sourceId": null }
  ],
  "lessons": [
    {
      "id": "l1", "number": 1, "title": "Bài 1", "description": null,
      "vocabulary": [
        { "id": "l1-v01", "expression": "経験", "reading": "けいけん",
          "furigana": "{経|けい}{験|けん}", "meaningVi": "kinh nghiệm",
          "partOfSpeech": "danh từ, động từ する", "note": null,
          "tags": ["topic:work"], "examples": ["ex1"], "sourceId": null }
      ],
      "kanji": [
        { "id": "l1-k01", "character": "経", "onReadings": ["ケイ", "キョウ"],
          "kunReadings": ["へ.る"], "hanViet": "KINH", "meaningVi": "trải qua",
          "strokeCount": 11, "note": null, "examples": ["ex1"] }
      ],
      "grammar": [
        { "id": "l1-g1", "pattern": "〜たばかり", "structure": "V-た + ばかり",
          "meaningVi": "vừa mới", "usage": "…", "cautionNote": "…",
          "examples": ["ex1"],
          "exercises": [
            { "id": "l1-g1-q1", "prompt": "{日本|にほん}に＿＿＿ばかりです。",
              "options": ["{来|く}る", "{来|き}た"], "answerIndex": 1,
              "explanationVi": "…" }
          ] }
      ]
    }
  ]
}
```

Thứ tự trong mảng là thứ tự hiển thị. Trường không có trong bảng bị bỏ qua (tương thích về sau).

**Furigana**: `{chữ gốc|cách đọc}` xen văn bản thường. Với từ vựng, phần chữ gốc ghép lại phải bằng `expression` và phần đọc ghép lại phải bằng `reading`; với câu ví dụ, chữ gốc phải bằng `japanese`.

## ID và cập nhật

- ID là khóa ổn định: tiến độ học và đánh dấu của người học gắn với `(loại, id)`. **Không đổi ID** của một mục khi cập nhật gói, nếu không tiến độ của mục đó sẽ không còn khớp.
- Nhập lại một gói có cùng `pack.id` sẽ **thay thế toàn bộ** nội dung của gói đó trong một transaction. Tiến độ và đánh dấu không bị đụng tới.
- Gói đi kèm app chỉ được cài khi chưa có hoặc `version` lớn hơn bản đã cài. Tệp người dùng chọn luôn được nhập; nếu phiên bản cũ hơn bản đã cài, báo cáo có cảnh báo.

## Kiểm tra khi nhập

| Mức | Khi nào | Kết quả |
|---|---|---|
| **Nghiêm trọng** | JSON sai cú pháp/kiểu, sai `format`/`formatVersion`, thiếu `pack.id`/`title`/`version`, không có nguồn hợp lệ, `defaultSourceId` không tồn tại, không còn bài hợp lệ, lỗi ghi database | Không ghi gì; nội dung cũ giữ nguyên |
| **Bỏ qua bản ghi** | Thiếu trường bắt buộc, ID trùng (cùng loại), từ trùng `expression`+`reading`, Kanji trùng trong một bài, Kanji không phải đúng 1 chữ Hán, furigana sai hoặc không khớp, nguồn không tồn tại, bài trùng `id`/`number`, bài tập không hợp lệ (< 2 đáp án, đáp án trùng, `answerIndex` ngoài phạm vi) | Bản ghi đó bị bỏ qua (bỏ bài thì bỏ cả mục trong bài); phần còn lại được nhập |
| **Cảnh báo** | Tham chiếu câu ví dụ/chủ đề không tồn tại (bị lược bỏ), số nét ngoài 1–40 (bị bỏ trống), cùng một Kanji ở nhiều bài, số mục của bài khác mục tiêu 30/27/7 | Vẫn nhập |

Báo cáo hiển thị số bản ghi hợp lệ / bị bỏ qua theo từng loại và danh sách vấn đề kèm vị trí (vd. `lessons[0].vocabulary[3] (id=…)`). Tệp tối đa 10 MB.

## Bản quyền nội dung

Không nhập giáo trình có bản quyền nếu chưa được phép. Ghi rõ nguồn và giấy phép trong `sources`; `licenseVerified` chỉ đặt `true` sau khi đã xác minh. Gói minh họa đi kèm app do nhóm phát triển tự biên soạn, được ghi nhãn "minh họa", `licenseVerified = false` vì chưa qua kiểm duyệt chuyên môn.
