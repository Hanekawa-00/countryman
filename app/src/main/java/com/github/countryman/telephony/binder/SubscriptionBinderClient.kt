package com.github.countryman.telephony.binder

import android.telephony.SubscriptionInfo

class SubscriptionBinderClient {
    fun getActiveSubscriptions(): List<SubscriptionInfo> {
        val subService = getSubscriptionService() ?: return emptyList()
        val methods = subService.javaClass.methods.filter { it.name == "getActiveSubscriptionInfoList" }

        val result = methods.firstNotNullOfOrNull { method ->
            runCatching {
                when (method.parameterTypes.size) {
                    3 -> method.invoke(subService, null, null, true)
                    2 -> method.invoke(subService, null, null)
                    1 -> method.invoke(subService, null)
                    0 -> method.invoke(subService)
                    else -> null
                }
            }.getOrNull()
        }

        @Suppress("UNCHECKED_CAST")
        return result as? List<SubscriptionInfo> ?: emptyList()
    }

    private fun getSubscriptionService(): Any? {
        val binder = SystemServiceLocator.getShizukuWrappedBinder("isub") ?: return null
        val stubClass = Class.forName("com.android.internal.telephony.ISub\$Stub")
        val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
        return asInterface.invoke(null, binder)
    }
}
