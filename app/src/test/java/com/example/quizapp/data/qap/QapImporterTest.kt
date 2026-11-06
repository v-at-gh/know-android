package com.example.quizapp.data.qap

import androidx.room3.Room
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.importer.DatasetImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class QapImporterTest {

    private lateinit var db: ContentDatabase

    private val sampleManifestJson = """
        {
          "qapFormatVersion": 2,
          "id": "systems-analyst",
          "version": "2026.10.15",
          "createdAt": "2026-10-15T12:00:00Z",
          "publisher": {
            "name": "QuizApp Core",
            "url": "https://example.com"
          },
          "signature": "sig-12345",
          "languages": ["en", "ru"],
          "domains": [
            {
              "slug": "analyst-requirements",
              "name": {"en": "Requirements Engineering", "ru": "Инженерия требований"},
              "description": {"en": "Elicitation and user stories", "ru": "Сбор требований"},
              "languages": ["en", "ru"]
            }
          ],
          "campaigns": [
            {
              "slug": "systems-analyst-campaign",
              "order": 20,
              "name": {"en": "Systems Analyst", "ru": "Системный аналитик"},
              "description": {"en": "Turn business needs into software", "ru": "Преобразование требований"},
              "domainSlugs": ["analyst-requirements"],
              "prerequisiteDomainSlugs": [],
              "prerequisiteCampaignSlugs": []
            }
          ]
        }
    """.trimIndent()

    private val sampleQuestionsJsonEn = """
        [
          {
            "uuid": "q-analyst-1",
            "text": "What is a functional requirement?",
            "answer": "A statement of software behavior",
            "distractors": [
              {"text": "A budget constraint"},
              {"text": "A SLA target"}
            ],
            "hints": ["Focus on behavior"],
            "difficulty": 1
          }
        ]
    """.trimIndent()

    private val sampleQuestionsJsonRu = """
        [
          {
            "uuid": "q-analyst-1",
            "text": "Что такое функциональное требование?",
            "answer": "Описание поведения системы",
            "distractors": [
              {"text": "Ограничение бюджета"},
              {"text": "Целевой SLA"}
            ],
            "hints": ["Фокус на поведении"],
            "difficulty": 1
          }
        ]
    """.trimIndent()

    private val v1DomainsJson = """
        [
          {
            "slug": "docker",
            "name": "Docker",
            "description": "Containers"
          }
        ]
    """.trimIndent()

    private val v1DockerQuestionsJson = """
        [
          {
            "uuid": "uuid-docker-1",
            "text": "What is Docker?",
            "answer": "Container platform",
            "distractors": [
              {"text": "Hypervisor"},
              {"text": "OS"}
            ],
            "hints": ["Containers"],
            "difficulty": 1
          }
        ]
    """.trimIndent()

    private val v1CampaignsJson = """
        [
          {
            "slug": "devops",
            "order": 10,
            "name": "DevOps Engineer",
            "description": "Master DevOps tools",
            "domainSlugs": ["docker"]
          },
          {
            "slug": "systems-analyst",
            "order": 20,
            "name": "Systems Analyst",
            "description": "Software specifications",
            "domainSlugs": ["docker"]
          }
        ]
    """.trimIndent()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `v2 pack imports campaign, domain links, translations, and questions`() = runBlocking {
        val importer = QapImporter(db) { path ->
            when (path) {
                "qap/manifest.json" -> sampleManifestJson
                "qap/datasets/en/analyst-requirements.json" -> sampleQuestionsJsonEn
                "qap/datasets/ru/analyst-requirements.json" -> sampleQuestionsJsonRu
                else -> null
            }
        }

        val report = importer.importFromAssets("qap")
        assertTrue(report.isImported)
        assertEquals(1, report.campaignsImported)
        assertEquals(1, report.domainsImported)
        assertEquals(2, report.questionsImported)

        // Verify Campaign entity
        val campaign = db.campaignDao().getBySlug("systems-analyst-campaign")
        assertNotNull(campaign)
        assertEquals(20, campaign!!.order)
        assertEquals("systems-analyst", campaign.sourcePackId)
        assertEquals("2026.10.15", campaign.sourcePackVersion)
        assertEquals("sig-12345", campaign.signature)

        // Verify Campaign Translations
        val enTranslation = db.campaignDao().getTranslation(campaign.id, "en")
        assertNotNull(enTranslation)
        assertEquals("Systems Analyst", enTranslation!!.name)
        assertEquals("Turn business needs into software", enTranslation.description)

        val ruTranslation = db.campaignDao().getTranslation(campaign.id, "ru")
        assertNotNull(ruTranslation)
        assertEquals("Системный аналитик", ruTranslation!!.name)

        // Verify Domain and Domain Translations
        val domain = db.domainDao().getBySlug("analyst-requirements")
        assertNotNull(domain)
        val domainEnTrans = db.domainDao().getTranslation(domain!!.id, "en")
        assertEquals("Requirements Engineering", domainEnTrans!!.name)

        // Verify CampaignDomain links
        val campaignDomains = db.campaignDao().getCampaignDomains(campaign.id)
        assertEquals(1, campaignDomains.size)
        assertEquals(domain.id, campaignDomains[0].domainId)
        assertEquals(0, campaignDomains[0].position)

        // Verify imported question
        val questionsEn = db.questionDao().getQuizQuestions("en")
        assertEquals(1, questionsEn.size)
        assertEquals("What is a functional requirement?", questionsEn[0].text)

        val questionsRu = db.questionDao().getQuizQuestions("ru")
        assertEquals(1, questionsRu.size)
        assertEquals("Что такое функциональное требование?", questionsRu[0].text)
    }

    @Test
    fun `v2 pack import is idempotent`() = runBlocking {
        val importer = QapImporter(db) { path ->
            when (path) {
                "qap/manifest.json" -> sampleManifestJson
                "qap/datasets/en/analyst-requirements.json" -> sampleQuestionsJsonEn
                "qap/datasets/ru/analyst-requirements.json" -> sampleQuestionsJsonRu
                else -> null
            }
        }

        val report1 = importer.importFromAssets("qap")
        assertEquals(1, report1.campaignsImported)

        val campaigns1 = db.campaignDao().getAll()
        assertEquals(1, campaigns1.size)

        // Re-import second time
        val report2 = importer.importFromAssets("qap")
        assertEquals(1, report2.campaignsImported)

        val campaigns2 = db.campaignDao().getAll()
        assertEquals(1, campaigns2.size)

        val campaign = campaigns2[0]
        val enTranslations = db.campaignDao().getTranslationsForCampaign(campaign.id)
        assertEquals(2, enTranslations.size) // "en" and "ru"

        val campaignDomains = db.campaignDao().getCampaignDomains(campaign.id)
        assertEquals(1, campaignDomains.size)
    }

    @Test
    fun `v1 path imports campaigns from campaigns json`() = runBlocking {
        val datasetImporter = DatasetImporter(db) { path ->
            when (path) {
                "datasets/en/domains.json" -> v1DomainsJson
                "datasets/en/docker.json" -> v1DockerQuestionsJson
                "datasets/en/campaigns.json" -> v1CampaignsJson
                else -> null
            }
        }

        val report = datasetImporter.importLanguage("en")
        assertEquals(1, report.domainsImported)

        val devops = db.campaignDao().getBySlug("devops")
        assertNotNull(devops)
        val devopsTrans = db.campaignDao().getTranslation(devops!!.id, "en")
        assertEquals("DevOps Engineer", devopsTrans!!.name)

        val sysAnalyst = db.campaignDao().getBySlug("systems-analyst")
        assertNotNull(sysAnalyst)
        val sysAnalystTrans = db.campaignDao().getTranslation(sysAnalyst!!.id, "en")
        assertEquals("Systems Analyst", sysAnalystTrans!!.name)

        val devopsDomains = db.campaignDao().getCampaignDomains(devops.id)
        assertEquals(1, devopsDomains.size)
        val dockerDomain = db.domainDao().getBySlug("docker")!!
        assertEquals(dockerDomain.id, devopsDomains[0].domainId)
    }
}
