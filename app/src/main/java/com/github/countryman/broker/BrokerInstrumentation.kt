package com.github.countryman.broker

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.os.SystemClock
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class BrokerInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        if (arguments == null) {
            publishResult(null, false, "Missing instrumentation arguments", false)
            finish(0, Bundle())
            return
        }

        val operationId = arguments.getString(ARG_OPERATION_ID)
        if (operationId.isNullOrEmpty()) {
            publishResult(null, false, "Missing operation id", false)
            finish(0, Bundle())
            return
        }

        val clear = arguments.getBoolean(ARG_CLEAR, false)
        try {
            delegateShellPermissionIdentity()

            val subId = arguments.getInt(ARG_SUB_ID, -1)
            if (subId < 0) {
                throw IllegalArgumentException("Invalid subscription id")
            }

            if (clear) {
                overrideConfig(subId, null)
            } else {
                val bundle = PersistableBundle().apply {
                    arguments.getString(ARG_COUNTRY_CODE)?.let {
                        putString(android.telephony.CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, it.lowercase())
                    }
                    arguments.getString(ARG_CARRIER_NAME)?.let {
                        putBoolean(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                        putString(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_STRING, it)
                    }
                }
                overrideConfig(subId, bundle)
            }

            publishResult(operationId, true, null, clear)
        } catch (t: Throwable) {
            publishResult(operationId, false, t.message ?: t.javaClass.name, clear)
        } finally {
            stopDelegatedShellPermissionIdentity()
            finish(0, Bundle())
        }
    }

    private fun publishResult(operationId: String?, success: Boolean, error: String?, clear: Boolean) {
        runCatching {
            val intent = Intent(ACTION_BROKER_RESULT).apply {
                setPackage(MAIN_APP_PACKAGE)
                putExtra(EXTRA_OPERATION_ID, operationId)
                putExtra(EXTRA_SUCCESS, success)
                putExtra(EXTRA_ERROR, error)
                putExtra(EXTRA_CLEAR, clear)
            }
            targetContext.sendBroadcast(intent)
            SystemClock.sleep(100)
        }
    }

    private fun overrideConfig(subId: Int, bundle: PersistableBundle?) {
        val loader = getCarrierConfigLoader() ?: error("CarrierConfigLoader unavailable")
        try {
            invokeOverrideConfig(loader, subId, bundle, true)
        } catch (e: SecurityException) {
            if (e.message?.contains("persistent=true only can be invoked by system app", ignoreCase = true) == true) {
                invokeOverrideConfig(loader, subId, bundle, false)
            } else {
                throw e
            }
        }
    }

    private fun delegateShellPermissionIdentity() {
        val activityManagerBinder = ShizukuBinderWrapper(
            SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
        )
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        val activityManager = asInterface.invoke(null, activityManagerBinder)
        val method = activityManager.javaClass.methods.firstOrNull {
            it.name == "startDelegateShellPermissionIdentity" && it.parameterTypes.size == 2
        } ?: error("Unable to find startDelegateShellPermissionIdentity on IActivityManager")
        method.invoke(activityManager, android.system.Os.getuid(), null)
    }

    private fun stopDelegatedShellPermissionIdentity() {
        runCatching {
            val activityManagerBinder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
            )
            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val activityManager = asInterface.invoke(null, activityManagerBinder)
            val method = activityManager.javaClass.methods.firstOrNull {
                it.name == "stopDelegateShellPermissionIdentity" && it.parameterTypes.isEmpty()
            } ?: return
            method.invoke(activityManager)
        }
    }

    private fun getCarrierConfigLoader(): Any? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "carrier_config") as? IBinder ?: return null
        val stubClass = Class.forName("com.android.internal.telephony.ICarrierConfigLoader\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder)
    }

    private fun invokeOverrideConfig(loader: Any, subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
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

    companion object {
        const val ACTION_BROKER_RESULT = "com.github.countryman.BROKER_RESULT"
        const val EXTRA_OPERATION_ID = "operation_id"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR = "error"
        const val EXTRA_CLEAR = "clear"
        const val MAIN_APP_PACKAGE = "com.github.countryman"

        const val ARG_OPERATION_ID = "broker_operation_id"
        const val ARG_SUB_ID = "broker_sub_id"
        const val ARG_COUNTRY_CODE = "broker_country_code"
        const val ARG_CARRIER_NAME = "broker_carrier_name"
        const val ARG_CLEAR = "broker_clear"
    }
}
