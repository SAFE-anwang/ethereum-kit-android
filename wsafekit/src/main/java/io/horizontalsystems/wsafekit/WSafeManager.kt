package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import java.math.BigInteger

class WSafeManager(
    evmKit: EthereumKit
)  {

    private val contractAddress = getContractAddress(evmKit.chain)

    private val safeConvertAddress = getSafeAddress(evmKit.chain)

    private val safeNetType = getSafeNetType(evmKit.chain)

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
        return TransactionData(to = contractAddress, value = BigInteger.ZERO,
            Web3jUtils.getEth2safeTransactionInput(amount, to
        ).hexStringToByteArray())
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
        val wethAddressHex = when (chain) {
            Chain.Ethereum -> "0xee9c1ea4dcf0aaf4ff2d78b6ff83aa69797b65eb"
            Chain.EthereumRopsten -> "0x32885f2faf83aeee39e2cfe7f302e3bb884869f4"
            else -> throw UnsupportedChainError.NoWethAddress
        }
        return Address(wethAddressHex)
    }

    /**
     * 获取跨链safe地址
     */
    private fun getSafeAddress(chain: Chain): String {
        val safeAddressHex = when (chain) {
            Chain.Ethereum -> "Xnr78kmFtZBWKypYeyDLaaQRLf2EoMSgMV"
            Chain.EthereumRopsten -> "XiY8mw8XXxfkfrgAwgVUs7qQW7vGGFLByx"
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
            Chain.EthereumRopsten -> "testnet"
            else -> throw UnsupportedChainError.NoSafeNetType
        }
        return safeAddressHex
    }

}
