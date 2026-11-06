package com.example.quizapp.data

import androidx.room3.ColumnTypeConverter
import com.example.quizapp.data.entity.Distractor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @ColumnTypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return json.encodeToString(ListSerializer(String.serializer()), value)
    }

    @ColumnTypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        if (value.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        } catch (e: Exception) {
            android.util.Log.w("Converters", "Malformed JSON list: $value", e)
            emptyList()
        }
    }

    @ColumnTypeConverter
    fun fromDistractorList(value: List<Distractor>?): String? {
        if (value == null) return null
        return json.encodeToString(ListSerializer(Distractor.serializer()), value)
    }

    @ColumnTypeConverter
    fun toDistractorList(value: String?): List<Distractor>? {
        if (value == null) return null
        if (value.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(Distractor.serializer()), value)
        } catch (e: Exception) {
            android.util.Log.w("Converters", "Malformed JSON distractor list: $value", e)
            emptyList()
        }
    }
}
