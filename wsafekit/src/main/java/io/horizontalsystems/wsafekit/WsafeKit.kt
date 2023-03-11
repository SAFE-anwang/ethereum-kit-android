package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import java.math.BigInteger

class WsafeKit(
    private val wSafeManager: WSafeManager
) {

    fun transactionData(
        amount: BigInteger,
        to: String
    ): TransactionData {
        return wSafeManager.transactionData(amount, to)
    }

    fun transactionDataMatic(
        amount: BigInteger,
        to: String
    ): TransactionData {
        return wSafeManager.transactionDataMatic(amount, to)
    }

    companion object {
        fun getInstance(chain: Chain): WsafeKit {
            val wSafeManager = WSafeManager(chain)
            return WsafeKit(wSafeManager)
        }
    }

}