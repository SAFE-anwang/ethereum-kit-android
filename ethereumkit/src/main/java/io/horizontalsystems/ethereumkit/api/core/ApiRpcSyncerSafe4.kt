package io.horizontalsystems.ethereumkit.api.core

import android.util.Log
import io.horizontalsystems.ethereumkit.api.jsonrpc.BlockNumberJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.network.ConnectionManager
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.web3j.protocol.Web3j
import java.util.*
import kotlin.concurrent.schedule

class ApiRpcSyncerSafe4(
    private val rpcApiProvider: IRpcApiProvider,
    private val connectionManager: ConnectionManager,
    private val syncInterval: Long,
    private val web3j: Web3j,
) : IRpcSyncer {
    private val disposables = CompositeDisposable()
    private var isStarted = false
    private var timer: Timer? = null

    init {
        connectionManager.listener = object : ConnectionManager.Listener {
            override fun onConnectionChange() {
                handleConnectionChange()
            }
        }
    }

    //region IRpcSyncer
    override var listener: IRpcSyncerListener? = null
    override val source = "API ${rpcApiProvider.source}"
    override var state: SyncerState = SyncerState.NotReady(EthereumKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.didUpdateSyncerState(value)
            }
        }

    override fun start() {
        isStarted = true

        handleConnectionChange()
    }

    override fun stop() {
        isStarted = false

        state = SyncerState.NotReady(EthereumKit.SyncError.NotStarted())
        disposables.clear()
        stopTimer()
    }

    override fun <T> single(rpc: JsonRpc<T>): Single<T> =
        rpcApiProvider.single(rpc)
    //endregion

    private fun handleConnectionChange() {
        if (!isStarted) return

        if (connectionManager.isConnected) {
            state = SyncerState.Ready
            startTimer()
        } else {
            state = SyncerState.NotReady(EthereumKit.SyncError.NoNetworkConnection())
            stopTimer()
        }
    }

    private fun startTimer() {
        timer = Timer().apply {
            schedule(0, syncInterval * 1000) {
                onFireTimer()
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun onFireTimer() {
        Single.create { emitter ->
            var error: Throwable = NodeApiProvider.ApiProviderError.ApiUrlNotFound
            try {
                val blockNumber = web3j.ethBlockNumber().send().blockNumber
                emitter.onSuccess(blockNumber)
                return@create
            } catch (throwable: Throwable) {
                error = throwable
            }
            emitter.onError(error)
        }.subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({ lastBlockNumber ->
                    listener?.didUpdateLastBlockHeight(lastBlockNumber.toLong())
                }, {
                    state = SyncerState.NotReady(it)
                }).let {
                    disposables.add(it)
                }
    }

}
