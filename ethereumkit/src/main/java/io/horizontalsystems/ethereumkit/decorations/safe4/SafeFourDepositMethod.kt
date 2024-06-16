package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class SafeFourDepositMethod(
        val to: Address,
        val lockDay: BigInteger
) : ContractMethod() {

    override val methodSignature = Companion.methodSignature
    override fun getArguments() = listOf(to, lockDay)

    companion object {
        const val methodSignature = "deposit(address,uint256)"
    }
}
