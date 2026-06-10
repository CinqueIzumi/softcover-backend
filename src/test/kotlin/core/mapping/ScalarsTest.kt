package nl.rhaydus.core.mapping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScalarsTest {
    @Test
    fun `roundRating returns null for null input`() {
        assertNull(roundRating(null))
    }

    @Test
    fun `roundRating rounds to one decimal place`() {
        assertEquals(3.1, roundRating(3.14))
        assertEquals(3.2, roundRating(3.15))
        assertEquals(4.0, roundRating(3.96))
    }

    @Test
    fun `roundRating leaves already-rounded values unchanged`() {
        assertEquals(0.0, roundRating(0.0))
        assertEquals(5.0, roundRating(5.0))
        assertEquals(4.3, roundRating(4.3))
    }

    @Test
    fun `releaseYearOrSentinel returns the year when present`() {
        assertEquals(2026, releaseYearOrSentinel(2026))
        assertEquals(0, releaseYearOrSentinel(0))
    }

    @Test
    fun `releaseYearOrSentinel returns -1 for null`() {
        assertEquals(-1, releaseYearOrSentinel(null))
    }

    @Test
    fun `passthroughDate returns the value untouched`() {
        assertEquals("2026-05-04", passthroughDate("2026-05-04"))
        assertEquals("2026-05-04T06:20:37.939189+00:00", passthroughDate("2026-05-04T06:20:37.939189+00:00"))
    }

    @Test
    fun `passthroughDate returns null for null`() {
        assertNull(passthroughDate(null))
    }
}
