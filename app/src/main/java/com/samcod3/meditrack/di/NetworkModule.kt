package com.samcod3.meditrack.di

import com.samcod3.meditrack.data.remote.api.CimaApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Interceptor to fix CIMA API's incorrect behavior of returning 204 with content.
 * HTTP 204 should have no content, but CIMA sometimes returns data with 204.
 * This interceptor changes 204 to 200 when there's content.
 */
class CimaResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        
        // If we get a 204 but there's content, treat it as 200
        if (response.code == 204) {
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
            if (contentLength > 0) {
                return response.newBuilder()
                    .code(200)
                    .message("OK")
                    .build()
            }
        }
        
        return response
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
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    single {
        OkHttpClient.Builder()
            .addInterceptor(CimaResponseInterceptor())  // Fix 204 with content
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
