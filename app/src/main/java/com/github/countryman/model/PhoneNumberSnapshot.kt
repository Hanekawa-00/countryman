package com.github.countryman.model

data class PhoneNumberSnapshot(
    val subId: Int,
    val simLabel: String,
    val displayNumber: String,
    val uiccNumber: String,
    val carrierNumber: String,
    val imsNumber: String,
    val lastKnownNumber: String,
    val displayMatchesIms: Boolean
)
