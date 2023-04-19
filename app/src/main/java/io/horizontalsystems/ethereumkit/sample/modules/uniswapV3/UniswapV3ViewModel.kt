package io.horizontalsystems.ethereumkit.sample.modules.uniswapV3

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.sample.Configuration
import io.horizontalsystems.ethereumkit.sample.core.Erc20Adapter
import io.horizontalsystems.ethereumkit.sample.core.EthereumAdapter
import io.horizontalsystems.ethereumkit.sample.modules.main.Erc20Token
import io.horizontalsystems.ethereumkit.sample.modules.main.GasPriceHelper
import io.horizontalsystems.uniswapkit.UniswapV3Kit
import io.horizontalsystems.uniswapkit.models.TradeOptions
import io.horizontalsystems.uniswapkit.models.TradeType
import io.horizontalsystems.uniswapkit.v3.SwapPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.rx2.await
import java.math.BigDecimal

class UniswapV3ViewModel(
    private val ethereumKit: EthereumKit,
    private val erc20Adapter: Erc20Adapter,
    private val ethereumAdapter: EthereumAdapter,
    private val gasPriceHelper: GasPriceHelper,
    private val signer: Signer
) : ViewModel() {

    private var uniswapV3Kit = UniswapV3Kit.getInstance(ethereumKit)
    private var gasPrice: GasPrice = GasPrice.Legacy(20_000_000_000)

    val fromToken: Erc20Token? = Configuration.erc20Tokens[0]
    val toToken: Erc20Token? = null

    private val fromUniswapToken = uniswapToken(fromToken)
    private val toUniswapToken = uniswapToken(toToken)

    private var amountIn: BigDecimal? = null
    private var amountOut: BigDecimal? = null
    private var tradeType: TradeType? = null
    private var loading = false
    private var error: Throwable? = null
    private var swapPath: SwapPath? = null
    private var allowance: BigDecimal = BigDecimal.ZERO


    var swapState by mutableStateOf(
        SwapState(
            amountIn = amountIn,
            amountOut = amountOut,
            tradeType = tradeType,
            loading = loading,
            error = error,
            allowance = allowance
        )
    )
        private set

    private val tradeOptions = TradeOptions(allowedSlippagePercent = BigDecimal("0.5"))

    private var job: Job? = null

    init {
        viewModelScope.launch {
            gasPriceHelper.gasPriceFlowable().collect {
                gasPrice = it
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncAllowance()
            emitState()
        }
    }

    suspend fun syncAllowance() {
        if (fromToken == null) {
            allowance = BigDecimal(Int.MAX_VALUE)
        } else {
            allowance = try {
                erc20Adapter.allowance(uniswapV3Kit.routerAddress).await().stripTrailingZeros()
            } catch (it: Throwable) {
                Log.e("AAA", "allowance error", it)
                BigDecimal.ZERO
            }
        }
    }

    fun approve() {
        val tmpAmountIn = amountIn ?: return
        val spenderAddress = uniswapV3Kit.routerAddress

        val token = fromUniswapToken
        val amount = tmpAmountIn.movePointRight(token.decimals).toBigInteger()

        val transactionData = erc20Adapter.approveTransactionData(spenderAddress, amount)

        viewModelScope.launch(Dispatchers.IO) {
            loading = true
            emitState()

            try {
                val gasLimit = ethereumKit.estimateGas(transactionData, gasPrice).await()
                Log.e("AAA", "gas limit: $gasLimit")
                val rawTransaction = ethereumKit.rawTransaction(transactionData, gasPrice, gasLimit).await()
                val signature = signer.signature(rawTransaction)
                val fullTransaction = ethereumKit.send(rawTransaction, signature).await()
                Log.e("AAA", "approve: ${fullTransaction.transaction.hash}")
            } catch (it: Throwable) {
                Log.e("AAA", "approve ERROR = ${it.message}")
            }
            syncAllowance()

            loading = false
            emitState()
        }
    }

    fun onChangeAmountIn(amountIn: BigDecimal?) {
        job?.cancel()
        this.amountIn = amountIn
        amountOut = null
        swapPath = null
        tradeType = TradeType.ExactIn
        error = null

        if (amountIn == null) {
            loading = false
            emitState()
        } else {
            loading = true
            emitState()

            job = viewModelScope.launch(Dispatchers.IO) {
                val bestTradeExactIn = uniswapV3Kit.bestTradeExactIn(
                    tokenIn = fromUniswapToken,
                    tokenOut = toUniswapToken,
                    amountIn = amountIn.movePointRight(fromUniswapToken.decimals).toBigInteger()
                )

                if (bestTradeExactIn != null) {
                    amountOut = BigDecimal(bestTradeExactIn.amountOut, toUniswapToken.decimals)
                    swapPath = bestTradeExactIn.swapPath
                } else {
                    error = Exception("No pool found for swap")
                }

                loading = false
                emitState()
            }
        }
    }

    private fun uniswapToken(token: Erc20Token?) = when (token) {
        null -> uniswapV3Kit.etherToken()
        else -> uniswapV3Kit.token(token.contractAddress, token.decimals)
    }

    fun onChangeAmountOut(amountOut: BigDecimal?) {
        job?.cancel()
        this.amountOut = amountOut
        amountIn = null
        swapPath = null
        tradeType = TradeType.ExactOut
        error = null

        if (amountOut == null) {
            loading = false
            emitState()
        } else {
            loading = true
            emitState()

            job = viewModelScope.launch(Dispatchers.IO) {
                val bestTradeExactOut = uniswapV3Kit.bestTradeExactOut(
                    tokenIn = fromUniswapToken,
                    tokenOut = toUniswapToken,
                    amountOut = amountOut.movePointRight(toUniswapToken.decimals).toBigInteger()
                )

                if (bestTradeExactOut != null) {
                    amountIn = BigDecimal(bestTradeExactOut.amountIn, fromUniswapToken.decimals)
                    swapPath = bestTradeExactOut.swapPath
                } else {
                    error = Exception("No pool found for swap")
                }

                loading = false
                emitState()
            }
        }
    }

    private fun emitState() {
        viewModelScope.launch {
            val balance = if (fromToken == null) {
                ethereumAdapter.balance
            } else {
                erc20Adapter.balance
            }

            if (error == null && balance < (amountIn ?: BigDecimal.ZERO)) {
                error = Exception("Not enough funds")
            }

            swapState = SwapState(
                amountOut = amountOut,
                amountIn = amountIn,
                tradeType = tradeType,
                loading = loading,
                error = error,
                allowance = allowance
            )
            Log.e("AAA", "swapState: $swapState")
        }
    }

    fun swap() {
        val tmpAmountIn = amountIn ?: return
        val tmpAmountOut = amountOut ?: return
        val tradeType = tradeType ?: return
        val swapPath = swapPath ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val amountIn = tmpAmountIn.movePointRight(fromUniswapToken.decimals).toBigInteger()
            val amountOut = tmpAmountOut.movePointRight(toUniswapToken.decimals).toBigInteger()
            val transactionData = uniswapV3Kit.transactionData(
                tradeType = tradeType,
                swapPath = swapPath,
                tokenIn = fromUniswapToken,
                tokenOut = toUniswapToken,
                amountIn = amountIn,
                amountOut = amountOut,
                tradeOptions = tradeOptions
            )

            loading = true
            emitState()

            try {
                val gasLimit = ethereumKit.estimateGas(transactionData, gasPrice).await()
                Log.e("AAA", "gas limit: $gasLimit")
                val rawTransaction = ethereumKit.rawTransaction(transactionData, gasPrice, gasLimit).await()
                val signature = signer.signature(rawTransaction)
//                val fullTransaction = ethereumKit.send(rawTransaction, signature).await()
//                error = null
//                Log.e("AAA", "swap SUCCESS, txHash=${fullTransaction.transaction.hash.toHexString()}")
            } catch (it: Throwable) {
                error = it
            }

            loading = false
            emitState()
        }
    }

    class Factory(
        private val ethereumKit: EthereumKit,
        private val erc20Adapter: Erc20Adapter,
        private val ethereumAdapter: EthereumAdapter,
        private val gasPriceHelper: GasPriceHelper,
        private val signer: Signer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UniswapV3ViewModel(ethereumKit, erc20Adapter, ethereumAdapter, gasPriceHelper, signer) as T
        }
    }
}


data class SwapState(
    val amountOut: BigDecimal?,
    val amountIn: BigDecimal?,
    val tradeType: TradeType?,
    val loading: Boolean,
    val error: Throwable?,
    val allowance: BigDecimal,
)
