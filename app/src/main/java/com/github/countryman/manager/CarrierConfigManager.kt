package com.github.countryman.manager

import android.content.Context
import com.github.countryman.core.model.OverrideProfile
import com.github.countryman.model.PhoneNumberSnapshot
import com.github.countryman.model.SimCardInfo
import com.github.countryman.telephony.repository.OverrideDispatch
import com.github.countryman.telephony.repository.TelephonyRepository

object CarrierConfigManager {
    private val repository = TelephonyRepository()

    fun getSimCards(context: Context): List<SimCardInfo> = repository.getSimCards(context)

    fun getPhoneNumberDiagnostics(context: Context): String = repository.buildPhoneNumberDiagnostics(context)

    fun getPhoneNumberSnapshot(context: Context, subId: Int): PhoneNumberSnapshot? {
        return repository.getPhoneNumberSnapshot(context, subId)
    }

    fun runPhoneNumberWriteExperiments(context: Context, subId: Int, value: String): String {
        return repository.runPhoneNumberWriteExperiments(context, subId, value)
    }

    fun setCarrierConfig(
        context: Context,
        subId: Int,
        countryCode: String?,
        carrierName: String? = null
    ): OverrideDispatch {
        return repository.applyOverride(
            context,
            OverrideProfile(
                subId = subId,
                countryCode = countryCode,
                carrierName = carrierName
            )
        )
    }

    fun resetCarrierConfig(context: Context, subId: Int): OverrideDispatch {
        return repository.resetOverride(context, subId)
    }

    fun restoreDisplayNumberDefault(context: Context, subId: Int) {
        repository.restoreDisplayNumberDefault(context, subId)
    }
}
