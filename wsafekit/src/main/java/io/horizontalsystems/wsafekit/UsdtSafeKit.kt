package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import java.math.BigInteger

class UsdtSafeKit(
    private val wSafeManager: UsdtSafeManager
) {

    fun transactionData(
        amount: BigInteger,
        to: String
    ): TransactionData {
        return wSafeManager.transactionData(amount, to)
    }

    fun transactionDataSafe4(
        amount: BigInteger,
        to: String,
        network: String
    ): TransactionData {
        return wSafeManager.transactionDataSafe4ToUsdt(amount, to, network)
    }

    companion object {
        fun getInstance(chain: Chain): UsdtSafeKit {
            val wSafeManager = UsdtSafeManager(chain)
            return UsdtSafeKit(wSafeManager)
        }
    }

}