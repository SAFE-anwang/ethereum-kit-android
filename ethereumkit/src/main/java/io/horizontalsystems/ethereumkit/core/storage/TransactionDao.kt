package io.horizontalsystems.ethereumkit.core.storage

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.reactivex.Single

@Dao
interface TransactionDao {

    @Query("SELECT * FROM `Transaction` WHERE hash=:hash")
    fun getTransaction(hash: ByteArray): Transaction?

    @Query("SELECT * FROM `InternalTransaction` ORDER BY blockNumber DESC LIMIT 1")
    fun getLastInternalTransaction() : InternalTransaction?

    /** 本地最后一条交易记录的区块高度（含 pending，NULL 区块高度的记录不参与计算） */
    @Query("SELECT MAX(blockNumber) FROM `Transaction`")
    fun getLastTransactionBlockNumber(): Long?

    @Query("SELECT * FROM `Transaction` WHERE hash IN (:hashes)")
    fun getTransactions(hashes: List<ByteArray>): List<Transaction>

    @RawQuery
    fun getTransactionsByRawQuery(query: SupportSQLiteQuery): Single<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transactions: List<Transaction>)

    @Query("SELECT * FROM `Transaction` WHERE blockNumber IS NULL AND isFailed IS 0")
    fun getPendingTransactions(): List<Transaction>

    @androidx.room.Transaction
    @RawQuery
    fun getPending(query: SupportSQLiteQuery): List<Transaction>

    @Query("SELECT * FROM `Transaction` WHERE blockNumber IS NOT NULL AND nonce IN (:nonces) AND `from`=:from")
    fun getNonPendingByNonces(from: ByteArray, nonces: List<Long>): List<Transaction>

    @Query("SELECT * FROM `InternalTransaction`")
    fun getInternalTransactions(): List<InternalTransaction>

    @Query("SELECT * FROM `InternalTransaction` WHERE hash IN (:hashes)")
    fun getInternalTransactionsByHashes(hashes: List<ByteArray>): List<InternalTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInternalTransactions(internalTransactions: List<InternalTransaction>)

}
