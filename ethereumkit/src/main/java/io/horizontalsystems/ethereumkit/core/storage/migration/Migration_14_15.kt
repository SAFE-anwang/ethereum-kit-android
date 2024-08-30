package io.horizontalsystems.ethereumkit.core.storage.migration

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_14_15 : Migration(14, 15) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Transaction` ADD `eventLogIndex` INTEGER NOT NULL default 0")
    }
}