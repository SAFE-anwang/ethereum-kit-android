package io.horizontalsystems.ethereumkit.models

enum class Chain(
        val id: Int,
        val coinType: Int,
        val syncInterval: Long,
        val isEIP1559Supported: Boolean
) {
    Ethereum(1, 60, 15, true),
    BinanceSmartChain(56, 60, 15, false),
    Polygon(137, 60, 15, true),
    Optimism(10, 60, 15, false),
    ArbitrumOne(42161, 60, 15, false),
    Avalanche(43114, 60, 15, true),
    EthereumGoerli(5, 1, 15, true),
    EthereumRopsten(3, 1, 15, true),
    EthereumKovan(42, 1, 4, true),
    EthereumRinkeby(4, 1, 15, true);

    val isMainNet = coinType != 1

    /*object Ethereum: Chain(1, 60, 15, true)
    object BinanceSmartChain: Chain(56, 60, 5, false) //BSC正式环境
//    object BinanceSmartChain: Chain(97, 60, 5, false)  //BSC测试环境
    object Polygon: Chain(137, 60, 1, true)
    object Optimism: Chain(10, 60, 1, false)
    object ArbitrumOne: Chain(42161, 60, 1, false)
    object EthereumRopsten: Chain(3, 1, 15, true)
    object EthereumKovan: Chain(42, 1, 4, true)
    object EthereumRinkeby: Chain(4, 1, 15, true)
    object EthereumGoerli: Chain(5, 1, 15, true)*/

}
