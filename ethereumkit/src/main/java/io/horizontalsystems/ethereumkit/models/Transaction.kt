package io.horizontalsystems.ethereumkit.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.horizontalsystems.ethereumkit.core.toHexString
import java.math.BigInteger

@Entity
class Transaction(
    @PrimaryKey
    val hash: ByteArray,
    val timestamp: Long,
    var isFailed: Boolean,

    val blockNumber: Long? = null,
    val transactionIndex: Int? = null,
    val from: Address? = null,
    val to: Address? = null,
    val value: BigInteger? = null,
    val input: ByteArray? = null,
    val nonce: Long? = null,
    val gasPrice: Long? = null,
    val maxFeePerGas: Long? = null,
    val maxPriorityFeePerGas: Long? = null,
    val gasLimit: Long? = null,
    val gasUsed: Long? = null,

    var replacedWith: ByteArray? = null,
    var lockDay: Int? = null,
    var eventLogIndex: Int = 0
) {

    @delegate:Ignore
    val hashString: String by lazy {
        hash.toHexString()
    }

    fun isWithdraw(): Boolean {
        if (input == null)  return false
        return input.toHexString().startsWith("0xcd9d6fca", true)
    }

    fun isUploadTransaction(): Boolean {
        if (input == null)  return false
        return input.toHexString().startsWith("0xa6aa19d2", true)
    }
}
