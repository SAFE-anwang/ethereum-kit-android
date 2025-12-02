package io.horizontalsystems.erc20kit.models

import androidx.room.Entity
import java.math.BigInteger

@Entity(primaryKeys = ["primaryKey"])
class TokenBalance(
        val value: BigInteger,
        val lockValue: BigInteger = BigInteger.ZERO,
        val primaryKey: String = "primaryKey"
)
