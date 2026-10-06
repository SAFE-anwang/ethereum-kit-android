package io.horizontalsystems.nftkit.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.nftkit.models.NftBalance
import io.horizontalsystems.nftkit.models.NftBalanceRecord
import io.horizontalsystems.nftkit.models.NftType
import java.math.BigInteger

@Dao
interface NftBalanceDao {

    // 说明：新增记录用 IGNORE。
    // 实时（websocket 直查）与区间扫描会重复投递同一笔 NFT 交易，
    // 两次「查库 → 判断不存在 → 插入」存在竞争，普通 INSERT 会因主键
    // (contractAddress, tokenId) 冲突抛 SQLiteConstraintException 导致崩溃；
    // IGNORE 下已存在的记录保持原值（真实余额），由后续余额同步刷新。

    @Query("SELECT * FROM NftBalanceRecord WHERE type = :type")
    fun nftBalances(type: NftType): List<NftBalance>

    @Query("SELECT * FROM NftBalanceRecord WHERE balance > 0")
    fun existingNftBalances(): List<NftBalance>

    @Query("SELECT * FROM NftBalanceRecord WHERE synced = 0")
    fun nonSyncedNftBalances(): List<NftBalance>

    @Query("SELECT * FROM NftBalanceRecord WHERE contractAddress = :contractAddress AND tokenId = :tokenId AND balance > 0")
    fun existingNftBalance(contractAddress: Address, tokenId: BigInteger): NftBalance?

    @Query("UPDATE NftBalanceRecord SET synced = 1, balance = :balance WHERE contractAddress = :contractAddress AND tokenId = :tokenId")
    fun setSynced(contractAddress: Address, tokenId: BigInteger, balance: Int)

    @Query("UPDATE NftBalanceRecord SET synced = 0 WHERE contractAddress = :contractAddress AND tokenId = :tokenId")
    fun setNotSynced(contractAddress: Address, tokenId: BigInteger)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(balances: List<NftBalanceRecord>)
}