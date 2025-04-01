package io.horizontalsystems.uniswapkit.contract

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class AddLiquidityETHMethod(
        val token: Address,
        val amountTokenDesired: BigInteger,
        val amountTokenMin: BigInteger,
        val amountETHMin: BigInteger,
        val to: Address,
        val deadline: BigInteger
) : ContractMethod() {

    override val methodSignature = Companion.methodSignature
    override fun getArguments() = listOf(token, amountTokenDesired, amountTokenMin, amountETHMin, to, deadline)

    companion object {
        const val methodSignature = "addLiquidityETH(address,uint256,uint256,uint256,address,uint256)"
    }

}
