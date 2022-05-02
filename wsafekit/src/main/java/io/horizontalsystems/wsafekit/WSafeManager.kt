package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionData
import java.math.BigInteger

class WSafeManager {

    private val contractAddress = Address("0x874a3d9a9655f18afc59b0e75463ea29ee2043c0")

    fun transactionData(amount: BigInteger,
                        to: String): TransactionData {
        return TransactionData(to = contractAddress, value = BigInteger.ZERO,
            Web3jUtils.getEth2safeTransactionInput(amount, to
        ).hexStringToByteArray())
    }

}
