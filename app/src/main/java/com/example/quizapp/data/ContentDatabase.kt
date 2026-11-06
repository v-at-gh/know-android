package com.example.quizapp.data

import android.content.Context
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.example.quizapp.data.dao.ArticleFtsDao
import com.example.quizapp.data.dao.CampaignDao
import com.example.quizapp.data.dao.DomainDao
import com.example.quizapp.data.dao.HintDao
import com.example.quizapp.data.dao.QuestionDao
import com.example.quizapp.data.entity.ArticleFts
import com.example.quizapp.data.entity.Campaign
import com.example.quizapp.data.entity.CampaignDomain
import com.example.quizapp.data.entity.CampaignTranslation
import com.example.quizapp.data.entity.Domain
import com.example.quizapp.data.entity.DomainTranslation
import com.example.quizapp.data.entity.Hint
import com.example.quizapp.data.entity.HintTranslation
import com.example.quizapp.data.entity.Question
import com.example.quizapp.data.entity.QuestionTranslation

@Database(
    entities = [
        Domain::class,
        DomainTranslation::class,
        Question::class,
        QuestionTranslation::class,
        Hint::class,
        HintTranslation::class,
        Campaign::class,
        CampaignTranslation::class,
        CampaignDomain::class,
        ArticleFts::class,
    ],
    version = 3,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class ContentDatabase : RoomDatabase() {

    abstract fun domainDao(): DomainDao
    abstract fun questionDao(): QuestionDao
    abstract fun hintDao(): HintDao
    abstract fun campaignDao(): CampaignDao
    abstract fun articleFtsDao(): ArticleFtsDao

    companion object {
        @Volatile
        private var INSTANCE: ContentDatabase? = null

        fun getInstance(context: Context): ContentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ContentDatabase::class.java,
                    "content.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
