package com.example.pace.util

import java.text.Normalizer
import java.util.Locale

object SearchTextMatcher {

    data class MatchRange(
        val start: Int,
        val endExclusive: Int
    )

    fun contains(source: String?, query: String): Boolean {
        if (source.isNullOrEmpty()) return false
        val normalizedQuery = normalize(query)
        if (normalizedQuery.text.isEmpty()) return false
        return normalize(source).text.contains(normalizedQuery.text)
    }

    fun findMatchRange(source: String?, query: String): MatchRange? {
        if (source.isNullOrEmpty()) return null

        val normalizedSource = normalize(source)
        val normalizedQuery = normalize(query)
        if (normalizedQuery.text.isEmpty()) return null

        val matchStart = normalizedSource.text.indexOf(normalizedQuery.text)
        if (matchStart < 0) return null

        val matchEnd = matchStart + normalizedQuery.text.length - 1
        return MatchRange(
            start = normalizedSource.indexMap[matchStart],
            endExclusive = normalizedSource.indexMap[matchEnd] + 1
        )
    }

    private fun normalize(source: String): NormalizedText {
        val text = StringBuilder()
        val indexMap = mutableListOf<Int>()

        source.forEachIndexed { index, char ->
            val normalizedChar = Normalizer.normalize(char.toString(), Normalizer.Form.NFKD)
                .lowercase(Locale.ROOT)
            normalizedChar.forEach {
                if (it.isIgnorableHangulFiller()) return@forEach
                text.append(it)
                indexMap.add(index)
            }
        }

        return NormalizedText(
            text = text.toString(),
            indexMap = indexMap
        )
    }

    private data class NormalizedText(
        val text: String,
        val indexMap: List<Int>
    )

    private fun Char.isIgnorableHangulFiller(): Boolean {
        return this == '\u115F' || // Hangul choseong filler
            this == '\u1160' ||    // Hangul jungseong filler
            this == '\u3164'       // Hangul filler
    }
}
