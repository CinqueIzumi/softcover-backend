package nl.rhaydus.core.mapping

import kotlin.math.roundToInt

fun roundRating(rating: Double?): Double? {
    if (rating == null) return null

    return (rating * 10).roundToInt() / 10.0
}

fun releaseYearOrSentinel(year: Int?): Int = year ?: -1

fun passthroughDate(value: String?): String? = value
