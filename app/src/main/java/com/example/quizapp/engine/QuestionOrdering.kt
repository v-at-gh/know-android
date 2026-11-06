package com.example.quizapp.engine

object QuestionOrdering {

    fun shuffledByDifficulty(
        questions: List<QuizQuestion>,
        seed: Long? = null,
        random: kotlin.random.Random = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default,
    ): List<QuizQuestion> {
        return questions
            .groupBy { it.difficulty }
            .toSortedMap()
            .flatMap { (_, group) -> group.shuffled(random) }
    }
}