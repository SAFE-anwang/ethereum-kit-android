package io.horizontalsystems.uniswapkit.contract

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class RemoveLiquidityMethod(
        val tokenA: Address,
        val tokenB: Address,
        val liquidity: BigInteger,
        val amountAMin: BigInteger,
        val amountBMin: BigInteger,
        val to: Address,
        val deadline: BigInteger
) : ContractMethod() {

    override val methodSignature = Companion.methodSignature
    override fun getArguments() = listOf(tokenA, tokenB, liquidity, amountAMin, amountBMin, to, deadline)

    companion object {
        const val methodSignature = "removeLiquidity(address,address,uint256,uint256,uint256,address,uint256)"
    }

}
