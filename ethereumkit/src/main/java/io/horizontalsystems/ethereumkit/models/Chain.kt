package io.horizontalsystems.ethereumkit.models

enum class Chain(
    val id: Int,
    val coinType: Int,
    val gasLimit: Long,
    val syncInterval: Long,
    val isEIP1559Supported: Boolean,
    val isAnBaoWallet: Boolean = false,
    val anBaoCoinType: Int = -1
) {
    Ethereum(1, 60, 2_000_000, 15, true, true, 7),
    BinanceSmartChain(56, 60, 10_000_000, 15, false),
    Polygon(137, 60, 10_000_000, 15, true),
    Optimism(10, 60, 10_000_000, 15, false),
    ArbitrumOne(42161, 60, 10_000_000, 15, false),
    Avalanche(43114, 60, 10_000_000, 15, true),
    Gnosis(100, 60, 10_000_000, 15, true),
    Fantom(250, 60, 10_000_000, 15, false),
    EthereumGoerli(5, 1, 10_000_000, 15, true),
    EthereumRopsten(3, 1, 2_000_000, 15, true),
//    SafeFour(6666665, 60, 300000000, 15, false);
    SafeFour(6666666, 60, 300000000, 15, false); // Test

    val isMainNet = coinType != 1

//    val isSafeFourTestNet = id == 6666666
    val isSafeFourTestNet = false

    val isSafe4TestNetId = id == 6666666
}
