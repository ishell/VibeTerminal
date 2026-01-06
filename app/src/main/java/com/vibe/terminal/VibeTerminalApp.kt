package com.vibe.terminal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class VibeTerminalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeSecurityProviders()
    }

    private fun initializeSecurityProviders() {
        // 移除 Android 自带的 Bouncy Castle (版本较旧)
        Security.removeProvider("BC")
        // 添加最新版本的 Bouncy Castle Provider
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
