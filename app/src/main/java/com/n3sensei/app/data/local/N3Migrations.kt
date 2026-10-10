package com.n3sensei.app.data.local

import androidx.room.migration.Migration

/**
 * Every schema migration, in order. The app never falls back to destructive migration, so a
 * missing step makes the database open fail instead of silently deleting learner data.
 *
 * To change the schema:
 * 1. Bump [N3Database] `version` and build; Room exports `app/schemas/.../<version>.json`.
 * 2. Add a `Migration(old, new)` here (or an AutoMigration on the @Database).
 * 3. Commit the new schema JSON; `N3DatabaseMigrationTest` then covers the new step.
 */
object N3Migrations {
    val ALL: Array<Migration> = emptyArray()
}
