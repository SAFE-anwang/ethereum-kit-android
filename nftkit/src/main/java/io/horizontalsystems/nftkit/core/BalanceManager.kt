package io.horizontalsystems.nftkit.core

import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.nftkit.models.Nft
import io.horizontalsystems.nftkit.models.NftBalance
import io.horizontalsystems.nftkit.models.NftType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger

class BalanceManager(
    private val balanceSyncManager: BalanceSyncManager,
    private val storage: Storage
) : IBalanceSyncManagerListener {
    private val _nftBalances = MutableStateFlow<List<NftBalance>>(listOf())

    val nftBalancesFlow: Flow<List<NftBalance>>
        get() = _nftBalances.asStateFlow()

    val nftBalances: List<NftBalance>
        get() = _nftBalances.value

    init {
        syncNftBalances()
    }

    fun nftBalance(contractAddress: Address, tokenId: BigInteger): NftBalance? =
        storage.existingNftBalance(contractAddress, tokenId)

    private suspend fun handleNftsFromTransactions(type: NftType, nfts: List<Nft>) {
        val existingBalances = storage.nftBalances(type)
        val existingNfts = existingBalances.map { it.nft }

        // 按数据库主键 (contractAddress, tokenId) 去重：
        // 实时直查与区间扫描会重复投递同一笔 NFT 交易，同一批内也可能出现重复，
        // 不去重会触发 NftBalanceRecord 主键冲突导致写入崩溃。
        val newNfts = nfts
            .distinctBy { it.contractAddress to it.tokenId }
            .filterNot { nft ->
                existingBalances.any {
                    it.nft.contractAddress == nft.contractAddress && it.nft.tokenId == nft.tokenId
                }
            }

        storage.setNotSynced(existingNfts)
        storage.saveNftBalances(newNfts.map { NftBalance(it, 0, false) })

        balanceSyncManager.sync()
    }

    private fun syncNftBalances() {
        _nftBalances.update {
            storage.existingNftBalances()
        }
    }

    suspend fun didSync(nfts: List<Nft>, type: NftType) {
        handleNftsFromTransactions(type, nfts)
    }

    override fun didFinishSyncBalances() {
        syncNftBalances()
    }
}