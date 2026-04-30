package com.github.countryman.model

data class SimCardInfo(
    val slot: Int,
    val subId: Int,
    val carrierName: String,
    val countryCode: String,
    val currentConfig: Map<String, String> = emptyMap()
)
