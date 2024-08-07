package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.decorations.TransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionTag
import java.math.BigInteger

class Safe4DepositOutgoingDecoration(
    val to: Address,
    val value: BigInteger,
    val sentToSelf: Boolean
) : TransactionDecoration {

    override fun tags() = buildList {
        addAll(listOf(TransactionTag.EVM_COIN, TransactionTag.EVM_COIN_OUTGOING, TransactionTag.OUTGOING))

        if (sentToSelf) {
            addAll(listOf(TransactionTag.EVM_COIN_INCOMING, TransactionTag.INCOMING))
        }

        add(TransactionTag.toAddress(to.hex))
    }

}
