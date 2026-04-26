package io.horizontalsystems.ethereumkit.core

import android.util.Log
import io.horizontalsystems.ethereumkit.models.*
import io.horizontalsystems.ethereumkit.network.EtherscanService
import io.reactivex.Single

class EtherscanTransactionProvider(
    private val etherscanService: EtherscanService,
    private val backupService: EtherscanService,
    private val address: Address
) : ITransactionProvider {

    override fun getTransactions(startBlock: Long): Single<List<ProviderTransaction>> {
        return etherscanService.getTransactionList(address, startBlock)
            .map { response ->
                response.result.distinctBy { it["hash"] }.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx["blockHash"]?.hexStringToByteArray()
                        val transactionIndex = tx.getValue("transactionIndex").toInt()
                        val from = Address(tx.getValue("from"))
                        val to = getAddressOrNull(tx["to"])
                        val value = tx.getValue("value").toBigInteger()
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val isError = tx["isError"]?.toIntOrNull()
                        val txReceiptStatus = tx["txreceipt_status"]?.toIntOrNull()
                        val input = tx.getValue("input").hexStringToByteArray()
                        val cumulativeGasUsed = tx["cumulativeGasUsed"]?.toLongOrNull()
                        val gasUsed = tx["gasUsed"]?.toLongOrNull()

                        ProviderTransaction(
                            blockNumber, timestamp, hash, nonce, blockHash, transactionIndex, from, to, value, gasLimit, gasPrice,
                            isError, txReceiptStatus, input, cumulativeGasUsed, gasUsed
                        )

                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }
            .onErrorReturn {
                getBackupServiceTransactions(startBlock)
            }
    }

    fun getBackupServiceTransactions(startBlock: Long): List<ProviderTransaction> {
        return backupService.getTransactionList(address, startBlock)
            .map { response ->
                Log.d("longwen", "response=$response")
                response.result.distinctBy { it["hash"] }.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx["blockHash"]?.hexStringToByteArray()
                        val transactionIndex = tx.getValue("transactionIndex").toInt()
                        val from = Address(tx.getValue("from"))
                        val to = getAddressOrNull(tx["to"])
                        val value = tx.getValue("value").toBigInteger()
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val isError = tx["isError"]?.toIntOrNull()
                        val txReceiptStatus = tx["txreceipt_status"]?.toIntOrNull()
                        val input = tx.getValue("input").hexStringToByteArray()
                        val cumulativeGasUsed = tx["cumulativeGasUsed"]?.toLongOrNull()
                        val gasUsed = tx["gasUsed"]?.toLongOrNull()

                        ProviderTransaction(
                            blockNumber, timestamp, hash, nonce, blockHash, transactionIndex, from, to, value, gasLimit, gasPrice,
                            isError, txReceiptStatus, input, cumulativeGasUsed, gasUsed
                        )

                    } catch (throwable: Throwable) {
                        Log.d("longwen", "error=$throwable")
                        null
                    }
                }
            }.blockingGet()
    }

    override fun getInternalTransactions(startBlock: Long): Single<List<ProviderInternalTransaction>> {
        return etherscanService.getInternalTransactionList(address, startBlock)
            .map { response ->
                response.result.mapNotNull { internalTx ->
                    try {
                        val hash = internalTx.getValue("hash").hexStringToByteArray()
                        val blockNumber = internalTx.getValue("blockNumber").toLong()
                        val timestamp = internalTx.getValue("timeStamp").toLong()
                        val from = Address(internalTx.getValue("from"))
                        val to = Address(internalTx.getValue("to"))
                        val value = internalTx.getValue("value").toBigInteger()
                        val traceId = internalTx.getValue("traceId")

                        ProviderInternalTransaction(hash, blockNumber, timestamp, from, to, value, traceId)
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }
            .onErrorReturn {
                getBackupInternalTransactions(startBlock)
            }
    }

    fun getBackupInternalTransactions(startBlock: Long): List<ProviderInternalTransaction> {
        return backupService.getInternalTransactionList(address, startBlock)
            .map { response ->
                response.result.mapNotNull { internalTx ->
                    try {
                        val hash = internalTx.getValue("hash").hexStringToByteArray()
                        val blockNumber = internalTx.getValue("blockNumber").toLong()
                        val timestamp = internalTx.getValue("timeStamp").toLong()
                        val from = Address(internalTx.getValue("from"))
                        val to = Address(internalTx.getValue("to"))
                        val value = internalTx.getValue("value").toBigInteger()
                        val traceId = internalTx.getValue("traceId")

                        ProviderInternalTransaction(hash, blockNumber, timestamp, from, to, value, traceId)
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }.blockingGet()
    }

    override fun getInternalTransactionsAsync(hash: ByteArray): Single<List<ProviderInternalTransaction>> {
        return etherscanService.getInternalTransactionsAsync(hash)
            .map { response ->
                response.result.mapNotNull { internalTx ->
                    try {
                        val blockNumber = internalTx.getValue("blockNumber").toLong()
                        val timestamp = internalTx.getValue("timeStamp").toLong()
                        val from = Address(internalTx.getValue("from"))
                        val to = Address(internalTx.getValue("to"))
                        val value = internalTx.getValue("value").toBigInteger()
                        val traceId = internalTx.getValue("traceId")

                        ProviderInternalTransaction(hash, blockNumber, timestamp, from, to, value, traceId)
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }
            .onErrorReturn {
                getBackupInternalTransactionsAsync(hash)
            }
    }

    override fun getTokenTransactions(startBlock: Long): Single<List<ProviderTokenTransaction>> {
        return etherscanService.getTokenTransactions(address, startBlock)
            .map { response ->
                response.result.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx.getValue("blockHash").hexStringToByteArray()
                        val from = Address(tx.getValue("from"))
                        val contractAddress = Address(tx.getValue("contractAddress"))
                        val to = Address(tx.getValue("to"))
                        val value = tx.getValue("value").toBigInteger()
                        val tokenName = tx.getValue("tokenName")
                        val tokenSymbol = tx.getValue("tokenSymbol")
                        val tokenDecimal = tx.getValue("tokenDecimal").toInt()
                        val transactionIndex = tx.get("transactionIndex")?.toInt() ?: 0
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val gasUsed = tx.getValue("gasUsed").toLong()
                        val cumulativeGasUsed = try {
                            tx.getValue("cumulativeGasUsed").toLong()
                        } catch (e: Exception) {
                            0L
                        }

                        ProviderTokenTransaction(
                            blockNumber, timestamp, hash, nonce, blockHash, from, contractAddress, to, value, tokenName, tokenSymbol, tokenDecimal,
                            transactionIndex, gasLimit, gasPrice, gasUsed, cumulativeGasUsed
                        )

                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }
            .onErrorReturn {
                getBackupTokenTransactions(startBlock)
            }
    }

    override fun getEip721Transactions(startBlock: Long): Single<List<ProviderEip721Transaction>> {
        return etherscanService.getEip721Transactions(address, startBlock)
            .map { response ->
                response.result.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx.getValue("blockHash").hexStringToByteArray()
                        val from = Address(tx.getValue("from"))
                        val contractAddress = Address(tx.getValue("contractAddress"))
                        val to = Address(tx.getValue("to"))
                        val tokenId = tx.getValue("tokenID").toBigInteger()
                        val tokenName = tx.getValue("tokenName")
                        val tokenSymbol = tx.getValue("tokenSymbol")
                        val tokenDecimal = tx.getValue("tokenDecimal").toInt()
                        val transactionIndex = tx.getValue("transactionIndex").toInt()
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val gasUsed = tx.getValue("gasUsed").toLong()
                        val cumulativeGasUsed = tx.getValue("cumulativeGasUsed").toLong()

                        ProviderEip721Transaction(
                            blockNumber = blockNumber,
                            timestamp = timestamp,
                            hash = hash,
                            nonce = nonce,
                            blockHash = blockHash,
                            transactionIndex = transactionIndex,
                            gasLimit = gasLimit,
                            gasPrice = gasPrice,
                            gasUsed = gasUsed,
                            cumulativeGasUsed = cumulativeGasUsed,
                            contractAddress = contractAddress,
                            from = from,
                            to = to,
                            tokenId = tokenId,
                            tokenName = tokenName,
                            tokenSymbol = tokenSymbol,
                            tokenDecimal = tokenDecimal
                        )
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }
            .onErrorReturn {
                getBackupEip721Transactions(startBlock)
            }
    }

    override fun getEip1155Transactions(startBlock: Long): Single<List<ProviderEip1155Transaction>> {
        return etherscanService.getEip1155Transactions(address, startBlock)
            .map { response ->
                response.result.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx.getValue("blockHash").hexStringToByteArray()
                        val from = Address(tx.getValue("from"))
                        val contractAddress = Address(tx.getValue("contractAddress"))
                        val to = Address(tx.getValue("to"))

                        val tokenId = tx.getValue("tokenID").toBigInteger()
                        val tokenValue = tx.getValue("tokenValue").toInt()
                        val tokenName = tx.getValue("tokenName")
                        val tokenSymbol = tx.getValue("tokenSymbol")
                        val transactionIndex = tx.getValue("transactionIndex").toInt()
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val gasUsed = tx.getValue("gasUsed").toLong()
                        val cumulativeGasUsed = tx.getValue("cumulativeGasUsed").toLong()

                        ProviderEip1155Transaction(
                            blockNumber = blockNumber,
                            timestamp = timestamp,
                            hash = hash,
                            nonce = nonce,
                            blockHash = blockHash,
                            transactionIndex = transactionIndex,
                            gasLimit = gasLimit,
                            gasPrice = gasPrice,
                            gasUsed = gasUsed,
                            cumulativeGasUsed = cumulativeGasUsed,
                            contractAddress = contractAddress,
                            from = from,
                            to = to,
                            tokenId = tokenId,
                            tokenValue = tokenValue,
                            tokenName = tokenName,
                            tokenSymbol = tokenSymbol
                        )
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }
            .onErrorReturn {
                getBackupEip1155Transactions(startBlock)
            }

    }

    fun getBackupInternalTransactionsAsync(hash: ByteArray): List<ProviderInternalTransaction> {
        return backupService.getInternalTransactionsAsync(hash)
            .map { response ->
                response.result.mapNotNull { internalTx ->
                    try {
                        val blockNumber = internalTx.getValue("blockNumber").toLong()
                        val timestamp = internalTx.getValue("timeStamp").toLong()
                        val from = Address(internalTx.getValue("from"))
                        val to = Address(internalTx.getValue("to"))
                        val value = internalTx.getValue("value").toBigInteger()
                        val traceId = internalTx.getValue("traceId")

                        ProviderInternalTransaction(hash, blockNumber, timestamp, from, to, value, traceId)
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }.blockingGet()
    }

    fun getBackupTokenTransactions(startBlock: Long): List<ProviderTokenTransaction> {
        return backupService.getTokenTransactions(address, startBlock)
            .map { response ->
                response.result.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx.getValue("blockHash").hexStringToByteArray()
                        val from = Address(tx.getValue("from"))
                        val contractAddress = Address(tx.getValue("contractAddress"))
                        val to = Address(tx.getValue("to"))
                        val value = tx.getValue("value").toBigInteger()
                        val tokenName = tx.getValue("tokenName")
                        val tokenSymbol = tx.getValue("tokenSymbol")
                        val tokenDecimal = tx.getValue("tokenDecimal").toInt()
                        val transactionIndex = tx.get("transactionIndex")?.toInt() ?: 0
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val gasUsed = tx.getValue("gasUsed").toLong()
                        val cumulativeGasUsed = try {
                            tx.getValue("cumulativeGasUsed").toLong()
                        } catch (e: Exception) {
                            0L
                        }

                        ProviderTokenTransaction(
                            blockNumber, timestamp, hash, nonce, blockHash, from, contractAddress, to, value, tokenName, tokenSymbol, tokenDecimal,
                            transactionIndex, gasLimit, gasPrice, gasUsed, cumulativeGasUsed
                        )

                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }.blockingGet()
    }

    fun getBackupEip721Transactions(startBlock: Long): List<ProviderEip721Transaction> {
        return backupService.getEip721Transactions(address, startBlock)
            .map { response ->
                response.result.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx.getValue("blockHash").hexStringToByteArray()
                        val from = Address(tx.getValue("from"))
                        val contractAddress = Address(tx.getValue("contractAddress"))
                        val to = Address(tx.getValue("to"))
                        val tokenId = tx.getValue("tokenID").toBigInteger()
                        val tokenName = tx.getValue("tokenName")
                        val tokenSymbol = tx.getValue("tokenSymbol")
                        val tokenDecimal = tx.getValue("tokenDecimal").toInt()
                        val transactionIndex = tx.getValue("transactionIndex").toInt()
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val gasUsed = tx.getValue("gasUsed").toLong()
                        val cumulativeGasUsed = tx.getValue("cumulativeGasUsed").toLong()

                        ProviderEip721Transaction(
                            blockNumber = blockNumber,
                            timestamp = timestamp,
                            hash = hash,
                            nonce = nonce,
                            blockHash = blockHash,
                            transactionIndex = transactionIndex,
                            gasLimit = gasLimit,
                            gasPrice = gasPrice,
                            gasUsed = gasUsed,
                            cumulativeGasUsed = cumulativeGasUsed,
                            contractAddress = contractAddress,
                            from = from,
                            to = to,
                            tokenId = tokenId,
                            tokenName = tokenName,
                            tokenSymbol = tokenSymbol,
                            tokenDecimal = tokenDecimal
                        )
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }.blockingGet()
    }

    fun getBackupEip1155Transactions(startBlock: Long): List<ProviderEip1155Transaction> {
        return backupService.getEip1155Transactions(address, startBlock)
            .map { response ->
                response.result.mapNotNull { tx ->
                    try {
                        val blockNumber = tx.getValue("blockNumber").toLong()
                        val timestamp = tx.getValue("timeStamp").toLong()
                        val hash = tx.getValue("hash").hexStringToByteArray()
                        val nonce = tx.getValue("nonce").toLong()
                        val blockHash = tx.getValue("blockHash").hexStringToByteArray()
                        val from = Address(tx.getValue("from"))
                        val contractAddress = Address(tx.getValue("contractAddress"))
                        val to = Address(tx.getValue("to"))

                        val tokenId = tx.getValue("tokenID").toBigInteger()
                        val tokenValue = tx.getValue("tokenValue").toInt()
                        val tokenName = tx.getValue("tokenName")
                        val tokenSymbol = tx.getValue("tokenSymbol")
                        val transactionIndex = tx.getValue("transactionIndex").toInt()
                        val gasLimit = tx.getValue("gas").toLong()
                        val gasPrice = tx.getValue("gasPrice").toLong()
                        val gasUsed = tx.getValue("gasUsed").toLong()
                        val cumulativeGasUsed = tx.getValue("cumulativeGasUsed").toLong()

                        ProviderEip1155Transaction(
                            blockNumber = blockNumber,
                            timestamp = timestamp,
                            hash = hash,
                            nonce = nonce,
                            blockHash = blockHash,
                            transactionIndex = transactionIndex,
                            gasLimit = gasLimit,
                            gasPrice = gasPrice,
                            gasUsed = gasUsed,
                            cumulativeGasUsed = cumulativeGasUsed,
                            contractAddress = contractAddress,
                            from = from,
                            to = to,
                            tokenId = tokenId,
                            tokenValue = tokenValue,
                            tokenName = tokenName,
                            tokenSymbol = tokenSymbol
                        )
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            }.blockingGet()

    }

    private fun getAddressOrNull(addressString: String?): Address? =
        if (!addressString.isNullOrEmpty()) Address(addressString) else null


    override fun getSafeAccountManagerTransactions(startBlock: Long): Single<List<Safe4AccountManagerTransaction>> {
        return etherscanService.getSafeAccountManagerTransactions(address, startBlock)
                .map { response ->
                    response.result.mapNotNull { internalTx ->
                        try {
                            val hash = internalTx.getValue("hash").hexStringToByteArray()
                            val blockNumber = internalTx.getValue("blockNumber").toLong()
                            val timestamp = internalTx.getValue("timeStamp").toLong()
                            val from = Address(internalTx.getValue("from"))
                            val to = Address(internalTx.getValue("to"))
                            val value = internalTx.getValue("amount").toBigInteger()
                            val action = internalTx.getValue("action")
                            val lockId = internalTx.getValue("lockId")
                            val lockDay = internalTx.getValue("lockDay").toInt()
                            val eventLogIndex = internalTx.getValue("eventLogIndex").toInt()

                            Safe4AccountManagerTransaction(hash, blockNumber, timestamp, from, to, value, action, lockId, lockDay, eventLogIndex)
                        } catch (throwable: Throwable) {
                            null
                        }
                    }
                }
    }

}
