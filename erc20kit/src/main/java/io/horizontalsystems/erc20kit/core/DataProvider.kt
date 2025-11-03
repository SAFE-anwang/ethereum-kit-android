package io.horizontalsystems.erc20kit.core

import android.util.Log
import io.horizontalsystems.erc20kit.contract.BalanceOfMethod
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.spv.core.toBigInteger
import io.reactivex.Single
import java.math.BigInteger

class DataProvider(
        private val ethereumKit: EthereumKit
) : IDataProvider {

    override fun getBalance(contractAddress: Address, address: Address): Single<Pair<BigInteger, BigInteger>> {
        val balanceSingle = ethereumKit.call(contractAddress, BalanceOfMethod(address).encodedABI())
            .map { it.sliceArray(IntRange(0, 31)).toBigInteger() }
        val lockBalanceSingle = ethereumKit.getLockBalance(contractAddress)
       return Single.zip(
           balanceSingle,
           lockBalanceSingle,
       ) { balance, lockBalance ->
           Log.d("DataProvider", "balance=$balance, lockBalance=$lockBalance")
           Pair(balance, lockBalance)
       }
    }

}
