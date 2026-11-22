package io.horizontalsystems.ethereumkit.transactionsyncers

import io.horizontalsystems.ethereumkit.models.ProviderEip1155Transaction
import io.horizontalsystems.ethereumkit.models.ProviderEip721Transaction
import io.horizontalsystems.ethereumkit.models.ProviderTokenTransaction
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Transaction

/**
 * Provider 交易 → 本地交易模型的统一映射。
 *
 * 区间同步（[EthereumTransactionSyncer]）与按区块直查（新区块实时通知后的快速补齐）
 * 共用同一份逻辑，避免两条路径的失败判定出现差异。
 */
fun ProviderTransaction.toTransaction(): Transaction {
    val isFailed = when {
        txReceiptStatus != null -> {
            txReceiptStatus != 1
        }
        isError != null -> {
            isError != 0
        }
        gasUsed != null -> {
            gasUsed == gasLimit
        }
        else -> {
            false
        }
    }

    return Transaction(
        hash = hash,
        timestamp = timestamp,
        isFailed = isFailed,
        blockNumber = blockNumber,
        transactionIndex = transactionIndex,
        from = from,
        to = to,
        value = value,
        input = input,
        nonce = nonce,
        gasPrice = gasPrice,
        gasUsed = gasUsed
    )
}

fun ProviderTokenTransaction.toShellTransaction(): Transaction =
    Transaction(
        hash = hash,
        timestamp = timestamp,
        isFailed = false,
        blockNumber = blockNumber,
        transactionIndex = transactionIndex,
        nonce = nonce,
        gasPrice = gasPrice,
        gasLimit = gasLimit,
        gasUsed = gasUsed,
    )

fun ProviderEip721Transaction.toShellTransaction(): Transaction =
    Transaction(
        hash = hash,
        timestamp = timestamp,
        isFailed = false,
        blockNumber = blockNumber,
        transactionIndex = transactionIndex,
        nonce = nonce,
        gasPrice = gasPrice,
        gasLimit = gasLimit,
        gasUsed = gasUsed,
    )

fun ProviderEip1155Transaction.toShellTransaction(): Transaction =
    Transaction(
        hash = hash,
        timestamp = timestamp,
        isFailed = false,
        blockNumber = blockNumber,
        transactionIndex = transactionIndex,
        nonce = nonce,
        gasPrice = gasPrice,
        gasLimit = gasLimit,
        gasUsed = gasUsed,
    )
