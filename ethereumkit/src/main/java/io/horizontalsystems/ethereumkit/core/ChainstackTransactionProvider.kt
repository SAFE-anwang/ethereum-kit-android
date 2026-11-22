package io.horizontalsystems.ethereumkit.core

import android.util.Log
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetBalanceJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetBlockWithTransactionsJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetTransactionCountJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.OtsSearchTransactionsJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.TraceFilterJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.OtsSearchPage
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTrace
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransaction
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.DefaultBlockParameter
import io.horizontalsystems.ethereumkit.models.ProviderEip1155Transaction
import io.horizontalsystems.ethereumkit.models.ProviderEip721Transaction
import io.horizontalsystems.ethereumkit.models.ProviderInternalTransaction
import io.horizontalsystems.ethereumkit.models.ProviderTokenTransaction
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Safe4AccountManagerTransaction
import io.horizontalsystems.ethereumkit.models.TransactionLog
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncerStateStorage
import io.horizontalsystems.ethereumkit.models.TransactionSyncerState
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Chainstack RPC 交易历史：优先 Otterscan（ots_*），不支持时用余额/nonce 二分定位原生币活动；
 * 代币/NFT 用链顶日志窗口 + 后台按页往旧补全。
 */
class ChainstackTransactionProvider(
    private val blockchain: IBlockchain,
    private val address: Address,
    private val scanProgressCache: TransactionSyncerStateStorage? = null,
) : ITransactionProvider {

    interface HistoryPageListener {
        fun onTransactions(transactions: List<ProviderTransaction>) {}
        fun onInternalTransactions(transactions: List<ProviderInternalTransaction>) {}
        fun onTokenTransactions(transactions: List<ProviderTokenTransaction>) {}
        fun onEip721Transactions(transactions: List<ProviderEip721Transaction>) {}
        fun onEip1155Transactions(transactions: List<ProviderEip1155Transaction>) {}
    }

    private class HistoryStopped : RuntimeException("history backfill stopped")

    private data class AccountSnapshot(val balance: BigInteger, val nonce: Long) {
        fun same(other: AccountSnapshot) = balance == other.balance && nonce == other.nonce
    }

    private data class NativeDiscoveryResult(
        val native: List<ProviderTransaction>,
        val internal: List<ProviderInternalTransaction>,
    )

    private data class LogWindow(val logs: List<TransactionLog>, val tipBlock: Long)

    companion object {
        const val TAG = "ChainstackTxProvider"

        private const val OTS_PAGE_SIZE = 25
        private const val MAX_NATIVE_ACTIVITY = 2000

        private const val TRANSFER_TIP_WINDOW = 5000L
        private const val LOGS_PAGE_BLOCKS = 1000L

        private const val TRACE_CHUNK_SIZE = 100L
        private const val TRACE_PAGE_SIZE = 500
        private const val TRACE_MAX_PAGES = 20

        private const val RECEIPT_FETCH_CONCURRENCY = 2

        private val rpcSemaphore = java.util.concurrent.Semaphore(2)
        private const val RPC_TIMEOUT_SECONDS = 30L

        private const val TRANSFER_TOPIC =
            "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
        private const val TRANSFER_SINGLE_TOPIC =
            "c3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62"

        private const val OTS_BEFORE_ID = "chainstack-ots-before"
        private const val OTS_DONE_ID = "chainstack-ots-done"
        private const val LOGS_NEXT_ID = "chainstack-logs-next"
        private const val LOGS1155_NEXT_ID = "chainstack-1155-next"
        private const val TRACE_NEXT_ID = "chainstack-trace-next"
    }

    private val addressHex = address.hex.lowercase()
    private val paddedAddress = "0x000000000000000000000000" + addressHex.removePrefix("0x")
    private val paddedAddressBytes = hexToBytes(paddedAddress)

    @Volatile
    private var scannedBlockHeight: Long = -1L

    @Volatile
    private var otsUnsupported = false

    @Volatile
    private var traceUnsupported = false

    private val accountStateCache = java.util.concurrent.ConcurrentHashMap<Long, AccountSnapshot>()

    private var historyListener: HistoryPageListener? = null
    private val backfillRunning = AtomicBoolean(false)
    private val backfillStopped = AtomicBoolean(false)
    private var backfillDisposable: CompositeDisposable? = null

    override val lastScannedBlockHeight: Long
        get() = scannedBlockHeight

    private fun readProgress(id: String): Long? =
        scanProgressCache?.get(id)?.lastBlockNumber

    private fun writeProgress(id: String, block: Long) {
        scanProgressCache?.save(TransactionSyncerState(id, block))
    }

    private fun <T> throttled(source: Single<T>): Single<T> {
        return Single.fromCallable {
            rpcSemaphore.acquire()
            try {
                source.timeout(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet()
            } finally {
                rpcSemaphore.release()
            }
        }.subscribeOn(Schedulers.io())
    }

    private fun fetchAccountState(block: Long): AccountSnapshot {
        accountStateCache[block]?.let { return it }
        val balance = blockchain.rpcSingle(
            GetBalanceJsonRpc(address, DefaultBlockParameter.BlockNumber(block))
        ).timeout(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet()
        val nonce = blockchain.rpcSingle(
            GetTransactionCountJsonRpc(address, DefaultBlockParameter.BlockNumber(block))
        ).timeout(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet()
        return AccountSnapshot(balance, nonce).also { accountStateCache[block] = it }
    }

    private fun markOtsUnsupportedIfNeeded(error: Throwable) {
        val code = (error as? JsonRpc.ResponseError.RpcError)?.error?.code
        if (code == -32601) {
            otsUnsupported = true
            Log.d(TAG, "ots namespace unsupported (code=$code)")
        }
    }

    private fun emitHistory(block: (HistoryPageListener) -> Unit) {
        historyListener?.let(block)
    }

    // region ITransactionProvider — 首屏（同步管理器）

    override fun getTransactionsInBlock(blockNumber: Long): Single<List<ProviderTransaction>> {
        return throttled(blockchain.rpcSingle(GetBlockWithTransactionsJsonRpc(blockNumber)))
            .map { block -> block.transactions.filter { isRelated(it) } to block.timestamp }
            .flatMap { (matched, timestamp) ->
                if (matched.isEmpty()) Single.just(emptyList())
                else Observable.fromIterable(matched)
                    .subscribeOn(Schedulers.io())
                    .flatMap({ tx -> receiptSingle(tx, blockNumber, timestamp).toObservable() }, RECEIPT_FETCH_CONCURRENCY)
                    .toList()
            }
            .onErrorReturn { emptyList() }
    }

    override fun getTransactions(startBlock: Long): Single<List<ProviderTransaction>> {
        val primary = if (startBlock > 1L) otsAfter(startBlock) else otsFirstPage()
        return primary.flatMap { txs ->
            if (txs.isNotEmpty() || !otsUnsupported) {
                Single.just(txs)
            } else {
                discoverNative(startBlock).map { it.native }
            }
        }.onErrorReturn { e ->
            Log.w(TAG, "getTransactions failed: $e")
            emptyList()
        }
    }

    override fun getInternalTransactions(startBlock: Long): Single<List<ProviderInternalTransaction>> {
        return otsFirstPage()
            .onErrorReturnItem(emptyList())
            .flatMap {
                if (!otsUnsupported) {
                    legacyInternalTransactions(startBlock)
                } else {
                    discoverNative(startBlock).map { it.internal }
                }
            }
            .onErrorReturn { e ->
                Log.w(TAG, "getInternalTransactions failed: $e")
                emptyList()
            }
    }

    override fun getInternalTransactionsAsync(hash: ByteArray): Single<List<ProviderInternalTransaction>> =
        Single.just(emptyList())

    override fun getTokenTransactions(startBlock: Long): Single<List<ProviderTokenTransaction>> {
        return transferWindow(startBlock)
            .map { window ->
                window.logs.mapNotNull { parseTokenTransaction(it, null) }
            }
            .doOnSuccess { if (scannedBlockHeight > 0) Unit }
            .onErrorReturn { emptyList() }
    }

    override fun getEip721Transactions(startBlock: Long): Single<List<ProviderEip721Transaction>> {
        return transferWindow(startBlock)
            .map { window ->
                window.logs.mapNotNull { parseEip721Transaction(it, null) }
            }
            .onErrorReturn { emptyList() }
    }

    override fun getEip1155Transactions(startBlock: Long): Single<List<ProviderEip1155Transaction>> {
        return transfer1155Window(startBlock)
            .map { window ->
                window.logs.mapNotNull { parseEip1155Transaction(it) }
            }
            .onErrorReturn { emptyList() }
    }

    override fun getSafeAccountManagerTransactions(startBlock: Long): Single<List<Safe4AccountManagerTransaction>> =
        Single.just(emptyList())

    // endregion

    // region Otterscan

    private fun otsSearchBefore(blockNumber: Long, pageSize: Int): Single<OtsSearchPage> {
        return blockchain.rpcSingle(OtsSearchTransactionsJsonRpc.before(addressHex, blockNumber, pageSize))
            .doOnError { markOtsUnsupportedIfNeeded(it) }
    }

    private fun otsSearchAfter(blockNumber: Long, pageSize: Int): Single<OtsSearchPage> {
        return blockchain.rpcSingle(OtsSearchTransactionsJsonRpc.after(addressHex, blockNumber, pageSize))
            .doOnError { markOtsUnsupportedIfNeeded(it) }
    }

    private fun otsFirstPage(): Single<List<ProviderTransaction>> {
        if (otsUnsupported) return Single.just(emptyList())
        return otsSearchBefore(0, OTS_PAGE_SIZE)
            .map { page -> mapOtsPage(page) }
            .onErrorReturn { e ->
                markOtsUnsupportedIfNeeded(e)
                emptyList()
            }
    }

    private fun otsAfter(startBlock: Long): Single<List<ProviderTransaction>> {
        if (otsUnsupported) return Single.just(emptyList())
        return otsSearchAfter(startBlock, OTS_PAGE_SIZE)
            .map { page -> mapOtsPage(page) }
            .onErrorReturn { e ->
                markOtsUnsupportedIfNeeded(e)
                emptyList()
            }
    }

    private fun mapOtsPage(page: OtsSearchPage): List<ProviderTransaction> {
        return page.txs.mapIndexed { index, tx ->
            if (!isRelated(tx)) return@mapIndexed null
            val receipt = page.receipts.getOrNull(index)
            val timestamp = receipt?.timestamp?.hexStringToLongOrNull() ?: 0L
            val blockNumber = tx.blockNumber ?: 0L
            ProviderTransaction(
                blockNumber = blockNumber,
                timestamp = timestamp,
                hash = tx.hash,
                nonce = tx.nonce,
                blockHash = tx.blockHash,
                transactionIndex = (tx.transactionIndex ?: 0L).toInt(),
                from = tx.from,
                to = tx.to,
                value = tx.value,
                gasLimit = tx.gasLimit,
                gasPrice = tx.gasPrice,
                input = tx.input,
                txReceiptStatus = receipt?.status?.toInt(),
            )
        }.filterNotNull()
    }

    private fun legacyInternalTransactions(startBlock: Long): Single<List<ProviderInternalTransaction>> {
        val tip = blockchain.lastBlockHeight ?: return Single.just(emptyList())
        return scanTracesInRange(startBlock, tip) { trace, ts ->
            trace.toProviderInternalTransaction(ts)
        }.onErrorReturnItem(emptyList())
    }

    // endregion

    // region 原生币：余额/nonce 二分定位

    private fun discoverNative(startBlock: Long): Single<NativeDiscoveryResult> {
        return Single.fromCallable {
            val tip = awaitHeight() ?: return@fromCallable NativeDiscoveryResult(emptyList(), emptyList())
            val lo = 0L
            val hi = tip
            val loState = fetchAccountState(lo)
            val hiState = fetchAccountState(hi)
            val native = mutableListOf<ProviderTransaction>()
            val internal = mutableListOf<ProviderInternalTransaction>()
            val hits = AtomicInteger(0)
            val capped = AtomicBoolean(false)
            searchAccountChanges(
                lo, hi, loState, hiState,
                native, internal, hits, capped,
            )
            if (hits.get() > 0) {
                scannedBlockHeight = maxOf(scannedBlockHeight, tip)
            }
            NativeDiscoveryResult(native, internal)
        }.subscribeOn(Schedulers.io())
    }

    private fun searchAccountChanges(
        lo: Long,
        hi: Long,
        loState: AccountSnapshot,
        hiState: AccountSnapshot,
        native: MutableList<ProviderTransaction>,
        internal: MutableList<ProviderInternalTransaction>,
        hits: AtomicInteger,
        capped: AtomicBoolean,
    ) {
        if (backfillStopped.get()) throw HistoryStopped()
        if (loState.same(hiState) || hi <= lo) return
        if (hits.get() >= MAX_NATIVE_ACTIVITY) {
            capped.set(true)
            return
        }
        if (hi == lo + 1) {
            val (blockNative, blockInternal) = activityAtBlock(hi)
            hits.addAndGet(blockNative.size + blockInternal.size)
            if (blockNative.isNotEmpty()) {
                native.addAll(blockNative)
                emitHistory { it.onTransactions(blockNative) }
            }
            if (blockInternal.isNotEmpty()) {
                internal.addAll(blockInternal)
                emitHistory { it.onInternalTransactions(blockInternal) }
            }
            return
        }
        val mid = lo + (hi - lo) / 2
        val midState = fetchAccountState(mid)
        searchAccountChanges(mid, hi, midState, hiState, native, internal, hits, capped)
        searchAccountChanges(lo, mid, loState, midState, native, internal, hits, capped)
    }

    private fun activityAtBlock(block: Long): Pair<List<ProviderTransaction>, List<ProviderInternalTransaction>> {
        if (traceUnsupported) {
            return blockScanAt(block)
        }
        val traces = try {
            val from = throttled(
                fetchTracePages(block, block, filterByFrom = true)
            ).blockingGet()
            val to = throttled(
                fetchTracePages(block, block, filterByFrom = false)
            ).blockingGet()
            (from + to).distinctBy { it.traceId }
        } catch (e: Throwable) {
            traceUnsupported = true
            return blockScanAt(block)
        }

        if (traces.isEmpty()) {
            return blockScanAt(block)
        }

        val timestamp = blockTimestamps(listOf(block)).blockingGet()[block] ?: 0L
        val native = traces.mapNotNull { it.toProviderTransaction(timestamp) }
        val internal = traces.mapNotNull { it.toProviderInternalTransaction(timestamp) }
        return native to internal
    }

    private fun blockScanAt(block: Long): Pair<List<ProviderTransaction>, List<ProviderInternalTransaction>> {
        val data = throttled(blockchain.rpcSingle(GetBlockWithTransactionsJsonRpc(block))).blockingGet()
        val matched = data.transactions.filter { isRelated(it) }
        if (matched.isEmpty()) return emptyList<ProviderTransaction>() to emptyList()
        val txs = matched.map { tx ->
            receiptSingle(tx, block, data.timestamp).blockingGet()
        }
        return txs to emptyList()
    }

    // endregion

    // region 代币/NFT：链顶窗口

    private fun transferWindow(startBlock: Long): Single<LogWindow> {
        val tip = blockchain.lastBlockHeight ?: return Single.just(LogWindow(emptyList(), -1L))
        if (tip <= 0) return Single.just(LogWindow(emptyList(), -1L))
        val fromBlock = maxOf(1L, tip - TRANSFER_TIP_WINDOW + 1, startBlock)
        return fetchTransferLogs(fromBlock, tip, TRANSFER_TOPIC, 1, 2)
            .map { logs ->
                ensureLogsBackfillCursor(fromBlock)
                scannedBlockHeight = maxOf(scannedBlockHeight, tip)
                LogWindow(logs, tip)
            }
    }

    private fun transfer1155Window(startBlock: Long): Single<LogWindow> {
        val tip = blockchain.lastBlockHeight ?: return Single.just(LogWindow(emptyList(), -1L))
        if (tip <= 0) return Single.just(LogWindow(emptyList(), -1L))
        val fromBlock = maxOf(1L, tip - TRANSFER_TIP_WINDOW + 1, startBlock)
        return fetchTransferLogs(fromBlock, tip, TRANSFER_SINGLE_TOPIC, 2, 3)
            .map { logs ->
                ensure1155BackfillCursor(fromBlock)
                LogWindow(logs, tip)
            }
    }

    private fun ensureLogsBackfillCursor(tipWindowStart: Long) {
        if (readProgress(LOGS_NEXT_ID) == null && tipWindowStart > 1) {
            writeProgress(LOGS_NEXT_ID, tipWindowStart - 1)
        }
    }

    private fun ensure1155BackfillCursor(tipWindowStart: Long) {
        if (readProgress(LOGS1155_NEXT_ID) == null && tipWindowStart > 1) {
            writeProgress(LOGS1155_NEXT_ID, tipWindowStart - 1)
        }
    }

    private fun fetchTransferLogs(
        fromBlock: Long,
        toBlock: Long,
        topic0: String,
        fromTopicIndex: Int,
        toTopicIndex: Int,
    ): Single<List<TransactionLog>> {
        val topic0Bytes = hexToBytes(topic0)
        val fromTopics = addressFilterTopics(topic0Bytes, fromTopicIndex)
        val toTopics = addressFilterTopics(topic0Bytes, toTopicIndex)
        val fromSingle = throttled(blockchain.getLogs(null, fromTopics, fromBlock, toBlock, false))
        val toSingle = throttled(blockchain.getLogs(null, toTopics, fromBlock, toBlock, false))
        return Single.zip(fromSingle, toSingle) { fromLogs, toLogs ->
            (fromLogs + toLogs).distinct().filter { isLogRelated(it, fromTopicIndex, toTopicIndex) }
        }.flatMap { logs -> fillTimestamps(logs) }
    }

    // endregion

    // region 历史补页（前台）

    fun startHistoryBackfill(listener: HistoryPageListener) {
        historyListener = listener
        if (!backfillRunning.compareAndSet(false, true)) return
        backfillStopped.set(false)
        backfillDisposable?.dispose()
        backfillDisposable = CompositeDisposable()

        val work = Completable.mergeArray(
            Completable.fromAction { backfillOts(listener) },
            Completable.fromAction { backfillTransferLogs(listener) },
            Completable.fromAction { backfill1155Logs(listener) },
            Completable.fromAction { backfillTrace(listener) },
        ).subscribeOn(Schedulers.io())
            .doFinally { backfillRunning.set(false) }

        work.subscribe(
            {},
            { e -> Log.w(TAG, "history backfill failed: $e") },
        ).let { backfillDisposable!!.add(it) }
    }

    fun stopHistoryBackfill() {
        backfillStopped.set(true)
        backfillRunning.set(false)
        backfillDisposable?.dispose()
        backfillDisposable = null
    }

    private fun awaitHeight(): Long? {
        repeat(30) {
            val h = blockchain.lastBlockHeight
            if (h != null && h > 0) return h
            Thread.sleep(200)
        }
        return blockchain.lastBlockHeight?.takeIf { it > 0 }
    }

    private fun backfillOts(listener: HistoryPageListener) {
        if (otsUnsupported) {
            backfillNativeDiscovery(listener)
            return
        }
        var cursor = readProgress(OTS_BEFORE_ID) ?: 0L
        if (readProgress(OTS_DONE_ID) == 1L) return
        while (!backfillStopped.get()) {
            val page = try {
                otsSearchBefore(cursor, OTS_PAGE_SIZE).blockingGet()
            } catch (e: Throwable) {
                markOtsUnsupportedIfNeeded(e)
                backfillNativeDiscovery(listener)
                return
            }
            val txs = mapOtsPage(page)
            if (txs.isNotEmpty()) {
                emitHistory { it.onTransactions(txs) }
            }
            if (page.lastPage || page.txs.isEmpty()) {
                writeProgress(OTS_DONE_ID, 1L)
                break
            }
            val minBlock = page.txs.mapNotNull { it.blockNumber }.minOrNull() ?: break
            cursor = minBlock
            writeProgress(OTS_BEFORE_ID, cursor)
        }
    }

    private fun backfillNativeDiscovery(listener: HistoryPageListener) {
        if (backfillStopped.get()) return
        val tip = awaitHeight() ?: return
        val cursor = readProgress(OTS_BEFORE_ID) ?: tip
        if (cursor <= 0) {
            writeProgress(OTS_DONE_ID, 1L)
            return
        }
        try {
            val lo = 0L
            val hi = cursor
            val native = mutableListOf<ProviderTransaction>()
            val internal = mutableListOf<ProviderInternalTransaction>()
            searchAccountChanges(
                lo, hi,
                fetchAccountState(lo), fetchAccountState(hi),
                native, internal, AtomicInteger(0), AtomicBoolean(false),
            )
            if (native.isNotEmpty()) emitHistory { it.onTransactions(native) }
            if (internal.isNotEmpty()) emitHistory { it.onInternalTransactions(internal) }
            writeProgress(OTS_BEFORE_ID, 0L)
            writeProgress(OTS_DONE_ID, 1L)
        } catch (e: HistoryStopped) {
            Log.d(TAG, "native discovery backfill stopped")
        }
    }

    private fun backfillTransferLogs(listener: HistoryPageListener) {
        if (awaitHeight() == null) return
        transferTip().blockingGet()
        val uncoveredTo = readProgress(LOGS_NEXT_ID) ?: return
        if (uncoveredTo == 0L) return
        val wide = tryWideLogs(uncoveredTo, TRANSFER_TOPIC, 1, 2)
        if (wide != null) {
            emitTransfer(listener, wide)
            writeProgress(LOGS_NEXT_ID, 0L)
            return
        }
        pageLogsBackward(LOGS_NEXT_ID, TRANSFER_TOPIC, 1, 2) { logs ->
            emitTransfer(listener, logs)
        }
    }

    private fun backfill1155Logs(listener: HistoryPageListener) {
        if (awaitHeight() == null) return
        val uncoveredTo = readProgress(LOGS1155_NEXT_ID) ?: return
        if (uncoveredTo == 0L) return
        pageLogsBackward(LOGS1155_NEXT_ID, TRANSFER_SINGLE_TOPIC, 2, 3) { logs ->
            val parsed = logs.mapNotNull { parseEip1155Transaction(it) }
            if (parsed.isNotEmpty()) {
                emitHistory { it.onEip1155Transactions(parsed) }
            }
        }
    }

    private fun backfillTrace(listener: HistoryPageListener) {
        try {
            otsFirstPage().blockingGet()
        } catch (_: Throwable) {
        }
        if (otsUnsupported) {
            Log.d(TAG, "trace backfill skipped; native discovery uses account state")
            return
        }
        val tip = awaitHeight() ?: return
        var cursor = readProgress(TRACE_NEXT_ID) ?: tip
        if (cursor <= 0L) return
        while (!backfillStopped.get() && cursor > 0) {
            val fromBlock = maxOf(1L, cursor - TRACE_CHUNK_SIZE + 1)
            val internals = scanTracesInRange(fromBlock, cursor) { trace, ts ->
                trace.toProviderInternalTransaction(ts)
            }.onErrorReturnItem(emptyList()).blockingGet()
            if (internals.isNotEmpty()) {
                emitHistory { it.onInternalTransactions(internals) }
            }
            cursor = fromBlock - 1
            writeProgress(TRACE_NEXT_ID, maxOf(cursor, 0L))
            if (cursor <= 0) break
        }
    }

    private fun transferTip(): Single<Unit> {
        return transferWindow(1).map { }
    }

    private fun tryWideLogs(
        uncoveredTo: Long,
        topic0: String,
        fromTopicIndex: Int,
        toTopicIndex: Int,
    ): List<TransactionLog>? {
        return try {
            fetchTransferLogs(1, uncoveredTo, topic0, fromTopicIndex, toTopicIndex).blockingGet()
        } catch (e: Throwable) {
            Log.d(TAG, "wide logs rejected, paging backward: $e")
            null
        }
    }

    private fun pageLogsBackward(
        progressId: String,
        topic0: String,
        fromTopicIndex: Int,
        toTopicIndex: Int,
        emit: (List<TransactionLog>) -> Unit,
    ) {
        var uncoveredTo = readProgress(progressId) ?: return
        if (uncoveredTo == 0L) return
        while (!backfillStopped.get() && uncoveredTo > 0) {
            val fromBlock = maxOf(1L, uncoveredTo - LOGS_PAGE_BLOCKS + 1)
            val logs = try {
                fetchTransferLogs(fromBlock, uncoveredTo, topic0, fromTopicIndex, toTopicIndex).blockingGet()
            } catch (e: Throwable) {
                Log.w(TAG, "pageLogsBackward failed: $e")
                break
            }
            emit(logs)
            uncoveredTo = fromBlock - 1
            writeProgress(progressId, maxOf(uncoveredTo, 0L))
            if (uncoveredTo <= 0) {
                writeProgress(progressId, 0L)
                break
            }
        }
    }

    private fun emitTransfer(listener: HistoryPageListener, logs: List<TransactionLog>) {
        val erc20 = logs.mapNotNull { parseTokenTransaction(it, null) }
        val erc721 = logs.mapNotNull { parseEip721Transaction(it, null) }
        if (erc20.isNotEmpty()) {
            emitHistory { it.onTokenTransactions(erc20) }
        }
        if (erc721.isNotEmpty()) {
            emitHistory { it.onEip721Transactions(erc721) }
        }
    }

    // endregion

    // region trace / logs 工具

    private fun <T : Any> scanTracesInRange(
        startBlock: Long,
        endBlock: Long,
        parser: (RpcTrace, Long) -> T?,
    ): Single<List<T>> {
        if (traceUnsupported) return Single.error(UnsupportedOperationException("trace unsupported"))
        val latest = minOf(endBlock, blockchain.lastBlockHeight ?: return Single.just(emptyList()))
        if (latest < startBlock) return Single.just(emptyList())

        val chunks = buildChunks(startBlock, latest, TRACE_CHUNK_SIZE)
        return Observable.fromIterable(chunks)
            .subscribeOn(Schedulers.io())
            .concatMapSingle { chunk ->
                val fromSingle = throttled(fetchTracePages(chunk.first, chunk.last, true))
                val toSingle = throttled(fetchTracePages(chunk.first, chunk.last, false))
                Single.zip(fromSingle, toSingle) { a, b -> (a + b).distinctBy { it.traceId } }
            }
            .toList()
            .map { it.flatten() }
            .flatMap { allTraces ->
                val blocks = allTraces.mapNotNull { it.blockNumberQuantity }.distinct()
                blockTimestamps(blocks).map { tsMap ->
                    allTraces.mapNotNull { parser(it, tsMap[it.blockNumberQuantity] ?: 0L) }
                }
            }
    }

    private fun fetchTracePages(fromBlock: Long, toBlock: Long, filterByFrom: Boolean): Single<List<RpcTrace>> {
        return Observable.range(0, TRACE_MAX_PAGES)
            .subscribeOn(Schedulers.io())
            .concatMapSingle { page ->
                throttled(
                    blockchain.rpcSingle(
                        TraceFilterJsonRpc(
                            fromBlock, toBlock, addressHex, filterByFrom,
                            page * TRACE_PAGE_SIZE, TRACE_PAGE_SIZE,
                        )
                    )
                )
            }
            .takeUntil { it.size < TRACE_PAGE_SIZE }
            .toList()
            .map { pages -> pages.flatten() }
    }

    private fun addressFilterTopics(topic0Bytes: ByteArray, topicIndex: Int): List<ByteArray?> {
        return List(topicIndex + 1) { i ->
            when (i) {
                0 -> topic0Bytes
                topicIndex -> paddedAddressBytes
                else -> null
            }
        }
    }

    private fun isLogRelated(log: TransactionLog, fromTopicIndex: Int, toTopicIndex: Int): Boolean {
        val from = log.topics.getOrNull(fromTopicIndex)?.lowercase()
        val to = log.topics.getOrNull(toTopicIndex)?.lowercase()
        return from == paddedAddress || to == paddedAddress
    }

    private fun blockTimestamps(blockNumbers: List<Long>): Single<Map<Long, Long>> {
        if (blockNumbers.isEmpty()) return Single.just(emptyMap())
        return Observable.fromIterable(blockNumbers)
            .subscribeOn(Schedulers.io())
            .concatMapSingle { blockNumber ->
                throttled(blockchain.getBlock(blockNumber))
                    .map { block -> blockNumber to block.timestamp }
                    .onErrorReturn { blockNumber to 0L }
            }
            .toList()
            .map { it.toMap() }
    }

    private fun fillTimestamps(logs: List<TransactionLog>): Single<List<TransactionLog>> {
        if (logs.isEmpty()) return Single.just(logs)
        if (logs.all { it.timestamp != null && it.timestamp != 0L }) return Single.just(logs)
        val blocks = logs.map { it.blockNumber }.distinct()
        return blockTimestamps(blocks).map { map ->
            logs.onEach { log -> log.timestamp = map[log.blockNumber] ?: 0L }
            logs
        }
    }

    private fun buildChunks(fromBlock: Long, toBlock: Long, chunkSize: Long): List<LongRange> {
        val chunks = mutableListOf<LongRange>()
        var start = fromBlock
        while (start <= toBlock) {
            val end = minOf(start + chunkSize - 1, toBlock)
            chunks.add(start..end)
            start = end + 1
        }
        return chunks
    }

    private fun RpcTrace.toProviderTransaction(timestamp: Long): ProviderTransaction? {
        if (!isTopLevel) return null
        if (type != "call" && type != "create") return null
        val action = action ?: return null
        val txHash = transactionHash ?: return null
        val fromAddr = action.from ?: return null
        val blockNum = blockNumberQuantity ?: return null
        return ProviderTransaction(
            blockNumber = blockNum,
            timestamp = timestamp,
            hash = txHash,
            nonce = 0,
            blockHash = blockHash,
            transactionIndex = transactionPositionQuantity ?: 0,
            from = fromAddr,
            to = action.to,
            value = action.valueQuantity ?: BigInteger.ZERO,
            gasLimit = action.gasQuantity ?: 0,
            gasPrice = 0,
            input = action.input ?: ByteArray(0),
        )
    }

    private fun RpcTrace.toProviderInternalTransaction(timestamp: Long): ProviderInternalTransaction? {
        if (isTopLevel) return null
        val action = action ?: return null
        val txHash = transactionHash ?: return null
        val fromAddr = action.from ?: return null
        val toAddr = action.to ?: return null
        val value = action.valueQuantity ?: return null
        val blockNum = blockNumberQuantity ?: return null
        if (value.signum() <= 0) return null
        return ProviderInternalTransaction(
            hash = txHash,
            blockNumber = blockNum,
            timestamp = timestamp,
            from = fromAddr,
            to = toAddr,
            value = value,
            traceId = traceId,
        )
    }

    private fun receiptSingle(
        tx: RpcTransaction,
        blockNumber: Long,
        blockTimestamp: Long,
    ): Single<ProviderTransaction> {
        return throttled(blockchain.getTransactionReceipt(tx.hash))
            .map { receipt ->
                ProviderTransaction(
                    blockNumber = blockNumber,
                    timestamp = blockTimestamp,
                    hash = tx.hash,
                    nonce = tx.nonce,
                    blockHash = tx.blockHash,
                    transactionIndex = (tx.transactionIndex ?: 0L).toInt(),
                    from = tx.from,
                    to = tx.to,
                    value = tx.value,
                    gasLimit = tx.gasLimit,
                    gasPrice = tx.gasPrice,
                    isError = if (receipt.status == 0L) 1 else 0,
                    txReceiptStatus = receipt.status?.toInt(),
                    input = tx.input,
                    cumulativeGasUsed = receipt.cumulativeGasUsed,
                    gasUsed = receipt.gasUsed,
                )
            }
            .onErrorReturn {
                ProviderTransaction(
                    blockNumber = blockNumber,
                    timestamp = blockTimestamp,
                    hash = tx.hash,
                    nonce = tx.nonce,
                    blockHash = tx.blockHash,
                    transactionIndex = (tx.transactionIndex ?: 0L).toInt(),
                    from = tx.from,
                    to = tx.to,
                    value = tx.value,
                    gasLimit = tx.gasLimit,
                    gasPrice = tx.gasPrice,
                    input = tx.input,
                )
            }
    }

    private fun isRelated(tx: RpcTransaction): Boolean {
        return tx.from.hex.equals(addressHex, true) ||
            tx.to?.hex?.equals(addressHex, true) == true
    }

    private fun parseTokenTransaction(log: TransactionLog, gasInfo: GasInfo?): ProviderTokenTransaction? {
        if (log.topics.size != 3) return null
        val from = topicToAddress(log.topics[1]) ?: return null
        val to = topicToAddress(log.topics[2]) ?: return null
        if (log.data.size < 32) return null
        val value = BigInteger(1, log.data.copyOfRange(0, 32))
        return ProviderTokenTransaction(
            blockNumber = log.blockNumber,
            timestamp = log.timestamp ?: 0L,
            hash = log.transactionHash,
            nonce = 0,
            blockHash = ByteArray(0),
            from = from,
            contractAddress = log.address,
            to = to,
            value = value,
            tokenName = "",
            tokenSymbol = "",
            tokenDecimal = 0,
            transactionIndex = gasInfo?.transactionIndex ?: log.transactionIndex,
            gasLimit = 0,
            gasPrice = gasInfo?.gasPrice ?: 0,
            gasUsed = gasInfo?.gasUsed ?: 0,
            cumulativeGasUsed = gasInfo?.cumulativeGasUsed ?: 0,
        )
    }

    private fun parseEip721Transaction(log: TransactionLog, gasInfo: GasInfo?): ProviderEip721Transaction? {
        if (log.topics.size != 4) return null
        val from = topicToAddress(log.topics[1]) ?: return null
        val to = topicToAddress(log.topics[2]) ?: return null
        val tokenId = BigInteger(1, hexToBytes(log.topics[3].removePrefix("0x")))
        return ProviderEip721Transaction(
            blockNumber = log.blockNumber,
            timestamp = log.timestamp ?: 0L,
            hash = log.transactionHash,
            nonce = 0,
            blockHash = ByteArray(0),
            transactionIndex = gasInfo?.transactionIndex ?: log.transactionIndex,
            gasLimit = 0,
            gasPrice = gasInfo?.gasPrice ?: 0,
            gasUsed = gasInfo?.gasUsed ?: 0,
            cumulativeGasUsed = gasInfo?.cumulativeGasUsed ?: 0,
            contractAddress = log.address,
            from = from,
            to = to,
            tokenId = tokenId,
            tokenName = "",
            tokenSymbol = "",
            tokenDecimal = 0,
        )
    }

    private fun parseEip1155Transaction(log: TransactionLog): ProviderEip1155Transaction? {
        if (log.topics.size != 4) return null
        val from = topicToAddress(log.topics[2]) ?: return null
        val to = topicToAddress(log.topics[3]) ?: return null
        val data = log.data
        if (data.size < 64) return null
        val tokenId = BigInteger(1, data.copyOfRange(0, 32))
        val tokenValue = BigInteger(1, data.copyOfRange(32, 64)).toInt()
        return ProviderEip1155Transaction(
            blockNumber = log.blockNumber,
            timestamp = log.timestamp ?: 0L,
            hash = log.transactionHash,
            nonce = 0,
            blockHash = log.blockHash,
            transactionIndex = log.transactionIndex,
            gasLimit = 0,
            gasPrice = 0,
            gasUsed = 0,
            cumulativeGasUsed = 0,
            contractAddress = log.address,
            from = from,
            to = to,
            tokenId = tokenId,
            tokenValue = tokenValue,
            tokenName = "",
            tokenSymbol = "",
        )
    }

    private fun topicToAddress(topic: String): Address? {
        val clean = topic.removePrefix("0x")
        if (clean.length < 64) return null
        return try {
            Address(hexToBytes(clean.substring(24, 64)))
        } catch (_: Throwable) {
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x")
        val len = clean.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = (
                (Character.digit(clean[i * 2], 16) shl 4) +
                    Character.digit(clean[i * 2 + 1], 16)
                ).toByte()
        }
        return result
    }

    private class GasInfo(
        val transactionIndex: Int,
        val gasPrice: Long,
        val gasUsed: Long,
        val cumulativeGasUsed: Long,
    )

    // endregion
}
