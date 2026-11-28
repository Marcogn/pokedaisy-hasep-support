package com.pokedaisy.app

/**
 * A DS game at 1x on a device too slow to finish its frames in time: rather than the whole game slowing down (music,
 * walking and battles at 45 or 50 frames a second), some frames skip their drawing (no 2D lines, no 3D render) and the
 * picture holds the last drawn one, so the game keeps the DS's speed. It draws one frame in [skip] + 1, [skip] the
 * smallest that fits: a drawn frame's time and a skipped one's (averages over the last ones, measured around the core's
 * frame; the skipped one's learned by trying one in two the first time the drawn ones run over) shared over the run must fit [budgetNanos] (within [REACH]). Nothing is skipped on a device that keeps
 * up, nor on one so slow that even [maxSkip] wouldn't bring the speed back.
 *
 * Skipping starts only once the game really falls behind: its frames come further apart than the DS's ([frameNanos],
 * [BEHIND]) for [RAISE_FRAMES] in a row. The core's time alone over [budgetNanos] isn't enough: a Thor's battles run at
 * 14-16 ms a frame and still keep the DS's 60 frames a second, and skipping there halved the picture at every attack.
 *
 * Fast-forward has its own pattern (EmulatorEngine's groups); this is for 1x only. Drawing is promised a frame ahead
 * (the next frame's 3D starts at this one's line 215: MgbaCore.pkNdsSetDraw's drawNext), so [plan] gives both.
 * Measured headless (HeartGold walking, the Mac): drawing one frame in 2 saves ~18% of the frames' time, one in 4 ~28%.
 */
internal class DsFrameSkip(
    private val budgetNanos: Long = DEFAULT_BUDGET_NANOS,
    private val maxSkip: Int = MAX_SKIP,
    private val frameNanos: Long = DS_FRAME_NANOS,
) {
    /** Frames skipped after each drawn one, now. */
    var skip = 0
        private set

    private var nextDraw = true   // what the last frame promised this one
    private var streak = 0        // skipped frames in a row, up to this one
    private var drawnAvg = 0.0    // nanos, a drawn frame
    private var skippedShare = 0.0   // a skipped frame's time over a drawn one's (0 until one was measured)
    private var lower = 0         // frames in a row that wanted fewer skips
    private var higher = 0        // frames in a row that wanted more skips and were behind
    private var lastStart = 0L    // nanoTime the last frame's core started (0 = none since a reset)
    // Nanos from one frame's start to the next. Starts at a DS frame: seeded from the first gap after a start or a reset
    // (the core's boot, a state load: up to 4 frames), it read as behind for seconds and skipped a heavy scene for nothing.
    private var periodAvg = frameNanos.toDouble()

    /** Before a frame: whether it is drawn, and whether the next one will be. */
    fun plan(): Pair<Boolean, Boolean> {
        val draw = nextDraw
        streak = if (draw) 0 else streak + 1
        nextDraw = streak + 1 > skip
        return draw to nextDraw
    }

    /** After a frame: when its core started and how long it took, and whether [plan] had it drawn. */
    fun frameDone(startNanos: Long, nanos: Long, drawn: Boolean) {
        if (lastStart != 0L) {
            // Capped: a pause (the screen off, a held transition) mustn't read as the game falling behind.
            val period = (startNanos - lastStart).coerceIn(0L, frameNanos * 4).toDouble()
            periodAvg += (period - periodAvg) / PERIOD_OVER
        }
        lastStart = startNanos
        if (drawn) drawnAvg = if (drawnAvg == 0.0) nanos.toDouble() else drawnAvg + (nanos - drawnAvg) / AVERAGE_OVER
        else if (drawnAvg > 0.0) {
            // Kept as a share of a drawn frame: the scene's weight changes both, the share much less.
            val share = nanos / drawnAvg
            skippedShare = if (skippedShare == 0.0) share else skippedShare + (share - skippedShare) / AVERAGE_OVER
        }
        val want = wanted()
        // More skips only once the game has fallen behind for a second: a battle's frames over the budget while the
        // device still keeps the DS's speed halved the picture for nothing (Black 2 on a Thor: 30 frames a second for
        // half a second at every attack). Fewer once the frames fit again, as before.
        val behind = periodAvg > frameNanos * BEHIND
        if (want > skip) { lower = 0; if (!behind) higher = 0 else if (++higher >= RAISE_FRAMES) { skip = want; higher = 0 } }
        else if (want < skip) { higher = 0; if (++lower >= SETTLE_FRAMES) { skip = want; lower = 0 } }   // ahead: once it holds
        else { lower = 0; higher = 0 }
    }

    /** For the log: the averages it decides from. */
    fun describe(): String = "drawn %.1f ms, skipped %s".format(
        drawnAvg / 1e6, if (skippedShare > 0.0) "%.0f%% of that".format(skippedShare * 100) else "not measured yet",
    )

    /** Back to drawing every frame (fast-forward, a held transition, another game): measurements kept. */
    fun reset() {
        nextDraw = true
        streak = 0
        skip = 0
        lower = 0
        higher = 0
        lastStart = 0L
        periodAvg = frameNanos.toDouble()
    }

    private fun wanted(): Int {
        if (drawnAvg <= budgetNanos) return 0
        // Never skipped yet: one in two, to learn what a skipped frame costs here (half a second at least: SETTLE_FRAMES).
        if (skippedShare == 0.0) return 1
        val skipped = drawnAvg * skippedShare
        for (k in 1..maxSkip) if ((drawnAvg + k * skipped) / (k + 1) <= budgetNanos * REACH) return k
        // Too slow for skipping to bring back the DS's speed (a 32-bit device, no JIT: 52 ms a frame, ~40 skipped): a
        // game a quarter faster showing one frame in four looks worse than a smooth slow one, so everything is drawn.
        return 0
    }

    companion object {
        /** The DS's frame (59.83 a second) less room for the rest of the loop (audio, the companion's sample). */
        val DEFAULT_BUDGET_NANOS = (1_000_000_000.0 / EmulatorEngine.NDS_FPS * 0.85).toLong()
        /** At most 3 skipped after each drawn one: the picture never drops under ~15 frames a second. */
        const val MAX_SKIP = 3
        const val AVERAGE_OVER = 16.0
        /** Half a second of frames that would fit with fewer skips before taking one away (no flicking back and forth). */
        const val SETTLE_FRAMES = 30
        /** A DS frame (59.83 a second). */
        val DS_FRAME_NANOS = (1_000_000_000.0 / EmulatorEngine.NDS_FPS).toLong()
        /** Behind: frames 5% further apart than the DS's (under ~57 a second). */
        const val BEHIND = 1.05
        /** The frames' spacing averaged over about a second. */
        const val PERIOD_OVER = 60.0
        /** A second of frames behind before skipping more: a moment's peak passes without a skip. */
        const val RAISE_FRAMES = 60
        /** How near the budget a skip must bring the frames to be worth it: within 10% of the DS's speed. */
        const val REACH = 1.1
    }
}
