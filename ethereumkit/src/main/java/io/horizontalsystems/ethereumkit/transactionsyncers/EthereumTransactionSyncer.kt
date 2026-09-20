package io.horizontalsystems.ethereumkit.transactionsyncers

import io.horizontalsystems.ethereumkit.core.ITransactionProvider
import io.horizontalsystems.ethereumkit.core.ITransactionSyncer
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncerStateStorage
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.models.TransactionSyncerState
import io.reactivex.Single

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

        return transactionProvider.getTransactions(lastTransactionBlockNumber + 1)
                .map { providerTransactions ->
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
                .onErrorReturnItem(Pair(listOf(), initial))
    }

    private fun handle(transactions: List<ProviderTransaction>) {
        // 优先使用交易中的最高区块；区块扫描类 Provider（如 Chainstack）会额外上报
        // 本次扫描到达的最高区块，保证「区间内没有相关交易」时进度也能推进，
        // 避免每次同步都重复扫描同一区间导致同步卡住。
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
