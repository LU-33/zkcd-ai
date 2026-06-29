package com.example.aicreationassistant.di

import android.content.Context
import androidx.room.Room
import com.example.aicreationassistant.data.local.AppDatabase
import com.example.aicreationassistant.data.remote.DeepSeekApi
import com.example.aicreationassistant.data.repository.ContentRepository
import com.example.aicreationassistant.data.repository.DeepSeekRepository
import com.example.aicreationassistant.security.CryptoManager
import com.example.aicreationassistant.util.Constants
import com.example.aicreationassistant.util.NetworkMonitor
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ServiceLocator(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    // Network monitoring
    val networkMonitor = NetworkMonitor(context)

    // API key
    private val apiKey: String = Constants.DEEPSEEK_API_KEY

    // OkHttp
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    // Gson
    private val gson by lazy { GsonBuilder().create() }

    // Retrofit
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.DEEPSEEK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // API
    val deepSeekApi: DeepSeekApi by lazy {
        retrofit.create(DeepSeekApi::class.java)
    }

    // Repositories
    val deepSeekRepository: DeepSeekRepository by lazy {
        DeepSeekRepository(deepSeekApi, apiKey)
    }

    // Database
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_assistant_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val contentRepository: ContentRepository by lazy {
        ContentRepository(database, cryptoManager)
    }
}
