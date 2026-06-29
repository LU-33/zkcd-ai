package com.example.aicreationassistant

import android.app.Application
import com.example.aicreationassistant.di.ServiceLocator
import com.example.aicreationassistant.security.CryptoManager

class AiCreationApp : Application() {

    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val cryptoManager = CryptoManager(this)
        serviceLocator = ServiceLocator(this, cryptoManager)
    }

    companion object {
        lateinit var instance: AiCreationApp
            private set
    }
}
