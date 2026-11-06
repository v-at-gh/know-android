package com.example.quizapp.data.importer

import com.example.quizapp.data.entity.Distractor
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Utility object for parsing domain and question JSON datasets into structured data entries,
 * validating question constraints (select vs construct types).
 */
object DatasetParser {

    /**
     * Represents a parsed knowledge domain entry from JSON.
     *
     * @property slug Unique slug identifier for the domain.
     * @property name Name of the domain.
     * @property description Description of the domain.
     */
    data class DomainEntry(
        val slug: String,
        val name: String,
        val description: String,
    )

    /**
     * Represents a parsed quiz question entry from JSON.
     *
     * @property uuid Unique UUID string of the question.
     * @property text The question prompt text.
     * @property answer The correct answer text.
     * @property distractors List of incorrect distractor choices.
     * @property difficulty Difficulty rating.
     * @property hints List of progressive hint strings.
     * @property answerWhy Optional explanation text for the correct answer.
     * @property relatedArticleSlug Optional slug of a related knowledge article.
     * @property type Question type string (e.g., "select" or "construct").
     * @property payloadJson Optional JSON payload string for construct questions.
     */
    data class QuestionEntry(
        val uuid: String,
        val text: String,
        val answer: String,
        val distractors: List<Distractor>,
        val difficulty: Int,
        val hints: List<String>,
        val answerWhy: String? = null,
        val relatedArticleSlug: String? = null,
        val type: String = "select",
        val payloadJson: String? = null,
    )

    /**
     * Parses a JSON string into a list of [DomainEntry] items.
     *
     * @param json Raw JSON string containing domain entries.
     * @return A list of parsed [DomainEntry] objects.
     */
    fun parseDomains(json: String): List<DomainEntry> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            DomainEntry(
                slug = obj.getString("slug"),
                name = obj.getString("name"),
                description = obj.getString("description"),
            )
        }
    }

    /**
     * Parses a JSON string into a pair of successfully parsed [QuestionEntry] items and error messages.
     *
     * Validates each question entry against formatting and business rules.
     *
     * @param json Raw JSON string containing question entries.
     * @return A [Pair] containing a list of valid [QuestionEntry] items and a list of error strings.
     */
    fun parseQuestions(json: String): Pair<List<QuestionEntry>, List<String>> {
        val arr = JSONArray(json)
        val valid = mutableListOf<QuestionEntry>()
        val errors = mutableListOf<String>()

        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val text = obj.getString("text")
                val reason = validate(obj, text)
                if (reason != null) {
                    errors.add("[q#$i ${text.take(50)}…] $reason")
                    continue
                }
                valid.add(buildEntry(obj))
            } catch (t: Throwable) {
                errors.add("[q#$i] parse failed: ${t.message ?: t::class.java.simpleName}")
            }
        }
        return valid to errors
    }

    private fun validate(obj: JSONObject, text: String): String? {
        val type = obj.optString("type", "select")
        if (type == "construct") {
            val tokens = obj.optJSONArray("tokens")
            val solution = obj.optJSONArray("solution")
            if (tokens == null || tokens.length() < 2) {
                return "construct question needs >= 2 tokens"
            }
            if (solution == null || solution.length() == 0) {
                return "construct question solution cannot be empty"
            }
            val solIndices = List(solution.length()) { i -> solution.getInt(i) }
            if (solIndices.any { it !in 0 until tokens.length() }) {
                return "construct solution index out of bounds"
            }
            if (solIndices.toSet().size != solIndices.size) {
                return "duplicate token indices in solution"
            }
            if (solIndices.size != tokens.length()) {
                return "construct solution must use every token exactly once"
            }
            return null
        }

        val answer = obj.getString("answer")
        val distractors = parseDistractors(obj.getJSONArray("distractors"))
        val distractorTexts = distractors.map { it.text }

        return when {
            distractors.size < 2 ->
                "only ${distractors.size} distractors (need >= 2)"
            answer in distractorTexts ->
                "answer also appears among distractors"
            distractorTexts.toSet().size != distractors.size ->
                "duplicate distractors"
            else -> null
        }
    }

    private fun buildEntry(obj: JSONObject): QuestionEntry {
        val type = obj.optString("type", "select")
        val uuid = obj.optString("uuid").takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val answerWhy = obj.optString("answerWhy").takeIf { it.isNotBlank() }
        val relatedArticleSlug = obj.optString("relatedArticleSlug").takeIf { it.isNotBlank() }

        val (answer, distractors, payloadJson) = if (type == "construct") {
            val tokensArr = obj.getJSONArray("tokens")
            val solutionArr = obj.getJSONArray("solution")
            val distractorTokensArr = obj.optJSONArray("distractorTokens") ?: JSONArray()
            val computedAnswer = List(solutionArr.length()) { i ->
                val tokenIdx = solutionArr.getInt(i)
                tokensArr.getJSONObject(tokenIdx).getString("text")
            }.joinToString(" ")
            val payload = JSONObject()
                .put("tokens", tokensArr)
                .put("solution", solutionArr)
                .put("distractorTokens", distractorTokensArr)
                .toString()
            Triple(computedAnswer, emptyList<Distractor>(), payload)
        } else {
            Triple(
                obj.getString("answer"),
                parseDistractors(obj.getJSONArray("distractors")),
                null,
            )
        }

        return QuestionEntry(
            uuid = uuid,
            text = obj.getString("text"),
            answer = answer,
            distractors = distractors,
            difficulty = obj.getInt("difficulty"),
            hints = obj.getJSONArray("hints").toStringList(),
            answerWhy = answerWhy,
            relatedArticleSlug = relatedArticleSlug,
            type = type,
            payloadJson = payloadJson,
        )
    }

    private fun parseDistractors(jsonArray: JSONArray): List<Distractor> =
        List(jsonArray.length()) { i ->
            when {
                jsonArray.isNull(i) -> Distractor(text = "")
                else -> {
                    val elem = jsonArray.get(i)
                    if (elem is JSONObject) {
                        val text = elem.getString("text")
                        val why = elem.optString("why").takeIf { it.isNotBlank() }
                        Distractor(text = text, why = why)
                    } else {
                        Distractor(text = elem.toString())
                    }
                }
            }
        }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { getString(it) }
}
