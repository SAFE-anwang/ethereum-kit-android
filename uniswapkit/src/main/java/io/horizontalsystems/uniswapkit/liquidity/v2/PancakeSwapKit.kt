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
    var currentSqrtPriceX96: BigInteger = BigInteger.ZERO

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

    fun swapDataV3(rpcSource: RpcSource, chain: Chain, tokenIn: Token, tokenOut: Token): Single<SwapData> {
        val tokenPairs = pairSelector.tokenPairs(chain, tokenIn, tokenOut)
        val singles = tokenPairs.map { (tokenA, tokenB) ->
            tradeManager.liquidityPair(rpcSource, chain, tokenA, tokenB, true)
            /*val (token0, token1) = if (tokenA.sortsBefore(tokenB)) Pair(tokenA, tokenB) else Pair(tokenB, tokenA)
            val reserve0 = TokenAmount(token0, BigInteger.ZERO)
            val reserve1 = TokenAmount(token1, BigInteger.ZERO)
            Pair(reserve0, reserve1)*/
        }

        return Single.zip(singles) { array ->
            val pairs = array.map { it as Pair }
            Log.d("TradeManager", "pairs=$pairs")
            val dexType =
                if (chain == Chain.BinanceSmartChain) DexType.PancakeSwap else DexType.Uniswap
            val poolManager = PoolManager(dexType)
            val tick = rxSingle {
                poolManager.getSqrtPriceX96(
                    rpcSource, chain, tokenIn.address, tokenOut.address,
                    FeeAmount.MEDIUM_PANCAKESWAP
                )
            }.blockingGet()
            currentSqrtPriceX96 = TickMath.getSqrtRatioAtTick(tick.toInt())
            Log.d("TradeManager", "currentSqrtPriceX96=$currentSqrtPriceX96")
            SwapData(pairs, tokenIn, tokenOut)
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
                                           tokenOutAmount: BigInteger
    ): TransactionData {
        val (token0, token1) = if (tokenIn.sortsBefore(tokenOut)) Pair(tokenIn, tokenOut) else Pair(tokenOut, tokenIn)
//        // 2. 从 sqrtPriceX96 获取当前 tick
        val currentTick = TickMath.getTickAtSqrtRatio(currentSqrtPriceX96)

        // 3. 设置价格区间（例如当前价格上下 10%）
        val (tickLower, tickUpper) = TickMath.getTickRange(currentTick, 10)
        return tradeManager.transactionLiquidityV3Data(
            receiveAddress,
            chain,
            tickLower.toBigInteger(),
            tickUpper.toBigInteger(),
            token0,
            token1,
            recipient,
            if (token0 == tokenIn) tokenInAmount else tokenOutAmount,
            if (token0 == tokenIn) tokenOutAmount else tokenInAmount
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
