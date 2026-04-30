package com.github.countryman.telephony.binder

import android.os.PersistableBundle

class CarrierConfigBinderClient {
    fun getConfigForSubId(subId: Int): PersistableBundle? {
        val loader = getCarrierConfigLoader() ?: return null
        val methods = loader.javaClass.methods.filter { it.name == "getConfigForSubId" }
        methods.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, String::class.java))
        }?.let { return it.invoke(loader, subId, "com.github.countryman") as? PersistableBundle }

        methods.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        }?.let { return it.invoke(loader, subId, "com.github.countryman") as? PersistableBundle }

        return null
    }

    fun overrideConfig(subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        val loader = getCarrierConfigLoader() ?: error("CarrierConfigLoader unavailable")
        val method = loader.javaClass.methods.firstOrNull {
            it.name == "overrideConfig" &&
                it.parameterTypes.size == 3 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                it.parameterTypes[2] == Boolean::class.javaPrimitiveType
        } ?: error("overrideConfig method not found")

        try {
            method.invoke(loader, subId, bundle, persistent)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException
            if (cause is SecurityException) throw cause
            throw e
        }
    }

    private fun getCarrierConfigLoader(): Any? {
        val binder = SystemServiceLocator.getShizukuWrappedBinder("carrier_config") ?: return null
        val stubClass = Class.forName("com.android.internal.telephony.ICarrierConfigLoader\$Stub")
        val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
        return asInterface.invoke(null, binder)
    }
}
