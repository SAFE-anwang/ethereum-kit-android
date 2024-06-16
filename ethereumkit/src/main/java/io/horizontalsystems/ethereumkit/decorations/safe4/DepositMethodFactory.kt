package io.horizontalsystems.ethereumkit.decorations.safe4

import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactory
import io.horizontalsystems.ethereumkit.contracts.ContractMethodHelper
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.spv.core.toBigInteger

object DepositMethodFactory : ContractMethodFactory {

    override val methodId = ContractMethodHelper.getMethodId(SafeFourDepositMethod.methodSignature)

    override fun createMethod(inputArguments: ByteArray): SafeFourDepositMethod {
        val address = Address(inputArguments.copyOfRange(12, 32))
        val value = inputArguments.copyOfRange(32, 64).toBigInteger()
        return SafeFourDepositMethod(address, value)
    }

}
