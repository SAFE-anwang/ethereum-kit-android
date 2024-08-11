package io.horizontalsystems.ethereumkit.api.core

import com.anwang.types.masternode.MasterNodeInfo
import com.anwang.types.proposal.ProposalInfo
import com.anwang.types.proposal.ProposalVoteInfo
import com.anwang.types.snvote.SNVoteRetInfo
import com.anwang.types.supernode.SuperNodeInfo
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.reactivex.Single
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger
import java.util.Arrays

class RpcHandler(val onSuccess: (RpcResponse) -> Unit, val onError: (Throwable) -> Unit)
typealias SubscriptionHandler = (RpcSubscriptionResponse) -> Unit

data class RpcResponse(val id: Int, val result: JsonElement?, val error: Error?) {
    data class Error(val code: Int, val message: String)
}

data class RpcSubscriptionResponse(val method: String, val params: Params) {
    data class Params(@SerializedName("subscription") val subscriptionId: String, val result: JsonElement)
}

data class RpcGeneralResponse(val id: Int?, val result: JsonElement?, val error: RpcResponse.Error?, val method: String?, val params: RpcSubscriptionResponse.Params?)

interface IRpcWebSocket {
    var listener: IRpcWebSocketListener?
    val source: String

    fun start()
    fun stop()
    fun <T> send(rpc: JsonRpc<T>)
}

interface IRpcWebSocketListener {
    fun didUpdate(socketState: WebSocketState)
    fun didReceive(response: RpcResponse)
    fun didReceive(response: RpcSubscriptionResponse)
}

sealed class WebSocketState {
    object Connecting : WebSocketState()
    object Connected : WebSocketState()
    class Disconnected(val error: Throwable) : WebSocketState()

    sealed class DisconnectError : Throwable() {
        object NotStarted : DisconnectError()
        class SocketDisconnected(val reason: String) : DisconnectError()
    }
}

interface IRpcApiProvider {
    val source: String

    fun <T> single(rpc: JsonRpc<T>): Single<T>
}

interface IRpcSyncer {
    var listener: IRpcSyncerListener?

    val source: String
    val state: SyncerState

    fun start()
    fun stop()
    fun <T> single(rpc: JsonRpc<T>): Single<T>
}

interface IRpcSyncerListener {
    fun didUpdateSyncerState(state: SyncerState)
    fun didUpdateLastBlockHeight(lastBlockHeight: Long)
}

sealed class SyncerState {
    object Preparing : SyncerState()
    object Ready : SyncerState()
    class NotReady(val error: Throwable) : SyncerState()
}
