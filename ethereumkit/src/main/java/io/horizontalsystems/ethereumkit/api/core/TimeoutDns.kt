package io.horizontalsystems.ethereumkit.api.core

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import java.util.concurrent.*

class TimeoutDns(
    private val timeoutMillis: Long = 10000,
    private val maxRetries: Int = 2,
    private val enableCache: Boolean = true
) : Dns {

    private val executor = Executors.newCachedThreadPool()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    data class CacheEntry(
        val addresses: List<InetAddress>,
        val timestamp: Long,
        val ttl: Long = 60000 // 默认缓存 60 秒
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // 检查缓存
        if (enableCache) {
            cache[hostname]?.let { entry ->
                if (!entry.isExpired()) {
                    return entry.addresses
                }
            }
        }

        var lastException: Exception? = null

        // 重试机制
        for (retry in 0..maxRetries) {
            try {
                val addresses = performLookup(hostname)

                // 更新缓存
                if (enableCache && addresses.isNotEmpty()) {
                    cache[hostname] = CacheEntry(addresses, System.currentTimeMillis())
                }

                return addresses
            } catch (e: UnknownHostException) {
                lastException = e
                if (retry < maxRetries) {
                    Thread.sleep((500 * (retry + 1)).toLong()) // 递增延迟
                }
            }
        }

        throw lastException ?: UnknownHostException("Failed to resolve host: $hostname")
    }

    private fun performLookup(hostname: String): List<InetAddress> {
        val task = FutureTask {
            try {
                InetAddress.getAllByName(hostname).toList().filter { it is Inet4Address }
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: Exception) {
                throw UnknownHostException("DNS lookup error: ${e.message}").apply {
                    initCause(e)
                }
            }
        }

        executor.execute(task)

        return try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true)
            throw UnknownHostException("DNS lookup timeout for $hostname after ${timeoutMillis}ms").apply {
                initCause(e)
            }
        } catch (e: ExecutionException) {
            task.cancel(true)
            throw (e.cause as? UnknownHostException)
                ?: UnknownHostException("DNS lookup failed for $hostname").apply {
                    initCause(e)
                }
        } catch (e: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            throw UnknownHostException("DNS lookup interrupted for $hostname").apply {
                initCause(e)
            }
        }
    }

    // 清理过期缓存（可选，定期调用）
    fun cleanExpiredCache() {
        cache.entries.removeIf { it.value.isExpired() }
    }
}