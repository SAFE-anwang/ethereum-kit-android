package io.horizontalsystems.uniswapkit.liquidity.v3

import java.math.BigInteger

/**
 * Uniswap V3 / PancakeSwap V3 TickMath 库
 *
 * 该库提供了 tick 与 sqrtPriceX96 之间的相互转换功能。
 * 核心公式: sqrt(1.0001^tick) * 2^96
 *
 * 基于 Uniswap V3 官方 Solidity 实现 (MIT License)
 * 原始代码: https://github.com/Uniswap/v3-core/blob/main/contracts/libraries/TickMath.sol
 */
object TickMath {

    // ==================== 常量定义 ====================

    /**
     * 最小 tick 值
     * 从 log base 1.0001 of 2**-128 计算得出
     */
    const val MIN_TICK = -887272

    /**
     * 最大 tick 值
     * 等于 -MIN_TICK
     */
    const val MAX_TICK = 887272

    /**
     * 最小 sqrt 价格 (Q64.96 格式)
     * 对应 getSqrtRatioAtTick(MIN_TICK) 的返回值
     */
    val MIN_SQRT_RATIO = BigInteger.valueOf(4295128739)

    /**
     * 最大 sqrt 价格 (Q64.96 格式)
     * 对应 getSqrtRatioAtTick(MAX_TICK) 的返回值
     */
    val MAX_SQRT_RATIO = BigInteger("1461446703485210103287273052203988822378723970342")

    /**
     * 2^32 常量，用于位运算
     */
    private val Q32 = BigInteger.ONE.shiftLeft(32)

    /**
     * 2^96 常量
     */
    private val Q96 = BigInteger.ONE.shiftLeft(96)

    /**
     * 2^128 常量
     */
    private val Q128 = BigInteger.ONE.shiftLeft(128)

    /**
     * 类型最大值 (uint256)
     */
    private val MAX_UINT256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

    // ==================== 核心函数 ====================

    /**
     * 根据 tick 计算 sqrtPriceX96
     *
     * 计算公式: sqrtPriceX96 = sqrt(1.0001^tick) * 2^96
     *
     * @param tick tick 值，必须在 [MIN_TICK, MAX_TICK] 范围内
     * @return sqrtPriceX96 Q64.96 格式的价格
     * @throws IllegalArgumentException 当 tick 超出有效范围时
     */
    fun getSqrtRatioAtTick(tick: Int): BigInteger {
        require(tick >= MIN_TICK && tick <= MAX_TICK) {
            "Tick must be between $MIN_TICK and $MAX_TICK, got $tick"
        }

        val absTick = if (tick < 0) -tick.toLong() else tick.toLong()

        // 计算 ratio (Q128.128 格式)
        var ratio = if (absTick and 0x1 != 0L) {
            BigInteger("fffcb933bd6fad37aa2d162d1a594001", 16)
        } else {
            BigInteger.ONE.shiftLeft(128)
        }

        // 通过逐位检查构建 ratio
        if (absTick and 0x2 != 0L) ratio = mulShift128(ratio, "fff97272373d413259a46990580e213a")
        if (absTick and 0x4 != 0L) ratio = mulShift128(ratio, "fff2e50f5f656932ef12357cf3c7fdcc")
        if (absTick and 0x8 != 0L) ratio = mulShift128(ratio, "ffe5caca7e10e4e61c3624eaa0941cd0")
        if (absTick and 0x10 != 0L) ratio = mulShift128(ratio, "ffcb9843d60f6159c9db58835c926644")
        if (absTick and 0x20 != 0L) ratio = mulShift128(ratio, "ff973b41fa98c081472e6896dfb254c0")
        if (absTick and 0x40 != 0L) ratio = mulShift128(ratio, "ff2ea16466c96a3843ec78b326b52861")
        if (absTick and 0x80 != 0L) ratio = mulShift128(ratio, "fe5dee046a99a2a811c461f1969c3053")
        if (absTick and 0x100 != 0L) ratio = mulShift128(ratio, "fcbe86c7900a88aedcffc83b479aa3a4")
        if (absTick and 0x200 != 0L) ratio = mulShift128(ratio, "f987a7253ac413176f2b074cf7815e54")
        if (absTick and 0x400 != 0L) ratio = mulShift128(ratio, "f3392b0822b70005940c7a398e4b70f3")
        if (absTick and 0x800 != 0L) ratio = mulShift128(ratio, "e7159475a2c29b7443b29c7fa6e889d9")
        if (absTick and 0x1000 != 0L) ratio = mulShift128(ratio, "d097f3bdfd2022b8845ad8f792aa5825")
        if (absTick and 0x2000 != 0L) ratio = mulShift128(ratio, "a9f746462d870fdf8a65dc1f90e061e5")
        if (absTick and 0x4000 != 0L) ratio = mulShift128(ratio, "70d869a156d2a1b890bb3df62baf32f7")
        if (absTick and 0x8000 != 0L) ratio = mulShift128(ratio, "31be135f97d08fd981231505542fcfa6")
        if (absTick and 0x10000 != 0L) ratio = mulShift128(ratio, "9aa508b5b7a84e1c677de54f3e99bc9")
        if (absTick and 0x20000 != 0L) ratio = mulShift128(ratio, "5d6af8dedb81196699c329225ee604")
        if (absTick and 0x40000 != 0L) ratio = mulShift128(ratio, "2216e584f5fa1ea926041bedfe98")
        if (absTick and 0x80000 != 0L) ratio = mulShift128(ratio, "48a170391f7dc42444e8fa2")

        // 如果 tick > 0，取倒数
        if (tick > 0) {
            ratio = MAX_UINT256.divide(ratio)
        }

        // 从 Q128.128 转换为 Q128.96，向上取整
        val result = ratio.shiftRight(32)
        val remainder = ratio.mod(Q32)

        return if (remainder == BigInteger.ZERO) {
            result
        } else {
            result.add(BigInteger.ONE)
        }
    }

    /**
     * 根据 sqrtPriceX96 计算对应的 tick 值
     *
     * 返回最大的 tick 值，使得 getSqrtRatioAtTick(tick) <= sqrtPriceX96
     *
     * @param sqrtPriceX96 Q64.96 格式的价格，必须在 [MIN_SQRT_RATIO, MAX_SQRT_RATIO) 范围内
     * @return tick 值
     * @throws IllegalArgumentException 当 sqrtPriceX96 超出有效范围时
     */
    fun getTickAtSqrtRatio(sqrtPriceX96: BigInteger): Int {
        require(sqrtPriceX96 >= MIN_SQRT_RATIO && sqrtPriceX96 < MAX_SQRT_RATIO) {
            "sqrtPriceX96 must be between $MIN_SQRT_RATIO and $MAX_SQRT_RATIO"
        }

        // 转换为 Q128.128 格式
        var ratio = sqrtPriceX96.shiftLeft(32)
        var r = ratio
        var msb = 0L

        // 找到最高有效位 (most significant bit)
        if (r > BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)) {
            msb = msb or 128
            r = r.shiftRight(128)
        }
        if (r > BigInteger("FFFFFFFFFFFFFFFF", 16)) {
            msb = msb or 64
            r = r.shiftRight(64)
        }
        if (r > BigInteger("FFFFFFFF", 16)) {
            msb = msb or 32
            r = r.shiftRight(32)
        }
        if (r > BigInteger("FFFF", 16)) {
            msb = msb or 16
            r = r.shiftRight(16)
        }
        if (r > BigInteger("FF", 16)) {
            msb = msb or 8
            r = r.shiftRight(8)
        }
        if (r > BigInteger("F", 16)) {
            msb = msb or 4
            r = r.shiftRight(4)
        }
        if (r > BigInteger("3", 16)) {
            msb = msb or 2
            r = r.shiftRight(2)
        }
        if (r > BigInteger.ONE) {
            msb = msb or 1
        }

        // 计算对数值
        r = if (msb >= 128) {
            ratio.shiftRight((msb - 127).toInt())
        } else {
            ratio.shiftLeft((127 - msb).toInt())
        }

        var log2 = (msb - 128).toBigInteger().shiftLeft(64)

        // 使用牛顿法逼近 log2
        for (i in 0 until 8) {
            r = r.multiply(r).shiftRight(127)
            val f = r.shiftRight(128)
            log2 = log2.or(f.shiftLeft((63 - i * 7)))
            r = r.shiftRight(f.toInt())
        }

        // 从 log2 计算 tick
        // tick = floor((log2 * ln2) / ln1.0001)
        // 简化计算: tick = (log2 * 13043817825332782212) >> 96

        // log2 是 Q64.64 格式，乘以 ln2/ln1.0001 的近似值
        val tickNumerator = log2.multiply(BigInteger("13043817825332782212"))
        var tick = tickNumerator.shiftRight(96).toLong()

        // 修正边界条件
        if (getSqrtRatioAtTick(tick.toInt()) > sqrtPriceX96) {
            tick--
        }

        return tick.toInt()
    }

    /**
     * 从价格计算 sqrtPriceX96
     *
     * @param price 价格 (token1/token0)，例如 3000 USDT/WBNB
     * @param decimals0 token0 的小数位数
     * @param decimals1 token1 的小数位数
     * @return sqrtPriceX96
     */
    fun priceToSqrtPriceX96(
        price: Double,
        decimals0: Int,
        decimals1: Int
    ): BigInteger {
        // 调整小数位数
        val adjustedPrice = price * Math.pow(10.0, (decimals0 - decimals1).toDouble())

        // sqrtPrice = sqrt(price)
        val sqrtPrice = Math.sqrt(adjustedPrice)

        // sqrtPriceX96 = sqrtPrice * 2^96
        val sqrtPriceX96 = sqrtPrice * Math.pow(2.0, 96.0)

        return BigInteger.valueOf(sqrtPriceX96.toLong())
    }

    /**
     * 从 sqrtPriceX96 计算价格
     *
     * @param sqrtPriceX96 Q64.96 格式的 sqrt 价格
     * @param decimals0 token0 的小数位数
     * @param decimals1 token1 的小数位数
     * @return 价格 (token1/token0)
     */
    fun sqrtPriceX96ToPrice(
        sqrtPriceX96: BigInteger,
        decimals0: Int,
        decimals1: Int
    ): Double {
        // sqrtPrice = sqrtPriceX96 / 2^96
        val sqrtPrice = sqrtPriceX96.toDouble() / Math.pow(2.0, 96.0)

        // price = sqrtPrice^2
        var price = sqrtPrice * sqrtPrice

        // 调整小数位数
        price /= Math.pow(10.0, (decimals0 - decimals1).toDouble())

        return price
    }

    // ==================== 辅助函数 ====================

    /**
     * 128位乘法并右移128位
     */
    private fun mulShift128(x: BigInteger, yHex: String): BigInteger {
        val y = BigInteger(yHex, 16)
        return x.multiply(y).shiftRight(128)
    }

    /**
     * 获取当前价格对应的 tick 范围
     * 用于创建流动性时设置合理的价格区间
     *
     * @param currentTick 当前 tick
     * @param widthPercent 区间宽度百分比，例如 10 表示当前价格上下各 10% 的范围
     * @return Pair(tickLower, tickUpper)
     */
    fun getTickRange(currentTick: Int, widthPercent: Int): Pair<Int, Int> {
        require(widthPercent in 1..100) { "widthPercent must be between 1 and 100" }

        // 价格区间 = (1 - widthPercent/100) 到 (1 + widthPercent/100)
        // tick 范围通过公式计算: price = 1.0001^tick

        // 简化计算：tick 变化 1 对应价格变化约 0.01%
        val deltaTick = (Math.log(1.0 + widthPercent / 100.0) / Math.log(1.0001)).toInt()

        var tickLower = currentTick - deltaTick
        var tickUpper = currentTick + deltaTick

        // 确保在合法范围内
        tickLower = maxOf(tickLower, MIN_TICK)
        tickUpper = minOf(tickUpper, MAX_TICK)

        return Pair(tickLower, tickUpper)
    }

    /**
     * 全范围流动性参数 (相当于 V2)
     * 返回 MIN_TICK 到 MAX_TICK
     */
    fun getFullRangeTickRange(): Pair<Int, Int> {
        return Pair(MIN_TICK, MAX_TICK)
    }
}