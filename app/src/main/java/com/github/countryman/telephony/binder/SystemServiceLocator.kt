package com.github.countryman.telephony.binder

import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper

object SystemServiceLocator {
    fun getBinder(serviceName: String): IBinder? {
        return runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            getService.invoke(null, serviceName) as? IBinder
        }.getOrNull()
    }

    fun getShizukuWrappedBinder(serviceName: String): IBinder? {
        val binder = getBinder(serviceName) ?: return null
        return ShizukuBinderWrapper(binder)
    }
}
