package io.horizontalsystems.uniswapkit.liquidity.router

import android.util.Log
import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactory
import io.horizontalsystems.ethereumkit.contracts.ContractMethodHelper
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class MintMethod(
    val tokenA: Address,
    val tokenB: Address,
    val fee: BigInteger,
    val tickLower: BigInteger,
    val tickUpper: BigInteger,
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
        fee,
        tickLower,
        tickUpper,
        amountADesired,
        amountBDesired,
        amountAMin,
        amountBMin,
        to,
        deadline
    )

    companion object {
        private const val methodSignature =
            "mint((address,address,uint24,int24,int24,uint256,uint256,uint256,uint256,address,uint256))"
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
                    BigInteger::class,
                    BigInteger::class,
                    BigInteger::class,
                    Address::class,
                    BigInteger::class
                )
            )
            Log.e("addLiquidity", "lower=${parsedArguments[3] as BigInteger}")
            return MintMethod(
                tokenA = parsedArguments[0] as Address,
                tokenB = parsedArguments[1] as Address,
                fee = parsedArguments[2] as BigInteger,
                tickLower = parsedArguments[3] as BigInteger,
                tickUpper = parsedArguments[4] as BigInteger,
                amountADesired = parsedArguments[5] as BigInteger,
                amountBDesired = parsedArguments[6] as BigInteger,
                amountAMin = parsedArguments[7] as BigInteger,
                amountBMin = parsedArguments[8] as BigInteger,
                to = parsedArguments[9] as Address,
                deadline = parsedArguments[10] as BigInteger,
            )
        }
    }
}
