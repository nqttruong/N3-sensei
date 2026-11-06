package com.n3sensei.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.n3sensei.app.data.local.dao.AnalyticsDao
import com.n3sensei.app.data.local.dao.BackupDao
import com.n3sensei.app.data.local.dao.ContentDao
import com.n3sensei.app.data.local.dao.ContentPackDao
import com.n3sensei.app.data.local.dao.ErrorDao
import com.n3sensei.app.data.local.dao.ExamDao
import com.n3sensei.app.data.local.dao.ProgressDao
import com.n3sensei.app.data.local.dao.ReminderHistoryDao
import com.n3sensei.app.data.local.dao.ReviewSessionDao
import com.n3sensei.app.data.local.dao.UserContentDao
import com.n3sensei.app.data.local.entity.ContentPackEntity
import com.n3sensei.app.data.local.entity.ContentSourceEntity
import com.n3sensei.app.data.local.entity.ErrorRecordEntity
import com.n3sensei.app.data.local.entity.ErrorReviewLogEntity
import com.n3sensei.app.data.local.entity.ExamAnswerEntity
import com.n3sensei.app.data.local.entity.ExamAttemptEntity
import com.n3sensei.app.data.local.entity.ExamAttemptItemEntity
import com.n3sensei.app.data.local.entity.ExamFlagEntity
import com.n3sensei.app.data.local.entity.ExamPassageEntity
import com.n3sensei.app.data.local.entity.ExamQuestionEntity
import com.n3sensei.app.data.local.entity.ExamQuestionItemEntity
import com.n3sensei.app.data.local.entity.ExamSectionEntity
import com.n3sensei.app.data.local.entity.ExamSetEntity
import com.n3sensei.app.data.local.entity.ExampleSentenceEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.GrammarExampleCrossRef
import com.n3sensei.app.data.local.entity.GrammarExerciseEntity
import com.n3sensei.app.data.local.entity.ItemMarkEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.KanjiExampleCrossRef
import com.n3sensei.app.data.local.entity.LearningProgressEntity
import com.n3sensei.app.data.local.entity.LessonEntity
import com.n3sensei.app.data.local.entity.ReminderHistoryEntity
import com.n3sensei.app.data.local.entity.ReviewItemEntity
import com.n3sensei.app.data.local.entity.ReviewRecordEntity
import com.n3sensei.app.data.local.entity.ReviewSessionEntity
import com.n3sensei.app.data.local.entity.TagEntity
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.UserKanjiExampleEntity
import com.n3sensei.app.data.local.entity.UserWordEntity
import com.n3sensei.app.data.local.entity.UserWordTranslationEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.data.local.entity.VocabularyExampleCrossRef
import com.n3sensei.app.data.local.entity.VocabularyKanjiLinkEntity
import com.n3sensei.app.data.local.entity.VocabularyTagCrossRef

@Database(
    entities = [
        ContentSourceEntity::class,
        ContentPackEntity::class,
        LessonEntity::class,
        VocabularyEntity::class,
        KanjiEntity::class,
        GrammarEntity::class,
        GrammarExerciseEntity::class,
        ExampleSentenceEntity::class,
        VocabularyExampleCrossRef::class,
        KanjiExampleCrossRef::class,
        GrammarExampleCrossRef::class,
        TagEntity::class,
        VocabularyTagCrossRef::class,
        LearningProgressEntity::class,
        ReviewRecordEntity::class,
        ItemMarkEntity::class,
        ReviewSessionEntity::class,
        UserContentEditEntity::class,
        VocabularyKanjiLinkEntity::class,
        UserWordEntity::class,
        UserWordTranslationEntity::class,
        UserKanjiExampleEntity::class,
        ReminderHistoryEntity::class,
        ExamSetEntity::class,
        ExamSectionEntity::class,
        ExamPassageEntity::class,
        ExamQuestionEntity::class,
        ExamQuestionItemEntity::class,
        ExamAttemptEntity::class,
        ExamAnswerEntity::class,
        ExamFlagEntity::class,
        ExamAttemptItemEntity::class,
        ErrorReviewLogEntity::class,
        ErrorRecordEntity::class,
        ReviewItemEntity::class,
    ],
    version = N3Database.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class N3Database : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun contentPackDao(): ContentPackDao
    abstract fun progressDao(): ProgressDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun reviewSessionDao(): ReviewSessionDao
    abstract fun backupDao(): BackupDao
    abstract fun userContentDao(): UserContentDao
    abstract fun reminderHistoryDao(): ReminderHistoryDao
    abstract fun examDao(): ExamDao
    abstract fun errorDao(): ErrorDao

    companion object {
        const val VERSION = 14
        const val NAME = "n3sensei.db"

        /**
         * The production database. Called once by the app container.
         *
         * @param onCorruption runs before a corrupt file is deleted and recreated (see [CorruptionAwareOpenHelperFactory]).
         */
        fun create(context: Context, name: String = NAME, onCorruption: (path: String?) -> Unit = {}): N3Database =
            Room.databaseBuilder(context.applicationContext, N3Database::class.java, name)
                .addMigrations(*N3Migrations.ALL)
                .openHelperFactory(CorruptionAwareOpenHelperFactory(FrameworkSQLiteOpenHelperFactory(), onCorruption))
                .build()
    }
}
