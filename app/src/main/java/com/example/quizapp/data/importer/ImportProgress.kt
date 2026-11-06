package com.example.quizapp.data.importer

/**
 * Tracks real-time import progress during dataset or QAP pack ingestion.
 *
 * @property domainsDone Number of domains completed so far.
 * @property domainsTotal Total number of domains to import.
 * @property currentDomainSlug The slug of the domain currently being imported.
 * @property questionsImported Total number of questions imported so far.
 */
data class Progress(
    val domainsDone: Int,
    val domainsTotal: Int,
    val currentDomainSlug: String,
    val questionsImported: Int,
)

/**
 * Encapsulates the results and statistics of a metadata import operation.
 *
 * @property domainsImported Number of domains successfully imported.
 * @property campaignsImported Number of campaigns successfully imported.
 * @property errors List of error messages encountered during metadata import.
 */
data class MetadataReport(
    val domainsImported: Int = 0,
    val campaignsImported: Int = 0,
    val errors: List<String> = emptyList(),
) {
    /** Returns `true` if any error messages occurred during import. */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /** Returns `true` if at least one domain was successfully imported. */
    val isImported: Boolean get() = domainsImported > 0
}
