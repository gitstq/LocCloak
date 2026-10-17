package com.locctl.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.locctl.app.adb.AdbManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class LocctlApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AdbManager.init(this)
    }

    companion object {
        @Volatile
        var instance: LocctlApp? = null
            private set

        fun get(): LocctlApp =
            instance ?: error("LocctlApp not initialized")
    }
}
