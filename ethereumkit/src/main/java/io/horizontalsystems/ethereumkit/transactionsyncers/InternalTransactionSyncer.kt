package io.horizontalsystems.ethereumkit.transactionsyncers

import io.horizontalsystems.ethereumkit.core.ITransactionProvider
import io.horizontalsystems.ethereumkit.core.ITransactionStorage
import io.horizontalsystems.ethereumkit.core.ITransactionSyncer
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import io.horizontalsystems.ethereumkit.models.ProviderInternalTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.reactivex.Single

class InternalTransactionSyncer(
        private val transactionProvider: ITransactionProvider,
        private val storage: ITransactionStorage
) : ITransactionSyncer {

    private fun handle(transactions: List<ProviderInternalTransaction>) {
        if (transactions.isEmpty()) return

        val existing = transactions.map { it.hash }
            .distinctBy { it.toHexString() }
            .chunked(80)
            .flatMap { batch -> storage.getInternalTransactionsByHashes(batch) }
            .map { "${it.hashString}|${it.from.hex}|${it.to.hex}|${it.value}" }
            .toSet()
        val fresh = transactions.distinctBy {
            "${it.hash.toHexString()}|${it.from.hex}|${it.to.hex}|${it.value}"
        }.filter {
            "${it.hash.toHexString()}|${it.from.hex}|${it.to.hex}|${it.value}" !in existing
        }
        if (fresh.isEmpty()) return

        storage.saveInternalTransactions(
            fresh.map { tx ->
                InternalTransaction(tx.hash, tx.blockNumber, tx.from, tx.to, tx.value)
            },
        )
    }

    override fun getTransactionsSingle(): Single<Pair<List<Transaction>, Boolean>> {
        val lastTransactionBlockNumber = storage.getLastInternalTransaction()?.blockNumber ?: 0
        val initial = lastTransactionBlockNumber == 0L

        return transactionProvider.getInternalTransactions(lastTransactionBlockNumber + 1)
                .doOnSuccess { providerInternalTransactions -> handle(providerInternalTransactions) }
                .map { providerInternalTransactions ->
                    val array = providerInternalTransactions.map { transaction ->
                        Transaction(
                                hash = transaction.hash,
                                timestamp = transaction.timestamp,
                                isFailed = false,
                                blockNumber = transaction.blockNumber,
                        )
                    }
                    Pair(array, initial)
                }
                .onErrorReturnItem(Pair(listOf(), initial))
    }

}
