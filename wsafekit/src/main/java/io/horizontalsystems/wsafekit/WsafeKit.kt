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

    fun transactionDataSafe4(
        amount: BigInteger,
        to: String
    ): TransactionData {
        return wSafeManager.transactionDataSafe4(amount, to)
    }

    companion object {
        fun getInstance(chain: Chain, isSafe4: Boolean = false): WsafeKit {
            val wSafeManager = WSafeManager(chain, isSafe4)
            return WsafeKit(wSafeManager)
        }
    }

}