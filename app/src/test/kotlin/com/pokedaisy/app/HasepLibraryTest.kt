package com.pokedaisy.app

import org.junit.Assert.assertEquals
import org.junit.Test

// The Hasep fork (docs/HASEP_FORK.md). Apart from HasepTest.kt: it uses the app's own CompanionSupport, which the
// shared module (companion/data's tests, Android + iOS) can't see.

/** The library's verdict (CompanionSupport, which the library tags read) takes Hasep by its table as detect() does: a
 * player's Hasep ROM was tagged NOT SUPPORTED while it played with its companion. */
class HasepLibraryTest {
    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()

    private val head: ByteArray = javaClass.classLoader!!.getResourceAsStream("fixtures/hasep/rom_head.bin")!!.readBytes()

    /** A 32 MB BPEE file (sparse) with [head] at its start: the fixture's head has no Nintendo header, so the game code
     * is written in. */
    private fun rom(head: ByteArray): java.io.File = tmp.newFile().also { f ->
        java.io.RandomAccessFile(f, "rw").use { r ->
            r.write(head.copyOf().also { "BPEE".toByteArray(Charsets.US_ASCII).copyInto(it, 0xAC) })
            r.setLength(0x2000000L)
        }
    }

    @Test
    fun `a Hasep ROM is supported in the library`() {
        assertEquals(true, CompanionSupport.isSupported(rom(head)))
    }

    @Test
    fun `the same size of BPEE hack without the table is not`() {
        assertEquals(false, CompanionSupport.isSupported(rom(ByteArray(head.size))))
    }
}
