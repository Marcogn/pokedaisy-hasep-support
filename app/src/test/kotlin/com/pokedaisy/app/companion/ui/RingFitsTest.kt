package com.pokedaisy.app.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QUICK MENU's RING: shown only where its tiles don't run into each other, else the grid (GitHub #1225). */
class RingFitsTest {
    // A labelled tile and the ring's gap on a 1080p screen (Paparazzi's QuickMenuScreenshotTest).
    private val w = 170f
    private val h = 143f
    private val gap = 30f

    @Test fun fitsOnABigScreen() {
        for (n in 2..16) assertTrue("n=$n", ringFits(n, w, h, gap, 1843f, 1037f))
    }

    @Test fun aFewTilesFitASmallScreen() {
        for (n in 2..5) assertTrue("n=$n", ringFits(n, w, h, gap, 1037f, 576f))
    }

    @Test fun manyTilesOnASmallScreenDont() {
        for ((limW, limH) in listOf(1037f to 576f, 768f to 432f, 1100f to 380f)) assertFalse("${limW}x$limH", ringFits(15, w, h, gap, limW, limH))
    }
}
