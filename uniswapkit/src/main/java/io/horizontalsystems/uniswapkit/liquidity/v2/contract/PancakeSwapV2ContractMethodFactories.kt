package io.horizontalsystems.uniswapkit.liquidity.contract

import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactories
import io.horizontalsystems.uniswapkit.liquidity.router.AddLiquidityMethod
import io.horizontalsystems.uniswapkit.v3.router.*

object PancakeSwapV2ContractMethodFactories : ContractMethodFactories() {
    init {
        val swapContractMethodFactories = listOf(
            AddLiquidityMethod.Factory(),
            MulticallMethod.Factory(PancakeSwapV2ContractMethodFactories),
        )
        registerMethodFactories(swapContractMethodFactories)
    }
}
