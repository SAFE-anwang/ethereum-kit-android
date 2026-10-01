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
        private val storage: TransactionSyncerStateStorage
) : ITransactionSyncer {

    companion object {
        const val SyncerId = "ethereum-transaction-syncer"
    }

    override fun getTransactionsSingle(): Single<Pair<List<Transaction>, Boolean>> {
        val lastTransactionBlockNumber = storage.get(SyncerId)?.lastBlockNumber ?: 0
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

        val syncerState = TransactionSyncerState(SyncerId, maxBlockNumber)

        storage.save(syncerState)
    }

}
