package io.horizontalsystems.ethereumkit.transactionsyncers

import android.util.Log
import io.horizontalsystems.ethereumkit.core.ITransactionProvider
import io.horizontalsystems.ethereumkit.core.ITransactionSyncer
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncerStateStorage
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.models.TransactionSyncerState
import kotlinx.coroutines.CancellationException

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

    override suspend fun getTransactions(): Pair<List<Transaction>, Boolean> {
        val lastTransactionBlockNumber = storage.get(SyncerId)?.lastBlockNumber ?: 0
        val initial = lastTransactionBlockNumber == 0L

        return try {
            val providerTransactions = transactionProvider.getTransactions(lastTransactionBlockNumber + 1)
            handle(providerTransactions)

            val array = providerTransactions.map { transaction ->
                        val isFailed = when {
                            transaction.txReceiptStatus != null -> {
                                transaction.txReceiptStatus != 1
                            }
                            transaction.isError != null -> {
                                transaction.isError != 0
                            }
                            transaction.gasUsed != null -> {
                                transaction.gasUsed == transaction.gasLimit
                            }
                            else -> {
                                false
                            }
                        }

                Transaction(
                        hash = transaction.hash,
                        timestamp = transaction.timestamp,
                        isFailed = isFailed,
                        blockNumber = transaction.blockNumber,
                        transactionIndex = transaction.transactionIndex,
                        from = transaction.from,
                        to = transaction.to,
                        value = transaction.value,
                        input = transaction.input,
                        nonce = transaction.nonce,
                        gasPrice = transaction.gasPrice,
                        gasUsed = transaction.gasUsed
                )
            }

            Pair(array, initial)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Pair(listOf(), initial)
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
