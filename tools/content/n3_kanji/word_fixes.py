"""Hand corrections for word cards of N3.pdf where the automatic split of the card text
picks the wrong lines. Keys are (deck, card). Values come from the card's own text."""

# The automatic split dropped a real sense (too long for the heuristic) or kept a
# mnemonic line as a sense.
GLOSS_FIX = {
    ("第01", 58): ["Hút, thấm hút (chất lỏng, bụi bẩn)", "Rút lấy, lấy đi (tiền bạc, sức lực, năng lượng)", "Bóc lột"],  # 吸い取る
    ("第01", 59): ["Hít vào (không khí, khói, bụi)", "Hút vào bên trong"],  # 吸い込む
    ("第02", 59): ["Chạm vào, tiếp xúc", "Đề cập đến, nhắc tới", "Vi phạm, trái với", "Cảm nhận, tiếp xúc với"],  # 触れる
    ("第03", 82): ["Được ban cho, được trao tặng", "Được phú cho, được hưởng"],  # 授かる
    ("第13", 24): ["Thư thái, làm dịu đi"],  # 和む: second line is a mnemonic
    ("第18", 52): ["Đêm qua"],  # 昨夜: second line is a mnemonic
}

# Cards with no Vietnamese gloss in the PDF (only the reading, or the Hán Việt). Translated by N3 Sensei from the JMdict
# (EDRDG, CC BY-SA 4.0) English senses of the entry with the same spelling and reading;
# not reviewed yet. Value: (JMdict ent_seq, Vietnamese gloss).
JMDICT_TRANSLATED = {
    ("視覚", "しかく"): ("1312010", "thị giác, thị lực"),
    ("安全", "あんぜん"): ("1153930", "an toàn"),
    ("戦争", "せんそう"): ("1390360", "chiến tranh"),
    ("勝敗", "しょうはい"): ("1346220", "thắng bại, kết quả (trận đấu)"),
    ("結婚", "けっこん"): ("1254790", "kết hôn"),
    ("関係", "かんけい"): ("1215810", "quan hệ, liên quan"),
    ("紅葉", "こうよう"): ("1578780", "lá đỏ (mùa thu), lá chuyển màu"),
    ("調査", "ちょうさ"): ("1429120", "điều tra, khảo sát"),
    ("検査", "けんさ"): ("1257890", "kiểm tra, xét nghiệm"),
    ("機会", "きかい"): ("1220800", "cơ hội, dịp"),
    ("性格", "せいかく"): ("1375290", "tính cách"),
    ("ご注意", "ごちゅうい"): ("1005090", "sự chú ý, cẩn thận (cách nói lịch sự)"),
    ("実る", "みのる"): ("1320850", "ra quả, chín; có kết quả"),
    ("成果", "せいか"): ("1375650", "thành quả"),
    ("結果", "けっか"): ("1254690", "kết quả"),
    ("確率", "かくりつ"): ("1205950", "xác suất"),
    ("個人", "こじん"): ("1264770", "cá nhân"),
    ("船長", "せんちょう"): ("1392060", "thuyền trưởng"),
    ("面積", "めんせき"): ("1533500", "diện tích"),
    ("収穫", "しゅうかく"): ("1330510", "thu hoạch"),
    ("苦悩", "くのう"): ("1244610", "khổ não, đau khổ"),
}

# Readings the PDF gets wrong (the card's reading does not spell the word). Key: (deck, card).
READING_FIX = {
    ("第04", 15): "おしごと",  # お仕事: the PDF reads しごと
}

# Word cards the PDF puts under a kanji the word does not contain (checked on the page images
# and against the word's spelling; see docs/N3_KANJI.md). Value: the kanji the word belongs to,
# or None when that kanji already has the same word card (the card is kept, not shown twice).
REGROUP = {
    ("第11", 12): "板",  # 木の板  under 坂 (p. 205): the PDF has no card for 板
    ("第11", 13): "板",  # 黒板
    ("第11", 14): "板",  # 板書
    ("第11", 15): "板",  # 板前
    ("第11", 16): "板",  # 看板
    ("第16", 56): "連",  # 連合 under 州 (p. 238)
    ("第10", 2): None,   # 身長 under 伸 (p. 197): already under 身 (第07#78)
}

# Kanji the regrouped words need that have no card in the PDF. Placed right after the card
# whose words they take. Hán Việt from KANJIDIC2 ("Bản"), the same as in the words' Hán Việt
# printed by the PDF (HẮC BẢN, KHÁN BẢN, BẢN THƯ…); meaning translated from KANJIDIC2.
ADDED_KANJI = {
    "板": {"after": "坂", "hanViet": "BẢN", "meaning": "tấm ván, tấm bảng"},
}
