package com.smdozk.tvremote

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class TvRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // dadb uses BouncyCastle for RSA ADB key generation
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
