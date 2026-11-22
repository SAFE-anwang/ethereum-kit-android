package io.horizontalsystems.ethereumkit.api.jsonrpc.models

import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransaction

/**
 * Otterscan ots_searchTransactions* 返回页。
 * receipts 中的 timestamp 为十六进制字符串（Otterscan 扩展字段）。
 */
data class OtsSearchPage(
    val txs: List<RpcTransaction> = emptyList(),
    val receipts: List<OtsReceipt> = emptyList(),
    val firstPage: Boolean = false,
    val lastPage: Boolean = false,
)

data class OtsReceipt(
    val status: Long? = null,
    val timestamp: String? = null,
)