"""Vocabulary contrast groups (mimikara-n3-vocab). AI-written drafts: every group needs a real reviewer."""
from dsl import Group, opt, seg

GROUPS = []


def group(*args):
    g = Group("VOCABULARY", *args)
    GROUPS.append(g)
    return g


# 1 ---------------------------------------------------------------------------------------------
g = group("sameru-cold-awake", "冷める và 覚める",
          "Hai động từ cùng đọc là さめる. 冷める là nguội đi (nhiệt độ, cảm xúc), 覚める là tỉnh ra (khỏi giấc ngủ, cơn say, sự mê muội).")
g.vocab("cold", "mimikara-n3-v211", "{冷|さ}める", "nguội đi; (cảm xúc) nguội lạnh", "Vật nóng mất nhiệt; hứng thú, tình cảm giảm đi.",
        [("{料理|りょうり}が{冷|さ}める", "thức ăn nguội"), ("{熱|ねつ}が{冷|さ}める", "nhiệt tình nguội đi")])
g.vocab("awake", "mimikara-n3-v505", "{覚|さ}める", "tỉnh ra, thức giấc", "Thoát khỏi trạng thái ngủ, say hay mê muội.",
        [("{目|め}が{覚|さ}める", "tỉnh giấc"), ("{酔|よ}いが{覚|さ}める", "tỉnh rượu")])
g.theory("Cùng âm, khác chữ", "Phát âm giống nhau nên phải dựa vào nghĩa để chọn chữ. Hỏi 'cái gì さめる?': đồ ăn, nước, tình cảm → 冷める (bộ băng 冫 = lạnh). Mắt, người, cơn say, giấc mơ → 覚める (覚 = giác: cảm giác, tỉnh táo).")
g.theory("Tha động từ đi kèm", "冷める (tự) ↔ 冷ます (làm nguội: スープを冷ます). 覚める (tự) ↔ 覚ます (làm tỉnh: 目を覚ます). Cặp tự/tha giữ nguyên chữ Kanji.")
g.compare("Nghĩa", cold="Nguội đi, giảm nhiệt, nguội lạnh", awake="Tỉnh ra, thức giấc")
g.compare("Chủ ngữ hay gặp", cold="料理、お茶、熱、愛情", awake="目、酔い、夢")
g.compare("Tha động từ", cold="冷ます", awake="覚ます")
g.example("cold", "{早|はや}く{食|た}べないと、スープが{冷|さ}めてしまうよ。", "Không ăn nhanh thì súp nguội mất đấy.")
g.example("cold", "{彼|かれ}への{気持|きも}ちはもう{冷|さ}めてしまった。", "Tình cảm dành cho anh ấy đã nguội lạnh rồi.")
g.example("awake", "{夜中|よなか}に{大|おお}きな{音|おと}がして、{目|め}が{覚|さ}めた。", "Nửa đêm có tiếng động lớn làm tôi tỉnh giấc.")
g.example("awake", "{冷|つめ}たい{水|みず}で{顔|かお}を{洗|あら}ったら、{眠気|ねむけ}が{覚|さ}めた。", "Rửa mặt bằng nước lạnh xong thì hết buồn ngủ.")
g.tip("冷 có bộ 冫 (băng, lạnh) → nguội. 覚 trong 感覚, 自覚 (cảm giác, tự giác) → tỉnh táo.")
g.mistake("Viết 'tỉnh giấc' bằng chữ Kanji.", "{今朝|けさ}は{五時|ごじ}に{目|め}が{冷|さ}めた。", "{今朝|けさ}は{五時|ごじ}に{目|め}が{覚|さ}めた。", "Tỉnh giấc là 目が覚める. 冷める chỉ sự nguội đi.", "awake", "cold")
g.mistake("Nói cà phê đã nguội.", "コーヒーが{覚|さ}めてしまった。", "コーヒーが{冷|さ}めてしまった。", "Cà phê mất nhiệt → 冷める.", "cold", "awake")
g.mc("cold", "Chọn câu viết đúng: 'Cà phê đã nguội mất rồi.'", [
    opt("a", "コーヒーが{冷|さ}めてしまった。", "cold"),
    opt("b", "コーヒーが{覚|さ}めてしまった。", "awake", "覚める là tỉnh giấc, tỉnh ra, không dùng cho đồ uống."),
    opt("c", "コーヒーが{冷|ひ}えてしまった。", None, "冷える là bị lạnh đi (thường do môi trường lạnh), không phải 'nguội' của đồ đang nóng."),
], "a", "Cà phê nguội đi → 冷める.")
g.fill("cold", "Chọn từ phù hợp để điền vào chỗ trống.", "{料理|りょうり}が＿＿＿うちに、どうぞ{召|め}し{上|あ}がってください。",
       "Xin mời dùng món ăn trước khi nó nguội.", [
           opt("a", "{冷|さ}めない", "cold"),
           opt("b", "{覚|さ}めない", "awake"),
       ], "a", "Món ăn nguội đi → 冷める; 冷めないうちに = trước khi nguội.")
g.error("awake", "Chọn phần viết sai chữ Kanji.", "Nhờ uống nước lạnh mà tôi tỉnh rượu.", [
    seg("{冷|つめ}たい{水|みず}を"), seg("{飲|の}んだら、"), seg("{酔|よ}いが"), seg("{冷|さ}めた。", "cold"),
], 3, "{覚|さ}めた。", "Tỉnh rượu là 酔いが覚める.")
g.context("awake", "Chọn câu đúng trong tình huống sau.", "Sáng nay chuông báo thức không kêu nhưng bạn vẫn tự tỉnh dậy lúc 6 giờ.", [
    opt("a", "{今朝|けさ}は{六時|ろくじ}に{目|め}が{覚|さ}めた。", "awake"),
    opt("b", "{今朝|けさ}は{六時|ろくじ}に{目|め}が{冷|さ}めた。", "cold", "冷める là nguội đi."),
    opt("c", "{今朝|けさ}は{六時|ろくじ}に{目|め}が{冷|ひ}えた。", None, "冷える là bị lạnh đi, không dùng cho việc tỉnh giấc."),
], "a", "目が覚める là tỉnh giấc.")

# 2 ---------------------------------------------------------------------------------------------
g = group("naosu-fix-cure", "直す và 治す",
          "Hai động từ cùng đọc là なおす. 直す là sửa (đồ vật, lỗi sai, thói quen), 治す là chữa (bệnh, vết thương).")
g.vocab("fix", "mimikara-n3-v161", "{直|なお}す", "sửa, sửa chữa, chỉnh lại", "Làm cho đồ hỏng, chỗ sai, thói xấu trở lại đúng.",
        [("{時計|とけい}を{直|なお}す", "sửa đồng hồ"), ("{間違|まちが}いを{直|なお}す", "sửa lỗi sai")])
g.vocab("cure", "mimikara-n3-v163", "{治|なお}す", "chữa (bệnh, vết thương)", "Làm cho cơ thể hết bệnh, lành vết thương.",
        [("{病気|びょうき}を{治|なお}す", "chữa bệnh"), ("{風邪|かぜ}を{治|なお}す", "chữa cảm")])
g.theory("Đồ vật hay cơ thể", "Đối tượng là đồ vật, câu chữ, thói quen, tâm trạng (機嫌を直す) → 直す. Đối tượng là bệnh, vết thương, cơ thể → 治す. Tự động từ tương ứng: 直る ／ 治る.")
g.theory("Nghĩa riêng của 直す", "Vます + 直す nghĩa là 'làm lại': 書き直す (viết lại), やり直す (làm lại). Nghĩa này chỉ có ở 直す.")
g.compare("Đối tượng", fix="Đồ vật, lỗi sai, thói quen", cure="Bệnh, vết thương")
g.compare("Tự động từ", fix="直る (được sửa)", cure="治る (khỏi bệnh)")
g.compare("Nghĩa thêm", fix="Vます＋直す: làm lại", cure="Không có")
g.example("fix", "{壊|こわ}れた{自転車|じてんしゃ}を{自分|じぶん}で{直|なお}した。", "Tôi tự sửa chiếc xe đạp bị hỏng.")
g.example("fix", "{先生|せんせい}に{作文|さくぶん}の{間違|まちが}いを{直|なお}してもらった。", "Tôi được thầy sửa lỗi trong bài văn.")
g.example("cure", "{早|はや}く{風邪|かぜ}を{治|なお}して、{学校|がっこう}に{行|い}きたい。", "Tôi muốn mau chữa khỏi cảm để đi học.")
g.example("cure", "この{薬|くすり}は{傷|きず}を{早|はや}く{治|なお}す{効果|こうか}がある。", "Thuốc này có tác dụng làm vết thương mau lành.")
g.tip("直 = thẳng (正直) → nắn lại cho thẳng, cho đúng. 治 = trị (治療) → chữa trị bệnh.")
g.mistake("Nói bác sĩ chữa bệnh.", "{医者|いしゃ}は{患者|かんじゃ}の{病気|びょうき}を{直|なお}す。", "{医者|いしゃ}は{患者|かんじゃ}の{病気|びょうき}を{治|なお}す。", "Bệnh → 治す.", "cure", "fix")
g.mistake("Nhờ thợ sửa máy giặt.", "{洗濯機|せんたくき}を{治|なお}してもらった。", "{洗濯機|せんたくき}を{直|なお}してもらった。", "Máy giặt là đồ vật → 直す.", "fix", "cure")
g.mc("cure", "Chọn câu viết đúng.", [
    opt("a", "{手術|しゅじゅつ}で{足|あし}のけがを{治|なお}した。", "cure"),
    opt("b", "{手術|しゅじゅつ}で{足|あし}のけがを{直|なお}した。", "fix", "Vết thương ở chân → 治す, không phải 直す."),
    opt("c", "{手術|しゅじゅつ}で{足|あし}のけがを{修理|しゅうり}した。", None, "修理 dùng cho máy móc, đồ vật."),
], "a", "Vết thương → 治す.")
g.fill("fix", "Chọn từ phù hợp để điền vào chỗ trống.", "レポートに{間違|まちが}いがあったので、{全部|ぜんぶ}{書|か}き＿＿＿。",
       "Báo cáo có lỗi nên tôi viết lại toàn bộ.", [
           opt("a", "{直|なお}した", "fix"),
           opt("b", "{治|なお}した", "cure", "治す chỉ dùng cho bệnh, vết thương; nghĩa 'làm lại' chỉ có ở 直す."),
       ], "a", "Vます + 直す = làm lại: 書き直す.")
g.error("cure", "Chọn phần viết sai chữ Kanji.", "Nghỉ ngơi cho tốt để chữa khỏi bệnh nhé.", [
    seg("ゆっくり{休|やす}んで、"), seg("{早|はや}く"), seg("{病気|びょうき}を"), seg("{直|なお}してね。", "fix"),
], 3, "{治|なお}してね。", "Bệnh → 治す.")
g.context("fix", "Chọn câu đúng trong tình huống sau.", "Đồng hồ của bạn bị chạy chậm, bạn mang ra tiệm để sửa.", [
    opt("a", "{時計|とけい}を{直|なお}してもらいに{行|い}く。", "fix"),
    opt("b", "{時計|とけい}を{治|なお}してもらいに{行|い}く。", "cure", "治す dùng cho bệnh; đồng hồ là đồ vật."),
    opt("c", "{時計|とけい}を{治療|ちりょう}してもらいに{行|い}く。", None, "治療 là điều trị bệnh."),
], "a", "Sửa đồ vật → 直す.")

# 3 ---------------------------------------------------------------------------------------------
g = group("fueru-fuyasu", "増える và 増やす",
          "Cặp tự động từ / tha động từ: 増える là 'tăng lên' (tự thay đổi, đi với が), 増やす là 'làm tăng' (có người tác động, đi với を).")
g.vocab("fueru", "mimikara-n3-v431", "{増|ふ}える", "tăng lên (tự động từ)", "Số lượng tự tăng lên; chủ ngữ là thứ tăng, trợ từ が.",
        [("{人口|じんこう}が{増|ふ}える", "dân số tăng"), ("{体重|たいじゅう}が{増|ふ}える", "cân nặng tăng")])
g.vocab("fuyasu", "mimikara-n3-v432", "{増|ふ}やす", "làm tăng (tha động từ)", "Có người chủ động làm cho tăng; đối tượng đi với を.",
        [("{貯金|ちょきん}を{増|ふ}やす", "tăng tiền tiết kiệm"), ("{社員|しゃいん}を{増|ふ}やす", "tăng nhân viên")])
g.theory("が hay を", "Nhìn trợ từ trước động từ: N が → 増える (tự tăng). N を → 増やす (ai đó làm tăng). Câu có chủ thể hành động (会社は、政府は、私は) và mục đích (〜ために) thường dùng 増やす.")
g.theory("Quy luật đuôi động từ", "Nhiều cặp tự/tha có đuôi 〜える (tự) ↔ 〜やす (tha): 増える／増やす、冷える／冷やす、燃える／燃やす. Ngược với cặp 減る／減らす (〜る ↔ 〜らす).")
g.compare("Loại động từ", fueru="Tự động từ (tự tăng)", fuyasu="Tha động từ (làm tăng)")
g.compare("Trợ từ", fueru="N が 増える", fuyasu="N を 増やす")
g.compare("Đối nghĩa", fueru="減る", fuyasu="減らす")
g.example("fueru", "{最近|さいきん}、{外国人|がいこくじん}{観光客|かんこうきゃく}が{増|ふ}えている。", "Gần đây khách du lịch nước ngoài đang tăng lên.")
g.example("fueru", "{冬|ふゆ}になると、{体重|たいじゅう}が{増|ふ}えやすい。", "Cứ đến mùa đông là dễ tăng cân.")
g.example("fuyasu", "{会社|かいしゃ}は{来年|らいねん}、{社員|しゃいん}を{増|ふ}やす{予定|よてい}だ。", "Năm sau công ty dự định tăng thêm nhân viên.")
g.example("fuyasu", "{日本語|にほんご}の{語彙|ごい}を{増|ふ}やすために、{毎日|まいにち}{本|ほん}を{読|よ}んでいる。", "Để tăng vốn từ tiếng Nhật, tôi đọc sách mỗi ngày.")
g.tip("増える: えー, tự tăng (が). 増やす: やす là 'làm', có người làm (を).")
g.mistake("Nói muốn tăng thời gian học.", "{勉強|べんきょう}の{時間|じかん}を{増|ふ}えたい。", "{勉強|べんきょう}の{時間|じかん}を{増|ふ}やしたい。", "Có trợ từ を và người nói chủ động làm tăng → 増やす.", "fuyasu", "fueru")
g.mistake("Nói số người dùng điện thoại tăng lên.", "スマホを{使|つか}う{人|ひと}が{増|ふ}やした。", "スマホを{使|つか}う{人|ひと}が{増|ふ}えた。", "人が (chủ ngữ tự tăng) → 増える.", "fueru", "fuyasu")
g.mc("fuyasu", "Chọn câu đúng.", [
    opt("a", "{政府|せいふ}は{保育園|ほいくえん}の{数|かず}を{増|ふ}やすことにした。", "fuyasu"),
    opt("b", "{政府|せいふ}は{保育園|ほいくえん}の{数|かず}を{増|ふ}えることにした。", "fueru", "増える là tự động từ, không đi với を."),
    opt("c", "{政府|せいふ}は{保育園|ほいくえん}の{数|かず}が{増|ふ}やすことにした。", None, "増やす cần を, không phải が."),
], "a", "Chủ thể (政府) chủ động làm tăng + を → 増やす.")
g.fill("fueru", "Chọn từ phù hợp để điền vào chỗ trống.", "{雨|あめ}が{続|つづ}いたので、{川|かわ}の{水|みず}が＿＿＿。",
       "Mưa liên tục nên nước sông dâng lên.", [
           opt("a", "{増|ふ}えた", "fueru"),
           opt("b", "{増|ふ}やした", "fuyasu", "Không ai làm tăng nước sông; nước tự tăng → 増えた."),
       ], "a", "水が (tự tăng) → 増える.")
g.error("fuyasu", "Chọn phần sai trong câu.", "Để tăng tiền tiết kiệm, tôi bớt ăn ngoài.", [
    seg("{貯金|ちょきん}を"), seg("{増|ふ}えるために、", "fueru"), seg("{外食|がいしょく}を"), seg("{減|へ}らしている。"),
], 1, "{増|ふ}やすために、", "貯金を + chủ động → 増やす.")
g.context("fueru", "Chọn câu đúng trong tình huống sau.", "Bạn lo lắng kể với bác sĩ rằng gần đây cân nặng của bạn tăng lên dù không ăn nhiều.", [
    opt("a", "あまり{食|た}べていないのに、{体重|たいじゅう}が{増|ふ}えてきたんです。", "fueru"),
    opt("b", "あまり{食|た}べていないのに、{体重|たいじゅう}を{増|ふ}やしてきたんです。", "fuyasu", "増やしてきた nghe như bạn cố ý tăng cân."),
    opt("c", "あまり{食|た}べていないのに、{体重|たいじゅう}が{増|ふ}やしてきたんです。", None, "増やす không đi với が."),
], "a", "Cân nặng tự tăng, không chủ ý → 体重が増える.")

# 4 ---------------------------------------------------------------------------------------------
g = group("mitsukaru-mitsukeru", "見つかる và 見つける",
          "見つかる là 'được tìm thấy, bị phát hiện' (tự động từ, N が), 見つける là 'tìm thấy, phát hiện ra' (tha động từ, N を, có người tìm).")
g.vocab("karu", "mimikara-n3-v152", "{見|み}つかる", "được tìm thấy, bị phát hiện", "Tự động từ: thứ bị mất hay điều bí mật hiện ra; N が 見つかる.",
        [("{財布|さいふ}が{見|み}つかる", "ví được tìm thấy"), ("{仕事|しごと}が{見|み}つかる", "tìm được việc")])
g.vocab("keru", "mimikara-n3-v153", "{見|み}つける", "tìm thấy, phát hiện ra", "Tha động từ: người chủ động tìm ra; N を 見つける.",
        [("{財布|さいふ}を{見|み}つける", "tìm thấy cái ví"), ("{間違|まちが}いを{見|み}つける", "phát hiện lỗi sai")])
g.theory("Chủ ngữ là ai", "見つける: chủ ngữ là người tìm (私は鍵を見つけた). 見つかる: chủ ngữ là thứ được tìm (鍵が見つかった), không cần nói ai tìm. Khi kết quả có được không do mình chủ động (仕事が見つかった) cũng dùng 見つかる.")
g.theory("Nghĩa 'bị phát hiện'", "見つかる còn nghĩa 'bị bắt gặp' khi làm điều lén lút: 先生に見つかる (bị thầy phát hiện). Người phát hiện đi với に.")
g.compare("Loại", karu="Tự động từ", keru="Tha động từ")
g.compare("Trợ từ", karu="N が 見つかる (người phát hiện: に)", keru="(人は) N を 見つける")
g.compare("Sắc thái", karu="Kết quả, được tìm thấy; bị bắt gặp", keru="Hành động tìm ra của ai đó")
g.example("karu", "なくした{鍵|かぎ}が{机|つくえ}の{下|した}から{見|み}つかった。", "Chiếc chìa khóa bị mất đã được tìm thấy dưới gầm bàn.")
g.example("karu", "{授業|じゅぎょう}{中|ちゅう}にゲームをしていて、{先生|せんせい}に{見|み}つかった。", "Chơi game trong giờ học và bị thầy phát hiện.")
g.example("keru", "{駅|えき}で{財布|さいふ}を{見|み}つけて、{交番|こうばん}に{届|とど}けた。", "Tôi nhặt được cái ví ở ga và đem nộp đồn cảnh sát.")
g.example("keru", "{安|やす}くておいしい{店|みせ}を{見|み}つけたよ。", "Tớ tìm được một quán vừa rẻ vừa ngon đấy.")
g.tip("かる (る tự động) = tự hiện ra; ける (る sau え) = mình tìm ra. Giống 掛かる／掛ける, 助かる／助ける.")
g.mistake("Kể mình đã tìm thấy chiếc nhẫn bị mất.", "{私|わたし}はなくした{指輪|ゆびわ}を{見|み}つかった。", "{私|わたし}はなくした{指輪|ゆびわ}を{見|み}つけた。", "Chủ ngữ là người tìm (私は) + を → 見つける.", "keru", "karu")
g.mistake("Nói việc làm thêm đã tìm được.", "やっといいアルバイトを{見|み}つかった。", "やっといいアルバイトが{見|み}つかった。", "見つかる đi với が. (Hoặc: アルバイトを見つけた.)", "karu", "keru")
g.mc("karu", "Chọn câu đúng.", [
    opt("a", "{探|さが}していた{本|ほん}がやっと{見|み}つかった。", "karu"),
    opt("b", "{探|さが}していた{本|ほん}がやっと{見|み}つけた。", "keru", "見つける cần を, không đi với が."),
    opt("c", "{探|さが}していた{本|ほん}をやっと{見|み}つかった。", None, "見つかる không đi với を."),
], "a", "本が (thứ được tìm thấy) → 見つかる.")
g.fill("keru", "Chọn từ phù hợp để điền vào chỗ trống.", "{子供|こども}が{公園|こうえん}で{小|ちい}さな{猫|ねこ}を＿＿＿。",
       "Bọn trẻ phát hiện một con mèo nhỏ trong công viên.", [
           opt("a", "{見|み}つけた", "keru"),
           opt("b", "{見|み}つかった", "karu", "見つかる không đi với を; chủ ngữ ở đây là người phát hiện (子供が)."),
       ], "a", "Người phát hiện (子供が) + 猫を → 見つける.")
g.error("karu", "Chọn phần sai trong câu.", "Chiếc điện thoại tôi đánh rơi đã được tìm thấy ở nhà ga.", [
    seg("{落|お}とした"), seg("スマホが"), seg("{駅|えき}で"), seg("{見|み}つけた。", "keru"),
], 3, "{見|み}つかった。", "スマホが (thứ được tìm thấy) → 見つかった.")
g.context("karu", "Chọn câu đúng trong tình huống sau.", "Bạn giấu kẹo trong ngăn bàn, mẹ mở ra và thấy. Bạn kể với em trai.", [
    opt("a", "{机|つくえ}に{隠|かく}していたお{菓子|かし}が、{母|はは}に{見|み}つかっちゃった。", "karu"),
    opt("b", "{机|つくえ}に{隠|かく}していたお{菓子|かし}が、{母|はは}に{見|み}つけちゃった。", "keru", "見つける không đi với が … に như vậy."),
    opt("c", "{机|つくえ}に{隠|かく}していたお{菓子|かし}を、{母|はは}が{見|み}つかっちゃった。", None, "見つかる không đi với を."),
], "a", "Bị phát hiện (người phát hiện: 母に) → 見つかる.")

# 5 ---------------------------------------------------------------------------------------------
g = group("kawaku-dry-thirsty", "乾く và 渇く",
          "Cùng đọc là かわく. 乾く là khô (mất nước: quần áo, đất, không khí), 渇く là khát (cổ họng cần nước, hoặc khao khát).")
g.vocab("dry", "mimikara-n3-v137", "{乾|かわ}く", "khô", "Đồ vật, không khí mất hơi nước: 洗濯物が乾く、空気が乾く.",
        [("{洗濯物|せんたくもの}が{乾|かわ}く", "quần áo giặt khô"), ("{空気|くうき}が{乾|かわ}く", "không khí khô")])
g.vocab("thirsty", "mimikara-n3-v121", "{渇|かわ}く", "khát", "Cổ họng cần nước: のどが渇く. Nghĩa bóng: khao khát (愛に渇く).",
        [("のどが{渇|かわ}く", "khát nước"), ("{心|こころ}が{渇|かわ}く", "tâm hồn khô khát")])
g.theory("Hỏi: cái gì かわく?", "のど (cổ họng) → 渇く, gần như cố định thành cụm のどが渇く. Quần áo, tóc, đất, sơn, không khí → 乾く.")
g.theory("Hình chữ giúp nhớ", "渇 có bộ thủy 氵 (nước): thiếu nước nên khát. 乾 là 'càn' (乾燥: khô ráo, 乾杯: cạn chén). Tha động từ chỉ có ở 乾く: 乾かす (sấy khô).")
g.compare("Nghĩa", dry="Khô (mất hơi nước)", thirsty="Khát (cần nước)")
g.compare("Cụm cố định", dry="洗濯物が乾く、空気が乾く", thirsty="のどが渇く")
g.compare("Từ liên quan", dry="乾かす、乾燥、乾杯", thirsty="渇き (cơn khát)")
g.example("dry", "{今日|きょう}は{天気|てんき}がいいから、{洗濯物|せんたくもの}がすぐ{乾|かわ}く。", "Hôm nay trời đẹp nên quần áo khô ngay.")
g.example("dry", "{冬|ふゆ}は{空気|くうき}が{乾|かわ}いているので、{肌|はだ}が{荒|あ}れやすい。", "Mùa đông không khí khô nên da dễ bị nứt nẻ.")
g.example("thirsty", "たくさん{走|はし}ったので、のどが{渇|かわ}いた。", "Chạy nhiều nên khát nước.")
g.example("thirsty", "のどが{渇|かわ}いたら、{我慢|がまん}しないで{水|みず}を{飲|の}んでください。", "Khát thì đừng nhịn, hãy uống nước.")
g.tip("渇 có 氵 (nước) → khát nước; 乾 → 乾燥 (khô ráo).")
g.mistake("Nói khát nước sau khi tập thể dục.", "{運動|うんどう}の{後|あと}は、のどが{乾|かわ}く。", "{運動|うんどう}の{後|あと}は、のどが{渇|かわ}く。", "Khát nước → のどが渇く.", "thirsty", "dry")
g.mistake("Nói tóc vẫn chưa khô.", "{髪|かみ}がまだ{渇|かわ}いていない。", "{髪|かみ}がまだ{乾|かわ}いていない。", "Tóc mất nước → 乾く.", "dry", "thirsty")
g.mc("thirsty", "Chọn câu viết đúng: 'Tôi khát nước, muốn uống gì đó lạnh.'", [
    opt("a", "のどが{渇|かわ}いたから、{冷|つめ}たい{物|もの}が{飲|の}みたい。", "thirsty"),
    opt("b", "のどが{乾|かわ}いたから、{冷|つめ}たい{物|もの}が{飲|の}みたい。", "dry", "乾く là khô (quần áo, không khí)."),
    opt("c", "のどが{乾燥|かんそう}したから、{冷|つめ}たい{物|もの}が{飲|の}みたい。", None, "乾燥 là khô ráo, không diễn tả cảm giác khát."),
], "a", "Khát nước → のどが渇く.")
g.fill("dry", "Chọn từ phù hợp để điền vào chỗ trống.", "ペンキが＿＿＿まで、{壁|かべ}に{触|さわ}らないでください。",
       "Đừng chạm vào tường cho đến khi sơn khô.", [
           opt("a", "{乾|かわ}く", "dry"),
           opt("b", "{渇|かわ}く", "thirsty"),
       ], "a", "Sơn khô đi → 乾く.")
g.error("dry", "Chọn phần viết sai chữ Kanji.", "Trời mưa nên quần áo giặt mãi không khô.", [
    seg("{雨|あめ}なので、"), seg("{洗濯物|せんたくもの}が"), seg("なかなか"), seg("{渇|かわ}かない。", "thirsty"),
], 3, "{乾|かわ}かない。", "Quần áo khô → 乾く.")
g.context("thirsty", "Chọn câu đúng trong tình huống sau.", "Sau trận bóng dưới nắng, bạn nói với đồng đội là bạn rất khát.", [
    opt("a", "ああ、のどがからからに{渇|かわ}いた。", "thirsty"),
    opt("b", "ああ、のどがからからに{乾|かわ}かした。", "dry", "乾かす là làm khô (tha động từ), không dùng cho cảm giác khát."),
    opt("c", "ああ、のどがからからに{冷|ひ}えた。", None, "冷える là bị lạnh."),
], "a", "Khát khô cổ → のどが渇く.")

# 6 ---------------------------------------------------------------------------------------------
g = group("tazuneru-ask-visit", "尋ねる và 訪ねる",
          "Cùng đọc là たずねる. 尋ねる là hỏi (thông tin), 訪ねる là đến thăm (người, nơi chốn).")
g.vocab("ask", "mimikara-n3-v131", "{尋|たず}ねる", "hỏi, hỏi thăm", "Hỏi để biết thông tin; lịch sự hơn 聞く: 道を尋ねる.",
        [("{道|みち}を{尋|たず}ねる", "hỏi đường"), ("{理由|りゆう}を{尋|たず}ねる", "hỏi lý do")])
g.vocab("visit", "mimikara-n3-v169", "{訪|たず}ねる", "đến thăm", "Đến gặp người hoặc đến một nơi: 友人を訪ねる、故郷を訪ねる.",
        [("{友人|ゆうじん}を{訪|たず}ねる", "đến thăm bạn"), ("{会社|かいしゃ}を{訪|たず}ねる", "đến thăm công ty")])
g.theory("Hỏi hay đến", "Tân ngữ là thông tin (道、理由、名前、意見) → 尋ねる. Tân ngữ là người hay nơi chốn được đến (先生、友達の家、京都) → 訪ねる. Có thể thử thay: 聞く → 尋ねる; 訪問する／行く → 訪ねる.")
g.theory("Mẹo chữ Kanji", "訪 = phỏng trong 訪問 (thăm viếng). 尋 = tầm trong 尋問 (thẩm vấn, hỏi han). Trong đề, nghĩ tới từ ghép âm Hán là chọn được chữ.")
g.compare("Nghĩa", ask="Hỏi", visit="Đến thăm")
g.compare("Tân ngữ hay gặp", ask="道、理由、名前、意見", visit="人、家、会社、故郷")
g.compare("Từ thay thế", ask="聞く、質問する", visit="訪問する")
g.example("ask", "{駅|えき}で{駅員|えきいん}に{出口|でぐち}を{尋|たず}ねた。", "Tôi hỏi nhân viên nhà ga lối ra ở đâu.")
g.example("ask", "{先生|せんせい}に{遅刻|ちこく}の{理由|りゆう}を{尋|たず}ねられた。", "Tôi bị thầy hỏi lý do đến muộn.")
g.example("visit", "{週末|しゅうまつ}、{大学|だいがく}の{先生|せんせい}のお{宅|たく}を{訪|たず}ねた。", "Cuối tuần tôi đến thăm nhà thầy giáo đại học.")
g.example("visit", "{二十年|にじゅうねん}ぶりに{生|う}まれた{町|まち}を{訪|たず}ねた。", "Sau hai mươi năm tôi mới về thăm thị trấn nơi mình sinh ra.")
g.tip("訪 → 訪問 (thăm); 尋 → 尋問 (hỏi).")
g.mistake("Viết 'đến thăm ông bà'.", "{夏休|なつやす}みに{祖父母|そふぼ}を{尋|たず}ねた。", "{夏休|なつやす}みに{祖父母|そふぼ}を{訪|たず}ねた。", "Đến thăm người → 訪ねる.", "visit", "ask")
g.mistake("Viết 'hỏi đường'.", "{知|し}らない{人|ひと}に{道|みち}を{訪|たず}ねた。", "{知|し}らない{人|ひと}に{道|みち}を{尋|たず}ねた。", "Hỏi thông tin (đường) → 尋ねる.", "ask", "visit")
g.mc("visit", "Chọn câu viết đúng.", [
    opt("a", "{来週|らいしゅう}、{取引先|とりひきさき}の{会社|かいしゃ}を{訪|たず}ねる{予定|よてい}だ。", "visit"),
    opt("b", "{来週|らいしゅう}、{取引先|とりひきさき}の{会社|かいしゃ}を{尋|たず}ねる{予定|よてい}だ。", "ask", "尋ねる là hỏi; đến công ty đối tác là 'thăm'."),
    opt("c", "{来週|らいしゅう}、{取引先|とりひきさき}の{会社|かいしゃ}に{尋|たず}ねる{予定|よてい}だ。", None, "Sai chữ và sai nghĩa: đây không phải hỏi công ty điều gì."),
], "a", "Đến thăm công ty → 訪ねる.")
g.fill("ask", "Chọn từ phù hợp để điền vào chỗ trống.", "{分|わ}からないことがあれば、{遠慮|えんりょ}なく{係|かかり}の{者|もの}にお＿＿＿ください。",
       "Nếu có điều gì không rõ, xin cứ hỏi nhân viên phụ trách.", [
           opt("a", "{尋|たず}ね", "ask"),
           opt("b", "{訪|たず}ね", "visit"),
       ], "a", "Hỏi điều không hiểu → 尋ねる.")
g.error("ask", "Chọn phần viết sai chữ Kanji.", "Cảnh sát hỏi tên và địa chỉ của tôi.", [
    seg("{警察官|けいさつかん}に"), seg("{名前|なまえ}と{住所|じゅうしょ}を"), seg("{訪|たず}ねられた。", "visit"),
], 2, "{尋|たず}ねられた。", "Hỏi thông tin (tên, địa chỉ) → 尋ねる.")
g.context("visit", "Chọn câu đúng trong tình huống sau.", "Bạn đi Kyoto và tranh thủ ghé nhà người bạn cũ sống ở đó.", [
    opt("a", "{京都|きょうと}に{行|い}ったついでに、{昔|むかし}の{友達|ともだち}を{訪|たず}ねた。", "visit"),
    opt("b", "{京都|きょうと}に{行|い}ったついでに、{昔|むかし}の{友達|ともだち}を{尋|たず}ねた。", "ask", "尋ねる là hỏi; 'hỏi người bạn' không phải ghé thăm."),
    opt("c", "{京都|きょうと}に{行|い}ったついでに、{昔|むかし}の{友達|ともだち}に{訪|たず}ねた。", None, "訪ねる đi với を (người, nơi được thăm)."),
], "a", "Ghé thăm người bạn → 友達を訪ねる.")

# 7 ---------------------------------------------------------------------------------------------
g = group("susumeru-recommend-advance", "勧める, 薦める và 進める",
          "Ba động từ cùng đọc là すすめる. 勧める là khuyên, mời (ai làm gì), 薦める là tiến cử, giới thiệu (người, vật tốt), 進める là làm cho tiến lên (công việc, kế hoạch, kim đồng hồ).")
g.vocab("kan", "mimikara-n3-v734", "{勧|すす}める", "khuyên, mời, rủ", "Khuyến khích ai làm một hành động: 運動を勧める、お茶を勧める.",
        [("{入会|にゅうかい}を{勧|すす}める", "mời gia nhập"), ("{医者|いしゃ}が{禁煙|きんえん}を{勧|すす}める", "bác sĩ khuyên bỏ thuốc")])
g.vocab("sen", "mimikara-n3-v735", "{薦|すす}める", "tiến cử, giới thiệu (cái tốt)", "Giới thiệu người, vật mình thấy tốt cho vị trí hay cho ai: 候補者を薦める、本を薦める.",
        [("{候補者|こうほしゃ}を{薦|すす}める", "tiến cử ứng viên"), ("いい{本|ほん}を{薦|すす}める", "giới thiệu sách hay")])
g.vocab("shin", "mimikara-n3-v719", "{進|すす}める", "làm cho tiến lên, xúc tiến", "Đẩy công việc, kế hoạch, thứ gì đó tiến về phía trước: 計画を進める、時計を進める.",
        [("{計画|けいかく}を{進|すす}める", "xúc tiến kế hoạch"), ("{時計|とけい}を{進|すす}める", "vặn đồng hồ nhanh lên")])
g.theory("Ba hướng nghĩa", "勧める: khuyên ai làm một việc (hành động) – 'nên làm đi'. 薦める: giới thiệu một người/vật cụ thể vì nó tốt – 'cái này hay đấy'. 進める: làm một việc tiến triển – không liên quan đến khuyên nhủ.")
g.theory("Phân biệt 勧める và 薦める", "Tân ngữ là hành động (〜ことを、運動を、受験を) → 勧める. Tân ngữ là người, sản phẩm, sách được tiến cử → 薦める. Trong thực tế 勧める đôi khi dùng rộng cho cả hai, nhưng 'tiến cử ứng viên' thì viết 薦める.")
g.compare("Nghĩa", kan="Khuyên, mời làm gì", sen="Tiến cử, giới thiệu cái tốt", shin="Làm cho tiến lên")
g.compare("Tân ngữ hay gặp", kan="運動、禁煙、入会、お茶", sen="候補者、人材、本、商品", shin="計画、仕事、話、時計")
g.compare("Từ Hán liên quan", kan="勧誘 (mời chào)", sen="推薦 (tiến cử)", shin="進行 (tiến hành)")
g.example("kan", "{医者|いしゃ}に{毎日|まいにち}{歩|ある}くことを{勧|すす}められた。", "Tôi được bác sĩ khuyên đi bộ mỗi ngày.")
g.example("kan", "{友達|ともだち}にサークルへの{入会|にゅうかい}を{勧|すす}めた。", "Tôi rủ bạn tham gia câu lạc bộ.")
g.example("sen", "{先生|せんせい}が{薦|すす}めてくれた{辞書|じしょ}はとても{使|つか}いやすい。", "Cuốn từ điển thầy giới thiệu rất dễ dùng.")
g.example("sen", "{部長|ぶちょう}は{次|つぎ}のリーダーに{田中|たなか}さんを{薦|すす}めた。", "Trưởng phòng đã tiến cử anh Tanaka làm trưởng nhóm tiếp theo.")
g.example("shin", "{予定|よてい}どおりに{工事|こうじ}を{進|すす}めている。", "Chúng tôi đang tiến hành công trình đúng kế hoạch.")
g.example("shin", "{話|はなし}を{先|さき}に{進|すす}めましょう。", "Chúng ta hãy nói tiếp sang phần sau.")
g.tip("勧 → 勧誘 (mời), 薦 → 推薦 (tiến cử), 進 → 進行 (tiến hành).")
g.mistake("Viết 'xúc tiến dự án'.", "プロジェクトを{勧|すす}める。", "プロジェクトを{進|すす}める。", "Làm cho dự án tiến triển → 進める.", "shin", "kan")
g.mistake("Viết 'tiến cử cô Sato làm ứng viên'.", "{佐藤|さとう}さんを{候補者|こうほしゃ}として{進|すす}めた。", "{佐藤|さとう}さんを{候補者|こうほしゃ}として{薦|すす}めた。", "Tiến cử người → 薦める.", "sen", "shin")
g.mc("kan", "Chọn câu viết đúng: 'Bác sĩ khuyên tôi bỏ thuốc lá.'", [
    opt("a", "{医者|いしゃ}にたばこをやめるように{勧|すす}められた。", "kan"),
    opt("b", "{医者|いしゃ}にたばこをやめるように{進|すす}められた。", "shin", "進める là làm tiến triển, không có nghĩa khuyên."),
    opt("c", "{医者|いしゃ}にたばこをやめるように{薦|すす}められた。", "sen", "薦める là tiến cử người, vật; khuyên làm một hành động dùng 勧める."),
], "a", "Khuyên ai làm một hành động → 勧める.")
g.fill("shin", "Chọn từ phù hợp để điền vào chỗ trống.", "{会議|かいぎ}の{時間|じかん}が{少|すく}ないので、{議題|ぎだい}を{早|はや}く＿＿＿ましょう。",
       "Thời gian họp ít nên ta mau chóng tiến hành các nội dung đi.", [
           opt("a", "{進|すす}め", "shin"),
           opt("b", "{勧|すす}め", "kan"),
           opt("c", "{薦|すす}め", "sen"),
       ], "a", "Làm cho nội dung họp tiến triển → 進める.")
g.error("sen", "Chọn phần viết sai chữ Kanji.", "Cuốn tiểu thuyết bạn tôi giới thiệu hay thật.", [
    seg("{友達|ともだち}が"), seg("{進|すす}めてくれた", "shin"), seg("{小説|しょうせつ}は"), seg("{本当|ほんとう}に{面白|おもしろ}かった。"),
], 1, "{薦|すす}めてくれた", "Giới thiệu một cuốn sách hay → 薦める.")
g.context("sen", "Chọn câu đúng trong tình huống sau.", "Công ty cần người phụ trách dự án mới; trưởng phòng đề cử chị Yamada vì chị có nhiều kinh nghiệm.", [
    opt("a", "{部長|ぶちょう}は{経験|けいけん}{豊富|ほうふ}な{山田|やまだ}さんを{担当者|たんとうしゃ}に{薦|すす}めた。", "sen"),
    opt("b", "{部長|ぶちょう}は{経験|けいけん}{豊富|ほうふ}な{山田|やまだ}さんを{担当者|たんとうしゃ}に{進|すす}めた。", "shin", "進める là làm tiến triển."),
    opt("c", "{部長|ぶちょう}は{経験|けいけん}{豊富|ほうふ}な{山田|やまだ}さんの{担当者|たんとうしゃ}を{勧|すす}めた。", "kan", "Câu sai cấu trúc và sai nghĩa: không phải khuyên ai làm việc gì."),
], "a", "Tiến cử người cho vị trí → 薦める.")

# 8 ---------------------------------------------------------------------------------------------
g = group("nobiru-grow-extend", "伸びる và 延びる",
          "Cùng đọc là のびる. 伸びる là dài ra, cao lên, phát triển (tóc, chiều cao, thành tích); 延びる là bị kéo dài, bị hoãn (thời gian, thời hạn, đường kéo dài thêm).")
g.vocab("grow", "mimikara-n3-v761", "{伸|の}びる", "dài ra, cao lên, tăng thêm", "Vật thể dài ra hay năng lực, số liệu phát triển: 背が伸びる、売り上げが伸びる.",
        [("{背|せ}が{伸|の}びる", "cao lên"), ("{成績|せいせき}が{伸|の}びる", "thành tích tiến bộ")])
g.vocab("extend", "mimikara-n3-v763", "{延|の}びる", "bị kéo dài, bị hoãn", "Thời gian, thời hạn, khoảng cách bị kéo dài hơn dự định: 会議が延びる、締め切りが延びる.",
        [("{締|し}め{切|き}りが{延|の}びる", "hạn chót được lùi"), ("{寿命|じゅみょう}が{延|の}びる", "tuổi thọ kéo dài")])
g.theory("Phát triển hay kéo dài thời gian", "Thứ có thể đo bằng độ dài vật lý hoặc mức phát triển (tóc, cây, chiều cao, doanh số, năng lực) → 伸びる. Thời gian, lịch trình, thời hạn, tuyến đường được nối dài → 延びる.")
g.theory("Tha động từ", "伸びる ↔ 伸ばす (để dài: 髪を伸ばす; phát huy: 才能を伸ばす). 延びる ↔ 延ばす (hoãn, kéo dài: 出発を延ばす). Cặp tự/tha giữ nguyên chữ.")
g.compare("Nghĩa", grow="Dài ra, cao lên, phát triển", extend="Kéo dài (thời gian), bị hoãn")
g.compare("Chủ ngữ hay gặp", grow="背、髪、草、売り上げ、実力", extend="会議、締め切り、出発、寿命、線路")
g.compare("Từ Hán liên quan", grow="伸長 (tăng trưởng)", extend="延期 (hoãn), 延長 (gia hạn)")
g.example("grow", "{一年|いちねん}で{息子|むすこ}の{背|せ}が{十|じゅう}センチも{伸|の}びた。", "Trong một năm con trai tôi cao thêm tận mười phân.")
g.example("grow", "{毎日|まいにち}{練習|れんしゅう}したので、{日本語|にほんご}の{力|ちから}がぐんと{伸|の}びた。", "Luyện tập mỗi ngày nên trình độ tiếng Nhật tiến bộ vượt bậc.")
g.example("extend", "{台風|たいふう}のため、{出発|しゅっぱつ}が{明日|あした}に{延|の}びた。", "Vì bão nên chuyến khởi hành bị hoãn sang ngày mai.")
g.example("extend", "{会議|かいぎ}が{一時間|いちじかん}も{延|の}びて、{疲|つか}れた。", "Cuộc họp bị kéo dài hẳn một tiếng, mệt quá.")
g.tip("延 → 延期 (hoãn), 延長 (kéo dài thời gian). 伸 → duỗi ra, lớn lên.")
g.mistake("Nói hạn nộp báo cáo được lùi đến tuần sau.", "レポートの{締|し}め{切|き}りが{来週|らいしゅう}まで{伸|の}びた。", "レポートの{締|し}め{切|き}りが{来週|らいしゅう}まで{延|の}びた。", "Thời hạn được kéo dài → 延びる.", "extend", "grow")
g.mistake("Nói tóc đã dài ra.", "{髪|かみ}がずいぶん{延|の}びたね。", "{髪|かみ}がずいぶん{伸|の}びたね。", "Tóc dài ra (độ dài vật lý) → 伸びる.", "grow", "extend")
g.mc("extend", "Chọn câu viết đúng.", [
    opt("a", "{雨|あめ}で{試合|しあい}が{来週|らいしゅう}に{延|の}びた。", "extend"),
    opt("b", "{雨|あめ}で{試合|しあい}が{来週|らいしゅう}に{伸|の}びた。", "grow", "伸びる dùng cho dài ra, phát triển; trận đấu bị hoãn → 延びる."),
    opt("c", "{雨|あめ}で{試合|しあい}が{来週|らいしゅう}に{伸|の}ばした。", None, "伸ばす là tha động từ, cần を và sai chữ."),
], "a", "Trận đấu bị hoãn → 延びる.")
g.fill("grow", "Chọn từ phù hợp để điền vào chỗ trống.", "{新商品|しんしょうひん}のおかげで、{今年|ことし}は{売|う}り{上|あ}げが{大|おお}きく＿＿＿。",
       "Nhờ sản phẩm mới, doanh số năm nay tăng mạnh.", [
           opt("a", "{伸|の}びた", "grow"),
           opt("b", "{延|の}びた", "extend"),
       ], "a", "Doanh số phát triển → 伸びる.")
g.error("grow", "Chọn phần viết sai chữ Kanji.", "Vào mùa hè, cỏ trong vườn mọc dài ra rất nhanh.", [
    seg("{夏|なつ}は"), seg("{庭|にわ}の{草|くさ}が"), seg("すぐに"), seg("{延|の}びる。", "extend"),
], 3, "{伸|の}びる。", "Cỏ mọc dài ra → 伸びる.")
g.context("extend", "Chọn câu đúng trong tình huống sau.", "Bạn báo với gia đình rằng tuyến tàu điện ngầm sẽ được kéo dài tới gần nhà vào năm sau.", [
    opt("a", "{来年|らいねん}、{地下鉄|ちかてつ}がうちの{近|ちか}くまで{延|の}びるそうだよ。", "extend"),
    opt("b", "{来年|らいねん}、{地下鉄|ちかてつ}がうちの{近|ちか}くまで{伸|の}びるそうだよ。", "grow", "Tuyến đường được nối dài thêm thường viết 延びる."),
    opt("c", "{来年|らいねん}、{地下鉄|ちかてつ}がうちの{近|ちか}くまで{進|すす}めるそうだよ。", None, "進める là tha động từ 'xúc tiến', không hợp."),
], "a", "Tuyến đường được kéo dài → 延びる.")

# 9 ---------------------------------------------------------------------------------------------
g = group("arawareru-appear-show", "現れる và 表れる",
          "Cùng đọc là あらわれる. 現れる là xuất hiện (người, vật trước đó không thấy nay hiện ra), 表れる là biểu hiện ra (cảm xúc, suy nghĩ, kết quả thể hiện ra bên ngoài).")
g.vocab("appear", "mimikara-n3-v788", "{現|あらわ}れる", "xuất hiện, hiện ra", "Người, vật, hiện tượng vốn không có mặt nay hiện ra: 犯人が現れる、太陽が現れる.",
        [("{犯人|はんにん}が{現|あらわ}れる", "thủ phạm xuất hiện"), ("{雲|くも}の{間|あいだ}から{月|つき}が{現|あらわ}れる", "trăng hiện ra giữa mây")])
g.vocab("show", "mimikara-n3-v790", "{表|あらわ}れる", "biểu hiện ra, thể hiện ra", "Thứ ở bên trong (cảm xúc, tính cách, hiệu quả) lộ ra ngoài: 顔に表れる、効果が表れる.",
        [("{気持|きも}ちが{顔|かお}に{表|あらわ}れる", "cảm xúc hiện trên mặt"), ("{効果|こうか}が{表|あらわ}れる", "hiệu quả thể hiện ra")])
g.theory("Hiện ra hay lộ ra", "Thứ có hình dạng cụ thể bước vào tầm nhìn (người, xe, mặt trời, ngôi sao mới) → 現れる (現 = hiện tại, xuất hiện). Thứ vô hình ở bên trong thể hiện ra (cảm xúc, tính cách, hiệu quả thuốc, kết quả nỗ lực) → 表れる (表 = biểu, bề mặt).")
g.theory("Tha động từ", "現れる ↔ 現す (姿を現す: lộ diện). 表れる ↔ 表す (気持ちを表す: biểu lộ cảm xúc).")
g.compare("Nghĩa", appear="Xuất hiện (có hình dạng)", show="Biểu hiện (từ bên trong ra)")
g.compare("Chủ ngữ hay gặp", appear="人、犯人、太陽、星", show="気持ち、性格、効果、結果")
g.compare("Từ Hán liên quan", appear="出現 (xuất hiện)", show="表現 (biểu hiện), 表情")
g.example("appear", "{約束|やくそく}の{時間|じかん}を{過|す}ぎても、{彼|かれ}は{現|あらわ}れなかった。", "Quá giờ hẹn mà anh ấy vẫn không xuất hiện.")
g.example("appear", "{霧|きり}が{晴|は}れて、{富士山|ふじさん}が{姿|すがた}を{現|あらわ}した。", "Sương tan, núi Phú Sĩ hiện ra.")
g.example("show", "{嬉|うれ}しい{気持|きも}ちが{顔|かお}に{表|あらわ}れている。", "Niềm vui hiện rõ trên khuôn mặt.")
g.example("show", "{薬|くすり}を{飲|の}み{始|はじ}めて{一週間|いっしゅうかん}で{効果|こうか}が{表|あらわ}れた。", "Bắt đầu uống thuốc được một tuần thì thấy hiệu quả.")
g.tip("現 → 出現 (người, vật xuất hiện). 表 → 表情 (nét mặt), 表現 (biểu hiện).")
g.mistake("Nói sự lo lắng hiện trên nét mặt.", "{不安|ふあん}な{気持|きも}ちが{顔|かお}に{現|あらわ}れていた。", "{不安|ふあん}な{気持|きも}ちが{顔|かお}に{表|あらわ}れていた。", "Cảm xúc bên trong thể hiện ra → 表れる.", "show", "appear")
g.mistake("Nói một con gấu xuất hiện trong làng.", "{村|むら}に{熊|くま}が{表|あらわ}れた。", "{村|むら}に{熊|くま}が{現|あらわ}れた。", "Con vật cụ thể xuất hiện → 現れる.", "appear", "show")
g.mc("show", "Chọn câu viết đúng.", [
    opt("a", "{努力|どりょく}の{結果|けっか}が{成績|せいせき}に{表|あらわ}れた。", "show"),
    opt("b", "{努力|どりょく}の{結果|けっか}が{成績|せいせき}に{現|あらわ}れた。", "appear", "Kết quả nỗ lực thể hiện ra (vô hình) → 表れる."),
    opt("c", "{努力|どりょく}の{結果|けっか}が{成績|せいせき}に{表|あらわ}した。", None, "表す là tha động từ, cần を."),
], "a", "Kết quả của nỗ lực thể hiện trong thành tích → 表れる.")
g.fill("appear", "Chọn từ phù hợp để điền vào chỗ trống.", "{暗|くら}い{道|みち}で、{突然|とつぜん}{知|し}らない{男|おとこ}が＿＿＿。",
       "Trên con đường tối, bỗng một người đàn ông lạ xuất hiện.", [
           opt("a", "{現|あらわ}れた", "appear"),
           opt("b", "{表|あらわ}れた", "show"),
       ], "a", "Người xuất hiện → 現れる.")
g.error("appear", "Chọn phần viết sai chữ Kanji.", "Sau cơn mưa, cầu vồng xuất hiện trên bầu trời.", [
    seg("{雨|あめ}が{止|や}んだ{後|あと}、"), seg("{空|そら}に"), seg("{虹|にじ}が"), seg("{表|あらわ}れた。", "show"),
], 3, "{現|あらわ}れた。", "Cầu vồng (hiện tượng có hình dạng) xuất hiện → 現れる.")
g.context("show", "Chọn câu đúng trong tình huống sau.", "Bạn nhận xét rằng tính cách cẩn thận của bạn mình thể hiện rõ qua nét chữ.", [
    opt("a", "{彼女|かのじょ}の{丁寧|ていねい}な{性格|せいかく}は{字|じ}に{表|あらわ}れているね。", "show"),
    opt("b", "{彼女|かのじょ}の{丁寧|ていねい}な{性格|せいかく}は{字|じ}に{現|あらわ}れているね。", "appear", "Tính cách (vô hình) thể hiện ra → 表れる."),
    opt("c", "{彼女|かのじょ}の{丁寧|ていねい}な{性格|せいかく}は{字|じ}を{表|あらわ}れているね。", None, "表れる là tự động từ, không đi với を."),
], "a", "Tính cách thể hiện qua chữ viết → 表れる.")

# 10 --------------------------------------------------------------------------------------------
g = group("kawaru-change-replace", "変わる và 代わる",
          "Cùng đọc là かわる. 変わる là thay đổi (bản thân sự vật khác đi), 代わる là thay thế (người/vật khác đứng vào vị trí).")
g.vocab("change", "mimikara-n3-v435", "{変|か}わる", "thay đổi, biến đổi", "Trạng thái, tính chất của chính sự vật khác đi: 天気が変わる、考えが変わる.",
        [("{季節|きせつ}が{変|か}わる", "mùa thay đổi"), ("{気持|きも}ちが{変|か}わる", "thay lòng, đổi ý")])
g.vocab("replace", "mimikara-n3-v437", "{代|か}わる", "thay, thay thế", "Người/vật khác làm thay vai trò: 父に代わって、担当が代わる. (Nguồn ghi chung 代わる・替わる・換わる.)",
        [("{担当者|たんとうしゃ}が{代|か}わる", "người phụ trách được thay"), ("{社長|しゃちょう}に{代|か}わって", "thay mặt giám đốc")])
g.theory("Khác đi hay đổi người", "Sự vật vẫn là nó nhưng khác trước (色が変わる、町が変わった) → 変わる. Một thứ rút đi, thứ khác vào chỗ (選手が代わる、電話を代わる: chuyển máy cho người khác) → 代わる.")
g.theory("Cụm hay gặp", "〜に代わって (thay cho), 電話代わりました (tôi đã cầm máy thay), 代わりに. 変わる có 変わった人 (người kỳ quặc), 生まれ変わる (đầu thai, lột xác).")
g.compare("Nghĩa", change="Thay đổi (chính nó khác đi)", replace="Thay thế (cái khác vào chỗ)")
g.compare("Cụm hay gặp", change="天気が変わる、気が変わる", replace="〜に代わって、電話を代わる")
g.compare("Từ Hán liên quan", change="変化 (biến hóa)", replace="代理 (đại lý), 交代 (thay phiên)")
g.example("change", "{山|やま}の{天気|てんき}は{変|か}わりやすい。", "Thời tiết trên núi dễ thay đổi.")
g.example("change", "{十年|じゅうねん}ぶりに{帰|かえ}ったら、{町|まち}がすっかり{変|か}わっていた。", "Mười năm mới về, thị trấn đã thay đổi hoàn toàn.")
g.example("replace", "{今日|きょう}は{母|はは}に{代|か}わって、{私|わたし}が{料理|りょうり}を{作|つく}る。", "Hôm nay tôi nấu ăn thay mẹ.")
g.example("replace", "{電話|でんわ}{代|か}わりました。{田中|たなか}です。", "Tôi đã nhận máy thay đây, Tanaka xin nghe.")
g.tip("変 → 変化 (thay đổi). 代 → 代理, 交代 (người khác thay vào).")
g.mistake("Thay mặt bố phát biểu.", "{父|ちち}に{変|か}わって、ご{挨拶|あいさつ}します。", "{父|ちち}に{代|か}わって、ご{挨拶|あいさつ}します。", "Thay mặt người khác → 代わる.", "replace", "change")
g.mistake("Nói lá cây đổi màu.", "{秋|あき}になって、{葉|は}の{色|いろ}が{代|か}わった。", "{秋|あき}になって、{葉|は}の{色|いろ}が{変|か}わった。", "Màu lá khác đi → 変わる.", "change", "replace")
g.mc("replace", "Chọn câu viết đúng.", [
    opt("a", "{後半|こうはん}から{山田|やまだ}{選手|せんしゅ}が{田中|たなか}{選手|せんしゅ}に{代|か}わって{出場|しゅつじょう}した。", "replace"),
    opt("b", "{後半|こうはん}から{山田|やまだ}{選手|せんしゅ}が{田中|たなか}{選手|せんしゅ}に{変|か}わって{出場|しゅつじょう}した。", "change", "Một cầu thủ thay cho cầu thủ khác → 代わる."),
    opt("c", "{後半|こうはん}から{山田|やまだ}{選手|せんしゅ}が{田中|たなか}{選手|せんしゅ}を{変|か}えて{出場|しゅつじょう}した。", None, "変える là làm thay đổi, sai nghĩa."),
], "a", "Cầu thủ vào thay → 代わる.")
g.fill("change", "Chọn từ phù hợp để điền vào chỗ trống.", "{留学|りゅうがく}してから、{物|もの}の{考|かんが}え{方|かた}が{大|おお}きく＿＿＿。",
       "Từ khi du học, cách suy nghĩ của tôi đã thay đổi nhiều.", [
           opt("a", "{変|か}わった", "change"),
           opt("b", "{代|か}わった", "replace"),
       ], "a", "Cách suy nghĩ khác đi → 変わる.")
g.error("change", "Chọn phần viết sai chữ Kanji.", "Tôi định đi nhưng đổi ý nên ở nhà.", [
    seg("{行|い}くつもりだったが、"), seg("{気|き}が"), seg("{代|か}わって、", "replace"), seg("{家|いえ}にいることにした。"),
], 2, "{変|か}わって、", "Đổi ý (気が変わる) → 変わる.")
g.context("replace", "Chọn câu đúng trong tình huống sau.", "Đồng nghiệp nghe điện thoại rồi chuyển máy cho bạn. Bạn cầm máy và nói câu mở đầu.", [
    opt("a", "お{電話|でんわ}{代|か}わりました。{鈴木|すずき}です。", "replace"),
    opt("b", "お{電話|でんわ}{変|か}わりました。{鈴木|すずき}です。", "change", "Người nghe máy được thay → 代わる."),
    opt("c", "お{電話|でんわ}{替|か}えました。{鈴木|すずき}です。", None, "替える là đổi, thay một thứ bằng thứ khác (tha động từ), không phải câu cố định khi nhận máy."),
], "a", "Câu cố định khi nhận máy thay người khác: お電話代わりました.")

# 11 --------------------------------------------------------------------------------------------
g = group("niru-niau", "似る và 似合う",
          "似る là giống (hai thứ có điểm giống nhau, nhất là ngoại hình, tính cách), 似合う là hợp, xứng (quần áo, kiểu tóc hợp với người).")
g.vocab("niru", "mimikara-n3-v425", "{似|に}る", "giống", "A は B に似ている: A giống B. Thường dùng ở dạng 似ている／似た.",
        [("{母|はは}に{似|に}ている", "giống mẹ"), ("よく{似|に}た{二人|ふたり}", "hai người rất giống nhau")])
g.vocab("niau", "mimikara-n3-v424", "{似合|にあ}う", "hợp, xứng", "Quần áo, màu sắc, kiểu tóc hợp với người: その服、よく似合うね.",
        [("{着物|きもの}が{似合|にあ}う", "mặc kimono hợp"), ("{似合|にあ}いの{夫婦|ふうふ}", "cặp vợ chồng xứng đôi")])
g.theory("Giống hay hợp", "So sánh hai đối tượng có điểm chung (顔、声、性格) → 似る (thường 似ている). Đánh giá một thứ hợp với người (服、髪型、色、メガネ) → 似合う.")
g.theory("Cách dùng ngữ pháp", "似る hầu như không dùng ở dạng hiện tại đơn để tả trạng thái; nói 父に似ている, không nói ×父に似る. 似合う dùng thẳng: 赤がよく似合う. Người được so sánh hay người mặc đi với に.")
g.compare("Nghĩa", niru="Giống", niau="Hợp, xứng")
g.compare("Cấu trúc", niru="A は B に似ている", niau="(服) が (人) に似合う")
g.compare("Dạng hay dùng", niru="似ている、似た N", niau="似合う、似合っている")
g.example("niru", "{彼女|かのじょ}は{笑|わら}った{顔|かお}がお{母|かあ}さんによく{似|に}ている。", "Lúc cười trông cô ấy rất giống mẹ.")
g.example("niru", "この{二|ふた}つの{漢字|かんじ}は{形|かたち}が{似|に}ているので、{間違|まちが}えやすい。", "Hai chữ Kanji này có hình dạng giống nhau nên dễ nhầm.")
g.example("niau", "その{青|あお}いシャツ、{君|きみ}によく{似合|にあ}うよ。", "Cái áo sơ mi xanh đó rất hợp với cậu đấy.")
g.example("niau", "{短|みじか}い{髪|かみ}も{似合|にあ}っていますね。", "Tóc ngắn cũng hợp với chị đấy.")
g.tip("似合う = 似 (giống) + 合う (khớp): 'khớp với' người mặc → hợp.")
g.mistake("Khen bạn mặc yukata đẹp.", "その{浴衣|ゆかた}、とても{似|に}ているね。", "その{浴衣|ゆかた}、とても{似合|にあ}っているね。", "Quần áo hợp với người → 似合う.", "niau", "niru")
g.mistake("Nói con trai giống bố.", "{息子|むすこ}は{父親|ちちおや}に{似合|にあ}っている。", "{息子|むすこ}は{父親|ちちおや}に{似|に}ている。", "Hai người có điểm giống → 似ている.", "niru", "niau")
g.mc("niau", "Chọn câu khen đúng.", [
    opt("a", "その{帽子|ぼうし}、{田中|たなか}さんによく{似合|にあ}いますね。", "niau"),
    opt("b", "その{帽子|ぼうし}、{田中|たなか}さんによく{似|に}ていますね。", "niru", "Cái mũ giống anh Tanaka? Ý khen 'hợp' phải dùng 似合う."),
    opt("c", "その{帽子|ぼうし}、{田中|たなか}さんによく{合|あ}わせますね。", None, "合わせる là kết hợp, điều chỉnh cho khớp, không dùng để khen."),
], "a", "Mũ hợp với người → 似合う.")
g.fill("niru", "Chọn từ phù hợp để điền vào chỗ trống.", "{日本語|にほんご}の「ありがとう」は、ポルトガル{語|ご}の「オブリガード」と{音|おと}が{少|すこ}し＿＿＿いる。",
       "Chữ ありがとう tiếng Nhật có âm hơi giống obrigado tiếng Bồ Đào Nha.", [
           opt("a", "{似|に}て", "niru"),
           opt("b", "{似合|にあ}って", "niau"),
       ], "a", "Hai từ có âm giống nhau → 似ている.")
g.error("niru", "Chọn phần sai trong câu.", "Tính cách của tôi giống bố.", [
    seg("{私|わたし}の{性格|せいかく}は"), seg("{父|ちち}に"), seg("{似合|にあ}っている。", "niau"),
], 2, "{似|に}ている。", "Hai người giống nhau về tính cách → 似ている.")
g.context("niau", "Chọn câu đúng trong tình huống sau.", "Bạn đi mua sắm với bạn thân, bạn ấy thử chiếc váy đỏ và bạn thấy rất hợp.", [
    opt("a", "わあ、{赤|あか}がすごく{似合|にあ}う！", "niau"),
    opt("b", "わあ、{赤|あか}がすごく{似|に}ている！", "niru", "似ている là giống; màu đỏ không 'giống' bạn."),
    opt("c", "わあ、{赤|あか}がすごく{間|ま}に{合|あ}う！", None, "間に合う là kịp giờ, sai nghĩa."),
], "a", "Màu sắc hợp với người → 似合う.")

# 12 --------------------------------------------------------------------------------------------
g = group("kowareru-wareru-oreru-yabureru", "壊れる, 割れる, 折れる và 破れる",
          "Bốn tự động từ đều dịch là 'bị hỏng', nhưng mỗi từ đi với một kiểu hư hại: 壊れる (máy móc, đồ vật hỏng chung), 割れる (vật cứng vỡ ra từng mảnh: kính, bát), 折れる (vật dài bị gãy gập: cành cây, xương), 破れる (vật mỏng bị rách: giấy, vải).")
g.vocab("kowareru", "mimikara-n3-v484", "{壊|こわ}れる", "bị hỏng", "Máy móc, đồ vật không dùng được nữa, hoặc vỡ nát nói chung: パソコンが壊れる.",
        [("{時計|とけい}が{壊|こわ}れる", "đồng hồ hỏng"), ("{家|いえ}が{壊|こわ}れる", "nhà bị sập")])
g.vocab("wareru", "mimikara-n3-v486", "{割|わ}れる", "bị vỡ", "Vật cứng (kính, sứ, đá) vỡ ra thành mảnh, nứt ra: 皿が割れる.",
        [("{皿|さら}が{割|わ}れる", "đĩa vỡ"), ("ガラスが{割|わ}れる", "kính vỡ")])
g.vocab("oreru", "mimikara-n3-v488", "{折|お}れる", "bị gãy", "Vật dài, mảnh bị gãy gập: 枝が折れる、骨が折れる.",
        [("{骨|ほね}が{折|お}れる", "gãy xương"), ("{鉛筆|えんぴつ}の{芯|しん}が{折|お}れる", "gãy ruột bút chì")])
g.vocab("yabureru", "mimikara-n3-v490", "{破|やぶ}れる", "bị rách", "Vật mỏng (giấy, vải, túi) bị rách, thủng: 袋が破れる.",
        [("ズボンが{破|やぶ}れる", "quần bị rách"), ("{袋|ふくろ}が{破|やぶ}れる", "túi bị rách")])
g.theory("Nhìn vào vật bị hỏng", "Máy móc, đồ có cơ chế (時計、パソコン、エアコン) → 壊れる. Vật cứng, giòn (皿、コップ、ガラス、卵) → 割れる. Vật dài, mảnh (枝、骨、箸、傘の骨) → 折れる. Vật mỏng, mềm (紙、服、袋、障子) → 破れる.")
g.theory("Tha động từ", "壊れる ↔ 壊す, 割れる ↔ 割る, 折れる ↔ 折る, 破れる ↔ 破る. Quy tắc chọn từ giống hệt cặp tự động từ.")
g.compare("Vật bị hỏng", kowareru="Máy móc, đồ vật nói chung", wareru="Vật cứng, giòn", oreru="Vật dài, mảnh", yabureru="Vật mỏng, mềm")
g.compare("Kiểu hư hại", kowareru="Không hoạt động, sập", wareru="Vỡ thành mảnh, nứt", oreru="Gãy gập", yabureru="Rách, thủng")
g.compare("Ví dụ", kowareru="パソコンが壊れた", wareru="コップが割れた", oreru="傘の骨が折れた", yabureru="紙が破れた")
g.example("kowareru", "{冷蔵庫|れいぞうこ}が{壊|こわ}れて、{食|た}べ{物|もの}が{全部|ぜんぶ}だめになった。", "Tủ lạnh hỏng nên đồ ăn hỏng hết.")
g.example("kowareru", "{大|おお}きな{地震|じしん}で、{古|ふる}い{家|いえ}がたくさん{壊|こわ}れた。", "Trận động đất lớn làm nhiều ngôi nhà cũ bị sập.")
g.example("wareru", "{手|て}が{滑|すべ}って、お{皿|さら}が{割|わ}れてしまった。", "Trượt tay làm vỡ mất cái đĩa.")
g.example("wareru", "ボールが{当|あ}たって、{窓|まど}ガラスが{割|わ}れた。", "Quả bóng đập vào làm vỡ kính cửa sổ.")
g.example("oreru", "{強|つよ}い{風|かぜ}で{木|き}の{枝|えだ}が{折|お}れた。", "Gió mạnh làm gãy cành cây.")
g.example("oreru", "スキーで{転|ころ}んで、{足|あし}の{骨|ほね}が{折|お}れた。", "Ngã khi trượt tuyết và gãy xương chân.")
g.example("yabureru", "{重|おも}い{物|もの}を{入|い}れたら、{紙袋|かみぶくろ}が{破|やぶ}れた。", "Cho đồ nặng vào thì túi giấy bị rách.")
g.example("yabureru", "{転|ころ}んだときに、ズボンのひざが{破|やぶ}れた。", "Lúc ngã, quần bị rách ở đầu gối.")
g.tip("Thử hình dung âm thanh: 割れる 'choang' (kính), 折れる 'rắc' (que gãy), 破れる 'roẹt' (giấy rách), 壊れる 'tịt' (máy hỏng).")
g.mistake("Nói chiếc cốc thủy tinh bị vỡ.", "コップが{壊|こわ}れて、{破片|はへん}が{床|ゆか}に{散|ち}らばった。", "コップが{割|わ}れて、{破片|はへん}が{床|ゆか}に{散|ち}らばった。", "Vật cứng vỡ thành mảnh → 割れる.", "wareru", "kowareru")
g.mistake("Nói chiếc áo bị rách.", "シャツが{折|お}れてしまった。", "シャツが{破|やぶ}れてしまった。", "Vải mỏng bị rách → 破れる.", "yabureru", "oreru")
g.mc("oreru", "Chọn câu đúng.", [
    opt("a", "{雪|ゆき}の{重|おも}さで{竹|たけ}が{折|お}れた。", "oreru"),
    opt("b", "{雪|ゆき}の{重|おも}さで{竹|たけ}が{破|やぶ}れた。", "yabureru", "破れる dùng cho vật mỏng như giấy, vải."),
    opt("c", "{雪|ゆき}の{重|おも}さで{竹|たけ}が{割|わ}れた。", "wareru", "割れる là vỡ ra từng mảnh hoặc nứt dọc; cây tre bị đè gãy gập → 折れる."),
], "a", "Thân tre dài bị gãy gập → 折れる.")
g.fill("kowareru", "Chọn từ phù hợp để điền vào chỗ trống.", "エアコンが＿＿＿しまったので、{修理|しゅうり}を{頼|たの}んだ。",
       "Máy điều hòa bị hỏng nên tôi đã gọi sửa.", [
           opt("a", "{壊|こわ}れて", "kowareru"),
           opt("b", "{割|わ}れて", "wareru"),
           opt("c", "{破|やぶ}れて", "yabureru"),
       ], "a", "Máy móc không hoạt động → 壊れる.")
g.error("yabureru", "Chọn phần sai trong câu.", "Bức thư cũ bị rách nên khó đọc.", [
    seg("{古|ふる}い{手紙|てがみ}が"), seg("{割|わ}れていて、", "wareru"), seg("{読|よ}みにくい。"),
], 1, "{破|やぶ}れていて、", "Giấy bị rách → 破れる.")
g.context("wareru", "Chọn câu đúng trong tình huống sau.", "Bạn làm rơi quả trứng xuống sàn bếp.", [
    opt("a", "あっ、{卵|たまご}が{割|わ}れちゃった。", "wareru"),
    opt("b", "あっ、{卵|たまご}が{折|お}れちゃった。", "oreru", "折れる dùng cho vật dài bị gãy."),
    opt("c", "あっ、{卵|たまご}が{破|やぶ}れちゃった。", "yabureru", "破れる dùng cho vật mỏng như giấy, vải."),
], "a", "Vỏ trứng cứng vỡ ra → 割れる.")

# 13 --------------------------------------------------------------------------------------------
g = group("azukaru-azukeru", "預かる và 預ける",
          "Hai động từ nhìn từ hai phía của cùng một việc gửi giữ: 預ける là gửi (đồ, tiền, con) cho người khác giữ; 預かる là nhận giữ hộ.")
g.vocab("karu", "mimikara-n3-v142", "{預|あず}かる", "nhận giữ, trông giữ (hộ)", "Người nhận đồ, tiền, trẻ của người khác để trông giữ: 荷物を預かる.",
        [("{荷物|にもつ}を{預|あず}かる", "giữ hộ hành lý"), ("{子供|こども}を{預|あず}かる", "trông hộ con")])
g.vocab("keru", "mimikara-n3-v143", "{預|あず}ける", "gửi (cho ai giữ)", "Người giao đồ, tiền, trẻ cho người khác giữ: 銀行にお金を預ける.",
        [("{銀行|ぎんこう}にお{金|かね}を{預|あず}ける", "gửi tiền ngân hàng"), ("{保育園|ほいくえん}に{子供|こども}を{預|あず}ける", "gửi con ở nhà trẻ")])
g.theory("Ai đưa, ai nhận", "Người đưa đồ đi → 預ける (A に B を預ける: gửi B cho A). Người nhận đồ để giữ → 預かる (A から B を預かる: nhận giữ B của A). Nhân viên khách sạn nói お荷物をお預かりします (chúng tôi xin giữ hành lý).")
g.theory("Mẹo trợ từ", "預ける: người nhận đi với に (フロントに預ける). 預かる: người gửi đi với から (お客様から預かる). Thấy に + người giữ → thường là 預ける.")
g.compare("Vai trò", karu="Người nhận giữ", keru="Người gửi")
g.compare("Cấu trúc", karu="(人) から N を預かる", keru="(人／所) に N を預ける")
g.compare("Câu thường nghe", karu="お荷物をお預かりします", keru="コインロッカーに荷物を預ける")
g.example("karu", "{旅行|りょこう}の{間|あいだ}、{隣|となり}の{人|ひと}が{猫|ねこ}を{預|あず}かってくれた。", "Trong lúc đi du lịch, người hàng xóm đã trông giữ con mèo cho tôi.")
g.example("karu", "{一万円|いちまんえん}お{預|あず}かりします。", "(Thu ngân) Tôi xin nhận của quý khách mười nghìn yên.")
g.example("keru", "{駅|えき}のコインロッカーに{荷物|にもつ}を{預|あず}けた。", "Tôi gửi hành lý vào tủ khóa ở ga.")
g.example("keru", "{毎月|まいつき}、{給料|きゅうりょう}の{一部|いちぶ}を{銀行|ぎんこう}に{預|あず}けている。", "Mỗi tháng tôi gửi một phần lương vào ngân hàng.")
g.tip("預ける: 'ける' như 'giao đi' (đẩy ra). 預かる: 'かる' như 'nhận về' (giống 助かる, 見つかる: phía nhận).")
g.mistake("Bạn nhờ hàng xóm giữ chìa khóa.", "{旅行中|りょこうちゅう}、{隣|となり}の{人|ひと}に{鍵|かぎ}を{預|あず}かった。", "{旅行中|りょこうちゅう}、{隣|となり}の{人|ひと}に{鍵|かぎ}を{預|あず}けた。", "Bạn là người gửi, hàng xóm là người nhận (に) → 預ける.", "keru", "karu")
g.mistake("Nhân viên lễ tân nói sẽ giữ hành lý.", "お{荷物|にもつ}をお{預|あず}けします。", "お{荷物|にもつ}をお{預|あず}かりします。", "Nhân viên là người nhận giữ → 預かる.", "karu", "keru")
g.mc("karu", "Bạn là nhân viên khách sạn. Câu nào đúng khi nhận vali của khách?", [
    opt("a", "チェックインまで、お{荷物|にもつ}をお{預|あず}かりします。", "karu"),
    opt("b", "チェックインまで、お{荷物|にもつ}をお{預|あず}けします。", "keru", "預ける là gửi đi; nhân viên là người nhận giữ."),
    opt("c", "チェックインまで、お{荷物|にもつ}をお{借|か}りします。", None, "借りる là mượn để dùng, không phải giữ hộ."),
], "a", "Nhân viên nhận giữ đồ → お預かりします.")
g.fill("keru", "Chọn từ phù hợp để điền vào chỗ trống.", "{働|はたら}いている{間|あいだ}、{子供|こども}を{保育園|ほいくえん}に＿＿＿。",
       "Trong lúc đi làm, tôi gửi con ở nhà trẻ.", [
           opt("a", "{預|あず}けている", "keru"),
           opt("b", "{預|あず}かっている", "karu"),
       ], "a", "Người mẹ gửi con cho nhà trẻ (に) → 預ける.")
g.error("keru", "Chọn phần sai trong câu.", "Tôi gửi áo khoác ở quầy gửi đồ rồi vào bảo tàng.", [
    seg("コートを"), seg("クロークに"), seg("{預|あず}かってから、", "karu"), seg("{美術館|びじゅつかん}に{入|はい}った。"),
], 2, "{預|あず}けてから、", "Tôi là người gửi áo cho quầy (に) → 預ける.")
g.context("karu", "Chọn câu đúng trong tình huống sau.", "Bạn của bạn phải đi công tác một tuần và nhờ bạn trông con chó. Bạn nhận lời.", [
    opt("a", "いいよ。{一週間|いっしゅうかん}、{犬|いぬ}を{預|あず}かるよ。", "karu"),
    opt("b", "いいよ。{一週間|いっしゅうかん}、{犬|いぬ}を{預|あず}けるよ。", "keru", "預ける là bạn gửi chó đi cho người khác, ngược tình huống."),
    opt("c", "いいよ。{一週間|いっしゅうかん}、{犬|いぬ}を{飼|か}うよ。", None, "飼う là nuôi (như của mình), không phải trông hộ."),
], "a", "Bạn là người nhận trông hộ → 預かる.")

# 14 --------------------------------------------------------------------------------------------
g = group("hieru-sameru", "冷える và 冷める",
          "Cùng chữ 冷 nhưng nghĩa khác: 冷える là trở nên lạnh (nhiệt độ hạ thấp xuống dưới mức bình thường: cơ thể, đồ uống trong tủ lạnh, thời tiết); 冷める là nguội đi (vật đang nóng trở về nhiệt độ thường; cảm xúc nguội lạnh).")
g.vocab("hieru", "mimikara-n3-v209", "{冷|ひ}える", "trở nên lạnh, bị lạnh", "Nhiệt độ hạ xuống thành lạnh: ビールが冷える、体が冷える、今朝は冷える.",
        [("{体|からだ}が{冷|ひ}える", "người bị lạnh"), ("よく{冷|ひ}えたビール", "bia ướp lạnh")])
g.vocab("sameru", "mimikara-n3-v211", "{冷|さ}める", "nguội đi", "Vật nóng mất nhiệt về mức bình thường; hứng thú giảm: スープが冷める.",
        [("お{茶|ちゃ}が{冷|さ}める", "trà nguội"), ("{興奮|こうふん}が{冷|さ}める", "cơn hưng phấn nguội đi")])
g.theory("Từ nóng về thường, hay xuống lạnh", "冷める: điểm xuất phát là nóng (料理、お風呂、お茶), kết quả là không còn nóng – thường là điều không mong muốn. 冷える: kết quả là lạnh, thấp hơn bình thường (冷蔵庫で冷える、手足が冷える、夜は冷える).")
g.theory("Mẹo kiểm tra", "Muốn đồ uống mát lạnh: ビールを冷やす → ビールが冷える (tốt). Để quên bát mì: ラーメンが冷める (tiếc). Thời tiết, cơ thể chỉ dùng 冷える. Tha động từ: 冷やす ／ 冷ます.")
g.compare("Kết quả", hieru="Lạnh (dưới mức thường)", sameru="Hết nóng (về mức thường)")
g.compare("Chủ ngữ hay gặp", hieru="ビール、体、手足、空気、天気", sameru="料理、お茶、お風呂、熱意")
g.compare("Tha động từ", hieru="冷やす (làm lạnh)", sameru="冷ます (để nguội)")
g.example("hieru", "{冷蔵庫|れいぞうこ}に{入|い}れておいたジュースがよく{冷|ひ}えている。", "Nước ép để trong tủ lạnh đã mát lạnh.")
g.example("hieru", "{今夜|こんや}は{冷|ひ}えるから、{暖|あたた}かくして{寝|ね}てね。", "Đêm nay trời lạnh, đắp ấm rồi ngủ nhé.")
g.example("sameru", "お{風呂|ふろ}が{冷|さ}めないうちに{入|はい}りなさい。", "Vào tắm đi trước khi nước bồn nguội.")
g.example("sameru", "{話|はな}しているうちに、ラーメンが{冷|さ}めてしまった。", "Mải nói chuyện nên bát ramen nguội mất.")
g.tip("冷える → 冷蔵庫 (tủ lạnh): lạnh đi. 冷める → cái nóng 'tỉnh' lại, hết nóng.")
g.mistake("Nói bia trong tủ lạnh đã lạnh.", "ビールがよく{冷|さ}めている。", "ビールがよく{冷|ひ}えている。", "Bia được làm lạnh xuống dưới mức thường → 冷える.", "hieru", "sameru")
g.mistake("Nói súp đã nguội.", "スープが{冷|ひ}えてしまった。もう{一度|いちど}{温|あたた}めよう。", "スープが{冷|さ}めてしまった。もう{一度|いちど}{温|あたた}めよう。", "Súp nóng mất nhiệt → 冷める.", "sameru", "hieru")
g.mc("hieru", "Chọn câu đúng: 'Sáng nay trời lạnh thật.'", [
    opt("a", "{今朝|けさ}はずいぶん{冷|ひ}えますね。", "hieru"),
    opt("b", "{今朝|けさ}はずいぶん{冷|さ}めますね。", "sameru", "冷める dùng cho vật nóng nguội đi, không dùng cho thời tiết."),
    opt("c", "{今朝|けさ}はずいぶん{冷|つめ}たいますね。", None, "Sai cách chia: 冷たい là tính từ."),
], "a", "Thời tiết trở lạnh → 冷える.")
g.fill("sameru", "Chọn từ phù hợp để điền vào chỗ trống.", "コーヒーが＿＿＿しまったので、{電子|でんし}レンジで{温|あたた}めた。",
       "Cà phê nguội mất nên tôi hâm lại bằng lò vi sóng.", [
           opt("a", "{冷|さ}めて", "sameru"),
           opt("b", "{冷|ひ}えて", "hieru"),
       ], "a", "Cà phê nóng mất nhiệt → 冷める.")
g.error("hieru", "Chọn phần sai trong câu.", "Đứng ngoài trời lâu nên tay chân lạnh cóng.", [
    seg("{長|なが}い{時間|じかん}"), seg("{外|そと}に{立|た}っていたので、"), seg("{手足|てあし}が"), seg("{冷|さ}めてしまった。", "sameru"),
], 3, "{冷|ひ}えてしまった。", "Cơ thể bị lạnh → 冷える.")
g.context("sameru", "Chọn câu đúng trong tình huống sau.", "Bạn nấu xong bữa tối và gọi gia đình ra ăn ngay kẻo thức ăn hết nóng.", [
    opt("a", "ご{飯|はん}が{冷|さ}めないうちに{食|た}べよう。", "sameru"),
    opt("b", "ご{飯|はん}が{冷|ひ}えないうちに{食|た}べよう。", "hieru", "冷える là trở nên lạnh; với đồ ăn vừa nấu, ý 'hết nóng' dùng 冷める."),
    opt("c", "ご{飯|はん}が{覚|さ}めないうちに{食|た}べよう。", None, "覚める là tỉnh giấc."),
], "a", "Đồ ăn nóng nguội đi → 冷める.")

# 15 --------------------------------------------------------------------------------------------
g = group("mayou-nayamu", "迷う và 悩む",
          "Cả hai liên quan đến 'băn khoăn', nhưng 迷う là lưỡng lự giữa các lựa chọn (hoặc lạc đường), còn 悩む là đau đầu, khổ tâm vì một vấn đề khó giải quyết.")
g.vocab("mayou", "mimikara-n3-v502", "{迷|まよ}う", "lạc đường; lưỡng lự", "(1) Không biết đường: 道に迷う; (2) Phân vân chọn A hay B: どちらにするか迷う.",
        [("{道|みち}に{迷|まよ}う", "lạc đường"), ("{何|なに}を{買|か}うか{迷|まよ}う", "phân vân mua gì")])
g.vocab("nayamu", "mimikara-n3-v503", "{悩|なや}む", "lo nghĩ, khổ tâm, đau đầu", "Suy nghĩ mãi về một vấn đề khó, gây khổ sở: 人間関係に悩む、将来について悩む.",
        [("{進路|しんろ}に{悩|なや}む", "đau đầu về con đường tương lai"), ("{頭痛|ずつう}に{悩|なや}む", "khổ vì chứng đau đầu")])
g.theory("Chọn lựa hay khổ tâm", "Có các phương án cụ thể để chọn (AかBか、どれにするか) → 迷う, cảm giác nhẹ nhàng hơn. Có một vấn đề làm mình khổ, lâu dài (いじめ、借金、病気、人間関係) → 悩む, cảm giác nặng nề. Nghĩa 'lạc đường' chỉ có ở 迷う.")
g.theory("Trợ từ", "悩む thường đi với に (問題に悩む) hoặc について. 迷う đi với か (〜か迷う) hoặc に (道に迷う).")
g.compare("Nghĩa", mayou="Lưỡng lự giữa lựa chọn; lạc đường", nayamu="Khổ tâm vì vấn đề khó")
g.compare("Mức nặng nề", mayou="Nhẹ, nhất thời", nayamu="Nặng, kéo dài")
g.compare("Cấu trúc", mayou="〜か迷う、道に迷う", nayamu="N に悩む、〜について悩む")
g.example("mayou", "メニューが{多|おお}くて、{何|なに}を{注文|ちゅうもん}するか{迷|まよ}ってしまう。", "Thực đơn nhiều quá nên tôi phân vân không biết gọi gì.")
g.example("mayou", "{初|はじ}めての{町|まち}で{道|みち}に{迷|まよ}った。", "Tôi bị lạc đường ở thành phố lần đầu đến.")
g.example("nayamu", "{彼|かれ}は{長|なが}い{間|あいだ}、{仕事|しごと}の{人間関係|にんげんかんけい}に{悩|なや}んでいる。", "Anh ấy đã khổ tâm vì các mối quan hệ ở nơi làm việc trong một thời gian dài.")
g.example("nayamu", "{悩|なや}みがあったら、{一人|ひとり}で{悩|なや}まないで{相談|そうだん}してね。", "Có chuyện phiền muộn thì đừng ôm một mình, hãy tâm sự nhé.")
g.tip("迷 có bộ 辶 (đi): đi mà không biết đường → lạc, lưỡng lự. 悩 có bộ 忄 (tâm): khổ trong lòng.")
g.mistake("Phân vân giữa hai chiếc áo.", "{赤|あか}と{青|あお}のどちらを{買|か}うか、{悩|なや}みに{苦|くる}しんでいる。", "{赤|あか}と{青|あお}のどちらを{買|か}うか{迷|まよ}っている。", "Chọn giữa hai lựa chọn cụ thể, nhẹ nhàng → 迷う.", "mayou", "nayamu")
g.mistake("Khổ sở vì nợ nần nhiều năm.", "{何年|なんねん}も{借金|しゃっきん}に{迷|まよ}っている。", "{何年|なんねん}も{借金|しゃっきん}に{悩|なや}んでいる。", "Vấn đề khó, gây khổ tâm lâu dài → 悩む.", "nayamu", "mayou")
g.mc("mayou", "Chọn câu đúng.", [
    opt("a", "{地図|ちず}を{持|も}っていなかったので、{山|やま}で{道|みち}に{迷|まよ}った。", "mayou"),
    opt("b", "{地図|ちず}を{持|も}っていなかったので、{山|やま}で{道|みち}に{悩|なや}んだ。", "nayamu", "Lạc đường chỉ dùng 迷う."),
    opt("c", "{地図|ちず}を{持|も}っていなかったので、{山|やま}で{道|みち}を{間違|まちが}った。", None, "間違う là tự động từ; 'đi nhầm đường' nói 道を間違えた."),
], "a", "Lạc đường → 道に迷う.")
g.fill("nayamu", "Chọn từ phù hợp để điền vào chỗ trống.", "{娘|むすめ}は{友達|ともだち}とうまくいかないことに＿＿＿いるようだ。",
       "Con gái tôi có vẻ đang khổ tâm vì không hòa hợp được với bạn bè.", [
           opt("a", "{悩|なや}んで", "nayamu"),
           opt("b", "{迷|まよ}って", "mayou"),
       ], "a", "Vấn đề quan hệ bạn bè gây khổ tâm → 悩む.")
g.error("mayou", "Chọn phần sai trong câu.", "Tôi đang phân vân không biết nên đi Kyoto hay Osaka.", [
    seg("{京都|きょうと}に{行|い}くか"), seg("{大阪|おおさか}に{行|い}くか、"), seg("{悩|なや}みに{苦|くる}しんでいる。", "nayamu"),
], 2, "{迷|まよ}っている。", "Phân vân giữa hai lựa chọn → 迷う; 悩みに苦しむ quá nặng nề cho việc chọn nơi du lịch.")
g.context("nayamu", "Chọn câu phù hợp nhất trong tình huống sau.", "Người bạn tâm sự rằng nhiều tháng nay bạn ấy mất ngủ vì áp lực công việc và không biết làm sao.", [
    opt("a", "{仕事|しごと}のストレスで、ずっと{悩|なや}んでいるんだね。", "nayamu"),
    opt("b", "{仕事|しごと}のストレスで、ずっと{迷|まよ}っているんだね。", "mayou", "迷う là phân vân giữa lựa chọn, không diễn tả khổ tâm kéo dài."),
    opt("c", "{仕事|しごと}のストレスで、ずっと{慌|あわ}てているんだね。", None, "慌てる là vội vàng, cuống quýt."),
], "a", "Khổ tâm kéo dài vì vấn đề khó → 悩む.")

# 16 --------------------------------------------------------------------------------------------
g = group("ukkari-tsui-omowazu", "うっかり, つい và 思わず",
          "Ba phó từ đều nói hành động không chủ ý. うっかり là do lơ đễnh, quên (vô ý làm sai); つい là biết không nên nhưng vẫn lỡ làm (thói quen, cám dỗ); 思わず là phản ứng bất giác ngay lúc đó (cười, kêu lên).")
g.vocab("ukkari", "mimikara-n3-v629", "うっかり", "lơ đễnh, vô ý (quên mất)", "Không chú ý nên quên hoặc làm sai: うっかり忘れる、うっかり乗り過ごす.",
        [("うっかり{忘|わす}れる", "lỡ quên mất"), ("うっかりミスをする", "mắc lỗi do sơ ý")])
g.vocab("tsui", "mimikara-n3-v630", "つい", "lỡ (biết là không nên mà vẫn)", "Không cưỡng lại được thói quen, cám dỗ: つい食べすぎる、つい言ってしまう.",
        [("つい{食|た}べすぎる", "lỡ ăn quá nhiều"), ("つい{夜更|よふ}かしする", "lỡ thức khuya")])
g.vocab("omowazu", "mimikara-n3-v631", "{思|おも}わず", "bất giác, buột (ra)", "Phản ứng tự nhiên tức thời không kịp nghĩ: 思わず笑う、思わず声を出す.",
        [("{思|おも}わず{笑|わら}う", "bật cười"), ("{思|おも}わず{叫|さけ}ぶ", "buột miệng hét lên")])
g.theory("Nguyên nhân của hành động", "うっかり: vì không để ý, đầu óc quên mất (傘を忘れた、駅を乗り過ごした). つい: biết là không nên nhưng thói quen, ham muốn thắng (ダイエット中なのについ食べた). 思わず: phản ứng tức thời trước một kích thích (びっくりして思わず叫んだ).")
g.theory("Vế sau hay gặp", "うっかり + 忘れる、間違える、〜てしまう. つい + 〜すぎる、〜てしまう (thói quen lặp lại). 思わず + 笑う、泣く、声を上げる、手を叩く (phản ứng cơ thể, cảm xúc).")
g.compare("Nguyên nhân", ukkari="Lơ đễnh, quên", tsui="Thói quen, cám dỗ", omowazu="Phản ứng bất giác")
g.compare("Người nói có biết là không nên?", ukkari="Không để ý lúc đó", tsui="Biết nhưng vẫn làm", omowazu="Không kịp nghĩ")
g.compare("Ví dụ", ukkari="うっかり財布を忘れた", tsui="つい買いすぎた", omowazu="思わず笑ってしまった")
g.example("ukkari", "うっかりして、{友達|ともだち}の{誕生日|たんじょうび}を{忘|わす}れてしまった。", "Tôi sơ ý quên mất sinh nhật bạn.")
g.example("ukkari", "{電車|でんしゃ}で{寝|ね}てしまい、うっかり{降|お}りる{駅|えき}を{過|す}ぎてしまった。", "Ngủ quên trên tàu nên lỡ đi quá ga cần xuống.")
g.example("tsui", "セールだと、いらない{物|もの}までつい{買|か}ってしまう。", "Cứ hễ giảm giá là tôi lỡ mua cả những thứ không cần.")
g.example("tsui", "{明日|あした}{試験|しけん}なのに、ついゲームをしてしまった。", "Mai thi rồi mà tôi vẫn lỡ chơi game.")
g.example("omowazu", "{彼|かれ}の{冗談|じょうだん}があまりに{面白|おもしろ}くて、{思|おも}わず{笑|わら}ってしまった。", "Câu đùa của anh ấy buồn cười quá nên tôi bật cười.")
g.example("omowazu", "{突然|とつぜん}{虫|むし}が{飛|と}んできて、{思|おも}わず「きゃっ」と{叫|さけ}んだ。", "Bỗng có con côn trùng bay tới, tôi buột miệng hét 'á'.")
g.tip("うっかり = quên (đầu óc); つい = thèm, quen tay (ý chí yếu); 思わず = phản xạ (cơ thể, cảm xúc).")
g.mistake("Ăn kiêng mà vẫn lỡ ăn bánh.", "ダイエット{中|ちゅう}なのに、{思|おも}わずケーキを{三|みっ}つも{食|た}べた。", "ダイエット{中|ちゅう}なのに、ついケーキを{三|みっ}つも{食|た}べた。",
          "Biết đang ăn kiêng mà vẫn ăn vì thèm → つい. 思わず là phản ứng tức thời, không hợp với ăn ba cái bánh.", "tsui", "omowazu")
g.mistake("Bật khóc khi xem cảnh xúc động.", "{感動的|かんどうてき}な{場面|ばめん}で、うっかり{泣|な}いてしまった。", "{感動的|かんどうてき}な{場面|ばめん}で、{思|おも}わず{泣|な}いてしまった。",
          "Khóc vì xúc động là phản ứng bất giác → 思わず. うっかり là do lơ đễnh, quên.", "omowazu", "ukkari")
g.mc("ukkari", "Câu nào diễn tả lỗi do sơ ý quên?", [
    opt("a", "うっかりして、{宿題|しゅくだい}を{家|いえ}に{置|お}いてきてしまった。", "ukkari"),
    opt("b", "{思|おも}わず、{宿題|しゅくだい}を{家|いえ}に{置|お}いてきてしまった。", "omowazu", "Để quên bài tập ở nhà không phải phản ứng bất giác."),
    opt("c", "つい、{宿題|しゅくだい}を{家|いえ}に{置|お}いてきてしまった。", "tsui", "つい dùng khi biết là không nên mà vẫn làm vì thói quen, cám dỗ."),
], "a", "Quên vì không để ý → うっかり.")
g.fill("omowazu", "Chọn từ phù hợp để điền vào chỗ trống.", "{窓|まど}の{外|そと}の{美|うつく}しい{景色|けしき}に、＿＿＿{声|こえ}を{上|あ}げた。",
       "Trước cảnh đẹp ngoài cửa sổ, tôi bất giác thốt lên.", [
           opt("a", "{思|おも}わず", "omowazu"),
           opt("b", "うっかり", "ukkari", "Thốt lên vì cảnh đẹp không phải do sơ ý quên."),
           opt("c", "つい", "tsui", "つい dùng cho thói quen, cám dỗ khó cưỡng."),
       ], "a", "Phản ứng tức thời trước cảnh đẹp → 思わず.")
g.error("tsui", "Chọn phần sai trong câu.", "Biết là có hại cho sức khỏe nhưng tối nào tôi cũng lỡ thức khuya.", [
    seg("{体|からだ}に{悪|わる}いと{分|わ}かっているのに、"), seg("{毎晩|まいばん}"), seg("{思|おも}わず{夜更|よふ}かしをしてしまう。", "omowazu"),
], 2, "つい{夜更|よふ}かしをしてしまう。", "Biết là không tốt mà vẫn lặp lại vì thói quen → つい.")
g.context("ukkari", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn đến siêu thị, ra quầy tính tiền mới nhận ra mình để quên ví ở nhà vì vội.", [
    opt("a", "{急|いそ}いでいて、うっかり{財布|さいふ}を{忘|わす}れてきちゃった。", "ukkari"),
    opt("b", "{急|いそ}いでいて、{思|おも}わず{財布|さいふ}を{忘|わす}れてきちゃった。", "omowazu", "Quên ví không phải phản ứng bất giác."),
    opt("c", "{急|いそ}いでいて、わざと{財布|さいふ}を{忘|わす}れてきちゃった。", None, "わざと là cố ý, trái tình huống."),
], "a", "Quên vì vội, không để ý → うっかり.")

# 17 --------------------------------------------------------------------------------------------
g = group("seikaku-seishitsu", "性格 và 性質",
          "Cả hai dịch là 'tính', nhưng 性格 là tính cách của con người (hoặc con vật), còn 性質 là tính chất vốn có của sự vật, vật chất (và đôi khi bản tính).")
g.vocab("kaku", "mimikara-n3-v113", "{性格|せいかく}", "tính cách", "Đặc điểm tâm lý, cách cư xử của một người: 明るい性格、性格が合う.",
        [("{明|あか}るい{性格|せいかく}", "tính cách vui vẻ"), ("{性格|せいかく}が{合|あ}う", "hợp tính nhau")])
g.vocab("shitsu", "mimikara-n3-v114", "{性質|せいしつ}", "tính chất", "Đặc điểm vốn có của vật chất, sự vật, vấn đề: 金属の性質、水に溶ける性質.",
        [("{金属|きんぞく}の{性質|せいしつ}", "tính chất của kim loại"), ("{燃|も}えやすい{性質|せいしつ}", "tính dễ cháy")])
g.theory("Người hay vật", "Nói về con người: vui vẻ, nóng nảy, nhút nhát, hợp nhau → 性格. Nói về vật chất, chất liệu, vấn đề: dễ cháy, tan trong nước, nặng hơn nước → 性質.")
g.theory("Trường hợp chồng lấn", "性質 đôi khi dùng cho bản tính bẩm sinh của người hay con vật (この犬は人なつこい性質だ). Nhưng trong giao tiếp, tả tính cách người gần như luôn dùng 性格; đề thi hỏi chất liệu thì chọn 性質.")
g.compare("Đối tượng", kaku="Con người (tính cách)", shitsu="Vật chất, sự vật (tính chất)")
g.compare("Tính từ hay đi kèm", kaku="明るい、優しい、短気な、おとなしい", shitsu="燃えやすい、溶けやすい、重い")
g.compare("Cụm hay gặp", kaku="性格が合う、性格がいい", shitsu="〜という性質がある、物質の性質")
g.example("kaku", "{姉|あね}は{明|あか}るい{性格|せいかく}で、{誰|だれ}とでもすぐ{仲良|なかよ}くなれる。", "Chị tôi tính tình vui vẻ, kết thân với ai cũng nhanh.")
g.example("kaku", "{彼|かれ}とは{性格|せいかく}が{合|あ}わないので、{一緒|いっしょ}に{仕事|しごと}をするのは{大変|たいへん}だ。", "Tôi không hợp tính với anh ấy nên làm việc cùng rất vất vả.")
g.example("shitsu", "{油|あぶら}には{水|みず}に{溶|と}けない{性質|せいしつ}がある。", "Dầu có tính chất không tan trong nước.")
g.example("shitsu", "この{素材|そざい}は{熱|ねつ}に{強|つよ}いという{性質|せいしつ}を{持|も}っている。", "Chất liệu này có tính chịu nhiệt tốt.")
g.tip("性格 = 'cách' cư xử của người. 性質 = 'chất' bên trong của vật.")
g.mistake("Tả bạn mình hiền lành.", "{山田|やまだ}さんは{優|やさ}しい{性質|せいしつ}の{人|ひと}だ。", "{山田|やまだ}さんは{優|やさ}しい{性格|せいかく}の{人|ひと}だ。", "Tả tính cách người → 性格.", "kaku", "shitsu")
g.mistake("Giải thích kim loại dẫn điện.", "{金属|きんぞく}には{電気|でんき}を{通|とお}す{性格|せいかく}がある。", "{金属|きんぞく}には{電気|でんき}を{通|とお}す{性質|せいしつ}がある。", "Đặc điểm của vật chất → 性質.", "shitsu", "kaku")
g.mc("shitsu", "Chọn câu đúng.", [
    opt("a", "ガソリンには{燃|も}えやすい{性質|せいしつ}がある。", "shitsu"),
    opt("b", "ガソリンには{燃|も}えやすい{性格|せいかく}がある。", "kaku", "性格 dùng cho người."),
    opt("c", "ガソリンには{燃|も}えやすい{態度|たいど}がある。", None, "態度 là thái độ (của người)."),
], "a", "Tính chất của xăng → 性質.")
g.fill("kaku", "Chọn từ phù hợp để điền vào chỗ trống.", "{弟|おとうと}はおとなしい＿＿＿なので、{初対面|しょたいめん}の{人|ひと}とはあまり{話|はな}さない。",
       "Em trai tôi tính hiền lành ít nói nên ít nói chuyện với người mới gặp.", [
           opt("a", "{性格|せいかく}", "kaku"),
           opt("b", "{性質|せいしつ}", "shitsu"),
       ], "a", "Tính cách của người → 性格.")
g.error("kaku", "Chọn phần sai trong câu.", "Hai người họ hợp tính nhau nên làm bạn lâu năm.", [
    seg("あの{二人|ふたり}は"), seg("{性質|せいしつ}が{合|あ}うので、", "shitsu"), seg("{長|なが}い{間|あいだ}"), seg("{友達|ともだち}でいる。"),
], 1, "{性格|せいかく}が{合|あ}うので、", "Hợp tính nhau → 性格が合う.")
g.context("shitsu", "Chọn câu phù hợp nhất trong tình huống sau.", "Giáo viên hóa học giải thích rằng muối có đặc điểm dễ tan trong nước.", [
    opt("a", "{塩|しお}には{水|みず}に{溶|と}けやすい{性質|せいしつ}があります。", "shitsu"),
    opt("b", "{塩|しお}には{水|みず}に{溶|と}けやすい{性格|せいかく}があります。", "kaku", "性格 dùng cho người."),
    opt("c", "{塩|しお}には{水|みず}に{溶|と}けやすい{特技|とくぎ}があります。", None, "特技 là tài lẻ, kỹ năng đặc biệt của người."),
], "a", "Tính chất của chất (muối) → 性質.")

# 18 --------------------------------------------------------------------------------------------
g = group("konzatsu-juutai", "混雑 và 渋滞",
          "Cả hai nói 'đông nghẹt', nhưng 混雑 là nơi chốn đông người (ga, cửa hàng, tàu), còn 渋滞 là xe cộ ùn tắc trên đường.")
g.vocab("konzatsu", "mimikara-n3-v347", "{混雑|こんざつ}", "đông đúc, chen chúc", "Một nơi có quá nhiều người: 駅が混雑する、混雑した店内. (Nguồn dịch là 'hỗn tạp'; nghĩa thường dùng là đông đúc.)",
        [("{駅|えき}が{混雑|こんざつ}する", "nhà ga đông đúc"), ("{混雑|こんざつ}を{避|さ}ける", "tránh chỗ đông")])
g.vocab("juutai", "mimikara-n3-v348", "{渋滞|じゅうたい}", "tắc đường, ùn tắc", "Xe cộ nối đuôi nhau không đi được: 道路が渋滞する、渋滞に巻き込まれる.",
        [("{道路|どうろ}が{渋滞|じゅうたい}する", "đường bị tắc"), ("{渋滞|じゅうたい}に{巻|ま}き{込|こ}まれる", "bị kẹt xe")])
g.theory("Người hay xe", "Đông người ở một địa điểm (駅、デパート、電車の中、会場) → 混雑. Xe cộ ùn tắc trên đường (高速道路、国道、帰省ラッシュの車) → 渋滞.")
g.theory("Từ gần nghĩa", "混む (văn nói) dùng được cho cả hai: 道が混む、店が混む. Khi dùng từ Hán trang trọng, phải chọn 混雑 hay 渋滞 cho đúng đối tượng.")
g.compare("Đối tượng", konzatsu="Người ở một nơi", juutai="Xe cộ trên đường")
g.compare("Nơi hay đi kèm", konzatsu="駅、店、電車、会場", juutai="道路、高速道路、交差点")
g.compare("Từ văn nói", konzatsu="混む、込む", juutai="混む、詰まる")
g.example("konzatsu", "{年末|ねんまつ}のデパートはとても{混雑|こんざつ}している。", "Trung tâm thương mại cuối năm rất đông đúc.")
g.example("konzatsu", "{朝|あさ}の{電車|でんしゃ}の{混雑|こんざつ}を{避|さ}けて、{早|はや}めに{家|いえ}を{出|で}る。", "Tôi ra khỏi nhà sớm để tránh cảnh chen chúc trên tàu buổi sáng.")
g.example("juutai", "{事故|じこ}のため、{高速道路|こうそくどうろ}が{十|じゅう}キロも{渋滞|じゅうたい}している。", "Vì tai nạn mà đường cao tốc bị tắc đến mười cây số.")
g.example("juutai", "{渋滞|じゅうたい}に{巻|ま}き{込|こ}まれて、{会議|かいぎ}に{遅|おく}れた。", "Bị kẹt xe nên tôi trễ họp.")
g.tip("渋滞: 'chậm (滞) như nhựa (渋) dính' → xe đứng im. 混雑: 'trộn lẫn (混) lộn xộn (雑)' → người chen chúc.")
g.mistake("Nói con đường trước ga bị tắc xe.", "{駅前|えきまえ}の{道路|どうろ}がひどく{混雑|こんざつ}していて、バスが{動|うご}かない。", "{駅前|えきまえ}の{道路|どうろ}がひどく{渋滞|じゅうたい}していて、バスが{動|うご}かない。", "Xe cộ ùn tắc trên đường → 渋滞.", "juutai", "konzatsu")
g.mistake("Nói công viên giải trí đông người.", "{休日|きゅうじつ}の{遊園地|ゆうえんち}は{渋滞|じゅうたい}している。", "{休日|きゅうじつ}の{遊園地|ゆうえんち}は{混雑|こんざつ}している。", "Đông người ở một địa điểm → 混雑.", "konzatsu", "juutai")
g.mc("juutai", "Chọn câu đúng.", [
    opt("a", "お{盆|ぼん}の{時期|じき}は、{高速道路|こうそくどうろ}がよく{渋滞|じゅうたい}する。", "juutai"),
    opt("b", "お{盆|ぼん}の{時期|じき}は、{高速道路|こうそくどうろ}がよく{混雑|こんざつ}する。", "konzatsu", "Với xe cộ trên đường cao tốc, từ chuẩn là 渋滞."),
    opt("c", "お{盆|ぼん}の{時期|じき}は、{高速道路|こうそくどうろ}がよく{満員|まんいん}になる。", None, "満員 là kín chỗ (người trong xe, phòng), không dùng cho đường."),
], "a", "Xe ùn tắc trên cao tốc → 渋滞.")
g.fill("konzatsu", "Chọn từ phù hợp để điền vào chỗ trống.", "{初詣|はつもうで}の{神社|じんじゃ}は{大変|たいへん}＿＿＿していますので、ご{注意|ちゅうい}ください。",
       "Đền thờ dịp lễ đầu năm rất đông người, xin quý khách chú ý.", [
           opt("a", "{混雑|こんざつ}", "konzatsu"),
           opt("b", "{渋滞|じゅうたい}", "juutai"),
       ], "a", "Đông người ở đền → 混雑.")
g.error("konzatsu", "Chọn phần sai trong câu.", "Sân ga giờ cao điểm đông nghẹt người.", [
    seg("ラッシュアワーの"), seg("ホームは"), seg("{人|ひと}で"), seg("{渋滞|じゅうたい}していた。", "juutai"),
], 3, "{混雑|こんざつ}していた。", "Người đông ở sân ga → 混雑.")
g.context("juutai", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn gọi điện báo sẽ đến muộn vì taxi kẹt giữa dòng xe không nhúc nhích.", [
    opt("a", "すみません、{渋滞|じゅうたい}で{少|すこ}し{遅|おく}れます。", "juutai"),
    opt("b", "すみません、{混雑|こんざつ}で{少|すこ}し{遅|おく}れます。", "konzatsu", "Kẹt xe trên đường → 渋滞."),
    opt("c", "すみません、{停電|ていでん}で{少|すこ}し{遅|おく}れます。", None, "停電 là mất điện."),
], "a", "Kẹt xe → 渋滞.")

# 19 --------------------------------------------------------------------------------------------
g = group("jiko-jiken", "事故 và 事件",
          "事故 là tai nạn, sự cố xảy ra ngoài ý muốn (giao thông, máy móc); 事件 là vụ việc, thường do con người gây ra có chủ ý và liên quan đến pháp luật (trộm, giết người, lừa đảo).")
g.vocab("jiko", "mimikara-n3-v351", "{事故|じこ}", "tai nạn, sự cố", "Việc không ai muốn xảy ra, do sơ suất hay rủi ro: 交通事故、事故に遭う.",
        [("{交通|こうつう}{事故|じこ}", "tai nạn giao thông"), ("{事故|じこ}に{遭|あ}う", "gặp tai nạn")])
g.vocab("jiken", "mimikara-n3-v352", "{事件|じけん}", "vụ án, vụ việc", "Sự việc gây chú ý, thường là hành vi phạm tội có chủ đích: 殺人事件、事件が起きる.",
        [("{殺人|さつじん}{事件|じけん}", "vụ án giết người"), ("{事件|じけん}を{解決|かいけつ}する", "phá án")])
g.theory("Có ai cố ý không?", "Không ai cố ý, do sơ suất hay rủi ro (xe đâm, máy hỏng, cháy do chập điện) → 事故. Có người cố ý gây ra, cảnh sát điều tra, có thủ phạm (trộm, cướp, giết người) → 事件.")
g.theory("Từ đi kèm", "事故: 交通事故、事故に遭う、事故を起こす、事故現場. 事件: 殺人事件、盗難事件、事件の犯人、事件を解決する. Thấy 犯人 (thủ phạm) → 事件.")
g.compare("Bản chất", jiko="Ngoài ý muốn, sơ suất", jiken="Thường có chủ ý, phạm tội")
g.compare("Từ hay đi kèm", jiko="交通、遭う、起こす", jiken="殺人、盗難、犯人、解決")
g.compare("Người liên quan", jiko="Nạn nhân, người gây tai nạn", jiken="Thủ phạm, cảnh sát điều tra")
g.example("jiko", "{雪|ゆき}の{日|ひ}は{交通|こうつう}{事故|じこ}が{多|おお}い。", "Ngày tuyết rơi có nhiều tai nạn giao thông.")
g.example("jiko", "{工場|こうじょう}で{機械|きかい}の{事故|じこ}があって、{二人|ふたり}がけがをした。", "Ở nhà máy xảy ra sự cố máy móc làm hai người bị thương.")
g.example("jiken", "{昨日|きのう}、{近所|きんじょ}で{盗難|とうなん}{事件|じけん}があった。", "Hôm qua gần nhà xảy ra một vụ trộm.")
g.example("jiken", "{警察|けいさつ}はまだ{事件|じけん}の{犯人|はんにん}を{捕|つか}まえていない。", "Cảnh sát vẫn chưa bắt được thủ phạm vụ án.")
g.tip("故 trong 故障 (hỏng hóc) → sự cố. 件 trong 用件, 案件 (vụ việc) → vụ án.")
g.mistake("Nói về vụ trộm ở cửa hàng tiện lợi.", "コンビニで{強盗|ごうとう}{事故|じこ}があった。", "コンビニで{強盗|ごうとう}{事件|じけん}があった。", "Cướp là hành vi cố ý phạm tội → 事件.", "jiken", "jiko")
g.mistake("Nói bị tai nạn xe đạp.", "{自転車|じてんしゃ}で{転|ころ}んで、{事件|じけん}に{遭|あ}った。", "{自転車|じてんしゃ}で{転|ころ}んで、{事故|じこ}に{遭|あ}った。", "Ngã xe ngoài ý muốn → 事故.", "jiko", "jiken")
g.mc("jiken", "Chọn câu đúng.", [
    opt("a", "その{事件|じけん}の{犯人|はんにん}は{三日後|みっかご}に{逮捕|たいほ}された。", "jiken"),
    opt("b", "その{事故|じこ}の{犯人|はんにん}は{三日後|みっかご}に{逮捕|たいほ}された。", "jiko", "Có 犯人 (thủ phạm) và bị bắt → vụ án 事件."),
    opt("c", "その{故障|こしょう}の{犯人|はんにん}は{三日後|みっかご}に{逮捕|たいほ}された。", None, "故障 là hỏng hóc máy móc."),
], "a", "Có thủ phạm bị bắt → 事件.")
g.fill("jiko", "Chọn từ phù hợp để điền vào chỗ trống.", "スピードを{出|だ}しすぎて、{車|くるま}の＿＿＿を{起|お}こしてしまった。",
       "Chạy quá tốc độ nên tôi đã gây tai nạn xe.", [
           opt("a", "{事故|じこ}", "jiko"),
           opt("b", "{事件|じけん}", "jiken"),
       ], "a", "Tai nạn do sơ suất → 事故を起こす.")
g.error("jiken", "Chọn phần sai trong câu.", "Cảnh sát đang điều tra vụ lừa đảo đó.", [
    seg("{警察|けいさつ}は"), seg("その{詐欺|さぎ}"), seg("{事故|じこ}を", "jiko"), seg("{調|しら}べている。"),
], 2, "{事件|じけん}を", "Lừa đảo là hành vi cố ý phạm tội → 詐欺事件.")
g.context("jiko", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn đến muộn vì tàu dừng lại do một sự cố kỹ thuật trên đường ray.", [
    opt("a", "{線路|せんろ}で{事故|じこ}があって、{電車|でんしゃ}が{止|と}まっていたんです。", "jiko"),
    opt("b", "{線路|せんろ}で{事件|じけん}があって、{電車|でんしゃ}が{止|と}まっていたんです。", "jiken", "事件 gợi ý có tội phạm; sự cố kỹ thuật → 事故."),
    opt("c", "{線路|せんろ}で{渋滞|じゅうたい}があって、{電車|でんしゃ}が{止|と}まっていたんです。", None, "渋滞 là kẹt xe trên đường bộ."),
], "a", "Sự cố ngoài ý muốn → 事故.")

# 20 --------------------------------------------------------------------------------------------
g = group("tateru-build-stand", "建てる và 立てる",
          "Cùng đọc là たてる. 建てる là xây (nhà, tòa nhà, công trình), 立てる là dựng đứng (cột, cờ), và nghĩa trừu tượng: lập (kế hoạch, mục tiêu), gây (tiếng ồn).")
g.vocab("build", "mimikara-n3-v477", "{建|た}てる", "xây, xây dựng", "Dựng công trình kiến trúc: 家を建てる、ビルを建てる.",
        [("{家|いえ}を{建|た}てる", "xây nhà"), ("{記念碑|きねんひ}を{建|た}てる", "dựng bia kỷ niệm")])
g.vocab("stand", "mimikara-n3-v475", "{立|た}てる", "dựng lên; lập (kế hoạch)", "Làm cho đứng thẳng; lập ra: 旗を立てる、計画を立てる、音を立てる.",
        [("{計画|けいかく}を{立|た}てる", "lập kế hoạch"), ("{音|おと}を{立|た}てる", "gây tiếng động")])
g.theory("Công trình hay mọi thứ khác", "Chỉ khi xây công trình (家、ビル、寺、学校、橋の塔) → 建てる (建 trong 建物, 建設). Cột, cờ, biển, que (dựng đứng) và nghĩa trừu tượng (計画、目標、予定、音、腹) → 立てる.")
g.theory("Tự động từ", "建てる ↔ 建つ (家が建つ: nhà được xây lên). 立てる ↔ 立つ (旗が立つ, 計画が立つ). Quy tắc chọn chữ giống nhau.")
g.compare("Nghĩa", build="Xây công trình", stand="Dựng đứng; lập (kế hoạch, mục tiêu)")
g.compare("Tân ngữ hay gặp", build="家、ビル、学校、寺", stand="旗、看板、計画、目標、音")
g.compare("Từ Hán liên quan", build="建物、建設、建築", stand="立派、設立、独立")
g.example("build", "{父|ちち}は{三十年前|さんじゅうねんまえ}にこの{家|いえ}を{建|た}てた。", "Bố tôi xây ngôi nhà này ba mươi năm trước.")
g.example("build", "{駅前|えきまえ}に{新|あたら}しいホテルが{建|た}てられる{予定|よてい}だ。", "Trước ga dự kiến sẽ xây một khách sạn mới.")
g.example("stand", "{夏休|なつやす}みの{旅行|りょこう}の{計画|けいかく}を{立|た}てた。", "Tôi đã lập kế hoạch du lịch nghỉ hè.")
g.example("stand", "{夜|よる}は{大|おお}きな{音|おと}を{立|た}てないでください。", "Ban đêm xin đừng gây tiếng động lớn.")
g.tip("建 → 建物 (tòa nhà): chỉ dùng cho công trình. Còn lại (cờ, kế hoạch, tiếng động) → 立.")
g.mistake("Viết 'lập mục tiêu'.", "{今年|ことし}の{目標|もくひょう}を{建|た}てた。", "{今年|ことし}の{目標|もくひょう}を{立|た}てた。", "Lập mục tiêu (nghĩa trừu tượng) → 立てる.", "stand", "build")
g.mistake("Viết 'xây trường học'.", "{村|むら}に{新|あたら}しい{小学校|しょうがっこう}を{立|た}てた。", "{村|むら}に{新|あたら}しい{小学校|しょうがっこう}を{建|た}てた。", "Xây công trình → 建てる.", "build", "stand")
g.mc("stand", "Chọn câu viết đúng.", [
    opt("a", "{試験|しけん}に{向|む}けて、{勉強|べんきょう}の{予定|よてい}を{立|た}てた。", "stand"),
    opt("b", "{試験|しけん}に{向|む}けて、{勉強|べんきょう}の{予定|よてい}を{建|た}てた。", "build", "建てる chỉ dùng cho công trình kiến trúc."),
    opt("c", "{試験|しけん}に{向|む}けて、{勉強|べんきょう}の{予定|よてい}を{作|つく}られた。", None, "作られた là bị động, không hợp chủ ngữ."),
], "a", "Lập kế hoạch học → 予定を立てる.")
g.fill("build", "Chọn từ phù hợp để điền vào chỗ trống.", "いつか{海|うみ}の{見|み}える{場所|ばしょ}に{家|いえ}を＿＿＿たい。",
       "Một ngày nào đó tôi muốn xây nhà ở nơi nhìn ra biển.", [
           opt("a", "{建|た}て", "build"),
           opt("b", "{立|た}て", "stand"),
       ], "a", "Xây nhà → 建てる.")
g.error("stand", "Chọn phần viết sai chữ Kanji.", "Họ cắm cờ trên đỉnh núi.", [
    seg("{彼|かれ}らは"), seg("{山|やま}の{頂上|ちょうじょう}に"), seg("{旗|はた}を"), seg("{建|た}てた。", "build"),
], 3, "{立|た}てた。", "Dựng đứng lá cờ → 立てる.")
g.context("build", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn kể rằng thành phố sẽ xây một sân vận động mới cạnh sông.", [
    opt("a", "{川|かわ}のそばに{新|あたら}しいスタジアムを{建|た}てるそうだ。", "build"),
    opt("b", "{川|かわ}のそばに{新|あたら}しいスタジアムを{立|た}てるそうだ。", "stand", "Sân vận động là công trình → 建てる."),
    opt("c", "{川|かわ}のそばに{新|あたら}しいスタジアムが{建|た}てるそうだ。", None, "建てる là tha động từ, cần を (hoặc dùng 建つ với が)."),
], "a", "Xây công trình → 建てる.")

# 21 --------------------------------------------------------------------------------------------
g = group("sorede-sokode", "それで và そこで",
          "Cả hai nối hai câu theo quan hệ nguyên nhân – kết quả. それで nêu kết quả tự nhiên của sự việc trước (vì thế mà…); そこで nêu hành động có chủ ý được chọn để giải quyết tình huống trước (vì vậy, bèn…).")
g.vocab("sorede", "mimikara-n3-v874", "それで", "vì thế, do đó; rồi sao nữa", "Kết quả đương nhiên của việc nói trước, hoặc hỏi tiếp câu chuyện (それで、どうなったの？).",
        [("それで{遅|おく}れた", "vì thế nên trễ"), ("それで、どうしたの", "rồi sao, cậu làm gì")])
g.vocab("sokode", "mimikara-n3-v875", "そこで", "vì vậy (bèn), thế là (quyết định)", "Trước tình huống đó, người ta chủ động làm một việc để đối phó: そこで、〜することにした.",
        [("そこで{相談|そうだん}した", "bèn đi hỏi ý kiến"), ("そこで{提案|ていあん}がある", "vì vậy tôi có đề xuất")])
g.theory("Kết quả tự nhiên hay giải pháp chủ động", "Câu sau là kết quả không do ai quyết định (遅れた、風邪をひいた、売り切れた) → それで. Câu sau là hành động có chủ ý để giải quyết vấn đề ở câu trước (〜することにした、〜てみた、提案します) → そこで.")
g.theory("Cách dùng riêng", "それで còn dùng để giục người kia kể tiếp: それで？ (rồi sao?). そこで không dùng như vậy. Trong văn bản đề xuất hay gặp mẫu: 〜が問題だ。そこで、〜を提案する.")
g.compare("Câu sau", sorede="Kết quả tự nhiên", sokode="Hành động chủ động để đối phó")
g.compare("Dùng hỏi tiếp chuyện", sorede="Được: それで？", sokode="Không")
g.compare("Mẫu hay gặp", sorede="〜。それで、〜てしまった", sokode="〜。そこで、〜ことにした")
g.example("sorede", "{昨日|きのう}は{雨|あめ}に{濡|ぬ}れた。それで、{風邪|かぜ}をひいてしまった。", "Hôm qua tôi bị ướt mưa. Vì thế mà bị cảm.")
g.example("sorede", "「{財布|さいふ}をなくしちゃって…」「それで、どうしたの？」", "— Tớ làm mất ví… — Rồi sao, cậu làm thế nào?")
g.example("sokode", "{一人|ひとり}では{仕事|しごと}が{終|お}わらなかった。そこで、{同僚|どうりょう}に{手伝|てつだ}いを{頼|たの}んだ。", "Một mình làm không xong việc. Vì vậy tôi nhờ đồng nghiệp giúp.")
g.example("sokode", "{最近|さいきん}、{社員|しゃいん}の{残業|ざんぎょう}が{多|おお}い。そこで、{会社|かいしゃ}は{新|あたら}しいルールを{作|つく}った。", "Gần đây nhân viên làm thêm giờ nhiều. Vì vậy công ty lập quy định mới.")
g.tip("それで = 'thế là… (xảy ra)'. そこで = 'ở chỗ đó (そこ), ta bèn…' – một quyết định.")
g.mistake("Ngủ quên nên lỡ tàu (kết quả không mong muốn).", "{寝坊|ねぼう}した。そこで、{電車|でんしゃ}に{乗|の}り{遅|おく}れた。", "{寝坊|ねぼう}した。それで、{電車|でんしゃ}に{乗|の}り{遅|おく}れた。", "Lỡ tàu là kết quả tự nhiên, không phải hành động chủ ý → それで.", "sorede", "sokode")
g.mistake("Quên ô nên bị ướt sũng (kết quả không mong muốn).", "{傘|かさ}を{忘|わす}れた。そこで、びしょ{濡|ぬ}れになった。", "{傘|かさ}を{忘|わす}れた。それで、びしょ{濡|ぬ}れになった。",
          "Bị ướt là kết quả tự nhiên, không ai chủ động chọn → それで. そこで chỉ dùng khi câu sau là hành động có chủ ý.", "sorede", "sokode")
g.mc("sokode", "Chọn từ nối phù hợp nhất: 「駅までの道が分からなくなった。___、近くの人に聞いてみることにした。」", [
    opt("a", "そこで", "sokode"),
    opt("b", "それに", None, "それに là 'hơn nữa' (thêm thông tin), không nối vấn đề với giải pháp."),
    opt("c", "それとも", None, "それとも là 'hay là' trong câu hỏi lựa chọn."),
], "a", "Gặp vấn đề → bèn chủ động hỏi người gần đó → そこで.", d=2)
g.fill("sorede", "Chọn từ nối phù hợp để điền vào chỗ trống.", "{昨夜|ゆうべ}は{隣|となり}の{部屋|へや}がうるさかった。＿＿＿、よく{眠|ねむ}れなかった。",
       "Đêm qua phòng bên ồn ào. Vì thế tôi không ngủ ngon.", [
           opt("a", "それで", "sorede"),
           opt("b", "そこで", "sokode", "Không ngủ được là kết quả tự nhiên, không phải hành động chủ ý."),
       ], "a", "Kết quả tự nhiên → それで.")
g.error("sorede", "Chọn phần sai trong đoạn hội thoại.", "— Hôm qua tớ gặp thầy cũ ở ga. — Rồi sao nữa?", [
    seg("「{昨日|きのう}、{駅|えき}で"), seg("{昔|むかし}の{先生|せんせい}に{会|あ}ったんだ。」"), seg("「そこで、", "sokode"), seg("どうなったの？」"),
], 2, "「それで、", "Giục người kia kể tiếp chỉ dùng それで.")
g.context("sokode", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn viết báo cáo: khách hàng than phiền thời gian chờ quá lâu, và công ty đã quyết định tăng thêm nhân viên quầy.", [
    opt("a", "{待|ま}ち{時間|じかん}が{長|なが}いという{苦情|くじょう}が{多|おお}かった。そこで、{窓口|まどぐち}の{係員|かかりいん}を{増|ふ}やすことにした。", "sokode"),
    opt("b", "{待|ま}ち{時間|じかん}が{長|なが}いという{苦情|くじょう}が{多|おお}かった。または、{窓口|まどぐち}の{係員|かかりいん}を{増|ふ}やすことにした。", None, "または là 'hoặc', không nối nguyên nhân – giải pháp."),
    opt("c", "{待|ま}ち{時間|じかん}が{長|なが}いという{苦情|くじょう}が{多|おお}かった。それとも、{窓口|まどぐち}の{係員|かかりいん}を{増|ふ}やすことにした。", None, "それとも dùng trong câu hỏi lựa chọn."),
], "a", "Vấn đề → giải pháp được quyết định → そこで.")

# 22 --------------------------------------------------------------------------------------------
g = group("matawa-soretomo", "または và それとも",
          "Cả hai nghĩa 'hoặc, hay là'. または dùng trong câu trần thuật, thông báo, quy định (A hoặc B đều được); それとも chỉ dùng trong câu hỏi lựa chọn (A hay là B?).")
g.vocab("matawa", "mimikara-n3-v878", "または", "hoặc", "Nêu các lựa chọn trong câu trần thuật, văn bản: 黒または青のペンで書いてください.",
        [("{電話|でんわ}またはメール", "điện thoại hoặc email"), ("{本人|ほんにん}または{家族|かぞく}", "bản thân hoặc người nhà")])
g.vocab("soretomo", "mimikara-n3-v879", "それとも", "hay là", "Đặt giữa hai câu hỏi để người nghe chọn một: コーヒーにしますか。それとも紅茶にしますか.",
        [("{行|い}く？それとも{行|い}かない？", "đi hay không đi?"), ("{和食|わしょく}？それとも{洋食|ようしょく}？", "món Nhật hay món Âu?")])
g.theory("Câu hỏi hay câu trần thuật", "Câu hỏi bắt người nghe chọn (〜か。それとも〜か) → それとも. Câu thông báo, hướng dẫn, quy định chấp nhận một trong các khả năng → または.")
g.theory("Vị trí trong câu", "または nối danh từ hoặc mệnh đề trong cùng một câu (A または B). それとも thường đứng đầu câu hỏi thứ hai, sau một câu hỏi khác (〜ますか。それとも〜ますか).")
g.compare("Loại câu", matawa="Trần thuật, thông báo, quy định", soretomo="Câu hỏi lựa chọn")
g.compare("Vị trí", matawa="Giữa hai danh từ/mệnh đề", soretomo="Đầu câu hỏi thứ hai")
g.compare("Ví dụ", matawa="鉛筆またはボールペンで書くこと", soretomo="電車で行く？それともバス？")
g.example("matawa", "{申込書|もうしこみしょ}は{郵送|ゆうそう}、またはメールで{送|おく}ってください。", "Hãy gửi đơn đăng ký qua bưu điện hoặc qua email.")
g.example("matawa", "{料金|りょうきん}は{現金|げんきん}またはカードでお{支払|しはら}いいただけます。", "Có thể thanh toán bằng tiền mặt hoặc thẻ.")
g.example("soretomo", "{夏休|なつやす}みは{海|うみ}に{行|い}く？それとも{山|やま}に{行|い}く？", "Nghỉ hè đi biển hay là đi núi?")
g.example("soretomo", "ここで{食|た}べますか。それとも{持|も}ち{帰|かえ}りですか。", "Quý khách ăn ở đây hay mang về?")
g.tip("それとも chỉ sống trong câu có dấu chấm hỏi; または sống trong biển báo, đơn từ.")
g.mistake("Nhân viên hỏi khách muốn trà hay cà phê.", "お{飲|の}み{物|もの}はコーヒーですか。または{紅茶|こうちゃ}ですか。", "お{飲|の}み{物|もの}はコーヒーですか。それとも{紅茶|こうちゃ}ですか。", "Câu hỏi lựa chọn → それとも.", "soretomo", "matawa")
g.mistake("Hướng dẫn ghi bằng bút đen hoặc xanh.", "{黒|くろ}、それとも{青|あお}のペンで{記入|きにゅう}してください。", "{黒|くろ}または{青|あお}のペンで{記入|きにゅう}してください。", "Câu hướng dẫn (không phải câu hỏi) → または.", "matawa", "soretomo")
g.mc("matawa", "Chọn câu đúng (thông báo của trường).", [
    opt("a", "{欠席|けっせき}する{場合|ばあい}は、{電話|でんわ}またはメールで{連絡|れんらく}すること。", "matawa"),
    opt("b", "{欠席|けっせき}する{場合|ばあい}は、{電話|でんわ}それともメールで{連絡|れんらく}すること。", "soretomo", "それとも chỉ dùng trong câu hỏi."),
    opt("c", "{欠席|けっせき}する{場合|ばあい}は、{電話|でんわ}それでメールで{連絡|れんらく}すること。", None, "それで là 'vì thế', sai nghĩa."),
], "a", "Thông báo, quy định → または.")
g.fill("soretomo", "Chọn từ phù hợp để điền vào chỗ trống.", "{今日|きょう}の{夕飯|ゆうはん}は{家|いえ}で{作|つく}る？＿＿＿{外|そと}で{食|た}べる？",
       "Tối nay mình nấu ở nhà hay là ăn ngoài?", [
           opt("a", "それとも", "soretomo"),
           opt("b", "または", "matawa", "または không dùng để nối hai câu hỏi lựa chọn trong hội thoại."),
       ], "a", "Câu hỏi lựa chọn → それとも.")
g.error("matawa", "Chọn phần sai trong câu.", "Người tham gia hãy mang theo hộ chiếu hoặc thẻ cư trú.", [
    seg("{参加者|さんかしゃ}は"), seg("パスポート"), seg("それとも", "soretomo"), seg("{在留|ざいりゅう}カードを{持|も}ってきてください。"),
], 2, "または", "Câu yêu cầu (không phải câu hỏi) → または.")
g.context("soretomo", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn hỏi bạn mình muốn xem phim hành động hay phim hoạt hình.", [
    opt("a", "アクション{映画|えいが}がいい？それともアニメ？", "soretomo"),
    opt("b", "アクション{映画|えいが}がいい？またはアニメ？", "matawa", "または không dùng để hỏi lựa chọn trong hội thoại."),
    opt("c", "アクション{映画|えいが}がいい？そこでアニメ？", None, "そこで là 'vì vậy', sai nghĩa."),
], "a", "Câu hỏi lựa chọn → それとも.")

# 23 --------------------------------------------------------------------------------------------
g = group("kikoku-kisei-kitaku", "帰国, 帰省 và 帰宅",
          "Ba danh động từ cùng có chữ 帰 (về), khác nhau ở nơi về: 帰国 là về nước, 帰省 là về quê (thăm gia đình), 帰宅 là về nhà (sau giờ làm, giờ học).")
g.vocab("kikoku", "mimikara-n3-v034", "{帰国|きこく}", "về nước", "Từ nước ngoài trở về nước mình: 帰国する、一時帰国.",
        [("{一時|いちじ}{帰国|きこく}する", "về nước tạm thời"), ("{帰国|きこく}の{準備|じゅんび}", "chuẩn bị về nước")])
g.vocab("kisei", "mimikara-n3-v035", "{帰省|きせい}", "về quê", "Về quê nhà (nơi bố mẹ sống) trong kỳ nghỉ: お盆に帰省する、帰省ラッシュ.",
        [("{実家|じっか}に{帰省|きせい}する", "về nhà bố mẹ ở quê"), ("{帰省|きせい}ラッシュ", "cao điểm về quê")])
g.vocab("kitaku", "mimikara-n3-v036", "{帰宅|きたく}", "về nhà", "Về nhà mình sau khi đi làm, đi học, đi chơi: 七時に帰宅する.",
        [("{帰宅|きたく}する", "về nhà"), ("{帰宅|きたく}{時間|じかん}", "giờ về nhà")])
g.theory("Về đâu?", "国 (nước) → 帰国: người sống ở nước ngoài về nước. 省 (thăm hỏi) → 帰省: về thăm quê, bố mẹ, thường dịp お盆, 正月. 宅 (nhà) → 帰宅: về nơi mình đang ở mỗi ngày.")
g.theory("Phân biệt 帰省 và 帰宅", "Người sống một mình ở Tokyo về nhà trọ sau giờ làm → 帰宅. Người đó về Osaka thăm bố mẹ dịp Tết → 帰省. Người nước ngoài về hẳn nước mình → 帰国.")
g.compare("Nơi về", kikoku="Nước mình (từ nước ngoài)", kisei="Quê (nhà bố mẹ)", kitaku="Nhà mình (hằng ngày)")
g.compare("Thời điểm hay gặp", kikoku="Hết hạn du học, công tác", kisei="お盆、正月", kitaku="Sau giờ làm, giờ học")
g.compare("Cụm hay gặp", kikoku="一時帰国、帰国子女", kisei="帰省ラッシュ、帰省客", kitaku="帰宅時間、帰宅途中")
g.example("kikoku", "{留学|りゅうがく}が{終|お}わったら、{帰国|きこく}して{日本語|にほんご}の{先生|せんせい}になりたい。", "Du học xong tôi muốn về nước làm giáo viên tiếng Nhật.")
g.example("kikoku", "{夏休|なつやす}みに{一時|いちじ}{帰国|きこく}する{予定|よてい}です。", "Kỳ nghỉ hè tôi dự định về nước tạm thời.")
g.example("kisei", "お{正月|しょうがつ}は{家族|かぞく}で{田舎|いなか}に{帰省|きせい}する。", "Tết tôi cùng gia đình về quê.")
g.example("kisei", "お{盆|ぼん}の{時期|じき}は{帰省|きせい}する{人|ひと}で{新幹線|しんかんせん}が{混|こ}む。", "Dịp Obon tàu Shinkansen đông vì người về quê.")
g.example("kitaku", "{父|ちち}は{毎日|まいにち}{七時|しちじ}ごろ{帰宅|きたく}する。", "Bố tôi về nhà khoảng bảy giờ mỗi ngày.")
g.example("kitaku", "{帰宅|きたく}{途中|とちゅう}に、スーパーで{買|か}い{物|もの}をした。", "Trên đường về nhà, tôi ghé siêu thị mua sắm.")
g.tip("帰 + 国 = về nước; 帰 + 省 (thăm viếng cha mẹ) = về quê; 帰 + 宅 (nhà) = về nhà.")
g.mistake("Nói hôm nay tan làm về nhà sớm.", "{今日|きょう}は{仕事|しごと}が{早|はや}く{終|お}わったので、{六時|ろくじ}に{帰省|きせい}した。", "{今日|きょう}は{仕事|しごと}が{早|はや}く{終|お}わったので、{六時|ろくじ}に{帰宅|きたく}した。", "Về nhà sau giờ làm → 帰宅.", "kitaku", "kisei")
g.mistake("Du học sinh nói sẽ về Việt Nam.", "{来月|らいげつ}、ベトナムに{帰宅|きたく}します。", "{来月|らいげつ}、ベトナムに{帰国|きこく}します。", "Về nước mình từ nước ngoài → 帰国.", "kikoku", "kitaku")
g.mc("kisei", "Anh Tanaka sống ở Tokyo, quê ở Hokkaido. Dịp Obon anh về thăm bố mẹ. Câu nào đúng?", [
    opt("a", "{田中|たなか}さんはお{盆|ぼん}に{北海道|ほっかいどう}へ{帰省|きせい}した。", "kisei"),
    opt("b", "{田中|たなか}さんはお{盆|ぼん}に{北海道|ほっかいどう}へ{帰国|きこく}した。", "kikoku", "Hokkaido vẫn ở Nhật, không phải về nước."),
    opt("c", "{田中|たなか}さんはお{盆|ぼん}に{北海道|ほっかいどう}へ{帰宅|きたく}した。", "kitaku", "帰宅 là về nhà hằng ngày, không phải về quê thăm bố mẹ."),
], "a", "Về quê thăm bố mẹ dịp Obon → 帰省.")
g.fill("kitaku", "Chọn từ phù hợp để điền vào chỗ trống.", "{台風|たいふう}が{近|ちか}づいているので、{社員|しゃいん}は{早|はや}めに＿＿＿してください。",
       "Bão đang đến gần nên nhân viên hãy về nhà sớm.", [
           opt("a", "{帰宅|きたく}", "kitaku"),
           opt("b", "{帰省|きせい}", "kisei"),
           opt("c", "{帰国|きこく}", "kikoku"),
       ], "a", "Nhân viên về nhà sau giờ làm → 帰宅.")
g.error("kikoku", "Chọn phần sai trong câu.", "Anh Lee đã về Hàn Quốc sau 5 năm làm việc ở Nhật.", [
    seg("{李|り}さんは"), seg("{日本|にほん}で{五年|ごねん}{働|はたら}いた{後|あと}、"), seg("{韓国|かんこく}に"), seg("{帰省|きせい}した。", "kisei"),
], 3, "{帰国|きこく}した。", "Từ Nhật về nước mình (Hàn Quốc) → 帰国.")
g.context("kisei", "Chọn câu phù hợp nhất trong tình huống sau.", "Bản tin nói vào cuối năm, đường cao tốc rời Tokyo đông nghẹt vì rất nhiều người về quê.", [
    opt("a", "{年末|ねんまつ}は{帰省|きせい}ラッシュで、{高速道路|こうそくどうろ}が{渋滞|じゅうたい}しています。", "kisei"),
    opt("b", "{年末|ねんまつ}は{帰宅|きたく}ラッシュで、{高速道路|こうそくどうろ}が{渋滞|じゅうたい}しています。", "kitaku", "帰宅ラッシュ là giờ tan tầm hằng ngày, không phải mùa về quê."),
    opt("c", "{年末|ねんまつ}は{帰国|きこく}ラッシュで、{高速道路|こうそくどうろ}が{渋滞|じゅうたい}しています。", "kikoku", "Người Nhật về quê không phải về nước."),
], "a", "Mùa về quê cuối năm → 帰省ラッシュ.")

# 24 --------------------------------------------------------------------------------------------
g = group("hakken-hatsumei", "発見 và 発明",
          "発見 là phát hiện ra thứ đã tồn tại nhưng chưa ai biết (định luật, loài mới, di tích); 発明 là phát minh ra thứ trước đó chưa có (máy móc, công cụ, kỹ thuật).")
g.vocab("hakken", "mimikara-n3-v374", "{発見|はっけん}", "phát hiện", "Tìm ra điều đã có sẵn trong tự nhiên hay thế giới: 新しい星を発見する、病気を早期発見する.",
        [("{新種|しんしゅ}を{発見|はっけん}する", "phát hiện loài mới"), ("{早期|そうき}{発見|はっけん}", "phát hiện sớm")])
g.vocab("hatsumei", "mimikara-n3-v375", "{発明|はつめい}", "phát minh", "Tạo ra thứ mới chưa từng có: 電話を発明する、発明家.",
        [("{電球|でんきゅう}を{発明|はつめい}する", "phát minh bóng đèn"), ("{発明家|はつめいか}", "nhà phát minh")])
g.theory("Đã có sẵn hay tạo mới", "Thứ vốn tồn tại, con người chỉ tìm thấy (星、新種、遺跡、ウイルス、法則、間違い) → 発見. Thứ do con người nghĩ ra, chế tạo (電話、飛行機、印刷機、カップ麺) → 発明.")
g.theory("Mẹo câu hỏi", "Hỏi: 'trước khi người này làm, thứ đó đã có chưa?' Đã có (chỉ chưa ai biết) → 発見. Chưa có → 発明.")
g.compare("Đối tượng", hakken="Thứ đã tồn tại", hatsumei="Thứ mới tạo ra")
g.compare("Ví dụ", hakken="新しい星、遺跡、ウイルス", hatsumei="電話、飛行機、電球")
g.compare("Người làm", hakken="発見者 (người phát hiện)", hatsumei="発明家 (nhà phát minh)")
g.example("hakken", "{工事|こうじ}{中|ちゅう}に、{古|ふる}い{時代|じだい}の{遺跡|いせき}が{発見|はっけん}された。", "Trong lúc thi công đã phát hiện ra di tích thời cổ.")
g.example("hakken", "{病気|びょうき}は{早|はや}く{発見|はっけん}すれば、{治|なお}る{可能性|かのうせい}が{高|たか}い。", "Bệnh nếu phát hiện sớm thì khả năng chữa khỏi cao.")
g.example("hatsumei", "エジソンは{多|おお}くの{物|もの}を{発明|はつめい}した。", "Edison đã phát minh ra nhiều thứ.")
g.example("hatsumei", "インスタントラーメンは{日本|にほん}で{発明|はつめい}された。", "Mì ăn liền được phát minh ở Nhật.")
g.tip("見 (nhìn) → 発見: nhìn thấy cái đã có. 明 (sáng, nghĩ ra) → 発明: nghĩ ra cái mới.")
g.mistake("Nói nhà khoa học tìm ra loài cá mới.", "{科学者|かがくしゃ}が{深海|しんかい}で{新|あたら}しい{魚|さかな}を{発明|はつめい}した。", "{科学者|かがくしゃ}が{深海|しんかい}で{新|あたら}しい{魚|さかな}を{発見|はっけん}した。", "Loài cá đã tồn tại, chỉ là được tìm thấy → 発見.", "hakken", "hatsumei")
g.mistake("Nói về người làm ra máy bay đầu tiên.", "ライト{兄弟|きょうだい}は{飛行機|ひこうき}を{発見|はっけん}した。", "ライト{兄弟|きょうだい}は{飛行機|ひこうき}を{発明|はつめい}した。", "Máy bay trước đó chưa có → 発明.", "hatsumei", "hakken")
g.mc("hatsumei", "Chọn câu đúng.", [
    opt("a", "ベルは{電話|でんわ}を{発明|はつめい}したことで{有名|ゆうめい}だ。", "hatsumei"),
    opt("b", "ベルは{電話|でんわ}を{発見|はっけん}したことで{有名|ゆうめい}だ。", "hakken", "Điện thoại là thứ được tạo ra mới → 発明."),
    opt("c", "ベルは{電話|でんわ}を{発表|はっぴょう}したことで{有名|ゆうめい}だ。", None, "発表 là công bố, không nói lên việc tạo ra."),
], "a", "Tạo ra điện thoại → 発明.")
g.fill("hakken", "Chọn từ phù hợp để điền vào chỗ trống.", "{作文|さくぶん}を{読|よ}み{直|なお}したら、{漢字|かんじ}の{間違|まちが}いを{三|みっ}つ＿＿＿した。",
       "Đọc lại bài văn, tôi phát hiện ba lỗi Kanji.", [
           opt("a", "{発見|はっけん}", "hakken"),
           opt("b", "{発明|はつめい}", "hatsumei"),
       ], "a", "Lỗi sai đã có sẵn, chỉ được tìm thấy → 発見.")
g.error("hakken", "Chọn phần sai trong câu.", "Năm 1930 người ta phát hiện ra sao Diêm Vương.", [
    seg("{一九三〇年|せんきゅうひゃくさんじゅうねん}に"), seg("{冥王星|めいおうせい}が"), seg("{発明|はつめい}された。", "hatsumei"),
], 2, "{発見|はっけん}された。", "Ngôi sao đã tồn tại, chỉ được tìm thấy → 発見.")
g.context("hatsumei", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn kể rằng một học sinh tiểu học đã tự chế ra một dụng cụ mới giúp gấp quần áo nhanh.", [
    opt("a", "{小学生|しょうがくせい}が{服|ふく}を{早|はや}く{畳|たた}める{道具|どうぐ}を{発明|はつめい}したそうだ。", "hatsumei"),
    opt("b", "{小学生|しょうがくせい}が{服|ふく}を{早|はや}く{畳|たた}める{道具|どうぐ}を{発見|はっけん}したそうだ。", "hakken", "Dụng cụ do bạn ấy tự chế tạo → 発明."),
    opt("c", "{小学生|しょうがくせい}が{服|ふく}を{早|はや}く{畳|たた}める{道具|どうぐ}を{見|み}つかったそうだ。", None, "見つかる không đi với を."),
], "a", "Chế tạo dụng cụ mới → 発明.")
