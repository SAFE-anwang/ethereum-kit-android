package io.horizontalsystems.erc20kit.core

import io.horizontalsystems.erc20kit.core.room.Erc20KitDatabase
import io.horizontalsystems.erc20kit.models.TokenBalance
import java.math.BigInteger

class Erc20Storage(
        database: Erc20KitDatabase
) : ITokenBalanceStorage {

    private val tokenBalanceDao = database.tokenBalanceDao

    override fun getBalance(): Pair<BigInteger, BigInteger>? {
        tokenBalanceDao.getBalance()?.let {
            return Pair(it.value, it.lockValue)
        }
        return null
    }

    override fun save(balance: BigInteger, lockBalance: BigInteger) {
        tokenBalanceDao.insert(TokenBalance(balance, lockBalance))
    }

}
