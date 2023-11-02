package io.horizontalsystems.ethereumkit.models

import java.net.URI

sealed class RpcSource() {
    class Http(val uris: List<URI>, val auth: String?) : RpcSource()
    class WebSocket(val uri: URI, val auth: String?) : RpcSource()

    companion object {
        private fun infuraHttp(subdomain: String, projectId: String, projectSecret: String? = null): Http {
            return Http(listOf(URI("https://$subdomain.infura.io/v3/$projectId")), projectSecret)
        }

        private fun infuraWebSocket(subdomain: String, projectId: String, projectSecret: String? = null): WebSocket {
            return WebSocket(URI("https://$subdomain.infura.io/ws/v3/$projectId"), projectSecret)
        }

        fun ethereumInfuraHttp(projectId: String, projectSecret: String? = null): Http {
            return infuraHttp("mainnet", projectId, projectSecret)
        }

        fun goerliInfuraHttp(projectId: String, projectSecret: String? = null): Http {
            return infuraHttp("goerli", projectId, projectSecret)
        }

        fun ethereumInfuraWebSocket(projectId: String, projectSecret: String? = null): WebSocket {
            return infuraWebSocket("mainnet", projectId, projectSecret)
        }

        fun goerliInfuraWebSocket(projectId: String, projectSecret: String? = null): WebSocket {
            return infuraWebSocket("goerli", projectId, projectSecret)
        }

        fun bscRpcHttp(): Http {
            return Http(listOf(URI("https://bscrpc.com")), null)
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
            return Http(listOf(URI("https://polygon-rpc.com")), null)
        }

        fun optimismRpcHttp(): Http {
            return Http(listOf(URI("https://mainnet.optimism.io")), null)
        }

        fun arbitrumOneRpcHttp(): Http {
            return Http(listOf(URI("https://arb1.arbitrum.io/rpc")), null)
        }

        fun avaxNetworkHttp(): Http {
            return Http(listOf(URI("https://api.avax.network/ext/bc/C/rpc")), null)
        }

        fun gnosisRpcHttp(): Http {
            return Http(listOf(URI("https://rpc.gnosischain.com")), null)
        }

        fun fantomRpcHttp(): Http {
            return Http(listOf(URI("https://rpc.fantom.network")), null)
        }

    }
}
