package com.example.quizapp.data.importer

import androidx.room3.Room
import com.example.quizapp.data.ContentDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProgressiveImportTest {

    private lateinit var db: ContentDatabase
    private lateinit var importer: DatasetImporter

    private val sampleDomainsJson = """
        [
          {
            "slug": "domain1",
            "name": "Domain One",
            "description": "First domain"
          },
          {
            "slug": "domain2",
            "name": "Domain Two",
            "description": "Second domain"
          }
        ]
    """.trimIndent()

    private val sampleQuestionsJson1 = """
        [
          {
            "uuid": "u-1",
            "text": "Question 1",
            "answer": "Ans 1",
            "distractors": [{"text": "D1"}, {"text": "D1-alt"}],
            "hints": ["Hint 1"],
            "difficulty": 1
          }
        ]
    """.trimIndent()

    private val sampleQuestionsJson2 = """
        [
          {
            "uuid": "u-2",
            "text": "Question 2",
            "answer": "Ans 2",
            "distractors": [{"text": "D2"}, {"text": "D2-alt"}],
            "hints": ["Hint 2"],
            "difficulty": 1
          }
        ]
    """.trimIndent()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        importer = DatasetImporter(db) { path ->
            when (path) {
                "datasets/en/domains.json" -> sampleDomainsJson
                "datasets/en/domain1.json" -> sampleQuestionsJson1
                "datasets/en/domain2.json" -> sampleQuestionsJson2
                else -> null
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importMetadataOnlyImportsDomainsAndCampaigns() = runBlocking {
        val report = importer.importMetadata("en")
        assertEquals(2, report.domainsImported)

        val domains = db.domainDao().getAll()
        assertEquals(2, domains.size)

        // Question count should be zero
        val questionCount = db.questionDao().countTranslationsByLanguage("en")
        assertEquals(0, questionCount)
    }

    @Test
    fun importLanguageReportsProgressMonotonically() = runBlocking {
        val progressList = mutableListOf<Progress>()
        val report = importer.importLanguage("en") { progress ->
            progressList.add(progress)
        }

        assertEquals(2, report.domainsImported)
        assertTrue(progressList.isNotEmpty())

        // Initial progress call
        assertEquals(0, progressList.first().domainsDone)
        assertEquals(2, progressList.first().domainsTotal)

        // Final progress call
        val last = progressList.last()
        assertEquals(2, last.domainsDone)
        assertEquals(2, last.domainsTotal)
        assertEquals(2, report.questionsImported)
    }
}
