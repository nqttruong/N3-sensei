# Contrast Lab — Báo cáo biên soạn nội dung (C1–C6)

Ngày: 2026-11-18. Toàn bộ số liệu dưới đây lấy từ lệnh đã chạy thật (`drafts_tool.py`, `build_pack.py`, Gradle, emulator `emulator-5554`). Nội dung do AI soạn và **chưa có người duyệt thật**: không có nhóm nào ở trạng thái APPROVED/PUBLISHED, và app phát hành không mang gói Contrast nào.

## Trạng thái tách bạch

| Trạng thái | Kết quả |
|---|---|
| Content generation complete | **Có**: 69 nhóm, 164 mục, 328 ví dụ, 277 câu hỏi |
| Technical validation complete | **Có**: validator Python (build) và Kotlin (import) 0 ERROR; 164/164 nguồn khớp Room; bộ chấm của app: mỗi câu đúng một đáp án |
| Human review pending | **Có**: 69/69 bản nháp ở `AI_GENERATED`, không có `approval`; chưa có `reviewers.json` |
| Published content ready | **Chưa**: build phát hành từ chối (69 STATUS_NOT_PUBLISHED, không ghi tệp) |

## 1. Kiến thức nguồn đã phân tích (C1)

Lệnh: `python3 tools/content/contrast/drafts_tool.py catalog` (chỉ đọc, không sửa gói nguồn).

| Nguồn | Gói | Số mục | Bài trong gói |
|---|---|---|---|
| Ngữ pháp | `n3-grammar` v4 | 174 | 25 |
| Từ vựng | `mimikara-n3-vocab` v2 | 880 | 12 |
| Kanji | `n3-kanji` v3 | 393 | 16 |
| Bài học tổng hợp | dựng từ ba danh sách trên (CoursePlan 24 Kanji / 30 từ / 7 ngữ pháp), không phải nguồn thứ tư | 30 bài (chỉ tính Kanji N3) | — |

**Hạn chế dữ liệu nguồn** (không sửa nguồn; mục lỗi không được dùng trong nội dung):
- Ngữ pháp: 64 mục không có `usage`, 141 không có `cautionNote`; `〜に対し（て）` có hai mục cùng pattern (`n3g-g025` đối với, `n3g-g034` trái với).
- Từ vựng: cả 880 mục không có `partOfSpeech` và `furigana`; 10 mục không có ví dụ. Một số dòng có lỗi: `v178` すれ違う đọc sai (すれちあう), `v343` 翻訳 đọc sai (ほにゃく), `v358` viết 自身 nhưng nghĩa là 'tự tin' (自信), `v360` 感心 dịch 'quan tâm' (nghĩa đúng: khâm phục), `v445` だまる dịch 'lừa', `v580` 敵とな, `v598` 大分 đọc ずいぶん, `v657` 鏡面(ひょうめん). Các mục này **không** được dùng. Một số mục được dùng có nghĩa dịch ngắn hoặc lệch (`v347` 混雑 'hỗn tạp', `v874` それで 'thế thì', `v875` そこで 'tiếp theo', `v631` 思わず 'không ngờ đến'); nội dung Contrast giải thích đúng nghĩa và giữ nguyên snapshot nguồn.
- Kanji: 288/393 nghĩa là bản dịch từ KANJIDIC2 chưa kiểm duyệt (ghi trong `note` của nguồn).

## 2. Nhóm ứng viên, nhóm hợp lệ, nhóm bị loại (C2)

- Ứng viên được liệt kê trong báo cáo này (nhóm được chọn cộng nhóm bị loại ở dưới): 36 ngữ pháp, 33 từ vựng, 41 Kanji, tổng 110.
- Hợp lệ và đã biên soạn: **69 nhóm** = 22 ngữ pháp + 24 từ vựng + 23 Kanji. Đạt mục tiêu tối thiểu 20 nhóm cho mỗi loại.
- Số mục mỗi nhóm: 45 nhóm 2 mục, 22 nhóm 3 mục, 2 nhóm 4 mục.

**Nhóm bị loại và lý do**
- Không có trong nguồn: ために／ように (nguồn chỉ có ため nghĩa nguyên nhân, `n3g-g111`), ことにする／ようにする, たばかり, 開く／開ける (không có 開ける), Kanji 持／待, 険／検 (không có trong `n3-kanji`).
- Dữ liệu nguồn lỗi: 自身／自信 (`v358`), 感心／感動 (nghĩa nguồn của 感心 sai), 空く／空ける (nghĩa nguồn 'bị thủng').
- Khó bảo đảm đáp án duy nhất: はずがない／わけがない, てたまらない／てならない／てしかたがない, に決まっている／に違いない, とは限らない／わけではない, てもいい／てもかまわない, ばかりでなく／うえに／はもちろん, うちに／間に, につれて／にしたがって, とうとう／ついに／やっと.
- Trùng hoặc chồng với nhóm đã có: たびに／ごとに, てばかりいる／ばかりだ, たて／たところだ, 減る／減らす.
- Không phải cặp dễ nhầm: 機／械 (luôn đi cùng nhau trong 機械).
- Hợp lệ nhưng để đợt sau (ưu tiên chất lượng hơn số lượng): Kanji 及／級／吸, 士／仕／志, 相／箱／想, 富／副／福, 性／姓, 粉／紛, 欲／浴, 各／絡／格／落, 帯／滞, 皆／階, 効／功, 以／似, 失／矢, 卒／率, 伸／神; từ vựng 予想／想像, 熱心／夢中, 決して／絶対に.

## 3. Nội dung đã biên soạn (C3, C4)

| Hạng mục | Số lượng |
|---|---|
| Lý thuyết (tổng quan, cách nhận biết, khi nào không dùng) | 139 |
| Dòng bảng so sánh | 207 |
| Ví dụ tiếng Nhật có furigana và bản dịch | 328 (≥ 2 mỗi mục) |
| Mẹo nhớ | 69 |
| Lỗi thường gặp (câu sai / câu đúng / giải thích, có hướng nhầm) | 137 |
| Câu hỏi | **277**: MULTIPLE_CHOICE 69, FILL_IN_THE_BLANK 70, ERROR_DETECTION 69, CONTEXT_SELECTION 69 |
| Nhóm có đủ 4 dạng bài | 69/69 |

- Mỗi câu có đáp án định sẵn và lời giải thích. Mỗi lựa chọn sai đều có `itemId` (để phân tích hướng nhầm) hoặc `feedbackVi`.
- Điền chỗ trống theo đúng contract là chọn đáp án; không có `acceptedAnswers` hay so khớp gần đúng.
- **Quy tắc furigana:**
  - Phần `japanese` được suy ra từ furigana, nên hai trường không thể lệch nhau.
  - Chữ Kanji viết sai trong câu hỏi Kanji luôn mang cách đọc thật của chính chữ đó.
  - Phương án là Kanji trần không có furigana, vì có furigana sẽ lộ đáp án. Đây là 124 cảnh báo FURIGANA_MISSING mà người duyệt phải xác nhận.
- **Nhóm cần kiểm duyệt chuyên sâu:**
  - Ngữ pháp có sắc thái gần nhau: `mitaida-rashii-youda`, `ippouda-bakarida`, `gimi-gachi-ppoi`, `muki-muke`, `nimotozuite-womotoni`, `oki-ni-goto-ni`.
  - Từ vựng: `ukkari-tsui-omowazu`, `susumeru-recommend-advance` (ranh giới 勧める／薦める), `nobiru-grow-extend` (tuyến đường dùng 延びる), `sorede-sokode`.
  - Ghi chú QA còn lại: 昨夜 đọc ゆうべ (hợp lệ, nguồn ghi さくや).

### Danh sách 69 nhóm

| Nhóm | Loại | Mục (id nguồn thật) | Ví dụ | Câu hỏi |
|---|---|---|---|---|
| `cg-g-koto-ni-shiteiru-naru` | G | 〜ことにしている (`n3g-g001`), 〜ことになっている (`n3g-g002`) | 4 | 5 |
| `cg-g-okage-sei` | G | 〜おかげで (`n3g-g018`), 〜せいで (`n3g-g019`) | 4 | 4 |
| `cg-g-souda-denbun-youtai` | G | 〜そうだ（伝聞） (`n3g-g118`), 〜そうだ（様態） (`n3g-g119`) | 4 | 4 |
| `cg-g-mitaida-rashii-youda` | G | 〜みたいだ (`n3g-g005`), 〜らしい (`n3g-g006`), 〜ようだ (`n3g-g121`) | 6 | 4 |
| `cg-g-hazuda-bekida` | G | 〜はずだ (`n3g-g080`), 〜べきだ (`n3g-g139`) | 4 | 4 |
| `cg-g-wake-ga-nai-dewanai-niwa` | G | 〜わけがない (`n3g-g082`), 〜わけではない (`n3g-g083`), 〜わけにはいかない (`n3g-g084`) | 6 | 4 |
| `cg-g-aida-aidani` | G | 〜間（は） (`n3g-g152`), 〜間に (`n3g-g153`) | 4 | 4 |
| `cg-g-tokoro-ru-teiru-ta` | G | 〜るところだ (`n3g-g154`), 〜ているところだ (`n3g-g155`), 〜たところだ (`n3g-g156`) | 6 | 4 |
| `cg-g-ippouda-bakarida` | G | 〜一方だ (`n3g-g047`), 〜ばかりだ (`n3g-g161`) | 4 | 4 |
| `cg-g-gimi-gachi-ppoi` | G | 〜ぎみ (`n3g-g068`), 〜がち (`n3g-g069`), 〜っぽい (`n3g-g073`) | 6 | 4 |
| `cg-g-muki-muke` | G | 〜向き (`n3g-g070`), 〜向け (`n3g-g071`) | 4 | 4 |
| `cg-g-nikanshite-nitaishite-nitotte` | G | 〜に関して (`n3g-g022`), 〜に対して (`n3g-g025`), 〜にとって (`n3g-g037`) | 6 | 4 |
| `cg-g-oki-ni-goto-ni` | G | 〜おきに (`n3g-g157`), 〜ごとに (`n3g-g158`) | 4 | 4 |
| `cg-g-kawarini-nikawatte` | G | 〜かわり（に） (`n3g-g086`), 〜にかわって／〜にかわり (`n3g-g087`) | 4 | 4 |
| `cg-g-mama-ppanashi` | G | 〜まま (`n3g-g149`), 〜っぱなし (`n3g-g150`) | 4 | 4 |
| `cg-g-kakeru-kiru` | G | 〜かけ／〜かける (`n3g-g102`), 〜きる (`n3g-g067`) | 4 | 4 |
| `cg-g-warini-kuseni` | G | 〜わりに（は） (`n3g-g104`), 〜くせに (`n3g-g105`) | 4 | 4 |
| `cg-g-karaniwa-karatoitte` | G | 〜からには (`n3g-g066`), 〜からといって (`n3g-g091`) | 4 | 4 |
| `cg-g-kaneru-kanenai` | G | 〜かねる (`n3g-g163`), 〜かねない (`n3g-g164`) | 4 | 4 |
| `cg-g-zuniwairarenai-naiwakeniwaikanai` | G | 〜ずにはいられない (`n3g-g143`), 〜ないわけにはいかない (`n3g-g085`) | 4 | 4 |
| `cg-g-nimotozuite-womotoni` | G | 〜にもとづいて／〜にもとづき (`n3g-g093`), 〜をもとに（して） (`n3g-g094`) | 4 | 4 |
| `cg-g-toshite-nitotte` | G | 〜として (`n3g-g090`), 〜にとって (`n3g-g037`) | 4 | 4 |
| `cg-v-sameru-cold-awake` | V | 冷める (`mimikara-n3-v211`), 覚める (`mimikara-n3-v505`) | 4 | 4 |
| `cg-v-naosu-fix-cure` | V | 直す (`mimikara-n3-v161`), 治す (`mimikara-n3-v163`) | 4 | 4 |
| `cg-v-fueru-fuyasu` | V | 増える (`mimikara-n3-v431`), 増やす (`mimikara-n3-v432`) | 4 | 4 |
| `cg-v-mitsukaru-mitsukeru` | V | 見つかる (`mimikara-n3-v152`), 見つける (`mimikara-n3-v153`) | 4 | 4 |
| `cg-v-kawaku-dry-thirsty` | V | 乾く (`mimikara-n3-v137`), 渇く (`mimikara-n3-v121`) | 4 | 4 |
| `cg-v-tazuneru-ask-visit` | V | 尋ねる (`mimikara-n3-v131`), 訪ねる (`mimikara-n3-v169`) | 4 | 4 |
| `cg-v-susumeru-recommend-advance` | V | 勧める (`mimikara-n3-v734`), 薦める (`mimikara-n3-v735`), 進める (`mimikara-n3-v719`) | 6 | 4 |
| `cg-v-nobiru-grow-extend` | V | 伸びる (`mimikara-n3-v761`), 延びる (`mimikara-n3-v763`) | 4 | 4 |
| `cg-v-arawareru-appear-show` | V | 現れる (`mimikara-n3-v788`), 表れる (`mimikara-n3-v790`) | 4 | 4 |
| `cg-v-kawaru-change-replace` | V | 変わる (`mimikara-n3-v435`), 代わる (`mimikara-n3-v437`) | 4 | 4 |
| `cg-v-niru-niau` | V | 似る (`mimikara-n3-v425`), 似合う (`mimikara-n3-v424`) | 4 | 4 |
| `cg-v-kowareru-wareru-oreru-yabureru` | V | 壊れる (`mimikara-n3-v484`), 割れる (`mimikara-n3-v486`), 折れる (`mimikara-n3-v488`), 破れる (`mimikara-n3-v490`) | 8 | 4 |
| `cg-v-azukaru-azukeru` | V | 預かる (`mimikara-n3-v142`), 預ける (`mimikara-n3-v143`) | 4 | 4 |
| `cg-v-hieru-sameru` | V | 冷える (`mimikara-n3-v209`), 冷める (`mimikara-n3-v211`) | 4 | 4 |
| `cg-v-mayou-nayamu` | V | 迷う (`mimikara-n3-v502`), 悩む (`mimikara-n3-v503`) | 4 | 4 |
| `cg-v-ukkari-tsui-omowazu` | V | うっかり (`mimikara-n3-v629`), つい (`mimikara-n3-v630`), 思わず (`mimikara-n3-v631`) | 6 | 4 |
| `cg-v-seikaku-seishitsu` | V | 性格 (`mimikara-n3-v113`), 性質 (`mimikara-n3-v114`) | 4 | 4 |
| `cg-v-konzatsu-juutai` | V | 混雑 (`mimikara-n3-v347`), 渋滞 (`mimikara-n3-v348`) | 4 | 4 |
| `cg-v-jiko-jiken` | V | 事故 (`mimikara-n3-v351`), 事件 (`mimikara-n3-v352`) | 4 | 4 |
| `cg-v-tateru-build-stand` | V | 建てる (`mimikara-n3-v477`), 立てる (`mimikara-n3-v475`) | 4 | 4 |
| `cg-v-sorede-sokode` | V | それで (`mimikara-n3-v874`), そこで (`mimikara-n3-v875`) | 4 | 4 |
| `cg-v-matawa-soretomo` | V | または (`mimikara-n3-v878`), それとも (`mimikara-n3-v879`) | 4 | 4 |
| `cg-v-kikoku-kisei-kitaku` | V | 帰国 (`mimikara-n3-v034`), 帰省 (`mimikara-n3-v035`), 帰宅 (`mimikara-n3-v036`) | 6 | 4 |
| `cg-v-hakken-hatsumei` | V | 発見 (`mimikara-n3-v374`), 発明 (`mimikara-n3-v375`) | 4 | 4 |
| `cg-k-seki-tsumu-seiseki-sekinin` | K | 積 (`n3k-k07a4d`), 績 (`n3k-k07e3e`), 責 (`n3k-k08cac`) | 6 | 4 |
| `cg-k-mi-matsu` | K | 未 (`n3k-k0672a`), 末 (`n3k-k0672b`) | 4 | 4 |
| `cg-k-shoku-chi-chi` | K | 植 (`n3k-k0690d`), 値 (`n3k-k05024`), 置 (`n3k-k07f6e`) | 6 | 4 |
| `cg-k-oku-oku-i` | K | 億 (`n3k-k05104`), 憶 (`n3k-k061b6`), 意 (`n3k-k0610f`) | 6 | 4 |
| `cg-k-hi-kare-nami` | K | 皮 (`n3k-k076ae`), 彼 (`n3k-k05f7c`), 波 (`n3k-k06ce2`) | 6 | 4 |
| `cg-k-shi-gi-eda` | K | 支 (`n3k-k0652f`), 技 (`n3k-k06280`), 枝 (`n3k-k0679d`) | 6 | 4 |
| `cg-k-kyuu-motomeru-tama-sukuu` | K | 求 (`n3k-k06c42`), 球 (`n3k-k07403`), 救 (`n3k-k06551`) | 6 | 4 |
| `cg-k-yo-jo-azukeru` | K | 予 (`n3k-k04e88`), 序 (`n3k-k05e8f`), 預 (`n3k-k09810`) | 6 | 4 |
| `cg-k-kyo-kyo-kyo` | K | 巨 (`n3k-k05de8`), 拒 (`n3k-k062d2`), 距 (`n3k-k08ddd`) | 6 | 4 |
| `cg-k-soku-soku-soku` | K | 則 (`n3k-k05247`), 側 (`n3k-k05074`), 測 (`n3k-k06e2c`) | 6 | 4 |
| `cg-k-kan-kan-kan` | K | 官 (`n3k-k05b98`), 管 (`n3k-k07ba1`), 館 (`n3k-k09928`) | 6 | 4 |
| `cg-k-bou-nai-wasureru-nozomu` | K | 亡 (`n3k-k04ea1`), 忘 (`n3k-k05fd8`), 望 (`n3k-k0671b`) | 6 | 4 |
| `cg-k-ju-ukeru-sazukeru` | K | 受 (`n3k-k053d7`), 授 (`n3k-k06388`) | 4 | 4 |
| `cg-k-shoku-shoku-shiki` | K | 職 (`n3k-k08077`), 織 (`n3k-k07e54`), 識 (`n3k-k08b58`) | 6 | 4 |
| `cg-k-fuku-fuku-fuku` | K | 復 (`n3k-k05fa9`), 複 (`n3k-k08907`), 腹 (`n3k-k08179`) | 6 | 4 |
| `cg-k-hirou-suteru` | K | 拾 (`n3k-k062fe`), 捨 (`n3k-k06368`) | 4 | 4 |
| `cg-k-koori-eien-oyogu` | K | 氷 (`n3k-k06c37`), 永 (`n3k-k06c38`), 泳 (`n3k-k06cf3`) | 6 | 4 |
| `cg-k-han-hen-saka-ita` | K | 反 (`n3k-k053cd`), 返 (`n3k-k08fd4`), 坂 (`n3k-k05742`), 板 (`n3k-k0677f`) | 8 | 4 |
| `cg-k-kata-kata` | K | 型 (`n3k-k0578b`), 形 (`n3k-k05f62`) | 4 | 4 |
| `cg-k-kou-shin` | K | 幸 (`n3k-k05e78`), 辛 (`n3k-k08f9b`) | 4 | 4 |
| `cg-k-on-dan-atatakai` | K | 温 (`n3k-k06e29`), 暖 (`n3k-k06696`) | 4 | 4 |
| `cg-k-kasu-kariru` | K | 貸 (`n3k-k08cb8`), 借 (`n3k-k0501f`) | 4 | 4 |
| `cg-k-tori-shima` | K | 鳥 (`n3k-k09ce5`), 島 (`n3k-k05cf6`) | 4 | 4 |

## 4. Kết quả validation (C5)

| Kiểm tra | Lệnh | Kết quả |
|---|---|---|
| Schema + ngữ nghĩa + furigana + đáp án + tham chiếu + trùng id + nguồn (Python, build) | `drafts_tool.py check` | 0 ERROR, 124 WARNING (đều FURIGANA_MISSING ở phương án Kanji), 69 STATUS_NOT_PUBLISHED (đúng: chưa duyệt), QA biên tập 0 lỗi |
| QA biên tập riêng | như trên | Kanji ngoài `{…|…}`, furigana trên chữ không có Kanji, cách đọc không phải hiragana, cách đọc từ ghép so với từ điển nguồn, cách đọc một chữ so với âm On/Kun của dữ liệu Kanji, ≥ 2 ví dụ mỗi mục, ≥ 3 lựa chọn MC/CS, nhóm trùng tập nguồn |
| Gói TEST-ONLY (Python, import + build, strict) | `build_pack.py --check … --strict` | 0 ERROR, ĐẠT |
| Build phát hành từ bản nháp | `build_pack.py --drafts …` | Từ chối: 69 STATUS_NOT_PUBLISHED, không ghi tệp |
| Fingerprint SHA-256 | tính bằng `fingerprint.py` từ dòng nguồn như importer lưu | 164/164 khớp (Python) và 164/164 `OK` qua `RoomContrastSourceResolver` (Kotlin, Room) |
| Validator Kotlin + bộ chấm của app | `ContrastContentDraftsTest` (JVM) | 0 ERROR; 277 câu, mỗi câu đúng một đáp án; ERROR_DETECTION ghép lại đúng câu |
| So với bản trước | — | Chưa có bản phát hành trước (`releases/` trống), nên chưa áp dụng |

## 5. Độ phủ trên dữ liệu nguồn

| Loại | Mục nguồn được dùng | Tổng | Tỉ lệ |
|---|---|---|---|
| GRAMMAR | 48 | 174 | 27.6 % |
| VOCABULARY | 52 | 880 | 5.9 % |
| KANJI | 62 | 393 | 15.8 % |

- Bài học tổng hợp: 26/30 bài có ít nhất một nhóm liên quan (tính theo thứ tự CoursePlan với Kanji N3).
- Nhóm theo id nguồn trùng lặp: 0. Có 2 nguồn được dùng ở hai nhóm khác nhau, có chủ đích: `n3g-g037` (にとって) và `mimikara-n3-v211` (冷める).

## 6. Trạng thái phê duyệt

- 69/69 nhóm ở `AI_GENERATED`, `aiAssisted` được ghi ở metadata gói.
- Không có người duyệt thật và chưa có `tools/content/contrast/reviewers.json`.
- Không dùng `reviewer-01` hay một tên giả để làm bằng chứng phê duyệt.
- AI tự kiểm tra **không** được coi là phê duyệt chuyên môn.
- Gói TEST-ONLY (`app/src/androidTest/assets/contrast/test-pack-n3-unreviewed.json`) ghi `approvedBy: chua-duyet-test-only`. Nó chỉ dùng cho kiểm thử thiết bị, và công cụ từ chối ghi gói này vào `app/src/main`.

**Quy trình phát hành khi có người duyệt:**
1. Người duyệt đọc từng nhóm.
2. Người duyệt sửa nếu cần, đặt `status: APPROVED`, thêm `approval` (`authoredBy`, `approvedBy` là mã thật, `approvedRevision`, `aiAssisted: true`) và xác nhận từng cảnh báo trong `acknowledgedWarnings`.
3. Chủ dự án tạo `reviewers.json`.
4. Chạy `build_pack.py --drafts … --reviewers …`.
5. Chép gói vào assets và đăng ký nguồn cài đặt bundled.
6. Chạy lại toàn bộ test.

## 7. Trạng thái tích hợp vào ứng dụng (C6)

- **Lối vào mới**, chỉ đọc, chỉ hiện khi có nhóm đã phát hành chứa mục liên quan:
  - Học → Bài học → chi tiết bài: "Phân biệt kiến thức dễ nhầm".
  - Trang Ngữ pháp, Từ vựng, Kanji: "Xem các kiến thức dễ nhầm".
  - Hiển thị dưới dạng một hàng chip cuộn ngang; bấm vào mở màn So sánh của nhóm.
- **Mã:**
  - `ContrastDao.observeGroupsForSources`: truy vấn mới, không đổi schema.
  - `ContrastContentRepository.observeGroupsForSources`.
  - `feature/contrast/ContrastLinks.kt`.
  - Một slot tùy chọn `contrastLinks` ở bốn màn hình.
  - Không đổi CoursePlan, nội dung gốc, tiến độ hay SRS cũ.
- **Gói nội dung:** chưa được cài vào app, vì chưa được duyệt. Bản phát hành hiện tại vì thế không hiện lối vào nào (đã kiểm bằng test).

## 8. Build và kiểm thử

| Lệnh | Kết quả |
|---|---|
| `python3 tools/content/contrast/test_contrast_tools.py` | 25/25 đạt |
| `./gradlew :app:testDebugUnitTest` | **596/596** đạt (thêm `ContrastContentDraftsTest` 4 test, 2 test `ContrastLinksViewModel`) |
| `ContrastContentPackRoomTest` (Room, nguồn bundled thật) | 3/3 đạt: import 69 nhóm / 164 mục / 277 câu / 328 ví dụ; 164 nguồn `OK`; không ghi bảng người học; nhập lại → UP_TO_DATE; truy vấn lối vào theo id |
| `ContrastContentEntryPointsEndToEndTest` (toàn app) | 2/2 đạt: Bài 1 → nhóm → So sánh → luyện hết phiên → Tổng kết, attempt đều đúng, SRS cũ không đổi; trang Ngữ pháp `n3g-g018`, Từ vựng `mimikara-n3-v211` (2 nhóm), Kanji `n3k-k076ae` mở đúng nhóm; không có gói thì không hiện lối vào |
| Toàn bộ test thiết bị, `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` | **461 test: 458 đạt, 1 thất bại, 2 bỏ qua** |
| `assembleDebug`, `assembleRelease` | Đạt. APK release (chưa ký) không chứa tệp Contrast nào |
| `lintDebug` | 0 lỗi; 29 cảnh báo đều có từ trước, không cảnh báo nào trong mã Contrast |

- **Thất bại duy nhất:** `HourPickerReachabilityTest`. Lỗi này có từ trước Contrast (xem `CONTRAST_LAB_M8_REPORT.md`), không liên quan nội dung mới.
- **Bỏ qua theo thiết kế:** 2 test ML Kit (cần mạng, phải bật riêng).
- **Lỗi giao diện tìm được và đã sửa trong C6:** ban đầu các liên kết là nút xếp dọc, ở bài có nhiều nhóm sẽ làm phần đầu bài phình to. Đã đổi thành một hàng chip cuộn ngang.
- **Room v16:** schema không đổi, không có migration. Không ghi vào `learning_progress` hay `review_records`. Năm tab giữ nguyên.

## 9. Nội dung chưa hoàn thiện

- **Chưa có phê duyệt thật** cho cả 69 nhóm. Đây là điều kiện chặn phát hành.
- Giấy phép nội dung chưa xác minh (`licenseVerified: false`).
- Độ phủ từ vựng còn thấp (5,9 %). Nhiều cặp Kanji hợp lệ đang để đợt sau (mục 2).
- Thiếu `usage` ở 64 mục ngữ pháp nguồn và nghĩa Kanji chưa kiểm duyệt của nguồn. Đây là hạn chế của dữ liệu gốc, không thuộc nội dung Contrast.
- Chưa thử TalkBack bằng tay với nội dung mới.

## 10. Sự cố "Contrast Lab trống" và chế độ xem trước của bản debug (2026-11-18)

**Nguyên nhân, có bằng chứng.** Thuộc loại C và D trong phân loại sự cố: nội dung đã có và đạt kiểm tra kỹ thuật, nhưng chưa được duyệt nên không có gói nào được đóng vào APK.
- **69 bản nháp đều chưa duyệt.** `tools/content/contrast/drafts/` có 69 nhóm, tất cả `"status": "AI_GENERATED"`. Không có `reviewers.json`, không có `releases/`. `build_pack.py --drafts` từ chối với 69 STATUS_NOT_PUBLISHED.
- **App không mang gói nào.** `AndroidContentImportRepository.CONTRAST_BUNDLED_PACK_ASSETS = emptyList()`, nên `installContrast()` thoát ngay và không gọi importer. `app/src/main/assets` không có tệp Contrast nào.
- **Vì vậy Room trống và Home hiện câu báo trống.** Bảng `contrast_groups` không có dòng nào, `observeGroups()` trả danh sách rỗng, và Home hiện `contrast_empty`.
- **Không phải lỗi bộ lọc hay lỗi import bị nuốt.** Importer không hề được gọi. Khi gói được cài, Home và Thư viện hiện đủ 69 nhóm (xem test bên dưới).

**Khắc phục an toàn: chế độ xem trước chỉ dành cho bản debug.**
- **Cách ly bằng source set:**
  - `app/src/debug/assets/contrast-preview/contrast-n3-preview.json` do `drafts_tool.py debug-preview` sinh ra. Pack id `contrast-n3-preview`, tiêu đề "NỘI DUNG THỬ NGHIỆM — CHƯA KIỂM DUYỆT", `approvedBy: chua-duyet-debug-preview`.
  - `src/debug/.../ContrastPreviewAssets.kt` khai báo đường dẫn; `src/release/...` khai báo danh sách rỗng.
- **Quy tắc cài:** gói xem trước chỉ được cài khi chưa có gói đã duyệt nào trong `CONTRAST_BUNDLED_PACK_ASSETS`.
- **Nhãn trên giao diện:** cả 8 màn Contrast hiện băng nhãn khi nội dung đến từ gói xem trước.
- **Bản nháp giữ nguyên `AI_GENERATED`.** Không ai được ghi là người duyệt.

**Kết quả đo:**
- Room thật trên emulator: cài APK debug, xóa dữ liệu app trên emulator, mở app bình thường, rồi đọc database của app. Kết quả:
  - `contrast_packs` có 1 dòng là `contrast-n3-preview`.
  - `contrast_groups` có 22 ngữ pháp, 24 từ vựng, 23 Kanji.
  - `contrast_questions` có 277 câu: MC 69, FILL 70, ERROR 69, CONTEXT 69.
  - `contrast_examples` có 328 dòng.
  - Không có dòng nào trong bảng dữ liệu học; `user_version` là 16.
- APK release: không có asset `contrast-preview`, và đường dẫn của nó không xuất hiện trong dex. APK debug có cả hai.
- **Test:**

| Lệnh | Kết quả |
|---|---|
| `ContrastDebugPreviewTest` | 4/4 đạt: asset và nhãn có trong bản debug; cài 69 nhóm, không ghi dữ liệu học; cài lại thì UP_TO_DATE và giữ attempt; có gói đã duyệt thì không cài gói xem trước; tắt cờ thì không cài |
| `ContrastDebugPreviewEndToEndTest` | Đạt: Home có 69 nhóm và băng nhãn, Thư viện tìm 鳥 ra 鳥 / 島, So sánh hiện mục và ví dụ, luyện tập (1 sai, 1 bỏ qua, còn lại đúng) tới Tổng kết; SRS cũ không đổi |
| Toàn bộ test thiết bị | 466 test: 463 đạt, 1 thất bại, 2 bỏ qua |
| Unit test | 597/597 |
| Test Python | 25/25 |
| `drafts_tool.py check` | 0 ERROR |
| `lintDebug` | 0 lỗi, không cảnh báo nào trong mã Contrast |
| `assembleDebug`, `assembleRelease` | Đạt |

  Lỗi duy nhất của suite thiết bị là `HourPickerReachabilityTest`, có từ trước Contrast. 2 test ML Kit bỏ qua theo thiết kế.
- **Lưu ý:** thiết bị debug đã cài gói xem trước phải xóa dữ liệu app trước khi thử một gói đã duyệt có cùng id nhóm.
