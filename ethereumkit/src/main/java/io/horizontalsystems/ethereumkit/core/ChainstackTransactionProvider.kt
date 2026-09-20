package io.horizontalsystems.ethereumkit.core

import android.util.Log
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetBlockWithTransactionsJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.TraceFilterJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcBlockWithTransactions
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTrace
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransaction
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.ProviderEip1155Transaction
import io.horizontalsystems.ethereumkit.models.ProviderEip721Transaction
import io.horizontalsystems.ethereumkit.models.ProviderInternalTransaction
import io.horizontalsystems.ethereumkit.models.ProviderTokenTransaction
import io.horizontalsystems.ethereumkit.models.ProviderTransaction
import io.horizontalsystems.ethereumkit.models.Safe4AccountManagerTransaction
import io.horizontalsystems.ethereumkit.models.TransactionLog
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通过 Chainstack RPC 节点同步交易数据，不依赖 Etherscan 等浏览器 API。
 *
 * 同步策略（优先级从高到低）：
 * - 普通交易 + 内部交易：优先使用 trace_filter（OpenEthereum/Erigon trace 命名空间，
 *   Chainstack 以太坊节点底层为 Erigon，完整支持）。地址过滤在节点端完成，
 *   一次请求即可覆盖整个区块区间（含合约内部转账，这是区块扫描做不到的），
 *   相比逐块 eth_getBlockByNumber(fullTx=true) 扫描（N 次请求、每次数 MB 响应）
 *   是数量级的提升。trace 不可用时自动降级为区块扫描；
 * - 代币交易(ERC20/721/1155)：eth_getLogs 的 topics 占位匹配把地址过滤下推到节点端，
 *   from/to 两个方向各查一次，节点只返回本地址相关日志（几十条）。绝不能只按 topic0
 *   全量拉取——ETH 主网 500 块的 Transfer 事件可达 5~15 万条（响应体 30~100MB），
 *   会直接 OOM；
 * - 首次同步只扫描最近 [FIRST_SYNC_BLOCK_RANGE] 个区块，之后由上层 syncer 传入的
 *   startBlock 驱动增量同步；区间无相关交易时也通过 [lastScannedBlockHeight] 推进进度。
 *
 * 说明：trace/日志扫描无法拿到 tokenName/tokenSymbol/nonce/gasPrice 等信息，
 * 这些字段由 DecorationManager 在本地解析补全。
 */
class ChainstackTransactionProvider(
    private val blockchain: IBlockchain,
    private val address: Address,
) : ITransactionProvider {

    companion object {
        const val TAG = "ChainstackTxProvider"

        /**
         * 首次同步扫描的区块范围（最近多少个区块）。
         *
         * 每次 eth_getBlockByNumber(fullTx=true) 的响应体可达数 MB（区块内全部交易的 input），
         * 范围过大既会拖慢同步也会显著抬高堆内存峰值，因此这里保守取 500 块，
         * 后续靠增量同步持续推进。
         */
        private const val FIRST_SYNC_BLOCK_RANGE = 500L

        /**
         * 单次增量同步最多扫描的区块数。
         *
         * 同样受单区块响应体大小限制，避免长时间未同步后一次性拉取过多区块。
         */
        private const val MAX_SYNC_BLOCK_RANGE = 2_000L

        /**
         * eth_getLogs 单个请求覆盖的最大区块跨度。
         *
         * 地址过滤已下推到节点端（topics 占位匹配），正常情况下返回量极小，
         * 这里分块只是为了防止节点对单次 getLogs 的区间/数量限制。
         */
        private const val LOGS_CHUNK_SIZE = 1_000L

        /**
         * trace_filter 单个请求覆盖的最大区块跨度。
         *
         * Erigon 的 trace_filter 按需扫描区块内全部 trace 再过滤，区间过大时
         * 服务端耗时会显著上升（容易撞上 [RPC_TIMEOUT_SECONDS]），因此取较小值。
         */
        private const val TRACE_CHUNK_SIZE = 500L

        /** trace_filter 单页数量（after/count 分页） */
        private const val TRACE_PAGE_SIZE = 500

        /** trace_filter 单 chunk 的最大分页数，防止异常场景死循环 */
        private const val TRACE_MAX_PAGES = 20

        /**
         * 区块扫描（trace 降级路径）并行拉取的最大并发数。
         *
         * 区块对象在 map 中立即过滤为 [BlockTxs] 后即可被 GC 回收，
         * 同时全局 [rpcSemaphore] 兜底限制在途请求数，小并发是安全的。
         * BSC 等无 trace 命名空间的链依赖此路径追赶进度，串行（并发 1）过慢。
         */
        private const val BLOCK_FETCH_CONCURRENCY = 4

        /**
         * 收据并行拉取的最大并发数。
         *
         * 注意：[io.horizontalsystems.ethereumkit.api.core.NodeApiProvider] 内部是
         * `blockingGet()` 的同步请求 + OkHttp 默认每主机 maxRequestsPerHost=5，
         * 并发过高会导致请求全部排队阻塞（表现为日志停在 start 后无任何返回）。
         * 另外收据请求本身很轻量，但仍需避免瞬时打爆节点。
         */
        private const val RECEIPT_FETCH_CONCURRENCY = 2

        /** eth_getLogs 并行请求的最大并发数（同一节点，同样受 OkHttp 队列限制） */
        private const val LOGS_FETCH_CONCURRENCY = 2

        /**
         * 全局 RPC 并发闸门。
         *
         * TransactionSyncManager 会用 Single.zip 同时订阅 4 类查询（普通/ERC20/721/1155），
         * 若各自再并发拉取，会瞬间把单节点的请求队列塞满，
         * 而 NodeApiProvider 内部是 blockingGet 同步等待，最终表现为所有请求都卡住无返回。
         *
         * 同时 fullTx=true 的区块响应体可达数 MB，在途请求越多堆内存峰值越高
         * （实测 511MB/512MB 打满后触发 8.5s 阻塞 GC 导致 UI 卡顿），
         * 因此这里收紧为 2，优先保证内存平稳。
         */
        private val rpcSemaphore = java.util.concurrent.Semaphore(2)

        /** 单个 RPC 请求的超时时间（秒），防止慢请求长期占用并发槽位 */
        private const val RPC_TIMEOUT_SECONDS = 30L

        // keccak256("Transfer(address,address,uint256)")
        private const val TRANSFER_TOPIC =
            "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

        // keccak256("TransferSingle(address,address,address,uint256,uint256)")
        private const val TRANSFER_SINGLE_TOPIC =
            "c3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62"
    }

    private val addressHex = address.hex.lowercase()

    /** 事件日志中地址 topic 的左填充形式 */
    private val paddedAddress = "0x000000000000000000000000" + addressHex.removePrefix("0x")

    /** [paddedAddress] 的字节形式，用于构造 eth_getLogs 的 topics 过滤参数 */
    private val paddedAddressBytes = hexToBytes(paddedAddress)

    /**
     * 在全局信号量保护下执行 RPC 请求，避免 4 类查询并发时把单节点请求队列打满。
     *
     * 注意 [io.horizontalsystems.ethereumkit.api.core.NodeApiProvider] 内部是
     * `blockingGet()` 同步等待，必须在 IO 线程订阅（subscribeOn），
     * 否则会阻塞下游调度线程。
     */
    private fun <T> throttled(source: Single<T>): Single<T> {
        // acquire 必须与请求执行在同一 IO 线程池，否则信号量占满后
        // 后续任务会在下游调度线程阻塞等待，而持有槽位的任务又需要该线程调度，形成死锁。
        return Single.fromCallable {
            rpcSemaphore.acquire()
            try {
                // 超时保护：NodeApiProvider 的 OkHttp 超时为 60s，这里更早放弃，
                // 避免单个慢请求长时间占用并发槽位导致整体同步停滞。
                source.timeout(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet()
            } finally {
                rpcSemaphore.release()
            }
        }.subscribeOn(Schedulers.io())
    }

    /** 最近一次扫描实际到达的最高区块，用于驱动增量同步进度 */
    @Volatile
    private var scannedBlockHeight: Long = -1L

    /**
     * 同一轮同步内的 eth_getLogs 请求去重缓存（按 chunk + 方向粒度）。
     *
     * getTokenTransactions(ERC20) 与 getEip721Transactions(721) 使用完全相同的
     * topic0=Transfer、地址占位与扫描区间，若不去重会对同一区间发两遍完全相同的请求。
     *
     * 缓存的是节点端按地址过滤后的结果（通常几十条，几十 KB），内存占用可忽略。
     */
    private val logsChunkSingleCache = java.util.concurrent.ConcurrentHashMap<String, Single<List<TransactionLog>>>()

    /**
     * 同一轮同步内的 trace_filter 请求去重缓存（按 chunk + 方向粒度）。
     *
     * getTransactions（顶层调用）与 getInternalTransactions（内部调用）使用完全相同的
     * 查询参数与区间，去重后同一轮同步只发一次请求，两处订阅共享结果。
     */
    private val traceChunkSingleCache = java.util.concurrent.ConcurrentHashMap<String, Single<List<RpcTrace>>>()

    /** 当前同步轮的起始区块，用于判断是否进入新一轮同步（进入则清空去重缓存） */
    @Volatile
    private var currentSyncStartBlock: Long = -1L

    /**
     * 节点不支持 trace 命名空间的记忆标记（如 BSC Geth）。
     *
     * 一旦确认不可用，后续同步轮直接走降级路径，
     * 避免每轮重复对全部 chunk 发注定失败的 trace_filter 请求（实测每轮浪费 ~8s）。
     */
    @Volatile
    private var traceUnsupported = false

    override val lastScannedBlockHeight: Long
        get() = scannedBlockHeight

    override fun getTransactions(startBlock: Long): Single<List<ProviderTransaction>> {
        Log.d(TAG, "getTransactions: start, startBlock=$startBlock")
        // 优先 trace_filter（一次请求覆盖全区间，节点端地址过滤，含普通转账），
        // 不可用（如 BSC Geth 节点无 trace 命名空间）时降级为逐块扫描
        return scanTraces<ProviderTransaction>(startBlock) { trace, timestamp ->
            trace.toProviderTransaction(timestamp)
        }.onErrorResumeNext { e ->
            Log.w(TAG, "getTransactions: trace_filter unavailable, fallback to block scan: $e")
            scanBlocksTransactions(startBlock)
        }
    }

    override fun getInternalTransactions(startBlock: Long): Single<List<ProviderInternalTransaction>> {
        Log.d(TAG, "getInternalTransactions: start, startBlock=$startBlock")
        // trace_filter 的非顶层调用即合约内部转账（区块扫描拿不到），不可用时保持空列表
        return scanTraces<ProviderInternalTransaction>(startBlock) { trace, timestamp ->
            trace.toProviderInternalTransaction(timestamp)
        }.onErrorResumeNext { e ->
            Log.w(TAG, "getInternalTransactions: trace_filter unavailable: $e")
            Single.just(emptyList())
        }
    }

    override fun getInternalTransactionsAsync(hash: ByteArray): Single<List<ProviderInternalTransaction>> {
        return Single.just(emptyList())
    }

    override fun getTokenTransactions(startBlock: Long): Single<List<ProviderTokenTransaction>> {
        Log.d(TAG, "getTokenTransactions: start, startBlock=$startBlock")
        // ERC20/721 的 Transfer：from 在 topic1、to 在 topic2
        return scanAddressLogs(startBlock, TRANSFER_TOPIC, fromTopicIndex = 1, toTopicIndex = 2) { log, gasInfo ->
            parseTokenTransaction(log, gasInfo)
        }
    }

    override fun getEip721Transactions(startBlock: Long): Single<List<ProviderEip721Transaction>> {
        Log.d(TAG, "getEip721Transactions: start, startBlock=$startBlock")
        return scanAddressLogs(startBlock, TRANSFER_TOPIC, fromTopicIndex = 1, toTopicIndex = 2) { log, gasInfo ->
            parseEip721Transaction(log, gasInfo)
        }
    }

    override fun getEip1155Transactions(startBlock: Long): Single<List<ProviderEip1155Transaction>> {
        Log.d(TAG, "getEip1155Transactions: start, startBlock=$startBlock")
        // ERC1155 的 TransferSingle：from 在 topic2、to 在 topic3（topic1 是 operator）
        return scanAddressLogs(startBlock, TRANSFER_SINGLE_TOPIC, fromTopicIndex = 2, toTopicIndex = 3) { log, _ ->
            parseEip1155Transaction(log)
        }
    }

    override fun getSafeAccountManagerTransactions(startBlock: Long): Single<List<Safe4AccountManagerTransaction>> {
        return Single.just(emptyList())
    }

    // region trace 扫描（优先路径）

    /**
     * 通过 trace_filter 按地址扫描交易（普通+内部调用）。
     *
     * Chainstack 以太坊节点（Erigon）支持 trace 命名空间，地址过滤在节点端完成，
     * 一次请求即可覆盖整个 chunk 区间，是区块扫描的数量级优化。
     *
     * 流程：
     * 1. 区间按 [TRACE_CHUNK_SIZE] 分块，from/to 两个方向各查一次（AND 语义所致），
     *    结果按 (transactionHash + traceAddress) 去重；
     * 2. 仅为相关 trace 涉及的区块补时间戳（轻量 getBlock，区块头几 KB）；
     * 3. 交给 parser 解析为具体交易类型；
     * 4. 全部 chunk 失败时抛异常（由调用方降级），部分失败则不推进进度待下轮重试。
     */
    private fun <T : Any> scanTraces(
        startBlock: Long,
        parser: (RpcTrace, Long) -> T?
    ): Single<List<T>> {
        // 已确认节点不支持 trace 命名空间，直接走降级路径
        if (traceUnsupported) {
            return Single.error(UnsupportedOperationException("trace namespace unsupported by node"))
        }

        val latestBlock = blockchain.lastBlockHeight ?: run {
            Log.w(TAG, "scanTraces: lastBlockHeight is null, skip (address=$addressHex)")
            return Single.just(emptyList())
        }
        if (latestBlock <= 0) {
            Log.w(TAG, "scanTraces: lastBlockHeight=$latestBlock invalid, skip")
            return Single.just(emptyList())
        }

        val range = scanRange(startBlock, latestBlock) ?: run {
            Log.w(TAG, "scanTraces: empty range (startBlock=$startBlock), skip")
            return Single.just(emptyList())
        }

        val chunks = buildChunks(range.first, range.last, TRACE_CHUNK_SIZE)

        Log.d(
            TAG,
            "scanTraces: start, address=$addressHex fromBlock=${range.first} " +
                    "toBlock=${range.last} chunks=${chunks.size}"
        )

        val startedAt = System.currentTimeMillis()
        val failedChunks = AtomicInteger(0)

        // 进入新一轮同步（startBlock 变化）时清空去重缓存
        if (currentSyncStartBlock != startBlock) {
            currentSyncStartBlock = startBlock
            traceChunkSingleCache.clear()
            logsChunkSingleCache.clear()
        }

        return Observable.fromIterable(chunks)
            .subscribeOn(Schedulers.io())
            .concatMapSingle { chunk ->
                val fromSingle = cachedTraceChunkSingle("$startBlock|from|${chunk.first}|${chunk.last}", chunk, filterByFrom = true, failedChunks)
                val toSingle = cachedTraceChunkSingle("$startBlock|to|${chunk.first}|${chunk.last}", chunk, filterByFrom = false, failedChunks)

                Single.zip(fromSingle, toSingle) { fromTraces, toTraces ->
                    (fromTraces + toTraces).distinctBy { it.traceId }
                }
            }
            .toList()
            .map { tracesPerChunk -> tracesPerChunk.flatten() }
            .flatMap { allTraces ->
                if (failedChunks.get() > 0 && allTraces.isEmpty()) {
                    // 全部 chunk 失败：节点不支持 trace 命名空间（如 BSC Geth）。
                    // 记住该状态，后续同步轮直接走降级路径，不再重复试探。
                    traceUnsupported = true
                    throw UnsupportedOperationException(
                        "trace_filter failed for all ${failedChunks.get()} chunks, " +
                                "mark node as trace-unsupported"
                    )
                }

                Log.d(
                    TAG,
                    "scanTraces: fetched ${allTraces.size} traces " +
                            "(failedChunks=${failedChunks.get()}) elapsed=${System.currentTimeMillis() - startedAt}ms"
                )

                // 按区块去重后批量补时间戳
                val blockNumbers = allTraces.mapNotNull { it.blockNumberQuantity }.distinct()
                blockTimestamps(blockNumbers).map { timestampByBlock ->
                    allTraces.mapNotNull { trace ->
                        val timestamp = timestampByBlock[trace.blockNumberQuantity] ?: 0L
                        parser(trace, timestamp)
                    }
                }
            }
            .doOnSuccess { result ->
                if (failedChunks.get() == 0) {
                    scannedBlockHeight = maxOf(scannedBlockHeight, range.last)
                } else {
                    Log.w(TAG, "scanTraces: ${failedChunks.get()} chunks failed, skip progress update")
                }
                Log.d(
                    TAG,
                    "scanTraces: done, parsed=${result.size} scannedBlockHeight=$scannedBlockHeight " +
                            "elapsed=${System.currentTimeMillis() - startedAt}ms"
                )
            }
    }

    /**
     * 获取（或复用）某个 chunk、某个方向的 trace_filter 全量结果（含分页）。
     *
     * getTransactions 与 getInternalTransactions 的查询参数完全相同，
     * 通过 [traceChunkSingleCache] 去重后同一轮同步只发一次请求。
     */
    private fun cachedTraceChunkSingle(
        cacheKey: String,
        chunk: LongRange,
        filterByFrom: Boolean,
        failedChunks: AtomicInteger
    ): Single<List<RpcTrace>> {
        return traceChunkSingleCache.computeIfAbsent(cacheKey) {
            fetchTracePages(chunk.first, chunk.last, filterByFrom)
                .onErrorReturn { e ->
                    failedChunks.incrementAndGet()
                    // RpcError 的 message 为空，需展开内部 error（code/message）才能定位原因
                    val detail = (e as? io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc.ResponseError.RpcError)
                        ?.error?.let { "code=${it.code} msg=${it.message}" } ?: e.message
                    Log.w(TAG, "scanTraces: chunk ${chunk.first}..${chunk.last} " +
                            "(${if (filterByFrom) "from" else "to"}) failed: $detail")
                    emptyList()
                }
                .cache()
        }
    }

    /** 拉取一个 chunk 的全部分页 trace（after/count 分页，直至不满页或达到页数上限） */
    private fun fetchTracePages(fromBlock: Long, toBlock: Long, filterByFrom: Boolean): Single<List<RpcTrace>> {
        return Observable.range(0, TRACE_MAX_PAGES)
            .subscribeOn(Schedulers.io())
            .concatMapSingle { page ->
                throttled(blockchain.rpcSingle(
                    TraceFilterJsonRpc(
                        fromBlock = fromBlock,
                        toBlock = toBlock,
                        addressHex = addressHex,
                        filterByFrom = filterByFrom,
                        after = page * TRACE_PAGE_SIZE,
                        count = TRACE_PAGE_SIZE
                    )
                ))
            }
            // 不满页说明已是最后一页，停止拉取
            .takeUntil { pageTraces -> pageTraces.size < TRACE_PAGE_SIZE }
            .toList()
            .map { pages -> pages.flatten() }
    }

    /** 顶层调用/合约创建 → 普通交易 */
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

    /** 非顶层调用（合约内部转账）→ 内部交易 */
    private fun RpcTrace.toProviderInternalTransaction(timestamp: Long): ProviderInternalTransaction? {
        if (isTopLevel) return null
        val action = action ?: return null
        val txHash = transactionHash ?: return null
        val fromAddr = action.from ?: return null
        val toAddr = action.to ?: return null
        val value = action.valueQuantity ?: return null
        val blockNum = blockNumberQuantity ?: return null
        // 零值内部转账无展示意义
        if (value.signum() <= 0) return null

        return ProviderInternalTransaction(
            hash = txHash,
            blockNumber = blockNum,
            timestamp = timestamp,
            from = fromAddr,
            to = toAddr,
            value = value,
            traceId = traceId
        )
    }

    /**
     * 批量获取区块时间戳（只拉区块头，eth_getBlockByNumber(fullTx=false)，几 KB/块）。
     */
    private fun blockTimestamps(blockNumbers: List<Long>): Single<Map<Long, Long>> {
        if (blockNumbers.isEmpty()) return Single.just(emptyMap())

        return Observable.fromIterable(blockNumbers)
            .subscribeOn(Schedulers.io())
            .concatMapSingle { blockNumber ->
                throttled(blockchain.getBlock(blockNumber))
                    .map { block -> blockNumber to block.timestamp }
                    .onErrorReturn { e ->
                        Log.w(TAG, "blockTimestamps: getBlock($blockNumber) failed: $e")
                        blockNumber to 0L
                    }
            }
            .toList()
            .map { pairs -> pairs.toMap() }
    }

    // endregion

    // region 区块扫描

    /** 计算本次扫描的区块区间：首次同步限制最近范围，增量同步限制单次上限 */
    private fun scanRange(startBlock: Long, latestBlock: Long): LongRange? {
        // startBlock 由上层 syncer 传入（lastBlockNumber + 1），首次同步时为 1
        val isFirstSync = startBlock <= 1L
        val fromBlock = if (isFirstSync) {
            latestBlock - FIRST_SYNC_BLOCK_RANGE + 1
        } else {
            startBlock
        }.coerceAtLeast(1L)

        // 增量同步时限制单次最大跨度，避免落后时一次拉取过多区块
        val rangeEnd = minOf(latestBlock, fromBlock + MAX_SYNC_BLOCK_RANGE - 1)

        if (fromBlock > rangeEnd) return null
        return fromBlock..rangeEnd
    }

    /**
     * 区块扫描中间结果：区块高度/时间戳 + 该区块中与本地址相关的交易。
     *
     * 只保留匹配到的交易，避免持有整个 [RpcBlockWithTransactions]（含区块内全部交易的 input 字节），
     * 否则首次同步扫描上千个区块时会因堆内驻留过多数据而 OOM。
     */
    private class BlockTxs(
        val number: Long,
        val timestamp: Long,
        val transactions: List<RpcTransaction>
    )

    private fun <T> scanBlocks(
        startBlock: Long,
        mapper: (Long, Long, List<RpcTransaction>) -> Single<List<T>>
    ): Single<List<T>> {
        val latestBlock = blockchain.lastBlockHeight ?: run {
            Log.w(TAG, "scanBlocks: lastBlockHeight is null, skip (address=$addressHex, startBlock=$startBlock)")
            return Single.just(emptyList())
        }
        if (latestBlock <= 0) {
            Log.w(TAG, "scanBlocks: lastBlockHeight=$latestBlock invalid, skip")
            return Single.just(emptyList())
        }

        val range = scanRange(startBlock, latestBlock) ?: run {
            Log.w(TAG, "scanBlocks: empty range (startBlock=$startBlock latestBlock=$latestBlock), skip")
            return Single.just(emptyList())
        }

        Log.d(
            TAG,
            "scanBlocks: start, address=$addressHex startBlock=$startBlock " +
                    "latestBlock=$latestBlock fromBlock=${range.first} toBlock=${range.last} " +
                    "range=${range.last - range.first + 1}"
        )

        val failedBlocks = AtomicInteger(0)
        val txCount = AtomicInteger(0)
        val fetchedBlocks = AtomicInteger(0)
        val totalBlocks = (range.last - range.first + 1).toInt()
        val startedAt = System.currentTimeMillis()

        return Observable.fromIterable(range.asIterable())
            // 整个扫描链在 IO 线程调度，避免因下游线程（Single.zip 订阅线程）承载慢操作而卡住
            .subscribeOn(Schedulers.io())
            // 有界并发拉取区块：fullTx=true 的单区块响应体可达数 MB，禁止高并发。
            // 安全前提（均已在位）：区块对象在 map 中立即过滤为 BlockTxs（仅相关交易）
            // 后即可被 GC 回收，同时在途请求数由全局 [rpcSemaphore] 兜底限制。
            // BSC 等无 trace 命名空间的链依赖此路径追赶进度，串行过慢。
            .flatMap({ blockNumber ->
                throttled(blockchain.rpcSingle(GetBlockWithTransactionsJsonRpc(blockNumber)))
                    // 立即提取区块内的交易，随后区块对象即可被 GC 回收。
                    .map<BlockTxs> { block ->
                        val done = fetchedBlocks.incrementAndGet()
                        if (done % 100 == 0 || done == totalBlocks) {
                            Log.d(
                                TAG,
                                "scanBlocks: progress $done/$totalBlocks " +
                                        "elapsed=${System.currentTimeMillis() - startedAt}ms"
                            )
                        }
                        txCount.addAndGet(block.transactions.size)
                        // 只保留本地址相关的交易，其余在方法返回后即可回收
                        BlockTxs(
                            number = blockNumber,
                            timestamp = block.timestamp,
                            transactions = block.transactions.filter { tx -> isRelated(tx) }
                        )
                    }
                    .onErrorReturn { e ->
                        failedBlocks.incrementAndGet()
                        Log.w(TAG, "scanBlocks: getBlockByNumber($blockNumber) failed: $e")
                        BlockTxs(blockNumber, 0L, emptyList())
                    }
                    .toObservable()
            }, BLOCK_FETCH_CONCURRENCY)
            // 顺序处理匹配交易与收据
            .concatMapSingle { data ->
                mapper(data.number, data.timestamp, data.transactions)
            }
            // 只累积最终结果（数量很小），不缓存中间区块
            .toList()
            .map { list -> list.flatten() }
            .doOnSuccess { result ->
                // 记录扫描进度：即使区间内没有相关交易，进度也要推进，避免每次重复扫描同一区间
                if (failedBlocks.get() == 0) {
                    scannedBlockHeight = range.last
                } else {
                    // 存在失败区块时只推进到成功扫描的最高区块，下次可重试失败部分
                    Log.w(TAG, "scanBlocks: ${failedBlocks.get()} blocks failed, skip progress update")
                }
                Log.d(
                    TAG,
                    "scanBlocks: done, blocks=${range.last - range.first + 1} " +
                            "failedBlocks=${failedBlocks.get()} scannedTxs=${txCount.get()} " +
                            "matched=${result.size} scannedBlockHeight=$scannedBlockHeight " +
                            "elapsed=${System.currentTimeMillis() - startedAt}ms"
                )
            }
    }

    /**
     * 区块扫描方式同步普通交易（trace_filter 不可用时的降级路径）。
     *
     * scanBlocks 已按地址过滤，这里只对命中的交易补拉收据。
     */
    private fun scanBlocksTransactions(startBlock: Long): Single<List<ProviderTransaction>> {
        return scanBlocks(startBlock) { blockNumber, blockTimestamp, matchedTransactions ->
            if (matchedTransactions.isEmpty()) {
                Single.just(emptyList())
            } else {
                Log.d(TAG, "scanBlocksTransactions: block $blockNumber matched ${matchedTransactions.size} txs")
                // 并发拉取收据以补齐 gasUsed / status（并发度由 throttled 全局闸门控制）
                Observable.fromIterable(matchedTransactions)
                    .subscribeOn(Schedulers.io())
                    .flatMapSingle { tx -> receiptSingle(tx, blockNumber, blockTimestamp) }
                    .toList()
            }
        }
    }

    private fun receiptSingle(
        tx: RpcTransaction,
        blockNumber: Long,
        blockTimestamp: Long
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
                    gasUsed = receipt.gasUsed
                )
            }
            .onErrorReturn { e ->
                // 收据获取失败时仍返回交易本体
                Log.w(TAG, "receiptSingle: getTransactionReceipt failed for ${tx.hash.toHexString()}: $e")
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
                    input = tx.input
                )
            }
    }

    private fun isRelated(tx: RpcTransaction): Boolean {
        return tx.from.hex.equals(addressHex, true) ||
                tx.to?.hex?.equals(addressHex, true) == true
    }

    // endregion

    // region 事件日志扫描

    /** 交易收据中与展示相关的字段（按交易哈希获取） */
    private class GasInfo(
        val transactionIndex: Int,
        val gasPrice: Long,
        val gasUsed: Long,
        val cumulativeGasUsed: Long,
    )

    /**
     * 构造 eth_getLogs 的 topics 过滤参数：
     * `[topic0, null..., paddedAddress(位于 topicIndex)]`
     *
     * topics 数组中 null 为通配符，非 null 位置必须精确匹配。
     * 例如 ERC20/721 的 from 过滤为 `[Transfer, paddedAddress]`，
     * to 过滤为 `[Transfer, null, paddedAddress]`。
     */
    private fun addressFilterTopics(topic0Bytes: ByteArray, topicIndex: Int): List<ByteArray?> {
        return List(topicIndex + 1) { i ->
            when (i) {
                0 -> topic0Bytes
                topicIndex -> paddedAddressBytes
                else -> null
            }
        }
    }

    /**
     * 按地址扫描事件日志（ERC20/721/1155 统一入口）。
     *
     * 内存安全的关键：把 from/to 地址过滤下推到节点端（eth_getLogs 的 topics 占位匹配），
     * 节点只返回与本地址相关的日志（通常几十条、几十 KB）。
     * 绝不能只按 topic0 拉全量日志——ETH 主网 1000 块的 Transfer 事件可达 20 万+条
     * （响应体几十 MB），经 Gson 双阶段解析（JsonElement 对象树 + 目标类型）瞬时峰值
     * 可达数百 MB，直接 OOM（此前区块同步 OOM 的根本原因）。
     *
     * 流程：
     * 1. 区间按 [LOGS_CHUNK_SIZE] 分块，from/to 两个方向各查一次（带占位 topics）；
     * 2. 两个方向的结果合并去重（[TransactionLog] 的 equals 按 hash+logIndex）；
     * 3. 本地再按 topic 地址过滤一次（对节点端过滤的双保险，幂等）；
     * 4. 仅为相关日志涉及的区块补时间戳（个位数请求）；
     * 5. 按交易哈希取收据补齐 gas 信息，交给 parser 解析。
     */
    private fun <T> scanAddressLogs(
        startBlock: Long,
        topic0: String,
        fromTopicIndex: Int,
        toTopicIndex: Int,
        parser: (TransactionLog, GasInfo?) -> T?
    ): Single<List<T>> {
        val latestBlock = blockchain.lastBlockHeight ?: run {
            Log.w(TAG, "scanAddressLogs: lastBlockHeight is null, skip (address=$addressHex)")
            return Single.just(emptyList())
        }
        if (latestBlock <= 0) {
            Log.w(TAG, "scanAddressLogs: lastBlockHeight=$latestBlock invalid, skip")
            return Single.just(emptyList())
        }

        val range = scanRange(startBlock, latestBlock) ?: run {
            Log.w(TAG, "scanAddressLogs: empty range (startBlock=$startBlock), skip")
            return Single.just(emptyList())
        }

        val topic0Bytes = hexToBytes(topic0)
        val fromTopics = addressFilterTopics(topic0Bytes, fromTopicIndex)
        val toTopics = addressFilterTopics(topic0Bytes, toTopicIndex)
        val chunks = buildChunks(range.first, range.last, LOGS_CHUNK_SIZE)

        Log.d(
            TAG,
            "scanAddressLogs: start, address=$addressHex fromBlock=${range.first} " +
                    "toBlock=${range.last} chunks=${chunks.size} topic0=$topic0"
        )

        val startedAt = System.currentTimeMillis()
        val failedChunks = AtomicInteger(0)

        // 进入新一轮同步（startBlock 变化）时清空去重缓存
        if (currentSyncStartBlock != startBlock) {
            currentSyncStartBlock = startBlock
            logsChunkSingleCache.clear()
        }

        return Observable.fromIterable(chunks)
            .subscribeOn(Schedulers.io())
            // 串行处理分块：每个分块拿到的是节点端已按地址过滤的结果（几十条），内存占用可忽略
            .concatMapSingle { chunk ->
                val fromSingle = cachedLogsSingle("$topic0|from|${chunk.first}|${chunk.last}", fromTopics, chunk, failedChunks)
                val toSingle = cachedLogsSingle("$topic0|to|${chunk.first}|${chunk.last}", toTopics, chunk, failedChunks)

                Single.zip(fromSingle, toSingle) { fromLogs, toLogs ->
                    // from==to（转给自己）的日志两个方向都会返回，TransactionLog 的
                    // equals/hashCode 基于 (transactionHash, logIndex)，distinct 可正确去重
                    (fromLogs + toLogs).distinct()
                }
            }
            .toList()
            .map { logsPerChunk -> logsPerChunk.flatten() }
            .flatMap { relatedLogs ->
                // 本地按 topic 地址再过滤一次（幂等双保险，防个别节点不支持占位匹配）
                val related = relatedLogs.filter { isLogRelated(it, fromTopicIndex, toTopicIndex) }
                Log.d(
                    TAG,
                    "scanAddressLogs: matched ${related.size} logs " +
                            "(raw=${relatedLogs.size}) elapsed=${System.currentTimeMillis() - startedAt}ms"
                )

                fillTimestamps(related).flatMap { logs ->
                    parseLogTransactions(logs, parser)
                }
            }
            .doOnSuccess { result ->
                // 全部分块成功时才推进扫描进度
                if (failedChunks.get() == 0) {
                    scannedBlockHeight = maxOf(scannedBlockHeight, range.last)
                } else {
                    Log.w(TAG, "scanAddressLogs: ${failedChunks.get()} chunks failed, skip progress update")
                }
                Log.d(
                    TAG,
                    "scanAddressLogs: done, parsed=${result.size} failedChunks=${failedChunks.get()} " +
                            "scannedBlockHeight=$scannedBlockHeight " +
                            "elapsed=${System.currentTimeMillis() - startedAt}ms"
                )
            }
            .doOnError { e ->
                Log.e(TAG, "scanAddressLogs: failed: $e")
            }
    }

    /**
     * 获取（或复用）某个分块、某个方向的 eth_getLogs 结果。
     *
     * ERC20 与 ERC721 的查询参数完全相同（同 topic0、同地址占位、同区间），
     * 通过 [logsChunkSingleCache] 去重后同一分块同一方向只发一次请求，两处订阅共享结果。
     */
    private fun cachedLogsSingle(
        cacheKey: String,
        topics: List<ByteArray?>,
        chunk: LongRange,
        failedChunks: AtomicInteger
    ): Single<List<TransactionLog>> {
        return logsChunkSingleCache.computeIfAbsent(cacheKey) {
            throttled(blockchain.getLogs(null, topics, chunk.first, chunk.last, false))
                .onErrorReturn { e ->
                    failedChunks.incrementAndGet()
                    Log.w(TAG, "scanAddressLogs: getLogs(${chunk.first}..${chunk.last}) failed: $e")
                    emptyList()
                }
                .doOnSuccess { logs ->
                    Log.d(
                        TAG,
                        "scanAddressLogs: chunk ${chunk.first}..${chunk.last} " +
                                "returned ${logs.size} logs"
                    )
                }
                .cache()
        }
    }

    /** 本地校验日志的 from/to topic 是否为本地址（对节点端过滤的双保险） */
    private fun isLogRelated(log: TransactionLog, fromTopicIndex: Int, toTopicIndex: Int): Boolean {
        val from = log.topics.getOrNull(fromTopicIndex)?.lowercase()
        val to = log.topics.getOrNull(toTopicIndex)?.lowercase()
        return from == paddedAddress || to == paddedAddress
    }

    /**
     * 为日志批量补齐区块时间戳。
     *
     * 这里必须使用 [IBlockchain.getBlock]（内部为 eth_getBlockByNumber(fullTx=false)），
     * 只拉区块头（几 KB）。绝不能使用 GetBlockWithTransactionsJsonRpc，
     * 那会把整个区块的全部交易再拉一遍（每块数 MB），成倍放大内存占用导致堆爆满与 UI 卡顿。
     */
    private fun fillTimestamps(logs: List<TransactionLog>): Single<List<TransactionLog>> {
        if (logs.isEmpty()) return Single.just(logs)

        // 若日志已带时间戳则无需补齐
        if (logs.all { it.timestamp != null && it.timestamp != 0L }) return Single.just(logs)

        // 同一区块可能有多条日志，先去重后批量取时间戳（复用轻量 getBlock）
        val blockNumbers = logs.map { it.blockNumber }.distinct()
        if (blockNumbers.isEmpty()) return Single.just(logs)

        return blockTimestamps(blockNumbers).map { timestampByBlock ->
            logs.forEach { log ->
                log.timestamp = timestampByBlock[log.blockNumber] ?: 0L
            }
            logs
        }
    }

    /** 按交易哈希聚合日志并拉取收据，补齐 gas 信息后交给 parser 解析 */
    private fun <T> parseLogTransactions(
        logs: List<TransactionLog>,
        parser: (TransactionLog, GasInfo?) -> T?
    ): Single<List<T>> {
        if (logs.isEmpty()) return Single.just(emptyList())

        // 同一笔交易的多条日志共用一个收据，每组取首条作为代表
        val groups = logs.groupBy { it.transactionHash.toHexString() }
        val firstLogs = groups.values.map { group -> group.first() }

        return fetchGasInfos(firstLogs).map { infos ->
            groups.flatMap { (hashKey, groupLogs) ->
                val gasInfo = infos[hashKey]
                groupLogs.mapNotNull { log ->
                    parser(log, gasInfo)
                }
            }
        }
    }

    /** 批量获取收据并构造 GasInfo（key 为交易哈希十六进制字符串），获取失败时对应值为 null */
    private fun fetchGasInfos(firstLogs: List<TransactionLog>): Single<Map<String, GasInfo?>> {
        // 注意：RxJava2 的 flatMapSingle 没有带 maxConcurrency 的重载，
        // 有界并发需用 flatMap + Single.toObservable() 实现
        return Observable.fromIterable(firstLogs)
            .subscribeOn(Schedulers.io())
            .flatMap({ firstLog ->
                val hashKey = firstLog.transactionHash.toHexString()
                throttled(blockchain.getTransactionReceipt(firstLog.transactionHash))
                    .map<Pair<String, GasInfo?>> { receipt ->
                        hashKey to GasInfo(
                            transactionIndex = firstLog.transactionIndex,
                            gasPrice = receipt.effectiveGasPrice,
                            gasUsed = receipt.gasUsed,
                            cumulativeGasUsed = receipt.cumulativeGasUsed
                        )
                    }
                    .onErrorReturn { e ->
                        Log.w(TAG, "fetchGasInfos: receipt failed for $hashKey: $e")
                        hashKey to null
                    }
                    .toObservable()
            }, RECEIPT_FETCH_CONCURRENCY)
            .toList()
            .map { list -> list.toMap() }
    }

    /** 将区块区间按 [chunkSize] 切分为多个子区间 */
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

    private fun parseTokenTransaction(
        log: TransactionLog,
        gasInfo: GasInfo?
    ): ProviderTokenTransaction? {
        // ERC721 的 Transfer 事件含 tokenId（topic 数量为 4），此处只处理 ERC20（topic 数量为 3）
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
            cumulativeGasUsed = gasInfo?.cumulativeGasUsed ?: 0
        )
    }

    private fun parseEip721Transaction(
        log: TransactionLog,
        gasInfo: GasInfo?
    ): ProviderEip721Transaction? {
        // ERC721 的 Transfer 事件有 4 个 topic（含 tokenId），且 value 为空
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
            tokenDecimal = 0
        )
    }

    private fun parseEip1155Transaction(log: TransactionLog): ProviderEip1155Transaction? {
        // TransferSingle(operator, from, to, id, value)：4 个 topic + data(id, value)
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
            tokenSymbol = ""
        )
    }

    /** topic 为 0x 前缀的 32 字节十六进制字符串，取后 20 字节作为地址 */
    private fun topicToAddress(topic: String): Address? {
        val clean = topic.removePrefix("0x")
        if (clean.length < 64) return null
        return try {
            Address(hexToBytes(clean.substring(24, 64)))
        } catch (e: Throwable) {
            Log.e(TAG, "topicToAddress error: $e")
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x")
        val len = clean.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                    Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return result
    }

    // endregion
}
