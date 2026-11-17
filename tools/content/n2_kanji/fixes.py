"""Hand-made data for the N2 kanji deck, keyed by Anki note id (stable across exports).

FROM_IMAGE: cards whose reading or meaning is only in the card's illustration. Typed from the
illustration (the large heading of the image: reading, then the short meaning), nothing added.
  kanji cards: note id -> meaning
  word cards:  note id -> (reading, meaning)
"""

# Kanji cards: "HÁN VIỆT" + image only.
KANJI_MEANING_FROM_IMAGE = {
    1784721245910: "Khiêm tốn, nhún nhường",                              # 謙
    1784723135927: "Khẩn trương, căng thẳng, gấp gáp, chặt chẽ",          # 緊
    1784723758460: "Giảng, thuyết, bài giảng, đàm phán",                  # 講
    1784725047438: "Mệt mỏi, kiệt sức",                                   # 疲
    1785899663263: "Chở, vận chuyển, chuyển đi",                          # 輸
    1787624420006: "Mỏ khoáng, khoáng sản, mỏ quặng",                     # 鉱
    1787624506250: "Kẻ địch, đối thủ, quân thù",                          # 敵
    1787624766065: "Kiên cố, cứng, cố định",                              # 固
    1787625438014: "Tạp chí, ghi chép, tạp san",                          # 誌
    1787626321859: "Bão, cuồng phong",                                    # 嵐
    1787626359763: "Ký túc xá, nhà ở tập thể",                            # 寮
    1787626394843: "Chữa bệnh, điều trị",                                 # 療
    1787626471546: "Đồng liêu, đồng nghiệp, bạn cùng làm việc, bạn cùng phòng (ký túc xá)",  # 僚
    1787626508895: "Góc, góc cạnh, nơi khuất",                            # 隅
    1787626606335: "Ngu, ngốc, dại dột",                                  # 愚
    1787626650987: "Trán, số tiền, hạn mức, khung tranh",                 # 額
}

# Word cards: Hán Việt (sometimes) + image only.
WORD_FROM_IMAGE = {
    1784636403261: ("いつだつする", "Lệch hướng, thoát khỏi, trốn thoát"),                     # 逸脱する
    1784636874649: ("のがれる", "Trốn thoát, thoát khỏi (nguy hiểm, truy đuổi, v.v.)"),        # 逃れる
    1784721286433: ("けんきょ", "Khiêm tốn, nhã nhặn, không kiêu ngạo"),                       # 謙虚
    1784723075113: ("やわらかな", "Mềm mại, mềm mại nhẹ nhàng"),                              # 柔らかな
    1784723193214: ("けんじゃ", "Người hiền triết, bậc thông thái"),                           # 賢者
    1784724103739: ("てっぽう", "Súng (cổ), súng hỏa mai"),                                   # 鉄砲
    1784724306508: ("ばっすい", "Trích xuất, trích đoạn, tinh tuyển"),                         # 抜粋
    1784724773700: ("くろうと", "Chuyên gia, người sành sỏi"),                                 # 玄人
    1785898375807: ("みゃくはく", "Nhịp đập của mạch (tim)"),                                  # 脈拍
    1785898600286: ("たんしゅくする", "Rút ngắn, thu ngắn, giảm bớt (thời gian, độ dài, quy mô, …)"),  # 短縮する
    1785898809507: ("かぎる", "Hạn chế, giới hạn, chỉ … mà thôi"),                             # 限る
    1785901125977: ("ふっしょくする", "Gạt bỏ, xóa sạch, quét sạch, tiêu trừ"),                 # 払拭する
    1787624448224: ("こうぶつ", "Khoáng vật (vật chất tự nhiên trong lòng đất)"),               # 鉱物
    1787624478121: ("たんこう", "Mỏ than, hầm mỏ than"),                                       # 炭鉱
    1787624565496: ("しゅくてき", "Kẻ thù truyền kiếp, đối thủ lâu năm"),                       # 宿敵
    1787624640605: ("てきとうにする", "Làm qua loa, tùy tiện, cho có"),                         # 適当にする
    1787624743597: ("あせがしたたる", "Mồ hôi chảy thành giọt, nhỏ giọt, rơi xuống"),            # 汗が滴る
    1787624755575: ("てんてきをうつ", "Truyền dịch (đường tĩnh mạch)"),                         # 点滴をうつ
    1787624881636: ("しげんがこかつする", "Tài nguyên bị cạn kiệt, nguồn lực cạn kiệt"),        # 資源が枯渇する
    1787625302166: ("きげん", "Nguồn gốc, khởi nguyên"),                                       # 起源
    1787625327333: ("げんきのみなもと", "Nguồn gốc sức mạnh, nguồn năng lượng"),               # 元気の源
    1787625351959: ("べっそうをたてる", "Xây biệt trang, xây biệt thự"),                       # 別荘を建てる
    1787625408533: ("きゃくをよそおう", "Giả vờ làm khách, đóng giả khách hàng"),              # 客を装う
    1787625502893: ("しょめいする", "Ký tên, ký danh, đăng ký tên (làm chữ ký)"),               # 署名する
    1787625584958: ("ちょさくけん", "Bản quyền, quyền tác giả"),                               # 著作権
    1787625682912: ("ないしょにする", "Giữ bí mật, không cho ai biết"),                         # 内緒にする
    1787625696575: ("ゆいしょあるいえがら", "Gia tộc có truyền thống, dòng dõi danh giá"),     # 由緒ある家柄
    1787625747208: ("そくせきりょうり", "Món ăn nhanh, món làm tức thì, món dã chiến"),        # 即席料理
    1787625772247: ("そくとうする", "Trả lời ngay lập tức"),                                   # 即答する
    1787625860037: ("じんせいのふしめ", "Cột mốc cuộc đời, bước ngoặt cuộc đời"),             # 人生の節目
    1787625948322: ("がんちゅうにない", "Không để vào trong mắt, không thèm coi trọng, bỏ ngoài tai"),  # 眼中にない
    1787626187514: ("きんゆうきかん", "Các tổ chức tài chính (ngân hàng, công ty tài chính, quỹ đầu tư, v.v.)"),  # 金融機関
    # The image explains 融資 (ゆうし): "cấp vốn, cho vay vốn, tài trợ (đầu tư)"; the card's word is 融資を受ける.
    1787626197911: ("ゆうしをうける", "Nhận vốn vay, nhận tài trợ (融資: cấp vốn, cho vay vốn, tài trợ đầu tư)"),  # 融資を受ける
    1787626223662: ("ゆうずうがきかないひと", "Người không linh hoạt, không biết tùy cơ ứng biến"),  # 融通がきかない人
    1787626290236: ("そうおん", "Tiếng ồn, âm thanh gây náo động"),                            # 騒音
    1787626372567: ("しゃいんりょうにはいる", "Vào ký túc xá của công ty"),                    # 社員寮に入る
    # The image's heading is a pun; its "Ý nghĩa gốc" box: vui, thú vị về cuộc sống ở ký túc xá.
    1787626384582: ("りょうせいかつがたのしい", "Cuộc sống ký túc xá vui vẻ, thú vị"),        # 寮生活が楽しい
    1787626420132: ("ざいたくりょうよう", "Chăm sóc, điều trị tại nhà"),                       # 在宅療養
    1787626543331: ("かたすみ", "Một góc, nơi khuất, góc nhỏ"),                                # 片隅
    1787626579332: ("はいぐうしゃ", "Người phối ngẫu, vợ/chồng, bạn đời (hợp pháp)"),          # 配偶者
    1787626625600: ("おろか", "Ngu ngốc, dại dột, thiếu sáng suốt"),                           # 愚か
    1787626638464: ("ぐもん", "Câu hỏi ngu ngốc, câu hỏi thiếu suy nghĩ"),                     # 愚問
    1787626665479: ("ごうけいきんがく", "Tổng số tiền, tổng chi phí, tổng kim ngạch"),          # 合計金額
    # The image explains 額縁 (がくぶち): "khung tranh, viền tranh"; the card's word is 写真の額縁.
    1787626676737: ("しゃしんのがくぶち", "Khung ảnh (額縁: khung tranh, viền tranh)"),        # 写真の額縁
    1787626690658: ("はんがくでかう", "Mua với nửa giá"),                                      # 半額で買う
    1787626731936: ("かくりょう", "Các bộ trưởng, thành viên nội các"),                        # 閣僚
    1787626743316: ("ぶっかく", "Chùa chiền, kiến trúc Phật giáo"),                            # 仏閣
    1787626779381: ("こうりゃくぼん", "Sách hướng dẫn, sách chiến lược (thường là sách hướng dẫn game)"),  # 攻略本
    1787627014829: ("かいかくする", "Đổi mới, cải tổ, cải cách"),                              # 改革する
}

# Kanji cards whose Hán Việt is not in capitals (the deck's mark of a kanji card).
KANJI_CARD = {"凍"}

# A word card that is the only card of its kanji: made a kanji (meaning from the card) and kept
# as that kanji's word as well.
KANJI_FROM_WORD_CARD = {"瓦"}

# Typos in the deck, matched on the card's own (expression, reading) and fixed; each one is
# listed in the report: -> (expression, reading, why).
TYPO = {
    ("祝いを断る", "いわいをこともる"): ("誘いを断る", "さそいをことわる",
                                   "deck typo: card under 誘 means 'Từ chối lời mời' (誘いを断る)"),
    ("胸が高鳴る", "むねが高鳴る"): ("胸が高鳴る", "むねがたかなる", "reading had kanji left in it"),
    ("二十歳", "はたち (hoặc にじゅっさい)"): ("二十歳", "はたち", "second reading moved to the meaning"),
    # Readings with the wrong particle (or a slip) for the word on the front.
    ("振動を感じる", "しんどうにかんじる"): ("振動を感じる", "しんどうをかんじる", "reading: に for を"),
    ("本籍地を変更する", "ほんせきちへんこうする"): ("本籍地を変更する", "ほんせきちをへんこうする", "reading: を missing"),
    ("作品が完成する", "さくひんにかんせいする"): ("作品が完成する", "さくひんがかんせいする", "reading: に for が"),
    ("苦楽を共にする", "くらくをともにして"): ("苦楽を共にする", "くらくをともにする", "reading: して for する"),
    ("桜が開花した", "さくらにかいかした"): ("桜が開花した", "さくらがかいかした", "reading: に for が"),
    ("脚色を加える", "きゃくしょくにくわえる"): ("脚色を加える", "きゃくしょくをくわえる", "reading: に for を"),
    ("誰知の知らない", "だれもしらない"): ("誰も知らない", "だれもしらない", "front: 誰知の for 誰も (reading and meaning are 誰も知らない)"),
    ("10時に消灯する", "しょうとうする"): ("10時に消灯する", "じゅうじにしょうとうする", "reading: only the verb was given"),
    ("水に体を沈める", "みずからだにしずめる"): ("水に体を沈める", "みずにからだをしずめる", "reading: particles swapped"),
    ("売上の七割を占める", "うりあげのななわりのしめる"): ("売上の七割を占める", "うりあげのななわりをしめる", "reading: の for を"),
}
TYPO_MEANING_PREFIX = {
    ("二十歳", "はたち (hoặc にじゅっさい)"): "(cũng đọc にじゅっさい) ",
}
