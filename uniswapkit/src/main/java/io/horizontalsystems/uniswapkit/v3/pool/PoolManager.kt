package io.horizontalsystems.uniswapkit.v3.pool

import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.ethereumkit.spv.core.toBigInteger
import io.horizontalsystems.uniswapkit.models.DexType
import io.horizontalsystems.uniswapkit.models.Fraction
import io.horizontalsystems.uniswapkit.v3.FeeAmount
import kotlinx.coroutines.rx2.await
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint160
import org.web3j.abi.datatypes.generated.Uint24
import java.math.BigInteger

class PoolManager(
    private val dexType: DexType
) {
    private fun factoryAddress(chain: Chain) = when (dexType) {
        DexType.Uniswap -> getUniswapFactoryAddress(chain)
        DexType.PancakeSwap -> getPancakeSwapFactoryAddress(chain)
    }

    private fun getUniswapFactoryAddress(chain: Chain)= when (chain) {
        Chain.Ethereum,
        Chain.Polygon,
        Chain.Optimism,
        Chain.ArbitrumOne,
        Chain.EthereumGoerli -> "0x1F98431c8aD98523631AE4a59f267346ea31F984"
        Chain.BinanceSmartChain -> "0xdB1d10011AD0Ff90774D0C6Bb92e5C5c8b4461F7"
        Chain.Base -> "0x33128a8fC17869897dcE68Ed026d694621f6FDfD"
        Chain.ZkSync -> "0x8FdA5a7a8dCA67BBcDd10F02Fa0649A937215422"
        else -> throw IllegalStateException("Not supported Uniswap chain ${chain}")
    }

    private fun getPancakeSwapFactoryAddress(chain: Chain)= when (chain) {
        Chain.BinanceSmartChain,
        Chain.Ethereum -> "0x0BFbCF9fa4f9C56B0F40a671Ad40E0805A091865"
        Chain.ZkSync -> "0x1BB72E0CbbEA93c08f535fc7856E0338D7F7a8aB"
        Chain.Polygon -> "0x1BB72E0CbbEA93c08f535fc7856E0338D7F7a8aB"
        else -> throw IllegalStateException("Not supported PancakeSwap chain ${chain}")
    }

    // get price of tokenA in tokenB
    suspend fun getPoolPrice(rpcSource: RpcSource, chain: Chain, tokenA: Address, tokenB: Address, fee: FeeAmount): Fraction {
        val poolAddress = getPoolAddress(rpcSource, chain, tokenA, tokenB, fee)
        val callResponse = ethCall(rpcSource, poolAddress, Slot0Method().encodedABI())
        val sqrtPriceX96 = callResponse.sliceArray(IntRange(0, 31)).toBigInteger()

        val price = Fraction(sqrtPriceX96.pow(2), BigInteger.valueOf(2).pow(192))
        return when {
            tokenA.hex < tokenB.hex -> price
            else -> price.invert()
        }
    }

    // get sqrtPriceX96 in Q64.96 format (token1/token0 price representation)
    suspend fun getSqrtPriceX96(rpcSource: RpcSource, chain: Chain, tokenA: Address, tokenB: Address, fee: FeeAmount): BigInteger {
        val poolAddress = getPoolAddress(rpcSource, chain, tokenA, tokenB, fee)
        // If pool doesn't exist at this fee tier, factory returns zero address
        val zeroAddress = Address("0x0000000000000000000000000000000000000000")
        if (poolAddress == zeroAddress) {
            android.util.Log.w("PoolManager", "Pool not found for $tokenA / $tokenB at fee $fee, returning ZERO")
            return BigInteger.ZERO
        }

        val callResponse = ethCall(rpcSource, poolAddress, Slot0Method().encodedABI())
        android.util.Log.d("PoolManager", "getSqrtPriceX96 callResponse.length=${callResponse.size} pool=$poolAddress")

        // Decode using Web3j FunctionReturnDecoder for correctness
        val function = Function(
            "slot0",
            emptyList(),
            listOf(
                object : org.web3j.abi.TypeReference<Uint160>() {},
                object : org.web3j.abi.TypeReference<Uint24>() {}
            )
        )
        val decoded = FunctionReturnDecoder.decode(
            org.web3j.utils.Numeric.toHexString(callResponse),
            function.outputParameters
        )
        if (decoded.isEmpty()) {
            android.util.Log.w("PoolManager", "Failed to decode slot0 response, returning ZERO")
            return BigInteger.ZERO
        }
        val sqrtPriceX96 = (decoded[0] as Uint160).value
        android.util.Log.d("PoolManager", "getSqrtPriceX96=$sqrtPriceX96")
        return sqrtPriceX96
    }

    private suspend fun getPoolAddress(rpcSource: RpcSource, chain: Chain, tokenA: Address, tokenB: Address, fee: FeeAmount): Address {
        val callResponse = ethCall(rpcSource, Address(factoryAddress(chain)), GetPoolMethod(tokenA, tokenB, fee.value).encodedABI())
        return Address(callResponse.sliceArray(IntRange(0, 31)))
    }

    private suspend fun ethCall(rpcSource: RpcSource, contractAddress: Address, data: ByteArray): ByteArray {
        return EthereumKit.call(rpcSource, contractAddress, data).await()
    }
}

