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

    override fun getBalance(contractAddress: Address, address: Address): Single<BigInteger> {
        return ethereumKit.call(contractAddress, BalanceOfMethod(address).encodedABI())
            .map { data ->
                // Non-existent contracts (common after Safe4 mainnet→testnet switch) return
                // empty 0x. Slicing that throws and leaves every SRC20 row in "同步错误".
                if (data.size < 32) {
                    BigInteger.ZERO
                } else {
                    data.sliceArray(IntRange(0, 31)).toBigInteger()
                }
            }
            .onErrorReturnItem(BigInteger.ZERO)
    }

}
