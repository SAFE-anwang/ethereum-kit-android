package io.horizontalsystems.ethereumkit.api.core

import android.util.Log
import com.anwang.Safe4
import com.anwang.src20.SRC20LockFactory
import com.anwang.types.accountmanager.AccountAmountInfo
import com.anwang.types.accountmanager.AccountRecord
import com.anwang.types.accountmanager.RecordUseInfo
import com.anwang.types.masternode.MasterNodeInfo
import com.anwang.types.proposal.ProposalInfo
import com.anwang.types.proposal.ProposalVoteInfo
import com.anwang.types.safe3.AvailableSafe3Info
import com.anwang.types.safe3.LockedSafe3Info
import com.anwang.types.snvote.SNVoteRetInfo
import com.anwang.types.supernode.SuperNodeInfo
import com.anwang.utils.Safe4Contract
import io.horizontalsystems.ethereumkit.api.jsonrpc.CallJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.DataJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.EstimateGasJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetBalanceJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetStorageAtJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.GetTransactionReceiptJsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcBlock
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransaction
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransactionReceipt
import io.horizontalsystems.ethereumkit.api.models.AccountState
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.EthereumKit.SyncState
import io.horizontalsystems.ethereumkit.core.IApiStorage
import io.horizontalsystems.ethereumkit.core.IBlockchain
import io.horizontalsystems.ethereumkit.core.IBlockchainListener
import io.horizontalsystems.ethereumkit.core.RpcApiProviderFactory
import io.horizontalsystems.ethereumkit.core.Safe4TransactionBuilder
import io.horizontalsystems.ethereumkit.core.eip1559.FeeHistory
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.DefaultBlockParameter
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.ethereumkit.models.Signature
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.ethereumkit.models.TransactionLog
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.DefaultBlockParameterNumber
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Arrays

class RpcBlockchainSafe4(
        val address: Address,
        private val storage: IApiStorage,
        private val syncer: IRpcSyncer,
        private val transactionBuilder: Safe4TransactionBuilder,
        val web3j: Web3j
) : IBlockchain, IRpcSyncerListener, ISafeFourOperate {

    val AccountManagerContractAddr4ac6 by lazy {
        if (Chain.SafeFour.isSafe4TestNetId) {
            "0xA7DBB85CB123106B0d227a317D00A53574694aC6"
        } else {
            "0xF80D63cE916850CF131a4760853B9d685F8ec65a"
        }
    }
    val AccountManagerContractAddr91b2 by lazy {
        if (Chain.SafeFour.isSafe4TestNetId) {
            "0xF6A2C019beF11825E73ed219c7b0582324dE91b2"
        } else {
            "0x5A9CDa846D12e047d87c06f633f2c4f344b33C97"
        }
    }

    val SRC20LockContract by lazy {
        if (Chain.SafeFour.isSafe4TestNetId) {
            "0x4f203092FB68732D8484c099a72dDc5a195f26f9"
        } else {
            "0x6A6dFAF83cc1741FE08A9EFDea596dEad68f7420"
        }
    }

    val safe4SwapContractAddress = "0x0000000000000000000000000000000000001101"

    private val disposables = CompositeDisposable()

    private val web3jSafe4: Safe4 = Safe4(web3j, Chain.SafeFour.id.toLong())
    private val accountManagerContract4ac6 = AccountManagerCustomContract(
        web3j,
        Chain.SafeFour.id.toLong(),
        AccountManagerContractAddr4ac6
    )
    private val accountManagerContract91b2 = AccountManagerCustomContract(
        web3j,
        Chain.SafeFour.id.toLong(),
        AccountManagerContractAddr91b2
    )

    val src20LockFactory by lazy {
        SRC20LockFactory(web3j, Chain.SafeFour.id.toLong(), SRC20LockContract)
    }

    private fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        storage.saveLastBlockHeight(lastBlockHeight)
        listener?.onUpdateLastBlockHeight(lastBlockHeight)
    }

    private fun onUpdateAccountState(state: AccountState) {
        storage.saveAccountState(state)
        listener?.onUpdateAccountState(state)
    }

    private fun syncLastBlockHeight() {
        Single.create { emitter ->
            var error: Throwable
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
                    onUpdateLastBlockHeight(lastBlockNumber.toLong())
                }, {
                    syncState = SyncState.NotSynced(it)
                }).let {
                    disposables.add(it)
                }
    }

    override fun syncAccountState() {
        Single.create { emitter ->
            var error: Throwable
            try {
                val safe4Account = web3jSafe4.account.getTotalAmount(org.web3j.abi.datatypes.Address(address.hex)).amount
                val balance = web3j.ethGetBalance(address.hex, DefaultBlockParameterName.LATEST).send().balance
                val transactionCount = web3j.ethGetTransactionCount(address.hex, DefaultBlockParameterName.LATEST).send().transactionCount
                emitter.onSuccess(BalanceInfo(balance, transactionCount, safe4Account))
//                return@create
            } catch (throwable: Throwable) {
                error = throwable
                emitter.onError(error)
            }
        }.subscribeOn(Schedulers.io())
                .subscribe({ (balance, transactionCount, lockBalance) ->
                    onUpdateAccountState(AccountState(balance, transactionCount.toLong(), timeLockBalance =  lockBalance))
                    syncState = SyncState.Synced()
                }, {
                    syncState = SyncState.NotSynced(it)
                }).let {
                    disposables.add(it)
                }
    }

    fun getLockBalance(contractAddress: Address): Single<BigInteger> {
        return Single.create<BigInteger> {
            try {
//                val accountManagerCustom = AccountManagerCustomContract(web3j, Chain.SafeFour.id.toLong(), contractAddress.hex)
//                val balance = accountManagerCustom.getTotalAmount(org.web3j.abi.datatypes.Address(address.hex)).amount
                // TODO
                it.onSuccess(BigInteger.ZERO)
            } catch (e: Exception) {
                Log.d("RpcBlockchainSafe4", "getLockBalance error=${e}")
                BigInteger.ZERO
            }
        }.onErrorReturnItem(BigInteger.ZERO)
    }

    private fun query(function: Function): Request<*, EthCall> {
        return query(function, org.web3j.abi.datatypes.Address.DEFAULT.value)
    }

    private fun query(function: Function, from: String): Request<*, EthCall> {
        val response = web3j.ethCall(org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(from, Safe4Contract.AccountManagerContractAddr, FunctionEncoder.encode(function)), DefaultBlockParameterName.LATEST)

        return response
    }

    //region IBlockchain
    override var syncState: SyncState = SyncState.NotSynced(EthereumKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateSyncState(value)
            }
        }

    override var listener: IBlockchainListener? = null

    override val source: String
        get() = "RPC ${syncer.source}"

    override val lastBlockHeight: Long?
        get() = storage.getLastBlockHeight()

    override val accountState: AccountState?
        get() = storage.getAccountState()

    override fun start() {
        syncState = SyncState.Syncing()
        syncer.start()
    }

    override fun refresh() {
        when (syncer.state) {
            SyncerState.Preparing -> {
            }

            SyncerState.Ready -> {
                syncAccountState()
                syncLastBlockHeight()
            }

            is SyncerState.NotReady -> {
                syncer.start()
            }
        }
    }

    override fun stop() {
        syncer.stop()
    }

    override fun send(rawTransaction: RawTransaction, signature: Signature, privateKey: BigInteger, lockTime: Int?): Single<Transaction> {
        // 锁仓
        if (lockTime != null) {
            val hash = web3jSafe4.account.deposit(privateKey.toHexString(),
                    rawTransaction.value,
                    org.web3j.abi.datatypes.Address(rawTransaction.to.hex), lockTime.toBigInteger())
            return Single.just(transactionBuilder.transactionDeposit(rawTransaction, signature, lockTime.toBigInteger(), hash))
        }
        val transaction = transactionBuilder.transaction(rawTransaction, signature)
        val encoded = transactionBuilder.encode(rawTransaction, signature)
        val ethSendTransaction = web3j.ethSendRawTransaction(encoded.toHexString()).send()
        return Single.just(ethSendTransaction).map { transaction }
    }

    override fun withdraw(privateKey: BigInteger) {
        try {
            val hash = web3jSafe4.account.withdraw(privateKey.toHexString())
            Log.e("Withdraw", "result=$hash")
        } catch (ex: Exception) {
            Log.e("Withdraw", "error=$ex")
        }
    }

    override fun withdrawByIds(privateKey: BigInteger, ids: List<BigInteger>, type: Int): Single<String> {
        return if (type == 1) {
            Single.just(accountManagerContract4ac6.withdrawByID(privateKey.toHexString(), ids))
        } else if (type == 2) {
            Single.just(accountManagerContract91b2.withdrawByID(privateKey.toHexString(), ids))
        } else {
            Single.just(web3jSafe4.account.withdrawByID(privateKey.toHexString(), ids))
        }
    }

    override fun removeVoteOrApproval(
        privateKey: BigInteger,
        ids: List<BigInteger>
    ): Single<String> {
        return Single.just(web3jSafe4.snvote.removeVoteOrApproval(privateKey.toHexString(), ids))
    }

    override fun superNodeRegister(
            privateKey: String,
            value: BigInteger,
            isUnion: Boolean,
            addr: String,
            lockDay: BigInteger,
            name: String,
            enode: String,
            description: String,
            creatorIncentive: BigInteger,
            partnerIncentive: BigInteger,
            voterIncentive: BigInteger
    ): Single<String> {
        val hash = web3jSafe4.supernode.register(privateKey, value, isUnion,
                org.web3j.abi.datatypes.Address(addr), lockDay,
                name, enode, description, creatorIncentive, partnerIncentive, voterIncentive)
        return Single.just(hash)
    }

    override fun nodeExist(isSuper: Boolean, address: String): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            try {
                val result = if (isSuper)
                        web3jSafe4.supernode.exist(org.web3j.abi.datatypes.Address(address))
                    else
                        web3jSafe4.masternode.exist(org.web3j.abi.datatypes.Address(address))
                emitter.onSuccess(result)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(false)
    }

    override fun masterNodeRegister(
            privateKey: String,
            value: BigInteger,
            isUnion: Boolean,
            addr: String,
            lockDay: BigInteger,
            enode: String,
            description: String,
            creatorIncentive: BigInteger,
            partnerIncentive: BigInteger
    ): Single<String> {
        val hash = web3jSafe4.masternode.register(privateKey, value, isUnion,
                org.web3j.abi.datatypes.Address(addr), lockDay,
                enode, description, creatorIncentive, partnerIncentive)
        return Single.just(hash)
    }

    override fun superAppendRegister(privateKey: String, value: BigInteger, addr: String, lockDay: BigInteger): Single<String> {
        val hash = web3jSafe4.supernode.appendRegister(privateKey, value,
                org.web3j.abi.datatypes.Address(addr), lockDay)
        return Single.just(hash)
    }

    override fun masterAppendRegister(privateKey: String, value: BigInteger, addr: String, lockDay: BigInteger): Single<String> {
        val hash = web3jSafe4.masternode.appendRegister(privateKey, value,
                org.web3j.abi.datatypes.Address(addr), lockDay)
        return Single.just(hash)
    }

    override fun voteOrApprovalWithAmount(privateKey: String, value: BigInteger, isVote: Boolean, dstAddr: String): Single<String> {
        val hash = web3jSafe4.snvote.voteOrApprovalWithAmount(privateKey, value, isVote,
                org.web3j.abi.datatypes.Address(dstAddr))
        return Single.just(hash)
    }

    override fun voteOrApproval(privateKey: String, isVote: Boolean, dstAddr: String, recordIDs: List<BigInteger>): Single<String> {
        val hash = web3jSafe4.snvote.voteOrApproval(privateKey, isVote,
                org.web3j.abi.datatypes.Address(dstAddr),
                recordIDs)
        return Single.just(hash)
    }

    override fun superNodeGetAll(start: Int, count: Int): Single<List<String>> {
        return Single.create<List<String>?> { emitter ->
            try {
                val list = web3jSafe4.supernode.getAll(start.toBigInteger(), count.toBigInteger()).map { it.value }
                emitter.onSuccess(list)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(listOf())
    }

    override fun superNodeInfo(address: String): SuperNodeInfo {
        return web3jSafe4.supernode.getInfo(org.web3j.abi.datatypes.Address(address))
    }

    override fun superNodeInfoById(id: Int): Single<SuperNodeInfo> {
        return Single.just(web3jSafe4.supernode.getInfoByID(id.toBigInteger()))
    }

    override fun masterNodeGetAll(start: Int, count: Int): Single<List<String>> {
        return Single.just(web3jSafe4.masternode.getAll(start.toBigInteger(), count.toBigInteger()).map { it.value })
    }

    override fun masterNodeInfo(address: String): MasterNodeInfo {
        return web3jSafe4.masternode.getInfo(org.web3j.abi.datatypes.Address(address))
    }

    override fun masterNodeInfoById(id: Int): Single<MasterNodeInfo> {
        return Single.just(web3jSafe4.masternode.getInfoByID(id.toBigInteger()))
    }

    override fun getTotalVoteNum(address: String): BigInteger {
        return web3jSafe4.snvote.getTotalVoteNum(org.web3j.abi.datatypes.Address(address))
    }

    override fun getTotalAmount(address: String): BigInteger {
        return web3jSafe4.snvote.getTotalAmount(org.web3j.abi.datatypes.Address(address))
    }

    override fun getAllVoteNum(): BigInteger {
        return web3jSafe4.snvote.allVoteNum
    }

    override fun getLockIds(addr: String, start: Int, count: Int): Single<List<BigInteger>> {
        try {
            return Single.just(web3jSafe4.account.getLockedIDs(org.web3j.abi.datatypes.Address(addr), start.toBigInteger(), count.toBigInteger()))
        } catch (e: Exception) {

        }
        return Single.just(emptyList())
    }

    override fun getTotalIDs(type: Int, addr: String, start: Int, count: Int): Single<List<BigInteger>> {
        try {
            return if (type == 1) {
                Single.just(
                    accountManagerContract4ac6.getTotalIDs(
                        org.web3j.abi.datatypes.Address(addr),
                        start.toBigInteger(),
                        count.toBigInteger()
                    )
                )
            } else if (type == 2) {
                Single.just(
                    accountManagerContract91b2.getTotalIDs(
                        org.web3j.abi.datatypes.Address(addr),
                        start.toBigInteger(),
                        count.toBigInteger()
                    )
                )
            } else {
                Single.just(
                    web3jSafe4.account.getTotalIDs(
                        org.web3j.abi.datatypes.Address(addr),
                        start.toBigInteger(),
                        count.toBigInteger()
                    )
                )
            }
        } catch (e: Exception) {

        }
        return Single.just(emptyList())
    }

    override fun getVotedIDs4Voter(addr: String, start: Int, count: Int): Single<List<BigInteger>> {
        return Single.just(web3jSafe4.snvote.getVotedIDs4Voter(org.web3j.abi.datatypes.Address(addr), start.toBigInteger(), count.toBigInteger()))
    }

    override fun getProposalVoteList(id: Int, start: Int, count: Int): Single<List<ProposalVoteInfo>> {
        return Single.just(web3jSafe4.proposal.getVoteInfo(
                id.toBigInteger(),
                start.toBigInteger(),
                count.toBigInteger()
        ))
    }

    override fun getProposalInfo(id: Int): ProposalInfo {
        return web3jSafe4.proposal.getInfo(id.toBigInteger())
    }

    override fun getRewardIDs(id: Int): List<BigInteger> {
        return web3jSafe4.proposal.getRewardIDs(id.toBigInteger())
    }

    override fun getRecordByID(id: Long, type: Int): AccountRecord {
        return if (type == 1) {
            accountManagerContract4ac6.getRecordByID(id.toBigInteger())
        } else if (type == 2) {
            accountManagerContract91b2.getRecordByID(id.toBigInteger())
        } else {
            web3jSafe4.account.getRecordByID(id.toBigInteger())
        }

    }

    override fun getVoters(address: String, start: Int, count: Int): Single<SNVoteRetInfo> {
        return Single.just(web3jSafe4.snvote.getVoters(
                org.web3j.abi.datatypes.Address(address),
                start.toBigInteger(),
                count.toBigInteger()
        ))
    }

    override fun getAvailableIDs(
        address: String,
        start: Int,
        count: Int
    ): Single<List<BigInteger>> {
        return Single.just(web3jSafe4.account.getAvailableIDs(org.web3j.abi.datatypes.Address(address), start.toBigInteger(), count.toBigInteger()))
    }

    override fun proposalCreate(privateKey: String, title: String, payAmount: BigInteger, payTimes: BigInteger, startPayTime: BigInteger, endPayTime: BigInteger, description: String): Single<String> {
        return Single.just(
                web3jSafe4.proposal.create(
                        privateKey, title, payAmount, payTimes, startPayTime, endPayTime, description
                )
        )
    }

    override fun getAllProposal(start: Int, count: Int): Single<List<BigInteger>> {
        return Single.just(
                web3jSafe4.proposal.getAll(start.toBigInteger(), count.toBigInteger())
        )
    }

    override fun getMineProposal(address: String, start: Int, count: Int): Single<List<BigInteger>> {
        return Single.just(
                web3jSafe4.proposal.getMines(org.web3j.abi.datatypes.Address(address), start.toBigInteger(), count.toBigInteger())
        )
    }

    override fun getVoteInfo(id: Int, start: Int, count: Int): Single<List<ProposalVoteInfo>> {
        return Single.just(
                web3jSafe4.proposal.getVoteInfo(id.toBigInteger(), start.toBigInteger(), count.toBigInteger())
        )
    }

    override fun getProposalVoterNum(id: Int): Single<BigInteger> {
        return Single.just(
                web3jSafe4.proposal.getVoterNum(id.toBigInteger())
        )
    }

    override fun getVoterNum(address: String): Single<BigInteger> {
        return Single.just(
                web3jSafe4.snvote.getVoterNum(org.web3j.abi.datatypes.Address(address))
        )
    }

    override fun getProposalNum(): Single<BigInteger> {
        return Single.just(
                web3jSafe4.proposal.getNum()
        )
    }

    override fun getMineNum(address: String): Single<BigInteger> {
        return Single.just(
                web3jSafe4.proposal.getMineNum(org.web3j.abi.datatypes.Address(address))
        )
    }

    override fun getTops(): Single<List<org.web3j.abi.datatypes.Address>> {
        return Single.just(
                web3jSafe4.supernode.tops
        )
    }

    override fun proposalVote(privateKey: String, id: Int, voteResult: Int): Single<String> {
        return Single.just(
                web3jSafe4.proposal.vote(privateKey, id.toBigInteger(), voteResult.toBigInteger())
        )
    }

    override fun getProposalBalance(): Single<BigInteger> {
        return Single.just(
                web3jSafe4.proposal.balance
        )
    }

    override fun getAddrs4Creator(isSuperNode: Boolean, address: String, start: Int, count: Int): Single<List<org.web3j.abi.datatypes.Address>> {
        return if (isSuperNode) {
                Single.just(
                        web3jSafe4.supernode.getAddrs4Creator(
                            org.web3j.abi.datatypes.Address(address),
                            start.toBigInteger(), count.toBigInteger())
                )
            } else {
                Single.just(
                        web3jSafe4.masternode.getAddrs4Creator(
                            org.web3j.abi.datatypes.Address(address),
                            start.toBigInteger(), count.toBigInteger())
                )
            }

    }

    override fun getAddrs4Partner(isSuperNode: Boolean, address: String, start: Int, count: Int): Single<List<org.web3j.abi.datatypes.Address>> {
        return if (isSuperNode) {
                Single.just(
                        web3jSafe4.supernode.getAddrs4Partner(
                            org.web3j.abi.datatypes.Address(address),
                            start.toBigInteger(), count.toBigInteger())
                )
            } else {
                Single.just(
                        web3jSafe4.masternode.getAddrs4Partner(
                            org.web3j.abi.datatypes.Address(address),
                            start.toBigInteger(), count.toBigInteger())
                )
            }

    }

    override fun getAddrNum4Creator(isSuperNode: Boolean, address: String): Single<BigInteger> {
        return if (isSuperNode) {
                Single.just(
                        web3jSafe4.supernode.getAddrNum4Creator(
                            org.web3j.abi.datatypes.Address(address))
                )
            } else {
                Single.just(
                        web3jSafe4.masternode.getAddrNum4Creator(
                            org.web3j.abi.datatypes.Address(address))
                )
            }

    }

    override fun getAddrNum4Partner(isSuperNode: Boolean, address: String): Single<BigInteger> {
        return if (isSuperNode) {
                Single.just(
                        web3jSafe4.supernode.getAddrNum4Partner(
                            org.web3j.abi.datatypes.Address(address))
                )
            } else {
                Single.just(
                        web3jSafe4.masternode.getAddrNum4Partner(
                            org.web3j.abi.datatypes.Address(address))
                )
            }

    }

    override fun getVotedIDNum4Voter(address: String): Single<BigInteger> {
        return Single.just((web3jSafe4.snvote.getVotedIDNum4Voter(org.web3j.abi.datatypes.Address(address))))
    }

    override fun getRecordUseInfo(recordId: Int): RecordUseInfo {
        return web3jSafe4.account.getRecordUseInfo(recordId.toBigInteger())
    }

    override fun superAddressExist(address: String): Boolean {
        return web3jSafe4.supernode.exist(org.web3j.abi.datatypes.Address(address))
    }

    override fun getNodeNum(isSuperNode: Boolean): BigInteger {
        return if (isSuperNode) {
            web3jSafe4.supernode.num
        } else {
            web3jSafe4.masternode.num
        }
    }

    override fun changeName(privateKey: String, addr: String, name: String): String {
        return web3jSafe4.supernode.changeName(privateKey, org.web3j.abi.datatypes.Address(addr), name)
    }

    override fun changeAddress(isSuperNode: Boolean, privateKey: String, addr: String, newAddr: String): String {
        return if (isSuperNode) {
            web3jSafe4.supernode.changeAddress(privateKey, org.web3j.abi.datatypes.Address(addr), org.web3j.abi.datatypes.Address(newAddr))
        } else {
            web3jSafe4.masternode.changeAddress(privateKey, org.web3j.abi.datatypes.Address(addr), org.web3j.abi.datatypes.Address(newAddr))
        }
    }

    override fun changeEnode(isSuperNode: Boolean, privateKey: String, addr: String, enode: String): String {
        return if (isSuperNode) {
            web3jSafe4.supernode.changeEnode(privateKey, org.web3j.abi.datatypes.Address(addr), enode)
        } else {
            web3jSafe4.masternode.changeEnode(privateKey, org.web3j.abi.datatypes.Address(addr), enode)
        }
    }

    override fun existEnode(isSuperNode: Boolean, enode: String): Boolean {
        return if (isSuperNode) {
            web3jSafe4.supernode.existEnode(enode)
        } else {
            web3jSafe4.masternode.existEnode(enode)
        }
    }

    override fun changeDescription(isSuperNode: Boolean, privateKey: String, addr: String, desc: String): String {
        return if (isSuperNode) {
            web3jSafe4.supernode.changeDescription(privateKey, org.web3j.abi.datatypes.Address(addr), desc)
        } else {
            web3jSafe4.masternode.changeDescription(privateKey, org.web3j.abi.datatypes.Address(addr), desc)
        }
    }

    override fun changeIncentive(
        privateKey: String,
        id: BigInteger,
        creatorIncentive: BigInteger,
        partnerIncentive: BigInteger,
        voterIncentive: BigInteger
    ): String {
        return web3jSafe4.supernode.changeIncentivePlan(privateKey, id, creatorIncentive, partnerIncentive, voterIncentive)
    }

    override fun safe3GetAvailableInfo(safe3Addr: String): Single<AvailableSafe3Info> {
        return Single.just(web3jSafe4.safe3.getAvailableInfo(safe3Addr))
    }

    override fun safe3GetLockedNum(safe3Addr: String): Single<BigInteger> {
        return Single.just(web3jSafe4.safe3.getLockedNum(safe3Addr))
    }

    override fun safe3GetLockedInfo(safe3Addr: String, start: Int, count: Int): Single<List<LockedSafe3Info>> {
        return Single.just(web3jSafe4.safe3.getLockedInfo(safe3Addr, start.toBigInteger(), count.toBigInteger()))
    }

    override fun existAvailableNeedToRedeem(safe3Addr: String): Boolean {
        return web3jSafe4.safe3.existAvailableNeedToRedeem(safe3Addr)
    }

    override fun existLockedNeedToRedeem(safe3Addr: String): Boolean {
        return web3jSafe4.safe3.existLockedNeedToRedeem(safe3Addr)
    }

    override fun existMasterNodeNeedToRedeem(safe3Addr: String): Boolean {
        return web3jSafe4.safe3.existMasterNodeNeedToRedeem(safe3Addr)
    }

    override fun redeemSafe3(callerAddress: String, privateKey: List<String>, targetAddress: String): List<String> {
        return web3jSafe4.safe3.batchRedeemSafe3(callerAddress, privateKey, org.web3j.abi.datatypes.Address(targetAddress))
    }

    override fun redeemMasterNode(callerAddress: String, privateKey: List<String>, targetAddress: String): List<String> {
        return web3jSafe4.safe3.batchRedeemMasterNode(callerAddress, privateKey, privateKey.map { "" }, org.web3j.abi.datatypes.Address(targetAddress))
    }

    override fun existFounder(isSuperNode: Boolean, founder: String): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            try {
                val list = if (isSuperNode)
                    web3jSafe4.supernode.existFounder(org.web3j.abi.datatypes.Address(founder))
                else
                    web3jSafe4.masternode.existFounder(org.web3j.abi.datatypes.Address(founder))
                emitter.onSuccess(list)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(false)
    }

    override fun getTops4Creator(address: String): Single<List<String>> {
        return Single.create<List<String>?> { emitter ->
            try {
                val list = web3jSafe4.supernode.getTops4Creator(org.web3j.abi.datatypes.Address(address)).map { it.value }
                emitter.onSuccess(list)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(listOf())
    }

    override fun existNodeAddress(address: String): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            try {
                val list = web3jSafe4.supernode.existNodeAddress(org.web3j.abi.datatypes.Address(address))
                emitter.onSuccess(list)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(false)
    }

    override fun existNodeEnode(enode: String): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            try {
                val list = web3jSafe4.supernode.existNodeEnode(enode)
                emitter.onSuccess(list)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(false)
    }

    override fun existNodeFounder(address: String): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            try {
                val list = web3jSafe4.supernode.existNodeFounder(org.web3j.abi.datatypes.Address(address))
                emitter.onSuccess(list)
                return@create
            } catch (e: Throwable) {
                emitter.onError(e)
            }
        }.onErrorReturnItem(false)
    }

    override fun addLockDay(privateKey: String, id: Long, day: Int): Single<String> {
        return Single.just(web3jSafe4.account.addLockDay(privateKey, id.toBigInteger(), day.toBigInteger()))
    }

    override fun getAvailableAmount(address: String): AccountAmountInfo {
        return web3jSafe4.account.getAvailableAmount(org.web3j.abi.datatypes.Address(address))
    }

    override fun getAccountTotalAmount(address: String, type: Int): AccountAmountInfo {
        return if (type == 1) {
            accountManagerContract4ac6.getTotalAmount(org.web3j.abi.datatypes.Address(address))
        } else if (type == 2) {
            accountManagerContract91b2.getTotalAmount(org.web3j.abi.datatypes.Address(address))
        } else {
            web3jSafe4.account.getTotalAmount(org.web3j.abi.datatypes.Address(address))
        }
    }

    override fun batchDeposit4One(
        privateKey: String,
        value: BigInteger,
        to: String,
        times: BigInteger,
        spaceDay: BigInteger,
        startDay: BigInteger
    ): Single<String> {
        return Single.create<String> { emitter ->
            try {
                val customAccountManager = getAccountManager(value, times)
                val result = if (customAccountManager != null) {
                    Log.e("batchDeposit4One", "custom account")
                    customAccountManager.batchDeposit4One(
                        privateKey,
                        value,
                        org.web3j.abi.datatypes.Address(to),
                        times,
                        spaceDay,
                        startDay
                    )
                } else {
                    web3jSafe4.account.batchDeposit4One(
                        privateKey,
                        value,
                        org.web3j.abi.datatypes.Address(to),
                        times,
                        spaceDay,
                        startDay
                    )
                }
                emitter.onSuccess(result)
            } catch (e: Throwable) {
                Log.e("batchDeposit4One", "error=$e")
                emitter.onError(e)
            }
        }
    }

    private fun getAccountManager(totalValue: BigInteger, times: BigInteger): AccountManagerCustomContract? {
        val singleValue = totalValue.divide(times).toBigDecimal().movePointLeft(18).stripTrailingZeros()
        Log.d("batchDeposit4One", "${BigDecimal.valueOf(0.01)}, $singleValue")
        return if (singleValue >= BigDecimal.valueOf(0.1) && singleValue < BigDecimal.valueOf(1)) {
            accountManagerContract91b2
        } else if (singleValue >= BigDecimal.valueOf(0.01) && singleValue < BigDecimal(0.1)) {
            accountManagerContract4ac6
        } else {
            null
        }
    }

    override fun getNonce(defaultBlockParameter: DefaultBlockParameter): Single<Long> {
        try {
            return Single.just(web3j.ethGetTransactionCount(address.hex, org.web3j.protocol.core.DefaultBlockParameter.valueOf(defaultBlockParameter.raw)).send().transactionCount.toLong())
        } catch (e: Exception) {
            return Single.just(0L)
        }
    }

    override fun estimateGas(to: Address?, amount: BigInteger?, gasLimit: Long?, gasPrice: GasPrice, data: ByteArray?): Single<Long> {
        return syncer.single(EstimateGasJsonRpc(address, to, amount, gasLimit, gasPrice, data))
    }

    override fun getTransactionReceipt(transactionHash: ByteArray): Single<RpcTransactionReceipt> {
        return syncer.single(GetTransactionReceiptJsonRpc(transactionHash))
        /*val transactionReceipt = web3j.ethGetTransactionReceipt(transactionHash.toHexString()).send().transactionReceipt.get()
        return Single.just(
                RpcTransactionReceipt(
                        transactionHash,
                        transactionReceipt.transactionIndex.toInt(),
                        transactionReceipt.blockHash.toByteArray(),
                        transactionReceipt.blockNumber.toLong(),
                        Address(transactionReceipt.from),
                        Address(transactionReceipt.to),
                        transactionReceipt.effectiveGasPrice.toLong(),
                        transactionReceipt.cumulativeGasUsed.toLong(),
                        transactionReceipt.gasUsed.toLong(),
                        Address(transactionReceipt.contractAddress),
                        transactionReceipt.logs.map {
                           TransactionLog(
                                   it.transactionHash.toByteArray(),
                                   it.transactionIndex.toInt(),
                                   it.logIndex.toInt(),
                                   Address(it.address),
                                   it.blockHash.toByteArray(),
                                   it.blockNumber.toLong(),
                                   it.data.toByteArray(),
                                   it.isRemoved,
                                   it.topics
                           )
                        },
                        transactionReceipt.logsBloom.toByteArray(),
                        transactionReceipt.root.toByteArray(),
                        transactionReceipt.status.toInt()
                )
        )*/
    }

    override fun getTransaction(transactionHash: ByteArray): Single<RpcTransaction> {
        val transaction = web3j.ethGetTransactionByHash(transactionHash.toHexString()).send().transaction.get()
        return Single.just(
                RpcTransaction(
                        transaction.hash.toByteArray(),
                        transaction.nonce.toLong(),
                        transaction.blockHash.toByteArray(),
                        transaction.blockNumber.toLong(),
                        transaction.transactionIndex.toInt(),
                        Address(transaction.from),
                        Address(transaction.to),
                        transaction.value,
                        transaction.gasPrice.toLong(),
                        transaction.maxFeePerGas.toLong(),
                        transaction.maxPriorityFeePerGas.toLong(),
                        transaction.gas.toLong(),
                        transaction.input.toByteArray()
                )
        )
    }

    override fun getBlock(blockNumber: Long): Single<RpcBlock> {
        val ethBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterNumber(blockNumber), false).send()
        return Single.just(RpcBlock(ethBlock.block.number.toLong(), ethBlock.block.timestamp.toLong()))
    }

    override fun getLogs(
        address: Address?,
        topics: List<ByteArray?>,
        fromBlock: Long,
        toBlock: Long,
        pullTimestamps: Boolean
    ): Single<List<TransactionLog>> {
        val ethLogs = web3j.ethGetLogs(
                EthFilter(DefaultBlockParameterNumber(fromBlock), DefaultBlockParameterNumber(toBlock), address?.hex)
        ).send()
        val transactionLogs = ethLogs.logs.map {
            val log = it.get() as org.web3j.protocol.core.methods.response.Log
            TransactionLog(
                    log.transactionHash.toByteArray(),
                    log.transactionIndex.toInt(),
                    log.logIndex.toInt(),
                    Address(log.address),
                    log.blockHash.toByteArray(),
                    log.blockNumber.toLong(),
                    log.data.toByteArray(),
                    log.isRemoved,
                    log.topics
            )
        }
        return if (pullTimestamps) {
            pullTransactionTimestamps(transactionLogs)
        } else {
            Single.just(transactionLogs)
        }
    }

    private fun pullTransactionTimestamps(logs: List<TransactionLog>): Single<List<TransactionLog>> {
        val logsByBlockNumber: MutableMap<Long, MutableList<TransactionLog>> = mutableMapOf()

        for (log in logs) {
            val logs: MutableList<TransactionLog> = logsByBlockNumber[log.blockNumber]
                ?: mutableListOf()
            logs.add(log)
            logsByBlockNumber[log.blockNumber] = logs
        }

        val requestSingles: MutableList<Single<RpcBlock>> = mutableListOf()

        for ((blockNumber, _) in logsByBlockNumber) {
//            requestSingles.add(syncer.single(GetBlockByNumberJsonRpc(blockNumber)))
            requestSingles.add(getBlock(blockNumber))
        }

        return Single.merge(requestSingles).toList().map { blocks ->
            val resultLogs: MutableList<TransactionLog> = mutableListOf()

            for (block in blocks) {
                val logsOfBlock = logsByBlockNumber[block.number] ?: continue

                for (log in logsOfBlock) {
                    log.timestamp = block.timestamp
                    resultLogs.add(log)
                }
            }
            resultLogs
        }
    }

    override fun getStorageAt(contractAddress: Address, position: ByteArray, defaultBlockParameter: DefaultBlockParameter): Single<ByteArray> {
        return syncer.single(GetStorageAtJsonRpc(contractAddress, position, defaultBlockParameter))
    }

    override fun call(contractAddress: Address, data: ByteArray, defaultBlockParameter: DefaultBlockParameter): Single<ByteArray> {
        return syncer.single(RpcBlockchain.callRpc(contractAddress, data, defaultBlockParameter))
    }

    override fun <T> rpcSingle(rpc: JsonRpc<T>): Single<T> {
        return syncer.single(rpc)
    }

    override fun getBalance(address: Address): Single<BigInteger> {
        return syncer.single(GetBalanceJsonRpc(address, DefaultBlockParameter.Latest))
    }

    //endregion

    //region IRpcSyncerListener
    override fun didUpdateLastBlockHeight(lastBlockHeight: Long) {
        onUpdateLastBlockHeight(lastBlockHeight)
    }

    override fun didUpdateSyncerState(state: SyncerState) {
        when (state) {
            SyncerState.Preparing -> {
                syncState = SyncState.Syncing()
            }

            SyncerState.Ready -> {
                syncState = SyncState.Syncing()
                syncAccountState()
                syncLastBlockHeight()
            }

            is SyncerState.NotReady -> {
                syncState = SyncState.NotSynced(state.error)
                disposables.clear()
            }
        }
    }

    fun <T> getGasPrice(): Single<T> {
        val gas = web3j.ethGasPrice().sendAsync()
        return Single.fromFuture(gas).map { it.gasPrice.toLong() as T }
    }

    fun <T> getFeeHistory(blockCount: Long, defaultBlockParameter: DefaultBlockParameter, rewardPercentiles: List<Int>): Single<T> {
        val feeHistory = web3j.ethFeeHistory(blockCount.toInt(),
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(defaultBlockParameter.raw),
                rewardPercentiles.map { it.toDouble() }
                ).send().feeHistory
        return Single.just(
                FeeHistory(
                    feeHistory.baseFeePerGas.map { it.toLong() },
                        feeHistory.gasUsedRatio,
                        feeHistory.oldestBlock.toLong(),
                        feeHistory.reward.map { list ->
                            list.map { it.toLong() }
                        }
                ) as T
        )
    }

    fun safe4ToSrc20(privateKey: BigInteger, transaction: TransactionData): Single<String> {
        return Single.create<String> { emitter ->
            try {
                val function = Function(
                    "deposit",
                    listOf(),
                    listOf<TypeReference<*>>(object : TypeReference<Uint256?>() {})
                )
                val result = call(privateKey, transaction.value, function)
                emitter.onSuccess(result)
            } catch (e: Throwable) {
                Log.d("safe4ToSrc20", "error=$e")
                emitter.onError(e)
            }
        }

    }

    fun src20ToSafe4(privateKey: BigInteger, transaction: TransactionData): Single<String> {
        return Single.create<String> { emitter ->
            try {
                val function = Function(
                    "withdraw",
                    Arrays.asList<Type<*>>(Uint256(transaction.value)),
                    listOf<TypeReference<*>>(object : TypeReference<Uint256?>() {})
                )
                val result = call(privateKey, BigInteger.ZERO, function)
                emitter.onSuccess(result)
            } catch (e: Throwable) {
                Log.e("src20ToSafe4", "e=$e")
                emitter.onError(e)
            }
        }
    }

    fun src20Lock(
        privateKey: String,
        token: String,
        to: String,
        value: BigInteger,
        lockDay: BigInteger
    ): Single<String> {
        return Single.create<String> { emitter ->
            try {
                Log.d("longwen", "lock src20 amount=$value, lockDay=$lockDay")
                val result = src20LockFactory.lock(
                    privateKey,
                    org.web3j.abi.datatypes.Address(token),
                    org.web3j.abi.datatypes.Address(to),
                    value,
                    lockDay
                )
                emitter.onSuccess(result)
            } catch (e: Throwable) {
                Log.d("safe4ToSrc20", "error=$e")
                emitter.onError(e)
            }
        }
    }

    private fun call(privateKey: BigInteger, value: BigInteger, function: Function): String {
        val nonce = getNonce(DefaultBlockParameter.Latest).blockingGet().toBigInteger()
        val gasPrice: BigInteger = web3j.ethGasPrice().send().gasPrice
        val data = FunctionEncoder.encode(function)
        val ethEstimateGas = web3j.ethEstimateGas(
            org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                address.hex,
                nonce,
                gasPrice,
                java.math.BigInteger.ZERO,
                safe4SwapContractAddress,
                value,
                data
            )
        ).send()
        if (ethEstimateGas.error != null) {
            throw java.lang.Exception(ethEstimateGas.error.message)
        }
        val gasLimit =
            ethEstimateGas.amountUsed.multiply(java.math.BigInteger.valueOf(6)).divide(java.math.BigInteger.valueOf(5))
        val rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
            nonce,
            gasPrice,
            gasLimit,
            safe4SwapContractAddress,
            value,
            data
        )
        val credentials: Credentials = Credentials.create(privateKey.toHexString())
        val signedTransactionData = Numeric.toHexString(
            TransactionEncoder.signMessage(
                rawTransaction,
                Chain.SafeFour.id.toLong(),
                credentials
            )
        )
        val ethSendTransaction =
            web3j.ethSendRawTransaction(signedTransactionData).sendAsync().get()
        if (ethSendTransaction.hasError()) {
            throw java.lang.Exception(ethSendTransaction.error.message)
        }
        return ethSendTransaction.transactionHash
    }

    //endregion

    companion object {
        fun instance(
            address: Address,
            storage: IApiStorage,
            syncer: IRpcSyncer,
            transactionBuilder: Safe4TransactionBuilder,
            web3j: Web3j
        ): RpcBlockchainSafe4 {

            val rpcBlockchain = RpcBlockchainSafe4(address, storage, syncer, transactionBuilder, web3j)
            syncer.listener = rpcBlockchain

            return rpcBlockchain
        }

        fun callRpc(contractAddress: Address, data: ByteArray, defaultBlockParameter: DefaultBlockParameter): DataJsonRpc =
            CallJsonRpc(contractAddress, data, defaultBlockParameter)

        fun estimateGas(
            rpcSource: RpcSource,
            from: Address,
            to: Address?,
            amount: BigInteger?,
            gasLimit: Long?,
            gasPrice: GasPrice,
            data: ByteArray?
        ): Single<Long> {
            val rpcApiProvider = RpcApiProviderFactory.nodeApiProvider(rpcSource)

            return rpcApiProvider.single(EstimateGasJsonRpc(from, to, amount, gasLimit, gasPrice, data))
        }
    }
}

data class BalanceInfo(
    val balance: BigInteger,
    val transactionCount: BigInteger,
    val lockBalance: BigInteger
)
