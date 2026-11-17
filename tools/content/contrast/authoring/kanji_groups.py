"""Kanji contrast groups (n3-kanji). AI-written drafts: every group needs a real reviewer.

Options that are bare kanji or kanji words carry no furigana on purpose (the reading would give the
answer away); the validator reports them as FURIGANA_MISSING warnings for the reviewer to acknowledge.
A wrong kanji in an error-detection or context sentence carries its own real reading, never the
reading of the intended word (so the furigana is always true to the characters written).
"""
from dsl import Group, opt, seg

GROUPS = []


def group(*args):
    g = Group("KANJI", *args)
    GROUPS.append(g)
    return g


# 1 ---------------------------------------------------------------------------------------------
g = group("seki-tsumu-seiseki-sekinin", "積, 績 và 責",
          "Ba chữ cùng đọc セキ và cùng có phần 責 bên phải. 積 (bộ 禾 lúa) là chất đống, tích lũy; 績 (bộ 糸 sợi) là thành tích; 責 đứng một mình là trách nhiệm, trách móc.")
g.kanji("tsumu", "n3k-k07a4d", "積", "chất đống, tích lũy; diện tích", ("禾", "Hòa (cây lúa)"), "Bên trái là 禾: lúa gặt về chất thành đống → 積む, 面積, 積極的.")
g.kanji("seki", "n3k-k07e3e", "績", "thành tích, kết quả công việc", ("糸", "Mịch (sợi chỉ)"), "Bên trái là 糸: kéo sợi từng chút thành tấm vải, như kết quả làm việc tích góp → 成績, 業績, 実績.")
g.kanji("seme", "n3k-k08cac", "責", "trách nhiệm; trách móc", ("貝", "Bối (vỏ sò, tiền)"), "Không có bộ bên trái; phần dưới là 貝 (tiền): món nợ phải trả → 責任, 責める.")
g.theory("Nhìn bộ bên trái", "Cùng phần 責 cho âm セキ, nghĩa do bộ bên trái quyết định: 禾 (lúa) → chất đống, tích lũy; 糸 (sợi) → thành quả dệt nên, thành tích; không có bộ → trách nhiệm.")
g.theory("Từ hay gặp trong đề", "積: 面積 (diện tích), 積極的 (tích cực), 見積もり (báo giá), 積もる. 績: 成績 (thành tích học tập), 業績 (thành tích kinh doanh), 実績. 責: 責任 (trách nhiệm), 無責任, 責める.")
g.compare("Bộ thủ", tsumu="禾 (lúa)", seki="糸 (sợi)", seme="貝 ở dưới, không bộ trái")
g.compare("Nghĩa", tsumu="Chất, tích lũy", seki="Thành tích", seme="Trách nhiệm, trách móc")
g.compare("Từ tiêu biểu", tsumu="面積、積極的、積もる", seki="成績、業績、実績", seme="責任、責める")
g.example("tsumu", "{雪|ゆき}が{三十|さんじゅう}センチも{積|つ}もった。", "Tuyết tích tụ dày tới ba mươi phân.")
g.example("tsumu", "{彼|かれ}は{何|なん}でも{積極的|せっきょくてき}に{挑戦|ちょうせん}する。", "Anh ấy việc gì cũng tích cực thử sức.")
g.example("seki", "{今学期|こんがっき}は{数学|すうがく}の{成績|せいせき}が{上|あ}がった。", "Học kỳ này điểm toán của tôi tăng lên.")
g.example("seki", "{今年|ことし}は{会社|かいしゃ}の{業績|ぎょうせき}がとてもいい。", "Năm nay kết quả kinh doanh của công ty rất tốt.")
g.example("seme", "{失敗|しっぱい}の{責任|せきにん}は{私|わたし}にあります。", "Trách nhiệm cho thất bại thuộc về tôi.")
g.example("seme", "{子供|こども}のミスをあまり{責|せ}めないでください。", "Xin đừng trách lỗi của con trẻ quá.")
g.tip("Lúa (禾) chất đống → 積. Sợi (糸) dệt thành quả → 績. Tiền (貝) phải trả → 責.")
g.mistake("Viết từ 'thành tích' (せいせき).", "{試験|しけん}の{成積|せいせき}が{悪|わる}かった。", "{試験|しけん}の{成績|せいせき}が{悪|わる}かった。", "成績 viết với 績 (bộ 糸). 成積 không phải là từ.", "seki", "tsumu")
g.mistake("Viết từ 'diện tích' (めんせき).", "この{部屋|へや}の{面績|めんせき}は{二十|にじゅう}{平方|へいほう}メートルだ。", "この{部屋|へや}の{面積|めんせき}は{二十|にじゅう}{平方|へいほう}メートルだ。", "Diện tích là 面積 (chất đống, phép nhân) với 積.", "tsumu", "seki")
g.mc("seki", "Chọn cách viết đúng của せいせき (thành tích học tập).", [
    opt("a", "成積", "tsumu", "積 là chất đống, tích lũy."),
    opt("b", "成績", "seki"),
    opt("c", "成責", "seme", "責 là trách nhiệm."),
], "b", "成績 viết với 績 (bộ 糸): kết quả làm ra.")
g.fill("seme", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "このプロジェクトのリーダーは{私|わたし}ですから、{失敗|しっぱい}したら{私|わたし}の＿＿＿{任|にん}です。",
       "Tôi là người phụ trách dự án này nên nếu thất bại thì là trách nhiệm của tôi.", [
           opt("a", "責", "seme"),
           opt("b", "積", "tsumu", "積任 không phải là từ."),
           opt("c", "績", "seki", "績任 không phải là từ."),
       ], "a", "責任 (trách nhiệm) viết với 責.")
g.error("tsumu", "Chọn phần viết sai chữ Kanji.", "Anh ấy là người tích cực, việc gì cũng hăng hái làm.", [
    seg("{彼|かれ}は"), seg("{何|なに}ごとにも"), seg("{績極的|せっきょくてき}な", "seki"), seg("{人|ひと}だ。"),
], 2, "{積極的|せっきょくてき}な", "積極的 (tích cực) viết với 積.")
g.context("seki", "Chọn câu viết đúng trong tình huống sau.", "Bạn báo cáo với cấp trên rằng doanh thu (kết quả kinh doanh) quý này tăng mạnh.", [
    opt("a", "{今期|こんき}の{業績|ぎょうせき}は{大幅|おおはば}に{伸|の}びました。", "seki"),
    opt("b", "{今期|こんき}の{業積|ぎょうせき}は{大幅|おおはば}に{伸|の}びました。", "tsumu", "業績 viết với 績, không phải 積."),
    opt("c", "{今期|こんき}の{業責|ぎょうせき}は{大幅|おおはば}に{伸|の}びました。", "seme", "業績 viết với 績, không phải 責."),
], "a", "業績 (thành tích kinh doanh) viết với 績.")

# 2 ---------------------------------------------------------------------------------------------
g = group("mi-matsu", "未 và 末",
          "Hai chữ chỉ khác độ dài của hai nét ngang: 未 (VỊ, chưa) có nét ngang trên ngắn hơn nét dưới; 末 (MẠT, cuối) có nét ngang trên dài hơn nét dưới.")
g.kanji("mi", "n3k-k0672a", "未", "chưa", ("木", "Mộc (cây)"), "Nét ngang trên NGẮN: cành non chưa lớn → 'chưa'. 未来, 未満, 未定.")
g.kanji("matsu", "n3k-k0672b", "末", "cuối, ngọn", ("木", "Mộc (cây)"), "Nét ngang trên DÀI: đánh dấu ngọn cây, phần cuối → 'cuối'. 週末, 年末, 月末.")
g.theory("Phân biệt bằng nét ngang", "Cả hai dựa trên 木 (cây) thêm một nét ngang. Nét trên ngắn hơn (未): cây chưa lớn, 'chưa'. Nét trên dài hơn (末): đánh dấu ở ngọn, 'cuối'. Khi viết tay phải giữ đúng tỉ lệ.")
g.theory("Từ hay gặp", "未: 未来 (tương lai: thời gian chưa đến), 未満 (chưa đủ), 未定 (chưa định), 未成年. 末: 週末 (cuối tuần), 年末 (cuối năm), 月末, 期末, 末っ子 (con út).")
g.compare("Nét ngang trên", mi="Ngắn hơn nét dưới", matsu="Dài hơn nét dưới")
g.compare("Nghĩa", mi="Chưa", matsu="Cuối")
g.compare("Từ tiêu biểu", mi="未来、未満、未定", matsu="週末、年末、月末")
g.example("mi", "{十八歳|じゅうはっさい}{未満|みまん}の{人|ひと}はお{酒|さけ}を{買|か}えません。", "Người chưa đủ 18 tuổi không mua được rượu.")
g.example("mi", "{旅行|りょこう}の{日程|にってい}はまだ{未定|みてい}です。", "Lịch trình du lịch vẫn chưa định.")
g.example("matsu", "{週末|しゅうまつ}は{家族|かぞく}と{公園|こうえん}に{行|い}きます。", "Cuối tuần tôi đi công viên với gia đình.")
g.example("matsu", "{年末|ねんまつ}は{仕事|しごと}が{忙|いそが}しい。", "Cuối năm công việc bận rộn.")
g.tip("末: ngọn (trên) dài vì đã đến 'cuối'. 未: ngọn ngắn vì 'chưa' mọc tới.")
g.mistake("Viết 'cuối tuần'.", "{週未|しゅうみ}に{映画|えいが}を{見|み}た。", "{週末|しゅうまつ}に{映画|えいが}を{見|み}た。", "Cuối → 末 (nét ngang trên dài).", "matsu", "mi")
g.mistake("Viết 'tương lai'.", "{末来|まつらい}のことを{考|かんが}える。", "{未来|みらい}のことを{考|かんが}える。", "Tương lai = thời gian chưa đến → 未.", "mi", "matsu")
g.mc("mi", "Chọn cách viết đúng của みらい (tương lai).", [
    opt("a", "未来", "mi"),
    opt("b", "末来", "matsu", "末 là cuối."),
    opt("c", "味来", None, "味 là vị (mùi vị)."),
], "a", "未来: thời gian chưa đến → 未.")
g.fill("matsu", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{家賃|やちん}は{毎月|まいつき}、{月|げつ}＿＿＿までに{払|はら}ってください。",
       "Hãy trả tiền nhà trước cuối mỗi tháng.", [
           opt("a", "末", "matsu"),
           opt("b", "未", "mi"),
       ], "a", "月末 (cuối tháng) viết với 末.")
g.error("mi", "Chọn phần viết sai chữ Kanji.", "Ngày cưới vẫn chưa được quyết định.", [
    seg("{結婚式|けっこんしき}の"), seg("{日|ひ}は"), seg("まだ"), seg("{末定|まつてい}です。", "matsu"),
], 3, "{未定|みてい}です。", "未定 (chưa định) viết với 未.")
g.context("matsu", "Chọn câu viết đúng trong tình huống sau.", "Bạn nhắn cho bạn: cuối năm mình sẽ về quê.", [
    opt("a", "{年末|ねんまつ}は{実家|じっか}に{帰|かえ}るよ。", "matsu"),
    opt("b", "{年未|ねんみ}は{実家|じっか}に{帰|かえ}るよ。", "mi", "Cuối năm viết 年末."),
    opt("c", "{年味|ねんみ}は{実家|じっか}に{帰|かえ}るよ。", None, "味 là vị, không liên quan."),
], "a", "年末 (cuối năm) viết với 末.")

# 3 ---------------------------------------------------------------------------------------------
g = group("shoku-chi-chi", "植, 値 và 置",
          "Ba chữ cùng có phần 直. Bộ thủ quyết định nghĩa: 植 (bộ 木 cây) là trồng; 値 (bộ 亻 người) là giá trị, giá cả; 置 (bộ 罒 lưới ở trên) là đặt, để.")
g.kanji("ueru", "n3k-k0690d", "植", "trồng (cây)", ("木", "Mộc (cây)"), "木 + 直: cắm cây thẳng xuống đất → trồng. 植える, 植物.")
g.kanji("ne", "n3k-k05024", "値", "giá, giá trị", ("亻", "Nhân đứng (người)"), "亻 + 直: con người định giá cho đúng (直) → giá trị. 値段, 価値, 数値.")
g.kanji("oku", "n3k-k07f6e", "置", "đặt, để", ("罒", "Võng (lưới), nằm trên"), "罒 ở trên + 直: đặt lưới xuống cho thẳng → đặt, để. 置く, 位置, 設置.")
g.theory("Bộ thủ là chìa khóa", "Phần 直 chung, nhìn phần còn lại: 木 bên trái → liên quan cây cối (植); 亻 bên trái → liên quan con người, giá trị (値); 罒 ở trên → đặt, để (置).")
g.theory("Âm đọc", "植: ショク, う.える (植物, 植える). 値: チ, ね, あたい (価値, 値段). 置: チ, お.く (位置, 置く). 値 và 置 cùng âm チ nên dễ viết nhầm trong 価値 ／ 位置.")
g.compare("Bộ thủ", ueru="木 (cây) bên trái", ne="亻 (người) bên trái", oku="罒 (lưới) ở trên")
g.compare("Nghĩa", ueru="Trồng", ne="Giá, giá trị", oku="Đặt, để")
g.compare("Từ tiêu biểu", ueru="植物、植える", ne="値段、価値、数値", oku="位置、置く、設置")
g.example("ueru", "{庭|にわ}に{桜|さくら}の{木|き}を{植|う}えた。", "Tôi đã trồng cây anh đào trong vườn.")
g.example("ueru", "この{植物|しょくぶつ}は{水|みず}をあまり{必要|ひつよう}としない。", "Loài cây này không cần nhiều nước.")
g.example("ne", "この{店|みせ}は{値段|ねだん}が{安|やす}くて{助|たす}かる。", "Quán này giá rẻ nên đỡ quá.")
g.example("ne", "この{本|ほん}は{読|よ}む{価値|かち}がある。", "Cuốn sách này đáng để đọc.")
g.example("oku", "{鍵|かぎ}は{机|つくえ}の{上|うえ}に{置|お}いてください。", "Hãy để chìa khóa trên bàn.")
g.example("oku", "{地図|ちず}で{駅|えき}の{位置|いち}を{確|たし}かめた。", "Tôi xem vị trí nhà ga trên bản đồ.")
g.tip("Cây (木) thì trồng – 植; người (亻) thì định giá – 値; lưới (罒) thì đặt xuống – 置.")
g.mistake("Viết 'giá cả' (ねだん).", "このかばんの{植段|しょくだん}は{高|たか}い。", "このかばんの{値段|ねだん}は{高|たか}い。", "Giá cả → 値 (bộ 亻).", "ne", "ueru")
g.mistake("Viết 'vị trí' (いち).", "{机|つくえ}の{位値|いち}を{変|か}えた。", "{机|つくえ}の{位置|いち}を{変|か}えた。", "Vị trí là chỗ đặt → 置.", "oku", "ne")
g.mc("oku", "Chọn cách viết đúng của いち (vị trí).", [
    opt("a", "位値", "ne", "値 là giá trị."),
    opt("b", "位置", "oku"),
    opt("c", "位植", "ueru", "植 là trồng cây."),
], "b", "位置: chỗ đặt → 置.")
g.fill("ueru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{春|はる}になったら、{花|はな}の{種|たね}を＿＿＿えよう。",
       "Sang xuân thì mình gieo hạt hoa nhé.", [
           opt("a", "植", "ueru"),
           opt("b", "値", "ne"),
           opt("c", "置", "oku"),
       ], "a", "植える (trồng) viết với 植 (bộ 木).")
g.error("ne", "Chọn phần viết sai chữ Kanji.", "Ý kiến này đáng để suy nghĩ.", [
    seg("この{意見|いけん}は"), seg("{考|かんが}える"), seg("{価置|かち}が", "oku"), seg("ある。"),
], 2, "{価値|かち}が", "価値 (giá trị) viết với 値.")
g.context("oku", "Chọn câu viết đúng trong tình huống sau.", "Bạn dặn em: đừng để cặp sách ở hành lang.", [
    opt("a", "{廊下|ろうか}にかばんを{置|お}かないで。", "oku"),
    opt("b", "{廊下|ろうか}にかばんを{値|ね}かないで。", "ne", "'Đặt, để' viết 置く; 値 (giá trị) không có nghĩa này."),
    opt("c", "{廊下|ろうか}にかばんを{植|う}かないで。", "ueru", "植 là trồng; 'đặt, để' viết 置く."),
], "a", "置く (đặt, để) viết với 置.")

# 4 ---------------------------------------------------------------------------------------------
g = group("oku-oku-i", "億, 憶 và 意",
          "Ba chữ cùng có phần 意. 意 là ý nghĩ; thêm 亻 (người) thành 億 – trăm triệu; thêm 忄 (tim) thành 憶 – ghi nhớ. 億 và 憶 cùng đọc オク.")
g.kanji("hyaku", "n3k-k05104", "億", "trăm triệu", ("亻", "Nhân đứng (người)"), "亻 + 意: số người nhiều đến mức chỉ có thể 'nghĩ' ra → trăm triệu. 一億, 億万長者.")
g.kanji("kioku", "n3k-k061b6", "憶", "ghi nhớ, ký ức", ("忄", "Tâm đứng (tim)"), "忄 + 意: ý nghĩ được giữ trong tim → nhớ. 記憶, 記憶力.")
g.kanji("i", "n3k-k0610f", "意", "ý, ý nghĩ, tâm ý", ("心", "Tâm (tim) ở dưới"), "音 (âm thanh) trên 心 (tim): tiếng nói của trái tim → ý nghĩ. 意味, 注意, 意見.")
g.theory("Một gốc, ba nghĩa", "意 (ý nghĩ) là gốc. Có 亻 bên trái → 億 (số lượng người khổng lồ: trăm triệu). Có 忄 bên trái → 憶 (giữ ý nghĩ trong lòng: nhớ). Chỉ một mình → 意.")
g.theory("Âm đọc dễ nhầm", "億 và 憶 cùng âm オク, nên phải dựa vào nghĩa: tiền, dân số → 億 (一億円, 人口一億人). Trí nhớ → 憶 (記憶). 意 đọc イ (意味, 注意).")
g.compare("Bộ thủ", hyaku="亻 (người)", kioku="忄 (tim)", i="心 ở dưới, không bộ trái")
g.compare("Nghĩa", hyaku="Trăm triệu", kioku="Ghi nhớ", i="Ý, ý nghĩ")
g.compare("Từ tiêu biểu", hyaku="一億、億万長者", kioku="記憶、記憶力", i="意味、注意、意見")
g.example("hyaku", "{日本|にほん}の{人口|じんこう}は{約|やく}{一億|いちおく}{二千万人|にせんまんにん}だ。", "Dân số Nhật Bản khoảng 120 triệu người.")
g.example("hyaku", "{宝|たから}くじで{一億円|いちおくえん}{当|あ}たったら{何|なに}をしたい？", "Nếu trúng xổ số một trăm triệu yên, cậu muốn làm gì?")
g.example("kioku", "{子供|こども}のころの{記憶|きおく}はあまりない。", "Tôi không còn nhiều ký ức thời thơ ấu.")
g.example("kioku", "{祖母|そぼ}は{年|とし}を{取|と}っても{記憶力|きおくりょく}がいい。", "Bà tôi có tuổi rồi mà trí nhớ vẫn tốt.")
g.example("i", "この{言葉|ことば}の{意味|いみ}が{分|わ}かりますか。", "Bạn có hiểu nghĩa của từ này không?")
g.example("i", "{車|くるま}に{注意|ちゅうい}して{道|みち}を{渡|わた}ってください。", "Hãy chú ý xe cộ khi qua đường.")
g.tip("Người (亻) đông như ý nghĩ → 億. Ý nghĩ cất trong tim (忄) → 憶.")
g.mistake("Viết 'ký ức' (きおく).", "その{日|ひ}の{記億|きおく}がない。", "その{日|ひ}の{記憶|きおく}がない。", "Trí nhớ, ký ức → 憶 (bộ 忄).", "kioku", "hyaku")
g.mistake("Viết 'một trăm triệu yên'.", "{家|いえ}の{値段|ねだん}は{一憶円|いちおくえん}だった。", "{家|いえ}の{値段|ねだん}は{一億円|いちおくえん}だった。", "Số trăm triệu → 億 (bộ 亻).", "hyaku", "kioku")
g.mc("kioku", "Chọn cách viết đúng của きおく (ký ức, trí nhớ).", [
    opt("a", "記億", "hyaku", "億 là trăm triệu."),
    opt("b", "記意", "i", "意 là ý nghĩ; từ này không tồn tại."),
    opt("c", "記憶", "kioku"),
], "c", "記憶: ghi nhớ trong lòng → 憶 (bộ 忄).")
g.fill("hyaku", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "この{映画|えいが}は{世界|せかい}で{十|じゅう}＿＿＿{円|えん}{以上|いじょう}の{売|う}り{上|あ}げがあった。",
       "Bộ phim này có doanh thu hơn một tỷ yên trên toàn thế giới.", [
           opt("a", "億", "hyaku"),
           opt("b", "憶", "kioku"),
           opt("c", "意", "i"),
       ], "a", "Đơn vị số trăm triệu → 億 (十億 = một tỷ).")
g.error("i", "Chọn phần viết sai chữ Kanji.", "Tôi hiểu rõ ý nghĩa của lời nói đó.", [
    seg("その{言葉|ことば}の"), seg("{憶味|おくみ}が", "kioku"), seg("よく"), seg("{分|わ}かった。"),
], 1, "{意味|いみ}が", "意味 (ý nghĩa) viết với 意.")
g.context("kioku", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng sau tai nạn, anh ấy mất một phần trí nhớ.", [
    opt("a", "{事故|じこ}の{後|あと}、{彼|かれ}は{記憶|きおく}の{一部|いちぶ}を{失|うしな}った。", "kioku"),
    opt("b", "{事故|じこ}の{後|あと}、{彼|かれ}は{記億|きおく}の{一部|いちぶ}を{失|うしな}った。", "hyaku", "記憶 viết với 憶 (bộ 忄), không phải 億."),
    opt("c", "{事故|じこ}の{後|あと}、{彼|かれ}は{記意|きおく}の{一部|いちぶ}を{失|うしな}った。", "i", "記憶 viết với 憶, không phải 意."),
], "a", "記憶 (trí nhớ) viết với 憶.")

# 5 ---------------------------------------------------------------------------------------------
g = group("hi-kare-nami", "皮, 彼 và 波",
          "Ba chữ cùng có phần 皮 (BÌ, da). Thêm 彳 (bước chân) thành 彼 – anh ấy, người kia; thêm 氵 (nước) thành 波 – sóng (mặt nước gợn như da).")
g.kanji("kawa", "n3k-k076ae", "皮", "da, vỏ", ("皮", "Bì (da) – chính là bộ thủ"), "Hình bàn tay lột lớp da thú → da, vỏ. 皮, 皮肉.")
g.kanji("kare", "n3k-k05f7c", "彼", "anh ấy; cái kia", ("彳", "Xích (bước chân)"), "彳 (đi) + 皮: người ở phía bên kia → anh ấy. 彼, 彼女, 彼氏.")
g.kanji("nami", "n3k-k06ce2", "波", "sóng", ("氵", "Thủy (nước)"), "氵 + 皮: lớp 'da' trên mặt nước gợn lên → sóng. 波, 津波, 電波.")
g.theory("Bộ bên trái", "Không có bộ: 皮 (da, vỏ). 彳 (người đi) → 彼 (người kia: anh ấy). 氵 (nước) → 波 (sóng). Âm On: 皮 ヒ, 彼 ヒ, 波 ハ.")
g.theory("Từ hay gặp", "皮: りんごの皮 (vỏ táo), 皮肉 (mỉa mai). 彼: 彼 (anh ấy), 彼女 (cô ấy, bạn gái), 彼氏 (bạn trai). 波: 波 (sóng), 津波 (sóng thần), 電波 (sóng điện).")
g.compare("Bộ bên trái", kawa="Không có", kare="彳 (bước chân)", nami="氵 (nước)")
g.compare("Nghĩa", kawa="Da, vỏ", kare="Anh ấy, người kia", nami="Sóng")
g.compare("Từ tiêu biểu", kawa="皮、皮肉", kare="彼、彼女、彼氏", nami="波、津波、電波")
g.example("kawa", "りんごの{皮|かわ}をむいて{食|た}べる。", "Gọt vỏ táo rồi ăn.")
g.example("kawa", "{彼|かれ}の{言|い}い{方|かた}には{皮肉|ひにく}がこもっていた。", "Cách nói của anh ta đầy mỉa mai.")
g.example("kare", "{彼女|かのじょ}は{来月|らいげつ}{結婚|けっこん}する。", "Cô ấy sẽ kết hôn vào tháng sau.")
g.example("kare", "{彼|かれ}は{私|わたし}の{同僚|どうりょう}です。", "Anh ấy là đồng nghiệp của tôi.")
g.example("nami", "{今日|きょう}は{風|かぜ}が{強|つよ}くて、{波|なみ}が{高|たか}い。", "Hôm nay gió mạnh, sóng cao.")
g.example("nami", "{山|やま}の{中|なか}は{電波|でんぱ}が{弱|よわ}い。", "Trong núi sóng điện thoại yếu.")
g.tip("Da (皮) gặp nước (氵) thành sóng (波); gặp người đi (彳) thành người kia (彼).")
g.mistake("Viết 'sóng thần'.", "{地震|じしん}の{後|あと}、{津皮|つひ}が{来|き}た。", "{地震|じしん}の{後|あと}、{津波|つなみ}が{来|き}た。", "Sóng → 波 (bộ 氵).", "nami", "kawa")
g.mistake("Viết 'cô ấy'.", "{波女|はじょ}は{先生|せんせい}です。", "{彼女|かのじょ}は{先生|せんせい}です。", "Cô ấy → 彼女 (bộ 彳).", "kare", "nami")
g.mc("nami", "Chọn cách viết đúng của でんぱ (sóng điện).", [
    opt("a", "電皮", "kawa", "皮 là da, vỏ."),
    opt("b", "電波", "nami"),
    opt("c", "電彼", "kare", "彼 là anh ấy."),
], "b", "電波 viết với 波 (sóng).")
g.fill("kawa", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "じゃがいもの＿＿＿をむいてから、{小|ちい}さく{切|き}ってください。",
       "Gọt vỏ khoai tây rồi cắt nhỏ.", [
           opt("a", "皮", "kawa"),
           opt("b", "波", "nami"),
           opt("c", "彼", "kare"),
       ], "a", "Vỏ (khoai) → 皮.")
g.error("kare", "Chọn phần viết sai chữ Kanji.", "Hôm qua tôi đi xem phim với bạn trai.", [
    seg("{昨日|きのう}、"), seg("{皮氏|ひし}と", "kawa"), seg("{映画|えいが}を"), seg("{見|み}に{行|い}った。"),
], 1, "{彼氏|かれし}と", "Bạn trai → 彼氏 (bộ 彳).")
g.context("nami", "Chọn câu viết đúng trong tình huống sau.", "Bạn nhắc bạn bè không nên tắm biển hôm nay vì sóng to.", [
    opt("a", "{今日|きょう}は{波|なみ}が{高|たか}いから、{海|うみ}に{入|はい}らないほうがいい。", "nami"),
    opt("b", "{今日|きょう}は{皮|かわ}が{高|たか}いから、{海|うみ}に{入|はい}らないほうがいい。", "kawa", "Sóng viết 波 (bộ 氵), không phải 皮."),
    opt("c", "{今日|きょう}は{彼|かれ}が{高|たか}いから、{海|うみ}に{入|はい}らないほうがいい。", "kare", "Sóng viết 波, không phải 彼."),
], "a", "Sóng → 波.")

# 6 ---------------------------------------------------------------------------------------------
g = group("shi-gi-eda", "支, 技 và 枝",
          "Ba chữ cùng có phần 支. 支 là chống đỡ, chi (tiền); thêm 扌 (tay) thành 技 – kỹ năng (khéo tay); thêm 木 (cây) thành 枝 – cành cây (nhánh tách ra từ thân).")
g.kanji("sasaeru", "n3k-k0652f", "支", "chống đỡ; chi ra; nhánh", ("支", "Chi – chính là bộ thủ"), "Hình bàn tay cầm cành tre: chống đỡ, tách nhánh. 支える, 支出, 支店.")
g.kanji("waza", "n3k-k06280", "技", "kỹ năng, kỹ thuật", ("扌", "Thủ (tay)"), "扌 + 支: đôi tay khéo léo → kỹ năng. 技術, 演技, 競技.")
g.kanji("eda", "n3k-k0679d", "枝", "cành cây", ("木", "Mộc (cây)"), "木 + 支: nhánh tách ra từ thân cây → cành. 枝, 枝豆.")
g.theory("Bộ bên trái", "Không có bộ: 支 (chống đỡ, chi tiền, chi nhánh). 扌 (tay) → 技 (kỹ năng khéo tay). 木 (cây) → 枝 (cành cây). 支 và 枝 cùng âm シ; 技 đọc ギ.")
g.theory("Từ hay gặp", "支: 支える (nâng đỡ), 支出 (chi tiêu), 支店 (chi nhánh), 支払う (thanh toán). 技: 技術 (kỹ thuật), 演技 (diễn xuất), 競技 (môn thi đấu). 枝: 枝 (cành), 枝豆 (đậu nành non).")
g.compare("Bộ bên trái", sasaeru="Không có", waza="扌 (tay)", eda="木 (cây)")
g.compare("Nghĩa", sasaeru="Chống đỡ, chi", waza="Kỹ năng", eda="Cành cây")
g.compare("Từ tiêu biểu", sasaeru="支える、支出、支店", waza="技術、演技、競技", eda="枝、枝豆")
g.example("sasaeru", "{家族|かぞく}が{私|わたし}を{支|ささ}えてくれた。", "Gia đình đã nâng đỡ tôi.")
g.example("sasaeru", "{今月|こんげつ}は{支出|ししゅつ}が{多|おお}すぎた。", "Tháng này chi tiêu nhiều quá.")
g.example("waza", "この{会社|かいしゃ}は{高|たか}い{技術|ぎじゅつ}を{持|も}っている。", "Công ty này có kỹ thuật cao.")
g.example("waza", "あの{俳優|はいゆう}の{演技|えんぎ}はすばらしい。", "Diễn xuất của diễn viên đó thật tuyệt.")
g.example("eda", "{雪|ゆき}の{重|おも}さで{木|き}の{枝|えだ}が{折|お}れた。", "Sức nặng của tuyết làm gãy cành cây.")
g.example("eda", "{夏|なつ}はビールと{枝豆|えだまめ}が{合|あ}う。", "Mùa hè bia rất hợp với đậu nành non.")
g.tip("Tay (扌) khéo → 技; cây (木) mọc nhánh → 枝; chỉ 支 thì là cái chống đỡ.")
g.mistake("Viết 'kỹ thuật'.", "{日本|にほん}の{支術|しじゅつ}はすばらしい。", "{日本|にほん}の{技術|ぎじゅつ}はすばらしい。", "Kỹ thuật (khéo tay) → 技 (bộ 扌).", "waza", "sasaeru")
g.mistake("Viết 'cành cây'.", "{鳥|とり}が{木|き}の{技|わざ}にとまっている。", "{鳥|とり}が{木|き}の{枝|えだ}にとまっている。", "Cành cây → 枝 (bộ 木).", "eda", "waza")
g.mc("waza", "Chọn cách viết đúng của えんぎ (diễn xuất).", [
    opt("a", "演支", "sasaeru", "支 là chống đỡ."),
    opt("b", "演枝", "eda", "枝 là cành cây."),
    opt("c", "演技", "waza"),
], "c", "演技: kỹ năng biểu diễn → 技 (bộ 扌).")
g.fill("sasaeru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{会計|かいけい}でカードで＿＿＿{払|はら}いをした。",
       "Tôi thanh toán bằng thẻ ở quầy tính tiền.", [
           opt("a", "支", "sasaeru"),
           opt("b", "技", "waza"),
           opt("c", "枝", "eda"),
       ], "a", "支払い (thanh toán) viết với 支 (chi ra).")
g.error("eda", "Chọn phần viết sai chữ Kanji.", "Gió mạnh làm cành hoa anh đào rung lắc.", [
    seg("{強|つよ}い{風|かぜ}で"), seg("{桜|さくら}の"), seg("{支|し}が", "sasaeru"), seg("{揺|ゆ}れている。"),
], 2, "{枝|えだ}が", "Cành cây → 枝.")
g.context("waza", "Chọn câu viết đúng trong tình huống sau.", "Bạn khen kỹ thuật của một thợ gốm lâu năm.", [
    opt("a", "{長年|ながねん}の{経験|けいけん}で{身|み}につけた{技術|ぎじゅつ}ですね。", "waza"),
    opt("b", "{長年|ながねん}の{経験|けいけん}で{身|み}につけた{支術|しじゅつ}ですね。", "sasaeru", "技術 viết với 技, không phải 支."),
    opt("c", "{長年|ながねん}の{経験|けいけん}で{身|み}につけた{枝術|しじゅつ}ですね。", "eda", "技術 viết với 技, không phải 枝."),
], "a", "技術 (kỹ thuật) viết với 技.")

# 7 ---------------------------------------------------------------------------------------------
g = group("kyuu-motomeru-tama-sukuu", "求, 球 và 救",
          "Ba chữ cùng đọc キュウ và cùng có phần 求. 求 là mong cầu, yêu cầu; thêm 王 (ngọc) thành 球 – quả cầu, quả bóng; thêm 攵 (đánh, hành động) thành 救 – cứu giúp.")
g.kanji("motomeru", "n3k-k06c42", "求", "yêu cầu, mong muốn, tìm kiếm", ("水", "Thủy (biến thể) – nét gốc của 求"), "Một mình 求: mong cầu, tìm kiếm. 求める, 要求, 求人.")
g.kanji("tama", "n3k-k07403", "球", "quả cầu, quả bóng", ("王", "Ngọc (viên ngọc)"), "王 (ngọc) + 求: viên ngọc tròn → quả cầu, bóng. 野球, 地球, 電球.")
g.kanji("sukuu", "n3k-k06551", "救", "cứu giúp", ("攵", "Phộc (đánh, hành động)"), "求 + 攵 (hành động): ra tay đáp lại lời cầu cứu → cứu. 救う, 救急車, 救助.")
g.theory("Phần thêm vào quyết định nghĩa", "Cùng phần 求 cho âm キュウ. Có 王 bên trái → 球 (vật tròn: bóng, cầu). Có 攵 bên phải → 救 (hành động cứu). Chỉ 求 → mong cầu, yêu cầu.")
g.theory("Từ hay gặp", "求: 求める (yêu cầu), 要求, 求人 (tuyển người). 球: 野球 (bóng chày), 地球 (trái đất), 電球 (bóng đèn). 救: 救う (cứu), 救急車 (xe cứu thương), 救助 (cứu hộ).")
g.compare("Phần thêm", motomeru="Không có", tama="王 bên trái", sukuu="攵 bên phải")
g.compare("Nghĩa", motomeru="Yêu cầu, mong cầu", tama="Quả cầu, bóng", sukuu="Cứu giúp")
g.compare("Từ tiêu biểu", motomeru="要求、求人", tama="野球、地球", sukuu="救急車、救助")
g.example("motomeru", "{客|きゃく}は{店|みせ}に{返金|へんきん}を{求|もと}めた。", "Khách yêu cầu cửa hàng hoàn tiền.")
g.example("motomeru", "この{会社|かいしゃ}は{今|いま}、{求人|きゅうじん}を{出|だ}している。", "Công ty này đang đăng tin tuyển người.")
g.example("tama", "{日曜日|にちようび}に{友達|ともだち}と{野球|やきゅう}をした。", "Chủ nhật tôi chơi bóng chày với bạn.")
g.example("tama", "{地球|ちきゅう}の{環境|かんきょう}を{守|まも}ろう。", "Hãy bảo vệ môi trường trái đất.")
g.example("sukuu", "{救急車|きゅうきゅうしゃ}を{呼|よ}んでください。", "Hãy gọi xe cứu thương.")
g.example("sukuu", "{消防士|しょうぼうし}が{火事|かじ}から{子供|こども}を{救|すく}った。", "Lính cứu hỏa đã cứu đứa trẻ khỏi đám cháy.")
g.tip("Ngọc (王) tròn → 球; ra tay (攵) → 救; chỉ mong (求) mà chưa làm gì.")
g.mistake("Viết 'xe cứu thương'.", "{球急車|きゅうきゅうしゃ}が{来|き}た。", "{救急車|きゅうきゅうしゃ}が{来|き}た。", "Cứu → 救 (có 攵).", "sukuu", "tama")
g.mistake("Viết 'trái đất'.", "{地求|ちきゅう}は{丸|まる}い。", "{地球|ちきゅう}は{丸|まる}い。", "Trái đất là quả cầu → 球 (có 王).", "tama", "motomeru")
g.mc("tama", "Chọn cách viết đúng của やきゅう (bóng chày).", [
    opt("a", "野球", "tama"),
    opt("b", "野求", "motomeru", "求 là mong cầu."),
    opt("c", "野救", "sukuu", "救 là cứu."),
], "a", "野球: môn chơi bằng quả bóng → 球.")
g.fill("motomeru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{住民|じゅうみん}は{市|し}に{道路|どうろ}の{修理|しゅうり}を{要|よう}＿＿＿した。",
       "Cư dân yêu cầu thành phố sửa đường.", [
           opt("a", "求", "motomeru"),
           opt("b", "球", "tama"),
           opt("c", "救", "sukuu"),
       ], "a", "要求 (yêu cầu) viết với 求.")
g.error("sukuu", "Chọn phần viết sai chữ Kanji.", "Đội cứu hộ đã cứu những người bị kẹt trên núi.", [
    seg("{山|やま}で{動|うご}けなくなった{人|ひと}を、"), seg("{球助隊|きゅうじょたい}が", "tama"), seg("{助|たす}け{出|だ}した。"),
], 1, "{救助隊|きゅうじょたい}が", "救助 (cứu hộ) viết với 救.")
g.context("motomeru", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng đang tìm việc và xem các tin tuyển dụng.", [
    opt("a", "{今|いま}、{求人|きゅうじん}{情報|じょうほう}を{見|み}ているところです。", "motomeru"),
    opt("b", "{今|いま}、{球人|きゅうじん}{情報|じょうほう}を{見|み}ているところです。", "tama", "求人 viết với 求, không phải 球."),
    opt("c", "{今|いま}、{救人|きゅうじん}{情報|じょうほう}を{見|み}ているところです。", "sukuu", "求人 viết với 求, không phải 救."),
], "a", "求人 (tuyển người) viết với 求.")

# 8 ---------------------------------------------------------------------------------------------
g = group("yo-jo-azukeru", "予, 序 và 預",
          "Ba chữ cùng có phần 予. 予 là trước (sẵn từ trước); thêm 广 (mái nhà) thành 序 – thứ tự, mở đầu; thêm 頁 (đầu, trang) thành 預 – gửi giữ.")
g.kanji("yo", "n3k-k04e88", "予", "trước, sẵn từ trước", ("亅", "Quyết (nét móc)"), "Một mình 予: trước. 予定, 予約, 予報, 予習.")
g.kanji("jo", "n3k-k05e8f", "序", "thứ tự, mở đầu", ("广", "Nghiễm (mái nhà)"), "广 + 予: các phòng trong nhà xếp theo trật tự → thứ tự. 順序, 序章.")
g.kanji("azukeru", "n3k-k09810", "預", "gửi, gửi giữ", ("頁", "Hiệt (đầu, trang)"), "予 + 頁: giao trước cho người khác giữ → gửi. 預ける, 預金.")
g.theory("Phần thêm vào", "予 (trước) là gốc. 广 (mái nhà) bao lấy → 序 (thứ tự). 頁 bên phải → 預 (gửi). 予 và 預 cùng âm ヨ, dễ nhầm trong 予金 (sai) ／ 預金 (đúng).")
g.theory("Từ hay gặp", "予: 予定 (dự định), 予約 (đặt trước), 予報 (dự báo), 予習 (học trước). 序: 順序 (thứ tự), 序章 (chương mở đầu). 預: 預ける (gửi), 預かる (giữ hộ), 預金 (tiền gửi).")
g.compare("Phần thêm", yo="Không có", jo="广 (mái nhà) bao ngoài", azukeru="頁 bên phải")
g.compare("Nghĩa", yo="Trước", jo="Thứ tự", azukeru="Gửi giữ")
g.compare("Từ tiêu biểu", yo="予定、予約、予報", jo="順序、序章", azukeru="預ける、預金")
g.example("yo", "{明日|あした}の{予定|よてい}はまだ{決|き}まっていない。", "Kế hoạch ngày mai vẫn chưa định.")
g.example("yo", "レストランを{予約|よやく}しておいた。", "Tôi đã đặt bàn nhà hàng trước.")
g.example("jo", "{順序|じゅんじょ}よく{並|なら}んでください。", "Xin hãy xếp hàng theo thứ tự.")
g.example("jo", "この{小説|しょうせつ}は{序章|じょしょう}から{面白|おもしろ}い。", "Cuốn tiểu thuyết này hay ngay từ chương mở đầu.")
g.example("azukeru", "{銀行|ぎんこう}にお{金|かね}を{預|あず}けた。", "Tôi gửi tiền vào ngân hàng.")
g.example("azukeru", "{毎月|まいつき}{少|すこ}しずつ{預金|よきん}している。", "Mỗi tháng tôi gửi tiết kiệm từng chút.")
g.tip("予 (trước) + nhà (广) = xếp thứ tự (序); + trang (頁) = giao gửi (預).")
g.mistake("Viết 'tiền gửi ngân hàng'.", "{銀行|ぎんこう}の{予金|よきん}を{下|お}ろした。", "{銀行|ぎんこう}の{預金|よきん}を{下|お}ろした。", "Tiền gửi → 預金 (gửi giữ).", "azukeru", "yo")
g.mistake("Viết 'dự báo thời tiết'.", "{天気|てんき}{預報|よほう}を{見|み}た。", "{天気|てんき}{予報|よほう}を{見|み}た。", "Dự báo (báo trước) → 予報.", "yo", "azukeru")
g.mc("yo", "Chọn cách viết đúng của よやく (đặt trước).", [
    opt("a", "預約", "azukeru", "預 là gửi giữ."),
    opt("b", "予約", "yo"),
    opt("c", "序約", "jo", "序 là thứ tự."),
], "b", "予約: hẹn trước → 予.")
g.fill("jo", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{説明書|せつめいしょ}の{順|じゅん}＿＿＿どおりに{組|く}み{立|た}ててください。",
       "Hãy lắp ráp theo đúng thứ tự trong sách hướng dẫn.", [
           opt("a", "序", "jo"),
           opt("b", "予", "yo"),
           opt("c", "預", "azukeru"),
       ], "a", "順序 (thứ tự) viết với 序.")
g.error("azukeru", "Chọn phần viết sai chữ Kanji.", "Tôi gửi hành lý ở quầy lễ tân khách sạn.", [
    seg("ホテルのフロントに"), seg("{荷物|にもつ}を"), seg("{予|よ}けた。", "yo"),
], 2, "{預|あず}けた。", "Gửi giữ → 預ける. (予 không có cách đọc あず.)")
g.context("yo", "Chọn câu viết đúng trong tình huống sau.", "Bạn nhắc học sinh học bài trước khi lên lớp.", [
    opt("a", "{明日|あした}の{授業|じゅぎょう}の{予習|よしゅう}をしておいてね。", "yo"),
    opt("b", "{明日|あした}の{授業|じゅぎょう}の{預習|よしゅう}をしておいてね。", "azukeru", "予習 viết với 予 (trước), không phải 預."),
    opt("c", "{明日|あした}の{授業|じゅぎょう}の{序習|じょしゅう}をしておいてね。", "jo", "予習 viết với 予, không phải 序."),
], "a", "予習 (học trước) viết với 予.")

# 9 ---------------------------------------------------------------------------------------------
g = group("kyo-kyo-kyo", "巨, 拒 và 距",
          "Ba chữ cùng đọc キョ và cùng có phần 巨. 巨 là to lớn; thêm 扌 (tay) thành 拒 – cự tuyệt (đưa tay đẩy ra); thêm 足 (chân) thành 距 – cự ly (khoảng cách bước chân).")
g.kanji("ookii", "n3k-k05de8", "巨", "to lớn, khổng lồ", ("工", "Công (thước thợ)"), "Hình cây thước lớn → to lớn. 巨大, 巨人.")
g.kanji("kobamu", "n3k-k062d2", "拒", "từ chối, cự tuyệt", ("扌", "Thủ (tay)"), "扌 + 巨: đưa tay đẩy ra → cự tuyệt. 拒否, 拒絶, 拒む.")
g.kanji("kyori", "n3k-k08ddd", "距", "cự ly, khoảng cách", ("⻊", "Túc (chân)"), "⻊ + 巨: khoảng cách đo bằng bước chân → cự ly. 距離.")
g.theory("Bộ bên trái", "Không có bộ: 巨 (to lớn). 扌 (tay) → 拒 (đẩy ra, từ chối). ⻊ (chân) → 距 (khoảng cách). Âm giống nhau hoàn toàn nên chỉ có thể dựa vào nghĩa của từ.")
g.theory("Từ hay gặp", "巨: 巨大 (khổng lồ), 巨人 (người khổng lồ), 巨額 (số tiền khổng lồ). 拒: 拒否 (từ chối), 拒絶 (cự tuyệt), 拒む. 距: 距離 (khoảng cách), 長距離 (đường dài).")
g.compare("Bộ bên trái", ookii="Không có", kobamu="扌 (tay)", kyori="⻊ (chân)")
g.compare("Nghĩa", ookii="To lớn", kobamu="Từ chối", kyori="Khoảng cách")
g.compare("Từ tiêu biểu", ookii="巨大、巨人", kobamu="拒否、拒む", kyori="距離、長距離")
g.example("ookii", "{海|うみ}の{底|そこ}で{巨大|きょだい}な{魚|さかな}が{見|み}つかった。", "Dưới đáy biển đã tìm thấy một con cá khổng lồ.")
g.example("ookii", "その{会社|かいしゃ}は{巨額|きょがく}の{借金|しゃっきん}を{抱|かか}えている。", "Công ty đó đang ôm khoản nợ khổng lồ.")
g.example("kobamu", "{彼|かれ}は{質問|しつもん}に{答|こた}えることを{拒否|きょひ}した。", "Anh ta từ chối trả lời câu hỏi.")
g.example("kobamu", "{住民|じゅうみん}は{工場|こうじょう}の{建設|けんせつ}を{拒|こば}んだ。", "Cư dân đã phản đối việc xây nhà máy.")
g.example("kyori", "{家|いえ}から{駅|えき}までの{距離|きょり}は{約|やく}{二|に}キロだ。", "Khoảng cách từ nhà đến ga khoảng hai cây số.")
g.example("kyori", "{長距離|ちょうきょり}の{運転|うんてん}は{疲|つか}れる。", "Lái xe đường dài thì mệt.")
g.tip("Tay (扌) đẩy ra → 拒; chân (⻊) bước đo → 距; chỉ 巨 là to lớn.")
g.mistake("Viết 'khoảng cách'.", "{二人|ふたり}の{間|あいだ}の{巨離|きょり}が{縮|ちぢ}まった。", "{二人|ふたり}の{間|あいだ}の{距離|きょり}が{縮|ちぢ}まった。", "Khoảng cách → 距 (bộ chân).", "kyori", "ookii")
g.mistake("Viết 'từ chối'.", "{会社|かいしゃ}は{要求|ようきゅう}を{距否|きょひ}した。", "{会社|かいしゃ}は{要求|ようきゅう}を{拒否|きょひ}した。", "Từ chối → 拒 (bộ tay).", "kobamu", "kyori")
g.mc("kyori", "Chọn cách viết đúng của きょり (khoảng cách).", [
    opt("a", "巨離", "ookii", "巨 là to lớn."),
    opt("b", "拒離", "kobamu", "拒 là từ chối."),
    opt("c", "距離", "kyori"),
], "c", "距離: khoảng cách bước chân → 距.")
g.fill("ookii", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{台風|たいふう}の{後|あと}、{海岸|かいがん}に＿＿＿{大|だい}な{木|き}が{流|なが}れ{着|つ}いた。",
       "Sau cơn bão, một thân cây khổng lồ trôi dạt vào bờ biển.", [
           opt("a", "巨", "ookii"),
           opt("b", "拒", "kobamu"),
           opt("c", "距", "kyori"),
       ], "a", "巨大 (khổng lồ) viết với 巨.")
g.error("kobamu", "Chọn phần viết sai chữ Kanji.", "Con mèo từ chối ăn thức ăn mới.", [
    seg("{猫|ねこ}は"), seg("{新|あたら}しいえさを"), seg("{食|た}べるのを"), seg("{巨否|きょひ}した。", "ookii"),
], 3, "{拒否|きょひ}した。", "Từ chối → 拒否.")
g.context("kyori", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng từ nhà đến trường xa nên đi xe đạp.", [
    opt("a", "{家|いえ}から{学校|がっこう}まで{距離|きょり}があるので、{自転車|じてんしゃ}で{通|かよ}っている。", "kyori"),
    opt("b", "{家|いえ}から{学校|がっこう}まで{巨離|きょり}があるので、{自転車|じてんしゃ}で{通|かよ}っている。", "ookii", "距離 viết với 距, không phải 巨."),
    opt("c", "{家|いえ}から{学校|がっこう}まで{拒離|きょり}があるので、{自転車|じてんしゃ}で{通|かよ}っている。", "kobamu", "距離 viết với 距, không phải 拒."),
], "a", "距離 (khoảng cách) viết với 距.")

# 10 --------------------------------------------------------------------------------------------
g = group("soku-soku-soku", "則, 側 và 測",
          "Ba chữ cùng đọc ソク và cùng có phần 則. 則 là quy tắc; thêm 亻 (người) thành 側 – phía, bên cạnh; thêm 氵 (nước) thành 測 – đo lường (đo mực nước).")
g.kanji("kisoku", "n3k-k05247", "則", "quy tắc, phép tắc", ("刂", "Đao (con dao)"), "貝 + 刂: khắc luật lên đồ đồng bằng dao → quy tắc. 規則, 原則.")
g.kanji("gawa", "n3k-k05074", "側", "phía, bên", ("亻", "Nhân đứng (người)"), "亻 + 則: người đứng ở một bên → phía, bên cạnh. 側, 右側, 側面.")
g.kanji("hakaru", "n3k-k06e2c", "測", "đo lường, ước lượng", ("氵", "Thủy (nước)"), "氵 + 則: đo mực nước theo quy chuẩn → đo. 測る, 測定, 予測.")
g.theory("Bộ bên trái", "Không có bộ: 則 (quy tắc). 亻 (người) → 側 (phía bên). 氵 (nước) → 測 (đo lường). Ba chữ đọc ソク giống nhau nên phải nhớ nghĩa của từ ghép.")
g.theory("Từ hay gặp", "則: 規則 (quy tắc), 原則 (nguyên tắc). 側: 右側 (bên phải), 内側 (bên trong), 側面 (mặt bên). 測: 測る (đo), 予測 (dự đoán), 測定 (đo đạc).")
g.compare("Bộ bên trái", kisoku="Không có (刂 bên phải)", gawa="亻 (người)", hakaru="氵 (nước)")
g.compare("Nghĩa", kisoku="Quy tắc", gawa="Phía, bên", hakaru="Đo lường")
g.compare("Từ tiêu biểu", kisoku="規則、原則", gawa="右側、内側、側面", hakaru="測る、予測、測定")
g.example("kisoku", "{学校|がっこう}の{規則|きそく}を{守|まも}ってください。", "Hãy tuân thủ nội quy của trường.")
g.example("kisoku", "{原則|げんそく}として、{遅刻|ちこく}は{認|みと}めません。", "Về nguyên tắc, không chấp nhận đi muộn.")
g.example("gawa", "{日本|にほん}では{車|くるま}は{道|みち}の{左側|ひだりがわ}を{走|はし}る。", "Ở Nhật xe chạy bên trái đường.")
g.example("gawa", "{窓|まど}の{内側|うちがわ}が{曇|くも}っている。", "Mặt trong cửa sổ bị mờ hơi nước.")
g.example("hakaru", "{毎朝|まいあさ}、{体温|たいおん}を{測|はか}っている。", "Sáng nào tôi cũng đo nhiệt độ cơ thể.")
g.example("hakaru", "{専門家|せんもんか}は{来年|らいねん}の{景気|けいき}を{予測|よそく}した。", "Các chuyên gia đã dự đoán tình hình kinh tế năm sau.")
g.tip("Người (亻) đứng bên → 側; nước (氵) đo mực → 測; dao (刂) khắc luật → 則.")
g.mistake("Viết 'nội quy'.", "{会社|かいしゃ}の{規側|きそく}に{従|したが}う。", "{会社|かいしゃ}の{規則|きそく}に{従|したが}う。", "Quy tắc → 則.", "kisoku", "gawa")
g.mistake("Viết 'dự đoán'.", "{結果|けっか}を{予則|よそく}するのは{難|むずか}しい。", "{結果|けっか}を{予測|よそく}するのは{難|むずか}しい。", "Dự đoán (đo trước) → 測.", "hakaru", "kisoku")
g.mc("gawa", "Chọn cách viết đúng của みぎがわ (bên phải).", [
    opt("a", "右測", "hakaru", "測 là đo lường."),
    opt("b", "右則", "kisoku", "則 là quy tắc."),
    opt("c", "右側", "gawa"),
], "c", "右側: phía bên phải → 側 (bộ 亻).")
g.fill("hakaru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{病院|びょういん}で{血圧|けつあつ}を＿＿＿ってもらった。",
       "Tôi được đo huyết áp ở bệnh viện.", [
           opt("a", "測", "hakaru"),
           opt("b", "則", "kisoku"),
           opt("c", "側", "gawa"),
       ], "a", "測る (đo) viết với 測.")
g.error("gawa", "Chọn phần viết sai chữ Kanji.", "Ở thang cuốn, người ta đứng bên trái.", [
    seg("エスカレーターでは、"), seg("{左測|ひだりそく}に", "hakaru"), seg("{立|た}つ{人|ひと}が{多|おお}い。"),
], 1, "{左側|ひだりがわ}に", "Bên trái → 左側.")
g.context("kisoku", "Chọn câu viết đúng trong tình huống sau.", "Bạn giải thích với bạn mới rằng ký túc xá có nội quy về giờ đóng cửa.", [
    opt("a", "この{寮|りょう}には{門限|もんげん}の{規則|きそく}があるよ。", "kisoku"),
    opt("b", "この{寮|りょう}には{門限|もんげん}の{規側|きそく}があるよ。", "gawa", "規則 viết với 則, không phải 側."),
    opt("c", "この{寮|りょう}には{門限|もんげん}の{規測|きそく}があるよ。", "hakaru", "規則 viết với 則, không phải 測."),
], "a", "規則 (nội quy) viết với 則.")

# 11 --------------------------------------------------------------------------------------------
g = group("kan-kan-kan", "官, 管 và 館",
          "Ba chữ cùng đọc カン và cùng có phần 官. 官 là quan chức, cơ quan nhà nước; thêm ⺮ (tre) thành 管 – ống, quản lý; thêm 食 (ăn) thành 館 – tòa nhà, hội quán (nơi ăn ở, đón khách).")
g.kanji("kan", "n3k-k05b98", "官", "quan chức, cơ quan nhà nước", ("宀", "Miên (mái nhà)"), "Mái nhà (宀) che chỗ làm việc của quan → quan chức. 警察官, 外交官.")
g.kanji("kuda", "n3k-k07ba1", "管", "ống; quản lý", ("⺮", "Trúc (tre)"), "⺮ + 官: ống tre; người nắm giữ ống sáo/chìa khóa → quản lý. 管理, 水道管, 保管.")
g.kanji("yakata", "n3k-k09928", "館", "tòa nhà, quán", ("食", "Thực (ăn)"), "食 + 官: nơi quan khách ăn ở → tòa nhà công cộng lớn. 図書館, 美術館, 旅館.")
g.theory("Bộ thủ", "官 một mình: người làm việc cho nhà nước. ⺮ (tre) ở trên → 管 (ống tre, quản lý). 食 (ăn) bên trái → 館 (tòa nhà: thư viện, bảo tàng, lữ quán).")
g.theory("Từ hay gặp", "官: 警察官 (cảnh sát), 外交官 (nhà ngoại giao), 官庁. 管: 管理 (quản lý), 保管 (bảo quản), 水道管 (ống nước). 館: 図書館 (thư viện), 美術館 (bảo tàng mỹ thuật), 旅館 (lữ quán), 映画館.")
g.compare("Bộ thủ", kan="宀 (mái nhà)", kuda="⺮ (tre) ở trên", yakata="食 (ăn) bên trái")
g.compare("Nghĩa", kan="Quan chức", kuda="Ống, quản lý", yakata="Tòa nhà công cộng")
g.compare("Từ tiêu biểu", kan="警察官、外交官", kuda="管理、保管", yakata="図書館、美術館、旅館")
g.example("kan", "{父|ちち}は{警察官|けいさつかん}として{働|はたら}いている。", "Bố tôi làm cảnh sát.")
g.example("kan", "{彼女|かのじょ}は{将来|しょうらい}、{外交官|がいこうかん}になりたいそうだ。", "Nghe nói cô ấy muốn trở thành nhà ngoại giao.")
g.example("kuda", "このビルの{管理|かんり}は{山田|やまだ}さんが{担当|たんとう}している。", "Anh Yamada phụ trách quản lý tòa nhà này.")
g.example("kuda", "{冬|ふゆ}は{水道管|すいどうかん}が{凍|こお}ることがある。", "Mùa đông có khi ống nước bị đóng băng.")
g.example("yakata", "{週末|しゅうまつ}は{図書館|としょかん}で{勉強|べんきょう}する。", "Cuối tuần tôi học ở thư viện.")
g.example("yakata", "{京都|きょうと}で{古|ふる}い{旅館|りょかん}に{泊|と}まった。", "Ở Kyoto tôi đã trọ ở một lữ quán cổ.")
g.tip("Có chỗ ăn (食) → tòa nhà đón khách 館; có ống tre (⺮) → 管; chỉ có mái nhà → quan 官.")
g.mistake("Viết 'thư viện'.", "{図書管|としょかん}で{本|ほん}を{借|か}りた。", "{図書館|としょかん}で{本|ほん}を{借|か}りた。", "Tòa nhà công cộng → 館 (bộ 食).", "yakata", "kuda")
g.mistake("Viết 'quản lý'.", "{商品|しょうひん}の{在庫|ざいこ}を{館理|かんり}する。", "{商品|しょうひん}の{在庫|ざいこ}を{管理|かんり}する。", "Quản lý → 管.", "kuda", "yakata")
g.mc("yakata", "Chọn cách viết đúng của びじゅつかん (bảo tàng mỹ thuật).", [
    opt("a", "美術館", "yakata"),
    opt("b", "美術管", "kuda", "管 là ống, quản lý."),
    opt("c", "美術官", "kan", "官 là quan chức."),
], "a", "美術館: tòa nhà trưng bày → 館.")
g.fill("kan", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{道|みち}が{分|わ}からなかったので、{交番|こうばん}の{警察|けいさつ}＿＿＿に{聞|き}いた。",
       "Không biết đường nên tôi hỏi viên cảnh sát ở đồn.", [
           opt("a", "官", "kan"),
           opt("b", "管", "kuda"),
           opt("c", "館", "yakata"),
       ], "a", "警察官 (viên cảnh sát) viết với 官.")
g.error("kuda", "Chọn phần viết sai chữ Kanji.", "Hãy bảo quản thuốc ở nơi mát.", [
    seg("{薬|くすり}は"), seg("{涼|すず}しい{場所|ばしょ}で"), seg("{保館|ほかん}してください。", "yakata"),
], 2, "{保管|ほかん}してください。", "保管 (bảo quản) viết với 管.")
g.context("yakata", "Chọn câu viết đúng trong tình huống sau.", "Bạn rủ bạn đi xem phim ở rạp gần ga.", [
    opt("a", "{駅前|えきまえ}の{映画館|えいがかん}に{行|い}かない？", "yakata"),
    opt("b", "{駅前|えきまえ}の{映画管|えいがかん}に{行|い}かない？", "kuda", "映画館 viết với 館, không phải 管."),
    opt("c", "{駅前|えきまえ}の{映画官|えいがかん}に{行|い}かない？", "kan", "映画館 viết với 館, không phải 官."),
], "a", "映画館 (rạp chiếu phim) viết với 館.")

# 12 --------------------------------------------------------------------------------------------
g = group("bou-nai-wasureru-nozomu", "亡, 忘 và 望",
          "Ba chữ cùng có phần 亡 và âm ボウ. 亡 là mất, chết; thêm 心 (tim) ở dưới thành 忘 – quên (tim mất đi điều gì); 望 (亡 + 月 + 王) là trông mong, hy vọng.")
g.kanji("nai", "n3k-k04ea1", "亡", "chết, mất", ("亠", "Đầu (nắp)"), "Người trốn vào góc khuất → biến mất, chết. 亡くなる, 死亡.")
g.kanji("wasureru", "n3k-k05fd8", "忘", "quên", ("心", "Tâm (tim) ở dưới"), "亡 trên 心: điều trong tim bị mất → quên. 忘れる, 忘れ物, 忘年会.")
g.kanji("nozomu", "n3k-k0671b", "望", "mong muốn, hy vọng; ngắm nhìn", ("月", "Nguyệt (trăng)"), "亡 + 月 + 王: người đứng ngóng trăng → trông mong. 希望, 望む, 失望.")
g.theory("Nhìn phần thêm vào", "亡 một mình: chết, mất (亡くなる: qua đời – cách nói lịch sự). 心 ở dưới → 忘 (quên). 月 và 王 → 望 (hy vọng, ngóng trông). Ba chữ đều có âm ボウ: 死亡 (しぼう), 忘年会 (ぼうねんかい), 希望 (きぼう).")
g.theory("Từ hay gặp", "亡: 亡くなる (qua đời), 死亡 (tử vong). 忘: 忘れる (quên), 忘れ物 (đồ để quên), 忘年会 (tiệc tất niên). 望: 希望 (hy vọng), 望む (mong muốn), 失望 (thất vọng).")
g.compare("Phần thêm", nai="Không có", wasureru="心 ở dưới", nozomu="月 và 王")
g.compare("Nghĩa", nai="Chết, mất", wasureru="Quên", nozomu="Mong muốn, hy vọng")
g.compare("Từ tiêu biểu", nai="亡くなる、死亡", wasureru="忘れる、忘れ物", nozomu="希望、望む")
g.example("nai", "{祖父|そふ}は{去年|きょねん}{亡|な}くなった。", "Ông tôi đã qua đời năm ngoái.")
g.example("nai", "その{事故|じこ}で{死亡|しぼう}した{人|ひと}はいなかった。", "Không có ai tử vong trong vụ tai nạn đó.")
g.example("wasureru", "{電車|でんしゃ}に{傘|かさ}を{忘|わす}れてしまった。", "Tôi để quên ô trên tàu.")
g.example("wasureru", "{十二月|じゅうにがつ}に{会社|かいしゃ}の{忘年会|ぼうねんかい}がある。", "Tháng mười hai có tiệc tất niên của công ty.")
g.example("nozomu", "{第一|だいいち}{希望|きぼう}の{大学|だいがく}に{合格|ごうかく}した。", "Tôi đã đỗ trường đại học nguyện vọng một.")
g.example("nozomu", "{多|おお}くの{人|ひと}が{平和|へいわ}を{望|のぞ}んでいる。", "Nhiều người mong muốn hòa bình.")
g.tip("Tim (心) mất (亡) điều gì → 忘 quên. Ngóng trăng (月) → 望 hy vọng.")
g.mistake("Viết 'hy vọng'.", "{将来|しょうらい}の{希忘|きぼう}を{話|はな}した。", "{将来|しょうらい}の{希望|きぼう}を{話|はな}した。", "Hy vọng → 望.", "nozomu", "wasureru")
g.mistake("Viết 'đồ để quên'.", "{望|のぞ}れ{物|もの}をしないように{気|き}をつけて。", "{忘|わす}れ{物|もの}をしないように{気|き}をつけて。", "Quên → 忘 (có 心).", "wasureru", "nozomu")
g.mc("wasureru", "Chọn cách viết đúng của ぼうねんかい (tiệc tất niên – 'quên năm cũ').", [
    opt("a", "望年会", "nozomu", "望 là hy vọng."),
    opt("b", "亡年会", "nai", "亡 là chết, mất."),
    opt("c", "忘年会", "wasureru"),
], "c", "忘年会: tiệc để quên đi năm cũ → 忘.")
g.fill("nai", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{恩師|おんし}が＿＿＿くなったという{知|し}らせを{受|う}けた。",
       "Tôi nhận được tin người thầy kính yêu đã qua đời.", [
           opt("a", "亡", "nai"),
           opt("b", "忘", "wasureru"),
           opt("c", "望", "nozomu"),
       ], "a", "亡くなる (qua đời) viết với 亡.")
g.error("nozomu", "Chọn phần viết sai chữ Kanji.", "Kết quả trận đấu làm người hâm mộ thất vọng.", [
    seg("{試合|しあい}の{結果|けっか}に、"), seg("ファンは"), seg("{失忘|しつぼう}した。", "wasureru"),
], 2, "{失望|しつぼう}した。", "Thất vọng (mất hy vọng) → 失望.")
g.context("wasureru", "Chọn câu viết đúng trong tình huống sau.", "Bạn nhắc em trai đừng quên mang bài tập.", [
    opt("a", "{宿題|しゅくだい}を{忘|わす}れないでね。", "wasureru"),
    opt("b", "{宿題|しゅくだい}を{亡|な}れないでね。", "nai", "Quên viết 忘れる (có 心)."),
    opt("c", "{宿題|しゅくだい}を{望|のぞ}れないでね。", "nozomu", "Quên viết 忘れる; 望 là mong muốn."),
], "a", "忘れる (quên) viết với 忘.")

# 13 --------------------------------------------------------------------------------------------
g = group("ju-ukeru-sazukeru", "受 và 授",
          "Hai chữ cùng đọc ジュ. 受 là nhận (tay đón lấy); 授 có thêm 扌 (tay) bên trái là trao, truyền dạy. Một bên nhận, một bên trao.")
g.kanji("ukeru", "n3k-k053d7", "受", "nhận, tiếp nhận", ("又", "Hựu (bàn tay)"), "Tay trên (爫) đưa, tay dưới (又) đón → nhận. 受ける, 受験, 受付.")
g.kanji("sazukeru", "n3k-k06388", "授", "trao, truyền dạy", ("扌", "Thủ (tay)"), "扌 + 受: thêm một bàn tay chủ động đưa ra → trao. 授業, 教授, 授ける.")
g.theory("Nhận hay trao", "受 (không có 扌): người nhận (試験を受ける: dự thi, 受付: quầy tiếp nhận). 授 (có 扌): người trao, người dạy (授業: giờ học – thầy truyền dạy; 教授: giáo sư).")
g.theory("Từ hay gặp", "受: 受ける, 受験 (dự thi), 受付 (lễ tân), 受信 (nhận tin), 受賞 (nhận giải). 授: 授業 (giờ học), 教授 (giáo sư), 授賞式 (lễ trao giải), 授かる.")
g.compare("Bộ trái", ukeru="Không có", sazukeru="扌 (tay)")
g.compare("Vai trò", ukeru="Người nhận", sazukeru="Người trao, người dạy")
g.compare("Từ tiêu biểu", ukeru="受験、受付、受賞", sazukeru="授業、教授、授賞式")
g.example("ukeru", "{来月|らいげつ}、{日本語|にほんご}{能力|のうりょく}{試験|しけん}を{受|う}ける。", "Tháng sau tôi dự thi năng lực tiếng Nhật.")
g.example("ukeru", "まず{受付|うけつけ}で{名前|なまえ}を{書|か}いてください。", "Trước tiên hãy ghi tên ở quầy tiếp nhận.")
g.example("sazukeru", "{今日|きょう}の{授業|じゅぎょう}は{三時|さんじ}に{終|お}わる。", "Giờ học hôm nay kết thúc lúc ba giờ.")
g.example("sazukeru", "{田中|たなか}{教授|きょうじゅ}は{経済学|けいざいがく}が{専門|せんもん}だ。", "Giáo sư Tanaka chuyên về kinh tế học.")
g.tip("Thêm tay (扌) là chủ động 'trao' → 授; không thêm là 'nhận' → 受.")
g.mistake("Viết 'giờ học'.", "{受業|じゅぎょう}に{遅|おく}れた。", "{授業|じゅぎょう}に{遅|おく}れた。", "Giờ học (thầy truyền dạy) → 授業.", "sazukeru", "ukeru")
g.mistake("Viết 'dự thi'.", "{大学|だいがく}を{授験|じゅけん}する。", "{大学|だいがく}を{受験|じゅけん}する。", "Dự thi (nhận bài thi) → 受験.", "ukeru", "sazukeru")
g.mc("sazukeru", "Chọn cách viết đúng của きょうじゅ (giáo sư).", [
    opt("a", "教受", "ukeru", "受 là nhận."),
    opt("b", "教授", "sazukeru"),
    opt("c", "教樹", None, "樹 là cây."),
], "b", "教授: người truyền dạy → 授.")
g.fill("ukeru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{彼|かれ}の{小説|しょうせつ}が{文学賞|ぶんがくしょう}を＿＿＿{賞|しょう}した。",
       "Tiểu thuyết của anh ấy đã nhận giải văn học.", [
           opt("a", "受", "ukeru"),
           opt("b", "授", "sazukeru"),
       ], "a", "受賞 (nhận giải) viết với 受.")
g.error("ukeru", "Chọn phần viết sai chữ Kanji.", "Xin hãy làm thủ tục ở quầy tiếp nhận tầng một.", [
    seg("{一階|いっかい}の"), seg("{授付|じゅつけ}で", "sazukeru"), seg("{手続|てつづ}きをしてください。"),
], 1, "{受付|うけつけ}で", "Quầy tiếp nhận → 受付.")
g.context("sazukeru", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng hôm nay giờ học tiếng Anh rất thú vị.", [
    opt("a", "{今日|きょう}の{英語|えいご}の{授業|じゅぎょう}はとても{面白|おもしろ}かった。", "sazukeru"),
    opt("b", "{今日|きょう}の{英語|えいご}の{受業|じゅぎょう}はとても{面白|おもしろ}かった。", "ukeru", "授業 viết với 授 (có 扌)."),
    opt("c", "{今日|きょう}の{英語|えいご}の{受験|じゅけん}はとても{面白|おもしろ}かった。", None, "受験 là dự thi, không phải giờ học."),
], "a", "授業 (giờ học) viết với 授.")

# 14 --------------------------------------------------------------------------------------------
g = group("shoku-shoku-shiki", "職, 織 và 識",
          "Ba chữ cùng có phần 戠 bên phải, chỉ khác bộ bên trái: 耳 (tai) → 職 – chức vụ, nghề; 糸 (sợi) → 織 – dệt, tổ chức; 言 (lời nói) → 識 – nhận biết, kiến thức.")
g.kanji("shigoto", "n3k-k08077", "職", "chức vụ, nghề nghiệp", ("耳", "Nhĩ (tai)"), "耳 bên trái: người lắng nghe và làm tròn phận sự → chức vụ, nghề. 職業, 就職, 職場.")
g.kanji("oru", "n3k-k07e54", "織", "dệt; tổ chức", ("糸", "Mịch (sợi chỉ)"), "糸 bên trái: kết sợi thành vải → dệt; kết người thành khối → 組織.")
g.kanji("shiru", "n3k-k08b58", "識", "nhận biết, kiến thức", ("言", "Ngôn (lời nói)"), "言 bên trái: qua lời nói mà hiểu biết → nhận biết. 知識, 常識, 意識.")
g.theory("Chỉ nhìn bộ trái", "Phần phải giống hệt nhau, nên bộ trái là tất cả: 耳 (tai) → 職; 糸 (sợi) → 織; 言 (lời nói) → 識. 職, 織 đọc ショク; 識, 織 đọc シキ (組織 そしき).")
g.theory("Từ hay gặp", "職: 職業 (nghề nghiệp), 就職 (tìm được việc), 職場 (nơi làm việc). 織: 組織 (tổ chức), 織る (dệt). 識: 知識 (kiến thức), 常識 (lẽ thường), 意識 (ý thức).")
g.compare("Bộ trái", shigoto="耳 (tai)", oru="糸 (sợi)", shiru="言 (lời nói)")
g.compare("Nghĩa", shigoto="Chức vụ, nghề", oru="Dệt, tổ chức", shiru="Nhận biết, kiến thức")
g.compare("Từ tiêu biểu", shigoto="職業、就職、職場", oru="組織、織る", shiru="知識、常識、意識")
g.example("shigoto", "{大学|だいがく}を{卒業|そつぎょう}して、{銀行|ぎんこう}に{就職|しゅうしょく}した。", "Tốt nghiệp đại học xong tôi vào làm ở ngân hàng.")
g.example("shigoto", "{新|あたら}しい{職場|しょくば}にもう{慣|な}れましたか。", "Anh đã quen nơi làm việc mới chưa?")
g.example("oru", "この{会社|かいしゃ}は{大|おお}きな{組織|そしき}だ。", "Công ty này là một tổ chức lớn.")
g.example("oru", "{祖母|そぼ}は{昔|むかし}、{布|ぬの}を{織|お}っていた。", "Ngày xưa bà tôi dệt vải.")
g.example("shiru", "{彼|かれ}は{歴史|れきし}の{知識|ちしき}が{豊富|ほうふ}だ。", "Anh ấy có kiến thức lịch sử phong phú.")
g.example("shiru", "{時間|じかん}を{守|まも}るのは{社会人|しゃかいじん}の{常識|じょうしき}だ。", "Đúng giờ là lẽ thường của người đi làm.")
g.tip("Tai (耳) nghe việc → 職; sợi (糸) dệt → 織; lời (言) dạy → 識.")
g.mistake("Viết 'kiến thức'.", "{日本|にほん}の{文化|ぶんか}について{知職|ちしょく}を{深|ふか}めたい。", "{日本|にほん}の{文化|ぶんか}について{知識|ちしき}を{深|ふか}めたい。", "Kiến thức → 識 (bộ 言).", "shiru", "shigoto")
g.mistake("Viết 'tổ chức'.", "{国際|こくさい}{組識|そしき}で{働|はたら}きたい。", "{国際|こくさい}{組織|そしき}で{働|はたら}きたい。", "Tổ chức (kết thành khối như dệt) → 織 (bộ 糸).", "oru", "shiru")
g.mc("shigoto", "Chọn cách viết đúng của しょくぎょう (nghề nghiệp).", [
    opt("a", "職業", "shigoto"),
    opt("b", "織業", "oru", "織 là dệt."),
    opt("c", "識業", "shiru", "識 là nhận biết."),
], "a", "職業 viết với 職 (bộ 耳).")
g.fill("shiru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{環境|かんきょう}{問題|もんだい}に{対|たい}する{意|い}＿＿＿が{高|たか}まっている。",
       "Ý thức về vấn đề môi trường đang được nâng cao.", [
           opt("a", "識", "shiru"),
           opt("b", "職", "shigoto"),
           opt("c", "織", "oru"),
       ], "a", "意識 (ý thức) viết với 識.")
g.error("shigoto", "Chọn phần viết sai chữ Kanji.", "Anh ấy đổi việc sang một công ty IT.", [
    seg("{彼|かれ}は"), seg("IT{企業|きぎょう}に"), seg("{転織|てんしょく}した。", "oru"),
], 2, "{転職|てんしょく}した。", "Chuyển việc → 転職 (bộ 耳).")
g.context("oru", "Chọn câu viết đúng trong tình huống sau.", "Bạn giới thiệu rằng mình làm cho một tổ chức tình nguyện.", [
    opt("a", "ボランティアの{組織|そしき}で{働|はたら}いています。", "oru"),
    opt("b", "ボランティアの{組職|そしょく}で{働|はたら}いています。", "shigoto", "組織 viết với 織 (bộ 糸)."),
    opt("c", "ボランティアの{組識|そしき}で{働|はたら}いています。", "shiru", "組織 viết với 織, không phải 識."),
], "a", "組織 (tổ chức) viết với 織.")

# 15 --------------------------------------------------------------------------------------------
g = group("fuku-fuku-fuku", "復, 複 và 腹",
          "Ba chữ cùng đọc フク và cùng có phần 复 bên phải. 彳 (bước chân) → 復 – trở lại, lặp lại; 衤 (áo) → 複 – kép, phức (áo nhiều lớp); 月 (thịt, cơ thể) → 腹 – bụng.")
g.kanji("kaeru", "n3k-k05fa9", "復", "trở lại, khôi phục, lặp lại", ("彳", "Xích (bước chân)"), "彳 + 复: bước đi rồi quay lại → trở lại, ôn lại. 復習, 往復, 回復.")
g.kanji("kasaneru", "n3k-k08907", "複", "kép, phức tạp, nhiều", ("衤", "Y (áo)"), "衤 + 复: áo may nhiều lớp → kép, nhiều. 複雑, 複数, 複写.")
g.kanji("hara", "n3k-k08179", "腹", "bụng", ("月", "Nhục (thịt, cơ thể)"), "月 (cơ thể) + 复 → bụng. お腹, 空腹, 腹が立つ.")
g.theory("Bộ trái quyết định", "彳 (bước chân) → 復 (đi rồi về: 往復, ôn lại: 復習). 衤 (áo, chú ý có thêm một chấm so với 礻) → 複 (nhiều lớp: 複雑, 複数). 月 (cơ thể) → 腹 (bụng).")
g.theory("Từ hay gặp", "復: 復習 (ôn tập), 往復 (khứ hồi), 回復 (hồi phục). 複: 複雑 (phức tạp), 複数 (số nhiều), 重複. 腹: お腹 (bụng), 空腹 (đói bụng), 腹が立つ (bực mình).")
g.compare("Bộ trái", kaeru="彳 (bước chân)", kasaneru="衤 (áo)", hara="月 (cơ thể)")
g.compare("Nghĩa", kaeru="Trở lại, lặp lại", kasaneru="Kép, phức tạp", hara="Bụng")
g.compare("Từ tiêu biểu", kaeru="復習、往復、回復", kasaneru="複雑、複数", hara="お腹、空腹")
g.example("kaeru", "{授業|じゅぎょう}の{後|あと}は、{必|かなら}ず{復習|ふくしゅう}している。", "Sau giờ học tôi luôn ôn lại bài.")
g.example("kaeru", "{東京|とうきょう}まで{往復|おうふく}の{切符|きっぷ}を{買|か}った。", "Tôi mua vé khứ hồi đi Tokyo.")
g.example("kasaneru", "この{問題|もんだい}は{思|おも}ったより{複雑|ふくざつ}だ。", "Vấn đề này phức tạp hơn tôi nghĩ.")
g.example("kasaneru", "{答|こた}えが{複数|ふくすう}ある{質問|しつもん}もあります。", "Cũng có câu hỏi có nhiều đáp án.")
g.example("hara", "{朝|あさ}から{何|なに}も{食|た}べていないので、{お腹|おなか}が{空|す}いた。", "Từ sáng chưa ăn gì nên đói bụng.")
g.example("hara", "{彼|かれ}の{態度|たいど}に{腹|はら}が{立|た}った。", "Tôi bực mình vì thái độ của anh ta.")
g.tip("Đi đi về về (彳) → 復; áo nhiều lớp (衤) → 複; cơ thể (月) → 腹.")
g.mistake("Viết 'ôn tập'.", "{試験|しけん}の{前|まえ}に{複習|ふくしゅう}する。", "{試験|しけん}の{前|まえ}に{復習|ふくしゅう}する。", "Ôn lại (lặp lại) → 復 (bộ 彳).", "kaeru", "kasaneru")
g.mistake("Viết 'phức tạp'.", "{手続|てつづ}きが{復雑|ふくざつ}で{分|わ}からない。", "{手続|てつづ}きが{複雑|ふくざつ}で{分|わ}からない。", "Phức tạp (nhiều lớp) → 複 (bộ 衤).", "kasaneru", "kaeru")
g.mc("kasaneru", "Chọn cách viết đúng của ふくざつ (phức tạp).", [
    opt("a", "復雑", "kaeru", "復 là trở lại."),
    opt("b", "腹雑", "hara", "腹 là bụng."),
    opt("c", "複雑", "kasaneru"),
], "c", "複雑: nhiều lớp đan xen → 複.")
g.fill("hara", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{空|くう}＿＿＿のときに{薬|くすり}を{飲|の}まないでください。",
       "Đừng uống thuốc khi bụng đói.", [
           opt("a", "腹", "hara"),
           opt("b", "復", "kaeru"),
           opt("c", "複", "kasaneru"),
       ], "a", "空腹 (đói bụng) viết với 腹.")
g.error("kaeru", "Chọn phần viết sai chữ Kanji.", "Sau phẫu thuật, sức khỏe bà tôi đã hồi phục.", [
    seg("{手術|しゅじゅつ}の{後|あと}、"), seg("{祖母|そぼ}の{体調|たいちょう}は"), seg("{回腹|かいふく}した。", "hara"),
], 2, "{回復|かいふく}した。", "Hồi phục (trở lại như cũ) → 回復.")
g.context("kaeru", "Chọn câu viết đúng trong tình huống sau.", "Bạn hỏi nhân viên nhà ga mua vé khứ hồi đi Osaka.", [
    opt("a", "{大阪|おおさか}までの{往復|おうふく}{切符|きっぷ}をください。", "kaeru"),
    opt("b", "{大阪|おおさか}までの{往複|おうふく}{切符|きっぷ}をください。", "kasaneru", "往復 viết với 復 (bộ 彳), không phải 複."),
    opt("c", "{大阪|おおさか}までの{往腹|おうふく}{切符|きっぷ}をください。", "hara", "往復 viết với 復, không phải 腹."),
], "a", "往復 (khứ hồi) viết với 復.")

# 16 --------------------------------------------------------------------------------------------
g = group("hirou-suteru", "拾 và 捨",
          "Hai chữ cùng bộ 扌 (tay) và nghĩa đối lập: 拾 là nhặt lên, 捨 là vứt bỏ. Người học hay viết nhầm vì hình dạng giống và luôn đi thành cặp.")
g.kanji("hirou", "n3k-k062fe", "拾", "nhặt", ("扌", "Thủ (tay)"), "扌 + 合 (hợp): tay gom lại → nhặt. 拾う, 拾得物.")
g.kanji("suteru", "n3k-k06368", "捨", "vứt bỏ", ("扌", "Thủ (tay)"), "扌 + 舎 (nhà, bỏ lại): tay buông ra → vứt. 捨てる, ゴミを捨てる.")
g.theory("Nhìn phần bên phải", "拾: bên phải là 合 (nắp đậy lên miệng hộp: gom vào) → nhặt. 捨: bên phải là 舎 (căn nhà: bỏ lại sau lưng) → vứt. 舎 có nhiều nét hơn, có hình mái nhà và chữ 土.")
g.theory("Cách đọc", "拾: ひろ.う, シュウ (拾得物: đồ nhặt được); cũng là chữ 'mười' trên chứng từ. 捨: す.てる, シャ (取捨選択: chọn lọc).")
g.compare("Phần bên phải", hirou="合 (hợp)", suteru="舎 (nhà)")
g.compare("Nghĩa", hirou="Nhặt lên", suteru="Vứt bỏ")
g.compare("Động từ", hirou="拾う", suteru="捨てる")
g.example("hirou", "{道|みち}で{財布|さいふ}を{拾|ひろ}って、{交番|こうばん}に{届|とど}けた。", "Tôi nhặt được ví trên đường và nộp cho đồn cảnh sát.")
g.example("hirou", "{子供|こども}たちが{公園|こうえん}のゴミを{拾|ひろ}っている。", "Bọn trẻ đang nhặt rác trong công viên.")
g.example("suteru", "ゴミは{決|き}められた{日|ひ}に{捨|す}ててください。", "Hãy vứt rác vào ngày quy định.")
g.example("suteru", "{古|ふる}い{服|ふく}を{捨|す}てて、{部屋|へや}を{片付|かたづ}けた。", "Tôi vứt quần áo cũ và dọn phòng.")
g.tip("拾: 合 = gom lại (nhặt). 捨: 舎 = bỏ lại nhà (vứt).")
g.mistake("Viết 'vứt rác'.", "ここにゴミを{拾|ひろ}てないでください。", "ここにゴミを{捨|す}てないでください。", "Vứt → 捨てる. (拾 đọc ひろ, chữ viết sai làm câu đọc thành ひろてない vô nghĩa.)", "suteru", "hirou")
g.mistake("Viết 'nhặt lá rụng'.", "{庭|にわ}の{落|お}ち{葉|ば}を{捨|す}った。", "{庭|にわ}の{落|お}ち{葉|ば}を{拾|ひろ}った。", "Nhặt → 拾う.", "hirou", "suteru")
g.mc("suteru", "Chọn chữ có nghĩa 'vứt bỏ'.", [
    opt("a", "捨", "suteru"),
    opt("b", "拾", "hirou", "拾 là nhặt."),
    opt("c", "給", None, "給 là cấp, lương."),
], "a", "捨 (bên phải 舎) là vứt bỏ.")
g.fill("hirou", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{落|お}とした{消|け}しゴムを{友達|ともだち}が＿＿＿ってくれた。",
       "Bạn tôi nhặt giúp cục tẩy tôi đánh rơi.", [
           opt("a", "拾", "hirou"),
           opt("b", "捨", "suteru"),
       ], "a", "拾う (nhặt) viết với 拾.")
g.error("suteru", "Chọn phần viết sai chữ Kanji.", "Không được vứt chai nhựa ở đây.", [
    seg("ペットボトルを"), seg("ここに"), seg("{拾|ひろ}ててはいけません。", "hirou"),
], 2, "{捨|す}ててはいけません。", "Vứt → 捨てる.")
g.context("hirou", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng đã nhặt được một chú mèo con bị bỏ rơi.", [
    opt("a", "{捨|す}てられていた{子猫|こねこ}を{拾|ひろ}った。", "hirou"),
    opt("b", "{拾|ひろ}われていた{子猫|こねこ}を{捨|す}った。", "suteru", "Câu đảo ngược nghĩa và 捨った là sai cách chia (捨てる → 捨てた)."),
    opt("c", "{捨|す}てられていた{子猫|こねこ}を{捨|す}てた。", None, "Nghĩa là 'vứt bỏ con mèo bị bỏ rơi', trái tình huống."),
], "a", "Nhặt con mèo bị bỏ rơi (捨てられていた) → 拾った.")

# 17 --------------------------------------------------------------------------------------------
g = group("koori-eien-oyogu", "氷, 永 và 泳",
          "Ba chữ hình dáng rất gần nhau: 永 (VĨNH, lâu dài) là gốc; thêm một chấm bên trái thành 氷 (BĂNG, nước đá); thêm bộ 氵 (nước) thành 泳 (VỊNH, bơi).")
g.kanji("koori", "n3k-k06c37", "氷", "băng, nước đá", ("水", "Thủy (nước)"), "水 (nước) thêm một chấm: nước đông lại → đá. 氷, 氷山, かき氷.")
g.kanji("nagai", "n3k-k06c38", "永", "lâu dài, vĩnh viễn", ("水", "Thủy (nước)"), "Dòng nước chảy mãi → lâu dài. 永遠, 永久, 永住.")
g.kanji("oyogu", "n3k-k06cf3", "泳", "bơi", ("氵", "Thủy (nước) bên trái"), "氵 + 永: người ở lâu trong nước → bơi. 泳ぐ, 水泳.")
g.theory("Nhìn chấm và bộ nước", "永: có chấm ở đỉnh, không có chấm bên trái. 氷: giống 水 thêm một chấm nhỏ bên trái (giọt nước đóng băng). 泳: bộ 氵 bên trái + 永.")
g.theory("Âm đọc", "氷: こおり, ヒョウ (氷山). 永: なが.い, エイ (永遠). 泳: およ.ぐ, エイ (水泳). 永 và 泳 cùng âm エイ nên dễ nhầm trong 水泳 ／ 永遠.")
g.compare("Đặc điểm hình", koori="水 + chấm bên trái", nagai="Chấm ở đỉnh, không bộ", oyogu="氵 + 永")
g.compare("Nghĩa", koori="Băng, đá", nagai="Lâu dài", oyogu="Bơi")
g.compare("Từ tiêu biểu", koori="氷、氷山、かき氷", nagai="永遠、永久、永住", oyogu="泳ぐ、水泳")
g.example("koori", "ジュースに{氷|こおり}を{入|い}れてください。", "Cho đá vào nước ép giúp tôi.")
g.example("koori", "{夏祭|なつまつ}りでかき{氷|ごおり}を{食|た}べた。", "Tôi ăn đá bào ở lễ hội mùa hè.")
g.example("nagai", "{二人|ふたり}は{永遠|えいえん}の{愛|あい}を{誓|ちか}った。", "Hai người thề nguyện tình yêu vĩnh cửu.")
g.example("nagai", "{彼|かれ}は{日本|にほん}に{永住|えいじゅう}するつもりだ。", "Anh ấy định định cư lâu dài ở Nhật.")
g.example("oyogu", "{子供|こども}のころから{水泳|すいえい}を{習|なら}っている。", "Tôi học bơi từ nhỏ.")
g.example("oyogu", "{海|うみ}で{泳|およ}ぐのは{気持|きも}ちがいい。", "Bơi ở biển thật dễ chịu.")
g.tip("Thêm 'giọt nước' (氵) → bơi 泳; thêm 'một hạt băng' (chấm trái) → đá 氷; không thêm gì → lâu dài 永.")
g.mistake("Viết 'bơi lội'.", "{趣味|しゅみ}は{水永|すいえい}です。", "{趣味|しゅみ}は{水泳|すいえい}です。", "Bơi → 泳 (có 氵).", "oyogu", "nagai")
g.mistake("Viết 'nước đá'.", "コップに{永|えい}を{入|い}れた。", "コップに{氷|こおり}を{入|い}れた。", "Đá → 氷 (chấm bên trái).", "koori", "nagai")
g.mc("nagai", "Chọn cách viết đúng của えいえん (vĩnh viễn).", [
    opt("a", "泳遠", "oyogu", "泳 là bơi."),
    opt("b", "永遠", "nagai"),
    opt("c", "氷遠", "koori", "氷 là băng."),
], "b", "永遠: lâu dài mãi → 永.")
g.fill("koori", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{寒|さむ}い{朝|あさ}、{池|いけ}に＿＿＿が{張|は}っていた。",
       "Buổi sáng lạnh, mặt ao đóng một lớp băng.", [
           opt("a", "氷", "koori"),
           opt("b", "永", "nagai"),
           opt("c", "泳", "oyogu"),
       ], "a", "Băng → 氷.")
g.error("oyogu", "Chọn phần viết sai chữ Kanji.", "Em gái tôi bơi giỏi hơn tôi.", [
    seg("{妹|いもうと}は"), seg("{私|わたし}より"), seg("{永|なが}ぐのが{上手|じょうず}だ。", "nagai"),
], 2, "{泳|およ}ぐのが{上手|じょうず}だ。", "Bơi → 泳ぐ.")
g.context("oyogu", "Chọn câu viết đúng trong tình huống sau.", "Bạn rủ bạn cuối tuần đi bể bơi.", [
    opt("a", "{週末|しゅうまつ}、プールに{泳|およ}ぎに{行|い}こうよ。", "oyogu"),
    opt("b", "{週末|しゅうまつ}、プールに{永|なが}ぎに{行|い}こうよ。", "nagai", "Bơi viết 泳ぐ (có 氵)."),
    opt("c", "{週末|しゅうまつ}、プールに{氷|こお}ぎに{行|い}こうよ。", "koori", "Bơi viết 泳ぐ, không phải 氷."),
], "a", "泳ぐ (bơi) viết với 泳.")

# 18 --------------------------------------------------------------------------------------------
g = group("han-hen-saka-ita", "反, 返, 坂 và 板",
          "Bốn chữ cùng có phần 反. 反 là ngược lại; thêm 辶 (bước đi) thành 返 – trả lại; thêm 土 (đất) thành 坂 – con dốc; thêm 木 (gỗ) thành 板 – tấm ván.")
g.kanji("han", "n3k-k053cd", "反", "ngược lại, phản đối", ("又", "Hựu (bàn tay)"), "Tay lật ngược tấm vải → ngược lại. 反対, 反省, 反応.")
g.kanji("kaesu", "n3k-k08fd4", "返", "trả lại, đáp lại", ("辶", "Sước (bước đi)"), "辶 + 反: đi ngược trở lại → trả lại. 返す, 返事, 返信.")
g.kanji("saka", "n3k-k05742", "坂", "con dốc", ("土", "Thổ (đất)"), "土 + 反: mặt đất nghiêng lật lên → con dốc. 坂, 坂道, 上り坂.")
g.kanji("ita", "n3k-k0677f", "板", "tấm ván, tấm bảng", ("木", "Mộc (gỗ)"), "木 + 反: miếng gỗ phẳng → tấm ván. 板, 黒板, 看板.")
g.theory("Bộ thủ quyết định", "Không có bộ: 反 (ngược, phản). 辶 (đi) → 返 (đi ngược lại: trả). 土 (đất) → 坂 (dốc). 木 (gỗ) → 板 (ván, bảng).")
g.theory("Âm đọc", "反: ハン (反対); 返: ヘン, かえ.す (返事); 坂: さか, ハン; 板: いた, ハン／バン (黒板 こくばん). Ba chữ đọc ハン nên khi viết từ Hán phải nhớ nghĩa.")
g.compare("Bộ thủ", han="Không có", kaesu="辶 (bước đi)", saka="土 (đất)", ita="木 (gỗ)")
g.compare("Nghĩa", han="Ngược lại", kaesu="Trả lại", saka="Con dốc", ita="Tấm ván, bảng")
g.compare("Từ tiêu biểu", han="反対、反省", kaesu="返事、返す", saka="坂道、上り坂", ita="黒板、看板")
g.example("han", "{多|おお}くの{住民|じゅうみん}がその{計画|けいかく}に{反対|はんたい}した。", "Nhiều cư dân phản đối kế hoạch đó.")
g.example("han", "{失敗|しっぱい}を{反省|はんせい}して、{次|つぎ}に{生|い}かしたい。", "Tôi muốn kiểm điểm thất bại để rút kinh nghiệm cho lần sau.")
g.example("kaesu", "{図書館|としょかん}に{本|ほん}を{返|かえ}しに{行|い}く。", "Tôi đi trả sách cho thư viện.")
g.example("kaesu", "メールの{返事|へんじ}がまだ{来|こ}ない。", "Thư trả lời email vẫn chưa đến.")
g.example("saka", "この{坂|さか}を{上|のぼ}ると、{学校|がっこう}が{見|み}える。", "Leo hết con dốc này sẽ thấy trường học.")
g.example("saka", "{自転車|じてんしゃ}で{急|きゅう}な{坂道|さかみち}を{下|くだ}った。", "Tôi đạp xe xuống con dốc đứng.")
g.example("ita", "{先生|せんせい}が{黒板|こくばん}に{字|じ}を{書|か}いた。", "Thầy giáo viết chữ lên bảng đen.")
g.example("ita", "{店|みせ}の{前|まえ}に{大|おお}きな{看板|かんばん}がある。", "Trước cửa hàng có tấm biển lớn.")
g.tip("Đi (辶) ngược lại → trả 返; đất (土) dựng ngược → dốc 坂; gỗ (木) phẳng → ván 板.")
g.mistake("Viết 'trả lời'.", "{友達|ともだち}に{反事|はんじ}を{書|か}いた。", "{友達|ともだち}に{返事|へんじ}を{書|か}いた。", "Trả lời (đáp lại) → 返 (có 辶).", "kaesu", "han")
g.mistake("Viết 'bảng đen'.", "{黒坂|こくはん}に{名前|なまえ}を{書|か}いてください。", "{黒板|こくばん}に{名前|なまえ}を{書|か}いてください。", "Bảng (tấm gỗ) → 板 (bộ 木).", "ita", "saka")
g.mc("saka", "Chọn chữ có nghĩa 'con dốc'.", [
    opt("a", "板", "ita", "板 là tấm ván."),
    opt("b", "坂", "saka"),
    opt("c", "返", "kaesu", "返 là trả lại."),
], "b", "坂 (bộ 土): con dốc.")
g.fill("kaesu", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{借|か}りたお{金|かね}は{来月|らいげつ}までに＿＿＿します。",
       "Tiền đã mượn tôi sẽ trả trước tháng sau.", [
           opt("a", "返", "kaesu"),
           opt("b", "反", "han"),
           opt("c", "板", "ita"),
       ], "a", "返す (trả lại) viết với 返.")
g.error("han", "Chọn phần viết sai chữ Kanji.", "Tôi phản đối ý kiến của anh ấy.", [
    seg("{私|わたし}は"), seg("{彼|かれ}の{意見|いけん}に"), seg("{返対|へんたい}です。", "kaesu"),
], 2, "{反対|はんたい}です。", "Phản đối → 反対.")
g.context("ita", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể đã làm một cái kệ bằng tấm ván gỗ.", [
    opt("a", "{木|き}の{板|いた}で{棚|たな}を{作|つく}った。", "ita"),
    opt("b", "{木|き}の{坂|さか}で{棚|たな}を{作|つく}った。", "saka", "Tấm ván viết 板 (bộ 木), 坂 là con dốc."),
    opt("c", "{木|き}の{反|はん}で{棚|たな}を{作|つく}った。", "han", "反 không có nghĩa tấm ván."),
], "a", "Tấm ván → 板.")

# 19 --------------------------------------------------------------------------------------------
g = group("kata-kata", "型 và 形",
          "Hai chữ cùng đọc かた／ケイ và cùng có phần 开. 型 (có 土 ở dưới) là khuôn mẫu, kiểu, loại; 形 (có 彡) là hình dạng, hình dáng bên ngoài.")
g.kanji("kata", "n3k-k0578b", "型", "khuôn, kiểu mẫu, loại", ("土", "Thổ (đất) ở dưới"), "刑 + 土: khuôn đất để đúc → khuôn mẫu, kiểu. 新型, 大型, 血液型.")
g.kanji("katachi", "n3k-k05f62", "形", "hình dạng", ("彡", "Sam (nét lông, hoa văn)"), "开 + 彡: đường nét trang trí → hình dạng. 形, 人形, 図形.")
g.theory("Khuôn mẫu hay hình dạng", "Kiểu, loại, mẫu dùng để phân loại hay đúc ra nhiều cái giống nhau (新型のスマホ, 大型車, 血液型) → 型. Hình dáng bên ngoài của một vật (丸い形, 人形, 三角形) → 形.")
g.theory("Mẹo hình chữ", "型 có 土 (đất) ở dưới: khuôn bằng đất. 形 có 彡 (ba nét xiên) bên phải: đường nét tạo hình. Hỏi 'loại nào?' → 型; hỏi 'hình gì?' → 形.")
g.compare("Bộ phận nhận biết", kata="土 ở dưới", katachi="彡 bên phải")
g.compare("Nghĩa", kata="Khuôn, kiểu, loại", katachi="Hình dạng")
g.compare("Từ tiêu biểu", kata="新型、大型、血液型", katachi="形、人形、三角形")
g.example("kata", "{新型|しんがた}のスマートフォンが{発売|はつばい}された。", "Mẫu điện thoại thông minh mới đã được bán ra.")
g.example("kata", "あなたの{血液型|けつえきがた}は{何|なん}ですか。", "Nhóm máu của bạn là gì?")
g.example("katachi", "このクッキーは{星|ほし}の{形|かたち}をしている。", "Chiếc bánh quy này có hình ngôi sao.")
g.example("katachi", "{娘|むすめ}は{人形|にんぎょう}と{遊|あそ}ぶのが{好|す}きだ。", "Con gái tôi thích chơi búp bê.")
g.tip("Đất (土) làm khuôn → 型 (kiểu mẫu). Nét vẽ (彡) tạo hình → 形 (hình dạng).")
g.mistake("Viết 'nhóm máu'.", "{血液形|けつえきけい}を{調|しら}べた。", "{血液型|けつえきがた}を{調|しら}べた。", "Nhóm máu là phân loại → 型.", "kata", "katachi")
g.mistake("Viết 'hình tròn'.", "{丸|まる}い{型|かた}のテーブルを{買|か}った。", "{丸|まる}い{形|かたち}のテーブルを{買|か}った。", "Hình dáng bên ngoài → 形.", "katachi", "kata")
g.mc("kata", "Chọn cách viết đúng của おおがた (cỡ lớn, loại lớn).", [
    opt("a", "大形", "katachi", "形 là hình dạng; 'cỡ lớn' như 大型車 viết 型."),
    opt("b", "大型", "kata"),
    opt("c", "大方", None, "大方 (おおかた) là 'phần lớn', nghĩa khác."),
], "b", "大型 (loại lớn) viết với 型.")
g.fill("katachi", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{三角|さんかく}＿＿＿の{面積|めんせき}を{求|もと}めなさい。",
       "Hãy tính diện tích hình tam giác.", [
           opt("a", "形", "katachi"),
           opt("b", "型", "kata"),
       ], "a", "三角形 (hình tam giác) viết với 形.")
g.error("kata", "Chọn phần viết sai chữ Kanji.", "Mẫu xe mới sẽ được bán vào mùa xuân năm sau.", [
    seg("{新形|しんけい}の", "katachi"), seg("{車|くるま}は"), seg("{来年|らいねん}の{春|はる}に{発売|はつばい}される。"),
], 0, "{新型|しんがた}の", "Mẫu (kiểu) mới → 新型.")
g.context("katachi", "Chọn câu viết đúng trong tình huống sau.", "Bạn tả chiếc bánh sinh nhật có hình trái tim.", [
    opt("a", "ハートの{形|かたち}のケーキを{作|つく}った。", "katachi"),
    opt("b", "ハートの{型|かた}のケーキを{作|つく}った。", "kata", "Tả hình dáng của chiếc bánh → 形. (型 là cái khuôn để làm bánh.)"),
    opt("c", "ハートの{方|かた}のケーキを{作|つく}った。", None, "方 là phía, cách; không phải hình dạng."),
], "a", "Hình dáng trái tim → 形.")

# 20 --------------------------------------------------------------------------------------------
g = group("kou-shin", "幸 và 辛",
          "Hai chữ chỉ khác một nét ngang: 幸 (HẠNH, hạnh phúc) có hai nét ngang ở trên (土 + 干 → nhiều nét hơn); 辛 (TÂN, cay, vất vả) có 立 ở trên. Nghĩa gần như ngược nhau.")
g.kanji("shiawase", "n3k-k05e78", "幸", "hạnh phúc, may mắn", ("干", "Can (phần dưới)"), "Phần trên là 土 rồi đến ソ và 干: thêm một nét so với 辛 → 'thêm một chút là hạnh phúc'. 幸せ, 幸福, 不幸.")
g.kanji("karai", "n3k-k08f9b", "辛", "cay; vất vả, khổ", ("辛", "Tân – chính là bộ thủ"), "Phần trên là 立: dao đâm, cay đắng → cay, khổ. 辛い (からい／つらい), 辛口.")
g.theory("Đếm nét ngang trên cùng", "幸: trên cùng là 土 (hai nét ngang, nét dưới dài hơn). 辛: trên cùng là 立 (chấm và nét ngang). Người ta hay nói 'cay đắng (辛) thêm một nét là hạnh phúc (幸)'.")
g.theory("Cách đọc của 辛い", "辛い có hai cách đọc: からい (cay) và つらい (khổ sở). Ngữ cảnh món ăn → からい; ngữ cảnh cảm xúc, công việc → つらい. 幸 đọc しあわ.せ, さいわ.い, コウ.")
g.compare("Phần trên", shiawase="土 (hai nét ngang)", karai="立")
g.compare("Nghĩa", shiawase="Hạnh phúc, may mắn", karai="Cay; vất vả")
g.compare("Từ tiêu biểu", shiawase="幸せ、幸福、不幸", karai="辛い、辛口")
g.example("shiawase", "{家族|かぞく}と{過|す}ごす{時間|じかん}が{一番|いちばん}{幸|しあわ}せだ。", "Thời gian bên gia đình là hạnh phúc nhất.")
g.example("shiawase", "{幸|さいわ}い、けが{人|にん}はいなかった。", "May mắn là không có ai bị thương.")
g.example("karai", "このカレーは{辛|から}すぎて{食|た}べられない。", "Món cà ri này cay quá, không ăn được.")
g.example("karai", "{友達|ともだち}と{別|わか}れるのは{辛|つら}い。", "Chia tay bạn bè thật khổ sở.")
g.tip("Thêm một nét (một chút cố gắng) vào cay đắng 辛 là thành hạnh phúc 幸.")
g.mistake("Viết 'hạnh phúc'.", "{二人|ふたり}は{辛|から}せに{暮|く}らした。", "{二人|ふたり}は{幸|しあわ}せに{暮|く}らした。", "Hạnh phúc → 幸せ.", "shiawase", "karai")
g.mistake("Viết 'cay'.", "{幸|さいわ}い{料理|りょうり}が{好|す}きだ。", "{辛|から}い{料理|りょうり}が{好|す}きだ。", "Cay → 辛い.", "karai", "shiawase")
g.mc("karai", "Chọn chữ đúng cho からい (cay).", [
    opt("a", "幸", "shiawase", "幸 là hạnh phúc."),
    opt("b", "辛", "karai"),
    opt("c", "空", None, "空 (から) là trống rỗng, không phải cay."),
], "b", "辛い (cay) viết với 辛.")
g.fill("shiawase", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "お{二人|ふたり}の＿＿＿せを{心|こころ}からお{祈|いの}りしています。",
       "Tôi thành tâm cầu chúc hai bạn hạnh phúc.", [
           opt("a", "幸", "shiawase"),
           opt("b", "辛", "karai"),
       ], "a", "幸せ (hạnh phúc) viết với 幸.")
g.error("karai", "Chọn phần viết sai chữ Kanji.", "Công việc vất vả nhưng tôi vẫn cố gắng.", [
    seg("{仕事|しごと}は"), seg("{幸|さいわ}いけれど、", "shiawase"), seg("{頑張|がんば}っています。"),
], 1, "{辛|つら}いけれど、", "Vất vả, khổ sở → 辛い (つらい).")
g.context("shiawase", "Chọn câu viết đúng trong tình huống sau.", "Bạn viết thiệp mừng cưới cho bạn thân.", [
    opt("a", "どうぞお{幸|しあわ}せに。", "shiawase"),
    opt("b", "どうぞお{辛|から}せに。", "karai", "辛 là cay, khổ; chúc mừng dùng 幸せ."),
    opt("c", "どうぞお{元気|げんき}せに。", None, "Không có cách nói này."),
], "a", "Chúc hạnh phúc → お幸せに.")

# 21 --------------------------------------------------------------------------------------------
g = group("on-dan-atatakai", "温 và 暖",
          "Cả hai đọc あたたかい nhưng dùng cho thứ khác nhau: 温 (bộ 氵 nước) cho vật, đồ ăn, nước, tấm lòng ấm (温かいスープ); 暖 (bộ 日 mặt trời) cho thời tiết, không khí, nhiệt độ môi trường (暖かい部屋).")
g.kanji("on", "n3k-k06e29", "温", "ấm (vật, nước, tấm lòng)", ("氵", "Thủy (nước)"), "氵 + 昷: nước ấm → hơi ấm của vật chạm vào được. 温かい, 温度, 温泉.")
g.kanji("dan", "n3k-k06696", "暖", "ấm (thời tiết, không khí)", ("日", "Nhật (mặt trời)"), "日 + 爰: nắng mặt trời sưởi ấm → không khí ấm. 暖かい, 暖房, 温暖化.")
g.theory("Vật hay môi trường", "Ấm của thứ chạm vào, ăn uống, hay tấm lòng (温かいご飯、温かい手、温かい言葉) → 温. Ấm của khí hậu, căn phòng, mùa (暖かい春、暖かい部屋、暖冬) → 暖. Đối nghĩa: 温かい ↔ 冷たい; 暖かい ↔ 寒い.")
g.theory("Từ Hán hay gặp", "温: 温度 (nhiệt độ), 体温 (thân nhiệt), 温泉 (suối nước nóng). 暖: 暖房 (máy sưởi), 暖冬 (đông ấm). 地球温暖化 (nóng lên toàn cầu) có cả hai chữ.")
g.compare("Bộ thủ", on="氵 (nước)", dan="日 (mặt trời)")
g.compare("Dùng cho", on="Đồ ăn, nước, vật, tấm lòng", dan="Thời tiết, không khí, phòng")
g.compare("Đối nghĩa", on="冷たい", dan="寒い")
g.example("on", "{寒|さむ}い{日|ひ}は{温|あたた}かいスープが{飲|の}みたくなる。", "Ngày lạnh lại thèm uống súp nóng ấm.")
g.example("on", "{先生|せんせい}の{温|あたた}かい{言葉|ことば}に{励|はげ}まされた。", "Tôi được khích lệ bởi lời nói ấm áp của thầy.")
g.example("dan", "{今日|きょう}は{春|はる}のように{暖|あたた}かい。", "Hôm nay ấm áp như mùa xuân.")
g.example("dan", "{寒|さむ}いので{暖房|だんぼう}をつけた。", "Lạnh quá nên tôi bật máy sưởi.")
g.tip("Có nước (氵) → thứ cầm, uống được 温; có mặt trời (日) → trời, phòng 暖.")
g.mistake("Viết 'mùa xuân ấm áp'.", "{温|あたた}かい{春|はる}が{来|き}た。", "{暖|あたた}かい{春|はる}が{来|き}た。", "Thời tiết, mùa → 暖かい.", "dan", "on")
g.mistake("Viết 'cơm nóng ấm'.", "{暖|あたた}かいご{飯|はん}を{食|た}べた。", "{温|あたた}かいご{飯|はん}を{食|た}べた。", "Đồ ăn → 温かい.", "on", "dan")
g.mc("dan", "Chọn câu viết đúng: 'Căn phòng này ấm áp.'", [
    opt("a", "この{部屋|へや}は{暖|あたた}かい。", "dan"),
    opt("b", "この{部屋|へや}は{温|あたた}かい。", "on", "Không khí trong phòng → 暖かい."),
    opt("c", "この{部屋|へや}は{熱|あつ}かい。", None, "Không có từ 熱かい."),
], "a", "Không khí căn phòng → 暖かい.")
g.fill("on", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{熱|ねつ}があるかどうか、{体|たい}＿＿＿を{測|はか}ってみよう。",
       "Thử đo thân nhiệt xem có sốt không.", [
           opt("a", "温", "on"),
           opt("b", "暖", "dan"),
       ], "a", "体温 (thân nhiệt) viết với 温.")
g.error("dan", "Chọn phần viết sai chữ Kanji.", "Lạnh quá, bật máy sưởi lên đi.", [
    seg("{寒|さむ}いから、"), seg("{温房|おんぼう}を", "on"), seg("つけて。"),
], 1, "{暖房|だんぼう}を", "Máy sưởi (làm ấm không khí) → 暖房.")
g.context("on", "Chọn câu viết đúng trong tình huống sau.", "Bạn cảm ơn gia đình chủ nhà đã đón tiếp bạn chu đáo, ấm áp.", [
    opt("a", "{温|あたた}かく{迎|むか}えてくださって、ありがとうございました。", "on"),
    opt("b", "{暖|あたた}かく{迎|むか}えてくださって、ありがとうございました。", "dan", "Sự ấm áp của tấm lòng thường viết 温かく."),
    opt("c", "{暑|あつ}く{迎|むか}えてくださって、ありがとうございました。", None, "暑い là nóng (thời tiết), sai nghĩa."),
], "a", "Tấm lòng ấm áp → 温かく.")

# 22 --------------------------------------------------------------------------------------------
g = group("kasu-kariru", "貸 và 借",
          "貸 là cho mượn, cho vay (đồ đi từ mình sang người khác); 借 là mượn, vay (đồ từ người khác về mình). Hai chữ khác hẳn hình dạng nhưng hay bị viết nhầm vì luôn đi đôi.")
g.kanji("kasu", "n3k-k08cb8", "貸", "cho mượn, cho vay", ("貝", "Bối (tiền) ở dưới"), "代 (thay) trên 貝 (tiền): đưa tiền cho người khác dùng thay → cho vay. 貸す, 貸し出し, 賃貸.")
g.kanji("kariru", "n3k-k0501f", "借", "mượn, vay", ("亻", "Nhân đứng (người)"), "亻 + 昔: người đi xin dùng tạm của người khác → mượn. 借りる, 借金.")
g.theory("Hướng của đồ vật", "Đồ đi ra khỏi tay mình (A が B に貸す: A cho B mượn) → 貸. Đồ đến tay mình (B が A に／から借りる: B mượn của A) → 借. Tiền nợ của mình là 借金.")
g.theory("Nhớ bằng bộ thủ", "貸 có 貝 (tiền) ở dưới: người có tiền nên cho vay. 借 có 亻 (người) bên trái: người đi mượn. Âm: 貸 か.す, タイ; 借 か.りる, シャク.")
g.compare("Hướng", kasu="Từ mình → người khác", kariru="Từ người khác → mình")
g.compare("Bộ thủ", kasu="貝 (tiền) ở dưới", kariru="亻 (người) bên trái")
g.compare("Từ tiêu biểu", kasu="貸す、貸し出し、賃貸", kariru="借りる、借金")
g.example("kasu", "{友達|ともだち}に{傘|かさ}を{貸|か}した。", "Tôi cho bạn mượn ô.")
g.example("kasu", "この{図書館|としょかん}は{本|ほん}を{二週間|にしゅうかん}{貸|か}し{出|だ}している。", "Thư viện này cho mượn sách hai tuần.")
g.example("kariru", "{先輩|せんぱい}にノートを{借|か}りた。", "Tôi mượn vở của đàn anh.")
g.example("kariru", "{家|いえ}を{買|か}うために{銀行|ぎんこう}からお{金|かね}を{借|か}りた。", "Tôi vay tiền ngân hàng để mua nhà.")
g.tip("Có tiền (貝) thì cho vay 貸; là người (亻) đi xin thì mượn 借.")
g.mistake("Viết 'mượn sách ở thư viện'.", "{図書館|としょかん}で{本|ほん}を{貸|か}りた。", "{図書館|としょかん}で{本|ほん}を{借|か}りた。", "Mình mượn (đồ đến tay mình) → 借りる.", "kariru", "kasu")
g.mistake("Viết 'tiền nợ'.", "{貸金|かしきん}を{返|かえ}すために{働|はたら}いている。", "{借金|しゃっきん}を{返|かえ}すために{働|はたら}いている。", "Khoản mình vay phải trả → 借金.", "kariru", "kasu")
g.mc("kasu", "Chọn câu viết đúng: 'Anh có thể cho tôi mượn bút được không?'", [
    opt("a", "ペンを{貸|か}してもらえませんか。", "kasu"),
    opt("b", "ペンを{借|か}してもらえませんか。", "kariru", "借 đi với 借りる; 'cho mượn' là 貸す."),
    opt("c", "ペンを{返|かえ}してもらえませんか。", None, "返す là trả lại."),
], "a", "Nhờ người khác cho mình mượn → 貸してもらう.")
g.fill("kariru", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{財布|さいふ}を{忘|わす}れたので、{友達|ともだち}にお{金|かね}を＿＿＿りた。",
       "Quên ví nên tôi mượn tiền bạn.", [
           opt("a", "借", "kariru"),
           opt("b", "貸", "kasu"),
       ], "a", "借りる (mượn) viết với 借.")
g.error("kasu", "Chọn phần viết sai chữ Kanji.", "Tôi đã cho em gái mượn máy ảnh.", [
    seg("{妹|いもうと}に"), seg("カメラを"), seg("{借|か}した。", "kariru"),
], 2, "{貸|か}した。", "Cho mượn → 貸す.")
g.context("kariru", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng mình thuê (mượn có trả tiền) một căn hộ gần trường.", [
    opt("a", "{学校|がっこう}の{近|ちか}くにアパートを{借|か}りた。", "kariru"),
    opt("b", "{学校|がっこう}の{近|ちか}くにアパートを{貸|か}りた。", "kasu", "Mượn, thuê về mình → 借りる."),
    opt("c", "{学校|がっこう}の{近|ちか}くにアパートを{貸|か}した。", None, "貸した là cho người khác thuê, trái tình huống."),
], "a", "Mình thuê căn hộ → 借りる.")

# 23 --------------------------------------------------------------------------------------------
g = group("tori-shima", "鳥 và 島",
          "鳥 (ĐIỂU, chim) và 島 (ĐẢO, đảo) trông rất giống nhau: 島 là 鳥 bỏ bốn chấm ở dưới và thay bằng 山 (núi) – hình con chim đậu trên ngọn núi giữa biển.")
g.kanji("tori", "n3k-k09ce5", "鳥", "chim", ("鳥", "Điểu – chính là bộ thủ"), "Hình con chim, bốn chấm 灬 ở dưới là chân và đuôi. 鳥, 小鳥, 焼き鳥.")
g.kanji("shima", "n3k-k05cf6", "島", "đảo", ("山", "Sơn (núi)"), "鳥 bỏ bốn chấm, thêm 山 ở dưới: chim đậu trên núi giữa biển → đảo. 島, 半島, 島国.")
g.theory("Bốn chấm hay núi", "Phần dưới có bốn chấm (灬) → 鳥 (chim). Phần dưới có 山 (núi) → 島 (đảo). Âm: 鳥 とり, チョウ; 島 しま, トウ.")
g.theory("Từ hay gặp", "鳥: 鳥 (chim), 小鳥 (chim nhỏ), 焼き鳥 (gà nướng xiên). 島: 島 (đảo), 半島 (bán đảo), 島国 (quốc đảo), 無人島 (đảo hoang).")
g.compare("Phần dưới", tori="Bốn chấm 灬", shima="山 (núi)")
g.compare("Nghĩa", tori="Chim", shima="Đảo")
g.compare("Từ tiêu biểu", tori="小鳥、焼き鳥", shima="半島、島国、無人島")
g.example("tori", "{朝|あさ}、{鳥|とり}の{声|こえ}で{目|め}が{覚|さ}めた。", "Sáng nay tôi thức giấc vì tiếng chim.")
g.example("tori", "{仕事|しごと}の{後|あと}、{焼|や}き{鳥|とり}を{食|た}べに{行|い}った。", "Sau giờ làm tôi đi ăn gà nướng xiên.")
g.example("shima", "{日本|にほん}は{島国|しまぐに}だ。", "Nhật Bản là một quốc đảo.")
g.example("shima", "{船|ふね}で{小|ちい}さな{島|しま}に{渡|わた}った。", "Tôi đi thuyền sang một hòn đảo nhỏ.")
g.tip("Chim (鳥) có chân (bốn chấm); đảo (島) có núi (山).")
g.mistake("Viết 'quốc đảo'.", "{日本|にほん}は{鳥国|とりくに}です。", "{日本|にほん}は{島国|しまぐに}です。", "Đảo → 島 (có 山).", "shima", "tori")
g.mistake("Viết 'chim nhỏ'.", "{小島|こじま}が{窓|まど}の{外|そと}で{鳴|な}いている。", "{小鳥|ことり}が{窓|まど}の{外|そと}で{鳴|な}いている。", "Chim → 鳥 (bốn chấm). 小島 là đảo nhỏ.", "tori", "shima")
g.mc("shima", "Chọn chữ có nghĩa 'đảo'.", [
    opt("a", "鳥", "tori", "鳥 là chim."),
    opt("b", "島", "shima"),
    opt("c", "烏", None, "烏 (からす) là con quạ."),
], "b", "島 (phần dưới là 山) là đảo.")
g.fill("tori", "Chọn chữ Kanji đúng để điền vào chỗ trống.", "{公園|こうえん}の{池|いけ}にたくさんの{水|みず}＿＿＿が{集|あつ}まっている。",
       "Có rất nhiều chim nước tụ tập ở hồ công viên.", [
           opt("a", "鳥", "tori"),
           opt("b", "島", "shima"),
       ], "a", "水鳥 (chim nước) viết với 鳥.")
g.error("shima", "Chọn phần viết sai chữ Kanji.", "Kỳ nghỉ hè tôi đi du lịch bán đảo Izu.", [
    seg("{夏休|なつやす}みに"), seg("{伊豆半鳥|いずはんちょう}へ", "tori"), seg("{旅行|りょこう}した。"),
], 1, "{伊豆半島|いずはんとう}へ", "Bán đảo → 半島 (có 山).")
g.context("tori", "Chọn câu viết đúng trong tình huống sau.", "Bạn kể rằng trong vườn có một con chim nhỏ làm tổ.", [
    opt("a", "{庭|にわ}の{木|き}に{小鳥|ことり}が{巣|す}を{作|つく}った。", "tori"),
    opt("b", "{庭|にわ}の{木|き}に{小島|こじま}が{巣|す}を{作|つく}った。", "shima", "小島 là đảo nhỏ; chim viết 鳥."),
    opt("c", "{庭|にわ}の{木|き}に{小烏|こがらす}が{巣|す}を{作|つく}った。", None, "烏 là con quạ, khác với 'chim nhỏ' trong tình huống."),
], "a", "Chim nhỏ → 小鳥.")
