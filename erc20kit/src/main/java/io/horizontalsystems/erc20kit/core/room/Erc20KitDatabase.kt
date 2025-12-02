package io.horizontalsystems.erc20kit.core.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.horizontalsystems.erc20kit.models.TokenBalance
import io.horizontalsystems.ethereumkit.api.storage.RoomTypeConverters

@Database(entities = [TokenBalance::class], version = 6, exportSchema = true)
@TypeConverters(RoomTypeConverters::class)
abstract class Erc20KitDatabase : RoomDatabase() {

    abstract val tokenBalanceDao: TokenBalanceDao

    companion object {

        fun getInstance(context: Context, databaseName: String): Erc20KitDatabase {
            return Room.databaseBuilder(context, Erc20KitDatabase::class.java, databaseName)
//                    .fallbackToDestructiveMigration()
                .addMigrations(
                    Migration_5_6
                )
                    .allowMainThreadQueries()
                    .build()
        }
    }

}
