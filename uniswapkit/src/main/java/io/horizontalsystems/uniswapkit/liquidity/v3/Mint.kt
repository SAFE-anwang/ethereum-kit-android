package io.horizontalsystems.uniswapkit.liquidity.v3

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

object Mint {
    const val FUNC_MINT = "mint"

    fun getMintFunction(
        token0: String,
        token1: String,
        fee: BigInteger,
        tickLower: BigInteger,
        tickUpper: BigInteger,
        amount0Desired: BigInteger,
        amount1Desired: BigInteger,
        amount0Min: BigInteger,
        amount1Min: BigInteger,
        recipient: String,
        deadline: BigInteger
    ): org.web3j.abi.datatypes.Function {
        return org.web3j.abi.datatypes.Function(
            FUNC_MINT,
            listOf(
                org.web3j.abi.datatypes.Address(token0),
                org.web3j.abi.datatypes.Address(token1),
                Uint256(fee),
                Uint256(tickLower),
                Uint256(tickUpper),
                Uint256(amount0Desired),
                Uint256(amount1Desired),
                Uint256(amount0Min),
                Uint256(amount1Min),
                org.web3j.abi.datatypes.Address(recipient),
                Uint256(deadline)
            ),
            listOf(TypeReference.create(Uint256::class.java))
        )
    }
}