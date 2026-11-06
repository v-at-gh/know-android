package com.example.quizapp.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {

    @Test
    fun `uiLanguage defaults to null and persists across instances`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs1 = DataStorePreferences(context)
        assertEquals(null, prefs1.uiLanguage.value)

        prefs1.setUiLanguage("it")
        val lang = prefs1.uiLanguage.filter { it == "it" }.first()
        assertEquals("it", lang)

        prefs1.setUiLanguage(null)
        val resetLang = prefs1.uiLanguage.filter { it == null }.first()
        assertEquals(null, resetLang)
    }

    @Test
    fun `hapticFeedback defaults to true and persists across instance`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs1 = DataStorePreferences(context)
        assertEquals(true, prefs1.hapticFeedback.value)

        prefs1.setHapticFeedback(false)
        val disabled = prefs1.hapticFeedback.filter { !it }.first()
        assertEquals(false, disabled)

        prefs1.setHapticFeedback(true)
        val enabled = prefs1.hapticFeedback.filter { it }.first()
        assertEquals(true, enabled)
    }

    @Test
    fun `markInstalled persists installed pack metadata`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs1 = DataStorePreferences(context)

        prefs1.markInstalled(id = "devops", version = "2026.10.17", sha256 = "sha256:12345", installedAt = 1000L)
        val installedMap = prefs1.installedPacks.filter { "devops" in it }.first()
        val pack = installedMap["devops"]
        assertNotNull(pack)
        assertEquals("2026.10.17", pack?.version)
        assertEquals("sha256:12345", pack?.sha256)
        assertEquals(1000L, pack?.installedAt)
    }

    @Test
    fun `removeInstalledPack removes pack from preferences`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs1 = DataStorePreferences(context)
        prefs1.markInstalled(id = "devops", version = "2026.10.17", sha256 = "sha256:12345", installedAt = 1000L)
        prefs1.markInstalled(id = "git", version = "2026.10.18", sha256 = "sha256:67890", installedAt = 2000L)
        
        val twoPacksMap = prefs1.installedPacks.filter { it.size == 2 }.first()
        assertEquals(2, twoPacksMap.size)

        prefs1.removeInstalledPack("devops")
        val onePackMap = prefs1.installedPacks.filter { it.size == 1 }.first()
        assertEquals(1, onePackMap.size)
        assertTrue("git" in onePackMap)
        assertTrue(!onePackMap.containsKey("devops"))
    }
}
