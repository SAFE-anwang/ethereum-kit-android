package io.horizontalsystems.ethereumkit.api.storage

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import io.horizontalsystems.ethereumkit.api.models.AccountState
import io.horizontalsystems.ethereumkit.api.models.LastBlockHeight
import io.horizontalsystems.ethereumkit.api.storage.migration.Migration_3_4


@Database(entities = [AccountState::class, LastBlockHeight::class], version = 4, exportSchema = false)
@TypeConverters(RoomTypeConverters::class)
abstract class ApiDatabase : RoomDatabase() {

    abstract fun balanceDao(): AccountStateDao
    abstract fun lastBlockHeightDao(): LastBlockHeightDao

    companion object {

        fun getInstance(context: Context, databaseName: String): ApiDatabase {
            return Room.databaseBuilder(context, ApiDatabase::class.java, databaseName)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .addMigrations(
                            Migration_3_4
                    )
                    .build()
        }

    }

}
