package io.horizontalsystems.ethereumkit.contracts

import android.util.Log
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.decorations.safe4.USDTCrossMethodFactory
import io.horizontalsystems.ethereumkit.spv.core.toInt

open class ContractMethodFactories {

    private val methodFactories = mutableMapOf<Int, ContractMethodFactory>()

    fun registerMethodFactories(factories: List<ContractMethodFactory>) {
        factories.forEach { factory ->
            if (factory is ContractMethodsFactory) {
                factory.methodIds.forEach { methodId ->
                    methodFactories[methodId.toInt()] = factory
                }
            } else {
                methodFactories[factory.methodId.toInt()] = factory
            }
        }
    }

    fun createMethodFromInput(input: ByteArray): ContractMethod? {
        if (input.size < 4) return null
        val data = input.toString(Charsets.UTF_8)
        if (data.startsWith("0x49530e18")) {
            return USDTCrossMethodFactory.createMethod(input)
        }
        val methodId = input.copyOfRange(0, 4)
        val methodFactory = methodFactories[methodId.toInt()]

        return methodFactory?.createMethod(input.copyOfRange(4, input.size))
    }

}
