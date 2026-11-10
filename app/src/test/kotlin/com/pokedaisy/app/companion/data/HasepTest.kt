package com.pokedaisy.app.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Hasep's own table of addresses (Hasep.kt) and a capture of a Hasep build read through it. */
class HasepTest {
    @Before fun setGame() { activeGame = GameKind.HEART_AND_SOUL }
    @After fun resetGame() { activeGame = GameKind.FIRERED }

    private val head: ByteArray = javaClass.classLoader!!.getResourceAsStream("fixtures/hasep/rom_head.bin")!!.readBytes()

    /** The fixture's ROM head; the rest of the ROM (code for the CRCs) reads as zeros. */
    private val rom = { addr: Long, len: Int ->
        val off = (addr - 0x08000000L).toInt()
        ByteArray(len) { i -> head.getOrElse(off + i) { 0 } }
    }

    @Test
    fun `the table parses and names what the configs need`() {
        val s = HasepSymbols.parse(rom)!!

        assertEquals(HasepSymbols.LAYOUT_VERSION, s.layoutVersion)
        assertEquals(82, s.size)
        assertEquals(0x020347DCL, s["gPlayerParty"])
        assertEquals(0x030041C0L, s["gSaveBlock1Ptr"])
        assertEquals(0x0805E441L, s["HandleInputChooseAction"])   // Thumb bit kept, like the game's pointers
        assertEquals(0x0805E440L, s.code("HandleInputChooseAction"))
        assertEquals(0x4021L, s["VAR_REPEL_STEP_COUNT"])
        // Every name hasepConfig / hasepFieldItems / hasepFlash asks for is there.
        hasepConfig(s, rom)
        hasepFlash(s, rom)
    }

    @Test
    fun `EXPAND's row is Heart and Soul's with the table's addresses`() {
        // The row checked headless (mgba_dump widerow + widecheck): the bedroom 59 of 60 frames exact, Cherrygrove
        // 203 of 204, the first frame after a loaded state aside.
        val row = hasepWideRow(HasepSymbols.parse(rom)!!)!!
        assertEquals(
            "3003054,2001d18,93097b8,30041c0,3000bd4,2001800,203a88c,203a406,203a408,203a3c4,3005bc0,3001fc9,852f8e0," +
                "8526898,8523300,0,852349c,0,3014,10,3ff,280,c,0,0,ffff,0,1abc138c,4,1,224,0,0,0,0,0",
            row.joinToString(",") { Integer.toHexString(it) },
        )
    }

    @Test
    fun `an older table without EXPAND's names gets no row`() {
        val older = HasepSymbols(mapOf("gPlayerParty" to 0x020347DCL), HasepSymbols.LAYOUT_VERSION)
        assertNull(hasepWideRow(older))
    }

    @Test
    fun `no table, no Hasep`() {
        assertNull(HasepSymbols.parse { _, len -> ByteArray(len) })
        // A newer layout than this app knows is refused rather than read wrong.
        val newer = head.copyOf().also { it[0x21C + 10] = 2 }
        assertNull(HasepSymbols.parse { addr, len -> newer.copyOfRange((addr - 0x08000000L).toInt(), (addr - 0x08000000L).toInt() + len) })
    }

    @Test
    fun `party, bag and location decode through the table`() {
        // A new game in the bedroom with a BULBASAUR Lv5 from the debug menu (the fixture's README.txt).
        val t = decodeNative("hasep", hasepConfig(HasepSymbols.parse(rom)!!, rom))
        assertEquals(1, t.party.size)
        val mon = t.party[0]
        assertEquals(1, mon.species)
        assertEquals("BULBASAUR", speciesNamesHns[mon.species])
        assertEquals(5, mon.level)
        assertEquals(20, mon.hp)
        assertEquals(20, mon.maxHp)
        assertPocketsInRange(t)
        assertEquals(0x3F, t.regionMapSectionId)
        assertEquals("New Bark Town", mapSecDataHns.getValue(t.regionMapSectionId).name)
        assertEquals(false, t.inBattle)
    }
}

class HasepBattleTest {
    private val head: ByteArray = javaClass.classLoader!!.getResourceAsStream("fixtures/hasep/rom_head.bin")!!.readBytes()
    private val rom = { addr: Long, len: Int ->
        val off = (addr - 0x08000000L).toInt()
        ByteArray(len) { i -> head.getOrElse(off + i) { 0 } }
    }

    @Test
    fun `a trainer battle decodes through the table`() {
        // Falkner's PIDGEY from the debug menu's Try Battle (fixtures/hasep_battle/README.txt).
        val s = HasepSymbols.parse(rom)!!
        val t = decodeNative("hasep_battle", hasepConfig(s, rom))
        assertEquals(true, t.inBattle)
        assertEquals(false, t.isDoubleBattle)
        val player = t.battleMons[0]
        assertEquals(1, player.species)
        assertEquals(5, player.level)
        assertEquals(13, player.type1)        // Grass
        assertEquals(4, player.type2)         // Poison
        assertEquals(20, player.maxHp)
        assertEquals(listOf(33, 45, 0, 0), player.moves.toList())   // Tackle, Growl
        val foe = t.battleMons[1]
        assertEquals(16, foe.species)
        assertEquals("PIDGEY", speciesNamesHns[foe.species])
        assertEquals(8, foe.level)
        assertEquals(1, foe.type1)            // Normal
        assertEquals(3, foe.type2)            // Flying
        assertEquals(25, foe.hp)
        // The player's controller is waiting in the move menu: the exported function, Thumb bit and all.
        val fixture = FixtureMemoryReader.load("hasep_battle")
        val fn = fixture.readCoreMemory(s["gBattlerControllerFuncs"], 4).let { b -> (0..3).fold(0L) { v, i -> v or ((b[i].toLong() and 0xFF) shl (8 * i)) } }
        assertEquals(s["HandleInputChooseMove"], fn)
    }
}
