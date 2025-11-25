package io.horizontalsystems.wsafekit

import android.util.Log
import io.horizontalsystems.erc20kit.contract.TransferMethod
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import okio.ByteString.Companion.encode
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.nio.charset.Charset

class UsdtSafeManager(
    chain: Chain
)  {

    private val contractAddress = getContractAddress(chain)

    private val safeConvertAddress = getSafeAddress(chain)

    private val safeNetType = getSafeNetType(chain)

    fun getSafeConvertAddress(): String {
        return safeConvertAddress;
    }

    fun getSafeNetType(): String {
        return safeNetType;
    }

    fun getContractAddress(): Address {
        return contractAddress;
    }

    fun transactionData(amount: BigInteger,
                        to: String): TransactionData {
        val function = org.web3j.abi.datatypes.Function(
            "transfer", // 函数名
            listOf(org.web3j.abi.datatypes.Address(getSafeConvertAddress()), Uint256(amount)), // 参数列表
            listOf(object : TypeReference<Bool>() {}) // 返回值类型
        )

        val transferData = FunctionEncoder.encode(function)
        val extraData = Numeric.toHexString("safe4:$to".toByteArray()).substring(2)
        Log.d("longwen", "extraData=$extraData, $to")
        val input = (transferData + extraData).hexStringToByteArray()
        return TransactionData(to = contractAddress, value = BigInteger.ZERO,
            input
        )
    }

    fun transactionDataSafe4ToUsdt(amount: BigInteger,
                                   to: String,
                                   network: String): TransactionData {
        return TransactionData(to = getContractAddress(Chain.SafeFour), value = amount,
            Web3jUtils.safe4ToUsdt(amount, to, network).toByteArray(),
            isSafeUsdt = true)
    }

    sealed class UnsupportedChainError : Throwable() {
        object NoWethAddress : UnsupportedChainError()
        object NoSafeAddress : UnsupportedChainError()
        object NoSafeNetType : UnsupportedChainError()
    }

    /**
     * 获取跨链eth合约地址
     */
    private fun getContractAddress(chain: Chain): Address {
        val wethAddressHex =
            when (chain) {
                Chain.SafeFour -> "0x9C1246a4BB3c57303587e594a82632c3171662C9"
                Chain.Ethereum -> "0xdAC17F958D2ee523a2206206994597C13D831ec7"
//            Chain.EthereumRopsten -> "0x32885f2faf83aeee39e2cfe7f302e3bb884869f4"
                Chain.BinanceSmartChain -> "0x55d398326f99059fF775485246999027B3197955" //BSC正式环境
//                Chain.Polygon -> "0xb7dd19490951339fe65e341df6ec5f7f93ff2779"
                Chain.TRON -> "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
                Chain.SOL -> "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
//            Chain.BinanceSmartChain -> "0xa3d8077c3a447049164e60294c892e5e4c7f3ad2" //BSC测试环境
                else -> throw UnsupportedChainError.NoWethAddress
            }
//        }
        return Address(wethAddressHex)
    }

    /**
     * 获取跨链safe地址
     */
    private fun getSafeAddress(chain: Chain): String {
        val safeAddressHex =
            when (chain) {
                Chain.SafeFour -> "0xcF5B813482d29232604ff7c93564fc44202f5998"
                Chain.Ethereum -> "0xbB92E5E0120fe5345D5b5d36fcCdAfA391976622"
                Chain.BinanceSmartChain -> "0xbB92E5E0120fe5345D5b5d36fcCdAfA391976622" //BSC正式环境
                Chain.TRON -> "TJefpssM9uEuUhrxnmGVotw4GRem63uXFr"
                Chain.SOL -> "E7gFBw75dnXad9GqYW5EVgCNAJ85uCe29L4x6iR4BAqQ"
                else -> throw UnsupportedChainError.NoSafeAddress
            }

        return safeAddressHex
    }

    /**
     * 获取跨链safe网络类型
     */
    private fun getSafeNetType(chain: Chain): String {
        val safeAddressHex = when (chain) {
            Chain.Ethereum -> "mainnet"
//            Chain.EthereumRopsten -> "testnet"
            Chain.BinanceSmartChain -> "mainnet" //BSC正式环境
            Chain.Polygon -> "mainnet"
            Chain.SafeFour -> "mainnet"
//            Chain.BinanceSmartChain -> "testnet" //BSC测试环境
            else -> throw UnsupportedChainError.NoSafeNetType
        }
        return safeAddressHex + "4"
    }

}
