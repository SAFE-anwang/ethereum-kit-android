package io.horizontalsystems.ethereumkit.core

import android.util.Log
import com.google.gson.Gson
import io.horizontalsystems.ethereumkit.api.core.IRpcWebSocketListener
import io.horizontalsystems.ethereumkit.api.core.NodeWebSocket
import io.horizontalsystems.ethereumkit.api.core.RpcResponse
import io.horizontalsystems.ethereumkit.api.core.RpcSubscriptionResponse
import io.horizontalsystems.ethereumkit.api.core.WebSocketState
import io.horizontalsystems.ethereumkit.api.jsonrpc.SubscribeJsonRpc
import io.horizontalsystems.ethereumkit.models.Address
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Chainstack 实时同步：通过 websocket `eth_subscribe` 监听新区块与地址相关日志。
 *
 * 收到事件后仅回调区块高度（[onNewBlock]），由上层触发一次「立即同步」，
 * 新增交易仍由既有的增量扫描路径补齐（`eth_getLogs` 拉取代币转账、
 * trace/区块扫描覆盖普通转账），因此这里不做任何交易解析，
 * 避免与 provider 的解析、装饰、去重逻辑重复实现。
 *
 * 分级触发：
 * - `logs`（topic0=Transfer，from/to 各一个方向）：地址相关的代币转账，出现即触发；
 * - `newHeads`：仅作兜底，最多每 [FALLBACK_NOTIFY_INTERVAL_MS] 触发一次，
 *   用于发现不产生日志的普通转账（SAFE/原生币转账），同时保证不出块时也有兜底。
 *
 * 连接断开由 [NodeWebSocket]（Scarlet 退避重连）自动恢复，重连后重新订阅。
 */
class ChainstackRealtimeSync(
    wsUrl: String,
    private val address: Address,
    private val onNewBlock: (Long) -> Unit,
) : IRpcWebSocketListener {

    companion object {
        const val TAG = "ChainstackRealtime"

        /** keccak256("Transfer(address,address,uint256)") */
        private const val TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

        /** 订阅类型标识 */
        private const val KIND_NEW_HEADS = "newHeads"
        private const val KIND_LOGS_FROM = "logs-from"
        private const val KIND_LOGS_TO = "logs-to"

        /**
         * newHeads 兜底触发的最小间隔。
         *
         * 分级触发：地址相关日志（代币转账）出现时立即触发；
         * newHeads 只做兜底，按该周期最多触发一次，用于覆盖不产生日志的普通转账
         * （SAFE/原生币转账在链上没有任何日志，只能靠区块通知发现）。
         * 出块很快时被跳过的区块由下一次触发一并补齐（增量扫描按区间覆盖，不会漏交易）。
         */
        private const val FALLBACK_NOTIFY_INTERVAL_MS = 30_000L

        /**
         * 由 HTTP RPC 地址推导 websocket 地址。
         *
         * Chainstack 同一节点的 ws 入口与 http 入口同主机同路径，仅协议不同；
         * 若节点实际路径不同，可通过 [io.horizontalsystems.ethereumkit.models.TransactionSource.SourceType.Chainstack.wsUrls] 显式指定。
         */
        fun wsUrlFrom(rpcUrl: String): String = when {
            rpcUrl.startsWith("https://") -> "wss://${rpcUrl.removePrefix("https://")}"
            rpcUrl.startsWith("http://") -> "ws://${rpcUrl.removePrefix("http://")}"
            else -> rpcUrl
        }
    }

    private val gson: Gson = EthereumKit.gson

    private val socket = NodeWebSocket(URI(wsUrl), gson)

    private val rpcId = AtomicInteger(0)

    /** 本地请求 id → 订阅类型：[didReceive] 收到订阅 id 后归入 [subscriptions] */
    private val pendingSubscribes = ConcurrentHashMap<Int, String>()

    /** 订阅 id → 订阅类型 */
    private val subscriptions = ConcurrentHashMap<String, String>()

    /** 事件日志中地址 topic 的左填充形式（32 字节十六进制） */
    private val paddedAddress = "0x000000000000000000000000" +
            address.hex.removePrefix("0x").lowercase()

    private var started = false

    /** 已通知过的最高区块：同一区块的多个事件只触发一次同步 */
    @Volatile
    private var lastNotifiedBlock = -1L

    /** 上次触发的时间戳，用于 newHeads 兜底节流 */
    @Volatile
    private var lastNotifyTime = 0L

    fun start() {
        if (started) return
        started = true
        socket.listener = this
        socket.start()
    }

    fun stop() {
        if (!started) return
        started = false
        socket.listener = null
        socket.stop()
        subscriptions.clear()
        pendingSubscribes.clear()
        lastNotifiedBlock = -1L
    }

    // region IRpcWebSocketListener

    override fun didUpdate(socketState: WebSocketState) {
        when (socketState) {
            WebSocketState.Connecting -> Log.d(TAG, "connecting")

            WebSocketState.Connected -> {
                Log.i(TAG, "connected, subscribe newHeads & logs for ${address.hex}")
                subscriptions.clear()
                pendingSubscribes.clear()
                subscribe(KIND_NEW_HEADS, listOf(KIND_NEW_HEADS))
                subscribe(
                    KIND_LOGS_FROM,
                    listOf("logs", mapOf("topics" to listOf(TRANSFER_TOPIC, paddedAddress)))
                )
                subscribe(
                    KIND_LOGS_TO,
                    listOf("logs", mapOf("topics" to listOf(TRANSFER_TOPIC, null, paddedAddress)))
                )
            }

            is WebSocketState.Disconnected -> {
                Log.w(TAG, "disconnected: ${socketState.error.message ?: socketState.error.javaClass.simpleName}")
                subscriptions.clear()
                pendingSubscribes.clear()
            }
        }
    }

    override fun didReceive(response: RpcResponse) {
        val kind = pendingSubscribes.remove(response.id) ?: return
        val subscriptionId = response.result?.takeIf { !it.isJsonNull }?.asString
        if (subscriptionId == null) {
            Log.w(TAG, "$kind subscribe failed: ${response.error?.message}")
            return
        }
        subscriptions[subscriptionId] = kind
        Log.d(TAG, "$kind subscribed: $subscriptionId")
    }

    override fun didReceive(response: RpcSubscriptionResponse) {
        try {
            val kind = subscriptions[response.params.subscriptionId] ?: return
            val payload = response.params.result.takeIf { it.isJsonObject }?.asJsonObject ?: return
            val blockNumber = when (kind) {
                KIND_NEW_HEADS -> parseHexLong(payload.get("number")?.asString)
                else -> parseHexLong(payload.get("blockNumber")?.asString)
            } ?: return

            notifyNewBlock(blockNumber, fromLog = kind != KIND_NEW_HEADS)
        } catch (e: Throwable) {
            Log.w(TAG, "handle subscription response error: $e")
        }
    }

    // endregion

    private fun subscribe(kind: String, params: List<Any>) {
        val rpc = SubscribeJsonRpc(params)
        rpc.id = rpcId.incrementAndGet()
        pendingSubscribes[rpc.id] = kind
        try {
            socket.send(rpc)
        } catch (e: Throwable) {
            pendingSubscribes.remove(rpc.id)
            Log.w(TAG, "$kind subscribe send failed: $e")
        }
    }

    private fun notifyNewBlock(blockNumber: Long, fromLog: Boolean) {
        // 同一区块的区块通知与日志通知只触发一次；旧区块（如重组回退）忽略
        if (blockNumber <= lastNotifiedBlock) return

        val now = System.currentTimeMillis()
        // 分级触发：日志（代币转账）立即触发；newHeads 按兜底周期触发，
        // 避免出块很快时每个区块都做一次同步/余额刷新
        if (!fromLog && now - lastNotifyTime < FALLBACK_NOTIFY_INTERVAL_MS) return

        lastNotifiedBlock = blockNumber
        lastNotifyTime = now
        Log.i(TAG, "new activity at block $blockNumber (${if (fromLog) "log" else "head"})")
        onNewBlock(blockNumber)
    }

    private fun parseHexLong(hex: String?): Long? = try {
        hex?.removePrefix("0x")?.toLong(16)
    } catch (e: Throwable) {
        null
    }
}
