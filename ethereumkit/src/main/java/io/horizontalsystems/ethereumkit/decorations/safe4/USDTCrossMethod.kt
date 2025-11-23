package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class USDTCrossMethod(
    val amount: BigInteger,
        val network: String,
        val to: Address,
) : ContractMethod() {

    override val methodSignature = Companion.methodSignature
    override fun getArguments() = listOf(amount, network, to)

    companion object {
        const val methodSignature = "crossChainRedeem(uint256,string,address)"
    }
}
