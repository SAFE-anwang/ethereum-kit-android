package io.horizontalsystems.uniswapkit.liquidity.router

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactory
import io.horizontalsystems.ethereumkit.contracts.ContractMethodHelper
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
    override fun getArguments() = listOf(
        tokenA,
        tokenB,
        amountADesired,
        amountBDesired,
        amountAMin,
        amountBMin,
        to,
        deadline
    )

    companion object {
        private const val methodSignature =
            "addLiquidity(address,address,uint256,uint256,uint256,uint256,address,uint256)"
    }

    class Factory : ContractMethodFactory {
        override val methodId = ContractMethodHelper.getMethodId(methodSignature)

        override fun createMethod(inputArguments: ByteArray): ContractMethod {
            val parsedArguments = ContractMethodHelper.decodeABI(
                inputArguments, listOf(
                    Address::class,
                    Address::class,
                    BigInteger::class,
                    BigInteger::class,
                    BigInteger::class,
                    BigInteger::class,
                    Address::class,
                    BigInteger::class
                )
            )

            return AddLiquidityMethod(
                tokenA = parsedArguments[0] as Address,
                tokenB = parsedArguments[1] as Address,
                amountADesired = parsedArguments[2] as BigInteger,
                amountBDesired = parsedArguments[3] as BigInteger,
                amountAMin = parsedArguments[4] as BigInteger,
                amountBMin = parsedArguments[5] as BigInteger,
                to = parsedArguments[6] as Address,
                deadline = parsedArguments[7] as BigInteger,
            )
        }
    }
}
