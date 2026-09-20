package io.horizontalsystems.ethereumkit.api.jsonrpc

import com.google.gson.reflect.TypeToken
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcBlockWithTransactions
import java.lang.reflect.Type

/**
 * eth_getBlockByNumber(blockNumber, true)，返回区块内完整的交易列表。
 */
class GetBlockWithTransactionsJsonRpc(
    @Transient val blockNumber: Long
) : JsonRpc<RpcBlockWithTransactions>(
    method = "eth_getBlockByNumber",
    params = listOf(blockNumber, true)
) {
    @Transient
    override val typeOfResult: Type = object : TypeToken<RpcBlockWithTransactions>() {}.type
}
