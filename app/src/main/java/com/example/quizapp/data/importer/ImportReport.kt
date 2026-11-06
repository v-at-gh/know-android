package com.example.quizapp.data.importer

/**
 * Summarizes the results of a full language dataset import.
 *
 * @property language The language code imported (e.g., "en").
 * @property domainsImported Number of domains successfully imported.
 * @property questionsImported Number of questions successfully imported.
 * @property hintsImported Number of hints successfully imported.
 * @property skippedDomains List of domain slugs skipped during import.
 * @property skippedQuestions List of question identifiers skipped due to validation errors.
 * @property errors List of error messages encountered during import.
 */
data class ImportReport(
    val language: String,
    val domainsImported: Int = 0,
    val questionsImported: Int = 0,
    val hintsImported: Int = 0,
    val skippedDomains: List<String> = emptyList(),
    val skippedQuestions: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    /** Returns `true` if at least one domain was successfully imported. */
    val isImported: Boolean get() = domainsImported > 0

    /** Returns `true` if any import errors occurred. */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    override fun toString(): String = buildString {
        append("ImportReport(lang=$language")
        append(", domains=$domainsImported")
        append(", questions=$questionsImported")
        append(", hints=$hintsImported")
        if (skippedDomains.isNotEmpty()) append(", skipped=${skippedDomains}")
        if (skippedQuestions.isNotEmpty()) append(", badQ=${skippedQuestions.size}")
        if (hasErrors) append(", errors=${errors.size}")
        append(")")
    }
}