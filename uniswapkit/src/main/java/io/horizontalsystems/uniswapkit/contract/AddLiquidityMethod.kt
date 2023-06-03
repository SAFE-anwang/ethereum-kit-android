package io.horizontalsystems.uniswapkit.contract

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class AddLiquidityMethod(
        val tokenA: Address,
        val tokenB: Address,
        val amountADesired: BigInteger,
        val amountBDesired: BigInteger,
        val amountAMin: BigInteger,
        val amountBMin: BigInteger,
        val to: Address,
        val deadline: BigInteger
) : ContractMethod() {

    override val methodSignature = Companion.methodSignature
    override fun getArguments() = listOf(tokenA, tokenB, amountADesired, amountBDesired, amountAMin, amountBMin, to, deadline)

    companion object {
        const val methodSignature = "addLiquidity(address,address,uint256,uint256,uint256,uint256,address,uint256)"
    }

}
