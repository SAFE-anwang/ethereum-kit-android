package io.horizontalsystems.ethereumkit.decorations.safe4

import android.util.Log
import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactories
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.spv.core.toInt

object SafeFourContractMethodFactories : ContractMethodFactories() {
    init {
        Log.d("createMethod", "${USDTCrossMethodFactory.methodId.toInt()}")
        registerMethodFactories(listOf(DepositMethodFactory, USDTCrossMethodFactory))
    }
}