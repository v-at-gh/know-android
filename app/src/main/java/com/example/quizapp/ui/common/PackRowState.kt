package com.example.quizapp.ui.common

import com.example.quizapp.data.prefs.InstalledPack
import com.example.quizapp.data.qap.QapPackEntry

/**
 * UI representation state of a QAP content pack entry row.
 */
data class PackRowState(
    val entry: QapPackEntry,
    val installed: Boolean,
    val updateAvailable: Boolean,
    val coveredLanguages: List<String>,
    val missingLanguages: List<String>,
)

fun buildPackRows(
    entries: List<QapPackEntry>,
    installedPacks: Map<String, InstalledPack>,
    playerLanguages: Set<String>,
): List<PackRowState> {
    return entries.map { entry ->
        val installedPack = installedPacks[entry.id]
        val installed = installedPack != null
        val updateAvailable = installed && installedPack.sha256 != entry.sha256
        val covered = entry.languages.filter { it in playerLanguages }
        val missing = playerLanguages.filter { it !in entry.languages }.sorted()
        PackRowState(
            entry = entry,
            installed = installed,
            updateAvailable = updateAvailable,
            coveredLanguages = covered,
            missingLanguages = missing,
        )
    }
}
