package io.horizontalsystems.erc20kit.core.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_5_6: Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE TokenBalance ADD `lockValue` TEXT NOT NULL DEFAULT 0")
    }
}