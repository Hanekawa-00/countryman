package com.github.countryman.telephony.repository

import android.content.Context
import android.os.IBinder
import android.os.PersistableBundle
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import android.util.Log
import com.github.countryman.core.model.OverrideProfile
import com.github.countryman.model.PhoneNumberSnapshot
import com.github.countryman.model.SimCardInfo
import com.github.countryman.telephony.binder.ActivityManagerBinderClient
import com.github.countryman.telephony.binder.CarrierConfigBinderClient
import com.github.countryman.telephony.binder.SubscriptionBinderClient

enum class OverrideDispatch {
    COMPLETED,
    PENDING_BROKER,
}

class TelephonyRepository(
    private val subscriptionClient: SubscriptionBinderClient = SubscriptionBinderClient(),
    private val carrierConfigClient: CarrierConfigBinderClient = CarrierConfigBinderClient(),
    private val activityManagerClient: ActivityManagerBinderClient = ActivityManagerBinderClient()
) {
    fun getSimCards(context: Context): List<SimCardInfo> {
        return subscriptionClient.getActiveSubscriptions()
            .sortedBy { it.simSlotIndex }
            .map { info ->
                SimCardInfo(
                    slot = info.simSlotIndex + 1,
                    subId = info.subscriptionId,
                    carrierName = getCarrierNameBySubId(context, info.subscriptionId),
                    countryCode = getCountryCode(context, info),
                    currentConfig = getCurrentConfig(info.subscriptionId)
                )
            }
    }

    fun applyOverride(context: Context, profile: OverrideProfile): OverrideDispatch {
        val bundle = PersistableBundle()
        if (!profile.countryCode.isNullOrEmpty() && profile.countryCode.length == 2) {
            bundle.putString(
                android.telephony.CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                profile.countryCode.lowercase()
            )
        }
        if (!profile.carrierName.isNullOrEmpty()) {
            bundle.putBoolean(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_STRING, profile.carrierName)
        }

        return overrideCarrierConfig(context, profile.subId, bundle, profile.countryCode, profile.carrierName)
    }

    fun resetOverride(context: Context, subId: Int): OverrideDispatch {
        return overrideCarrierConfig(context, subId, null, null, null)
    }

    fun restoreDisplayNumberDefault(context: Context, subId: Int) {
        val snapshot = getPhoneNumberSnapshot(context, subId)
        val restoreValue = listOf(
            snapshot?.imsNumber,
            snapshot?.lastKnownNumber,
            snapshot?.carrierNumber,
            snapshot?.uiccNumber
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        val iface = getHiddenInterface("isub", "com.android.internal.telephony.ISub\$Stub")
            ?: error("ISub unavailable")
        val method = iface.javaClass.methods.firstOrNull {
            it.name == "setDisplayNumber" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == Int::class.javaPrimitiveType
        } ?: error("setDisplayNumber unavailable")

        method.invoke(iface, restoreValue, subId)
    }

    fun buildPhoneNumberDiagnostics(context: Context): String {
        val lines = mutableListOf<String>()
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        val subs = subscriptionClient.getActiveSubscriptions().sortedBy { it.simSlotIndex }

        lines += "== Subscription Values =="
        if (subs.isEmpty()) {
            lines += "No active subscriptions"
        } else {
            subs.forEach { info ->
                lines += "SIM${info.simSlotIndex + 1} subId=${info.subscriptionId}"
                lines += "  SubscriptionInfo.number = ${info.number.orEmpty().ifBlank { "<empty>" }}"
                lines += "  SubscriptionInfo.displayName = ${info.displayName ?: "<null>"}"
                lines += "  ISub.getPhoneNumber = ${readIsubPhoneNumber(info.subscriptionId)}"
                lines += "  ISub.getLastKnownPhoneNumber = ${readIsubLastKnownPhoneNumber(info.subscriptionId)}"
                if (telephonyManager != null) {
                    val subTm = runCatching { telephonyManager.createForSubscriptionId(info.subscriptionId) }.getOrNull()
                    lines += "  TelephonyManager.getLine1Number = ${readCallResult { subTm?.line1Number }}"
                    lines += "  TelephonyManager.getMsisdn(reflect) = ${readCallResult { reflectNoArgString(subTm, "getMsisdn") }}"
                    lines += "  TelephonyManager.getVoiceMailNumber = ${readCallResult { subTm?.voiceMailNumber }}"
                }
            }
        }

        lines += ""
        lines += "== ISub Number-like Methods =="
        lines += inspectInterfaceMethods("isub", "com.android.internal.telephony.ISub\$Stub")

        lines += ""
        lines += "== IPhoneSubInfo Number-like Methods =="
        lines += inspectInterfaceMethods("iphonesubinfo", "com.android.internal.telephony.IPhoneSubInfo\$Stub")

        lines += ""
        lines += "== Public TelephonyManager Number-like Methods =="
        lines += TelephonyManager::class.java.methods
            .filter { it.name.contains("number", ignoreCase = true) || it.name.contains("msisdn", ignoreCase = true) || it.name.contains("line1", ignoreCase = true) }
            .sortedBy { it.name }
            .joinToString("\n") { "  ${it.name}${it.parameterTypes.joinToString(prefix = "(", postfix = ")") { p -> p.simpleName }}" }

        return lines.joinToString("\n")
    }

    fun getPhoneNumberSnapshot(context: Context, subId: Int): PhoneNumberSnapshot? {
        val info = subscriptionClient.getActiveSubscriptions().firstOrNull { it.subscriptionId == subId } ?: return null
        val displayNumber = info.number.orEmpty().normalizedFieldValue()
        val valuesBySource = readIsubPhoneNumberValues(subId)
        val imsNumber = valuesBySource[PHONE_NUMBER_SOURCE_IMS].orEmpty().normalizedFieldValue()
        return PhoneNumberSnapshot(
            subId = subId,
            simLabel = "SIM${info.simSlotIndex + 1}",
            displayNumber = displayNumber,
            uiccNumber = valuesBySource[PHONE_NUMBER_SOURCE_UICC].orEmpty().normalizedFieldValue(),
            carrierNumber = valuesBySource[PHONE_NUMBER_SOURCE_CARRIER].orEmpty().normalizedFieldValue(),
            imsNumber = imsNumber,
            lastKnownNumber = readIsubLastKnownPhoneNumberRaw(subId).normalizedFieldValue(),
            displayMatchesIms = displayNumber.isNotBlank() && imsNumber.isNotBlank() && displayNumber == imsNumber
        )
    }

    fun runPhoneNumberWriteExperiments(context: Context, subId: Int, value: String): String {
        val lines = mutableListOf<String>()
        lines += "== Write Experiments =="
        lines += "subId=$subId"
        lines += "value=$value"
        lines += ""

        lines += "ISub.setDisplayNumber(String, int)"
        lines += "  ${attemptIsubSetDisplayNumber(subId, value)}"
        lines += ""

        lines += "TelephonyManager.setLine1NumberForDisplay(String, String)"
        lines += "  ${attemptTelephonySetLine1NumberForDisplay(context, subId, value)}"
        lines += ""

        lines += "ISub.setPhoneNumber(...)"
        lines += "  ${attemptIsubSetPhoneNumber(subId, value)}"
        lines += ""

        lines += "== Readback =="
        lines += buildPhoneNumberDiagnostics(context)
        return lines.joinToString("\n")
    }

    private fun getCurrentConfig(subId: Int): Map<String, String> {
        return runCatching {
            val config = carrierConfigClient.getConfigForSubId(subId) ?: return emptyMap()
            buildMap {
                config.getString(android.telephony.CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)
                    ?.let { put("国家码", it) }
                if (config.getBoolean(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                    config.getString(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_STRING)
                        ?.let { put("运营商名称", it) }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun getCarrierNameBySubId(context: Context, subId: Int): String {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return ""
        return runCatching {
            telephonyManager.createForSubscriptionId(subId).networkOperatorName
        }.getOrElse {
            telephonyManager.networkOperatorName
        }
    }

    private fun getCountryCode(context: Context, info: SubscriptionInfo): String {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return ""
        val subTm = runCatching { telephonyManager.createForSubscriptionId(info.subscriptionId) }.getOrElse { telephonyManager }

        val mccCandidates = listOfNotNull(
            runCatching { info.mccString }.getOrNull()?.takeIf { it.isNotBlank() },
            runCatching { info.mcc.takeIf { it > 0 }?.toString() }.getOrNull(),
            runCatching { subTm.simOperator }.getOrNull()?.takeIf { it.length >= 3 }?.take(3),
            runCatching { subTm.networkOperator }.getOrNull()?.takeIf { it.length >= 3 }?.take(3)
        )

        mccCandidates.forEach { mcc ->
            resolveCountryCodeFromMcc(mcc)?.let { return it }
        }

        return listOf(
            runCatching { subTm.simCountryIso }.getOrNull(),
            runCatching { subTm.networkCountryIso }.getOrNull()
        ).firstOrNull { !it.isNullOrBlank() }?.uppercase().orEmpty()
    }

    private fun resolveCountryCodeFromMcc(mcc: String): String? {
        val mccInt = mcc.toIntOrNull() ?: return null

        runCatching {
            val mccTable = Class.forName("com.android.internal.telephony.MccTable")
            val method = mccTable.methods.firstOrNull {
                it.name == "countryCodeForMcc" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            } ?: return@runCatching null

            (method.invoke(null, mccInt) as? String)
                ?.takeIf { it.isNotBlank() }
                ?.uppercase()
        }.getOrNull()?.let { return it }

        return when (mccInt) {
            in 460..461 -> "CN"
            in 454..455 -> if (mccInt == 455) "MO" else "HK"
            466 -> "TW"
            in 440..441 -> "JP"
            450 -> "KR"
            in 310..316 -> "US"
            in 302..302 -> "CA"
            in 234..235 -> "GB"
            262 -> "DE"
            208 -> "FR"
            222 -> "IT"
            214 -> "ES"
            250 -> "RU"
            724 -> "BR"
            525 -> "SG"
            502 -> "MY"
            520 -> "TH"
            452 -> "VN"
            510 -> "ID"
            515 -> "PH"
            404, 405, 406 -> "IN"
            505 -> "AU"
            else -> null
        }
    }

    private fun overrideCarrierConfig(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
        countryCode: String?,
        carrierName: String?
    ): OverrideDispatch {
        try {
            carrierConfigClient.overrideConfig(subId, bundle, true)
            return OverrideDispatch.COMPLETED
        } catch (e: SecurityException) {
            if (shouldUseBroker(e)) {
                Log.w(TAG, "Direct override blocked for shell, falling back to broker", e)
                overrideCarrierConfigUsingBroker(subId, countryCode, carrierName, bundle == null)
                return OverrideDispatch.PENDING_BROKER
            } else {
                throw e
            }
        }
    }

    private fun shouldUseBroker(error: SecurityException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("cannot be invoked by shell", ignoreCase = true) ||
            message.contains("cannot be called by shell", ignoreCase = true)
    }

    private fun overrideCarrierConfigUsingBroker(
        subId: Int,
        countryCode: String?,
        carrierName: String?,
        clear: Boolean
    ) {
        val arguments = android.os.Bundle().apply {
            putString(BROKER_ARG_OPERATION_ID, java.util.UUID.randomUUID().toString())
            putInt(BROKER_ARG_SUB_ID, subId)
            putBoolean(BROKER_ARG_CLEAR, clear)
            countryCode?.let { putString(BROKER_ARG_COUNTRY_CODE, it) }
            carrierName?.let { putString(BROKER_ARG_CARRIER_NAME, it) }
        }
        activityManagerClient.startBrokerInstrumentation(arguments)
    }

    private fun inspectInterfaceMethods(serviceName: String, stubClassName: String): String {
        val iface = getHiddenInterface(serviceName, stubClassName) ?: return "  <unavailable>"
        val methods = iface.javaClass.methods
            .filter {
                it.name.contains("number", ignoreCase = true) ||
                    it.name.contains("msisdn", ignoreCase = true) ||
                    it.name.contains("line1", ignoreCase = true)
            }
            .sortedBy { it.name }

        if (methods.isEmpty()) {
            return "  <no candidate methods>"
        }

        return methods.joinToString("\n") { method ->
            val params = method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
            "  ${method.name}$params -> ${method.returnType.simpleName}"
        }
    }

    private fun getHiddenInterface(serviceName: String, stubClassName: String): Any? {
        val binder = com.github.countryman.telephony.binder.SystemServiceLocator.getShizukuWrappedBinder(serviceName) ?: return null
        val stubClass = Class.forName(stubClassName)
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder)
    }

    private fun attemptIsubSetDisplayNumber(subId: Int, value: String): String {
        val iface = getHiddenInterface("isub", "com.android.internal.telephony.ISub\$Stub")
            ?: return "<unavailable>"
        val method = iface.javaClass.methods.firstOrNull {
            it.name == "setDisplayNumber" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == Int::class.javaPrimitiveType
        } ?: return "<method not found>"

        return try {
            val result = method.invoke(iface, value, subId)
            "ok result=$result"
        } catch (t: Throwable) {
            formatThrowable(t)
        }
    }

    private fun attemptTelephonySetLine1NumberForDisplay(context: Context, subId: Int, value: String): String {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            ?: return "<telephony unavailable>"
        val subTm = runCatching { telephonyManager.createForSubscriptionId(subId) }.getOrNull()
            ?: return "<sub telephony unavailable>"
        val method = subTm.javaClass.methods.firstOrNull {
            it.name == "setLine1NumberForDisplay" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == String::class.java
        } ?: return "<method not found>"

        return try {
            val result = method.invoke(subTm, "", value)
            "ok result=$result"
        } catch (t: Throwable) {
            formatThrowable(t)
        }
    }

    private fun attemptIsubSetPhoneNumber(subId: Int, value: String): String {
        val iface = getHiddenInterface("isub", "com.android.internal.telephony.ISub\$Stub")
            ?: return "<unavailable>"
        val method = iface.javaClass.methods.firstOrNull {
            it.name == "setPhoneNumber"
        } ?: return "<method not found>"

        val candidateArgs = buildSetPhoneNumberCandidates(method.parameterTypes, subId, value)
        if (candidateArgs.isEmpty()) {
            return "<unsupported signature ${formatParameterTypes(method.parameterTypes)}>"
        }

        val errors = mutableListOf<String>()
        candidateArgs.forEachIndexed { index, args ->
            try {
                method.invoke(iface, *args)
                return "ok variant=$index signature=${formatParameterTypes(method.parameterTypes)} args=${args.joinToString(prefix = "[", postfix = "]")}"
            } catch (t: Throwable) {
                errors += "variant=$index ${formatThrowable(t)}"
            }
        }
        return "<failed signature=${formatParameterTypes(method.parameterTypes)} ${errors.joinToString(" ; ")}>"
    }

    private fun readIsubPhoneNumber(subId: Int): String {
        val valuesBySource = readIsubPhoneNumberValues(subId)
        return listOf(
            PHONE_NUMBER_SOURCE_UICC,
            PHONE_NUMBER_SOURCE_CARRIER,
            PHONE_NUMBER_SOURCE_IMS
        ).joinToString(" | ") { source ->
            "${phoneNumberSourceLabel(source)}=${valuesBySource[source].orEmpty().normalizedFieldValue()}"
        }
    }

    private fun readIsubPhoneNumberValues(subId: Int): Map<Int, String> {
        val iface = getHiddenInterface("isub", "com.android.internal.telephony.ISub\$Stub") ?: return mapOf(
            PHONE_NUMBER_SOURCE_UICC to "<unavailable>",
            PHONE_NUMBER_SOURCE_CARRIER to "<unavailable>",
            PHONE_NUMBER_SOURCE_IMS to "<unavailable>"
        )
        val method = iface.javaClass.methods.firstOrNull {
            it.name == "getPhoneNumber" && it.parameterTypes.size == 4
        } ?: return mapOf(
            PHONE_NUMBER_SOURCE_UICC to "<method not found>",
            PHONE_NUMBER_SOURCE_CARRIER to "<method not found>",
            PHONE_NUMBER_SOURCE_IMS to "<method not found>"
        )

        return listOf(
            PHONE_NUMBER_SOURCE_UICC,
            PHONE_NUMBER_SOURCE_CARRIER,
            PHONE_NUMBER_SOURCE_IMS
        ).map { source ->
            source to arrayOf<Any?>(subId, source, PACKAGE_NAME, null)
        }.associate { (source, args) ->
            source to readCallResult { method.invoke(iface, *args) as? String }
        }
    }

    private fun readIsubLastKnownPhoneNumber(subId: Int): String {
        return readIsubLastKnownPhoneNumberRaw(subId).normalizedFieldValue()
    }

    private fun readIsubLastKnownPhoneNumberRaw(subId: Int): String {
        val iface = getHiddenInterface("isub", "com.android.internal.telephony.ISub\$Stub") ?: return "<unavailable>"
        val method = iface.javaClass.methods.firstOrNull {
            it.name == "getLastKnownPhoneNumberFromFirstAvailableSource" && it.parameterTypes.size == 3
        } ?: return "<method not found>"

        return readCallResult { method.invoke(iface, subId, PACKAGE_NAME, null) as? String }
    }

    private fun buildSetPhoneNumberCandidates(
        parameterTypes: Array<Class<*>>,
        subId: Int,
        value: String
    ): List<Array<Any?>> {
        return when {
            parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                    String::class.java
                )
            ) -> listOf(
                arrayOf<Any?>(subId, PHONE_NUMBER_SOURCE_CARRIER, value, PACKAGE_NAME, null),
                arrayOf<Any?>(subId, PHONE_NUMBER_SOURCE_IMS, value, PACKAGE_NAME, null),
                arrayOf<Any?>(subId, PHONE_NUMBER_SOURCE_UICC, value, PACKAGE_NAME, null),
                arrayOf<Any?>(PHONE_NUMBER_SOURCE_CARRIER, subId, value, PACKAGE_NAME, null),
                arrayOf<Any?>(PHONE_NUMBER_SOURCE_IMS, subId, value, PACKAGE_NAME, null),
                arrayOf<Any?>(PHONE_NUMBER_SOURCE_UICC, subId, value, PACKAGE_NAME, null)
            )

            parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java
                )
            ) -> listOf(
                arrayOf<Any?>(subId, PHONE_NUMBER_SOURCE_CARRIER, value, PACKAGE_NAME),
                arrayOf<Any?>(subId, PHONE_NUMBER_SOURCE_IMS, value, PACKAGE_NAME),
                arrayOf<Any?>(subId, PHONE_NUMBER_SOURCE_UICC, value, PACKAGE_NAME),
                arrayOf<Any?>(PHONE_NUMBER_SOURCE_CARRIER, subId, value, PACKAGE_NAME),
                arrayOf<Any?>(PHONE_NUMBER_SOURCE_IMS, subId, value, PACKAGE_NAME),
                arrayOf<Any?>(PHONE_NUMBER_SOURCE_UICC, subId, value, PACKAGE_NAME)
            )

            else -> emptyList()
        }
    }

    private fun formatParameterTypes(parameterTypes: Array<Class<*>>): String {
        return parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
    }

    private fun String.normalizedFieldValue(): String {
        return if (isBlank() || this == "<empty>") "" else this
    }

    private fun phoneNumberSourceLabel(source: Int): String {
        return when (source) {
            PHONE_NUMBER_SOURCE_UICC -> "uicc"
            PHONE_NUMBER_SOURCE_CARRIER -> "carrier"
            PHONE_NUMBER_SOURCE_IMS -> "ims"
            else -> "source=$source"
        }
    }

    private fun reflectNoArgString(instance: Any?, methodName: String): String? {
        if (instance == null) return null
        val method = instance.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() } ?: return null
        return method.invoke(instance) as? String
    }

    private fun readCallResult(block: () -> String?): String {
        return try {
            block().orEmpty().ifBlank { "<empty>" }
        } catch (t: Throwable) {
            formatThrowable(t)
        }
    }

    private fun formatThrowable(t: Throwable): String {
        val real = if (t is java.lang.reflect.InvocationTargetException) t.targetException ?: t else t
        return "<${real.javaClass.simpleName}: ${real.message.orEmpty().ifBlank { "no message" }}>"
    }

    companion object {
        private const val TAG = "TelephonyRepository"
        private const val PACKAGE_NAME = "com.github.countryman"
        private const val BROKER_ARG_OPERATION_ID = "broker_operation_id"
        private const val BROKER_ARG_SUB_ID = "broker_sub_id"
        private const val BROKER_ARG_COUNTRY_CODE = "broker_country_code"
        private const val BROKER_ARG_CARRIER_NAME = "broker_carrier_name"
        private const val BROKER_ARG_CLEAR = "broker_clear"
        private const val PHONE_NUMBER_SOURCE_UICC = 1
        private const val PHONE_NUMBER_SOURCE_CARRIER = 2
        private const val PHONE_NUMBER_SOURCE_IMS = 3
    }
}
