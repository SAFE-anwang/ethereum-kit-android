package io.horizontalsystems.uniswapkit.v3

import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.StaticStruct
import org.web3j.abi.datatypes.generated.Int24
import org.web3j.abi.datatypes.generated.Uint24
import org.web3j.abi.datatypes.generated.Uint256

// 1. 定义 MintParams 类
class MintParams(
    token0: Address,
    token1: Address,
    fee: Uint24,
    tickLower: Int24,
    tickUpper: Int24,
    amount0Desired: Uint256,
    amount1Desired: Uint256,
    amount0Min: Uint256,
    amount1Min: Uint256,
    recipient: Address,
    deadline: Uint256
) : StaticStruct(
    token0, token1, fee, tickLower, tickUpper,
    amount0Desired, amount1Desired, amount0Min, amount1Min,
    recipient, deadline
)