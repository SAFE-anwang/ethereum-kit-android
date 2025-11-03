package io.horizontalsystems.erc20kit.core

import io.horizontalsystems.ethereumkit.models.Address
import io.reactivex.Single
import java.math.BigInteger

interface IBalanceManagerListener {
    fun onSyncBalanceSuccess(balance: Pair<BigInteger, BigInteger>)
    fun onSyncBalanceError(error: Throwable)
}

interface IBalanceManager {
    var listener: IBalanceManagerListener?

    val balance: Pair<BigInteger, BigInteger>?
    fun sync()
}

interface ITokenBalanceStorage {
    fun getBalance(): Pair<BigInteger, BigInteger>?
    fun save(balance: BigInteger, lockBalance: BigInteger)
}

interface IDataProvider {
    fun getBalance(contractAddress: Address, address: Address): Single<Pair<BigInteger, BigInteger>>
}
