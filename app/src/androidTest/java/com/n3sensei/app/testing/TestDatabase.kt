package com.n3sensei.app.testing

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.data.local.N3Database

fun inMemoryDatabase(): N3Database =
    Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        N3Database::class.java,
    ).build()

fun N3Database.countRows(table: String): Int =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        it.moveToFirst()
        it.getInt(0)
    }
