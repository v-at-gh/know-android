package com.example.quizapp.data.knowledge

object LatexPatterns {

    val inlineDollar = Regex("""(?<!\$)\$([^\$]+?)\$(?!\$)""")
    val inlineParenRaw = Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)
    val displayBracket = Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
    val displayPollar = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)

    fun extractInlineFormulas(text: String): List<String> =
        inlineDollar.findAll(text).map { it.groupValues[1].trim() }.toList()
}