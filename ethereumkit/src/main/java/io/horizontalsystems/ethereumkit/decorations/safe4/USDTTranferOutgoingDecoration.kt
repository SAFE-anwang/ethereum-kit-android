package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.decorations.TransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionTag
import java.math.BigInteger

class USDTTranferOutgoingDecoration(
    val to: Address,
    val value: BigInteger,
    val contract: Address
) : TransactionDecoration {

    override fun tags() = buildList {
        addAll(listOf(TransactionTag.EVM_COIN, TransactionTag.EVM_COIN_OUTGOING, TransactionTag.OUTGOING))

        add(TransactionTag.toAddress(to.hex))
    }

}
