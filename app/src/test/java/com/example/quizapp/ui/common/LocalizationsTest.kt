package com.example.quizapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationsTest {

    @Test
    fun `uiLanguages contains 61 languages`() {
        assertEquals(61, Localizations.uiLanguages.size)
        assertTrue(Localizations.uiLanguages.contains("en"))
        assertTrue(Localizations.uiLanguages.contains("it"))
        assertTrue(Localizations.uiLanguages.contains("ro"))
        assertTrue(Localizations.uiLanguages.contains("zh"))
        assertTrue(Localizations.uiLanguages.contains("hi"))
    }

    @Test
    fun `sortedUiLanguages puts en first`() {
        val sorted = Localizations.sortedUiLanguages()
        assertEquals(61, sorted.size)
        assertEquals("en", sorted.first())
    }
}
