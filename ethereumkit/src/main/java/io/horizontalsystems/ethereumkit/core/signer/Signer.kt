package io.horizontalsystems.ethereumkit.core.signer

import io.horizontalsystems.ethereumkit.core.HDWalletDelegate
import io.horizontalsystems.ethereumkit.core.TransactionBuilder
import io.horizontalsystems.ethereumkit.core.TransactionSigner
import io.horizontalsystems.ethereumkit.core.hexStringToByteArrayOrNull
import io.horizontalsystems.ethereumkit.core.signer.Signer.PrivateKeyValidationError.InvalidDataLength
import io.horizontalsystems.ethereumkit.core.signer.Signer.PrivateKeyValidationError.InvalidDataString
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.crypto.TypedData
import io.horizontalsystems.ethereumkit.models.*
import io.horizontalsystems.ethereumkit.spv.core.toBigInteger
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import java.math.BigInteger

class Signer(
    private val transactionBuilder: TransactionBuilder,
    private val transactionSigner: TransactionSigner,
    private val ethSigner: EthSigner,
    val privateKey: BigInteger
) {

    fun signature(rawTransaction: RawTransaction): Signature {
        return transactionSigner.signature(rawTransaction)
    }

    fun signedTransaction(
        address: Address,
        value: BigInteger,
        transactionInput: ByteArray,
        gasPrice: GasPrice,
        gasLimit: Long,
        nonce: Long
    ): ByteArray {
        val rawTransaction = RawTransaction(
            gasPrice,
            gasLimit,
            address,
            value,
            nonce,
            transactionInput
        )
        val signature = transactionSigner.signature(rawTransaction)
        return transactionBuilder.encode(rawTransaction, signature)
    }

    fun signByteArray(message: ByteArray): ByteArray {
        return ethSigner.signByteArray(message)
    }

    fun signByteArrayLegacy(message: ByteArray): ByteArray {
        return ethSigner.signByteArrayLegacy(message)
    }

    fun signTypedData(rawJsonMessage: String): ByteArray {
        return ethSigner.signTypedData(rawJsonMessage)
    }

    fun parseTypedData(rawJsonMessage: String): TypedData? {
        return ethSigner.parseTypedData(rawJsonMessage)
    }

    companion object {
        fun getInstance(privateKey: BigInteger, chain: Chain, isAnBaoWallet: Boolean = false): Signer {
            val address = address(privateKey)

            val transactionSigner = TransactionSigner(privateKey, chain.id)
            val transactionBuilder = TransactionBuilder(address, chain.id)
            val ethSigner = EthSigner(privateKey, CryptoUtils, EIP712Encoder())

            return Signer(transactionBuilder, transactionSigner, ethSigner, privateKey)
        }

        fun getInstance(seed: ByteArray, chain: Chain, isAnBaoWallet: Boolean): Signer {
            return getInstance(privateKey(seed, chain, isAnBaoWallet), chain, isAnBaoWallet)
        }

        fun address(
            words: List<String>,
            passphrase: String = "",
            chain: Chain,
            isAnBaoWallet: Boolean
        ): Address {
            return address(Mnemonic().toSeed(words, passphrase), chain, isAnBaoWallet)
        }

        fun address(seed: ByteArray, chain: Chain, isAnBaoWallet: Boolean): Address {
            val privateKey = privateKey(seed, chain, isAnBaoWallet)
            return address(privateKey)
        }

        fun privateKey(
            words: List<String>,
            passphrase: String = "",
            chain: Chain,
            isAnBaoWallet: Boolean
        ): BigInteger {
            return privateKey(Mnemonic().toSeed(words, passphrase), chain, isAnBaoWallet)
        }

        @Throws(InvalidDataString::class, InvalidDataLength::class)
        fun privateKey(value: String): BigInteger {
            val data = value.hexStringToByteArrayOrNull()
                ?: throw InvalidDataString()
            if (data.size != 32) {
                throw InvalidDataLength()
            }
            return data.toBigInteger()
        }

        fun privateKey(seed: ByteArray, chain: Chain, isAnBaoWallet: Boolean = false): BigInteger {
//            val hdWallet = HDWallet(seed, chain.coinType, HDWallet.Purpose.BIP44)
            val hdWallet = HDWalletDelegate(seed, chain.coinType, HDWallet.Purpose.BIP44, isAnBaoWallet, anBaoCoinType = chain.anBaoCoinType)
            return hdWallet.privateKey(0, 0, true).privKey
        }

        fun address(privateKey: BigInteger): Address {
            val publicKey =
                CryptoUtils.ecKeyFromPrivate(privateKey).publicKeyPoint.getEncoded(false).drop(1)
                    .toByteArray()
            return Address(CryptoUtils.sha3(publicKey).takeLast(20).toByteArray())
        }

        fun getAnBaoPrivateKeys(seed: ByteArray, chain: Chain): List<BigInteger> {
            val hdWallet = HDWalletDelegate(seed, chain.coinType, HDWallet.Purpose.BIP44, true, anBaoCoinType = chain.anBaoCoinType)
            val privateKeys = mutableListOf<BigInteger>()
            repeat(5) {
                privateKeys.add(hdWallet.privateKey(0, it, true).privKey)
            }
            return privateKeys
        }
    }

    open class PrivateKeyValidationError : Exception() {
        class InvalidDataString : PrivateKeyValidationError()
        class InvalidDataLength : PrivateKeyValidationError()
    }
}
