package com.luc.body.sprite

object SvgMarkupNormalizer {
    fun normalize(source: String): String = source.replaceFirst(LOWERCASE_VIEWBOX, "viewBox")

    private val LOWERCASE_VIEWBOX = Regex("""\bviewbox(?=\s*=)""")
}
