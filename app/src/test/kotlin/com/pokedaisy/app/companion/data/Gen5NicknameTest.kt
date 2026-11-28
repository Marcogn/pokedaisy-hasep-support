package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Gen 5's ♂ / ♀ are text codes 0x246D / 0x246E (Unicode's ⑭ / ⑮): a party NIDORAN♂ once read "NIDORAN⑭". */
class Gen5NicknameTest {
    /** A Black / White party record (0xDC bytes), its blocks and party fields already plain, blocks in ABCD order. */
    private fun record(species: Int, name: IntArray): ByteArray {
        val r = ByteArray(0xDC)
        fun put16(o: Int, v: Int) { r[o] = v.toByte(); r[o + 1] = (v shr 8).toByte() }
        put16(4, 3) // both parts decrypted
        val blocks = 8
        put16(blocks, species) // block A
        name.forEachIndexed { i, ch -> put16(blocks + 64 + i * 2, ch) } // block C
        put16(blocks + 64 + name.size * 2, 0xFFFF)
        var sum = 0
        for (i in 0 until 0x80 step 2) sum += (r[blocks + i].toInt() and 0xFF) or ((r[blocks + i + 1].toInt() and 0xFF) shl 8)
        put16(6, sum and 0xFFFF)
        r[0x88 + 4] = 5 // level
        put16(0x88 + 6, 20); put16(0x88 + 8, 20) // HP
        return r
    }

    private fun name(s: String, last: Int) = (s.map { it.code } + last).toIntArray()

    @Test
    fun `party nickname - the gender signs are the game's own codes`() {
        val male = decodeGen4PartyMon(record(32, name("NIDORAN", 0x246D)), 0, 649, 0xDC)
        assertEquals("NIDORAN♂", male?.nickname)
        val female = decodeGen4PartyMon(record(29, name("NIDORAN", 0x246E)), 0, 649, 0xDC)
        assertEquals("NIDORAN♀", female?.nickname)
    }

    @Test
    fun `text codes - the signs mapped, everything else UTF-16`() {
        assertEquals('♂', Gen5Text.char(0x246D))
        assertEquals('♀', Gen5Text.char(0x246E))
        assertEquals('é', Gen5Text.char(0xE9))
    }
}
