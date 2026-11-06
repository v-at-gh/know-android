package com.example.quizapp.data.qap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QapCatalogTest {

    @Test
    fun `parse valid catalog manifest with packs`() {
        val json = """
            {
              "updatedAt": "2026-10-15T12:00:00Z",
              "packs": [
                {
                  "id": "devops",
                  "file": "devops.qap",
                  "size": 1048576,
                  "sha256": "sha256:1234567890abcdef",
                  "version": "1.0.0",
                  "kind": "campaign",
                  "createdAt": "2026-10-15T10:00:00Z",
                  "languages": ["en", "ru"],
                  "campaigns": ["devops"],
                  "domains": ["cicd", "iac"],
                  "totals": {"questions": 42, "articles": 10}
                },
                {
                  "id": "broken",
                  "error": "Failed to read zip"
                }
              ]
            }
        """.trimIndent()

        val catalog = QapCatalog.parse(json)
        assertEquals(1, catalog.size)

        val pack = catalog[0]
        assertEquals("devops", pack.id)
        assertEquals("devops.qap", pack.file)
        assertEquals(1048576L, pack.size)
        assertEquals(1.0, pack.sizeMb, 0.001)
        assertEquals("sha256:1234567890abcdef", pack.sha256)
        assertEquals("1.0.0", pack.version)
        assertEquals("campaign", pack.kind)
        assertEquals("2026-10-15T10:00:00Z", pack.createdAt)
        assertEquals(listOf("en", "ru"), pack.languages)
        assertEquals(listOf("devops"), pack.campaigns)
        assertEquals(listOf("cicd", "iac"), pack.domains)
        assertEquals(42, pack.totals["questions"])
        assertEquals(10, pack.totals["articles"])
    }

    @Test
    fun `parse empty or missing packs returns empty list`() {
        val json = """{"updatedAt": "2026-10-15T12:00:00Z"}"""
        val catalog = QapCatalog.parse(json)
        assertTrue(catalog.isEmpty())
    }
}
