package com.github.countryman.telephony.binder

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import rikka.shizuku.SystemServiceHelper

class ActivityManagerBinderClient {
    fun startBrokerInstrumentation(arguments: Bundle) {
        val activityManager = getActivityManager()
        val uiAutomationConnection = Class.forName("android.app.UiAutomationConnection")
            .getDeclaredConstructor()
            .newInstance()
        val startInstrumentation = activityManager.javaClass.methods.firstOrNull {
            it.name == "startInstrumentation" && it.parameterTypes.size == 8
        } ?: error("Unable to find startInstrumentation on IActivityManager")

        startInstrumentation.invoke(
            activityManager,
            ComponentName(BROKER_PACKAGE, BROKER_CLASS),
            null,
            0,
            arguments,
            null,
            uiAutomationConnection,
            0,
            null
        )
    }

    private fun getActivityManager(): Any {
        val activityManagerBinder = rikka.shizuku.ShizukuBinderWrapper(
            SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
        )
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, activityManagerBinder)
    }

    companion object {
        private const val BROKER_PACKAGE = "com.github.countryman"
        private const val BROKER_CLASS = "com.github.countryman.broker.BrokerInstrumentation"
    }
}
