package com.example.quizapp.data.qap

/**
 * Encapsulates the results and summary statistics of importing a QAP pack into the database.
 *
 * @property packId Unique identifier of the imported pack.
 * @property packVersion Version string of the imported pack.
 * @property campaignsImported Number of campaigns successfully imported.
 * @property domainsImported Number of domains successfully imported.
 * @property questionsImported Number of questions successfully imported.
 * @property hintsImported Number of hints successfully imported.
 * @property errors List of error messages encountered during import.
 */
data class QapImportReport(
    val packId: String,
    val packVersion: String,
    val campaignsImported: Int = 0,
    val domainsImported: Int = 0,
    val questionsImported: Int = 0,
    val hintsImported: Int = 0,
    val errors: List<String> = emptyList(),
) {
    /** Returns `true` if at least one campaign or domain was successfully imported. */
    val isImported: Boolean get() = campaignsImported > 0 || domainsImported > 0

    /** Returns `true` if any import errors occurred. */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    override fun toString(): String = buildString {
        append("QapImportReport(packId=$packId, version=$packVersion")
        append(", campaigns=$campaignsImported")
        append(", domains=$domainsImported")
        append(", questions=$questionsImported")
        append(", hints=$hintsImported")
        if (hasErrors) append(", errors=${errors.size}")
        append(")")
    }
}
