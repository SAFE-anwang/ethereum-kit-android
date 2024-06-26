package io.horizontalsystems.ethereumkit.api.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_13_14 : Migration(13, 14) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Transaction` ADD `lockDay` INTEGER default 0")
    }
}