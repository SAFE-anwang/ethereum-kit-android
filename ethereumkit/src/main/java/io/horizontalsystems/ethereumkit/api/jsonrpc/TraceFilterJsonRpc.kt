package io.horizontalsystems.ethereumkit.api.jsonrpc

import com.google.gson.reflect.TypeToken
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTrace
import io.horizontalsystems.ethereumkit.core.toHexString
import java.lang.reflect.Type

/**
 * trace_filter（OpenEthereum / Erigon trace 命名空间）：
 * 按地址过滤返回区块区间内涉及该地址的所有调用 trace（含普通转账与合约内部转账）。
 *
 * 注意：fromAddress 与 toAddress 同时传入时语义为 AND（需同时满足），
 * 查询「from 或 to 是本地址」需分两次请求（与 eth_getLogs 的两方向模式一致）。
 *
 * @param filterByFrom true 时按 fromAddress 过滤，false 时按 toAddress 过滤
 * @param after 分页偏移（相对整个过滤结果集的索引）
 * @param count 单页数量
 */
class TraceFilterJsonRpc(
    fromBlock: Long,
    toBlock: Long,
    addressHex: String,
    filterByFrom: Boolean,
    after: Int,
    count: Int
) : JsonRpc<List<RpcTrace>>(
    method = "trace_filter",
    params = listOf(
        mapOf(
            "fromBlock" to fromBlock.toHexString(),
            "toBlock" to toBlock.toHexString(),
            (if (filterByFrom) "fromAddress" else "toAddress") to listOf(addressHex),
            "after" to after,
            "count" to count
        )
    )
) {
    @Transient
    override val typeOfResult: Type = object : TypeToken<List<RpcTrace>>() {}.type
}
