package com.example.quizapp.ui.common

import java.util.Locale

/**
 * Returns the localized display name for the given language code, capitalized.
 */
fun languageDisplayName(code: String): String {
    val locale = Locale.forLanguageTag(code)
    val name = locale.getDisplayName(locale)
    return name.replaceFirstChar { it.uppercase() }
}

/**
 * Sorts a collection of language codes, placing "en" first followed by alphabetical order.
 */
fun sortLanguages(langs: Collection<String>): List<String> =
    langs.sortedWith(compareBy({ it != "en" }, { it }))