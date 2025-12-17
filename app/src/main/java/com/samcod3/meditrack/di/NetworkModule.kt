package com.samcod3.meditrack.di

import android.util.Log
import com.samcod3.meditrack.data.remote.api.CimaApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network Interceptor to fix CIMA API's incorrect behavior of returning 204 with content.
 * HTTP 204 should have no content, but CIMA sometimes returns data with 204.
 * This interceptor changes 204 to 200 when there's content.
 * 
 * MUST be added as a network interceptor (not application interceptor) to work before
 * OkHttp's CallServerInterceptor validates the protocol.
 */
class CimaResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        try {
            val response = chain.proceed(request)
            
            // If we get a 204 but there's content, treat it as 200
            if (response.code == 204) {
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                Log.d("CimaInterceptor", "Got 204 with Content-Length: $contentLength")
                if (contentLength > 0) {
                    return response.newBuilder()
                        .code(200)
                        .message("OK")
                        .build()
                }
            }
            
            return response
        } catch (e: java.net.ProtocolException) {
            // Handle the "HTTP 204 had non-zero Content-Length" error
            if (e.message?.contains("204") == true && e.message?.contains("Content-Length") == true) {
                Log.w("CimaInterceptor", "Caught 204 protocol error, treating as 'not found'")
                // Return a synthetic 404 response since the API returned an invalid 204
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found (CIMA returned invalid 204)")
                    .body("{}".toResponseBody())
                    .build()
            }
            throw e
        }
    }
}

val networkModule = module {
    
    single {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    single {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }
    
    single {
        OkHttpClient.Builder()
            .addInterceptor(CimaResponseInterceptor())  // Application interceptor - catches exceptions
            .addInterceptor(get<HttpLoggingInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    single {
        Retrofit.Builder()
            .baseUrl("https://cima.aemps.es/cima/rest/")
            .client(get())
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
    }
    
    single<CimaApiService> {
        get<Retrofit>().create(CimaApiService::class.java)
    }
}
