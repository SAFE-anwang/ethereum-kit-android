package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.decorations.TransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionTag
import java.math.BigInteger

class Safe4DepositIncomingDecoration(
    val from: Address,
    val value: BigInteger
) : TransactionDecoration {

    override fun tags() = listOf(
        TransactionTag.EVM_COIN,
        TransactionTag.EVM_COIN_INCOMING,
        TransactionTag.INCOMING,
        TransactionTag.fromAddress(from.hex)
    )

}
