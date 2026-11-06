package com.example.quizapp.data.qap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QapManifestParserTest {

    @Test
    fun `minimal valid manifest parses correctly`() {
        val json = """
            {
              "qapFormatVersion": 2,
              "id": "basics-pack",
              "version": "1.0.0",
              "createdAt": "2026-10-15T12:00:00Z",
              "languages": ["en"],
              "domains": [
                {
                  "slug": "math",
                  "name": {"en": "Mathematics"},
                  "description": {"en": "Math basics"},
                  "languages": ["en"]
                }
              ],
              "campaigns": [
                {
                  "slug": "basics",
                  "order": 1,
                  "name": {"en": "Basics Campaign"},
                  "description": {"en": "Intro to basics"},
                  "domainSlugs": ["math"],
                  "prerequisiteDomainSlugs": [],
                  "prerequisiteCampaignSlugs": []
                }
              ]
            }
        """.trimIndent()

        val manifest = QapManifestParser.parse(json)
        assertEquals(2, manifest.qapFormatVersion)
        assertEquals("basics-pack", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals("2026-10-15T12:00:00Z", manifest.createdAt)
        assertNull(manifest.signature)
        assertEquals(1, manifest.domains.size)
        assertEquals("math", manifest.domains[0].slug)
        assertEquals("Mathematics", manifest.domains[0].name["en"])
        assertEquals(1, manifest.campaigns.size)
        assertEquals("basics", manifest.campaigns[0].slug)
        assertEquals(listOf("math"), manifest.campaigns[0].domainSlugs)
    }

    @Test(expected = QapManifestException::class)
    fun `missing qapFormatVersion throws QapManifestException`() {
        val json = """
            {
              "id": "basics-pack",
              "version": "1.0.0",
              "createdAt": "2026-10-15T12:00:00Z",
              "domains": [],
              "campaigns": []
            }
        """.trimIndent()

        QapManifestParser.parse(json)
    }

    @Test(expected = QapManifestException::class)
    fun `missing id throws QapManifestException`() {
        val json = """
            {
              "qapFormatVersion": 2,
              "version": "1.0.0",
              "createdAt": "2026-10-15T12:00:00Z",
              "domains": [],
              "campaigns": []
            }
        """.trimIndent()

        QapManifestParser.parse(json)
    }

    @Test(expected = QapManifestException::class)
    fun `unknown qapFormatVersion throws QapManifestException`() {
        val json = """
            {
              "qapFormatVersion": 3,
              "id": "basics-pack",
              "version": "1.0.0",
              "createdAt": "2026-10-15T12:00:00Z",
              "domains": [],
              "campaigns": []
            }
        """.trimIndent()

        QapManifestParser.parse(json)
    }

    @Test
    fun `domain with locale map missing some languages parses successfully`() {
        val json = """
            {
              "qapFormatVersion": 2,
              "id": "systems-analyst",
              "version": "2026.10.15",
              "createdAt": "2026-10-15T12:00:00Z",
              "languages": ["en", "ru", "cs"],
              "domains": [
                {
                  "slug": "analyst-requirements",
                  "name": {"en": "Requirements Engineering"},
                  "description": {"en": "Elicitation and user stories"},
                  "languages": ["en", "ru", "cs"]
                }
              ],
              "campaigns": []
            }
        """.trimIndent()

        val manifest = QapManifestParser.parse(json)
        val domain = manifest.domains[0]
        assertEquals(1, domain.name.size)
        assertEquals("Requirements Engineering", domain.name["en"])
        assertNull(domain.name["ru"])
        assertNull(domain.name["cs"])
    }

    @Test
    fun `signature present as string and as null`() {
        val jsonWithSignature = """
            {
              "qapFormatVersion": 2,
              "id": "pack-signed",
              "version": "1.0",
              "createdAt": "2026-10-15T12:00:00Z",
              "signature": "ed25519:abcdef123456",
              "domains": [],
              "campaigns": []
            }
        """.trimIndent()

        val manifest1 = QapManifestParser.parse(jsonWithSignature)
        assertEquals("ed25519:abcdef123456", manifest1.signature)

        val jsonWithNullSignature = """
            {
              "qapFormatVersion": 2,
              "id": "pack-null-sig",
              "version": "1.0",
              "createdAt": "2026-10-15T12:00:00Z",
              "signature": null,
              "domains": [],
              "campaigns": []
            }
        """.trimIndent()

        val manifest2 = QapManifestParser.parse(jsonWithNullSignature)
        assertNull(manifest2.signature)
    }
}
