package com.luc.body.sprite

object SpriteCycleParser {
    fun longestDurationMs(svg: String): Long = ANIMATION_DECLARATION.findAll(withoutComments(svg))
        .flatMap { declaration ->
            declaration.groupValues[1].splitToSequence(',').mapNotNull { item ->
                TIME.find(item)?.let(::toMilliseconds)
            }
        }
        .maxOrNull()
        ?: 0L

    private fun withoutComments(svg: String): String = svg
        .replace(XML_COMMENT, "")
        .replace(CSS_COMMENT, "")

    private fun toMilliseconds(match: MatchResult): Long {
        val value = match.groupValues[1].toDouble()
        return if (match.groupValues[2].equals("ms", ignoreCase = true)) {
            value.toLong()
        } else {
            (value * 1_000).toLong()
        }
    }

    private val ANIMATION_DECLARATION = Regex(
        """(?i)\banimation(?:-duration)?\s*:\s*([^;}\"']*)""",
    )
    private val TIME = Regex("""(?i)(\d+(?:\.\d+)?|\.\d+)\s*(ms|s)\b""")
    private val XML_COMMENT = Regex("""<!--[\s\S]*?-->""")
    private val CSS_COMMENT = Regex("""/\*[\s\S]*?\*/""")
}
