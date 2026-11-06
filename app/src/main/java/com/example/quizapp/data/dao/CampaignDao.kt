package com.example.quizapp.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.example.quizapp.data.entity.Campaign
import com.example.quizapp.data.entity.CampaignDomain
import com.example.quizapp.data.entity.CampaignTranslation

@Dao
interface CampaignDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(campaign: Campaign): Long

    @Update
    suspend fun update(campaign: Campaign)

    @Query("SELECT * FROM campaigns WHERE slug = :slug")
    suspend fun getBySlug(slug: String): Campaign?

    @Query("SELECT * FROM campaigns ORDER BY `order`, id")
    suspend fun getAll(): List<Campaign>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslation(translation: CampaignTranslation): Long

    @Update
    suspend fun updateTranslation(translation: CampaignTranslation)

    @Query("SELECT * FROM campaign_translations WHERE campaignId = :campaignId AND language = :language")
    suspend fun getTranslation(campaignId: Long, language: String): CampaignTranslation?

    @Query("SELECT * FROM campaign_translations WHERE campaignId = :campaignId")
    suspend fun getTranslationsForCampaign(campaignId: Long): List<CampaignTranslation>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCampaignDomain(campaignDomain: CampaignDomain): Long

    @Update
    suspend fun updateCampaignDomain(campaignDomain: CampaignDomain)

    @Query("SELECT * FROM campaign_domains WHERE campaignId = :campaignId AND domainId = :domainId")
    suspend fun getCampaignDomain(campaignId: Long, domainId: Long): CampaignDomain?

    @Query("SELECT * FROM campaign_domains WHERE campaignId = :campaignId ORDER BY position")
    suspend fun getCampaignDomains(campaignId: Long): List<CampaignDomain>
}
