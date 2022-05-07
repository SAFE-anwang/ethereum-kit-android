package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.core.EthereumKit
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

    companion object {
        fun getInstance(ethereumKit: EthereumKit): WsafeKit {
            val wSafeManager = WSafeManager(ethereumKit)
            return WsafeKit(wSafeManager)
        }
    }

}