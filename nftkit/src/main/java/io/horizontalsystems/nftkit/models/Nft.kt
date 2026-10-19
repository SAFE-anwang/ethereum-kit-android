package io.horizontalsystems.nftkit.models

import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger
import java.util.*

data class Nft(
    val type: NftType,
    val contractAddress: Address,
    val tokenId: BigInteger,
    val tokenName: String
) {
    /**
     * 相等性按 NFT 身份（类型 + 合约地址 + tokenId）判定，与数据库主键
     * NftBalanceRecord(contractAddress, tokenId) 保持一致。
     *
     * 注意不能比较 [tokenName]：它由元数据解析得到、可能为空或随后变化，
     * 会造成同一个 NFT 被判为不同，进而重复插入触发主键冲突。
     */
    override fun equals(other: Any?): Boolean {
        return other is Nft && other.type == type &&
                other.contractAddress == contractAddress && other.tokenId == tokenId
    }

    override fun hashCode(): Int {
        return Objects.hash(type, contractAddress, tokenId)
    }
}