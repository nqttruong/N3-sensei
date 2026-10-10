package com.n3sensei.app.testing

import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExampleCrossRef
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.KanjiExampleCrossRef
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyTagCrossRef
import com.n3sensei.app.data.local.model.ContentPackBundle

/**
 * SEED / TEST DATA ONLY. Lives in test sources and is never shipped in the app.
 * Small self-written sample used to exercise the database; it is not course content and its
 * pack is flagged `isSeed = true`.
 */
object SeedData {
    const val SOURCE_ID = "seed-test-source"
    const val PACK_ID = "seed-test"
    const val LESSON_1 = "seed-l01"
    const val LESSON_EMPTY = "seed-l02"

    const val V_KEIKEN = "seed-l01-v01"
    const val V_FUERU = "seed-l01-v02"
    const val V_TODOKERU = "seed-l01-v03"
    const val V_SHIKKARI = "seed-l01-v04"
    const val K_KEI = "seed-l01-k01"
    const val K_ZOU = "seed-l01-k02"
    const val G_TABAKARI = "seed-l01-g01"

    val source = ContentSourceEntity(
        id = SOURCE_ID,
        name = "N3 Sensei – dữ liệu seed cho kiểm thử",
        url = null,
        license = "Tự soạn, chỉ dùng cho kiểm thử tự động; không phải nội dung khóa học",
        attribution = null,
        licenseVerified = false,
    )

    private fun vocab(id: String, order: Int, expression: String, reading: String, furigana: String?, meaning: String, pos: String) =
        VocabularyEntity(id, LESSON_1, SOURCE_ID, expression, reading, furigana, meaning, pos, "N3", order, note = null)

    private fun example(id: String, japanese: String, furigana: String, vi: String) =
        ExampleSentenceEntity(id, PACK_ID, SOURCE_ID, japanese, furigana, vi)

    fun bundle(version: Int = 1, importedAt: Long = 1_700_000_000_000) = ContentPackBundle(
        sources = listOf(source),
        pack = ContentPackEntity(PACK_ID, "Gói seed kiểm thử", version, SOURCE_ID, isSeed = true, importedAt = importedAt),
        lessons = listOf(
            LessonEntity(LESSON_1, PACK_ID, 1, "Bài seed 1", "N3", "Dữ liệu kiểm thử"),
            LessonEntity(LESSON_EMPTY, PACK_ID, 2, "Bài seed 2 (rỗng)", "N3", null),
        ),
        vocabulary = listOf(
            vocab(V_KEIKEN, 1, "経験", "けいけん", "{経験|けいけん}", "kinh nghiệm", "danh từ, động từ する"),
            vocab(V_FUERU, 2, "増える", "ふえる", "{増|ふ}える", "tăng lên, gia tăng", "động từ nhóm 2 (tự động từ)"),
            vocab(V_TODOKERU, 3, "届ける", "とどける", "{届|とど}ける", "gửi đến, chuyển đến", "động từ nhóm 2 (tha động từ)"),
            vocab(V_SHIKKARI, 4, "しっかり", "しっかり", null, "chắc chắn, vững vàng", "phó từ"),
        ),
        kanji = listOf(
            KanjiEntity(K_KEI, LESSON_1, SOURCE_ID, "経", listOf("ケイ", "キョウ"), listOf("へ.る"), "KINH", "trải qua, kinh qua", 11, 1, null),
            KanjiEntity(K_ZOU, LESSON_1, SOURCE_ID, "増", listOf("ゾウ"), listOf("ま.す", "ふ.える", "ふ.やす"), "TĂNG", "tăng, thêm", 14, 2, null),
        ),
        grammar = listOf(
            GrammarEntity(
                id = G_TABAKARI,
                lessonId = LESSON_1,
                sourceId = SOURCE_ID,
                pattern = "〜たばかり",
                structure = "V-た + ばかり",
                meaningVi = "vừa mới (làm gì)",
                usage = "Diễn tả hành động vừa xảy ra, theo cảm nhận của người nói là chưa lâu.",
                cautionNote = "Khác 〜たところ: 〜たところ chỉ thời điểm ngay sau khi hành động kết thúc.",
                sortOrder = 1,
            ),
        ),
        examples = listOf(
            example("seed-ex01", "日本で働いた経験があります。", "{日本|にほん}で{働|はたら}いた{経験|けいけん}があります。", "Tôi có kinh nghiệm làm việc ở Nhật."),
            example("seed-ex02", "最近、外国人の観光客が増えています。", "{最近|さいきん}、{外国人|がいこくじん}の{観光客|かんこうきゃく}が{増|ふ}えています。", "Gần đây khách du lịch nước ngoài đang tăng lên."),
            example("seed-ex03", "この荷物を友達に届けます。", "この{荷物|にもつ}を{友達|ともだち}に{届|とど}けます。", "Tôi sẽ mang gói hàng này đến cho bạn."),
            example("seed-ex04", "日本に来たばかりです。", "{日本|にほん}に{来|き}たばかりです。", "Tôi vừa mới đến Nhật."),
        ),
        vocabularyExamples = listOf(
            VocabularyExampleCrossRef(V_KEIKEN, "seed-ex01", 1),
            VocabularyExampleCrossRef(V_FUERU, "seed-ex02", 1),
            VocabularyExampleCrossRef(V_TODOKERU, "seed-ex03", 1),
        ),
        // seed-ex01 / seed-ex02 are shared with vocabulary items.
        kanjiExamples = listOf(
            KanjiExampleCrossRef(K_KEI, "seed-ex01", 1),
            KanjiExampleCrossRef(K_ZOU, "seed-ex02", 1),
        ),
        grammarExamples = listOf(GrammarExampleCrossRef(G_TABAKARI, "seed-ex04", 1)),
        tags = listOf(TagEntity("topic:work", "Công việc")),
        vocabularyTags = listOf(VocabularyTagCrossRef(V_KEIKEN, "topic:work")),
    )
}
