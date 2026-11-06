package com.example.quizapp.data.entity

import com.example.quizapp.data.dao.DomainWithTranslation
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainSelectionPersistenceTest {

    private val englishDomains = listOf(
        DomainWithTranslation(1L, "math", "Mathematics", "Math desc"),
        DomainWithTranslation(2L, "physics", "Physics", "Physics desc"),
        DomainWithTranslation(3L, "biology", "Biology", "Biology desc"),
    )

    private val germanDomains = listOf(
        DomainWithTranslation(10L, "math", "Mathematik", "Mathe desc"),
        DomainWithTranslation(11L, "physics", "Physik", "Physik desc"),
        DomainWithTranslation(12L, "biology", "Biologie", "Biologie desc"),
    )

    @Test
    fun `default selection when selectedDomainSlugs is null includes all preferred domains`() {
        val player = Player(
            id = 1L,
            name = "TestPlayer",
            preferredDomainSlugs = listOf("math", "physics", "biology"),
            selectedDomainSlugs = null,
        )

        val preferred = player.preferredDomainSlugs.toSet()
        val selectedSlugs = player.selectedDomainSlugs?.toSet()?.intersect(preferred) ?: preferred

        assertEquals(setOf("math", "physics", "biology"), selectedSlugs)

        val selectedIds = englishDomains
            .filter { it.slug in selectedSlugs }
            .map { it.id }
            .toSet()

        assertEquals(setOf(1L, 2L, 3L), selectedIds)
    }

    @Test
    fun `persisted selection restores saved domain slugs`() {
        val player = Player(
            id = 1L,
            name = "TestPlayer",
            preferredDomainSlugs = listOf("math", "physics", "biology"),
            selectedDomainSlugs = listOf("math", "physics"),
        )

        val preferred = player.preferredDomainSlugs.toSet()
        val selectedSlugs = player.selectedDomainSlugs?.toSet()?.intersect(preferred) ?: preferred

        assertEquals(setOf("math", "physics"), selectedSlugs)

        val selectedIds = englishDomains
            .filter { it.slug in selectedSlugs }
            .map { it.id }
            .toSet()

        assertEquals(setOf(1L, 2L), selectedIds)
    }

    @Test
    fun `changing primary language preserves selected domain slugs with new translated IDs`() {
        // Player has selected math and physics in English
        var player = Player(
            id = 1L,
            name = "TestPlayer",
            preferredDomainSlugs = listOf("math", "physics", "biology"),
            selectedDomainSlugs = listOf("math", "physics"),
        )

        // Change Primary Language to German
        player = player.copy(primaryContentLanguage = "de")

        val preferred = player.preferredDomainSlugs.toSet()
        val selectedSlugs = player.selectedDomainSlugs?.toSet()?.intersect(preferred) ?: preferred

        // Slugs remain math and physics
        assertEquals(setOf("math", "physics"), selectedSlugs)

        // German domain IDs (10L and 11L) are properly resolved
        val selectedGermanIds = germanDomains
            .filter { it.slug in selectedSlugs }
            .map { it.id }
            .toSet()

        assertEquals(setOf(10L, 11L), selectedGermanIds)
    }

    @Test
    fun `clearing all domains persists empty selection`() {
        val player = Player(
            id = 1L,
            name = "TestPlayer",
            preferredDomainSlugs = listOf("math", "physics", "biology"),
            selectedDomainSlugs = emptyList(),
        )

        val preferred = player.preferredDomainSlugs.toSet()
        val selectedSlugs = player.selectedDomainSlugs?.toSet()?.intersect(preferred) ?: preferred

        assertEquals(emptySet<String>(), selectedSlugs)

        val selectedIds = englishDomains
            .filter { it.slug in selectedSlugs }
            .map { it.id }
            .toSet()

        assertEquals(emptySet<Long>(), selectedIds)
    }
}
