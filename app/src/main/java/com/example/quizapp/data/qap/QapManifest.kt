package com.example.quizapp.data.qap

/**
 * Represents the manifest metadata of a QAP (Quiz Archive Package) package.
 *
 * @property qapFormatVersion The version format number of the QAP specification (expected 2).
 * @property id Unique identifier of the package.
 * @property version Version string of the package.
 * @property createdAt Timestamp when the package was created.
 * @property publisher Optional [Publisher] information.
 * @property signature Optional cryptographic signature of the package.
 * @property languages List of language codes supported by the package.
 * @property domains List of [DomainEntry] items included in the package.
 * @property campaigns List of [CampaignEntry] items included in the package.
 * @property checksums Optional map of file checksums.
 */
data class QapManifest(
    val qapFormatVersion: Int,
    val id: String,
    val version: String,
    val createdAt: String,
    val publisher: Publisher? = null,
    val signature: String? = null,
    val languages: List<String> = emptyList(),
    val domains: List<DomainEntry> = emptyList(),
    val campaigns: List<CampaignEntry> = emptyList(),
    val checksums: Map<String, String>? = null,
) {
    /**
     * Represents publisher information for a QAP package.
     *
     * @property name The publisher name.
     * @property url Optional publisher website URL.
     */
    data class Publisher(
        val name: String,
        val url: String? = null,
    )

    /**
     * Represents a knowledge domain entry in the QAP manifest.
     *
     * @property slug Unique slug identifier for the domain.
     * @property name Localized domain names mapped by language code.
     * @property description Localized domain descriptions mapped by language code.
     * @property languages List of languages available for this domain.
     */
    data class DomainEntry(
        val slug: String,
        val name: Map<String, String>,
        val description: Map<String, String>,
        val languages: List<String>,
    )

    /**
     * Represents a campaign entry in the QAP manifest.
     *
     * @property slug Unique slug identifier for the campaign.
     * @property order Display ordering index for the campaign.
     * @property name Localized campaign names mapped by language code.
     * @property description Localized campaign descriptions mapped by language code.
     * @property domainSlugs List of domain slugs associated with this campaign.
     * @property prerequisiteDomainSlugs List of prerequisite domain slugs required to unlock this campaign.
     * @property prerequisiteCampaignSlugs List of prerequisite campaign slugs required to unlock this campaign.
     */
    data class CampaignEntry(
        val slug: String,
        val order: Int = 0,
        val name: Map<String, String>,
        val description: Map<String, String>,
        val domainSlugs: List<String>,
        val prerequisiteDomainSlugs: List<String> = emptyList(),
        val prerequisiteCampaignSlugs: List<String> = emptyList(),
    )
}
