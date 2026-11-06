package com.example.quizapp.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "campaigns",
    indices = [Index(value = ["slug"], unique = true)],
)
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val order: Int,
    val sourcePackId: String? = null,
    val sourcePackVersion: String? = null,
    val signature: String? = null,
    val prerequisiteDomainSlugs: List<String> = emptyList(),
    val prerequisiteCampaignSlugs: List<String> = emptyList(),
)

@Entity(
    tableName = "campaign_translations",
    foreignKeys = [
        ForeignKey(
            entity = Campaign::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["campaignId", "language"], unique = true)],
)
data class CampaignTranslation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val language: String,
    val name: String,
    val description: String,
)

@Entity(
    tableName = "campaign_domains",
    foreignKeys = [
        ForeignKey(
            entity = Campaign::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Domain::class,
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["campaignId", "domainId"], unique = true)],
)
data class CampaignDomain(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val domainId: Long,
    val position: Int,
)
