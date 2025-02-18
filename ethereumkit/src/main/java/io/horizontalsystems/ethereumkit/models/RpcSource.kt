package io.horizontalsystems.ethereumkit.models

import java.net.URI

sealed class RpcSource {
    data class Http(val uris: List<URI>, val auth: String?) : RpcSource()
    data class WebSocket(val uri: URI, val auth: String?) : RpcSource()

    data class RpcInfo(val name: String, val endpoint: String)

    companion object {
        var rpcList = emptyList<RpcInfo>()

        private fun getCacheRpc(key: String): String? = if (rpcList.isEmpty()) {
            null
        } else {
            rpcList.find { key == it.name }?.endpoint
        }

        fun bscRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("bsc") ?: "https://bsc-mainnet.core.chainstack.com/67f0d109c5c0b7f0aa251a89f12c0b7b")), null)
        }

        fun p2pifyRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("p2pify") ?: "https://nd-981-064-010.p2pify.com/3abdd3b90f012f4427380b632deb4180")), null)
        }

        fun binanceSmartChainHttp(): Http {
            return Http(
                    listOf(
                            URI("https://bsc-dataseed.binance.org/"),
                            URI("https://bsc-dataseed1.defibit.io/"),
                            URI("https://bsc-dataseed1.ninicoin.io/"),
                            URI("https://bsc-dataseed2.defibit.io/"),
                            URI("https://bsc-dataseed3.defibit.io/"),
                            URI("https://bsc-dataseed4.defibit.io/"),
                            URI("https://bsc-dataseed2.ninicoin.io/"),
                            URI("https://bsc-dataseed3.ninicoin.io/"),
                            URI("https://bsc-dataseed4.ninicoin.io/"),
                            URI("https://bsc-dataseed1.binance.org/"),
                            URI("https://bsc-dataseed2.binance.org/"),
                            URI("https://bsc-dataseed3.binance.org/"),
                            URI("https://bsc-dataseed4.binance.org/")
                        //BSC测试环境
//                        URI("https://data-seed-prebsc-1-s1.binance.org:8545/"),
//                        URI("https://data-seed-prebsc-2-s1.binance.org:8545/"),
//                        URI("https://data-seed-prebsc-1-s2.binance.org:8545/"),
//                        URI("https://data-seed-prebsc-2-s2.binance.org:8545/"),
//                        URI("https://data-seed-prebsc-1-s3.binance.org:8545/"),
//                        URI("https://data-seed-prebsc-2-s3.binance.org:8545/")
                    ),
                    null
            )
        }

        fun polygonRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("polygon") ?: "https://polygon-mainnet.core.chainstack.com/e9c77e1e564c041e111132211eb0df0f")), null)
        }

        fun optimismRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("optimism") ?: "https://optimism-mainnet.core.chainstack.com/9f3d2000dae7846908ac871ef96e18fe")), null)
        }

        fun arbitrumOneRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("arbitrum") ?: "https://arbitrum-mainnet.core.chainstack.com/43d06a32450091e3da629e17f3d53a5e")), null)
        }

        fun avaxNetworkHttp(): Http {
            return Http(listOf(URI(getCacheRpc("avax") ?: "https://avalanche-mainnet.core.chainstack.com/ext/bc/C/rpc/0d78d62f3dc1baf5968e7bf78018ce02")), null)
        }

        fun gnosisRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("gnosis") ?: "https://nd-786-294-051.p2pify.com/4c89e746f92af4af9f76befc8dd64e59")), null)
        }

        fun fantomRpcHttp(): Http {
            return Http(listOf(URI(getCacheRpc("fantom") ?: "https://fantom-mainnet.core.chainstack.com/01d412d3dbe245ad17742e58fa017171")), null)
        }


        fun safeFourHttp(): Http {
            return Http(
                if (Chain.SafeFour.isSafeFourTestNet) {
                    listOf(
                        URI("https://safe4testnet.anwang.com/rpc")
                    )
                } else {
                    listOf(
                        URI (getCacheRpc("safe4") ?: "https://safe4.anwang.com/rpc")
                    )
                },
                    null
            )
        }

    }
}
