package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactories

object SafeFourContractMethodFactories : ContractMethodFactories() {
    init {
        registerMethodFactories(listOf(DepositMethodFactory))
    }
}