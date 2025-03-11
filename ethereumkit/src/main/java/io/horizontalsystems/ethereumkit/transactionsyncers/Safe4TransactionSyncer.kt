package io.horizontalsystems.ethereumkit.transactionsyncers

import android.util.Log
import io.horizontalsystems.ethereumkit.core.ITransactionProvider
import io.horizontalsystems.ethereumkit.core.ITransactionSyncer
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncerStateStorage
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Safe4AccountManagerTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.models.TransactionSyncerState
import io.reactivex.Single

class Safe4TransactionSyncer(
        private val address: String,
        private val transactionProvider: ITransactionProvider,
        private val storage: TransactionSyncerStateStorage
) : ITransactionSyncer {

    companion object {
        const val SyncerId = "safe4-account-manager-transaction-syncer"
    }

    override fun getTransactionsSingle(): Single<Pair<List<Transaction>, Boolean>> {
        val lastTransactionBlockNumber = storage.get(SyncerId)?.lastBlockNumber ?: 0
        val initial = lastTransactionBlockNumber == 0L
        return transactionProvider.getSafeAccountManagerTransactions(lastTransactionBlockNumber + 1)
                .doOnSuccess { providerTransactions -> handle(providerTransactions) }
                .map { providerTransactions ->
                    val array = providerTransactions.map { transaction ->
                        val transactionSize = providerTransactions.filter { it.hash.toHexString() == transaction.hash.toHexString() }
                        transaction.transaction(transactionSize.size)
                    }.filter { it.from!!.hex != address && it.to!!.hex == address }.distinctBy { it.hashString }

                    Pair(array, initial)
                }
                .onErrorReturnItem(Pair(listOf(), initial))
    }

    private fun handle(transactions: List<Safe4AccountManagerTransaction>) {
        val maxBlockNumber = transactions.maxOfOrNull { it.blockNumber } ?: return
        val syncerState = TransactionSyncerState(SyncerId, maxBlockNumber)

        storage.save(syncerState)
    }

}
