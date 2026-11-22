package io.horizontalsystems.ethereumkit.api.jsonrpc

import com.google.gson.reflect.TypeToken
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.OtsSearchPage
import io.horizontalsystems.ethereumkit.core.toHexString
import java.lang.reflect.Type

/**
 * Otterscan 按地址分页查询交易（ots_searchTransactionsBefore / After）。
 * Chainstack 等节点若不支持会返回 -32601，调用方改走余额/nonce 定位。
 */
class OtsSearchTransactionsJsonRpc private constructor(
    methodName: String,
    addressHex: String,
    blockNumber: Long,
    pageSize: Int
) : JsonRpc<OtsSearchPage>(
    method = methodName,
    params = listOf(addressHex, blockNumber.toHexString(), pageSize)
) {
    @Transient
    override val typeOfResult: Type = object : TypeToken<OtsSearchPage>() {}.type

    companion object {
        fun before(addressHex: String, blockNumber: Long, pageSize: Int) =
            OtsSearchTransactionsJsonRpc("ots_searchTransactionsBefore", addressHex, blockNumber, pageSize)

        fun after(addressHex: String, blockNumber: Long, pageSize: Int) =
            OtsSearchTransactionsJsonRpc("ots_searchTransactionsAfter", addressHex, blockNumber, pageSize)
    }
}