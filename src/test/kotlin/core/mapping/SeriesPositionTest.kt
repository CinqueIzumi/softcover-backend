package nl.rhaydus.core.mapping

import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesPositionTest {
    @Test
    fun `single whole number`() {
        assertEquals(listOf(1.0), parseSeriesPositions("1", null))
    }

    @Test
    fun `single fractional number`() {
        assertEquals(listOf(1.5), parseSeriesPositions("1.5", null))
    }

    @Test
    fun `whole-number range is expanded inclusively`() {
        assertEquals(listOf(1.0, 2.0, 3.0), parseSeriesPositions("1-3", null))
    }

    @Test
    fun `whole-number range with equal endpoints yields a single value`() {
        assertEquals(listOf(2.0), parseSeriesPositions("2-2", null))
    }

    @Test
    fun `fractional range keeps only the endpoints`() {
        assertEquals(listOf(1.5, 2.5), parseSeriesPositions("1.5-2.5", null))
    }

    @Test
    fun `mixed whole and fractional range keeps only the endpoints`() {
        assertEquals(listOf(1.0, 2.5), parseSeriesPositions("1-2.5", null))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(listOf(1.0, 2.0, 3.0), parseSeriesPositions(" 1 - 3 ", null))
    }

    @Test
    fun `null details falls back to position`() {
        assertEquals(listOf(4.0), parseSeriesPositions(null, 4.0))
    }

    @Test
    fun `null details and no position yields empty`() {
        assertEquals(emptyList(), parseSeriesPositions(null, null))
    }

    @Test
    fun `blank details falls back to position`() {
        assertEquals(listOf(7.0), parseSeriesPositions("   ", 7.0))
    }

    @Test
    fun `unparseable details falls back to position`() {
        assertEquals(listOf(4.0), parseSeriesPositions("abc", 4.0))
    }

    @Test
    fun `unparseable details with no position yields empty`() {
        assertEquals(emptyList(), parseSeriesPositions("abc", null))
    }

    @Test
    fun `malformed range where end is less than start falls back to position`() {
        assertEquals(listOf(9.0), parseSeriesPositions("3-1", 9.0))
    }

    @Test
    fun `malformed range with no position yields empty`() {
        assertEquals(emptyList(), parseSeriesPositions("3-1", null))
    }

    @Test
    fun `too many range parts falls back to position`() {
        assertEquals(listOf(5.0), parseSeriesPositions("1-3-5", 5.0))
    }
}
