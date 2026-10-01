package io.horizontalsystems.ethereumkit.api.jsonrpc.models

/**
 * eth_getBlockByNumber(fullTx=true) 的返回结构，用于区块扫描同步交易。
 */
data class RpcBlockWithTransactions(
    val number: Long,
    val timestamp: Long,
    val transactions: List<RpcTransaction> = emptyList()
)
