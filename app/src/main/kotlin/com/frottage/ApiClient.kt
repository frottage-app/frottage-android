package com.frottage

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val API_BASE_URL = "https://frottage.fly.dev" // For analytics and ratings

    // Shared OkHttpClient instance for Retrofit and other app components
    internal val okHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    // Shared Json instance for kotlinx.serialization
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true // Good for slightly malformed but recoverable JSON
        }

    private val retrofit: Retrofit =
        Retrofit
            .Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    val frottageApiService: FrottageApiService by lazy {
        retrofit.create(FrottageApiService::class.java)
    }
}
