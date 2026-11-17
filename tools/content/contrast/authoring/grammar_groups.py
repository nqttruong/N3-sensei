"""Grammar contrast groups (n3-grammar). AI-written drafts: every group needs a real reviewer."""
from dsl import Group, opt, seg

GROUPS = []


def group(*args):
    g = Group("GRAMMAR", *args)
    GROUPS.append(g)
    return g


# 1 ---------------------------------------------------------------------------------------------
g = group("koto-ni-shiteiru-naru", "〜ことにしている và 〜ことになっている",
          "Cả hai nói về việc được làm đều đặn theo một quyết định. Khác nhau ở người quyết định: chính người nói (ことにしている) hay người khác, tổ chức, quy định (ことになっている).")
g.grammar("shiteiru", "n3g-g001", "〜ことにしている", "Tôi (tự mình) có thói quen / nguyên tắc là…", "Vる／Vない ＋ ことにしている",
          "Quyết định do chính người nói đặt ra và đang duy trì. Chủ ngữ thường là 私 (hoặc ẩn). Nhấn mạnh ý chí cá nhân.")
g.grammar("natteiru", "n3g-g002", "〜ことになっている", "Theo quy định / theo kế hoạch đã định thì…", "Vる／Vない ＋ ことになっている",
          "Quyết định đến từ bên ngoài người nói: luật, nội quy, công ty, lịch đã chốt. Người nói chỉ truyền đạt lại. Hay đi với 〜では、規則で、法律で.")
g.theory("Khác nhau ở người ra quyết định",
         "する là chủ động làm: 〜ことにする là tự quyết định, 〜ことにしている là đã tự quyết và vẫn đang giữ. なる là tự trở thành: 〜ことになる là việc được quyết định (không do mình), 〜ことになっている là quyết định đó đang có hiệu lực như một quy định.")
g.theory("Khi nào không dùng",
         "Không dùng ことにしている cho luật lệ, nội quy chung (nghe như người nói tự đặt ra luật). Không dùng ことになっている cho thói quen riêng chỉ mình biết (nghe như có ai bắt buộc).")
g.theory("Nhận biết nhanh trong đề thi",
         "Có 私は、健康のために、自分で… → ことにしている. Có 規則で、法律で、この会社では、予定では… → ことになっている.")
g.compare("Ai quyết định", shiteiru="Chính người nói", natteiru="Người khác, tổ chức, luật lệ")
g.compare("Từ hay đi kèm", shiteiru="私は、毎日、〜ために (mục đích cá nhân)", natteiru="〜では、規則で、法律で、予定では")
g.compare("Sắc thái", shiteiru="Thói quen, nguyên tắc cá nhân", natteiru="Quy định, lịch đã chốt, truyền đạt khách quan")
g.example("shiteiru", "{私|わたし}は{寝|ね}る{前|まえ}に{日記|にっき}を{書|か}くことにしている。", "Tôi có thói quen viết nhật ký trước khi ngủ (tự mình đặt ra).")
g.example("shiteiru", "{甘|あま}い{物|もの}は{週|しゅう}に{一回|いっかい}だけ{食|た}べることにしている。", "Tôi tự đặt nguyên tắc chỉ ăn đồ ngọt mỗi tuần một lần.")
g.example("natteiru", "この{会社|かいしゃ}では、{毎週|まいしゅう}{金曜日|きんようび}に{会議|かいぎ}をすることになっている。", "Ở công ty này, theo quy định thứ Sáu hằng tuần sẽ họp.")
g.example("natteiru", "{図書館|としょかん}の{本|ほん}は{二週間|にしゅうかん}{以内|いない}に{返|かえ}すことになっている。", "Theo quy định, sách thư viện phải trả trong vòng hai tuần.")
g.tip("する = mình làm, nên にしている là mình đặt ra. なる = tự thành, nên になっている là đã được định sẵn cho mình.")
g.mistake("Muốn nói thói quen do chính mình đặt ra.", "{私|わたし}は{健康|けんこう}のために、{毎朝|まいあさ}{走|はし}ることになっている。",
          "{私|わたし}は{健康|けんこう}のために、{毎朝|まいあさ}{走|はし}ることにしている。",
          "健康のために là mục đích cá nhân, do mình tự quyết, nên dùng ことにしている. Dùng ことになっている nghe như có ai bắt mình chạy.", "shiteiru", "natteiru")
g.mistake("Nói về nội quy của trường.", "{学校|がっこう}の{規則|きそく}で、{制服|せいふく}を{着|き}ることにしている。",
          "{学校|がっこう}の{規則|きそく}で、{制服|せいふく}を{着|き}ることになっている。",
          "規則で (theo nội quy) cho biết người quyết định là nhà trường, nên dùng ことになっている.", "natteiru", "shiteiru")
g.mc("natteiru", "Câu nào diễn tả một quy định do nơi ở đặt ra (không phải do người nói tự quyết)?", [
    opt("a", "{毎晩|まいばん}、{十時|じゅうじ}に{寝|ね}ることにしている。", "shiteiru", "Đây là thói quen người nói tự đặt ra."),
    opt("b", "この{寮|りょう}では、{十時|じゅうじ}に{寝|ね}ることになっている。", "natteiru"),
    opt("c", "{毎晩|まいばん}、{十時|じゅうじ}に{寝|ね}ることにした。", None, "ことにした là vừa tự quyết định (một lần), không phải quy định."),
], "b", "この寮では (ở ký túc xá này) cho biết quy định do nơi ở đặt ra, nên dùng ことになっている.")
g.fill("shiteiru", "Chọn cách nói phù hợp để điền vào chỗ trống.", "{健康|けんこう}のために、{私|わたし}は{毎朝|まいあさ}{三十分|さんじゅっぷん}{歩|ある}く＿＿＿。",
       "Vì sức khỏe, mỗi sáng tôi (tự đặt ra) đi bộ ba mươi phút.", [
           opt("a", "ことにしている", "shiteiru"),
           opt("b", "ことになっている", "natteiru", "Không có quy định nào ở đây; người nói tự duy trì thói quen."),
       ], "a", "Người nói diễn đạt thói quen do bản thân quyết định duy trì vì sức khỏe, nên dùng ことにしている. Muốn dùng ことになっている phải có ngữ cảnh về quy định hay sự sắp xếp từ bên ngoài.")
g.fill("natteiru", "Chọn cách nói phù hợp để điền vào chỗ trống.", "{日本|にほん}では、{車|くるま}は{道|みち}の{左側|ひだりがわ}を{走|はし}る＿＿＿。",
       "Ở Nhật, (theo luật) xe chạy bên trái đường.", [
           opt("a", "ことにしている", "shiteiru"),
           opt("b", "ことになっている", "natteiru"),
       ], "b", "Luật giao thông do nhà nước đặt ra, không phải do người nói quyết định, nên dùng ことになっている.")
g.error("shiteiru", "Người nói tự đặt ra thói quen này. Chọn phần sai trong câu.", "Vì sức khỏe, tôi (tự đặt ra) mỗi ngày ăn rau.", [
    seg("{私|わたし}は"), seg("{健康|けんこう}のために、"), seg("{毎日|まいにち}{野菜|やさい}を{食|た}べる"), seg("ことになっている。", "natteiru"),
], 3, "ことにしている。", "Thói quen do người nói tự đặt ra (健康のために) phải dùng ことにしている. ことになっている nghĩa là có người khác quy định.")
g.context("natteiru", "Chọn câu tự nhiên nhất trong tình huống sau.", "Bạn giải thích cho đồng nghiệp mới rằng ở công ty, nhân viên phải nộp báo cáo vào thứ Sáu hằng tuần.", [
    opt("a", "{毎週|まいしゅう}{金曜日|きんようび}に{報告書|ほうこくしょ}を{出|だ}すことになっています。", "natteiru"),
    opt("b", "{毎週|まいしゅう}{金曜日|きんようび}に{報告書|ほうこくしょ}を{出|だ}すことにしています。", "shiteiru", "Câu này nghe như thói quen riêng của bạn, không phải quy định của công ty."),
    opt("c", "{毎週|まいしゅう}{金曜日|きんようび}に{報告書|ほうこくしょ}を{出|だ}すことにしました。", None, "ことにしました là bạn vừa tự quyết định."),
], "a", "Quy định của công ty là quyết định từ bên ngoài người nói, nên dùng ことになっています.")

# 2 ---------------------------------------------------------------------------------------------
g = group("okage-sei", "〜おかげで và 〜せいで",
          "Cả hai nêu nguyên nhân dẫn đến một kết quả. おかげで dùng khi kết quả tốt (biết ơn), せいで dùng khi kết quả xấu (đổ lỗi).")
g.grammar("okage", "n3g-g018", "〜おかげで", "Nhờ…, nhờ có… (kết quả tốt)", "N の／Vthường・Aい・Aな ＋ おかげで／おかげだ",
          "Người nói thấy biết ơn nguyên nhân. Dùng với người giúp đỡ, sự việc thuận lợi. Dùng mỉa mai cho kết quả xấu thì chỉ là cách nói châm biếm.")
g.grammar("sei", "n3g-g019", "〜せいで", "Tại…, do… (kết quả xấu)", "N の／Vthường・Aい・Aな ＋ せいで／せいだ",
          "Người nói đổ lỗi, khó chịu với nguyên nhân. Kết quả là điều không mong muốn: trễ, ốm, thất bại.")
g.theory("Phân biệt bằng kết quả", "Hãy nhìn vế sau: kết quả tốt (合格した、元気になった、間に合った) → おかげで. Kết quả xấu (遅れた、失敗した、眠れなかった) → せいで. Nối giống nhau: N の, động từ và tính từ ở thể thường.")
g.theory("Khi nào không dùng", "Không dùng せいで để cảm ơn ai. Không dùng おかげで cho kết quả xấu nếu không có ý mỉa mai. Khi chỉ muốn nêu nguyên nhân khách quan (báo chí, thông báo) thì dùng ため(に), không mang cảm xúc.")
g.compare("Kết quả", okage="Tốt, mong muốn", sei="Xấu, không mong muốn")
g.compare("Cảm xúc của người nói", okage="Biết ơn, vui", sei="Trách móc, bực bội")
g.compare("Câu cảm ơn hay đi kèm", okage="〜のおかげです。ありがとうございます。", sei="(không dùng để cảm ơn)")
g.example("okage", "{先生|せんせい}のおかげで、{試験|しけん}に{合格|ごうかく}できました。", "Nhờ thầy mà em đã thi đỗ.")
g.example("okage", "{薬|くすり}を{飲|の}んだおかげで、{熱|ねつ}が{下|さ}がった。", "Nhờ uống thuốc mà hạ sốt.")
g.example("sei", "{雨|あめ}のせいで、{試合|しあい}が{中止|ちゅうし}になった。", "Tại mưa mà trận đấu bị hủy.")
g.example("sei", "{夜|よる}コーヒーを{飲|の}んだせいで、{眠|ねむ}れなかった。", "Tại uống cà phê buổi tối nên tôi không ngủ được.")
g.tip("おかげ có gốc từ 陰 (bóng mát che chở): được che chở thì biết ơn. せい gợi tới việc 'đổ lỗi cho ai đó'.")
g.mistake("Muốn cảm ơn bạn đã giúp mình làm xong việc.", "{田中|たなか}さんのせいで、{早|はや}く{終|お}わりました。",
          "{田中|たなか}さんのおかげで、{早|はや}く{終|お}わりました。", "Làm xong sớm là kết quả tốt và người nói biết ơn, nên dùng おかげで. せいで nghe như trách anh Tanaka.", "okage", "sei")
g.mistake("Bị trễ tàu vì tắc đường.", "{渋滞|じゅうたい}のおかげで、{電車|でんしゃ}に{乗|の}り{遅|おく}れた。",
          "{渋滞|じゅうたい}のせいで、{電車|でんしゃ}に{乗|の}り{遅|おく}れた。", "Lỡ tàu là kết quả xấu; dùng おかげで ở đây chỉ đúng khi cố ý mỉa mai.", "sei", "okage")
g.mc("sei", "Chọn câu dùng đúng ngữ pháp và tự nhiên.", [
    opt("a", "{風邪|かぜ}のせいで、{旅行|りょこう}に{行|い}けなかった。", "sei"),
    opt("b", "{風邪|かぜ}のおかげで、{旅行|りょこう}に{行|い}けなかった。", "okage", "Không đi du lịch được là kết quả xấu, おかげで nghe như biết ơn cơn cảm."),
    opt("c", "{風邪|かぜ}のせいで、{旅行|りょこう}を{楽|たの}しめた。", None, "せいで đi với kết quả xấu, còn 'tận hưởng chuyến đi' là kết quả tốt."),
], "a", "Bị cảm nên không đi du lịch được: kết quả xấu, đổ lỗi cho cơn cảm → せいで.")
g.fill("okage", "Chọn từ phù hợp để điền vào chỗ trống.", "{友達|ともだち}が{手伝|てつだ}ってくれた＿＿＿、{引|ひ}っ{越|こ}しが{一日|いちにち}で{終|お}わった。",
       "Nhờ bạn giúp mà việc chuyển nhà xong trong một ngày.", [
           opt("a", "おかげで", "okage"),
           opt("b", "せいで", "sei", "Việc xong trong một ngày là kết quả tốt; せいで dùng khi đổ lỗi."),
       ], "a", "Bạn giúp đỡ và kết quả tốt (xong trong một ngày), người nói biết ơn → おかげで.")
g.error("sei", "Chọn phần sai trong câu.", "Tại cái đồng hồ báo thức hỏng mà sáng nay tôi đi học muộn.", [
    seg("{目覚|めざ}まし{時計|どけい}が{壊|こわ}れていた"), seg("おかげで、", "okage"), seg("{今朝|けさ}は"), seg("{学校|がっこう}に{遅刻|ちこく}した。"),
], 1, "せいで、", "Đi học muộn là kết quả xấu và người nói đổ lỗi cho đồng hồ, nên phải dùng せいで.")
g.context("okage", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn vừa thi đỗ JLPT N3 và muốn cảm ơn cô giáo đã luyện thi cho bạn.", [
    opt("a", "{先生|せんせい}のおかげで{合格|ごうかく}できました。ありがとうございました。", "okage"),
    opt("b", "{先生|せんせい}のせいで{合格|ごうかく}できました。ありがとうございました。", "sei", "せいで mang ý trách móc, không dùng khi cảm ơn."),
    opt("c", "{先生|せんせい}のために{合格|ごうかく}できました。ありがとうございました。", None, "のために ở đây nghe như 'đỗ vì cô (mục đích)', không diễn tả nhờ cô giúp."),
], "a", "Kết quả tốt nhờ cô giáo và người nói biết ơn → おかげで.")

# 3 ---------------------------------------------------------------------------------------------
g = group("souda-denbun-youtai", "〜そうだ (nghe nói) và 〜そうだ (trông có vẻ)",
          "Hai mẫu cùng viết là そうだ nhưng nghĩa và cách nối khác hẳn: そうだ truyền đạt (nghe nói) nối với thể thường, そうだ dáng vẻ (trông có vẻ) nối với thân động từ ます và tính từ bỏ い／な.")
g.grammar("denbun", "n3g-g118", "〜そうだ（伝聞）", "Nghe nói là…, nghe đồn rằng…", "V thể thường／Aい／Aな だ／N だ ＋ そうだ",
          "Truyền lại thông tin nghe hoặc đọc được từ người khác, tin tức. Hay đi với 〜によると、〜では. Không có dạng phủ định そうではない cho nghĩa này.")
g.grammar("youtai", "n3g-g119", "〜そうだ（様態）", "Trông có vẻ…, sắp…", "Vます ＋ そうだ／Aい（bỏ い）＋ そうだ／Aな ＋ そうだ（いい→よさそう、ない→なさそう）",
          "Phán đoán từ những gì chính mắt thấy, hoặc dấu hiệu sắp xảy ra (雨が降りそう). Có thể đi trước danh từ: おいしそうなケーキ.")
g.theory("Nhìn cách nối là biết nghĩa", "降るそうだ (thể thường) = nghe nói sẽ mưa. 降りそうだ (thân ます) = trông như sắp mưa. 高いそうだ = nghe nói đắt; 高そうだ = trông có vẻ đắt. 元気だそうだ = nghe nói khỏe; 元気そうだ = trông khỏe.")
g.theory("Nguồn thông tin", "伝聞: thông tin đến từ người khác (天気予報によると、ニュースで、友達の話では). 様態: thông tin đến từ mắt mình (見ると、顔を見て). Trong đề, cụm 〜によると gần như luôn đi với そうだ truyền đạt.")
g.compare("Nghĩa", denbun="Nghe nói, nghe đồn", youtai="Trông có vẻ, sắp")
g.compare("Cách nối với 降る／高い／元気", denbun="降るそうだ／高いそうだ／元気だそうだ", youtai="降りそうだ／高そうだ／元気そうだ")
g.compare("Đứng trước danh từ", denbun="Không (không nói ×降るそうな雨)", youtai="Được: おいしそうなパン")
g.example("denbun", "{天気予報|てんきよほう}によると、{明日|あした}は{雪|ゆき}が{降|ふ}るそうだ。", "Theo dự báo thời tiết, nghe nói ngày mai có tuyết.")
g.example("denbun", "{田中|たなか}さんは{来月|らいげつ}{結婚|けっこん}するそうです。", "Nghe nói tháng sau anh Tanaka sẽ kết hôn.")
g.example("youtai", "{空|そら}が{暗|くら}くなってきた。{雨|あめ}が{降|ふ}りそうだ。", "Trời tối dần rồi. Trông như sắp mưa.")
g.example("youtai", "このケーキはとてもおいしそうですね。", "Cái bánh này trông ngon quá nhỉ.")
g.tip("Nghe (耳) thì giữ nguyên câu rồi thêm そうだ; thấy (目) thì cắt đuôi rồi thêm そうだ.")
g.mistake("Bạn đọc tin tức rằng giá xăng sẽ tăng.", "ニュースによると、ガソリンが{高|たか}そうだ。",
          "ニュースによると、ガソリンが{高|たか}くなるそうだ。", "ニュースによると cho biết thông tin nghe được, nên phải dùng そうだ truyền đạt nối với thể thường.", "denbun", "youtai")
g.mistake("Nhìn chiếc túi và đoán là đắt.", "このかばんは{高|たか}いそうですね。",
          "このかばんは{高|たか}そうですね。", "Đoán từ những gì nhìn thấy dùng そうだ dáng vẻ: 高い → 高そう.", "youtai", "denbun")
g.mc("denbun", "Câu nào có nghĩa \"Nghe nói cửa hàng kia rất ngon\"?", [
    opt("a", "あの{店|みせ}はおいしいそうだ。", "denbun"),
    opt("b", "あの{店|みせ}はおいしそうだ。", "youtai", "おいしそう là 'trông có vẻ ngon' (nhìn thấy), không phải nghe nói."),
    opt("c", "あの{店|みせ}はおいしいようにする。", None, "ようにする là cố gắng làm sao cho…, không hợp nghĩa."),
], "a", "Nghe nói → thể thường おいしい + そうだ.")
g.fill("youtai", "Chọn dạng đúng để điền vào chỗ trống.", "あ、{棚|たな}の{上|うえ}の{箱|はこ}が{落|お}ち＿＿＿だ。{気|き}をつけて！",
       "A, cái hộp trên kệ trông như sắp rơi. Cẩn thận!", [
           opt("a", "そう", "youtai"),
           opt("b", "るそう", "denbun", "落ちるそうだ là 'nghe nói sẽ rơi', không hợp khi đang nhìn thấy cái hộp."),
       ], "a", "Nhìn thấy dấu hiệu sắp rơi → thân ます 落ち + そうだ.")
g.error("denbun", "Chọn phần sai trong câu.", "Theo lời bạn tôi, nghe nói bộ phim đó rất thú vị.", [
    seg("{友達|ともだち}の{話|はなし}によると、"), seg("その{映画|えいが}は"), seg("とても"), seg("{面白|おもしろ}そうだ。", "youtai"),
], 3, "{面白|おもしろ}いそうだ。", "によると cho biết thông tin nghe từ bạn, nên dùng そうだ truyền đạt: 面白い + そうだ.")
g.context("youtai", "Chọn câu tự nhiên nhất trong tình huống sau.", "Bạn nhìn thấy đồng nghiệp ngáp liên tục, mắt díp lại, và muốn hỏi thăm.", [
    opt("a", "{大丈夫|だいじょうぶ}ですか。とても{眠|ねむ}いそうですね。", "denbun", "眠いそうですね là 'nghe nói anh buồn ngủ', như đang kể lại lời người khác."),
    opt("b", "{大丈夫|だいじょうぶ}ですか。とても{眠|ねむ}いらしいですね。", None, "らしい dựa vào thông tin nghe được; nói về người đang đứng trước mặt thì nghe xa cách, không tự nhiên."),
    opt("c", "{大丈夫|だいじょうぶ}ですか。とても{眠|ねむ}そうですね。", "youtai"),
], "c", "Đoán từ những gì nhìn thấy trên mặt đồng nghiệp → そうだ dáng vẻ: 眠い → 眠そう.")

# 4 ---------------------------------------------------------------------------------------------
g = group("mitaida-rashii-youda", "〜みたいだ, 〜らしい và 〜ようだ",
          "Ba cách nói 'hình như, có vẻ'. ようだ là cách nói trang trọng, みたいだ là văn nói; らしい thường dựa vào thông tin nghe được và còn có nghĩa 'đúng chất…'. Cách nối với danh từ cũng khác nhau.")
g.grammar("mitai", "n3g-g005", "〜みたいだ", "Hình như…, giống như… (văn nói)", "N／V thể thường／Aい／Aな（bỏ な） ＋ みたいだ",
          "Phán đoán dựa trên điều chính mình thấy, nghe, cảm nhận; hoặc so sánh 'giống như'. Thân mật, dùng trong hội thoại. Nối thẳng với danh từ: 子供みたい.")
g.grammar("rashii", "n3g-g006", "〜らしい", "Nghe nói…, có vẻ…; đúng chất…", "N／V thể thường／Aい／Aな（bỏ な） ＋ らしい ／ N ＋ らしい（đúng chất N）",
          "Phán đoán dựa trên thông tin gián tiếp (nghe nói, đọc được), người nói giữ khoảng cách. N + らしい còn nghĩa 'ra dáng, đúng chất N': 男らしい、春らしい.")
g.grammar("youda", "n3g-g121", "〜ようだ", "Có vẻ như…, hình như… (suy đoán)", "V thể thường／Aい ＋ ようだ；Aな ＋ な ＋ ようだ；N ＋ の ＋ ようだ",
          "Phán đoán dựa trên quan sát và cảm nhận của chính người nói; trang trọng hơn みたいだ, dùng trong văn viết và lời nói lịch sự.")
g.theory("Cách nối với danh từ và tính từ đuôi な", "Danh từ: 子供みたいだ ／ 子供らしい ／ 子供のようだ. Tính từ な: 静かみたいだ ／ 静からしい ／ 静かなようだ. Sai cách nối là lỗi hay ra trong phần phát hiện lỗi.")
g.theory("Nguồn phán đoán và mức trang trọng", "ようだ, みたいだ: người nói tự quan sát (ようだ trang trọng, みたいだ thân mật). らしい: thường dựa vào lời người khác, tin tức, người nói không chịu trách nhiệm về thông tin. N らしい (đúng chất) thì hai mẫu kia không thay được.")
g.compare("Nối với danh từ", mitai="N ＋ みたい", rashii="N ＋ らしい", youda="N ＋ の ＋ よう")
g.compare("Mức trang trọng", mitai="Văn nói, thân mật", rashii="Trung tính", youda="Trang trọng, văn viết")
g.compare("Nghĩa riêng", mitai="So sánh 'giống như' (thân mật)", rashii="'Đúng chất, ra dáng' (男らしい)", youda="So sánh 'giống như' (trang trọng, văn chương)")
g.example("mitai", "{隣|となり}の{部屋|へや}、だれもいないみたいだよ。", "Phòng bên cạnh hình như không có ai đâu.")
g.example("mitai", "あの{二人|ふたり}は{本当|ほんとう}の{兄弟|きょうだい}みたいだ。", "Hai người đó trông như anh em ruột vậy.")
g.example("rashii", "ニュースによると、{犯人|はんにん}はまだ{見|み}つかっていないらしい。", "Theo tin tức, có vẻ thủ phạm vẫn chưa bị tìm thấy.")
g.example("rashii", "{今日|きょう}は{暖|あたた}かくて、{春|はる}らしい{天気|てんき}だ。", "Hôm nay ấm áp, đúng là thời tiết mùa xuân.")
g.example("youda", "{道|みち}が{濡|ぬ}れている。{夜|よる}の{間|あいだ}に{雨|あめ}が{降|ふ}ったようだ。", "Đường ướt. Có vẻ trong đêm trời đã mưa.")
g.example("youda", "{部長|ぶちょう}は{今日|きょう}、{少|すこ}しお{疲|つか}れのようです。", "Hôm nay trưởng phòng có vẻ hơi mệt.")
g.tip("みたい nối thẳng như nói chuyện; よう cần の／な như văn viết; らしい = 'nghe đồn' hoặc 'ra dáng'.")
g.mistake("Nối ようだ với danh từ.", "{外|そと}はまるで{冬|ふゆ}ようだ。", "{外|そと}はまるで{冬|ふゆ}のようだ。", "Danh từ + ようだ cần の: 冬のようだ. Không có の thì chỉ đúng với みたいだ: 冬みたいだ.", "youda", "mitai")
g.mistake("Khen một người đàn ông mạnh mẽ, ra dáng đàn ông.", "{彼|かれ}は{本当|ほんとう}に{男|おとこ}みたいな{人|ひと}だ。", "{彼|かれ}は{本当|ほんとう}に{男|おとこ}らしい{人|ひと}だ。",
          "Ý 'ra dáng, đúng chất đàn ông' chỉ có ở N らしい. 男みたいな nghe như người đó không phải đàn ông mà chỉ giống đàn ông.", "rashii", "mitai")
g.mc("rashii", "Câu nào có nghĩa 'Cô ấy rất nữ tính (đúng chất phụ nữ)'?", [
    opt("a", "{彼女|かのじょ}はとても{女|おんな}らしい。", "rashii"),
    opt("b", "{彼女|かのじょ}はとても{女|おんな}みたいだ。", "mitai", "女みたいだ nghĩa là 'giống phụ nữ', dùng cho người không phải phụ nữ."),
    opt("c", "{彼女|かのじょ}はとても{女|おんな}のようだ。", "youda", "女のようだ cũng là so sánh 'như là phụ nữ', không có nghĩa 'đúng chất'."),
], "a", "N + らしい mang nghĩa 'đúng chất, ra dáng N'; みたい, よう chỉ là so sánh.")
g.fill("youda", "Chọn cách nối đúng để điền vào chỗ trống.", "この{町|まち}は{夜|よる}になると、とても{静|しず}か＿＿＿ようだ。",
       "Thị trấn này có vẻ về đêm rất yên tĩnh.", [
           opt("a", "な", "youda"),
           opt("b", "の", None, "Danh từ mới nối bằng の; 静か là tính từ đuôi な."),
           opt("c", "だ", None, "Không nối だ trước ようだ."),
       ], "a", "Tính từ đuôi な + な + ようだ: 静かなようだ.")
g.error("mitai", "Chọn phần sai trong câu (văn nói thân mật).", "Cậu ấy nói chuyện cứ như trẻ con ấy.", [
    seg("{彼|かれ}は"), seg("{話|はな}し{方|かた}が"), seg("{子供|こども}のみたい", "youda"), seg("だね。"),
], 2, "{子供|こども}みたい", "みたい nối thẳng với danh từ, không có の: 子供みたいだ. Nếu muốn có の thì phải dùng ようだ (子供のようだ).")
g.context("rashii", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn không tự thấy, chỉ đọc trên mạng rằng quán ramen mới mở rất đông khách và muốn kể lại cho bạn.", [
    opt("a", "{駅前|えきまえ}の{新|あたら}しいラーメン{屋|や}、すごく{人気|にんき}があるらしいよ。", "rashii"),
    opt("b", "{駅前|えきまえ}の{新|あたら}しいラーメン{屋|や}、すごく{人気|にんき}があるようにするよ。", None, "ようにする là cố gắng làm cho…, sai nghĩa."),
    opt("c", "{駅前|えきまえ}の{新|あたら}しいラーメン{屋|や}、すごく{人気|にんき}らしい{人|ひと}だよ。", None, "Câu sai cấu trúc: 人気らしい人 không phải cách nói kể lại tin."),
], "a", "Thông tin đọc được trên mạng, người nói kể lại và giữ khoảng cách → らしい.")

# 5 ---------------------------------------------------------------------------------------------
g = group("hazuda-bekida", "〜はずだ và 〜べきだ",
          "Cả hai đều có thể dịch là 'phải…', nhưng はずだ là suy luận chắc chắn dựa trên căn cứ (lẽ ra là, chắc là), còn べきだ là đánh giá nên làm hay không (nên, phải).")
g.grammar("hazu", "n3g-g080", "〜はずだ", "Chắc chắn là…, lẽ ra…; thảo nào…", "V thể thường・Aい・Aな・N の ＋ はずだ",
          "Người nói suy ra từ căn cứ khách quan (lịch trình, logic, thông tin đã biết). Không thể hiện ý kiến nên hay không nên.")
g.grammar("beki", "n3g-g139", "〜べきだ", "Nên…, phải…", "V辞書形 ＋ べきだ（する → すべきだ／するべきだ）",
          "Người nói đánh giá điều đúng đắn, hợp đạo lý, có tính khuyên bảo mạnh. Thường không dùng với người trên trực tiếp.")
g.theory("Suy luận hay đánh giá", "Hỏi: câu này nói về khả năng (chắc là có, chắc là đến) hay về bổn phận (nên làm)? Khả năng → はずだ. Bổn phận, lời khuyên → べきだ. 'Anh ấy đã xuất phát từ 3 giờ nên chắc đã tới' → 着いたはずだ.")
g.theory("Khi nào không dùng", "Không dùng べきだ để đoán sự việc (×もう着くべきだ với nghĩa 'chắc sắp tới'). Không dùng はずだ để khuyên (×約束は守るはずだ với nghĩa 'nên giữ lời'). べきだ chỉ nối với thể từ điển, không nối với tính từ hay danh từ trực tiếp.")
g.compare("Bản chất", hazu="Suy luận từ căn cứ", beki="Đánh giá, lời khuyên")
g.compare("Câu hỏi để kiểm tra", hazu="Có căn cứ để tin là đúng không?", beki="Có đúng đắn, nên làm không?")
g.compare("Cách nối", hazu="Thể thường, Aな, N の", beki="Chỉ V thể từ điển (する→すべき)")
g.example("hazu", "{三時|さんじ}に{出発|しゅっぱつ}したから、もう{着|つ}いているはずだ。", "Đã xuất phát lúc 3 giờ nên chắc là đã tới rồi.")
g.example("hazu", "{彼|かれ}は{日本|にほん}に{十年|じゅうねん}{住|す}んでいたから、{日本語|にほんご}が{上手|じょうず}なはずだ。", "Anh ấy đã sống ở Nhật mười năm nên chắc chắn giỏi tiếng Nhật.")
g.example("beki", "{約束|やくそく}は{守|まも}るべきだ。", "Đã hứa thì nên giữ lời.")
g.example("beki", "{分|わ}からないことがあったら、すぐ{質問|しつもん}すべきだ。", "Có gì không hiểu thì nên hỏi ngay.")
g.tip("はず = 'theo lý thì' (đầu óc suy ra); べき = 'theo lẽ phải thì' (lương tâm bảo làm).")
g.mistake("Đoán rằng gói hàng đã gửi hôm qua thì hôm nay sẽ tới.", "{昨日|きのう}{送|おく}ったから、{今日|きょう}{届|とど}くべきだ。", "{昨日|きのう}{送|おく}ったから、{今日|きょう}{届|とど}くはずだ。",
          "Đây là suy luận từ việc đã gửi hôm qua, không phải lời khuyên, nên dùng はずだ.", "hazu", "beki")
g.mistake("Khuyên bạn nên xin lỗi.", "あなたが{悪|わる}いのだから、{先|さき}に{謝|あやま}るはずだ。", "あなたが{悪|わる}いのだから、{先|さき}に{謝|あやま}るべきだ。",
          "Người nói đánh giá điều nên làm (xin lỗi trước), nên dùng べきだ.", "beki", "hazu")
g.mc("beki", "Câu nào là lời khuyên 'nên…'?", [
    opt("a", "{学生|がくせい}はもっと{本|ほん}を{読|よ}むべきだ。", "beki"),
    opt("b", "{学生|がくせい}はもっと{本|ほん}を{読|よ}むはずだ。", "hazu", "読むはずだ là đoán 'chắc là đọc nhiều hơn', không phải lời khuyên."),
    opt("c", "{学生|がくせい}はもっと{本|ほん}を{読|よ}むそうだ。", None, "読むそうだ là 'nghe nói đọc nhiều hơn'."),
], "a", "Lời khuyên về điều nên làm → べきだ.")
g.fill("hazu", "Chọn từ phù hợp để điền vào chỗ trống.", "{鍵|かぎ}はかばんに{入|い}れた＿＿＿なのに、どこにもない。",
       "Lẽ ra tôi đã bỏ chìa khóa vào cặp, vậy mà tìm đâu cũng không thấy.", [
           opt("a", "はず", "hazu"),
           opt("b", "べき", "beki", "べき không nối với thể quá khứ 入れた và không dùng để nói 'lẽ ra đã'."),
       ], "a", "Người nói tin chắc (theo trí nhớ) là đã bỏ vào cặp → 入れたはずなのに.")
g.error("hazu", "Chọn phần sai trong câu.", "Chuyến bay cất cánh lúc 10 giờ, nên giờ này chắc anh ấy đang ở trên máy bay.", [
    seg("{飛行機|ひこうき}は{十時|じゅうじ}に"), seg("{出発|しゅっぱつ}したから、"), seg("{彼|かれ}は{今|いま}ごろ{機内|きない}に"), seg("いるべきだ。", "beki"),
], 3, "いるはずだ。", "Đây là suy luận từ giờ cất cánh, nên dùng はずだ. いるべきだ nghĩa là 'anh ấy nên ở trên máy bay' (đánh giá).")
g.context("beki", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn của bạn mượn tiền nhiều lần mà không trả. Bạn nói với một người khác rằng theo bạn, người mượn tiền thì nên trả đúng hẹn.", [
    opt("a", "{借|か}りたお{金|かね}は、{約束|やくそく}の{日|ひ}までに{返|かえ}すべきだよ。", "beki"),
    opt("b", "{借|か}りたお{金|かね}は、{約束|やくそく}の{日|ひ}までに{返|かえ}すはずだよ。", "hazu", "返すはずだ là đoán 'chắc là sẽ trả', không nói lên ý 'nên trả'."),
    opt("c", "{借|か}りたお{金|かね}は、{約束|やくそく}の{日|ひ}までに{返|かえ}すことになったよ。", None, "ことになった là 'đã được quyết định là', không phải ý kiến của bạn."),
], "a", "Người nói nêu ý kiến về điều đúng đắn → べきだ.")

# 6 ---------------------------------------------------------------------------------------------
g = group("wake-ga-nai-dewanai-niwa", "〜わけがない, 〜わけではない và 〜わけにはいかない",
          "Ba mẫu cùng có わけ nhưng nghĩa khác nhau: わけがない là phủ định mạnh (không đời nào), わけではない là phủ định một phần (không hẳn là), わけにはいかない là không thể làm vì lý do xã hội, đạo lý.")
g.grammar("ganai", "n3g-g082", "〜わけがない", "Không lẽ nào…, không đời nào…", "V thể thường・Aい・Aな・N な／である ＋ わけがない",
          "Người nói phủ định hoàn toàn khả năng một việc là đúng, dựa trên lý lẽ của mình. Gần nghĩa はずがない.")
g.grammar("dewanai", "n3g-g083", "〜わけではない", "Không hẳn là…, không có nghĩa là…", "V thể thường・Aい・Aな・N な／である ＋ わけではない",
          "Phủ định một phần, sửa lại suy nghĩ có thể bị hiểu nhầm. Hay đi với 全部、いつも、必ずしも、別に.")
g.grammar("niwa", "n3g-g084", "〜わけにはいかない", "Không thể… (vì lý do xã hội, đạo lý)", "Vる ＋ わけにはいかない",
          "Có khả năng làm nhưng hoàn cảnh, trách nhiệm, lương tâm không cho phép. Không dùng cho việc không làm được vì năng lực.")
g.theory("Ba mức phủ định khác nhau", "わけがない: 'không thể có chuyện đó' (phủ định khả năng). わけではない: 'không phải là như vậy hoàn toàn' (phủ định một phần). わけにはいかない: 'tôi không thể làm việc đó' (bị ngăn bởi nghĩa vụ). Hai mẫu đầu nói về sự thật; mẫu cuối nói về hành động của người nói.")
g.theory("Nhận biết nhanh", "Có 別に、全部、いつも、必ずしも → わけではない. Có lý do trách nhiệm (明日は試験だから、約束したから、仕事中だから) + hành động → わけにはいかない. Có lý lẽ bác bỏ mạnh (あんなに練習したのだから…負ける) → わけがない.")
g.compare("Nghĩa", ganai="Không đời nào (phủ định hoàn toàn)", dewanai="Không hẳn (phủ định một phần)", niwa="Không thể làm (vì nghĩa vụ)")
g.compare("Nói về", ganai="Khả năng của một sự việc", dewanai="Sửa hiểu lầm", niwa="Hành động của chính người nói")
g.compare("Cách nối", ganai="Thể thường, Aな／N な", dewanai="Thể thường, Aな／N な", niwa="Chỉ Vる (Vない cho 'buộc phải')")
g.example("ganai", "こんな{簡単|かんたん}な{問題|もんだい}を{間違|まちが}えるわけがない。", "Câu dễ thế này thì không đời nào làm sai.")
g.example("ganai", "{彼|かれ}が{嘘|うそ}をつくわけがない。", "Không lẽ nào anh ấy lại nói dối.")
g.example("dewanai", "{肉|にく}が{嫌|きら}いなわけではないが、あまり{食|た}べない。", "Không phải tôi ghét thịt, chỉ là không ăn nhiều.")
g.example("dewanai", "{高|たか}い{物|もの}がいつもいいわけではない。", "Đồ đắt không hẳn lúc nào cũng tốt.")
g.example("niwa", "{明日|あした}は{大事|だいじ}な{会議|かいぎ}があるから、{休|やす}むわけにはいかない。", "Mai có cuộc họp quan trọng nên tôi không thể nghỉ được.")
g.example("niwa", "{車|くるま}で{来|き}たので、お{酒|さけ}を{飲|の}むわけにはいかない。", "Tôi đi ô tô đến nên không thể uống rượu.")
g.tip("がない = không có khả năng; ではない = không phải hoàn toàn; にはいかない = không 'đi' được vì bị trách nhiệm chặn đường.")
g.mistake("Không thể bỏ về giữa chừng vì còn việc phải xong hôm nay.", "{今日中|きょうじゅう}に{終|お}わらせる{仕事|しごと}があるので、{帰|かえ}るわけがない。",
          "{今日中|きょうじゅう}に{終|お}わらせる{仕事|しごと}があるので、{帰|かえ}るわけにはいかない。", "Đây là hành động của người nói bị trách nhiệm ngăn lại, nên dùng わけにはいかない. わけがない là phủ định khả năng của sự việc.", "niwa", "ganai")
g.mistake("Sửa hiểu lầm: không phải ghét anh ta, chỉ là ít nói chuyện.", "{彼|かれ}が{嫌|きら}いなわけがない。ただ、あまり{話|はな}さないだけだ。",
          "{彼|かれ}が{嫌|きら}いなわけではない。ただ、あまり{話|はな}さないだけだ。", "Câu sau ただ…だけだ cho thấy đây là sửa hiểu lầm một phần → わけではない.", "dewanai", "ganai")
g.mc("dewanai", "Câu nào có nghĩa 'Không phải tôi hiểu hết tất cả'?", [
    opt("a", "{全部|ぜんぶ}{分|わ}かるわけではない。", "dewanai"),
    opt("b", "{全部|ぜんぶ}{分|わ}かるわけがない。", "ganai", "わけがない là 'không đời nào hiểu hết', phủ định mạnh."),
    opt("c", "{全部|ぜんぶ}{分|わ}かるわけにはいかない。", "niwa", "わけにはいかない là 'không thể (cho phép mình) hiểu hết', vô nghĩa ở đây."),
], "a", "全部 + わけではない: phủ định một phần.")
g.fill("niwa", "Chọn cách nói phù hợp để điền vào chỗ trống.", "{先生|せんせい}に{頼|たの}まれた{仕事|しごと}だから、{断|ことわ}る＿＿＿。",
       "Vì là việc thầy nhờ nên tôi không thể từ chối.", [
           opt("a", "わけにはいかない", "niwa"),
           opt("b", "わけではない", "dewanai", "わけではない là 'không hẳn là từ chối', không hợp."),
           opt("c", "わけがない", "ganai", "わけがない nói về khả năng của sự việc, không phải người nói bị ràng buộc."),
       ], "a", "Lý do đạo lý (thầy nhờ) ngăn người nói từ chối → わけにはいかない.")
g.error("ganai", "Chọn phần sai trong câu.", "Anh ấy đã luyện tập chăm chỉ như thế thì không đời nào thua.", [
    seg("あんなに"), seg("{一生懸命|いっしょうけんめい}{練習|れんしゅう}したのだから、"), seg("{彼|かれ}が"), seg("{負|ま}けるわけにはいかない。", "niwa"),
], 3, "{負|ま}けるわけがない。", "Người nói phủ định khả năng anh ấy thua dựa trên lý lẽ → わけがない. わけにはいかない chỉ hành động bị trách nhiệm ngăn lại.")
g.context("dewanai", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn bè nghĩ bạn không thích đồ ngọt vì bạn hay từ chối bánh. Thật ra bạn thích, chỉ là đang ăn kiêng.", [
    opt("a", "{甘|あま}い{物|もの}が{嫌|きら}いなわけではないよ。ダイエット{中|ちゅう}なんだ。", "dewanai"),
    opt("b", "{甘|あま}い{物|もの}が{嫌|きら}いなわけがないよ。ダイエット{中|ちゅう}なんだ。", "ganai", "わけがない là phủ định mạnh 'làm sao mà ghét được', nghe gay gắt và không hợp mạch sửa hiểu lầm."),
    opt("c", "{甘|あま}い{物|もの}を{食|た}べるわけにはいかないよ。{嫌|きら}いなんだ。", "niwa", "Câu sau 嫌いなんだ trái với sự thật là bạn thích đồ ngọt."),
], "a", "Sửa hiểu lầm một phần (không phải ghét) → わけではない.")

# 7 ---------------------------------------------------------------------------------------------
g = group("aida-aidani", "〜間（は） và 〜間に",
          "間 nói hành động kéo dài suốt cả khoảng thời gian; 間に nói một việc xảy ra (một lần) vào lúc nào đó trong khoảng thời gian ấy.")
g.grammar("aida", "n3g-g152", "〜間（は）", "Suốt trong khi…", "N の／Aい／Aな な／V thể thường（hay Vている） ＋ 間（は）",
          "Vế sau là trạng thái hoặc hành động kéo dài từ đầu đến cuối khoảng thời gian (ずっと…ている、…ていた).")
g.grammar("aidani", "n3g-g153", "〜間に", "Trong lúc… (thì một việc xảy ra)", "N の／Aい／Aな な／V thể thường（hay Vている） ＋ 間に",
          "Vế sau là hành động hay sự việc xảy ra một lần, kết thúc trong khoảng thời gian đó (…した、…てしまった).")
g.theory("Nhìn động từ ở vế sau", "Vế sau kéo dài (ずっと寝ていた、待っていた) → 間. Vế sau là việc xảy ra một lần, có điểm kết thúc (泥棒が入った、メールが来た、掃除を済ませた) → 間に.")
g.theory("Phân biệt với うちに", "間に chỉ vị trí trong khoảng thời gian. うちに nhấn mạnh 'tranh thủ trước khi tình trạng thay đổi' (若いうちに). Câu có 若いうちに、温かいうちに thường không thay bằng 間に.")
g.compare("Vế sau", aida="Kéo dài suốt khoảng thời gian", aidani="Xảy ra một lần trong khoảng thời gian")
g.compare("Từ hay đi kèm", aida="ずっと、〜ている／〜ていた", aidani="〜た、〜てしまった、もう")
g.compare("Hình ảnh", aida="Một đường kéo dài", aidani="Một điểm nằm trong đoạn")
g.example("aida", "{夏休|なつやす}みの{間|あいだ}、ずっと{祖母|そぼ}の{家|いえ}にいた。", "Suốt kỳ nghỉ hè, tôi ở nhà bà.")
g.example("aida", "{母|はは}が{料理|りょうり}をしている{間|あいだ}、{私|わたし}は{本|ほん}を{読|よ}んでいた。", "Trong suốt lúc mẹ nấu ăn, tôi đọc sách.")
g.example("aidani", "{留守|るす}の{間|あいだ}に、{泥棒|どろぼう}が{入|はい}った。", "Trong lúc vắng nhà thì trộm vào.")
g.example("aidani", "{子供|こども}が{寝|ね}ている{間|あいだ}に、{掃除|そうじ}を{済|す}ませた。", "Tôi đã dọn dẹp xong trong lúc con đang ngủ.")
g.tip("間 = cả đoạn đường; 間に = một điểm dừng trên đoạn đường đó. Thêm に là thêm 'một thời điểm'.")
g.mistake("Kể rằng mình ngủ suốt chuyến tàu.", "{電車|でんしゃ}に{乗|の}っている{間|あいだ}に、ずっと{寝|ね}ていた。",
          "{電車|でんしゃ}に{乗|の}っている{間|あいだ}、ずっと{寝|ね}ていた。", "ずっと寝ていた là trạng thái kéo dài suốt chuyến đi, nên dùng 間 (không có に).", "aida", "aidani")
g.mistake("Ai đó gọi điện khi bạn đang tắm.", "シャワーを{浴|あ}びている{間|あいだ}、{電話|でんわ}がかかってきた。",
          "シャワーを{浴|あ}びている{間|あいだ}に、{電話|でんわ}がかかってきた。", "Cuộc gọi đến là việc xảy ra một lần trong lúc tắm, nên dùng 間に.", "aidani", "aida")
g.mc("aidani", "Chọn câu đúng.", [
    opt("a", "{日本|にほん}にいる{間|あいだ}に、{富士山|ふじさん}に{登|のぼ}りたい。", "aidani"),
    opt("b", "{日本|にほん}にいる{間|あいだ}、{富士山|ふじさん}に{登|のぼ}りたい。", "aida", "Leo núi Phú Sĩ là việc làm một lần, không kéo dài suốt thời gian ở Nhật."),
    opt("c", "{日本|にほん}にいる{間|あいだ}に、ずっと{日本語|にほんご}を{勉強|べんきょう}していた。", None, "ずっと…ていた là kéo dài suốt khoảng thời gian, phải dùng 間 không có に."),
], "a", "Muốn leo núi Phú Sĩ một lần vào lúc nào đó trong thời gian ở Nhật → 間に.")
g.fill("aida", "Chọn từ phù hợp để điền vào chỗ trống.", "{会議|かいぎ}の＿＿＿、{彼|かれ}は{一言|ひとこと}も{話|はな}さなかった。",
       "Suốt cuộc họp, anh ấy không nói một lời nào.", [
           opt("a", "{間|あいだ}", "aida"),
           opt("b", "{間|あいだ}に", "aidani", "Không nói lời nào là trạng thái kéo dài suốt cuộc họp, không phải việc xảy ra một lần."),
       ], "a", "Trạng thái 'không nói gì' kéo dài từ đầu đến cuối cuộc họp → 間.")
g.error("aidani", "Chọn phần sai trong câu.", "Trong lúc tôi đi mua sắm thì có bưu phẩm đến.", [
    seg("{私|わたし}が"), seg("{買|か}い{物|もの}に{行|い}っている"), seg("{間|あいだ}、", "aida"), seg("{荷物|にもつ}が{届|とど}いた。"),
], 2, "{間|あいだ}に、", "Bưu phẩm đến là việc xảy ra một lần trong khoảng thời gian đi mua sắm → 間に.")
g.context("aida", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn kể rằng trong toàn bộ thời gian sống ở Osaka (3 năm), bạn làm thêm ở một quán ăn.", [
    opt("a", "{大阪|おおさか}に{住|す}んでいた{間|あいだ}、ずっとレストランでアルバイトをしていた。", "aida"),
    opt("b", "{大阪|おおさか}に{住|す}んでいた{間|あいだ}に、ずっとレストランでアルバイトをしていた。", "aidani", "ずっと…していた kéo dài suốt thời gian, không đi với 間に."),
    opt("c", "{大阪|おおさか}に{住|す}んでいるうちに、ずっとアルバイトをしたい。", None, "うちに và thì tương lai không hợp với việc kể lại quá khứ."),
], "a", "Làm thêm suốt cả thời gian sống ở Osaka → 間 + ずっと.")

# 8 ---------------------------------------------------------------------------------------------
g = group("tokoro-ru-teiru-ta", "〜るところだ, 〜ているところだ và 〜たところだ",
          "Ba mẫu ところだ chỉ vị trí của hành động trên dòng thời gian: sắp làm (Vる), đang làm (Vている), vừa làm xong (Vた). Chỉ khác ở dạng động từ đứng trước.")
g.grammar("ru", "n3g-g154", "〜るところだ", "Sắp…, chuẩn bị…", "Vる ＋ ところだ",
          "Hành động chưa bắt đầu nhưng sắp bắt đầu ngay. Hay đi với 今から、これから、ちょうど.")
g.grammar("teiru", "n3g-g155", "〜ているところだ", "Đang…", "Vている ＋ ところだ",
          "Hành động đang diễn ra đúng lúc nói. Nhấn mạnh 'đang giữa chừng', hay đi với 今、ちょうど.")
g.grammar("ta", "n3g-g156", "〜たところだ", "Vừa mới… xong", "Vた ＋ ところだ",
          "Hành động vừa kết thúc ngay trước lúc nói (cảm nhận của người nói). Hay đi với 今、ちょうど、たった今.")
g.theory("Một hành động, ba thời điểm", "今から食べるところだ (chưa ăn, sắp ăn) → 今食べているところだ (đang ăn) → 今食べたところだ (ăn xong rồi). Dạng động từ trước ところ quyết định thời điểm.")
g.theory("Lưu ý khi dùng", "たところだ chỉ dùng cho việc vừa xong ngay trước lúc nói; với khoảng thời gian dài hơn (tuần trước, tháng trước) dùng たばかりだ. Không dùng ところだ với từ chỉ thời gian cụ thể đã lâu như 三日前に.")
g.compare("Thời điểm", ru="Trước khi bắt đầu", teiru="Đang diễn ra", ta="Ngay sau khi kết thúc")
g.compare("Dạng động từ", ru="Vる (từ điển)", teiru="Vている", ta="Vた")
g.compare("Từ hay đi kèm", ru="今から、これから", teiru="今、ちょうど今", ta="今、たった今")
g.example("ru", "{今|いま}から{出|で}かけるところです。", "Bây giờ tôi chuẩn bị đi ra ngoài.")
g.example("ru", "{今|いま}ちょうどあなたに{電話|でんわ}をかけるところでした。", "Tôi đang định gọi điện cho bạn đúng lúc này.")
g.example("teiru", "{今|いま}、レポートを{書|か}いているところだから、{後|あと}で{話|はな}そう。", "Giờ mình đang viết báo cáo, lát nữa nói chuyện nhé.")
g.example("teiru", "{父|ちち}は{今|いま}、{車|くるま}を{洗|あら}っているところです。", "Bây giờ bố tôi đang rửa xe.")
g.example("ta", "{今|いま}{駅|えき}に{着|つ}いたところです。", "Tôi vừa mới tới ga.")
g.example("ta", "{会議|かいぎ}はたった{今|いま}{終|お}わったところだ。", "Cuộc họp vừa mới kết thúc ngay lúc này.")
g.tip("る = mũi tên chưa bắn, ている = mũi tên đang bay, た = mũi tên vừa cắm vào đích.")
g.mistake("Bạn gọi rủ đi ăn, nhưng bạn vừa ăn xong.", "ごめん、{今|いま}ご{飯|はん}を{食|た}べるところなんだ。", "ごめん、{今|いま}ご{飯|はん}を{食|た}べたところなんだ。",
          "Đã ăn xong rồi thì dùng Vた + ところ. 食べるところ là 'sắp ăn'.", "ta", "ru")
g.mistake("Đang tắm thì có khách.", "{今|いま}シャワーを{浴|あ}びたところなので、{少|すこ}し{待|ま}ってください。", "{今|いま}シャワーを{浴|あ}びているところなので、{少|すこ}し{待|ま}ってください。",
          "Đang tắm (chưa xong) thì dùng Vている + ところ.", "teiru", "ta")
g.mc("ru", "Đồng hồ sắp điểm 9 giờ, bộ phim chưa bắt đầu. Câu nào đúng?", [
    opt("a", "{映画|えいが}がもうすぐ{始|はじ}まるところだ。", "ru"),
    opt("b", "{映画|えいが}がもうすぐ{始|はじ}まったところだ。", "ta", "始まったところ là 'vừa bắt đầu', nhưng phim chưa bắt đầu; もうすぐ cũng không đi với dạng た."),
    opt("c", "{映画|えいが}がもうすぐ{始|はじ}まっているところだ。", "teiru", "始まっている là đã bắt đầu và đang chiếu; không hợp với もうすぐ."),
], "a", "Chưa bắt đầu, sắp bắt đầu → Vる + ところだ.")
g.fill("teiru", "Chọn dạng đúng để điền vào chỗ trống.", "「もしもし、{今|いま}{話|はな}せる？」「ごめん、{今|いま}{運転|うんてん}し＿＿＿ところなんだ。」",
       "— Alo, giờ nói chuyện được không? — Xin lỗi, mình đang lái xe.", [
           opt("a", "ている", "teiru"),
           opt("b", "た", "ta", "運転したところ là vừa lái xong; người nói không thể nghe điện vì đang lái."),
           opt("c", "る", "ru", "運転するところ là sắp lái, chưa phải lý do không nói chuyện được."),
       ], "a", "Đang lái xe nên không nói chuyện được → Vている + ところ.")
g.error("ta", "Chọn phần sai trong câu.", "Tàu vừa mới chạy mất nên phải đợi chuyến tiếp theo 20 phút.", [
    seg("{電車|でんしゃ}は"), seg("{今|いま}{出|で}るところなので、", "ru"), seg("{次|つぎ}の{電車|でんしゃ}まで"), seg("{二十分|にじゅっぷん}{待|ま}たなければならない。"),
], 1, "{今|いま}{出|で}たところなので、", "Tàu đã chạy rồi (nên phải đợi chuyến sau) → Vた + ところ: 出たところ.")
g.context("ta", "Chọn câu trả lời phù hợp nhất trong tình huống sau.", "Bạn vừa đến chỗ hẹn được một phút thì bạn của bạn tới và xin lỗi vì đến muộn. Bạn muốn nói mình cũng vừa mới tới.", [
    opt("a", "{大丈夫|だいじょうぶ}、{私|わたし}も{今|いま}{来|き}たところだよ。", "ta"),
    opt("b", "{大丈夫|だいじょうぶ}、{私|わたし}も{今|いま}{来|く}るところだよ。", "ru", "来るところ là 'sắp tới', nhưng bạn đã ở đó rồi."),
    opt("c", "{大丈夫|だいじょうぶ}、{私|わたし}も{今|いま}{来|き}ているところだよ。", "teiru", "来ているところ không dùng để nói 'vừa tới'."),
], "a", "Vừa mới tới → Vた + ところだ.")

# 9 ---------------------------------------------------------------------------------------------
g = group("ippouda-bakarida", "〜一方だ và 〜ばかりだ",
          "Cả hai đều nghĩa 'ngày càng…', chỉ một sự thay đổi cứ tiếp diễn theo một hướng. ばかりだ hầu như chỉ dùng cho chiều hướng xấu; 一方だ hay dùng cho chiều xấu nhưng cũng dùng được cho thay đổi trung tính hoặc tốt.")
g.grammar("ippou", "n3g-g047", "〜一方だ", "Ngày càng…, cứ thế mà…", "Vる ＋ 一方だ",
          "Thay đổi liên tục theo một hướng, không dừng. Đi với động từ chỉ sự thay đổi (増える、減る、悪くなる、上がる). Có thể dùng cho thay đổi trung tính hoặc tốt.")
g.grammar("bakari", "n3g-g161", "〜ばかりだ", "Ngày càng… (thường theo chiều xấu)", "Vる ＋ ばかりだ",
          "Thay đổi theo hướng xấu cứ tiếp diễn, người nói lo lắng, bất mãn. Không dùng cho thay đổi tốt.")
g.theory("Chiều hướng của thay đổi", "Xấu (悪くなる、増える[ゴミ]、減る[客]) → cả hai đều được, ばかりだ nhấn mạnh sự lo lắng. Tốt (上手になる、よくなる) → chỉ 一方だ (×上手になるばかりだ).")
g.theory("Chỉ đi với động từ thay đổi", "Cả hai đều cần động từ chỉ sự biến đổi (〜くなる、〜になる、増える、減る、上がる、下がる). Không dùng với hành động một lần như 食べる、行く.")
g.compare("Chiều hướng", ippou="Xấu, trung tính, đôi khi tốt", bakari="Hầu như chỉ xấu")
g.compare("Cảm xúc người nói", ippou="Khách quan hơn", bakari="Lo lắng, bất mãn rõ")
g.compare("Ví dụ", ippou="人口が増える一方だ", bakari="病気は悪くなるばかりだ")
g.example("ippou", "この{町|まち}の{人口|じんこう}は{減|へ}る{一方|いっぽう}だ。", "Dân số thị trấn này ngày càng giảm.")
g.example("ippou", "{彼女|かのじょ}の{日本語|にほんご}は{上手|じょうず}になる{一方|いっぽう}だ。", "Tiếng Nhật của cô ấy ngày càng giỏi.")
g.example("bakari", "{物価|ぶっか}は{上|あ}がるばかりで、{生活|せいかつ}が{苦|くる}しい。", "Vật giá cứ tăng mãi, cuộc sống thật khó khăn.")
g.example("bakari", "{薬|くすり}を{飲|の}んでも、{咳|せき}はひどくなるばかりだ。", "Uống thuốc rồi mà cơn ho cứ ngày càng nặng hơn.")
g.tip("ばかり hay mang sắc thái tiêu cực ('chỉ toàn…'); 一方 là 'một phía', cứ một chiều mà đi, tốt xấu đều được.")
g.mistake("Khen học sinh tiến bộ nhanh.", "{田中|たなか}さんの{成績|せいせき}はよくなるばかりだ。", "{田中|たなか}さんの{成績|せいせき}はよくなる{一方|いっぽう}だ。",
          "Thay đổi tốt (thành tích tốt lên) không dùng ばかりだ; dùng 一方だ.", "ippou", "bakari")
g.mc("ippou", "Câu nào tự nhiên?", [
    opt("a", "{練習|れんしゅう}のおかげで、{彼|かれ}のピアノは{上手|じょうず}になる{一方|いっぽう}だ。", "ippou"),
    opt("b", "{練習|れんしゅう}のおかげで、{彼|かれ}のピアノは{上手|じょうず}になるばかりだ。", "bakari", "ばかりだ không dùng cho thay đổi tốt."),
    opt("c", "{練習|れんしゅう}のおかげで、{彼|かれ}はピアノを{弾|ひ}く{一方|いっぽう}だ。", None, "弾く không phải động từ chỉ sự thay đổi."),
], "a", "Thay đổi tốt, tiếp diễn → 一方だ.")
g.fill("bakari", "Chọn cách nói phù hợp nhất để điền vào chỗ trống.", "{雨|あめ}は{止|や}むどころか、{強|つよ}くなる＿＿＿で、{今日|きょう}の{試合|しあい}は{無理|むり}そうだ。",
       "Mưa chẳng những không tạnh mà còn ngày càng to, trận đấu hôm nay chắc không được rồi.", [
           opt("a", "ばかり", "bakari"),
           opt("b", "ところ", None, "強くなるところ là 'sắp mạnh lên', không diễn tả sự thay đổi tiếp diễn."),
           opt("c", "はず", None, "強くなるはず là suy đoán 'chắc sẽ mạnh lên', không hợp với どころか."),
       ], "a", "Thay đổi xấu tiếp diễn, người nói lo lắng → 強くなるばかりで. (一方で ở đây dễ bị hiểu thành 'mặt khác'.)")
g.error("ippou", "Chọn phần sai trong câu.", "Nhờ quảng cáo trên mạng, số khách của cửa hàng ngày càng tăng, chủ quán rất vui.", [
    seg("ネットの{宣伝|せんでん}のおかげで、"), seg("{店|みせ}の{客|きゃく}は"), seg("{増|ふ}えるばかりで、", "bakari"), seg("{店長|てんちょう}は{喜|よろこ}んでいる。"),
], 2, "{増|ふ}える{一方|いっぽう}で、", "Khách tăng là điều đáng mừng (店長は喜んでいる); ばかりだ mang sắc thái lo lắng nên không hợp. Dùng 一方で.")
g.context("bakari", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn lo lắng kể với bác sĩ rằng dù đã nghỉ ngơi, cơn đau lưng của bạn cứ ngày một tệ đi.", [
    opt("a", "{休|やす}んでいるのに、{腰|こし}の{痛|いた}みはひどくなるばかりなんです。", "bakari"),
    opt("b", "{休|やす}んでいるのに、{腰|こし}の{痛|いた}みはひどくなったところなんです。", None, "ひどくなったところ là 'vừa mới nặng lên', không phải tiếp diễn."),
    opt("c", "{休|やす}んでいるのに、{腰|こし}の{痛|いた}みはひどくなるはずなんです。", None, "はず là suy đoán, không phải mô tả điều đang xảy ra."),
], "a", "Tình trạng xấu đi liên tục, người nói lo lắng → ばかりだ.")

# 10 --------------------------------------------------------------------------------------------
g = group("gimi-gachi-ppoi", "〜ぎみ, 〜がち và 〜っぽい",
          "Ba hậu tố đều diễn tả một khuynh hướng, nhưng ぎみ là 'hơi có dấu hiệu' ở thời điểm hiện tại, がち là 'hay bị, thường hay' (lặp lại nhiều lần), còn っぽい là 'trông như, có tính chất giống' hoặc 'dễ…' (văn nói).")
g.grammar("gimi", "n3g-g068", "〜ぎみ", "Hơi có vẻ…, có chiều hướng…", "Vます（bỏ ます）・N ＋ ぎみ（だ／の N）",
          "Một chút dấu hiệu của trạng thái (thường không tốt) đang thấy lúc này: 風邪ぎみ、疲れぎみ、太りぎみ、遅れぎみ.")
g.grammar("gachi", "n3g-g069", "〜がち", "Hay…, thường hay… (khuynh hướng)", "Vます（bỏ ます）・N ＋ がち（だ／の N）",
          "Việc không tốt dễ xảy ra, lặp lại nhiều lần: 忘れがち、遅れがち、病気がち、曇りがち.")
g.grammar("ppoi", "n3g-g073", "〜っぽい", "Có vẻ…, giống như…; hay…, dễ…", "Vます（bỏ ます）・Aい（bỏ い）・N ＋ っぽい",
          "Văn nói. (1) Có tính chất giống: 子供っぽい、安っぽい、白っぽい; (2) dễ…: 忘れっぽい、怒りっぽい (tính cách).")
g.theory("Một lần, nhiều lần hay tính chất", "ぎみ: trạng thái hơi có dấu hiệu ở hiện tại (今日は風邪ぎみだ). がち: lặp đi lặp lại thành khuynh hướng (冬は風邪をひきがちだ). っぽい: tính chất, vẻ ngoài, tính cách (子供っぽい服、怒りっぽい人).")
g.theory("Mức trang trọng", "ぎみ, がち dùng được trong văn viết. っぽい là văn nói, thân mật. 忘れがち (hay quên trong tình huống nào đó) và 忘れっぽい (tính hay quên) gần nghĩa; っぽい nói về tính cách con người.")
g.compare("Nghĩa chính", gimi="Hơi có dấu hiệu (lúc này)", gachi="Hay xảy ra (lặp lại)", ppoi="Trông như, có tính chất; dễ…")
g.compare("Ví dụ tiêu biểu", gimi="風邪ぎみ、疲れぎみ", gachi="遅れがち、病気がち", ppoi="子供っぽい、忘れっぽい")
g.compare("Văn phong", gimi="Trung tính", gachi="Trung tính, văn viết", ppoi="Văn nói")
g.example("gimi", "{最近|さいきん}、{少|すこ}し{太|ふと}りぎみなので、{運動|うんどう}を{始|はじ}めた。", "Dạo này hơi có dấu hiệu tăng cân nên tôi bắt đầu tập thể dục.")
g.example("gimi", "{今日|きょう}は{風邪|かぜ}ぎみで、{頭|あたま}が{少|すこ}し{痛|いた}い。", "Hôm nay tôi hơi bị cảm, đầu hơi đau.")
g.example("gachi", "{冬|ふゆ}は{運動不足|うんどうぶそく}になりがちだ。", "Mùa đông người ta hay bị thiếu vận động.")
g.example("gachi", "{彼|かれ}は{子供|こども}のころ、{病気|びょうき}がちだった。", "Hồi nhỏ anh ấy hay ốm.")
g.example("ppoi", "その{服|ふく}は{少|すこ}し{子供|こども}っぽいね。", "Bộ quần áo đó trông hơi trẻ con nhỉ.")
g.example("ppoi", "{祖父|そふ}は{年|とし}を{取|と}って{忘|わす}れっぽくなった。", "Ông tôi có tuổi nên trở nên hay quên.")
g.tip("ぎみ = 'chớm' (một chút, bây giờ); がち = 'hay bị' (nhiều lần); っぽい = 'trông như / tính hay' (văn nói).")
g.mistake("Kể rằng tàu điện mùa tuyết thường xuyên bị trễ.", "{雪|ゆき}の{日|ひ}は、{電車|でんしゃ}が{遅|おく}れっぽい。", "{雪|ゆき}の{日|ひ}は、{電車|でんしゃ}が{遅|おく}れがちだ。",
          "Việc xảy ra lặp lại nhiều lần (tàu hay bị trễ) dùng がち. っぽい dùng cho tính chất, tính cách, không dùng cho sự việc như tàu trễ.", "gachi", "ppoi")
g.mistake("Nói hôm nay mình hơi mệt.", "{今日|きょう}は{少|すこ}し{疲|つか}れがちです。", "{今日|きょう}は{少|すこ}し{疲|つか}れぎみです。",
          "Hơi có dấu hiệu mệt ngay hôm nay (một lần) → ぎみ. がち là hay mệt (lặp lại).", "gimi", "gachi")
g.mc("ppoi", "Câu nào có nghĩa 'Anh ấy là người dễ nổi nóng'?", [
    opt("a", "{彼|かれ}は{怒|おこ}りっぽい{人|ひと}だ。", "ppoi"),
    opt("b", "{彼|かれ}は{怒|おこ}りぎみの{人|ひと}だ。", "gimi", "ぎみ là hơi có dấu hiệu lúc này, không diễn tả tính cách."),
    opt("c", "{彼|かれ}は{怒|おこ}りかけの{人|ひと}だ。", None, "かけ là đang làm dở, không phải tính cách."),
], "a", "Tính cách dễ nổi giận → 怒りっぽい.")
g.fill("gachi", "Chọn từ phù hợp để điền vào chỗ trống.", "{忙|いそが}しいと、{食事|しょくじ}が{不規則|ふきそく}になり＿＿＿だ。",
       "Khi bận rộn, bữa ăn hay trở nên thất thường.", [
           opt("a", "がち", "gachi"),
           opt("b", "っぽい", "ppoi", "っぽい chỉ vẻ ngoài hay tính cách, không dùng cho khuynh hướng của sự việc."),
           opt("c", "ぎみ", "gimi", "ぎみ chỉ dấu hiệu ở một thời điểm, câu này nói về điều thường xảy ra mỗi khi bận."),
       ], "a", "Điều không tốt hay xảy ra mỗi khi bận → がち.")
g.error("gimi", "Chọn phần sai trong câu.", "Từ sáng tôi đã hơi bị cảm nên hôm nay sẽ về sớm.", [
    seg("{朝|あさ}から"), seg("{少|すこ}し{風邪|かぜ}がちなので、", "gachi"), seg("{今日|きょう}は"), seg("{早|はや}く{帰|かえ}ります。"),
], 1, "{少|すこ}し{風邪|かぜ}ぎみなので、", "朝から少し (từ sáng, một chút) là dấu hiệu ngay hôm nay → 風邪ぎみ. 風邪がち là hay bị cảm (lặp lại nhiều lần).")
g.context("gimi", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn nói với cấp trên rằng dự án đang hơi bị chậm so với kế hoạch (tuần này).", [
    opt("a", "プロジェクトが{予定|よてい}より{少|すこ}し{遅|おく}れぎみです。", "gimi"),
    opt("b", "プロジェクトが{予定|よてい}より{少|すこ}し{遅|おく}れっぽいです。", "ppoi", "っぽい là văn nói thân mật, không dùng khi báo cáo cấp trên và không hợp với tiến độ."),
    opt("c", "プロジェクトが{予定|よてい}より{少|すこ}し{遅|おく}れたところです。", None, "遅れたところ là 'vừa mới trễ', không diễn tả xu hướng hơi chậm."),
], "a", "Hơi có dấu hiệu chậm ở thời điểm này, báo cáo trang trọng → 遅れぎみ.")

# 11 --------------------------------------------------------------------------------------------
g = group("muki-muke", "〜向き và 〜向け",
          "向き là 'phù hợp với' (tự nhiên hợp với ai, cái gì), còn 向け là 'dành cho, làm ra cho' (được làm ra với mục đích hướng tới đối tượng đó).")
g.grammar("muki", "n3g-g070", "〜向き", "Phù hợp với…, hợp với…", "N ＋ 向き（だ／の N）",
          "Đặc điểm sẵn có của sự vật hợp với đối tượng, dù không được làm riêng cho họ: この料理は子供向きだ (hợp với trẻ). Có dạng phủ định 向きではない、不向き.")
g.grammar("muke", "n3g-g071", "〜向け", "Dành cho…, hướng tới…", "N ＋ 向け（だ／の N／に）",
          "Được sản xuất, thiết kế với chủ đích cho đối tượng: 子供向けの番組、外国人向けのパンフレット、海外向けに輸出する.")
g.theory("Tự nhiên hợp hay được làm cho", "Câu hỏi kiểm tra: người làm ra có nhắm đến đối tượng đó không? Có (chương trình làm cho trẻ em, tài liệu viết cho người nước ngoài) → 向け. Không, chỉ là tính chất hợp (món này không cay nên hợp với trẻ) → 向き.")
g.theory("Dạng phủ định và cách nối", "向き có 向きではない、不向き (không hợp). 向け hay đi với に + động từ: 海外向けに作る. Cả hai nối thẳng với danh từ.")
g.compare("Ý nghĩa", muki="Hợp với (tính chất sẵn có)", muke="Dành cho (mục đích khi làm ra)")
g.compare("Câu kiểm tra", muki="Có hợp không?", muke="Làm ra cho ai?")
g.compare("Dạng hay gặp", muki="初心者向き、不向き", muke="子供向けの番組、海外向けに輸出する")
g.example("muki", "この{山|やま}は{低|ひく}くて{道|みち}も{広|ひろ}いので、{初心者|しょしんしゃ}{向|む}きだ。", "Ngọn núi này thấp, đường rộng nên hợp với người mới.")
g.example("muki", "{細|こま}かい{作業|さぎょう}が{苦手|にがて}な{私|わたし}は、この{仕事|しごと}に{不向|ふむ}きだ。", "Tôi vốn kém việc tỉ mỉ nên không hợp với công việc này.")
g.example("muke", "これは{外国人|がいこくじん}{向|む}けに{書|か}かれた{日本|にほん}の{歴史|れきし}の{本|ほん}です。", "Đây là cuốn sách lịch sử Nhật viết dành cho người nước ngoài.")
g.example("muke", "この{会社|かいしゃ}は{高齢者|こうれいしゃ}{向|む}けのスマートフォンを{作|つく}っている。", "Công ty này sản xuất điện thoại dành cho người cao tuổi.")
g.tip("向け: 'hướng mũi tên tới' khách hàng khi sản xuất. 向き: 'quay mặt vừa vặn' với ai đó, tự nhiên hợp.")
g.mistake("Giới thiệu một chương trình TV được làm riêng cho trẻ em.", "これは{子供|こども}{向|む}きに{作|つく}られた{番組|ばんぐみ}です。", "これは{子供|こども}{向|む}けに{作|つく}られた{番組|ばんぐみ}です。",
          "作られた (được làm ra) cho thấy mục đích nhắm vào trẻ em → 向け.", "muke", "muki")
g.mistake("Nói căn hộ nhỏ này hợp với người sống một mình.", "この{部屋|へや}は{狭|せま}いので、{一人暮|ひとりぐ}らし{向|む}けだ。", "この{部屋|へや}は{狭|せま}いので、{一人暮|ひとりぐ}らし{向|む}きだ。",
          "Lý do là đặc điểm sẵn có (phòng hẹp) nên hợp với người ở một mình → 向き. Không phải phòng được xây riêng cho họ.", "muki", "muke")
g.mc("muke", "Câu nào nói về sản phẩm được làm riêng cho thị trường nước ngoài?", [
    opt("a", "これは{海外|かいがい}{向|む}けの{製品|せいひん}です。", "muke"),
    opt("b", "これは{海外|かいがい}{向|む}きの{製品|せいひん}です。", "muki", "向き chỉ sự phù hợp tự nhiên, không nói lên mục đích sản xuất."),
    opt("c", "これは{海外|かいがい}{向|む}かいの{製品|せいひん}です。", None, "向かい là 'phía đối diện', không dùng ở đây."),
], "a", "Sản xuất nhắm vào thị trường nước ngoài → 海外向け.")
g.fill("muki", "Chọn từ phù hợp để điền vào chỗ trống.", "この{料理|りょうり}は{辛|から}くないので、{子供|こども}＿＿＿だ。",
       "Món này không cay nên hợp với trẻ con.", [
           opt("a", "{向|む}き", "muki"),
           opt("b", "{向|む}け", "muke", "Món ăn không được làm riêng cho trẻ; chỉ vì không cay nên hợp."),
       ], "a", "Đặc điểm sẵn có (không cay) phù hợp với trẻ → 向き.")
g.error("muke", "Chọn phần sai trong câu.", "Thành phố đã làm bản đồ dành cho du khách nước ngoài.", [
    seg("{市|し}は"), seg("{外国人|がいこくじん}{観光客|かんこうきゃく}"), seg("{向|む}きの", "muki"), seg("{地図|ちず}を{作|つく}った。"),
], 2, "{向|む}けの", "Bản đồ được thành phố làm ra nhắm vào du khách nước ngoài → 向け.")
g.context("muki", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn bè hỏi nên chọn đôi giày nào để đi bộ đường dài. Bạn thấy đôi giày thể thao bình thường của mình nhẹ và êm nên hợp với việc đi bộ lâu.", [
    opt("a", "この{靴|くつ}は{軽|かる}くて{柔|やわ}らかいから、{長|なが}く{歩|ある}くのに{向|む}いているよ。", "muki"),
    opt("b", "この{靴|くつ}は{軽|かる}くて{柔|やわ}らかいから、{長|なが}く{歩|ある}く{人|ひと}{向|む}けに{作|つく}られたよ。", "muke", "Bạn không biết giày được làm cho ai; câu này khẳng định mục đích sản xuất."),
    opt("c", "この{靴|くつ}は{軽|かる}くて{柔|やわ}らかいから、{長|なが}く{歩|ある}くことになっているよ。", None, "ことになっている là 'theo quy định', không hợp."),
], "a", "Chỉ là đặc điểm sẵn có phù hợp → 向き (動詞 向いている).")

# 12 --------------------------------------------------------------------------------------------
g = group("nikanshite-nitaishite-nitotte", "〜に関して, 〜に対して và 〜にとって",
          "Ba trợ từ ghép đều có thể dịch là 'về, đối với', nhưng に関して là 'về chủ đề', に対して là 'hướng tới đối tượng' (hành động, thái độ), にとって là 'từ lập trường của ai' (đánh giá).")
g.grammar("kanshite", "n3g-g022", "〜に関して", "Liên quan đến…, về…", "N ＋ に関して／に関する N",
          "Nêu chủ đề của việc nói, viết, nghiên cứu, điều tra: 〜に関して話す、調べる、質問する. Trang trọng hơn について.")
g.grammar("taishite", "n3g-g025", "〜に対して", "Đối với…, hướng tới…", "N ＋ に対して／に対する N",
          "Hành động, thái độ, cảm xúc hướng thẳng vào đối tượng: 客に対して丁寧だ、質問に対して答える、親に対する態度.")
g.grammar("nitotte", "n3g-g037", "〜にとって", "Đối với… (từ lập trường của…)", "N ＋ にとって",
          "Đánh giá từ góc nhìn của N: 大切だ、難しい、ありがたい、問題だ. Vế sau là tính từ đánh giá, không phải hành động.")
g.theory("Vế sau quyết định", "Vế sau là động từ nói, viết, điều tra (話す、書く、研究する) → に関して. Vế sau là thái độ, phản ứng hướng vào ai/cái gì (優しい、厳しい、反対する、答える) → に対して. Vế sau là đánh giá (大切だ、難しい、必要だ) → にとって.")
g.theory("Mẹo thay thế", "Thay được bằng について → に関して. Thay được bằng 〜に向かって (hướng về) → に対して. Thay được bằng 〜の立場から見ると (nhìn từ lập trường) → にとって.")
g.compare("Chức năng", kanshite="Chủ đề", taishite="Đối tượng của hành động, thái độ", nitotte="Lập trường đánh giá")
g.compare("Vế sau thường gặp", kanshite="話す、調べる、質問する、資料", taishite="優しい、厳しい、反対する、答える", nitotte="大切だ、難しい、必要だ")
g.compare("Ví dụ", kanshite="環境問題に関して研究する", taishite="子供に対して厳しい", nitotte="私にとって大切な人")
g.example("kanshite", "この{事件|じけん}に{関|かん}して、{警察|けいさつ}が{調|しら}べている。", "Cảnh sát đang điều tra về vụ việc này.")
g.example("kanshite", "{新|あたら}しい{制度|せいど}に{関|かん}する{説明会|せつめいかい}が{開|ひら}かれた。", "Buổi giải thích về chế độ mới đã được tổ chức.")
g.example("taishite", "{店員|てんいん}はどの{客|きゃく}に{対|たい}しても{親切|しんせつ}だ。", "Nhân viên cửa hàng tử tế với mọi khách hàng.")
g.example("taishite", "{先生|せんせい}の{質問|しつもん}に{対|たい}して、{誰|だれ}も{答|こた}えられなかった。", "Không ai trả lời được câu hỏi của thầy.")
g.example("nitotte", "{留学生|りゅうがくせい}にとって、{日本|にほん}の{夏|なつ}は{暑|あつ}すぎる。", "Đối với du học sinh, mùa hè Nhật quá nóng.")
g.example("nitotte", "この{写真|しゃしん}は{私|わたし}にとって{一番|いちばん}{大切|たいせつ}な{宝物|たからもの}だ。", "Tấm ảnh này là báu vật quý nhất đối với tôi.")
g.tip("関 = 'liên quan' (chủ đề), 対 = 'đối mặt' (hành động hướng vào), とって = 'lấy chỗ đứng của ai' (đánh giá).")
g.mistake("Nói rằng sức khỏe là quan trọng đối với tôi.", "{私|わたし}に{対|たい}して、{健康|けんこう}が{一番|いちばん}{大切|たいせつ}だ。", "{私|わたし}にとって、{健康|けんこう}が{一番|いちばん}{大切|たいせつ}だ。",
          "Vế sau là đánh giá (大切だ) từ lập trường của tôi → にとって.", "nitotte", "taishite")
g.mistake("Thái độ của học sinh với giáo viên.", "{彼|かれ}は{先生|せんせい}にとって{失礼|しつれい}な{態度|たいど}をとった。", "{彼|かれ}は{先生|せんせい}に{対|たい}して{失礼|しつれい}な{態度|たいど}をとった。",
          "Thái độ hướng thẳng vào giáo viên → に対して.", "taishite", "nitotte")
g.mc("kanshite", "Chọn câu dùng đúng.", [
    opt("a", "{日本|にほん}の{経済|けいざい}に{関|かん}してレポートを{書|か}いた。", "kanshite"),
    opt("b", "{日本|にほん}の{経済|けいざい}にとってレポートを{書|か}いた。", "nitotte", "にとって cần vế sau là đánh giá, không đi với 書く."),
    opt("c", "{日本|にほん}の{経済|けいざい}に{対|たい}してレポートを{書|か}いた。", "taishite", "Viết báo cáo về chủ đề, không phải hành động hướng vào nền kinh tế."),
], "a", "Chủ đề của bài báo cáo → に関して.")
g.fill("taishite", "Chọn từ phù hợp để điền vào chỗ trống.", "{部長|ぶちょう}は{部下|ぶか}の{失敗|しっぱい}＿＿＿{厳|きび}しい。",
       "Trưởng phòng nghiêm khắc với lỗi của cấp dưới.", [
           opt("a", "に{対|たい}して", "taishite"),
           opt("b", "にとって", "nitotte", "にとって + 厳しい nghĩa là 'khó khăn đối với', không phải thái độ nghiêm khắc của trưởng phòng."),
           opt("c", "に{関|かん}して", "kanshite", "に関して chỉ chủ đề, không diễn tả thái độ hướng vào."),
       ], "a", "Thái độ nghiêm khắc hướng vào lỗi của cấp dưới → に対して.")
g.error("nitotte", "Chọn phần sai trong câu.", "Đối với người cao tuổi, cầu thang ở nhà ga này thật vất vả.", [
    seg("{高齢者|こうれいしゃ}に{関|かん}して、", "kanshite"), seg("この{駅|えき}の"), seg("{階段|かいだん}は"), seg("とても{大変|たいへん}だ。"),
], 0, "{高齢者|こうれいしゃ}にとって、", "Vế sau là đánh giá (大変だ) từ lập trường người cao tuổi → にとって.")
g.context("taishite", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn kể rằng nhiều cư dân đã phản đối kế hoạch xây đường cao tốc.", [
    opt("a", "{多|おお}くの{住民|じゅうみん}が{高速道路|こうそくどうろ}の{計画|けいかく}に{対|たい}して{反対|はんたい}した。", "taishite"),
    opt("b", "{多|おお}くの{住民|じゅうみん}が{高速道路|こうそくどうろ}の{計画|けいかく}にとって{反対|はんたい}した。", "nitotte", "にとって không đi với hành động phản đối."),
    opt("c", "{多|おお}くの{住民|じゅうみん}が{高速道路|こうそくどうろ}の{計画|けいかく}によって{反対|はんたい}した。", None, "によって là 'do, bởi', sai nghĩa."),
], "a", "Hành động phản đối hướng vào kế hoạch → に対して.")

# 13 --------------------------------------------------------------------------------------------
g = group("oki-ni-goto-ni", "〜おきに và 〜ごとに",
          "Cả hai nói sự việc lặp lại đều đặn. ごとに là 'cứ mỗi (đơn vị)', おきに là 'cứ cách (bỏ qua) bao nhiêu'. Với ngày, '2日おき' khác '2日ごと'; với khoảng thời gian ngắn hay khoảng cách, hai cách nói thường trùng nghĩa.")
g.grammar("oki", "n3g-g157", "〜おきに", "Cứ cách…", "Số lượng ＋ おきに",
          "Đếm phần bị bỏ qua ở giữa: 一日おき = cách một ngày (ngày 1, 3, 5…). Với phút, giờ, mét (5分おき、10メートルおき) thường hiểu như ごとに.")
g.grammar("goto", "n3g-g158", "〜ごとに", "Cứ mỗi…", "N／Số lượng／Vる ＋ ごとに",
          "Mỗi khi đủ một đơn vị thì việc xảy ra: 一日ごと = mỗi ngày; 一週間ごと = mỗi tuần. Còn đi với danh từ, động từ: 家ごとに、会うごとに.")
g.theory("Ngày, tuần, tháng: khác nhau rõ", "一日おきに = ngày có ngày không (1, 3, 5). 一日ごとに = ngày nào cũng (1, 2, 3). 二日おきに = cứ cách hai ngày (1, 4, 7); 二日ごとに = cứ hai ngày một lần (1, 3, 5). Đề thi hay hỏi chỗ này.")
g.theory("Chỗ chỉ ごとに dùng được", "ごとに đi được với danh từ không phải số lượng (国ごとに、クラスごとに) và với động từ (会うごとに: mỗi lần gặp). おきに chỉ đi với số lượng.")
g.compare("一日〜", oki="Cách một ngày (ngày có, ngày không)", goto="Mỗi ngày")
g.compare("Đi với danh từ, động từ", oki="Không", goto="Được: 国ごとに、会うごとに")
g.compare("Khoảng thời gian ngắn", oki="5分おき ≈ cứ 5 phút", goto="5分ごと ≈ cứ 5 phút")
g.example("oki", "この{薬|くすり}は{一日|いちにち}おきに{飲|の}んでください。", "Thuốc này hãy uống cách ngày.")
g.example("oki", "{道|みち}には{十|じゅう}メートルおきに{木|き}が{植|う}えてある。", "Trên đường cứ cách mười mét lại trồng một cây.")
g.example("goto", "オリンピックは{四年|よねん}ごとに{開|ひら}かれる。", "Thế vận hội được tổ chức bốn năm một lần.")
g.example("goto", "{国|くに}ごとに{文化|ぶんか}や{習慣|しゅうかん}が{違|ちが}う。", "Mỗi nước có văn hóa và phong tục khác nhau.")
g.tip("おき = 'đặt (置く) khoảng trống ở giữa'; ごと = 'mỗi (毎) một đơn vị'.")
g.mistake("Bác sĩ dặn uống thuốc ngày nào cũng uống.", "この{薬|くすり}は{一日|いちにち}おきに{飲|の}むこと。{毎日|まいにち}{忘|わす}れないで。", "この{薬|くすり}は{一日|いちにち}ごとに{飲|の}むこと。{毎日|まいにち}{忘|わす}れないで。",
          "毎日 (mỗi ngày) mâu thuẫn với 一日おき (cách ngày). Uống mỗi ngày → 一日ごとに (hay 毎日).", "goto", "oki")
g.mistake("Nói về đặc điểm của từng lớp học.", "クラスおきに{雰囲気|ふんいき}が{違|ちが}う。", "クラスごとに{雰囲気|ふんいき}が{違|ちが}う。",
          "おきに chỉ đi với số lượng; với danh từ như クラス dùng ごとに.", "goto", "oki")
g.mc("oki", "Lịch tập: thứ Hai, thứ Tư, thứ Sáu, Chủ nhật… (ngày có ngày không). Câu nào đúng?", [
    opt("a", "{一日|いちにち}おきにジムに{行|い}っている。", "oki"),
    opt("b", "{一日|いちにち}ごとにジムに{行|い}っている。", "goto", "一日ごと là ngày nào cũng đi."),
    opt("c", "{二日|ふつか}おきにジムに{行|い}っている。", None, "二日おき là cách hai ngày (thứ Hai, thứ Năm, Chủ nhật…), không đúng lịch."),
], "a", "Ngày có ngày không = cách một ngày → 一日おきに.")
g.fill("goto", "Chọn từ phù hợp để điền vào chỗ trống.", "{彼女|かのじょ}は{会|あ}う＿＿＿きれいになっていく。",
       "Mỗi lần gặp, cô ấy lại đẹp hơn.", [
           opt("a", "ごとに", "goto"),
           opt("b", "おきに", "oki", "おきに chỉ đi với số lượng, không đi với động từ."),
       ], "a", "Động từ + ごとに: mỗi lần làm việc đó.")
g.error("goto", "Chọn phần sai trong câu.", "Ở thành phố này, rác được phân loại theo từng khu.", [
    seg("この{市|し}では、"), seg("ゴミの{出|だ}し{方|かた}が"), seg("{地区|ちく}おきに", "oki"), seg("{決|き}められている。"),
], 2, "{地区|ちく}ごとに", "Với danh từ (地区) chỉ dùng ごとに.")
g.context("oki", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn giải thích với bạn mới rằng xe buýt ở bến này cứ 15 phút có một chuyến.", [
    opt("a", "このバス{停|てい}は{十五分|じゅうごふん}おきにバスが{来|き}ますよ。", "oki"),
    opt("b", "このバス{停|てい}は{十五分|じゅうごふん}{間|あいだ}にバスが{来|き}ますよ。", None, "15分間に là 'trong khoảng 15 phút', không nói sự lặp lại đều đặn."),
    opt("c", "このバス{停|てい}は{十五分|じゅうごふん}ばかりバスが{来|き}ますよ。", None, "ばかり sai nghĩa ở đây."),
], "a", "Khoảng thời gian ngắn lặp lại đều đặn → 15分おきに (15分ごとに cũng được).")

# 14 --------------------------------------------------------------------------------------------
g = group("kawarini-nikawatte", "〜かわりに và 〜にかわって",
          "Cả hai nói 'thay cho'. かわり(に) nối với の hoặc động từ, tính từ, và còn nghĩa 'bù lại, đổi lại'. にかわって nối thẳng với danh từ, nghĩa 'thay mặt, thay thế ai/cái gì làm việc gì'.")
g.grammar("kawari", "n3g-g086", "〜かわり（に）", "Thay vì…; bù lại…; đổi lại…", "Vる・Vた・Aい・Aな・N の ＋ かわり（に）",
          "(1) Thay thế: 私のかわりに (thay tôi); (2) Thay vì làm A thì làm B: 映画を見るかわりに本を読む; (3) Có mặt tốt bù cho mặt xấu: 給料が安いかわりに休みが多い.")
g.grammar("nikawatte", "n3g-g087", "〜にかわって／〜にかわり", "Thay cho…, thay mặt…", "N ＋ にかわって／にかわり",
          "Người hay vật N vốn làm việc gì thì nay có người/vật khác làm: 社長にかわって挨拶する、人間にかわってロボットが働く. Trang trọng.")
g.theory("Cách nối khác nhau", "Danh từ: 私のかわりに ／ 私にかわって. Động từ, tính từ: chỉ かわりに (安いかわりに). Thấy の trước chỗ trống → かわりに; thấy N trực tiếp + に → にかわって.")
g.theory("Nghĩa 'bù lại' chỉ có ở かわりに", "Hai mặt tốt xấu bù trừ nhau (この部屋は狭いかわりに家賃が安い), hay đổi công (宿題を手伝うかわりに、昼ご飯をおごって) chỉ dùng かわりに.")
g.compare("Nối với danh từ", kawari="N の ＋ かわりに", nikawatte="N ＋ にかわって")
g.compare("Nối với động từ, tính từ", kawari="Được (安いかわりに)", nikawatte="Không")
g.compare("Nghĩa 'bù lại, đổi lại'", kawari="Có", nikawatte="Không")
g.example("kawari", "{今日|きょう}は{母|はは}のかわりに{私|わたし}が{夕飯|ゆうはん}を{作|つく}った。", "Hôm nay tôi nấu bữa tối thay mẹ.")
g.example("kawari", "この{店|みせ}は{値段|ねだん}が{高|たか}いかわりに、{味|あじ}は{最高|さいこう}だ。", "Quán này đắt nhưng bù lại vị thì tuyệt vời.")
g.example("nikawatte", "{社長|しゃちょう}にかわって、{副社長|ふくしゃちょう}がご{挨拶|あいさつ}いたします。", "Thay mặt giám đốc, phó giám đốc xin phát biểu.")
g.example("nikawatte", "{最近|さいきん}は{手紙|てがみ}にかわって、メールがよく{使|つか}われる。", "Gần đây email được dùng nhiều thay cho thư tay.")
g.tip("Có の → かわりに. Có ý 'đổi lại / bù lại' → かわりに. Trang trọng, thay mặt ai → にかわって.")
g.mistake("Nói căn hộ xa ga nhưng bù lại rộng.", "このアパートは{駅|えき}から{遠|とお}いにかわって、{広|ひろ}い。", "このアパートは{駅|えき}から{遠|とお}いかわりに、{広|ひろ}い。",
          "Tính từ (遠い) và nghĩa bù trừ chỉ đi với かわりに.", "kawari", "nikawatte")
g.mistake("Thay mặt lớp trưởng phát biểu, nối với danh từ.", "{委員長|いいんちょう}のにかわって、{私|わたし}が{話|はな}します。", "{委員長|いいんちょう}にかわって、{私|わたし}が{話|はな}します。",
          "にかわって nối thẳng với danh từ, không có の. (Hoặc nói 委員長のかわりに.)", "nikawatte", "kawari")
g.mc("kawari", "Câu nào có nghĩa 'Tôi giúp bạn làm bài tập, đổi lại bạn mời tôi bữa trưa nhé'?", [
    opt("a", "{宿題|しゅくだい}を{手伝|てつだ}うかわりに、お{昼|ひる}をおごってね。", "kawari"),
    opt("b", "{宿題|しゅくだい}を{手伝|てつだ}うにかわって、お{昼|ひる}をおごってね。", "nikawatte", "にかわって không nối với động từ và không có nghĩa 'đổi lại'."),
    opt("c", "{宿題|しゅくだい}を{手伝|てつだ}うおかげで、お{昼|ひる}をおごってね。", None, "おかげで là 'nhờ…', không phải đổi công."),
], "a", "Đổi công, nối với động từ → かわりに.")
g.fill("nikawatte", "Chọn từ phù hợp để điền vào chỗ trống (văn trang trọng).", "{工場|こうじょう}では、{人間|にんげん}＿＿＿ロボットが{危険|きけん}な{作業|さぎょう}をしている。",
       "Ở nhà máy, robot làm những công việc nguy hiểm thay cho con người.", [
           opt("a", "にかわって", "nikawatte"),
           opt("b", "のかわりで", None, "Không có cách nói のかわりで; phải là のかわりに."),
           opt("c", "かわりに", "kawari", "Danh từ phải có の: 人間のかわりに."),
       ], "a", "Danh từ nối thẳng → 人間にかわって.")
g.error("kawari", "Chọn phần sai trong câu.", "Công việc này lương thấp nhưng bù lại được nghỉ nhiều.", [
    seg("この{仕事|しごと}は"), seg("{給料|きゅうりょう}が{安|やす}いにかわって、", "nikawatte"), seg("{休|やす}みが"), seg("{多|おお}い。"),
], 1, "{給料|きゅうりょう}が{安|やす}いかわりに、", "Nghĩa bù trừ và nối với tính từ (安い) chỉ dùng かわりに.")
g.context("nikawatte", "Chọn câu phù hợp nhất trong tình huống sau.", "Trong buổi lễ, giám đốc vắng mặt và bạn (trưởng phòng) phát biểu thay. Bạn nói câu mở đầu trang trọng.", [
    opt("a", "{本日|ほんじつ}は{社長|しゃちょう}にかわりまして、{私|わたし}がご{挨拶|あいさつ}{申|もう}し{上|あ}げます。", "nikawatte"),
    opt("b", "{本日|ほんじつ}は{社長|しゃちょう}にとって、{私|わたし}がご{挨拶|あいさつ}{申|もう}し{上|あ}げます。", None, "にとって là 'đối với', sai nghĩa."),
    opt("c", "{本日|ほんじつ}は{社長|しゃちょう}が{安|やす}いかわりに、{私|わたし}がご{挨拶|あいさつ}{申|もう}し{上|あ}げます。", "kawari", "Câu vô nghĩa: không có quan hệ bù trừ."),
], "a", "Thay mặt giám đốc, trang trọng → 社長にかわりまして (dạng lịch sự của にかわって).")

# 15 --------------------------------------------------------------------------------------------
g = group("mama-ppanashi", "〜まま và 〜っぱなし",
          "Cả hai nói một trạng thái được giữ nguyên. まま trung tính, nối được với danh từ, tính từ, động từ thể た／ない. っぱなし nối với thân ます, thường mang ý chê: làm rồi để đó không xử lý, hoặc làm liên tục suốt.")
g.grammar("mama", "n3g-g149", "〜まま", "Cứ để nguyên…, giữ nguyên…", "Vた／Vない／N の／Aい／Aな な ＋ まま",
          "Giữ nguyên trạng thái cũ khi làm việc khác: 靴のまま入る、電気をつけたまま寝る、昔のまま. Trung tính, không nhất thiết là chê.")
g.grammar("ppanashi", "n3g-g150", "〜っぱなし", "Cứ để nguyên…; … suốt", "Vます（bỏ ます） ＋ っぱなし",
          "(1) Làm xong rồi bỏ đó, không dọn, không tắt (thường bị chê): 出しっぱなし、開けっぱなし; (2) Một trạng thái kéo dài liên tục: 立ちっぱなし、負けっぱなし.")
g.theory("Cách nối khác nhau", "まま: Vた (つけたまま), Vない (言わないまま), N の (制服のまま), tính từ (若いまま). っぱなし: chỉ thân ます (つけっぱなし、立ちっぱなし). Danh từ hay tính từ đứng trước → chỉ まま.")
g.theory("Sắc thái", "っぱなし gần như luôn có ý phê phán hoặc than phiền (水を出しっぱなしにするな). まま có thể trung tính hay tích cực (昔のままの町並み: phố xá vẫn như xưa).")
g.compare("Cách nối", mama="Vた／Vない／N の／tính từ", ppanashi="Chỉ Vます (bỏ ます)")
g.compare("Sắc thái", mama="Trung tính", ppanashi="Thường chê, than phiền")
g.compare("Nghĩa riêng", mama="Giữ nguyên như cũ (昔のまま)", ppanashi="Làm liên tục suốt (立ちっぱなし)")
g.example("mama", "{日本|にほん}の{家|いえ}には{靴|くつ}のまま{入|はい}ってはいけない。", "Không được để nguyên giày mà vào nhà người Nhật.")
g.example("mama", "{昨日|きのう}はテレビをつけたまま{寝|ね}てしまった。", "Hôm qua tôi lỡ ngủ quên mà vẫn để tivi bật.")
g.example("ppanashi", "{誰|だれ}だ、{水|みず}を{出|だ}しっぱなしにしたのは。", "Ai để nước chảy mà không khóa thế này.")
g.example("ppanashi", "{今日|きょう}は{一日中|いちにちじゅう}{立|た}ちっぱなしで、{足|あし}が{痛|いた}い。", "Hôm nay đứng suốt cả ngày nên đau chân.")
g.tip("Thấy danh từ / tính từ trước chỗ trống → まま. Thấy lời trách 'bỏ đó' hay 'suốt cả ngày' với thân ます → っぱなし.")
g.mistake("Ngợi khen thị trấn vẫn giữ nét xưa.", "この{町|まち}は{百年前|ひゃくねんまえ}っぱなしだ。", "この{町|まち}は{百年前|ひゃくねんまえ}のままだ。",
          "っぱなし không nối với danh từ và mang ý chê; 'vẫn như trăm năm trước' → N のまま.", "mama", "ppanashi")
g.mistake("Kể rằng đã ra ngoài mà để cửa mở.", "ドアを{開|あ}けまま{出|で}かけてしまった。", "ドアを{開|あ}けっぱなしで{出|で}かけてしまった。",
          "Thân ます (開け) không nối với まま; dùng 開けっぱなし, hoặc nói 開けたまま (Vた + まま).", "ppanashi", "mama")
g.mc("mama", "Chọn câu đúng.", [
    opt("a", "{制服|せいふく}のまま{遊|あそ}びに{行|い}った。", "mama"),
    opt("b", "{制服|せいふく}っぱなしで{遊|あそ}びに{行|い}った。", "ppanashi", "っぱなし không nối với danh từ."),
    opt("c", "{制服|せいふく}のっぱなしで{遊|あそ}びに{行|い}った。", None, "Không có cách nói のっぱなし."),
], "a", "Danh từ + の + まま: cứ mặc nguyên đồng phục.")
g.fill("ppanashi", "Chọn từ phù hợp để điền vào chỗ trống.", "{窓|まど}を{開|あ}け＿＿＿にして{出|で}かけたら、{雨|あめ}が{入|はい}ってきた。",
       "Tôi ra ngoài mà để cửa sổ mở toang, thế là mưa hắt vào.", [
           opt("a", "っぱなし", "ppanashi"),
           opt("b", "まま", "mama", "まま cần Vた: 開けたまま; 開け + まま sai cách nối."),
       ], "a", "Thân ます 開け + っぱなし: mở rồi để đó, gây hậu quả xấu.")
g.error("mama", "Chọn phần sai trong câu.", "Ông tôi vẫn khỏe như hồi trẻ.", [
    seg("{祖父|そふ}は"), seg("{今|いま}も"), seg("{若|わか}いっぱなしで", "ppanashi"), seg("{元気|げんき}だ。"),
], 2, "{若|わか}いままで", "Tính từ (若い) chỉ nối với まま; っぱなし chỉ nối với thân ます.")
g.context("ppanashi", "Chọn câu phù hợp nhất trong tình huống sau.", "Mẹ bực mình vì con trai lại để đèn phòng tắm sáng mà không tắt.", [
    opt("a", "また{電気|でんき}をつけっぱなしにして！ちゃんと{消|け}しなさい。", "ppanashi"),
    opt("b", "また{電気|でんき}をつけるままにして！ちゃんと{消|け}しなさい。", "mama", "まま cần Vた: つけたまま; つけるまま sai."),
    opt("c", "また{電気|でんき}をつけかけて！ちゃんと{消|け}しなさい。", None, "つけかける là bật dở, không đúng tình huống."),
], "a", "Bật rồi bỏ đó, có ý trách → つけっぱなし.")

# 16 --------------------------------------------------------------------------------------------
g = group("kakeru-kiru", "〜かける và 〜きる",
          "Hai hậu tố gắn vào thân ます nói về mức độ hoàn thành, nhưng ngược nhau: かける là làm dở, mới bắt đầu, chưa xong; きる là làm đến hết, trọn vẹn.")
g.grammar("kakeru", "n3g-g102", "〜かけ／〜かける", "Đang… dở, làm… giữa chừng; suýt…", "Vます（bỏ ます） ＋ かけ（の N）／かける",
          "Hành động đã bắt đầu nhưng chưa xong: 読みかけの本、食べかけのパン. Với động từ trạng thái tức thời: 死にかける (suýt chết).")
g.grammar("kiru", "n3g-g067", "〜きる", "Làm hết, làm xong hoàn toàn…; không thể… hết", "Vます（bỏ ます） ＋ きる／きれる／きれない",
          "Làm đến tận cùng, không còn lại: 使いきる、走りきる、食べきれない. Dạng きれない: không thể làm hết (vì quá nhiều).")
g.theory("Dở dang hay trọn vẹn", "読みかけの本 = sách đang đọc dở. 読みきった本 = sách đã đọc hết. Một nửa cái bánh bị bỏ lại là 食べかけ; ăn sạch là 食べきる; nhiều quá ăn không hết là 食べきれない.")
g.theory("Dạng thường gặp trong đề", "かけ + の + N (飲みかけのコーヒー); 〜かけたが (vừa định… thì). きれない với số lượng lớn (多すぎて数えきれない). きる còn nhấn mạnh mức độ: 疲れきる (mệt lả).")
g.compare("Mức độ hoàn thành", kakeru="Chưa xong, đang dở", kiru="Xong hoàn toàn, đến hết")
g.compare("Dạng hay gặp", kakeru="〜かけの N、〜かけた", kiru="〜きる、〜きれない")
g.compare("Ví dụ", kakeru="読みかけの本", kiru="一晩で読みきった")
g.example("kakeru", "{机|つくえ}の{上|うえ}に{飲|の}みかけのコーヒーが{置|お}いてある。", "Trên bàn có cốc cà phê đang uống dở.")
g.example("kakeru", "{何|なに}か{言|い}いかけたが、{彼|かれ}は{黙|だま}ってしまった。", "Anh ấy định nói gì đó nhưng rồi im lặng.")
g.example("kiru", "{面白|おもしろ}くて、{一晩|ひとばん}でこの{小説|しょうせつ}を{読|よ}みきった。", "Hay quá nên tôi đọc hết cuốn tiểu thuyết trong một đêm.")
g.example("kiru", "こんなにたくさんの{料理|りょうり}、{一人|ひとり}では{食|た}べきれない。", "Nhiều món thế này, một mình ăn không hết được.")
g.tip("かける: 'treo' (かける) lơ lửng giữa chừng. きる: 'cắt' (切る) đứt đến cùng, không còn gì.")
g.mistake("Kể rằng đã chạy hết quãng đường marathon.", "{初|はじ}めてのマラソンだったが、{最後|さいご}まで{走|はし}りかけた。", "{初|はじ}めてのマラソンだったが、{最後|さいご}まで{走|はし}りきった。",
          "最後まで (đến cuối) cho thấy đã hoàn thành → 走りきった.", "kiru", "kakeru")
g.mistake("Nói về cuốn sách đang đọc dở.", "これは{読|よ}みきりの{本|ほん}だから、まだ{片付|かたづ}けないで。", "これは{読|よ}みかけの{本|ほん}だから、まだ{片付|かたづ}けないで。",
          "Chưa đọc xong nên đừng cất → 読みかけの本.", "kakeru", "kiru")
g.mc("kiru", "Câu nào có nghĩa 'Tôi đã dùng hết tiền tiêu vặt tháng này'?", [
    opt("a", "{今月|こんげつ}のお{小遣|こづか}いを{使|つか}いきった。", "kiru"),
    opt("b", "{今月|こんげつ}のお{小遣|こづか}いを{使|つか}いかけた。", "kakeru", "使いかけた là mới bắt đầu dùng, chưa hết."),
    opt("c", "{今月|こんげつ}のお{小遣|こづか}いを{使|つか}いっぱなしだ。", None, "っぱなし là để đó không xử lý, không có nghĩa 'hết'."),
], "a", "Dùng đến hết → 使いきる.")
g.fill("kakeru", "Chọn từ phù hợp để điền vào chỗ trống.", "{食|た}べ＿＿＿のパンをそのままにしないで、{冷蔵庫|れいぞうこ}に{入|い}れておいて。",
       "Đừng để nguyên ổ bánh mì đang ăn dở, cho vào tủ lạnh đi.", [
           opt("a", "かけ", "kakeru"),
           opt("b", "きり", "kiru", "食べきりのパン không phải 'bánh đang ăn dở'; bánh còn lại nên chưa ăn hết."),
       ], "a", "Ăn dở, còn lại một phần → 食べかけのパン.")
g.error("kiru", "Chọn phần sai trong câu.", "Ở quảng trường có rất nhiều người, đông đến mức không đếm xuể.", [
    seg("{広場|ひろば}には"), seg("{数|かぞ}えかけないほど", "kakeru"), seg("たくさんの{人|ひと}が"), seg("{集|あつ}まっていた。"),
], 1, "{数|かぞ}えきれないほど", "Không đếm hết được vì quá nhiều → 数えきれない.")
g.context("kakeru", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn thấy trên bàn có một chai nước ép mới uống được một nửa và muốn hỏi xem của ai.", [
    opt("a", "この{飲|の}みかけのジュース、{誰|だれ}の？", "kakeru"),
    opt("b", "この{飲|の}みきったジュース、{誰|だれ}の？", "kiru", "飲みきった là đã uống hết, nhưng chai vẫn còn một nửa."),
    opt("c", "この{飲|の}むところのジュース、{誰|だれ}の？", None, "飲むところ là 'sắp uống', không dùng làm định ngữ như thế này."),
], "a", "Uống dở, còn một nửa → 飲みかけのジュース.")

# 17 --------------------------------------------------------------------------------------------
g = group("warini-kuseni", "〜わりに（は） và 〜くせに",
          "Cả hai nói kết quả trái với điều mong đợi từ vế trước. わりに là nhận xét khách quan 'so với… thì lại', có thể khen hoặc chê; くせに là trách móc, coi thường một người cụ thể.")
g.grammar("wari", "n3g-g104", "〜わりに（は）", "So với… thì (lại)…", "V thể thường・Aい・Aな な・N の ＋ わりに（は）",
          "Đánh giá khác với mức thông thường của vế trước: 値段のわりにおいしい (khen), 年のわりに若く見える. Chủ ngữ có thể là người hay vật.")
g.grammar("kuse", "n3g-g105", "〜くせに", "Dù… vậy mà… (trách móc, coi thường)", "V thể thường・Aい・Aな な・N の ＋ くせに",
          "Người nói bực bội, chê trách hành vi của một người: 知っているくせに教えてくれない. Chủ ngữ hai vế là cùng một người. Văn nói, không dùng cho vật, không dùng để khen.")
g.theory("Khách quan hay trách móc", "わりに: so sánh với tiêu chuẩn, có thể là lời khen (このレストランは安いわりにおいしい). くせに: luôn là lời chê, khó chịu với người đó (子供のくせに生意気だ). Câu khen, câu về đồ vật → わりに.")
g.theory("Khi nào không dùng くせに", "Không dùng くせに cho bản thân (người nói tự trách mình thì dùng のに), cho đồ vật, hay trong văn trang trọng. Hai vế của くせに phải cùng chủ ngữ.")
g.compare("Sắc thái", wari="Nhận xét, có thể khen hoặc chê", kuse="Luôn trách móc, coi thường")
g.compare("Chủ ngữ", wari="Người hoặc vật", kuse="Chỉ người, cùng chủ ngữ hai vế")
g.compare("Ví dụ", wari="この店は安いわりにおいしい", kuse="知っているくせに教えてくれない")
g.example("wari", "この{店|みせ}は{値段|ねだん}のわりに{量|りょう}が{多|おお}い。", "Quán này so với giá thì phần ăn lại nhiều.")
g.example("wari", "{祖父|そふ}は{年|とし}のわりに{元気|げんき}だ。", "So với tuổi thì ông tôi lại khỏe.")
g.example("kuse", "{彼|かれ}は{自分|じぶん}では{何|なに}もしないくせに、{文句|もんく}ばかり{言|い}う。", "Anh ta tự mình chẳng làm gì vậy mà toàn phàn nàn.")
g.example("kuse", "{知|し}っているくせに、{教|おし}えてくれないなんてひどい。", "Biết rõ vậy mà không chịu nói cho, thật quá đáng.")
g.tip("わりに = 'so với' (cái cân); くせに = 'vậy mà còn' (ngón tay chỉ trích).")
g.mistake("Khen chiếc máy tính nhẹ so với kích thước.", "このパソコンは{大|おお}きいくせに{軽|かる}い。", "このパソコンは{大|おお}きいわりに{軽|かる}い。",
          "Chủ ngữ là đồ vật và câu mang ý khen → わりに. くせに chỉ dùng để trách người.", "wari", "kuse")
g.mistake("Bực bội vì đồng nghiệp hứa rồi không làm.", "{彼|かれ}は「やる」と{言|い}ったわりに、{何|なに}もしなかった。", "{彼|かれ}は「やる」と{言|い}ったくせに、{何|なに}もしなかった。",
          "Người nói đang trách hành vi của anh ta → くせに. (わりに nghe như nhận xét nhẹ nhàng.)", "kuse", "wari")
g.mc("wari", "Câu nào là lời khen?", [
    opt("a", "{彼|かれ}は{初心者|しょしんしゃ}のわりに、とても{上手|じょうず}だ。", "wari"),
    opt("b", "{彼|かれ}は{初心者|しょしんしゃ}のくせに、とても{上手|じょうず}だ。", "kuse", "くせに mang ý trách, không dùng để khen."),
    opt("c", "{彼|かれ}は{初心者|しょしんしゃ}のせいで、とても{上手|じょうず}だ。", None, "せいで chỉ nguyên nhân của kết quả xấu."),
], "a", "Khen 'so với người mới thì giỏi' → わりに.")
g.fill("kuse", "Chọn từ phù hợp để điền vào chỗ trống (bạn đang trách em trai).", "{自分|じぶん}で{散|ち}らかした＿＿＿、{全然|ぜんぜん}{片付|かたづ}けないんだから。",
       "Tự mình bày bừa ra vậy mà chẳng chịu dọn gì cả.", [
           opt("a", "くせに", "kuse"),
           opt("b", "わりに", "wari", "わりに là so sánh với tiêu chuẩn, không diễn tả sự bực bội."),
       ], "a", "Trách em trai, cùng chủ ngữ hai vế → くせに.")
g.error("wari", "Chọn phần sai trong câu.", "Căn hộ này gần ga, so với vị trí đó thì tiền thuê rẻ.", [
    seg("このアパートは"), seg("{駅|えき}に{近|ちか}いくせに、", "kuse"), seg("{家賃|やちん}が"), seg("{安|やす}い。"),
], 1, "{駅|えき}に{近|ちか}いわりに、", "Chủ ngữ là căn hộ (vật) và câu là nhận xét tích cực → わりに.")
g.context("kuse", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn bực mình vì người bạn biết rõ giờ hẹn mà vẫn đến muộn một tiếng.", [
    opt("a", "{時間|じかん}を{知|し}っていたくせに、どうして{一時間|いちじかん}も{遅|おく}れたの？", "kuse"),
    opt("b", "{時間|じかん}を{知|し}っていたわりに、どうして{一時間|いちじかん}も{遅|おく}れたの？", "wari", "わりに là nhận xét so sánh, không thể hiện sự bực bội như tình huống."),
    opt("c", "{時間|じかん}を{知|し}っていたおかげで、どうして{一時間|いちじかん}も{遅|おく}れたの？", None, "おかげで dùng cho kết quả tốt."),
], "a", "Trách móc người bạn → くせに.")

# 18 --------------------------------------------------------------------------------------------
g = group("karaniwa-karatoitte", "〜からには và 〜からといって",
          "Hình thức gần giống nhau nhưng nghĩa khác: からには là 'một khi đã… thì phải…' (quyết tâm, nghĩa vụ); からといって là 'không phải cứ vì… mà…' (vế sau phủ định).")
g.grammar("karaniwa", "n3g-g066", "〜からには", "Một khi đã… thì phải…", "V thể thường ＋ からには",
          "Vế trước là sự thật hay quyết định đã có; vế sau là ý chí, nghĩa vụ, lời khuyên mạnh: 〜なければならない、〜つもりだ、〜べきだ、〜たい.")
g.grammar("karatoitte", "n3g-g091", "〜からといって", "Không phải cứ vì… mà…", "Câu (thể thường) ＋ からといって ＋ …（phủ định）",
          "Phủ nhận một suy luận thông thường: vế sau thường là 〜わけではない、〜とは限らない、〜てはいけない.")
g.theory("Nhìn vế sau", "Vế sau là quyết tâm, nghĩa vụ (最後までやる、頑張らなければならない) → からには. Vế sau phủ định một suy luận (〜わけではない、〜とは限らない) → からといって.")
g.theory("Mẹo nhớ hình thức", "からには = から (vì) + には (thì nhất định): đã vì thế thì nhất định phải. からといって = 'dù nói là vì…' → tiếp theo là lời phản bác.")
g.compare("Nghĩa", karaniwa="Một khi đã… thì phải", karatoitte="Không phải cứ vì… mà")
g.compare("Vế sau", karaniwa="Ý chí, nghĩa vụ", karatoitte="Phủ định: わけではない、とは限らない")
g.compare("Ví dụ", karaniwa="約束したからには守る", karatoitte="高いからといっていいとは限らない")
g.example("karaniwa", "{試合|しあい}に{出|で}るからには、{絶対|ぜったい}に{勝|か}ちたい。", "Một khi đã ra thi đấu thì nhất định tôi muốn thắng.")
g.example("karaniwa", "{約束|やくそく}したからには、{守|まも}らなければならない。", "Một khi đã hứa thì phải giữ lời.")
g.example("karatoitte", "{日本人|にほんじん}だからといって、{敬語|けいご}が{上手|じょうず}なわけではない。", "Không phải cứ là người Nhật thì giỏi kính ngữ.")
g.example("karatoitte", "{値段|ねだん}が{高|たか}いからといって、いい{物|もの}だとは{限|かぎ}らない。", "Không phải cứ đắt là đồ tốt.")
g.tip("からには → 'thì PHẢI'; からといって → 'cũng KHÔNG hẳn'.")
g.mistake("Khuyên: đã vào đại học thì phải học chăm.", "{大学|だいがく}に{入|はい}ったからといって、しっかり{勉強|べんきょう}するべきだ。", "{大学|だいがく}に{入|はい}ったからには、しっかり{勉強|べんきょう}するべきだ。",
          "Vế sau là nghĩa vụ (勉強するべきだ) → からには.", "karaniwa", "karatoitte")
g.mistake("Nói: không phải cứ rảnh là vui.", "{暇|ひま}だからには、{楽|たの}しいわけではない。", "{暇|ひま}だからといって、{楽|たの}しいわけではない。",
          "Vế sau わけではない phủ định suy luận → からといって.", "karatoitte", "karaniwa")
g.mc("karatoitte", "Chọn câu đúng.", [
    opt("a", "{一度|いちど}{失敗|しっぱい}したからといって、あきらめることはない。", "karatoitte"),
    opt("b", "{一度|いちど}{失敗|しっぱい}したからには、あきらめることはない。", "karaniwa", "からには cần vế sau là quyết tâm, nghĩa vụ; ことはない là phủ định suy luận."),
    opt("c", "{一度|いちど}{失敗|しっぱい}したおかげで、あきらめることはない。", None, "おかげで không hợp với nghĩa câu."),
], "a", "Không phải cứ thất bại một lần mà phải bỏ cuộc → からといって.")
g.fill("karaniwa", "Chọn từ phù hợp để điền vào chỗ trống.", "リーダーに{選|えら}ばれた＿＿＿、{責任|せきにん}を{持|も}ってやります。",
       "Một khi đã được chọn làm trưởng nhóm, tôi sẽ làm với tinh thần trách nhiệm.", [
           opt("a", "からには", "karaniwa"),
           opt("b", "からといって", "karatoitte", "Vế sau là quyết tâm, không phải phủ định."),
       ], "a", "Vế sau là quyết tâm → からには.")
g.error("karatoitte", "Chọn phần sai trong câu.", "Không phải cứ nhiều tiền là hạnh phúc.", [
    seg("お{金|かね}が"), seg("たくさんあるからには、", "karaniwa"), seg("{幸|しあわ}せだとは"), seg("{限|かぎ}らない。"),
], 1, "たくさんあるからといって、", "Vế sau とは限らない phủ định suy luận → からといって.")
g.context("karaniwa", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn đã quyết định du học Nhật và nói với bố mẹ về quyết tâm của mình.", [
    opt("a", "{日本|にほん}に{留学|りゅうがく}するからには、N1に{合格|ごうかく}するまで{頑張|がんば}ります。", "karaniwa"),
    opt("b", "{日本|にほん}に{留学|りゅうがく}するからといって、N1に{合格|ごうかく}するまで{頑張|がんば}ります。", "karatoitte", "からといって cần vế sau phủ định."),
    opt("c", "{日本|にほん}に{留学|りゅうがく}するくせに、N1に{合格|ごうかく}するまで{頑張|がんば}ります。", None, "くせに là trách móc, không hợp."),
], "a", "Quyết tâm sau một quyết định đã có → からには.")

# 19 --------------------------------------------------------------------------------------------
g = group("kaneru-kanenai", "〜かねる và 〜かねない",
          "Chỉ khác nhau một chữ ない nhưng nghĩa gần như ngược: かねる là 'khó có thể (làm)' – lời từ chối lịch sự; かねない là 'có thể sẽ (xảy ra điều xấu)' – lo ngại.")
g.grammar("kaneru", "n3g-g163", "〜かねる", "Khó có thể…, không thể…", "Vます（bỏ ます） ＋ かねる",
          "Người nói muốn nhưng không thể làm vì hoàn cảnh, lập trường; cách từ chối lịch sự, hay dùng trong kinh doanh: お答えしかねます、賛成しかねる.")
g.grammar("kanenai", "n3g-g164", "〜かねない", "Có thể… (lo ngại điều xấu)", "Vます（bỏ ます） ＋ かねない",
          "Khả năng xảy ra kết quả xấu: 事故を起こしかねない、病気になりかねない. Không dùng cho điều tốt.")
g.theory("Ngược nghĩa dù giống hình", "かねる (khẳng định) = không làm được; かねない (phủ định) = có thể xảy ra. Mẹo: かねない là 'không thể nói là không xảy ra' → có thể xảy ra.")
g.theory("Chủ ngữ và sắc thái", "かねる: chủ ngữ là người nói (hoặc phía người nói), từ chối lịch sự. かねない: thường nói về hành vi nguy hiểm, tình huống xấu có thể xảy ra cho ai đó, cảnh báo.")
g.compare("Nghĩa", kaneru="Khó có thể làm (từ chối)", kanenai="Có thể xảy ra (điều xấu)")
g.compare("Bối cảnh", kaneru="Kinh doanh, lịch sự", kanenai="Cảnh báo, lo ngại")
g.compare("Ví dụ", kaneru="その質問にはお答えしかねます", kanenai="スピードを出しすぎると事故を起こしかねない")
g.example("kaneru", "{申|もう}し{訳|わけ}ございませんが、その{件|けん}についてはお{答|こた}えしかねます。", "Thành thật xin lỗi, về việc đó chúng tôi khó có thể trả lời.")
g.example("kaneru", "その{意見|いけん}には{賛成|さんせい}しかねる。", "Tôi khó có thể tán thành ý kiến đó.")
g.example("kanenai", "そんなに{無理|むり}をしたら、{体|からだ}を{壊|こわ}しかねない。", "Gắng sức như thế thì có thể hỏng sức khỏe đấy.")
g.example("kanenai", "{寝不足|ねぶそく}で{運転|うんてん}すると、{事故|じこ}を{起|お}こしかねない。", "Thiếu ngủ mà lái xe thì có thể gây tai nạn.")
g.tip("かねる → 'không làm được' (lịch sự); かねない → 'có khi lại xảy ra' (nguy hiểm).")
g.mistake("Cảnh báo về việc đi xe đạp vừa đi vừa xem điện thoại.", "スマホを{見|み}ながら{自転車|じてんしゃ}に{乗|の}ると、{危|あぶ}ないことになりかねる。", "スマホを{見|み}ながら{自転車|じてんしゃ}に{乗|の}ると、{危|あぶ}ないことになりかねない。",
          "Cảnh báo kết quả xấu có thể xảy ra → かねない.", "kanenai", "kaneru")
g.mistake("Nhân viên lịch sự từ chối yêu cầu đổi hàng đã dùng.", "{使用済|しようず}みの{商品|しょうひん}の{交換|こうかん}は、お{受|う}けしかねないです。", "{使用済|しようず}みの{商品|しょうひん}の{交換|こうかん}は、お{受|う}けしかねます。",
          "Từ chối lịch sự → かねます. かねない nghĩa là 'có thể sẽ nhận', ngược ý.", "kaneru", "kanenai")
g.mc("kanenai", "Câu nào là lời cảnh báo?", [
    opt("a", "このままでは、{会社|かいしゃ}は{倒産|とうさん}しかねない。", "kanenai"),
    opt("b", "このままでは、{会社|かいしゃ}は{倒産|とうさん}しかねる。", "kaneru", "かねる là 'khó có thể (làm)', không dùng để cảnh báo."),
    opt("c", "このままでは、{会社|かいしゃ}は{倒産|とうさん}するわけにはいかない。", None, "わけにはいかない là không thể làm vì lý do đạo lý."),
], "a", "Lo ngại kết quả xấu có thể xảy ra → かねない.")
g.fill("kaneru", "Chọn từ phù hợp để điền vào chỗ trống (nhân viên trả lời khách).", "{個人|こじん}{情報|じょうほう}ですので、お{教|おし}えし＿＿＿。",
       "Vì là thông tin cá nhân nên chúng tôi khó có thể cho biết.", [
           opt("a", "かねます", "kaneru"),
           opt("b", "かねません", "kanenai", "かねません (dạng lịch sự của かねない) là 'có thể sẽ cho biết', ngược ý từ chối."),
       ], "a", "Từ chối lịch sự → かねます.")
g.error("kanenai", "Chọn phần sai trong câu.", "Nếu cứ để như vậy, cảm lạnh có thể chuyển thành bệnh nặng.", [
    seg("{風邪|かぜ}を"), seg("そのままにしておくと、"), seg("{大|おお}きな{病気|びょうき}に"), seg("なりかねる。", "kaneru"),
], 3, "なりかねない。", "Kết quả xấu có thể xảy ra → なりかねない.")
g.context("kaneru", "Chọn câu phù hợp nhất trong tình huống sau.", "Khách hàng yêu cầu giảm giá 50%. Bạn là nhân viên và muốn từ chối một cách lịch sự.", [
    opt("a", "{申|もう}し{訳|わけ}ありませんが、そのご{要望|ようぼう}にはお{応|こた}えしかねます。", "kaneru"),
    opt("b", "{申|もう}し{訳|わけ}ありませんが、そのご{要望|ようぼう}にはお{応|こた}えしかねません。", "kanenai", "かねません nghĩa là có thể sẽ đáp ứng, ngược ý."),
    opt("c", "{申|もう}し{訳|わけ}ありませんが、そのご{要望|ようぼう}にはお{応|こた}えするはずです。", None, "はずです là suy đoán 'chắc là sẽ đáp ứng'."),
], "a", "Từ chối lịch sự trong kinh doanh → お応えしかねます.")

# 20 --------------------------------------------------------------------------------------------
g = group("zuniwairarenai-naiwakeniwaikanai", "〜ずにはいられない và 〜ないわけにはいかない",
          "Cả hai dịch là 'không thể không…', nhưng ずにはいられない là không kìm được cảm xúc, phản ứng tự nhiên; ないわけにはいかない là buộc phải làm vì nghĩa vụ, hoàn cảnh xã hội.")
g.grammar("zuniwa", "n3g-g143", "〜ずにはいられない", "Không thể không…, không kìm được mà…", "Vない（bỏ ない） ＋ ずにはいられない（する → せずにはいられない）",
          "Cảm xúc hay phản ứng tự nhiên trào ra, người nói không kìm nén được: 笑わずにはいられない、泣かずにはいられない、心配せずにはいられない.")
g.grammar("naiwake", "n3g-g085", "〜ないわけにはいかない", "Không thể không…, buộc phải…", "Vない ＋ わけにはいかない",
          "Dù không muốn, người nói vẫn phải làm vì trách nhiệm, lễ nghĩa, hoàn cảnh: 行かないわけにはいかない、払わないわけにはいかない.")
g.theory("Cảm xúc hay nghĩa vụ", "Lý do đến từ bên trong (buồn cười quá, cảm động quá, lo quá) → ずにはいられない. Lý do đến từ bên ngoài (là tiệc cưới của sếp, đã hứa, là quy định) → ないわけにはいかない.")
g.theory("Cách nối dễ sai", "ずにはいられない: bỏ ない rồi thêm ず (笑わない → 笑わず); する → せず (không phải しず). ないわけにはいかない: giữ nguyên Vない (行かない + わけにはいかない).")
g.compare("Nguồn gốc", zuniwa="Cảm xúc, phản ứng tự nhiên", naiwake="Nghĩa vụ, hoàn cảnh xã hội")
g.compare("Người nói có muốn không", zuniwa="Tự nhiên muốn, không nhịn được", naiwake="Thường không muốn nhưng phải làm")
g.compare("Ví dụ", zuniwa="あまりにおかしくて笑わずにはいられない", naiwake="上司の結婚式なので行かないわけにはいかない")
g.example("zuniwa", "その{映画|えいが}は{悲|かな}しくて、{泣|な}かずにはいられなかった。", "Bộ phim buồn đến mức tôi không kìm được nước mắt.")
g.example("zuniwa", "{子供|こども}の{帰|かえ}りが{遅|おそ}いと、{心配|しんぱい}せずにはいられない。", "Con về muộn là tôi không thể không lo.")
g.example("naiwake", "{部長|ぶちょう}に{誘|さそ}われたので、{飲|の}み{会|かい}に{行|い}かないわけにはいかない。", "Được trưởng phòng mời nên tôi không thể không đi nhậu.")
g.example("naiwake", "{明日|あした}は{試験|しけん}だから、{今夜|こんや}は{勉強|べんきょう}しないわけにはいかない。", "Mai thi rồi nên tối nay buộc phải học.")
g.tip("ずにはいられない: 'lòng không yên' (いられない = không ngồi yên được). ないわけにはいかない: 'lý lẽ không cho phép' (わけ = lý do).")
g.mistake("Kể rằng bộ phim hài quá nên không nhịn được cười.", "あまりにおかしくて、{笑|わら}わないわけにはいかなかった。", "あまりにおかしくて、{笑|わら}わずにはいられなかった。",
          "Cười vì buồn cười là phản ứng tự nhiên → ずにはいられない. ないわけにはいかない nghe như bị bắt phải cười.", "zuniwa", "naiwake")
g.mistake("Phải dự đám cưới của đồng nghiệp thân dù bận.", "{忙|いそが}しいが、{同僚|どうりょう}の{結婚式|けっこんしき}だから{出席|しゅっせき}せずにはいられない。", "{忙|いそが}しいが、{同僚|どうりょう}の{結婚式|けっこんしき}だから{出席|しゅっせき}しないわけにはいかない。",
          "Lý do là quan hệ xã hội (đám cưới đồng nghiệp), không phải cảm xúc → ないわけにはいかない.", "naiwake", "zuniwa")
g.mc("zuniwa", "Câu nào diễn tả cảm xúc không kìm được?", [
    opt("a", "{彼|かれ}の{話|はなし}を{聞|き}いて、{感動|かんどう}せずにはいられなかった。", "zuniwa"),
    opt("b", "{彼|かれ}の{話|はなし}を{聞|き}いて、{感動|かんどう}しないわけにはいかなかった。", "naiwake", "ないわけにはいかない là bị buộc phải làm, không hợp với cảm xúc tự nhiên."),
    opt("c", "{彼|かれ}の{話|はなし}を{聞|き}いて、{感動|かんどう}しずにはいられなかった。", None, "Sai cách chia: する → せず, không phải しず."),
], "a", "Cảm động trào ra tự nhiên → せずにはいられない.")
g.fill("naiwake", "Chọn cách nói phù hợp để điền vào chỗ trống.", "{自分|じぶん}で{引|ひ}き{受|う}けた{仕事|しごと}だから、{最後|さいご}までやら＿＿＿。",
       "Vì là việc tự mình nhận nên không thể không làm đến cùng.", [
           opt("a", "ないわけにはいかない", "naiwake"),
           opt("b", "ずにはいられない", "zuniwa", "Lý do là trách nhiệm đã nhận, không phải cảm xúc không kìm được."),
       ], "a", "Trách nhiệm với việc đã nhận → やらないわけにはいかない.")
g.error("zuniwa", "Chọn phần sai trong câu.", "Thấy con mèo bị bỏ rơi ướt mưa, tôi không thể không giúp.", [
    seg("{雨|あめ}に{濡|ぬ}れた"), seg("{捨|す}て{猫|ねこ}を{見|み}て、"), seg("{助|たす}けないわけには", "naiwake"), seg("いられなかった。"),
], 2, "{助|たす}けずには", "Đi với いられない phải là Vず + には: 助けずにはいられない (thương xót, không kìm được).")
g.context("naiwake", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn không thích đám đông, nhưng là trưởng ban tổ chức nên phải có mặt ở lễ hội của trường.", [
    opt("a", "{実行|じっこう}{委員長|いいんちょう}だから、{学園祭|がくえんさい}に{行|い}かないわけにはいかない。", "naiwake"),
    opt("b", "{実行|じっこう}{委員長|いいんちょう}だから、{学園祭|がくえんさい}に{行|い}かずにはいられない。", "zuniwa", "ずにはいられない nghe như bạn rất háo hức muốn đi, trái với tình huống."),
    opt("c", "{実行|じっこう}{委員長|いいんちょう}だから、{学園祭|がくえんさい}に{行|い}くわけがない。", None, "わけがない là 'không đời nào đi', sai nghĩa."),
], "a", "Nghĩa vụ của trưởng ban, dù không thích → ないわけにはいかない.")

# 21 --------------------------------------------------------------------------------------------
g = group("nimotozuite-womotoni", "〜にもとづいて và 〜をもとに",
          "Cả hai nghĩa 'dựa trên'. にもとづいて là làm theo, không lệch khỏi một căn cứ, quy tắc (luật, dữ liệu, kế hoạch); をもとに là lấy một thứ làm chất liệu, gốc rồi tạo ra cái mới (phim dựa trên tiểu thuyết).")
g.grammar("motozuite", "n3g-g093", "〜にもとづいて／〜にもとづき", "Dựa trên…, căn cứ vào…", "N ＋ にもとづいて／にもとづき／にもとづく N",
          "Căn cứ được tuân theo, làm nền cho phán đoán, hành động: 法律に基づいて判断する、データに基づく分析、計画に基づいて進める.")
g.grammar("motoni", "n3g-g094", "〜をもとに（して）", "Dựa vào… (làm chất liệu, gốc)", "N ＋ をもとに（して）／をもとにした N",
          "Lấy N làm nguyên liệu để sáng tác, tạo ra cái mới: 実話をもとにした映画、アンケートをもとに商品を開発する.")
g.theory("Tuân theo hay lấy làm chất liệu", "Hành động là phán đoán, xử lý, tiến hành theo quy định → にもとづいて (規則に基づいて処分する). Hành động là tạo ra cái mới (作る、書く、映画化する) → をもとに (経験をもとに小説を書く).")
g.theory("Trợ từ đi kèm", "にもとづいて đi với に; をもとに đi với を. Lỗi hay gặp là lẫn trợ từ: ×をもとづいて、×にもとに.")
g.compare("Ý nghĩa", motozuite="Căn cứ, tuân theo", motoni="Chất liệu, gốc để tạo ra")
g.compare("Danh từ hay đi trước", motozuite="法律、規則、データ、計画、事実", motoni="小説、実話、経験、アンケート")
g.compare("Trợ từ", motozuite="に ＋ もとづいて", motoni="を ＋ もとに")
g.example("motozuite", "{会社|かいしゃ}の{規則|きそく}に{基|もと}づいて、{処分|しょぶん}が{決|き}められた。", "Hình thức xử lý được quyết định dựa trên nội quy công ty.")
g.example("motozuite", "この{報告書|ほうこくしょ}は{調査|ちょうさ}データに{基|もと}づいて{書|か}かれている。", "Bản báo cáo này được viết căn cứ vào dữ liệu điều tra.")
g.example("motoni", "この{映画|えいが}は{実際|じっさい}にあった{話|はなし}をもとに{作|つく}られた。", "Bộ phim này được làm dựa trên câu chuyện có thật.")
g.example("motoni", "お{客様|きゃくさま}の{意見|いけん}をもとに、{新|あたら}しい{商品|しょうひん}を{開発|かいはつ}した。", "Chúng tôi phát triển sản phẩm mới dựa trên ý kiến khách hàng.")
g.tip("基づく: đứng 'trên nền móng' (基) – không được lệch. もとに: lấy 'nguyên liệu gốc' (元) – rồi biến thành cái khác.")
g.mistake("Bộ phim hoạt hình được làm dựa trên một cuốn truyện tranh.", "このアニメは{人気|にんき}{漫画|まんが}に{基|もと}づいて{作|つく}られた。", "このアニメは{人気|にんき}{漫画|まんが}をもとに{作|つく}られた。",
          "Lấy truyện tranh làm chất liệu để tạo ra phim → をもとに.", "motoni", "motozuite")
g.mistake("Thẩm phán phán quyết theo luật.", "{裁判官|さいばんかん}は{法律|ほうりつ}をもとに{判断|はんだん}する。", "{裁判官|さいばんかん}は{法律|ほうりつ}に{基|もと}づいて{判断|はんだん}する。",
          "Phán quyết tuân theo luật, không phải lấy luật làm chất liệu sáng tác → に基づいて.", "motozuite", "motoni")
g.mc("motozuite", "Câu nào tự nhiên nhất?", [
    opt("a", "{計画|けいかく}に{基|もと}づいて、{工事|こうじ}を{進|すす}める。", "motozuite"),
    opt("b", "{計画|けいかく}をもとづいて、{工事|こうじ}を{進|すす}める。", None, "Sai trợ từ: phải là に基づいて."),
    opt("c", "{計画|けいかく}にもとにして、{工事|こうじ}を{進|すす}める。", None, "Sai trợ từ: phải là をもとにして."),
], "a", "Tiến hành theo đúng kế hoạch → 計画に基づいて.")
g.fill("motoni", "Chọn từ phù hợp để điền vào chỗ trống.", "{祖母|そぼ}から{聞|き}いた{戦争|せんそう}の{話|はなし}＿＿＿、{小説|しょうせつ}を{書|か}いた。",
       "Tôi viết tiểu thuyết dựa trên chuyện chiến tranh nghe từ bà.", [
           opt("a", "をもとに", "motoni"),
           opt("b", "に{基|もと}づいて", "motozuite", "Chuyện bà kể là chất liệu để sáng tác, không phải căn cứ phải tuân theo."),
       ], "a", "Lấy câu chuyện làm chất liệu sáng tác tiểu thuyết → をもとに.")
g.error("motozuite", "Chọn phần sai trong câu.", "Thuế được tính căn cứ theo thu nhập năm trước.", [
    seg("{税金|ぜいきん}は"), seg("{前|まえ}の{年|とし}の{収入|しゅうにゅう}を"), seg("{基|もと}づいて", "motozuite"), seg("{計算|けいさん}される。"),
], 1, "{前|まえ}の{年|とし}の{収入|しゅうにゅう}に", "基づく đi với trợ từ に: 収入に基づいて.")
g.context("motoni", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn giới thiệu một trò chơi mà nhóm bạn tự làm, lấy cảm hứng và chất liệu từ truyền thuyết cổ của Nhật.", [
    opt("a", "このゲームは{日本|にほん}の{昔話|むかしばなし}をもとにして{作|つく}りました。", "motoni"),
    opt("b", "このゲームは{日本|にほん}の{昔話|むかしばなし}に{対|たい}して{作|つく}りました。", None, "に対して là 'đối với, hướng tới', sai nghĩa."),
    opt("c", "このゲームは{日本|にほん}の{昔話|むかしばなし}を{基|もと}づいて{作|つく}りました。", None, "Sai trợ từ (を基づいて) và sai sắc thái: đây là sáng tác từ chất liệu."),
], "a", "Lấy truyện cổ làm chất liệu sáng tác → をもとにして.")

# 22 --------------------------------------------------------------------------------------------
g = group("toshite-nitotte", "〜として và 〜にとって",
          "として nói 'với tư cách, vai trò là'; にとって nói 'đối với (từ lập trường của) ai'. Người học hay nhầm vì cả hai đều có thể dịch là 'đối với' hoặc 'là'.")
g.grammar("toshite", "n3g-g090", "〜として", "Với tư cách là…, như là…", "N ＋ として（は／も／の）",
          "Nêu vai trò, tư cách, danh nghĩa của người hay vật khi hành động: 留学生として来日する、記念として贈る、趣味として楽しむ.")
g.grammar("nitotte", "n3g-g037", "〜にとって", "Đối với… (từ lập trường của…)", "N ＋ にとって",
          "Đánh giá từ góc nhìn của N; vế sau là tính từ đánh giá: 大切だ、難しい、ありがたい.")
g.theory("Vai trò hay góc nhìn", "Trả lời câu hỏi 'ở vai trò gì?' → として (医者として働く: làm việc với tư cách bác sĩ). Trả lời câu hỏi 'theo đánh giá của ai?' → にとって (医者にとって大切なこと: điều quan trọng đối với bác sĩ).")
g.theory("Vế sau", "として thường đi với động từ hành động hoặc danh từ (として働く、としての責任). にとって đi với tính từ đánh giá (にとって難しい、大事だ), không đi với động từ hành động.")
g.compare("Ý nghĩa", toshite="Tư cách, vai trò", nitotte="Lập trường đánh giá")
g.compare("Vế sau", toshite="Động từ hành động, の + N", nitotte="Tính từ đánh giá")
g.compare("Ví dụ", toshite="通訳として働く", nitotte="私にとって大切な人")
g.example("toshite", "{彼|かれ}は{通訳|つうやく}として{会議|かいぎ}に{参加|さんか}した。", "Anh ấy tham dự cuộc họp với tư cách phiên dịch.")
g.example("toshite", "これは{旅行|りょこう}の{記念|きねん}として{買|か}ったお{皿|さら}です。", "Đây là chiếc đĩa tôi mua làm kỷ niệm chuyến du lịch.")
g.example("nitotte", "{子供|こども}にとって、{遊|あそ}びは{勉強|べんきょう}と{同|おな}じくらい{大切|たいせつ}だ。", "Đối với trẻ con, vui chơi quan trọng ngang với học.")
g.example("nitotte", "{外国人|がいこくじん}にとって、{漢字|かんじ}を{覚|おぼ}えるのは{大変|たいへん}だ。", "Đối với người nước ngoài, nhớ Kanji thật vất vả.")
g.tip("として = 'đội mũ' một vai trò rồi hành động; にとって = 'đứng ở chỗ' của ai đó mà nhìn.")
g.mistake("Nói mình đến Nhật với tư cách du học sinh.", "{私|わたし}は{留学生|りゅうがくせい}にとって{日本|にほん}に{来|き}ました。", "{私|わたし}は{留学生|りゅうがくせい}として{日本|にほん}に{来|き}ました。",
          "Tư cách khi hành động (来ました) → として.", "toshite", "nitotte")
g.mistake("Nói gia đình quan trọng nhất đối với mình.", "{私|わたし}として、{家族|かぞく}が{一番|いちばん}{大切|たいせつ}だ。", "{私|わたし}にとって、{家族|かぞく}が{一番|いちばん}{大切|たいせつ}だ。",
          "Đánh giá 大切だ từ lập trường của tôi → にとって. (私としては là 'về phần tôi thì (ý kiến)'.)", "nitotte", "toshite")
g.mc("toshite", "Chọn câu đúng.", [
    opt("a", "{趣味|しゅみ}として{写真|しゃしん}を{撮|と}っている。", "toshite"),
    opt("b", "{趣味|しゅみ}にとって{写真|しゃしん}を{撮|と}っている。", "nitotte", "にとって không đi với hành động 撮っている."),
    opt("c", "{趣味|しゅみ}に{対|たい}して{写真|しゃしん}を{撮|と}っている。", None, "に対して là hướng tới đối tượng, sai nghĩa."),
], "a", "Chụp ảnh như một sở thích (vai trò) → として.")
g.fill("nitotte", "Chọn từ phù hợp để điền vào chỗ trống.", "この{仕事|しごと}は、{経験|けいけん}のない{人|ひと}＿＿＿{難|むずか}しすぎる。",
       "Công việc này quá khó đối với người chưa có kinh nghiệm.", [
           opt("a", "にとって", "nitotte"),
           opt("b", "として", "toshite", "Vế sau là đánh giá 難しすぎる, không phải hành động theo vai trò."),
       ], "a", "Đánh giá 'quá khó' từ góc nhìn người chưa có kinh nghiệm → にとって.")
g.error("toshite", "Chọn phần sai trong câu.", "Cô ấy làm việc ở bệnh viện với tư cách y tá.", [
    seg("{彼女|かのじょ}は"), seg("{看護師|かんごし}にとって", "nitotte"), seg("{病院|びょういん}で"), seg("{働|はたら}いている。"),
], 1, "{看護師|かんごし}として", "Tư cách khi làm việc → として.")
g.context("nitotte", "Chọn câu phù hợp nhất trong tình huống sau.", "Bạn nói với bạn bè rằng chiếc đồng hồ cũ của ông để lại là vật vô giá với bạn.", [
    opt("a", "この{時計|とけい}は{私|わたし}にとって{宝物|たからもの}なんだ。", "nitotte"),
    opt("b", "この{時計|とけい}は{私|わたし}として{宝物|たからもの}なんだ。", "toshite", "私として không diễn tả góc nhìn đánh giá."),
    opt("c", "この{時計|とけい}は{私|わたし}に{関|かん}して{宝物|たからもの}なんだ。", None, "に関して là 'về chủ đề', sai nghĩa."),
], "a", "Đánh giá từ lập trường của tôi → にとって.")
