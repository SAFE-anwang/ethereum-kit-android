package io.horizontalsystems.ethereumkit.api.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_3_4 : Migration(3, 4) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE AccountState ADD `timeLockBalance` TEXT NOT NULL default 0")
    }
}