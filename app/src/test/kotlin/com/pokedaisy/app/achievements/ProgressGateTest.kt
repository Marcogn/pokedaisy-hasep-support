package com.pokedaisy.app.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Progress pop-ups only for new progress ([ProgressGate]): a measured value that jumps and falls back in a wild battle's start
 * (Emerald, LeafGreen: pop-ups for unrelated achievements in every battle) shows at most once. */
class ProgressGateTest {
    @Test
    fun aSpikeAgainAndAgainShowsNothingNew() {
        val g = ProgressGate()
        g.seen(1, "0/150")
        assertTrue(g.shows(1, "3/150"))
        // Every battle after: the same read again, or less.
        repeat(5) { assertFalse(g.shows(1, "3/150")) }
        assertFalse(g.shows(1, "1/150"))
        // Real progress past it still shows.
        assertTrue(g.shows(1, "4/150"))
    }

    @Test
    fun theListsValuesCountAsHad() {
        val g = ProgressGate()
        g.seen(7, "20/30")
        assertFalse(g.shows(7, "12/30"))
        assertFalse(g.shows(7, "20/30"))
        assertTrue(g.shows(7, "21/30"))
    }

    @Test
    fun resetStartsOver() {
        val g = ProgressGate()
        assertTrue(g.shows(2, "50%"))
        assertFalse(g.shows(2, "50%"))
        g.reset()
        assertTrue(g.shows(2, "50%"))
    }

    @Test
    fun unreadableProgressStillShows() {
        val g = ProgressGate()
        assertTrue(g.shows(3, "Level 5"))
        assertTrue(g.shows(3, "Level 5"))
        assertTrue(g.shows(3, null))
    }

    @Test
    fun fractions() {
        assertEquals(0.5, progressFraction("1,000/2,000")!!, 1e-9)
        assertEquals(0.45, progressFraction("45%")!!, 1e-9)
        assertEquals(0.25, progressFraction("1 / 4")!!, 1e-9)
        assertNull(progressFraction("3/0"))
        assertNull(progressFraction(""))
        assertNull(progressFraction("abc"))
    }
}
