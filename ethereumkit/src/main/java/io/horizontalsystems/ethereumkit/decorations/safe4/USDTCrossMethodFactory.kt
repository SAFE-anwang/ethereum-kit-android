package io.horizontalsystems.ethereumkit.decorations.safe4

import android.util.Log
import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactory
import io.horizontalsystems.ethereumkit.contracts.ContractMethodHelper
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.spv.core.toBigInteger
import org.web3j.utils.Numeric
import java.math.BigInteger

object USDTCrossMethodFactory : ContractMethodFactory {

    override val methodId = ContractMethodHelper.getMethodId(USDTCrossMethod.methodSignature)

    override fun createMethod(inputArguments: ByteArray): USDTCrossMethod {
        val paramsData = inputArguments.toString(Charsets.UTF_8).substring(10)

        val value = Numeric.toBigInt(paramsData.substring(0, 64))

        val dynamicParam1Pos: BigInteger = Numeric.toBigInt(paramsData.substring(64, 128))
        val dynamicParam2Pos: BigInteger = Numeric.toBigInt(paramsData.substring(128, 192))


        // 解析动态参数 - 链名称
        val chainNameStart: Int = dynamicParam1Pos.toInt() * 2
        val chainNameLength: BigInteger =
            Numeric.toBigInt(paramsData.substring(chainNameStart, chainNameStart + 64))
        val chainNameHex = paramsData.substring(
            chainNameStart + 64,
            chainNameStart + 64 + chainNameLength.toInt() * 2
        )
        val chainName = String(Numeric.hexStringToByteArray(chainNameHex))


        // 解析动态参数 - 接收地址
        val addressStart: Int = dynamicParam2Pos.toInt() * 2
        val addressLength: BigInteger =
            Numeric.toBigInt(paramsData.substring(addressStart, addressStart + 64))
        val addressHex = paramsData.substring(
            addressStart + 64,
            addressStart + 64 + addressLength.toInt() * 2
        )
        val recipientAddress = String(Numeric.hexStringToByteArray(addressHex))

        return USDTCrossMethod(value, chainName, Address(recipientAddress))
    }

}
