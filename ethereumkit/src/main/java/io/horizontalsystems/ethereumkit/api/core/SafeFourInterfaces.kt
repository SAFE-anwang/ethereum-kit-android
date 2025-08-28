package io.horizontalsystems.ethereumkit.api.core

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
import io.reactivex.Single
import org.web3j.abi.datatypes.Address
import java.math.BigInteger

interface ISafeFourOperate {
	fun withdraw(privateKey: BigInteger)
	fun withdrawByIds(privateKey: BigInteger, ids: List<BigInteger>, type: Int): Single<String>

	fun superNodeRegister(
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
	): Single<String>
	
	fun nodeExist(isSuper: Boolean, address: String): Single<Boolean>

	fun masterNodeRegister(
			privateKey: String,
			value: BigInteger,
			isUnion: Boolean,
			addr: String,
			lockDay: BigInteger,
			enode: String,
			description: String,
			creatorIncentive: BigInteger,
			partnerIncentive: BigInteger
	): Single<String>

	fun superAppendRegister(
			privateKey: String,
			value: BigInteger,
			addr: String,
			lockDay: BigInteger
	): Single<String>

	fun masterAppendRegister(
			privateKey: String,
			value: BigInteger,
			addr: String,
			lockDay: BigInteger
	): Single<String>

	fun voteOrApprovalWithAmount(
			privateKey: String,
			value: BigInteger,
			isVote: Boolean,
			dstAddr: String,
	): Single<String>

	fun voteOrApproval(
			privateKey: String,
			isVote: Boolean,
			dstAddr: String,
			recordIDs: List<BigInteger>
	): Single<String>

	fun superNodeGetAll(start: Int, count: Int): Single<List<String>>
	fun superNodeInfo(address: String): SuperNodeInfo
	fun superNodeInfoById(id: Int): Single<SuperNodeInfo>

	fun masterNodeGetAll(start: Int, count: Int): Single<List<String>>
	fun masterNodeInfo(address: String): MasterNodeInfo
	fun masterNodeInfoById(id: Int): Single<MasterNodeInfo>

	fun getTotalVoteNum(address: String): BigInteger
	fun getTotalAmount(address: String): BigInteger
	fun getAllVoteNum(): BigInteger

	fun getLockIds(addr: String, start: Int, count: Int): Single<List<BigInteger>>
	fun getTotalIDs(type: Int, addr: String, start: Int, count: Int): Single<List<BigInteger>>
	fun getVotedIDs4Voter(addr: String, start: Int, count: Int): Single<List<BigInteger>>

	fun getProposalVoteList(id: Int, start: Int, count: Int): Single<List<ProposalVoteInfo>>

	fun getProposalInfo(id: Int): ProposalInfo
	fun getRewardIDs(id: Int): List<BigInteger>

	fun getRecordByID(id: Int, type: Int = 0): AccountRecord

	fun getVoters(address: String, start: Int, count: Int): Single<SNVoteRetInfo>
	fun getAvailableIDs(address: String, start: Int, count: Int): Single<List<BigInteger>>

	fun proposalCreate(
			privateKey: String,
			title: String,
			payAmount: BigInteger,
			payTimes: BigInteger,
			startPayTime: BigInteger,
			endPayTime: BigInteger,
			description: String
	): Single<String>
	fun getAllProposal(start: Int, count: Int): Single<List<BigInteger>>

	fun getMineProposal(privateKey: String, start: Int, count: Int): Single<List<BigInteger>>
	fun getVoteInfo(id: Int, start: Int, count: Int): Single<List<ProposalVoteInfo>>

	fun getVoterNum(address: String): Single<BigInteger>
	fun getProposalVoterNum(id: Int): Single<BigInteger>
	fun getProposalNum(): Single<BigInteger>
	fun getMineNum(privateKey: String): Single<BigInteger>

	fun getTops(): Single<List<Address>>

	fun proposalVote(privateKey: String, id: Int, voteResult: Int): Single<String>
	fun getProposalBalance(): Single<BigInteger>

	fun getAddrs4Creator(isSuperNode: Boolean, address: String, start: Int, count: Int): Single<List<Address>>
	fun getAddrNum4Creator(isSuperNode: Boolean, address: String): Single<BigInteger>

	fun getAddrs4Partner(isSuperNode: Boolean, address: String, start: Int, count: Int): Single<List<Address>>
	fun getAddrNum4Partner(isSuperNode: Boolean, address: String): Single<BigInteger>

	fun getVotedIDNum4Voter(address: String): Single<BigInteger>

	fun getRecordUseInfo(recordId: Int): RecordUseInfo

	fun superAddressExist(address: String): Boolean

	fun getNodeNum(isSuperNode: Boolean): BigInteger

	fun changeName(privateKey: String, addr: String, name: String): String
	fun changeAddress(isSuperNode: Boolean, privateKey: String, addr: String, newAddr: String): String
	fun changeEnode(isSuperNode: Boolean, privateKey: String, addr: String, enode: String): String

	fun existEnode(isSuperNode: Boolean, enode: String): Boolean
	fun changeDescription(isSuperNode: Boolean, privateKey: String, addr: String, desc: String): String
	fun changeIncentive(privateKey: String, id: BigInteger, creatorIncentive: BigInteger, partnerIncentive: BigInteger, voterIncentive: BigInteger): String

	fun safe3GetAvailableInfo(safe3Addr: String): Single<AvailableSafe3Info>
	fun safe3GetLockedNum(safe3Addr: String): Single<BigInteger>

	fun safe3GetLockedInfo(safe3Addr: String, start: Int, count: Int): Single<List<LockedSafe3Info>>

	fun existAvailableNeedToRedeem(safe3Addr: String): Boolean
	fun existLockedNeedToRedeem(safe3Addr: String): Boolean

	fun existMasterNodeNeedToRedeem(safe3Addr: String): Boolean

	fun redeemSafe3(callerAddress: String, privateKey: List<String>, targetAddress: String): List<String>

	fun redeemMasterNode(callerAddress: String, privateKey: List<String>, targetAddress: String): List<String>

	fun existFounder(isSuperNode: Boolean, founder: String): Single<Boolean>
	fun getTops4Creator(address: String): Single<List<String>>

	fun existNodeAddress(address: String): Single<Boolean>
	fun existNodeEnode(enode: String): Single<Boolean>
	fun existNodeFounder(address: String): Single<Boolean>
	fun addLockDay(privateKey: String, id: Long, day: Int): Single<String>

	fun getAvailableAmount(address: String): AccountAmountInfo
	fun getAccountTotalAmount(address: String, type: Int): AccountAmountInfo

	fun batchDeposit4One(privateKey: String, value:BigInteger, to:String, times:BigInteger, spaceDay: BigInteger, startDay: BigInteger): Single<String>
}