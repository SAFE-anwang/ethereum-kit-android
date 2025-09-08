package io.horizontalsystems.uniswapkit.contract

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class CreatePoolMethod(
        val tokenA: Address,
        val tokenB: Address,
//        val fee: Int
) : ContractMethod() {

    override val methodSignature = Companion.methodSignature
    override fun getArguments() = listOf(tokenA, tokenB)

    companion object {
        const val methodSignature = "createPair(address,address)"
    }

}
