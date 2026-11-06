package com.example.quizapp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseMigrationTest {

    @Test
    fun testDestructiveFallbackEnabled() {
        val contentDatabaseSrc = File("src/main/java/com/example/quizapp/data/ContentDatabase.kt").readText()
        val userDatabaseSrc = File("src/main/java/com/example/quizapp/data/UserDatabase.kt").readText()
        assertTrue("ContentDatabase must use fallbackToDestructiveMigration",
            "fallbackToDestructiveMigration" in contentDatabaseSrc)
        assertTrue("UserDatabase must use fallbackToDestructiveMigration",
            "fallbackToDestructiveMigration" in userDatabaseSrc)
        assertFalse("Migration(3,3) is invalid no-op and must be removed",
            "Migration(3, 3)" in contentDatabaseSrc || "Migration(3,3)" in contentDatabaseSrc)
        assertFalse("Migration(3,3) is invalid no-op and must be removed in UserDatabase",
            "Migration(3, 3)" in userDatabaseSrc || "Migration(3,3)" in userDatabaseSrc)
    }
}
