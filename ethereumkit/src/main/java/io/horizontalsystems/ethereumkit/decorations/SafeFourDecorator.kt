package io.horizontalsystems.ethereumkit.decorations

import io.horizontalsystems.ethereumkit.contracts.ContractEventInstance
import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.core.ITransactionDecorator
import io.horizontalsystems.ethereumkit.decorations.safe4.Safe4DepositIncomingDecoration
import io.horizontalsystems.ethereumkit.decorations.safe4.Safe4DepositOutgoingDecoration
import io.horizontalsystems.ethereumkit.decorations.safe4.SafeFourDepositMethod
import io.horizontalsystems.ethereumkit.decorations.safe4.USDTCrossMethod
import io.horizontalsystems.ethereumkit.decorations.safe4.USDTTranferOutgoingDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import java.math.BigInteger

class SafeFourDecorator(private val address: Address) : ITransactionDecorator {

    override fun decoration(from: Address?, to: Address?, value: BigInteger?, contractMethod: ContractMethod?, internalTransactions: List<InternalTransaction>, eventInstances: List<ContractEventInstance>, isLock: Boolean): TransactionDecoration? {
        if (from == null || value == null) return null
        if (to == null) return ContractCreationDecoration()

        if (contractMethod != null && contractMethod is USDTCrossMethod) {
            return USDTTranferOutgoingDecoration(from, value, to)
        }
        if (contractMethod != null && contractMethod is SafeFourDepositMethod) {
            if (from == address) {
                return Safe4DepositOutgoingDecoration(to, value, to == address)
            }

            if (to == address) {
                return Safe4DepositIncomingDecoration(from, value)
            }
        }

        if (isLock && to == address) {
            return Safe4DepositIncomingDecoration(from, value)
        }

        return null
    }

}
