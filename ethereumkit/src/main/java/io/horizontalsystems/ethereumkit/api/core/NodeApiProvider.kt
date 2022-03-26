package io.horizontalsystems.ethereumkit.api.core

import com.google.gson.Gson
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.reactivex.Single
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.util.logging.Logger

class NodeApiProvider(
        private val urls: List<URL>,
        override val blockTime: Long,
        private val gson: Gson,
        auth: String? = null
) : IRpcApiProvider {

    private val logger = Logger.getLogger(this.javaClass.simpleName)
    private val service: InfuraService

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }
                .setLevel(HttpLoggingInterceptor.Level.BASIC)

        val headersInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            auth?.let {
                requestBuilder.header("Authorization", Credentials.basic("", auth))
            }
            chain.proceed(requestBuilder.build())
        }

        val httpClient = OkHttpClient.Builder()
//            .proxy(Proxy( Proxy.Type.HTTP , InetSocketAddress("47.89.208.160", 58972) ))
                .addInterceptor(loggingInterceptor)
                .addInterceptor(headersInterceptor)

        val retrofit = Retrofit.Builder()
                .baseUrl("${urls.first()}/")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient.build())
                .build()

        service = retrofit.create(InfuraService::class.java)
    }

    override val source: String = urls.first().host

    override fun <T> single(rpc: JsonRpc<T>): Single<T> =
            Single.create { emitter ->
                var error: Throwable = ApiProviderError.ApiUrlNotFound

                urls.forEach { url ->
                    try {
                        val response = service.single(url.toURI(), gson.toJson(rpc))
                                .map { response -> rpc.parseResponse(response, gson) }
                                .blockingGet()

                        emitter.onSuccess(response)
                        return@create
                    } catch (throwable: Throwable) {
                        error = throwable
                    }
                }
                emitter.onError(error)
            }

    private interface InfuraService {
        @POST
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun single(@Url uri: URI, @Body jsonRpc: String): Single<RpcResponse>
    }

    sealed class ApiProviderError : Throwable() {
        object ApiUrlNotFound : ApiProviderError()
    }

}
