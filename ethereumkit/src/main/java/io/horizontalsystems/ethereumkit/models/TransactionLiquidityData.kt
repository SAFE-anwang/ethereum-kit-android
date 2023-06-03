package io.horizontalsystems.ethereumkit.models

import io.horizontalsystems.ethereumkit.core.toHexString
import java.math.BigInteger
import java.util.*

data class TransactionLiquidityData(
        val to: Address,
        val value1: BigInteger,
        val value2: BigInteger,
        val input: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other is TransactionLiquidityData -> to == other.to && value1 == other.value1 && value2 == other.value2 && input.contentEquals(other.input)
            else -> false
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(to, value1, value2, input)
    }

    override fun toString(): String {
        return "TransactionData {to: ${to.hex}, value: $value1, input: ${input.toHexString()}}"
    }

}
