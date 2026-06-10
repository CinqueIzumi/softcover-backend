package nl.rhaydus.core.mapping

import kotlin.math.floor

// Series positions from the first book_series entry's `details` string, falling back
// to its numeric `position` (§6.1):
//   "1"        -> [1.0]
//   "1-3"      -> [1.0, 2.0, 3.0]   (whole-number range, expanded)
//   "1.5-2.5"  -> [1.5, 2.5]        (fractional range, endpoints only)
//   empty/unparseable -> [position] if present, else []
//   end < start (malformed) -> [position] / []
fun parseSeriesPositions(details: String?, position: Double?): List<Double> {
    val fallback = position?.let { listOf(it) } ?: emptyList()

    val trimmed = details?.trim()
    if (trimmed.isNullOrEmpty()) return fallback

    val parts = trimmed.split("-")
    return when (parts.size) {
        1 -> {
            val value = parts[0].trim().toDoubleOrNull() ?: return fallback
            listOf(value)
        }

        2 -> {
            val start = parts[0].trim().toDoubleOrNull() ?: return fallback
            val end = parts[1].trim().toDoubleOrNull() ?: return fallback
            if (end < start) return fallback

            // Expand only when both endpoints are whole numbers; otherwise keep the
            // two endpoints as-is (a fractional range is not enumerable).
            if (start.isWholeNumber() && end.isWholeNumber()) {
                (start.toInt()..end.toInt()).map { it.toDouble() }
            } else {
                listOf(start, end)
            }
        }

        else -> fallback
    }
}

private fun Double.isWholeNumber(): Boolean = this == floor(this)
