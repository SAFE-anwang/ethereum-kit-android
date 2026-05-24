package io.horizontalsystems.uniswapkit

import android.content.Context
import android.util.Log
import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.uniswapkit.contract.AddLiquidityETHMethod
import io.horizontalsystems.uniswapkit.contract.GetReservesMethod
import io.horizontalsystems.uniswapkit.contract.SwapETHForExactTokensMethod
import io.horizontalsystems.uniswapkit.contract.SwapExactETHForTokensMethod
import io.horizontalsystems.uniswapkit.contract.SwapExactTokensForETHMethod
import io.horizontalsystems.uniswapkit.contract.SwapExactTokensForTokensMethod
import io.horizontalsystems.uniswapkit.contract.SwapTokensForExactETHMethod
import io.horizontalsystems.uniswapkit.contract.SwapTokensForExactTokensMethod
import io.horizontalsystems.uniswapkit.liquidity.router.AddLiquidityMethod
import io.horizontalsystems.uniswapkit.models.*
import io.horizontalsystems.uniswapkit.models.Token.Erc20
import io.horizontalsystems.uniswapkit.models.Token.Ether
import io.horizontalsystems.uniswapkit.v3.MintParams
import io.reactivex.Single
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Int24
import org.web3j.abi.datatypes.generated.Uint128
import org.web3j.abi.datatypes.generated.Uint24
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import java.util.logging.Logger

class TradeManager {
    private val logger = Logger.getLogger(this.javaClass.simpleName)

    fun routerAddress(chain: Chain): Address = getRouterAddress(chain, Extensions.isSafeSwap || chain == Chain.SafeFour)
    fun routerAddressV3(chain: Chain): Address = getRouterV3Address(chain, Extensions.isSafeSwap || chain == Chain.SafeFour)
    fun factoryAddressString(chain: Chain): String = getFactoryAddressString(chain, Extensions.isSafeSwap || chain == Chain.SafeFour)
    fun initCodeHashString(chain: Chain): String = getInitCodeHashString(chain, Extensions.isSafeSwap || chain == Chain.SafeFour)

    fun  liquidityRouterAddress(chain: Chain): Address = getLiquidityRouterAddress(chain)
    fun liquidityFactoryAddressString(chain: Chain): String = getLiquidityFactoryAddressString(chain)
    fun liquidityInitCodeHashString(chain: Chain): String = getLiquidityInitCodeHashString(chain)

    fun liquidityRouterAddressV3(chain: Chain): Address = getLiquidityRouterAddressV3(chain)
    fun liquidityFactoryAddressStringV3(chain: Chain): String = getLiquidityFactoryAddressStringV3(chain)
    fun liquidityInitCodeHashStringV3(chain: Chain): String = getLiquidityInitCodeHashStringV3(chain)

    sealed class UnsupportedChainError : Throwable() {
        object NoRouterAddress : UnsupportedChainError()
        object NoFactoryAddress : UnsupportedChainError()
        object NoInitCodeHash : UnsupportedChainError()
    }

    fun pair(rpcSource: RpcSource, chain: Chain, tokenA: Token, tokenB: Token): Single<Pair> {
        val (token0, token1) = if (tokenA.sortsBefore(tokenB)) Pair(tokenA, tokenB) else Pair(tokenB, tokenA)
        val factoryAddressString = factoryAddressString(chain)
        val initCodeHashString = initCodeHashString(chain)

        val pairAddress = Pair.address(token0, token1, factoryAddressString, initCodeHashString)

        logger.info("pairAddress: ${pairAddress.hex}")

        return EthereumKit.call(rpcSource, pairAddress, GetReservesMethod().encodedABI())
                .map { data ->
                    logger.info("getReserves data: ${data.toHexString()}")

                    var rawReserve0: BigInteger = BigInteger.ZERO
                    var rawReserve1: BigInteger = BigInteger.ZERO

                    if (data.size == 3 * 32) {
                        rawReserve0 = BigInteger(data.copyOfRange(0, 32))
                        rawReserve1 = BigInteger(data.copyOfRange(32, 64))
                    }

                    val reserve0 = TokenAmount(token0, rawReserve0)
                    val reserve1 = TokenAmount(token1, rawReserve1)

                    logger.info("getReserves reserve0: $reserve0, reserve1: $reserve1")

                    Pair(reserve0, reserve1)
                }
    }


    fun liquidityPair(rpcSource: RpcSource, chain: Chain, tokenA: Token, tokenB: Token, isV3: Boolean = false): Single<Pair> {

        val (token0, token1) = if (tokenA.sortsBefore(tokenB)) Pair(tokenA, tokenB) else Pair(tokenB, tokenA)
        val factory = if (isV3) liquidityFactoryAddressStringV3(chain) else liquidityFactoryAddressString(chain)
        val initCodeHash = if (isV3) liquidityInitCodeHashStringV3(chain) else liquidityInitCodeHashString(chain)
        val pairAddress = Pair.address(token0, token1, factory, initCodeHash)

        logger.info("pairAddress: ${pairAddress.hex}")

        return EthereumKit.call(rpcSource, pairAddress, GetReservesMethod().encodedABI())
                .map { data ->
                    logger.info("getReserves data: ${data.toHexString()}")

                    var rawReserve0: BigInteger = BigInteger.ZERO
                    var rawReserve1: BigInteger = BigInteger.ZERO

                    if (data.size == 3 * 32) {
                        rawReserve0 = BigInteger(data.copyOfRange(0, 32))
                        rawReserve1 = BigInteger(data.copyOfRange(32, 64))
                    }

                    val reserve0 = TokenAmount(token0, rawReserve0)
                    val reserve1 = TokenAmount(token1, rawReserve1)

                    logger.info("liquidity getReserves reserve0: $reserve0, reserve1: $reserve1")

                    Pair(reserve0, reserve1)
                }
    }

    fun transactionData(receiveAddress: Address, chain: Chain, tradeData: TradeData): TransactionData {
        val routerAddress = routerAddress(chain)

        return buildSwapData(receiveAddress, tradeData).let {

            TransactionData(routerAddress, it.amount, it.input)
        }
    }

    private class SwapData(val amount: BigInteger, val input: ByteArray)

    private fun buildSwapData(receiveAddress: Address, tradeData: TradeData): SwapData {
        val trade = tradeData.trade

        val tokenIn = trade.tokenAmountIn.token
        val tokenOut = trade.tokenAmountOut.token

        val path = trade.route.path.map { it.address }
        val to = tradeData.options.recipient ?: receiveAddress
        val deadline = (Date().time / 1000 + tradeData.options.ttl).toBigInteger()

        val method = when (trade.type) {
            TradeType.ExactOut -> buildMethodForExactOut(tokenIn, tokenOut, path, to, deadline, tradeData, trade)
            TradeType.ExactIn -> buildMethodForExactIn(tokenIn, tokenOut, path, to, deadline, tradeData, trade)
        }

        val amount = if (tokenIn.isEther) {
            when (trade.type) {
                TradeType.ExactIn -> trade.tokenAmountIn.rawAmount
                TradeType.ExactOut -> tradeData.tokenAmountInMax.rawAmount
            }
        } else {
            BigInteger.ZERO
        }

        return SwapData(amount, method.encodedABI())
    }


    fun transactionLiquidityData(receiveAddress: Address, chain: Chain,
                                 tokenIn: Token,
                                 tokenOut: Token,
                                 recipient: Address?,
                                 tokenInAmount: BigInteger,
                                 tokenOutAmount: BigInteger): TransactionData {
        val routerAddress = liquidityRouterAddress(chain)

        return buildLiquidityData(receiveAddress, tokenIn, tokenOut, recipient, tokenInAmount, tokenOutAmount, chain).let {

            TransactionData(routerAddress, it.value, it.input, isBothErc = it.isBothErc)
        }
    }

    fun transactionLiquidityV3Data(receiveAddress: Address,
                                   chain: Chain,
                                   tickLower: BigInteger,
                                   tickUpper: BigInteger,
                                   tokenIn: Token,
                                   tokenOut: Token,
                                   recipient: Address?,
                                   tokenInAmount: BigInteger,
                                   tokenOutAmount: BigInteger,
                                   fee: BigInteger = BigInteger.valueOf(2500)
    ): TransactionData {
        val routerAddress = liquidityRouterAddressV3(chain)

        return buildLiquidityV3Data(receiveAddress, tokenIn, tokenOut, tickLower, tickUpper,
            recipient, tokenInAmount, tokenOutAmount, chain, fee).let {

            TransactionData(routerAddress, it.value, it.input, isBothErc = it.isBothErc, isV3 = true)
        }
    }


    private fun buildLiquidityData(receiveAddress: Address,
                                   tokenIn: Token,
                                   tokenOut: Token,
                                   recipient: Address?,
                                   tokenInAmount: BigInteger,
                                   tokenOutAmount: BigInteger,
                                   chain: Chain): TransactionData {
//        val trade = tradeData.trade

//        val tokenIn = trade.tokenAmountIn.token
//        val tokenOut = trade.tokenAmountOut.token

        val to = recipient ?: receiveAddress
        val deadline = (Date().time / 1000 + (60 * 20)).toBigInteger()
        val slippage = if (tokenIn.address.hex == "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c" ||
                tokenOut.address.hex == "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c") {
            "75"
        } else {
            "95"
        }
        val amount0Min: BigInteger = tokenInAmount.multiply(
            BigInteger(
                slippage
            )
        ).divide(BigInteger("1000"))
        val amount1Min: BigInteger = tokenOutAmount.multiply(
                BigInteger(
                    slippage
                )
        ).divide(BigInteger("1000"))

        val amountETHMin: BigInteger = tokenInAmount.multiply(
            BigInteger(
                "95"
            )
        ).divide(BigInteger("1000"))
        val method = buildMethodForLiquidityOut(tokenIn, tokenOut, to, deadline, tokenInAmount, tokenOutAmount, amount0Min, amount1Min, amountETHMin)
        return TransactionData(liquidityRouterAddress(chain), method.second, method.first.encodedABI(),
            isBothErc = tokenIn is Erc20 && tokenOut is Erc20)
    }



    private fun buildLiquidityV3Data(receiveAddress: Address,
                                   tokenIn: Token,
                                   tokenOut: Token,
                                     tickLower: BigInteger,
                                     tickUpper: BigInteger,
                                   recipient: Address?,
                                   tokenInAmount: BigInteger,
                                   tokenOutAmount: BigInteger,
                                   chain: Chain,
                                   fee: BigInteger = BigInteger.valueOf(2500)): TransactionData {

        val to = recipient ?: receiveAddress
        val deadline = (Date().time / 1000 + (60 * 20)).toBigInteger()
        // V3 mint() 合约内部自动按当前池子价格比例计算实际使用的 amount0/amount1；
        // desired 值只是上限，minimum 设为 0 避免因用户提供等额 token 但池子
        // 价格 ratio ≠ 1:1 而触发 "Price slippage check" revert。
        val amount0Min = BigInteger.ZERO
        val amount1Min = BigInteger.ZERO
        val tokenInAddress = tokenIn.address.eip55.lowercase()
        val tokenOutAddress = tokenOut.address.eip55.lowercase()
        // 确保 token0 < token1（按地址排序）
        val (actualToken0, actualToken1) = if (tokenInAddress < tokenOutAddress) {
            Pair(tokenIn, tokenOut)
        } else {
            Pair(tokenOut, tokenIn)
        }

        val (actualAmount0, actualAmount1) = if (tokenInAddress < tokenOutAddress) {
            Pair(tokenInAmount, tokenOutAmount)
        } else {
            Pair(tokenOutAmount, tokenInAmount)
        }

        val (actualAmount0Min, actualAmount1Min) = if (tokenInAddress < tokenOutAddress) {
            Pair(amount0Min, amount1Min)
        } else {
            Pair(amount1Min, amount0Min)
        }

        Log.e("addLiquidity", "tickLower=${tickLower}， tickUpper=${tickUpper}, fee=$fee")
        Log.e("addLiquidity", "amount0=$actualAmount0, amount1=$actualAmount1, amount0Min=$actualAmount0Min, amount1Min=$actualAmount1Min")
        // 使用 Web3j FunctionEncoder 构建
        val function = Function(
            "mint",
            listOf(
                MintParams(
                    org.web3j.abi.datatypes.Address(actualToken0.address.eip55),
                    org.web3j.abi.datatypes.Address(actualToken1.address.eip55),
                    Uint24(fee),
                    Int24(tickLower),
                    Int24(tickUpper),
                    Uint256(actualAmount0),
                    Uint256(actualAmount1),
                    Uint256(actualAmount0Min),
                    Uint256(actualAmount1Min),
                    org.web3j.abi.datatypes.Address(to.eip55),
                    Uint256(deadline)
                )
            ),
            listOf(
                TypeReference.create(Uint256::class.java),
                TypeReference.create(Uint128::class.java),
                TypeReference.create(Uint256::class.java),
                TypeReference.create(Uint256::class.java)
            )
        )
        val encodedFunction = FunctionEncoder.encode(function)
        Log.d("addLiquidity", "encodedFunction=${encodedFunction.hexStringToByteArray()}")
        return TransactionData(liquidityRouterAddressV3(chain), tokenInAmount, encodedFunction.hexStringToByteArray(),
            isBothErc = tokenIn is Erc20 && tokenOut is Erc20)
    }


    private class LiquidityData(val amountA: BigInteger, val amountB: BigInteger, val input: ByteArray)

    private fun buildMethodForLiquidityOut(tokenIn: Token, tokenOut: Token, to: Address, deadline: BigInteger,
                                           tokenInAmount: BigInteger,
                                           tokenOutAmount: BigInteger,
                                           amount0Min: BigInteger,
                                           amount1Min: BigInteger,
                                           amountETHMin: BigInteger): kotlin.Pair<ContractMethod, BigInteger> {
        return when {
            (tokenIn is Erc20 && tokenOut is Ether) ||
                    (tokenIn is Ether && tokenOut is Erc20) -> {
                 val pair = if (tokenIn is Erc20) {
                            Pair(tokenIn.address, tokenInAmount)
                        } else {
                            Pair(tokenOut.address, tokenOutAmount)
                        }
                val amountMin = if (tokenIn is Erc20) {
                    amount0Min
                } else {
                    amount1Min
                }
                Pair(
                    AddLiquidityETHMethod(
                        pair.first,
                        pair.second,
                        amountMin,
                        amountETHMin,
                        to,
                        deadline
                    ),
                    pair.second
                )
            }
            tokenIn is Erc20 && tokenOut is Erc20 -> {
                Pair(
                    AddLiquidityMethod(
                        tokenIn.address,
                        tokenOut.address,
                        tokenInAmount,
                        tokenOutAmount,
                        amount0Min,
                        amount1Min,
                        to,
                        deadline
                    ),
                    tokenInAmount
                )
            }
            else -> throw Exception("Invalid tokenIn/Out for add liquidity!")
        }
        /*return AddLiquidityMethod(
            tokenIn.address,
            tokenOut.address,
            trade.tokenAmountIn.rawAmount,
            trade.tokenAmountOut.rawAmount,
            amount0Min,
            amount1Min,
            to,
            deadline)*/
    }


    private fun buildMethodForExactOut(tokenIn: Token, tokenOut: Token, path: List<Address>, to: Address, deadline: BigInteger, tradeData: TradeData, trade: Trade): ContractMethod {
        val amountInMax = tradeData.tokenAmountInMax.rawAmount
        val amountOut = trade.tokenAmountOut.rawAmount

        return when {
            tokenIn is Ether && tokenOut is Erc20 -> SwapETHForExactTokensMethod(amountOut, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Ether -> SwapTokensForExactETHMethod(amountOut, amountInMax, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Erc20 -> SwapTokensForExactTokensMethod(amountOut, amountInMax, path, to, deadline)
            else -> throw Exception("Invalid tokenIn/Out for swap!")
        }
    }

    private fun buildMethodForExactIn(tokenIn: Token, tokenOut: Token, path: List<Address>, to: Address, deadline: BigInteger, tradeData: TradeData, trade: Trade): ContractMethod {
        val amountIn = trade.tokenAmountIn.rawAmount
        val amountOutMin = tradeData.tokenAmountOutMin.rawAmount

        return when {
            tokenIn is Ether && tokenOut is Erc20 -> SwapExactETHForTokensMethod(amountOutMin, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Ether -> SwapExactTokensForETHMethod(amountIn, amountOutMin, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Erc20 -> SwapExactTokensForTokensMethod(amountIn, amountOutMin, path, to, deadline)
            else -> throw Exception("Invalid tokenIn/Out for swap!")
        }
    }

    companion object {

        var safeSwapv2Safe4Router = "0x6476008C612dF9F8Db166844fFE39D24aEa12271"
        var safeSwapv2Safe4CodeHash = "ad0e51aa7a058efb9eb40fd6385473f0175ee7419e8d4f91a4e0294ec12b2d13"
        var safeSwapv2Safe4Factory = "0xB3c827077312163c53E3822defE32cAffE574B42"

        fun getRouterAddress(chain: Chain, isSafeSwap: Boolean) =
            if (isSafeSwap) {
                when (chain) {
                    Chain.Ethereum, Chain.EthereumGoerli -> Address(
                        "0x6476008C612dF9F8Db166844fFE39D24aEa12271"
                    )
                    Chain.BinanceSmartChain -> Address("0x6476008C612dF9F8Db166844fFE39D24aEa12271")
                    Chain.SafeFour -> Address(safeSwapv2Safe4Router)
                    Chain.Polygon -> Address("0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb")
                    Chain.Avalanche -> Address("0x60aE616a2155Ee3d9A68541Ba4544862310933d4")
                    Chain.Base -> Address("0x4752ba5DBc23f44D87826276BF6Fd6b1C372aD24")
                    else -> throw UnsupportedChainError.NoRouterAddress
                }
            } else {
                when (chain) {
                    Chain.Ethereum, Chain.EthereumGoerli -> Address(
                        "0x7a250d5630b4cf539739df2c5dacb4c659f2488d"
                    )
                    Chain.BinanceSmartChain -> Address("0x10ED43C718714eb63d5aA57B78B54704E256024E")
                    Chain.Polygon -> Address("0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb")
                    Chain.Avalanche -> Address("0x60aE616a2155Ee3d9A68541Ba4544862310933d4")
                    Chain.Base -> Address("0x4752ba5DBc23f44D87826276BF6Fd6b1C372aD24")
                    else -> throw UnsupportedChainError.NoRouterAddress
                }
            }

        private fun getFactoryAddressString(chain: Chain, isSafeSwap: Boolean) =
            if (isSafeSwap) {
                when (chain) {
                    Chain.Ethereum, Chain.EthereumGoerli -> "0xB3c827077312163c53E3822defE32cAffE574B42"
                    Chain.SafeFour -> safeSwapv2Safe4Factory
                    Chain.BinanceSmartChain -> "0xB3c827077312163c53E3822defE32cAffE574B42"
                    Chain.Polygon -> "0x5757371414417b8C6CAad45bAeF941aBc7d3Ab32"
                    Chain.Avalanche -> "0x9Ad6C38BE94206cA50bb0d90783181662f0Cfa10"
                    Chain.Base -> "0x8909Dc15e40173Ff4699343b6eB8132c65e18eC6"
                    else -> throw UnsupportedChainError.NoFactoryAddress
                }
            } else {
                when (chain) {
                    Chain.Ethereum,
                    Chain.SafeFour,
                    Chain.EthereumGoerli -> "0x5c69bee701ef814a2b6a3edd4b1652cb9cc5aa6f"
                    Chain.BinanceSmartChain -> "0xcA143Ce32Fe78f1f7019d7d551a6402fC5350c73"
                    Chain.Polygon -> "0x5757371414417b8C6CAad45bAeF941aBc7d3Ab32"
                    Chain.Avalanche -> "0x9Ad6C38BE94206cA50bb0d90783181662f0Cfa10"
                    Chain.Base -> "0x8909Dc15e40173Ff4699343b6eB8132c65e18eC6"
                    else -> throw UnsupportedChainError.NoFactoryAddress
                }
            }

        private fun getFactoryV3AddressString(chain: Chain, isSafeSwap: Boolean) =
            if (isSafeSwap) {
                when (chain) {
                    Chain.Ethereum, Chain.EthereumGoerli -> "0xB3c827077312163c53E3822defE32cAffE574B42"
                    Chain.SafeFour -> safeSwapv2Safe4Factory
                    Chain.BinanceSmartChain -> "0xB3c827077312163c53E3822defE32cAffE574B42"
                    Chain.Polygon -> "0x5757371414417b8C6CAad45bAeF941aBc7d3Ab32"
                    Chain.Avalanche -> "0x9Ad6C38BE94206cA50bb0d90783181662f0Cfa10"
                    Chain.Base -> "0x8909Dc15e40173Ff4699343b6eB8132c65e18eC6"
                    else -> throw UnsupportedChainError.NoFactoryAddress
                }
            } else {
                when (chain) {
                    Chain.Ethereum,
                    Chain.SafeFour,
                    Chain.EthereumGoerli -> "0x1F98431c8aD98523631AE4a59f267346ea31F984"
                    Chain.BinanceSmartChain -> "0x0BFbCF9fa4f9C56B0F40a671Ad40E0805A091865"
                    else -> throw UnsupportedChainError.NoFactoryAddress
                }
            }


        fun getRouterV3Address(chain: Chain, isSafeSwap: Boolean) =
            if (isSafeSwap) {
                when (chain) {
                    Chain.Ethereum, Chain.EthereumGoerli -> Address(
                        "0x6476008C612dF9F8Db166844fFE39D24aEa12271"
                    )
                    Chain.BinanceSmartChain -> Address("0x6476008C612dF9F8Db166844fFE39D24aEa12271")
                    Chain.SafeFour -> Address(safeSwapv2Safe4Router)
                    Chain.Polygon -> Address("0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb")
                    Chain.Avalanche -> Address("0x60aE616a2155Ee3d9A68541Ba4544862310933d4")
                    Chain.Base -> Address("0x4752ba5DBc23f44D87826276BF6Fd6b1C372aD24")
                    else -> throw UnsupportedChainError.NoRouterAddress
                }
            } else {
                when (chain) {
                    Chain.Ethereum, Chain.EthereumGoerli -> Address(
                        "0xC36442b4a4522E871399CD717aBDD847Ab11FE88"
                    )
                    Chain.BinanceSmartChain -> Address("0x46A15B0b27311cedF172AB29E4f4766fbE7F4364")
                    else -> throw UnsupportedChainError.NoRouterAddress
                }
            }

        private fun getInitCodeHashString(chain: Chain, isSafeSwap: Boolean) =
            if (isSafeSwap) {
                when (chain) {
                    Chain.Ethereum,
                    Chain.EthereumGoerli,
                    Chain.Polygon,
                    Chain.Avalanche,
                    Chain.Base -> "0xad0e51aa7a058efb9eb40fd6385473f0175ee7419e8d4f91a4e0294ec12b2d13"
                    Chain.BinanceSmartChain -> "0xad0e51aa7a058efb9eb40fd6385473f0175ee7419e8d4f91a4e0294ec12b2d13"
                    Chain.SafeFour -> safeSwapv2Safe4CodeHash
                    else -> throw UnsupportedChainError.NoInitCodeHash
                }
            } else {
                when (chain) {
                    Chain.Ethereum,
                    Chain.EthereumGoerli,
                    Chain.Polygon,
                    Chain.Avalanche,
                    Chain.Base,
                    Chain.SafeFour -> "0x96e8ac4277198ff8b6f785478aa9a39f403cb768dd02cbee326c3e7da348845f"
                    Chain.BinanceSmartChain -> "0x00fb7f630766e6a796048ea87d01acd3068e8ff67d078148a3fa3f4a84f69bd5"
                    else -> throw UnsupportedChainError.NoInitCodeHash
                }
            }

        private fun getLiquidityRouterAddress(chain: Chain) =
            when (chain) {
                Chain.SafeFour -> Address(safeSwapv2Safe4Router)
                Chain.Ethereum, Chain.EthereumGoerli -> Address(
                    "0x7a250d5630b4cf539739df2c5dacb4c659f2488d"
                )
                Chain.BinanceSmartChain -> Address("0x10ED43C718714eb63d5aA57B78B54704E256024E")
                Chain.Polygon -> Address("0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb")
                Chain.Avalanche -> Address("0x60aE616a2155Ee3d9A68541Ba4544862310933d4")
                else -> throw UnsupportedChainError.NoRouterAddress
            }

        private fun getLiquidityRouterAddressV3(chain: Chain) =
            when (chain) {
                Chain.SafeFour -> Address(safeSwapv2Safe4Router)
                Chain.Ethereum, Chain.EthereumGoerli -> Address(
                "0xC36442b4a4522E871399CD717aBDD847Ab11FE88"
                )
                Chain.BinanceSmartChain -> Address("0x46A15B0b27311cedF172AB29E4f4766fbE7F4364")
                Chain.Polygon -> Address("0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb")
                Chain.Avalanche -> Address("0x60aE616a2155Ee3d9A68541Ba4544862310933d4")
                else -> throw UnsupportedChainError.NoRouterAddress
            }

        /*private fun getLiquidityRouterAddressV3(chain: Chain) =
            when (chain) {
                Chain.SafeFour -> Address(safeSwapv2Safe4Router)
                Chain.Ethereum, Chain.EthereumGoerli -> Address(
                    "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
                )
                Chain.BinanceSmartChain -> Address("0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c")
                Chain.Polygon -> Address("0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb")
                Chain.Avalanche -> Address("0x60aE616a2155Ee3d9A68541Ba4544862310933d4")
                else -> throw UnsupportedChainError.NoRouterAddress
            }*/

        private fun getLiquidityFactoryAddressString(chain: Chain) =
            when (chain) {
                Chain.SafeFour -> safeSwapv2Safe4Factory
                Chain.Ethereum, Chain.EthereumGoerli -> "0x5c69bee701ef814a2b6a3edd4b1652cb9cc5aa6f"
                Chain.BinanceSmartChain -> "0xcA143Ce32Fe78f1f7019d7d551a6402fC5350c73"
                Chain.Polygon -> "0x02a84c1b3BBD7401a5f7fa98a384EBC70bB5749E"
                Chain.Avalanche -> "0x9Ad6C38BE94206cA50bb0d90783181662f0Cfa10"
                else -> throw UnsupportedChainError.NoFactoryAddress
            }
        private fun getLiquidityFactoryAddressStringV3(chain: Chain) =
            when (chain) {
                Chain.SafeFour -> safeSwapv2Safe4Factory
                Chain.Ethereum, Chain.EthereumGoerli -> "0x1F98431c8aD98523631AE4a59f267346ea31F984"
                Chain.BinanceSmartChain -> "0x0BFbCF9fa4f9C56B0F40a671Ad40E0805A091865"
                Chain.Polygon -> "0x02a84c1b3BBD7401a5f7fa98a384EBC70bB5749E"
                Chain.Avalanche -> "0x9Ad6C38BE94206cA50bb0d90783181662f0Cfa10"
                else -> throw UnsupportedChainError.NoFactoryAddress
            }

        private fun getLiquidityInitCodeHashString(chain: Chain) =
                when (chain) {
                    Chain.SafeFour -> safeSwapv2Safe4CodeHash
                    Chain.Ethereum, Chain.EthereumGoerli, Chain.Polygon, Chain.Avalanche -> "0x96e8ac4277198ff8b6f785478aa9a39f403cb768dd02cbee326c3e7da348845f"
                    Chain.BinanceSmartChain -> "0x00fb7f630766e6a796048ea87d01acd3068e8ff67d078148a3fa3f4a84f69bd5"
                    else -> throw UnsupportedChainError.NoInitCodeHash
                }

        private fun getLiquidityInitCodeHashStringV3(chain: Chain) =
                when (chain) {
                    Chain.SafeFour -> safeSwapv2Safe4CodeHash
                    Chain.Ethereum, Chain.EthereumGoerli, Chain.Polygon, Chain.Avalanche -> "0xe34f199b19b2b4f47f68442619d555527d244f78a3297ea89325f843f87b8b54"
                    Chain.BinanceSmartChain -> "0x6ce8eb472fa82df5469c6ab6d485f17c3ad13c8cd7af59b3d4a8026f5c8f5cc1"
                    else -> throw UnsupportedChainError.NoInitCodeHash
                }

        fun tradeExactIn(pairs: List<Pair>, tokenAmountIn: TokenAmount, tokenOut: Token, maxHops: Int = 3, currentPairs: List<Pair> = listOf(), originalTokenAmountIn: TokenAmount? = null): List<Trade> {
            //todo validations

            val trades = mutableListOf<Trade>()
            val originalTokenAmountIn = originalTokenAmountIn ?: tokenAmountIn

            for ((index, pair) in pairs.withIndex()) {

                val tokenAmountOut = try {
                    pair.tokenAmountOut(tokenAmountIn)
                } catch (error: Throwable) {
                    continue
                }

                if (tokenAmountOut.token == tokenOut) {
                    val trade = Trade(
                            TradeType.ExactIn,
                            Route(currentPairs + listOf(pair), originalTokenAmountIn.token, tokenOut),
                            originalTokenAmountIn,
                            tokenAmountOut
                    )
                    trades.add(trade)
                } else if (maxHops > 1 && pairs.size > 1) {
                    val pairsExcludingThisPair = pairs.toMutableList().apply { removeAt(index) }
                    val tradesRecursion = tradeExactIn(
                            pairsExcludingThisPair,
                            tokenAmountOut,
                            tokenOut,
                            maxHops - 1,
                            currentPairs + listOf(pair),
                            originalTokenAmountIn
                    )
                    trades.addAll(tradesRecursion)
                }
            }
            return trades
        }

        fun tradeLiquidityExactIn(pairs: List<Pair>, tokenAmountIn: TokenAmount, tokenOut: Token, maxHops: Int = 3, currentPairs: List<Pair> = listOf(), originalTokenAmountIn: TokenAmount? = null): List<Trade> {
            //todo validations
            val trades = mutableListOf<Trade>()
            val originalTokenAmountIn = originalTokenAmountIn ?: tokenAmountIn

            for ((index, pair) in pairs.withIndex()) {

                val tokenAmountOut = try {
                    pair.tokenAmountOut(tokenAmountIn)
                } catch (error: Throwable) {
                    Log.d("TAG","tradeLiquidityExactIn: ${error}")
                    continue
                }

                if (tokenAmountOut.token == tokenOut) {
                    val trade = Trade(
                            TradeType.ExactIn,
                            Route(currentPairs + listOf(pair), originalTokenAmountIn.token, tokenOut),
                            originalTokenAmountIn,
                            tokenAmountOut
                    )
                    trades.add(trade)
                } else if (maxHops > 1 && pairs.size > 1) {
                    val pairsExcludingThisPair = pairs.toMutableList().apply { removeAt(index) }
                    val tradesRecursion = tradeLiquidityExactIn(
                            pairsExcludingThisPair,
                            tokenAmountOut,
                            tokenOut,
                            maxHops - 1,
                            currentPairs + listOf(pair),
                            originalTokenAmountIn
                    )
                    trades.addAll(tradesRecursion)
                }
            }
            return trades
        }

        fun tradeExactOut(pairs: List<Pair>, tokenIn: Token, tokenAmountOut: TokenAmount, maxHops: Int = 3, currentPairs: List<Pair> = listOf(), originalTokenAmountOut: TokenAmount? = null): List<Trade> {
            //todo validations

            val trades = mutableListOf<Trade>()
            val originalTokenAmountOut = originalTokenAmountOut ?: tokenAmountOut

            for ((index, pair) in pairs.withIndex()) {

                val tokenAmountIn = try {
                    pair.tokenAmountIn(tokenAmountOut)
                } catch (error: Throwable) {
                    continue
                }

                if (tokenAmountIn.token == tokenIn) {
                    val trade = Trade(
                            TradeType.ExactOut,
                            Route(listOf(pair) + currentPairs, tokenIn, originalTokenAmountOut.token),
                            tokenAmountIn,
                            originalTokenAmountOut
                    )
                    trades.add(trade)
                } else if (maxHops > 1 && pairs.size > 1) {
                    val pairsExcludingThisPair = pairs.toMutableList().apply { removeAt(index) }
                    val tradesRecursion = tradeExactOut(
                            pairsExcludingThisPair,
                            tokenIn,
                            tokenAmountIn,
                            maxHops - 1,
                            currentPairs + listOf(pair),
                            originalTokenAmountOut
                    )
                    trades.addAll(tradesRecursion)
                }
            }
            return trades
        }

    }

}
