package io.horizontalsystems.ethereumkit.transactionsyncers

import android.util.Log
import io.horizontalsystems.ethereumkit.core.ITransactionProvider
import io.horizontalsystems.ethereumkit.core.ITransactionSyncer
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncerStateStorage
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.models.TransactionSyncerState
import io.reactivex.Single

private const val TAG = "EthereumTxSyncer"

class EthereumTransactionSyncer(
        private val transactionProvider: ITransactionProvider,
        private val storage: TransactionSyncerStateStorage,
        /**
         * 进度状态 key。
         *
         * 上层按数据源隔离（见 EthereumKit）：Chainstack 扫描会把进度推进到链头，
         * 若切换到 Etherscan 后沿用该进度，Etherscan 只会查询链头附近的区块，
         * 恢复钱包的历史交易将永远同步不下来。隔离后每个数据源各用各的进度，
         * 新数据源没有进度 → 从 0 开始，从而完整拉取历史。
         */
        private val syncerId: String = SyncerId
) : ITransactionSyncer {

    companion object {
        const val SyncerId = "ethereum-transaction-syncer"
    }

    override fun getTransactionsSingle(): Single<Pair<List<Transaction>, Boolean>> {
        val lastTransactionBlockNumber = storage.get(syncerId)?.lastBlockNumber ?: 0
        val initial = lastTransactionBlockNumber == 0L

        Log.i(TAG, "getTransactionsSingle: start, startBlock=${lastTransactionBlockNumber + 1}")

        return transactionProvider.getTransactions(lastTransactionBlockNumber + 1)
                .map { providerTransactions ->
                    Log.i(
                        TAG,
                        "getTransactionsSingle: received ${providerTransactions.size} txs " +
                                "(startBlock=${lastTransactionBlockNumber + 1}, " +
                                "maxBlock=${providerTransactions.maxOfOrNull { it.blockNumber }})"
                    )
                    handle(providerTransactions)
                    providerTransactions
                }
                .map { providerTransactions ->
                    // 与按区块直查路径共用同一映射
                    Pair(providerTransactions.map { it.toTransaction() }, initial)
                }
                // 失败不再被吞成「空列表 + 成功」：此前 Provider 已扫描到交易时，
                // 收据获取/状态存储等任何一环出错都会被转成空数据，
                // 同步状态还被标记为 Synced 导致不再重试，交易凭空丢失。
                // 现在交由 TransactionSyncManager 记录错误并置为 NotSynced，等待下次 sync() 重试。
                .doOnError { e ->
                    Log.e(TAG, "getTransactionsSingle: failed: $e")
                }
    }

    private fun handle(transactions: List<ProviderTransaction>) {
        // 优先使用交易中的最高区块；区块扫描类 Provider（如 Chainstack）会额外上报
        // 本次扫描到达的最高区块，保证「区间内没有相关交易」时进度也能推进，
        // 避免每次同步都重复扫描同一区间导致同步卡住。
        // startBlock 超前于本地数据时的修正由 ChainstackTransactionProvider 内部处理。
        val maxTransactionBlock = transactions.maxOfOrNull { it.blockNumber }
        val scannedBlock = transactionProvider.lastScannedBlockHeight

        val maxBlockNumber = listOfNotNull(
            maxTransactionBlock,
            scannedBlock.takeIf { it > 0 }
        ).maxOrNull() ?: return

        val syncerState = TransactionSyncerState(syncerId, maxBlockNumber)

        storage.save(syncerState)
    }

}
