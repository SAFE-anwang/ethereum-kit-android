package io.horizontalsystems.ethereumkit.decorations

import io.horizontalsystems.ethereumkit.models.TransactionTag

class ContractCreationDecoration : TransactionDecoration {
    override fun tags() = listOf(
        // 加上原生币标签：交易列表按原生币（如 SAFE）过滤时，
        // 合约创建（如 NFT 发行）交易才会出现在列表中
        TransactionTag.EVM_COIN,
        "contractCreation"
    )
}
