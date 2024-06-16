package io.horizontalsystems.ethereumkit.core

import com.anwang.utils.Safe4Contract
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.models.*
import io.horizontalsystems.ethereumkit.spv.core.toBigInteger
import io.horizontalsystems.ethereumkit.spv.rlp.RLP
import java.math.BigInteger

class Safe4TransactionBuilder(
        private val address: Address,
        val chainId: Int
) {

    fun transaction(rawTransaction: RawTransaction, signature: Signature): Transaction {
        val transactionHash = CryptoUtils.sha3(encode(rawTransaction, signature))
        var maxFeePerGas: Long? = null
        var maxPriorityFeePerGas: Long? = null

        if (rawTransaction.gasPrice is GasPrice.Eip1559) {
            maxFeePerGas = rawTransaction.gasPrice.maxFeePerGas
            maxPriorityFeePerGas = rawTransaction.gasPrice.maxPriorityFeePerGas
        }

        return Transaction(
                hash = transactionHash,
                timestamp = System.currentTimeMillis() / 1000,
                nonce = rawTransaction.nonce,
                input = rawTransaction.data,
                from = address,
                to = rawTransaction.to,
                value = rawTransaction.value,
                gasPrice = rawTransaction.gasPrice.max,
                maxFeePerGas = maxFeePerGas,
                maxPriorityFeePerGas = maxPriorityFeePerGas,
                gasLimit = rawTransaction.gasLimit,
                isFailed = false,
        )
    }

    fun transactionDeposit(rawTransaction: RawTransaction, signature: Signature, lockDay: BigInteger, hash: String): Transaction {
        val data = Safe4Web3jUtils.getDepositTransactionInput(rawTransaction.to.hex, lockDay)
        val tempRawTransaction = RawTransaction(
                rawTransaction.gasPrice,
                rawTransaction.gasLimit,
                Address(Safe4Contract.AccountManagerContractAddr),
                rawTransaction.value,
                rawTransaction.nonce,
                data.hexStringToByteArray()
        )
        val transactionHash = CryptoUtils.sha3(encode(tempRawTransaction, signature))
        var maxFeePerGas: Long? = null
        var maxPriorityFeePerGas: Long? = null

        if (rawTransaction.gasPrice is GasPrice.Eip1559) {
            maxFeePerGas = rawTransaction.gasPrice.maxFeePerGas
            maxPriorityFeePerGas = rawTransaction.gasPrice.maxPriorityFeePerGas
        }

        return Transaction(
                hash = hash.hexStringToByteArray(),
                timestamp = System.currentTimeMillis() / 1000,
                nonce = rawTransaction.nonce,
                input = tempRawTransaction.data,
                from = address,
                to = tempRawTransaction.to,
                value = rawTransaction.value,
                gasPrice = rawTransaction.gasPrice.max,
                maxFeePerGas = maxFeePerGas,
                maxPriorityFeePerGas = maxPriorityFeePerGas,
                gasLimit = rawTransaction.gasLimit,
                isFailed = false,
        )
    }

    fun encode(rawTransaction: org.web3j.crypto.RawTransaction, signature: Signature): ByteArray =
            encodeDeposit(rawTransaction, signature, chainId)

    fun encode(rawTransaction: RawTransaction, signature: Signature): ByteArray =
            encode(rawTransaction, signature, chainId)

    companion object {

        fun encode(rawTransaction: RawTransaction, signature: Signature?, chainId: Int = 1): ByteArray {
            val signatureArray = signature?.let {
                arrayOf(
                        RLP.encodeInt(it.v),
                        RLP.encodeBigInteger(it.r.toBigInteger()),
                        RLP.encodeBigInteger(it.s.toBigInteger())
                )
            } ?: arrayOf()

            return when (rawTransaction.gasPrice) {
                is GasPrice.Eip1559 -> {
                    val elements = arrayOf(
                            RLP.encodeInt(chainId),
                            RLP.encodeLong(rawTransaction.nonce),
                            RLP.encodeLong(rawTransaction.gasPrice.maxPriorityFeePerGas),
                            RLP.encodeLong(rawTransaction.gasPrice.maxFeePerGas),
                            RLP.encodeLong(rawTransaction.gasLimit),
                            RLP.encodeElement(rawTransaction.to.raw),
                            RLP.encodeBigInteger(rawTransaction.value),
                            RLP.encodeElement(rawTransaction.data),
                            RLP.encode(arrayOf<Any>())
                    ) + signatureArray

                    val encodedTransaction = RLP.encodeList(*elements)
                    "0x02".hexStringToByteArray() + encodedTransaction
                }
                is GasPrice.Legacy -> {
                    val elements = arrayOf(
                            RLP.encodeLong(rawTransaction.nonce),
                            RLP.encodeLong(rawTransaction.gasPrice.legacyGasPrice),
                            RLP.encodeLong(rawTransaction.gasLimit),
                            RLP.encodeElement(rawTransaction.to.raw),
                            RLP.encodeBigInteger(rawTransaction.value),
                            RLP.encodeElement(rawTransaction.data)
                    ) + signatureArray

                    RLP.encodeList(*elements)
                }
            }
        }

        fun encodeDeposit(rawTransaction: org.web3j.crypto.RawTransaction, signature: Signature?, chainId: Int = 1): ByteArray {
            val signatureArray = signature?.let {
                arrayOf(
                        RLP.encodeInt(it.v),
                        RLP.encodeBigInteger(it.r.toBigInteger()),
                        RLP.encodeBigInteger(it.s.toBigInteger())
                )
            } ?: arrayOf()
            val elements = arrayOf(
                    RLP.encodeLong(rawTransaction.nonce.toLong()),
                    RLP.encodeLong(rawTransaction.gasPrice.toLong()),
                    RLP.encodeLong(rawTransaction.gasLimit.toLong()),
                    RLP.encodeElement(rawTransaction.to.toByteArray()),
                    RLP.encodeBigInteger(rawTransaction.value),
                    RLP.encodeElement(rawTransaction.data.toByteArray()),
                    RLP.encodeInt(chainId)
            ) + signatureArray

            return RLP.encodeList(*elements)
        }

        fun encodeDeposit(rawTransaction: RawTransaction, signature: Signature?, chainId: Int = 1): ByteArray {
            val signatureArray = signature?.let {
                arrayOf(
                        RLP.encodeInt(it.v),
                        RLP.encodeBigInteger(it.r.toBigInteger()),
                        RLP.encodeBigInteger(it.s.toBigInteger())
                )
            } ?: arrayOf()
            val elements = arrayOf(
                    RLP.encodeLong(rawTransaction.nonce),
                    RLP.encodeLong((rawTransaction.gasPrice as GasPrice.Legacy).legacyGasPrice),
                    RLP.encodeLong(rawTransaction.gasLimit),
                    RLP.encodeElement(rawTransaction.to.raw),
                    RLP.encodeBigInteger(rawTransaction.value),
                    RLP.encodeElement(rawTransaction.data),
                    RLP.encodeInt(chainId)
            ) + signatureArray

            return RLP.encodeList(*elements)
        }

    }
}
