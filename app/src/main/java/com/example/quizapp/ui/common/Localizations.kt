package com.example.quizapp.ui.common

/**
 * Utility object holding supported UI language codes, system locale caches, and localization helpers.
 */
object Localizations {
    val uiLanguages: List<String> = listOf(
        "en", "ro", "ru", "it", "cs", "hy", "pl",
        "fr", "es", "nl", "ka", "de", "az", "uk", "sr",
        "zh", "hi", "ar", "bn", "id", "ur", "ja", "pa", "mr", "te", "tr", "pt", "hu", "el", "sv",
        "bg", "fi", "vi", "ta", "ko", "fa", "tl", "jv", "th", "gu", "kn", "sq", "hr", "nb", "sk",
        "ca", "be", "lt", "bs", "gl", "sl", "ml", "su", "or", "my", "uz", "ps", "kk", "tt", "tg", "lv",
    )

    @Volatile
    private var cachedSystemLocale: String = "en"

    fun updateSystemLocale(lang: String) {
        if (lang.isNotBlank()) cachedSystemLocale = lang
    }

    fun systemLanguageCode(): String = cachedSystemLocale

    fun systemDisplayName(): String =
        languageDisplayName(cachedSystemLocale)

    fun displayName(code: String): String =
        languageDisplayName(code)

    fun sortedUiLanguages(): List<String> {
        val sysLang = systemLanguageCode()
        return uiLanguages.sortedWith(
            compareBy(
                { it != "en" && it != sysLang },
                { if (it == "en") 0 else if (it == sysLang) 1 else 2 },
                { languageDisplayName(it) },
            )
        )
    }
}
