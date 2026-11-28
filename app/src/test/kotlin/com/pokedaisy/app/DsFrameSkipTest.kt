package com.pokedaisy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DsFrameSkipTest {
    private val budget = 14_000_000L

    private val frame = 16_710_000L
    private var clock = 1_000_000_000L

    /** Runs [frames] frames: a drawn one costs [drawn] ns of core time, a skipped one [skipped], and the rest of the loop
     * [rest]; a frame starts a DS frame after the last one, or as soon as the last one is done when it ran over. The
     * draw pattern. */
    private fun DsFrameSkip.run(frames: Int, drawn: Long, skipped: Long, rest: Long = 2_000_000L): List<Boolean> {
        val seen = mutableListOf<Boolean>()
        var promised: Boolean? = null
        repeat(frames) {
            val (draw, next) = plan()
            promised?.let { assertEquals("a frame is drawn as the one before promised", it, draw) }
            promised = next
            seen += draw
            val core = if (draw) drawn else skipped
            frameDone(clock, core, draw)
            clock += maxOf(frame, core + rest)
        }
        return seen
    }

    @Test fun aDeviceThatKeepsUpDrawsEveryFrame() {
        val s = DsFrameSkip(budget, frameNanos = frame)
        val drawn = s.run(600, drawn = 9_000_000L, skipped = 6_000_000L)
        assertTrue(drawn.all { it })
        assertEquals(0, s.skip)
    }

    @Test fun aSlowDeviceDrawsOneFrameInTwo() {
        // 16 ms drawn, 11 ms skipped: one in two averages 13.5 ms, under the 14 ms budget.
        val s = DsFrameSkip(budget, frameNanos = frame)
        val drawn = s.run(600, drawn = 16_000_000L, skipped = 11_000_000L)
        assertEquals(1, s.skip)
        val tail = drawn.takeLast(100)
        assertEquals(50, tail.count { it })
    }

    @Test fun aSlowerDeviceSkipsThree() {
        // 22 ms drawn, 12.5 skipped: one in three averages 15.7 ms (over the 15.4 ms reach), one in four 14.9.
        val s = DsFrameSkip(budget, frameNanos = frame)
        val drawn = s.run(600, drawn = 22_000_000L, skipped = 12_500_000L)
        assertEquals(DsFrameSkip.MAX_SKIP, s.skip)
        assertEquals(25, drawn.takeLast(100).count { it })
    }

    @Test fun aDeviceSkippingCantSaveDrawsEveryFrame() {
        // The Galaxy Tab A (32-bit, interpreted): 52 ms drawn, ~40 skipped - three in four skipped would still be
        // 43 ms, far from the DS's speed, for a picture at a quarter of the frames. Better smooth and slow.
        val s = DsFrameSkip(budget, frameNanos = frame)
        val drawn = s.run(600, drawn = 52_000_000L, skipped = 40_000_000L)
        assertEquals(0, s.skip)
        assertTrue(drawn.takeLast(100).all { it })
    }

    @Test fun fewerSkipsOnlyOnceItHolds() {
        val s = DsFrameSkip(budget, frameNanos = frame)
        s.run(300, drawn = 16_000_000L, skipped = 11_000_000L)
        assertEquals(1, s.skip)
        // The scene gets lighter: a few frames aren't enough to stop skipping...
        s.run(10, drawn = 9_000_000L, skipped = 6_000_000L)
        assertEquals(1, s.skip)
        // ...half a second of them is.
        s.run(200, drawn = 9_000_000L, skipped = 6_000_000L)
        assertEquals(0, s.skip)
    }

    @Test fun aDeviceThatKeepsUpThroughHeavyFramesDrawsEveryFrame() {
        // A battle on a Thor: 15-16 ms of core time a frame for seconds at a time, over the budget, yet the frames
        // still come a DS frame apart. Nothing is skipped.
        val s = DsFrameSkip(budget, frameNanos = frame)
        val drawn = mutableListOf<Boolean>()
        repeat(6) {
            drawn += s.run(300, drawn = 16_000_000L, skipped = 11_000_000L, rest = 500_000L)
            drawn += s.run(120, drawn = 12_000_000L, skipped = 8_000_000L)
        }
        assertTrue(drawn.all { it })
        assertEquals(0, s.skip)
    }

    @Test fun skipsOnlyOnceItFallsBehindForASecond() {
        val s = DsFrameSkip(budget, frameNanos = frame)
        // 18 ms a frame (16 + 2): behind. The spacing's average needs a moment to get there, then a second of it.
        s.run(30, drawn = 16_000_000L, skipped = 11_000_000L)
        assertEquals(0, s.skip)
        s.run(150, drawn = 16_000_000L, skipped = 11_000_000L)
        assertEquals(1, s.skip)
    }

    @Test fun aPauseIsNotFallingBehind() {
        val s = DsFrameSkip(budget, frameNanos = frame)
        s.run(60, drawn = 15_000_000L, skipped = 10_000_000L, rest = 500_000L)
        clock += 2_000_000_000L   // the screen off for two seconds
        s.run(120, drawn = 15_000_000L, skipped = 10_000_000L, rest = 500_000L)
        assertEquals(0, s.skip)
    }

    @Test fun aLongFirstGapAfterAResetIsNotFallingBehind() {
        // A game starting or a state loading: the first frame comes 4 frames late, then the frames are heavy (a battle at
        // 4x on a Thor) but still a DS frame apart. Nothing is skipped.
        val s = DsFrameSkip(budget, frameNanos = frame)
        s.run(60, drawn = 15_000_000L, skipped = 10_000_000L, rest = 500_000L)
        s.reset()
        clock += frame * 4
        val drawn = s.run(300, drawn = 16_000_000L, skipped = 11_000_000L, rest = 500_000L)
        assertTrue(drawn.all { it })
        assertEquals(0, s.skip)
    }

    @Test fun resetDrawsTheNextFrame() {
        val s = DsFrameSkip(budget, frameNanos = frame)
        s.run(300, drawn = 20_000_000L, skipped = 12_000_000L)
        s.reset()
        assertEquals(0, s.skip)
        assertTrue(s.plan().first)
    }
}
