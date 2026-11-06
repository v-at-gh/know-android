package com.example.quizapp.data

import android.content.Context
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.example.quizapp.data.dao.AttemptDao
import com.example.quizapp.data.dao.FeedbackDao
import com.example.quizapp.data.dao.PlayerDao
import com.example.quizapp.data.entity.Attempt
import com.example.quizapp.data.entity.Feedback
import com.example.quizapp.data.entity.Player

@Database(
    entities = [
        Player::class,
        Attempt::class,
        Feedback::class,
    ],
    version = 3,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao
    abstract fun attemptDao(): AttemptDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "user.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
