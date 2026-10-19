package io.horizontalsystems.ethereumkit.api.jsonrpc.models

import io.horizontalsystems.ethereumkit.core.hexStringToBigIntegerOrNull
import io.horizontalsystems.ethereumkit.core.hexStringToLongOrNull
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

/**
 * trace_filter（OpenEthereum / Erigon trace 命名空间）返回的单条 trace 结构。
 *
 * 数字字段的解析陷阱：trace_* 命名空间返回的 blockNumber/transactionPosition 等为
 * 十进制裸数字（如 25975572），而 EthereumKit.gson 全局注册的 Long/BigInteger
 * TypeAdapter 只按 hex 解析（会把 "25975572" 误解析为 0x25975572）。
 * 因此这里数字字段统一用 String 接收，由 [traceQuantity]/[traceBigQuantity]
 * 按「有 0x 前缀走 hex，否则十进制」双格式解析。
 */
data class RpcTrace(
    val action: RpcTraceAction? = null,
    val blockHash: ByteArray? = null,
    val blockNumber: String? = null,
    val result: RpcTraceResult? = null,
    val traceAddress: List<String?>? = null,
    val transactionHash: ByteArray? = null,
    val transactionPosition: String? = null,
    val type: String? = null
) {

    /** 顶层交易（直接由外部交易发起，非合约内部调用） */
    val isTopLevel: Boolean
        get() = traceAddress?.isEmpty() != false

    /** 区块高度 */
    val blockNumberQuantity: Long?
        get() = traceQuantity(blockNumber)

    /** 交易在区块内的索引 */
    val transactionPositionQuantity: Int?
        get() = traceQuantity(transactionPosition)?.toInt()

    /** 去重标识：同一交易的同一调用路径 */
    val traceId: String
        get() = "${transactionHash?.toHexString()}-${traceAddress?.joinToString("-") ?: "top"}"
}

data class RpcTraceAction(
    val from: Address? = null,
    val to: Address? = null,
    val gas: String? = null,
    val input: ByteArray? = null,
    val value: String? = null,
    val callType: String? = null
) {

    val gasQuantity: Long?
        get() = traceQuantity(gas)

    val valueQuantity: BigInteger?
        get() = traceBigQuantity(value)
}

data class RpcTraceResult(
    val gasUsed: String? = null,
    val address: Address? = null
) {

    val gasUsedQuantity: Long?
        get() = traceQuantity(gasUsed)
}

/** 双格式数量解析：0x 前缀按 hex，否则按十进制 */
internal fun traceQuantity(value: String?): Long? {
    value ?: return null
    return if (value.startsWith("0x") || value.startsWith("0X")) {
        value.hexStringToLongOrNull()
    } else {
        value.toLongOrNull()
    }
}

/** 双格式大数解析：0x 前缀按 hex，否则按十进制 */
internal fun traceBigQuantity(value: String?): BigInteger? {
    value ?: return null
    return if (value.startsWith("0x") || value.startsWith("0X")) {
        value.hexStringToBigIntegerOrNull()
    } else {
        value.toBigIntegerOrNull()
    }
}
