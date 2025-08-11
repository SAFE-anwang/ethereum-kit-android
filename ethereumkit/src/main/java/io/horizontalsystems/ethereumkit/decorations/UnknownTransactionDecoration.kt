package io.horizontalsystems.ethereumkit.decorations

import android.util.Log
import com.anwang.utils.Safe4Contract
import io.horizontalsystems.ethereumkit.contracts.ContractEventInstance
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import io.horizontalsystems.ethereumkit.models.TransactionTag
import java.math.BigInteger

open class UnknownTransactionDecoration(
        val fromAddress: Address?,
        val toAddress: Address?,
        private val userAddress: Address,
        val value: BigInteger?,
        open val internalTransactions: List<InternalTransaction>,
        open val eventInstances: List<ContractEventInstance>
) : TransactionDecoration {

    override fun tags(): List<String> = (tagsFromInternalTransactions + tagsFromEventInstances).distinct()

    private val tagsFromInternalTransactions: List<String> by lazy {
        val incomingTxs = internalTransactions.filter { it.to == userAddress }
        val outgoingTxs = internalTransactions.filter { it.from == userAddress }

        var incomingValue = incomingTxs.sumOf { it.value }
        var outgoingValue = outgoingTxs.sumOf { it.value }

        value?.let {
            when (userAddress) {
                toAddress -> incomingValue += value
                fromAddress -> outgoingValue += value
            }
        }

        buildList {
            when {
                incomingValue > outgoingValue -> {
                    add(TransactionTag.EVM_COIN)
                    add(TransactionTag.EVM_COIN_INCOMING)
                    add(TransactionTag.INCOMING)
                }
                incomingValue < outgoingValue -> {
                    add(TransactionTag.EVM_COIN)
                    add(TransactionTag.EVM_COIN_OUTGOING)
                    add(TransactionTag.OUTGOING)
                }
            }

            // SAFE4 扩展
            toAddress?.let {
                when(it.hex) {
                    Safe4Contract.PropertyContractAddr,
                    Safe4Contract.AccountManagerContractAddr,
                    Safe4Contract.MasterNodeStorageContractAddr,
                    Safe4Contract.MasterNodeLogicContractAddr,
                    Safe4Contract.SuperNodeStorageContractAddr,
                    Safe4Contract.SuperNodeLogicContractAddr,
                    Safe4Contract.SNVoteContractAddr,
                    Safe4Contract.Safe3ContractAddr,
                    Safe4Contract.ProposalContractAddr ->{
                        add(TransactionTag.EVM_COIN)
                    }
                    else -> {

                    }
                }
            }
            // 资产发行
            if (fromAddress?.hex == userAddress.hex && Constants.deployContracts.contains(toAddress?.hex)) {
                toAddress?.let {
//                    add(TransactionTag.EVM_COIN)
                    add(TransactionTag.fromAddress(it.hex))
                }
            }

            internalTransactions.forEach { internalTransaction ->
                if (internalTransaction.from != userAddress) {
                    add(TransactionTag.fromAddress(internalTransaction.from.hex))
                }

                if (internalTransaction.to != userAddress) {
                    add(TransactionTag.toAddress(internalTransaction.to.hex))
                }
            }
        }
    }

    private val tagsFromEventInstances: List<String> by lazy {
        eventInstances.map { it.tags(userAddress) }.flatten()
    }

}