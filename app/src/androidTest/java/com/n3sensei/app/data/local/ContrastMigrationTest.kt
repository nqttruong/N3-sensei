package com.n3sensei.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.repository.OfflineContrastPracticeRepository
import com.n3sensei.app.domain.contrast.ContrastSession
import com.n3sensei.app.domain.contrast.PracticeScope
import com.n3sensei.app.domain.contrast.SessionItem
import com.n3sensei.app.domain.contrast.SessionStatus
import com.n3sensei.app.domain.model.ItemType
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * 15 → 16 (Contrast Lab): only new tables appear, and EVERY row of EVERY v15 table is unchanged.
 *
 * The v15 database is filled from the exported schema itself (schemas/15.json): two rows per table,
 * in foreign-key order, with foreign keys enforced, so every table (content, progress, review
 * history, marks, sessions, learner words and edits, reminders, exams, mistake notebook, sync…)
 * holds related data. Rows are compared column by column before and after.
 */
@RunWith(AndroidJUnit4::class)
class ContrastMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "contrast-migration-v15"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, N3Database::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    private data class Fk(val table: String, val columns: List<String>, val referenced: List<String>)
    private data class Table(val name: String, val columns: List<Pair<String, String>>, val fks: List<Fk>)

    private fun schema(version: Int): List<Table> {
        val text = instrumentation.context.assets.open("${N3Database::class.java.name}/$version.json").bufferedReader().use { it.readText() }
        val entities = JSONObject(text).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { i ->
            val e = entities.getJSONObject(i)
            val fields = e.getJSONArray("fields")
            val fks = e.optJSONArray("foreignKeys")
            Table(
                e.getString("tableName"),
                (0 until fields.length()).map { fields.getJSONObject(it).let { f -> f.getString("columnName") to f.getString("affinity") } },
                (0 until (fks?.length() ?: 0)).map { k ->
                    val fk = fks!!.getJSONObject(k)
                    Fk(fk.getString("table"), fk.getJSONArray("columns").let { a -> (0 until a.length()).map(a::getString) },
                        fk.getJSONArray("referencedColumns").let { a -> (0 until a.length()).map(a::getString) })
                },
            )
        }
    }

    private fun inForeignKeyOrder(tables: List<Table>): List<Table> {
        val done = linkedMapOf<String, Table>()
        fun visit(t: Table, path: Set<String>) {
            if (t.name in done || t.name in path) return
            t.fks.forEach { fk -> tables.firstOrNull { it.name == fk.table && it.name != t.name }?.let { visit(it, path + t.name) } }
            done[t.name] = t
        }
        tables.forEach { visit(it, emptySet()) }
        return done.values.toList()
    }

    /** Two related rows per table; FK columns take the values of the parent's row n. */
    private fun fill(db: SupportSQLiteDatabase, tables: List<Table>) {
        val rows = mutableMapOf<String, MutableList<Map<String, Any>>>()
        inForeignKeyOrder(tables).forEach { table ->
            (1..2).forEach { n ->
                val values = linkedMapOf<String, Any>()
                table.columns.forEach { (column, affinity) ->
                    values[column] = when (affinity) {
                        "INTEGER" -> n.toLong()
                        "REAL" -> n + 0.25
                        "BLOB" -> byteArrayOf(n.toByte())
                        else -> "${table.name}.$column.$n"
                    }
                }
                table.fks.forEach { fk ->
                    val parent = rows.getValue(fk.table)[n - 1]
                    fk.columns.zip(fk.referenced).forEach { (child, ref) -> values[child] = parent.getValue(ref) }
                }
                val cv = ContentValues()
                values.forEach { (k, v) ->
                    when (v) {
                        is Long -> cv.put(k, v)
                        is Double -> cv.put(k, v)
                        is ByteArray -> cv.put(k, v)
                        else -> cv.put(k, v as String)
                    }
                }
                db.insert(table.name, SQLiteDatabase.CONFLICT_ABORT, cv)
                rows.getOrPut(table.name) { mutableListOf() } += values
            }
        }
    }

    private fun snapshot(db: SupportSQLiteDatabase, tables: List<Table>): Map<String, List<String>> = tables.associate { table ->
        val order = table.columns.joinToString(", ") { "`${it.first}`" }
        table.name to db.query("SELECT * FROM `${table.name}` ORDER BY $order").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add((0 until c.columnCount).joinToString(" | ") { i ->
                        val value = when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "∅"
                            android.database.Cursor.FIELD_TYPE_INTEGER -> "i:${c.getLong(i)}"
                            android.database.Cursor.FIELD_TYPE_FLOAT -> "f:${c.getDouble(i)}"
                            android.database.Cursor.FIELD_TYPE_BLOB -> "b:${c.getBlob(i).joinToString(",")}"
                            else -> "s:${c.getString(i)}"
                        }
                        "${c.getColumnName(i)}=$value"
                    })
                }
            }
        }
    }

    private fun count(db: SupportSQLiteDatabase, sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun v15ToV16_addsOnlyContrastTables_andEveryV15RowIsUnchanged() {
        val v15 = schema(15)
        assertEquals(45, v15.size)
        val before = helper.createDatabase(name, 15).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            fill(db, v15)
            // A realistic learner row too, read back through Room below.
            db.execSQL("INSERT INTO learning_progress VALUES ('VOCABULARY','mimikara-n3-v001','REVIEW',3,2880,1800000000000,1700000000000,9,2,1600000000000,1700000000000)")
            assertEquals(0, count(db, "SELECT COUNT(*) FROM pragma_foreign_key_check"))
            snapshot(db, v15)
        }
        assertTrue("every v15 table holds rows", before.values.all { it.isNotEmpty() })

        helper.runMigrationsAndValidate(name, 16, true, N3Migrations.MIGRATION_15_16).use { db ->
            assertEquals(before, snapshot(db, v15))
            val contrastTables = schema(16).map { it.name } - v15.map { it.name }.toSet()
            assertEquals(13, contrastTables.size)
            assertTrue(contrastTables.all { it.startsWith("contrast_") })
            contrastTables.forEach { assertEquals(it, 0, count(db, "SELECT COUNT(*) FROM `$it`")) }
            assertEquals(0, count(db, "SELECT COUNT(*) FROM pragma_foreign_key_check"))
            db.query("PRAGMA integrity_check").use { c -> c.moveToFirst(); assertEquals("ok", c.getString(0)) }
        }
    }

    @Test
    fun theMigratedDatabase_opensWithRoom_oldRowsReadable_contrastUsable() = runTest {
        val v15 = schema(15)
        helper.createDatabase(name, 15).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            fill(db, v15)
            db.execSQL("INSERT INTO learning_progress VALUES ('VOCABULARY','mimikara-n3-v001','REVIEW',3,2880,1800000000000,1700000000000,9,2,1600000000000,1700000000000)")
        }
        val before = helper.runMigrationsAndValidate(name, 16, true, N3Migrations.MIGRATION_15_16).use { snapshot(it, v15) }

        val room = Room.databaseBuilder(context, N3Database::class.java, name).addMigrations(*N3Migrations.ALL).build()
        try {
            val progress = room.progressDao().get(ItemType.VOCABULARY, "mimikara-n3-v001")!!
            assertEquals(2880L to 9, progress.intervalMinutes to progress.reviewCount)
            val practice = OfflineContrastPracticeRepository(room.contrastDao())
            val t = Instant.parse("2026-11-17T02:00:00Z")
            practice.createSession(ContrastSession("s1", PracticeScope.Due, SessionStatus.ACTIVE, t, t, 1, listOf(SessionItem(0, "g", "q", 1, 0))))
            assertEquals("s1", practice.activeSession()!!.id)
            // Writing Contrast data leaves every older row as it was.
            assertEquals(before, snapshot(room.openHelper.writableDatabase, v15))
        } finally {
            room.close()
        }
    }
}
