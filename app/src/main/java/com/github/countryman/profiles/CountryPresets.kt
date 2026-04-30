package com.github.countryman.profiles

import java.util.Locale

object CountryPresets {
    data class CountryInfo(
        val code: String,
        val name: String,
        val englishName: String,
        val flagEmoji: String
    )

    val countries: List<CountryInfo> = Locale.getISOCountries()
        .map { code ->
            val locale = Locale("", code)
            val chineseName = locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE).ifBlank {
                locale.getDisplayCountry(Locale.ENGLISH)
            }
            CountryInfo(
                code = code,
                name = chineseName,
                englishName = locale.getDisplayCountry(Locale.ENGLISH),
                flagEmoji = codeToFlagEmoji(code)
            )
        }
        .sortedWith(compareBy<CountryInfo> { it.name }.thenBy { it.code })

    private fun codeToFlagEmoji(code: String): String {
        if (code.length != 2 || !code.all { it.isLetter() }) return ""
        val base = 0x1F1E6
        val first = base + (code[0].uppercaseChar().code - 'A'.code)
        val second = base + (code[1].uppercaseChar().code - 'A'.code)
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
