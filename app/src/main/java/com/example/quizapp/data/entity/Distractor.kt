package com.example.quizapp.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class Distractor(
    val text: String,
    val why: String? = null,
)
