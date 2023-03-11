package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import java.math.BigInteger

class WSafeManager(
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
        return TransactionData(to = contractAddress, value = BigInteger.ZERO,
            Web3jUtils.getEth2safeTransactionInput(amount, to
        ).hexStringToByteArray())
    }

    fun transactionDataMatic(amount: BigInteger,
                        to: String): TransactionData {
        return TransactionData(to = contractAddress, value = BigInteger.ZERO,
            Web3jUtils.getMatic2safeTransactionInput(amount, to
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
            Chain.BinanceSmartChain -> "0x4d7fa587ec8e50bd0e9cd837cb4da796f47218a1" //BSC正式环境
            Chain.Polygon -> "0xb7Dd19490951339fE65E341Df6eC5f7f93FF2779"
//            Chain.BinanceSmartChain -> "0xa3d8077c3a447049164e60294c892e5e4c7f3ad2" //BSC测试环境
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
            Chain.BinanceSmartChain -> "XdyjRkZpyDdPD3uJAUC3MzJSoCtEZincFf" //BSC正式环境
            Chain.Polygon -> "XuPmDoaNb6rbNywefkTbESHXiYqNpYvaPU"
//            Chain.BinanceSmartChain -> "Xm3DvW7ZpmCYtyhtPSu5iYQknpofseVxaF" //BSC测试环境
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
            Chain.BinanceSmartChain -> "mainnet" //BSC正式环境
            Chain.Polygon -> "mainnet"
//            Chain.BinanceSmartChain -> "testnet" //BSC测试环境
            else -> throw UnsupportedChainError.NoSafeNetType
        }
        return safeAddressHex
    }

}
