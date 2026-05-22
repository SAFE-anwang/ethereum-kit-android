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
import io.horizontalsystems.uniswapkit.models.*
import io.reactivex.Single
import java.math.BigDecimal
import java.math.BigInteger
import java.util.logging.Logger

class PancakeSwapV3Kit(
    val tradeManager: TradeManager,
    private val pairSelector: PairSelector,
    private val tokenFactory: TokenFactory
) {
    private val logger = Logger.getLogger(this.javaClass.simpleName)

    fun routerAddress(chain: Chain): Address
         = tradeManager.liquidityRouterAddress(chain)

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


    fun transactionData(receiveAddress: Address, chain: Chain, tradeData: TradeData): TransactionData {
        return tradeManager.transactionData(receiveAddress, chain, tradeData)
    }

    companion object {
        fun getInstance(): PancakeSwapV3Kit {
            val tradeManager = TradeManager()
            val tokenFactory = TokenFactory()
            val pairSelector = PairSelector(tokenFactory)

            return PancakeSwapV3Kit(tradeManager, pairSelector, tokenFactory)
        }

    }

}

