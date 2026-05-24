package io.horizontalsystems.uniswapkit.liquidity

import android.util.Log
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.ethereumkit.models.TransactionLiquidityData
import io.horizontalsystems.uniswapkit.PairSelector
import io.horizontalsystems.uniswapkit.TokenFactory
import io.horizontalsystems.uniswapkit.TradeManager
import io.horizontalsystems.uniswapkit.contract.SwapContractMethodFactories
import io.horizontalsystems.uniswapkit.liquidity.v3.TickMath
import io.horizontalsystems.uniswapkit.models.*
import io.horizontalsystems.uniswapkit.v3.FeeAmount
import io.horizontalsystems.uniswapkit.v3.pool.PoolManager
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxSingle
import java.math.BigDecimal
import java.math.BigInteger
import java.util.logging.Logger

class PancakeSwapKit(
    val tradeManager: TradeManager,
    private val pairSelector: PairSelector,
    private val tokenFactory: TokenFactory
) {
    private val logger = Logger.getLogger(this.javaClass.simpleName)

    fun routerAddress(chain: Chain, isV3: Boolean = false): Address
         = if (isV3) tradeManager.liquidityRouterAddressV3(chain) else tradeManager.liquidityRouterAddress(chain)

    fun etherToken(chain: Chain): Token {
        return tokenFactory.etherToken(chain)
    }

    fun token(contractAddress: Address, decimals: Int): Token {
        return tokenFactory.token(contractAddress, decimals)
    }

    fun swapData(rpcSource: RpcSource, chain: Chain, tokenIn: Token, tokenOut: Token): Single<SwapData> {
        val tokenPairs = pairSelector.tokenPairs(chain, tokenIn, tokenOut)
        val singles = tokenPairs.map { (tokenA, tokenB) ->
            tradeManager.liquidityPair(rpcSource, chain, tokenA, tokenB)
            /*val (token0, token1) = if (tokenA.sortsBefore(tokenB)) Pair(tokenA, tokenB) else Pair(tokenB, tokenA)
            val reserve0 = TokenAmount(token0, BigInteger.ZERO)
            val reserve1 = TokenAmount(token1, BigInteger.ZERO)
            Pair(reserve0, reserve1)*/
        }
//        return Single.just(SwapData(singles, tokenIn, tokenOut))

        return Single.zip(singles) { array ->
            val pairs = array.map { it as Pair }
            Log.d("TradeManager", "pairs=$pairs")
            SwapData(pairs, tokenIn, tokenOut)
        }
    }

    fun swapDataV3(rpcSource: RpcSource, chain: Chain, tokenIn: Token, tokenOut: Token): Single<SwapDataV3Result> {
        val tokenPairs = pairSelector.tokenPairs(chain, tokenIn, tokenOut)
        val singles = tokenPairs.map { (tokenA, tokenB) ->
            tradeManager.liquidityPair(rpcSource, chain, tokenA, tokenB, true)
        }

        return Single.zip(singles) { array ->
            val pairs = array.map { it as Pair }
            Log.d("TradeManager", "pairs=$pairs")
            val dexType =
                if (chain == Chain.BinanceSmartChain) DexType.PancakeSwap else DexType.Uniswap
            val sqrtPriceX96 = try {
                val poolManager = PoolManager(dexType)
                val result = rxSingle {
                    poolManager.getSqrtPriceX96(
                        rpcSource, chain, tokenIn.address, tokenOut.address,
                        FeeAmount.MEDIUM_PANCAKESWAP
                    )
                }.blockingGet()
                Log.d("PancakeSwapKit", "swapDataV3 sqrtPriceX96=$result (tokenIn=${tokenIn.address}, tokenOut=${tokenOut.address})")
                result
            } catch (e: Exception) {
                Log.e("PancakeSwapKit", "Failed to get sqrtPriceX96: ${e.message}")
                BigInteger.ZERO
            }
            SwapDataV3Result(SwapData(pairs, tokenIn, tokenOut), sqrtPriceX96)
        }
    }

    fun bestTradeExact(swapData: SwapData, amountIn: BigDecimal, amountOut: BigDecimal, options: TradeOptions = TradeOptions()): TradeData {
        val tokenAmountIn = TokenAmount(swapData.tokenIn, amountIn)
        val tokenAmountOut = TokenAmount(swapData.tokenOut, amountOut)
        val sortedTrades = TradeManager.tradeExactIn(
            swapData.pairs,
            tokenAmountIn,
            swapData.tokenOut
        ).sorted()

        logger.info("bestTradeExactIn trades (${sortedTrades.size}):")
        sortedTrades.forEachIndexed { index, trade ->
            logger.info("$index: {in: ${trade.tokenAmountIn}, out: ${trade.tokenAmountOut}, impact: ${trade.priceImpact.toBigDecimal(2)}, pathSize: ${trade.route.path.size}")
        }

        val trade = sortedTrades.firstOrNull() ?: throw TradeError.TradeNotFound()
        logger.info("bestTradeExactIn path: ${trade.route.path.joinToString(" > ")}")

        return TradeData(trade, options)
    }

    fun bestTradeExactIn(swapData: SwapData, amountIn: BigDecimal, options: TradeOptions = TradeOptions()): TradeData {
        val tokenAmountIn = TokenAmount(swapData.tokenIn, amountIn)
        val sortedTrades = TradeManager.tradeLiquidityExactIn(
            swapData.pairs,
            tokenAmountIn,
            swapData.tokenOut
        ).sorted()

        logger.info("bestTradeExactIn trades (${sortedTrades.size}):")
        sortedTrades.forEachIndexed { index, trade ->
            logger.info("$index: {in: ${trade.tokenAmountIn}, out: ${trade.tokenAmountOut}, impact: ${trade.priceImpact.toBigDecimal(2)}, pathSize: ${trade.route.path.size}")
        }

        val trade = sortedTrades.firstOrNull() ?: throw TradeError.TradeNotFound()
        logger.info("bestTradeExactIn path: ${trade.route.path.joinToString(" > ")}")

        return TradeData(trade, options)
    }

    fun bestTradeExactOut(swapData: SwapData, amountOut: BigDecimal, options: TradeOptions = TradeOptions()): TradeData {
        val tokenAmountOut = TokenAmount(swapData.tokenOut, amountOut)
        val sortedTrades = TradeManager.tradeExactOut(
            swapData.pairs,
            swapData.tokenIn,
            tokenAmountOut
        ).sorted()

        logger.info("bestTradeExactOut trades  (${sortedTrades.size}):")
        sortedTrades.forEachIndexed { index, trade ->
            logger.info("$index: {in: ${trade.tokenAmountIn}, out: ${trade.tokenAmountOut}, impact: ${trade.priceImpact}, pathSize: ${trade.route.path.size}")
        }

        val trade = sortedTrades.firstOrNull() ?: throw TradeError.TradeNotFound()
        logger.info("bestTradeExactOut path: ${trade.route.path.joinToString(" > ")}")

        return TradeData(trade, options)
    }

    fun transactionData(receiveAddress: Address, chain: Chain, tradeData: TradeData): TransactionData {
        return tradeManager.transactionData(receiveAddress, chain, tradeData)
    }

    fun transactionLiquidityData(receiveAddress: Address, chain: Chain,
                                 tokenIn: Token,
                                 tokenOut: Token,
                                 recipient: Address?,
                                 tokenInAmount: BigInteger,
                                 tokenOutAmount: BigInteger): TransactionData {
        val (token0, token1) = if (tokenIn.sortsBefore(tokenOut)) Pair(tokenIn, tokenOut) else Pair(tokenOut, tokenIn)
        return tradeManager.transactionLiquidityData(receiveAddress, chain,
            token0, token1, recipient,
            if (token0 == tokenIn) tokenInAmount else tokenOutAmount,
            if (token0 == tokenIn) tokenOutAmount else tokenInAmount
        )
    }

    fun transactionLiquidityV3Data(receiveAddress: Address,
                                           chain: Chain,
                                           tokenIn: Token,
                                           tokenOut: Token,
                                           recipient: Address?,
                                           tokenInAmount: BigInteger,
                                           tokenOutAmount: BigInteger,
                                           sqrtPriceX96: BigInteger,
                                           minPrice: BigDecimal? = null,
                                           maxPrice: BigDecimal? = null,
                                           fee: FeeAmount = FeeAmount.MEDIUM_PANCAKESWAP
    ): TransactionData {
        val (token0, token1) = if (tokenIn.sortsBefore(tokenOut)) Pair(tokenIn, tokenOut) else Pair(tokenOut, tokenIn)
        val currentTick = TickMath.getTickAtSqrtRatio(sqrtPriceX96)

        // 使用用户输入的价格区间（如果有），否则使用当前价格 ±10%
        var tickLower: Int
        var tickUpper: Int
        if (minPrice != null && maxPrice != null && minPrice > BigDecimal.ZERO && maxPrice > BigDecimal.ZERO) {
            // 计算当前价格（从 sqrtPriceX96 转换）
            val q96 = BigDecimal(BigInteger.ONE.shiftLeft(96))
            val sqrtPrice = BigDecimal(sqrtPriceX96).divide(q96, 18, java.math.RoundingMode.HALF_UP)
            val currentPrice = sqrtPrice.multiply(sqrtPrice)
            // 计算 tick: tick = log(price) / log(1.0001)
            val log10001 = Math.log(1.0001)
            val minPriceDouble = minPrice.divide(currentPrice, 18, java.math.RoundingMode.HALF_UP).toDouble()
            val maxPriceDouble = maxPrice.divide(currentPrice, 18, java.math.RoundingMode.HALF_UP).toDouble()
            tickLower = maxOf((Math.log(minPriceDouble) / log10001 + currentTick).toInt(), TickMath.MIN_TICK)
            tickUpper = minOf((Math.log(maxPriceDouble) / log10001 + currentTick).toInt(), TickMath.MAX_TICK)
            logger.info("Using custom price range: $minPrice - $maxPrice (ticks: $tickLower - $tickUpper)")
        } else {
            val (lower, upper) = TickMath.getTickRange(currentTick, 10)
            tickLower = lower
            tickUpper = upper
        }

        // Snap ticks to tickSpacing (required by Uniswap/PancakeSwap V3 pool.mint())
        val tickSpacing = fee.tickSpacing
        tickLower = (tickLower / tickSpacing) * tickSpacing
        tickUpper = ((tickUpper + tickSpacing - 1) / tickSpacing) * tickSpacing
        tickLower = maxOf(tickLower, TickMath.MIN_TICK)
        tickUpper = minOf(tickUpper, TickMath.MAX_TICK)

        // Ensure tickLower < tickUpper after snapping (otherwise mint will revert)
        if (tickLower >= tickUpper) {
            tickLower = tickUpper - tickSpacing
            if (tickLower < TickMath.MIN_TICK) {
                tickLower = TickMath.MIN_TICK
                tickUpper = TickMath.MIN_TICK + tickSpacing
            }
            logger.info("Tick range adjusted after spacing check: tickLower=$tickLower tickUpper=$tickUpper")
        }

        logger.info("Snapped ticks to spacing=$tickSpacing (fee=${fee.value}): tickLower=$tickLower tickUpper=$tickUpper")

        return tradeManager.transactionLiquidityV3Data(
            receiveAddress,
            chain,
            tickLower.toBigInteger(),
            tickUpper.toBigInteger(),
            token0,
            token1,
            recipient,
            if (token0 == tokenIn) tokenInAmount else tokenOutAmount,
            if (token0 == tokenIn) tokenOutAmount else tokenInAmount,
            fee.value
        )
    }

    companion object {
        fun getInstance(): PancakeSwapKit {
            val tradeManager = TradeManager()
            val tokenFactory = TokenFactory()
            val pairSelector = PairSelector(tokenFactory)

            return PancakeSwapKit(tradeManager, pairSelector, tokenFactory)
        }

        /*fun addDecorators(ethereumKit: EthereumKit) {
            ethereumKit.addMethodDecorator(SwapMethodDecorator(SwapContractMethodFactories))
            ethereumKit.addTransactionDecorator(SwapTransactionDecorator())
        }*/

    }

}

sealed class TradeError : Throwable() {
    class TradeNotFound : TradeError()
}

sealed class TokenAmountError : Throwable() {
    class NegativeAmount : TokenAmountError()
}

sealed class PairError : Throwable() {
    class NotInvolvedToken : PairError()
    class InsufficientReserves : PairError()
    class InsufficientReserveOut : PairError()
}

sealed class RouteError : Throwable() {
    class EmptyPairs : RouteError()
    class InvalidPair(val index: Int) : RouteError()
}
