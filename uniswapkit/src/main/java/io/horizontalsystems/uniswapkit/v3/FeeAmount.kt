package io.horizontalsystems.uniswapkit.v3

import io.horizontalsystems.uniswapkit.models.DexType
import java.math.BigInteger

sealed class FeeAmount(val value: BigInteger, val tickSpacing: Int) {
    object LOWEST : FeeAmount(100.toBigInteger(), 1)
    object LOW : FeeAmount(500.toBigInteger(), 10)
    object MEDIUM_PANCAKESWAP : FeeAmount(2500.toBigInteger(), 50)
    object MEDIUM_UNISWAP : FeeAmount(3000.toBigInteger(), 60)
    object HIGH : FeeAmount(10000.toBigInteger(), 200)

    companion object {
        fun sorted(dexType: DexType) = listOf(
            LOWEST,
            LOW,
            when (dexType) {
                DexType.Uniswap -> MEDIUM_UNISWAP
                DexType.PancakeSwap -> MEDIUM_PANCAKESWAP
            },
            HIGH
        )
    }
}
