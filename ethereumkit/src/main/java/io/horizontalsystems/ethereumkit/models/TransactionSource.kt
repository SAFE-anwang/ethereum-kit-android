package io.horizontalsystems.ethereumkit.models

class TransactionSource(val name: String, val type: SourceType) {

    fun transactionUrl(hash: String) =
        when (type) {
            is SourceType.Etherscan -> "${type.txBaseUrl}/tx/$hash"
            is SourceType.Chainstack -> "${type.txBaseUrl}/tx/$hash"
        }

    sealed class SourceType {
        class Etherscan(val apiBaseUrl: String, val txBaseUrl: String, val apiKeys: List<String>) : SourceType()

        /**
         * 通过 Chainstack RPC 节点的区块扫描同步交易（不依赖 Etherscan 等浏览器 API）。
         * [rpcUrls] 为同一链的多个可用 RPC 端点，用于负载与容错；[txBaseUrl] 仅用于拼接交易详情链接。
         */
        class Chainstack(val rpcUrls: List<String>, val txBaseUrl: String) : SourceType()
    }

    companion object {
        private fun etherscan(name: String, explorerUrl: String, apiKeys: List<String>): TransactionSource {
            return TransactionSource(
                name, SourceType.Etherscan("https://api.etherscan.io/v2/", explorerUrl, apiKeys)
            )
        }

        fun ethereum(apiKeys: List<String>): TransactionSource {
            return etherscan("etherscan.io", "https://etherscan.io", apiKeys)
        }

        fun binance(apiKeys: List<String>): TransactionSource {
            return etherscan("bscscan.com", "https://bscscan.com", apiKeys)
        }

        fun polygon(apiKeys: List<String>): TransactionSource {
            return etherscan("polygonscan.com", "https://polygonscan.com", apiKeys)
        }

        fun optimism(apiKeys: List<String>): TransactionSource {
            return etherscan("optimistic.etherscan.io", "https://optimistic.etherscan.io", apiKeys)
        }

        fun arbitrumOne(apiKeys: List<String>): TransactionSource {
            return etherscan("arbiscan.io", "https://arbiscan.io", apiKeys)
        }

        fun avalanche(apiKeys: List<String>): TransactionSource {
            return etherscan("snowtrace.io", "https://snowtrace.io", apiKeys)
        }

        fun gnosis(apiKeys: List<String>): TransactionSource {
            return etherscan("gnosisscan.io", "https://gnosisscan.io", apiKeys)
        }

        fun base(apiKeys: List<String>): TransactionSource {
            return etherscan("basescan.org", "https://basescan.org", apiKeys)
        }

        fun fantom(apiKeys: List<String>): TransactionSource {
            return etherscan("ftmscan.com", "https://ftmscan.com", apiKeys)
        }

        fun zkSync(apiKeys: List<String>): TransactionSource {
            return etherscan("era.zksync.network", "https://era.zksync.network", apiKeys)
        }

        fun safeFourscan(apiKey: String): TransactionSource {
            return TransactionSource(
                if (Chain.SafeFour.isSafe4TestNetId) {
                    "safe4testnet.anwang.com"
                } else {
                    "safe4.anwang.com"
                },
                    if (Chain.SafeFour.isSafe4TestNetId) {
                        SourceType.Etherscan("https://safe4testnet.anwang.com/", "https://safe4testnet.anwang.com", listOf(apiKey))
                    } else {
                        SourceType.Etherscan("https://safe4.anwang.com/", "https://safe4.anwang.com", listOf(apiKey))
                    }

            )
        }

        /**
         * Chainstack RPC 区块扫描数据源。
         * 名称统一为 "chainstack"，配合各链的浏览器地址用于展示交易详情链接。
         */
        fun chainstack(rpcUrls: List<String>, explorerUrl: String): TransactionSource {
            return TransactionSource(
                "chainstack",
                SourceType.Chainstack(rpcUrls, explorerUrl)
            )
        }
    }

}
