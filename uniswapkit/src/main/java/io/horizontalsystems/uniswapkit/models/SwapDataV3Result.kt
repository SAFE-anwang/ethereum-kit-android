package io.horizontalsystems.uniswapkit.models

import java.math.BigInteger

/**
 * Encapsulates SwapData with its corresponding sqrtPriceX96 fetched during swapDataV3().
 * Keeps sqrtPriceX96 scoped to the specific request instead of storing it as global mutable state.
 */
data class SwapDataV3Result(
    val swapData: SwapData,
    val sqrtPriceX96: BigInteger = BigInteger.ZERO
)
