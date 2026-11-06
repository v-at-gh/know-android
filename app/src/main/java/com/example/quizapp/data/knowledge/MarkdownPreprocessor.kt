package com.example.quizapp.data.knowledge

object MarkdownPreprocessor {

    fun preprocess(markdown: String): String {
        var result = markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        result = LatexPatterns.displayBracket.replace(result) { m ->
            "```math\n${m.groupValues[1].trim()}\n```"
        }
        result = LatexPatterns.displayPollar.replace(result) { m ->
            "```math\n${m.groupValues[1].trim()}\n```"
        }
        result = LatexPatterns.inlineParenRaw.replace(result) { m ->
            "\$${m.groupValues[1]}\$"
        }
        return result
    }
}