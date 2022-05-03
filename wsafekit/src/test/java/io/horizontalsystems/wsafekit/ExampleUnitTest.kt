package io.horizontalsystems.wsafekit

import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.core.toRawHexString
import org.junit.Test

import org.junit.Assert.*
import java.math.BigDecimal

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testABI() {
        val input = Web3jUtils.getEth2safeTransactionInput(BigDecimal(100000000000000).toBigInteger(), "XifXhmvNK7CUtgRXhFWUvLQ9HbqM6bjvrr")
        println(input)

        val encode = input.hexStringToByteArray()
        println(encode.decodeToString())
        println(encode.toHexString())

//        val encode = input.toString().hexToByteArray()
//        println(encode.decodeToString())
//        println(encode.toHexString())
    }

}
