package com.example.quizapp.data.importer

import androidx.room3.Room
import com.example.quizapp.data.ContentDatabase
import com.example.quizapp.data.entity.Hint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DatasetImporterTest {

    private lateinit var db: ContentDatabase
    private lateinit var importer: DatasetImporter

    private val sampleDomainsJson = """
        [
          {
            "slug": "docker",
            "name": "Docker",
            "description": "Containers and images"
          }
        ]
    """.trimIndent()

    private val sampleQuestionsJson = """
        [
          {
            "uuid": "uuid-1",
            "text": "What is Docker?",
            "answer": "A container platform",
            "distractors": [
              {"text": "A hypervisor", "why": "No"},
              {"text": "An OS", "why": "No"},
              {"text": "A text editor", "why": "No"}
            ],
            "hints": ["Hint zero", "Hint one", "Hint two"],
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
                "datasets/en/docker.json" -> sampleQuestionsJson
                else -> null
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `partial import recovery restores missing question translations`() = runBlocking {
        // 1. Initial full import
        val report1 = importer.importLanguage("en")
        assertEquals(1, report1.domainsImported)
        assertEquals(1, report1.questionsImported)
        assertEquals(3, report1.hintsImported)

        val initialCount = db.questionDao().countTranslationsByLanguage("en")
        assertEquals(1, initialCount)

        // 2. Simulate partial import loss: delete question_translations rows
        db.questionDao().deleteAllTranslations()
        assertEquals(0, db.questionDao().countTranslationsByLanguage("en"))

        // 3. Re-run importLanguage
        val report2 = importer.importLanguage("en")
        assertEquals(1, report2.questionsImported)

        // 4. Assert missing rows are restored and counts match expected
        assertEquals(1, db.questionDao().countTranslationsByLanguage("en"))
        val hints = db.questionDao().getHintTexts(db.questionDao().getByDomainOrdered(db.domainDao().getBySlug("docker")!!.id).first().id, "en")
        assertEquals(3, hints.size)
        assertEquals("Hint zero", hints[0])
    }

    @Test
    fun `hint insertion with non-contiguous order aligns correctly`() = runBlocking {
        // First import to create question and domain
        importer.importLanguage("en")
        val domain = db.domainDao().getBySlug("docker")!!
        val question = db.questionDao().getByDomainOrdered(domain.id).first()

        // Create pre-existing hints with a gap (e.g. order 0 and order 2, omitting order 1)
        db.hintDao().insert(Hint(questionId = question.id, order = 0))
        db.hintDao().insert(Hint(questionId = question.id, order = 2))

        // Re-import language with 3 hints in asset ("Hint zero", "Hint one", "Hint two")
        importer.importLanguage("en")

        // Verify hints are correctly ordered and matched by order:
        // index 0 -> order 0
        // index 1 -> order 1 (inserted new)
        // index 2 -> order 2
        val hintsOrdered = db.hintDao().getByQuestionOrdered(question.id)
        assertEquals(3, hintsOrdered.size)
        assertEquals(0, hintsOrdered[0].order)
        assertEquals(1, hintsOrdered[1].order)
        assertEquals(2, hintsOrdered[2].order)

        val hintTexts = db.questionDao().getHintTexts(question.id, "en")
        assertEquals(listOf("Hint zero", "Hint one", "Hint two"), hintTexts)
    }
}
