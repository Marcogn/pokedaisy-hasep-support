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

/** SHINY ODDS and the FOE TEAM on a Hasep build whose table has gEnemyParty (2.0.6.PLUS.11 on). */
class HasepShinyTest {
    private val head: ByteArray = javaClass.classLoader!!.getResourceAsStream("fixtures/hasep_wild/rom_head.bin")!!.readBytes()
    private val rom = { addr: Long, len: Int ->
        val off = (addr - 0x08000000L).toInt()
        ByteArray(len) { i -> head.getOrElse(off + i) { 0 } }
    }

    private class Ram(src: MemoryReader) {
        val ewram: ByteArray = src.readCoreMemory(0x02000000L, 0x40000)
        val iwram: ByteArray = src.readCoreMemory(0x03000000L, 0x8000)
        private fun at(addr: Long) = if (addr >= 0x03000000L) iwram to (addr - 0x03000000L).toInt() else ewram to (addr - 0x02000000L).toInt()
        fun read(addr: Long, size: Int): ByteArray { val (b, o) = at(addr); return b.copyOfRange(o, o + size) }
        fun write(addr: Long, bytes: ByteArray) { val (b, o) = at(addr); bytes.copyInto(b, o) }
    }

    @Test
    fun `a new wild foe is made shiny the way the game reads it`() {
        // A PIKACHU Lv5 just made by setwildbattle, no battle yet (fixtures/hasep_wild/README.txt).
        val s = HasepSymbols.parse(rom)!!
        val cfg = hasepConfig(s, rom)
        assertEquals(s["gEnemyParty"], cfg.enemyParty)
        assertEquals(8, cfg.shinyBelow)
        val target = ShinyOdds.targetFor(cfg, national = true)!!
        val ram = Ram(FixtureMemoryReader.load("hasep_wild"))
        val before = com.pokedaisy.app.companion.data.save.Gen3BoxMon.read(ram.read(target.enemyParty, 80), 0)
        assertEquals(true, before.checksumOk)
        assertEquals(25, before.species and 0x7FF)   // species:11 | teraType:5
        // Odds of 1 in 1, so the roll always rewrites; two frames, as on the device.
        val roller = ShinyOdds.Roller(target, java.util.Random(1))
        val pokes = StringBuilder()
        val writer = { a: Long, bytes: ByteArray ->
            for (i in bytes.indices) pokes.append("poke8 0x%08X 0x%02X\n".format(a + i, bytes[i].toInt() and 0xFF))
            ram.write(a, bytes); true
        }
        roller.tick(1, ram::read, writer)
        roller.tick(1, ram::read, writer)
        val after = com.pokedaisy.app.companion.data.save.Gen3BoxMon.read(ram.read(target.enemyParty, 80), 0)
        assertEquals(true, after.checksumOk)
        assertEquals(25, after.species and 0x7FF)
        val shinyValue = ((after.otId ushr 16) xor (after.otId and 0xFFFF) xor (after.personality ushr 16) xor (after.personality and 0xFFFF))
        assertEquals(true, shinyValue < 8)
        assertEquals(before.personality % 25, after.personality % 25)   // the nature kept
        // For the headless check: these pokes, then the game's own GetMonData(foe, MON_DATA_IS_SHINY).
        java.io.File("build/hasep-shiny-pokes.txt").writeText(pokes.toString())
    }
}

class HasepFoeTeamTest {
    private val head: ByteArray = javaClass.classLoader!!.getResourceAsStream("fixtures/hasep_wild/rom_head.bin")!!.readBytes()
    private val rom = { addr: Long, len: Int ->
        val off = (addr - 0x08000000L).toInt()
        ByteArray(len) { i -> head.getOrElse(off + i) { 0 } }
    }

    @Before fun setGame() { activeGame = GameKind.HEART_AND_SOUL }
    @After fun resetGame() { activeGame = GameKind.FIRERED }

    @Test
    fun `a trainer battle shows the foe's whole team`() {
        // Falkner from the debug menu (fixtures/hasep_trainer/README.txt): PIDGEY Lv8 out, NOCTOWL behind it.
        val t = decodeNative("hasep_trainer", hasepConfig(HasepSymbols.parse(rom)!!, rom))
        assertEquals(true, t.isTrainerBattle)
        assertEquals(listOf(16, 164), t.enemyParty.map { it.species })
        assertEquals("NOCTOWL", speciesNamesHns[t.enemyParty[1].species])
        assertEquals(0, t.enemyActive)
        assertEquals(false, t.enemyParty[0].shiny)
    }
}

class HasepRepelTest {
    private val head: ByteArray = javaClass.classLoader!!.getResourceAsStream("fixtures/hasep_wild/rom_head.bin")!!.readBytes()
    private val rom = { addr: Long, len: Int ->
        val off = (addr - 0x08000000L).toInt()
        ByteArray(len) { i -> head.getOrElse(off + i) { 0 } }
    }

    @Test
    fun `the bag offers the Repels, and nothing else`() {
        // Checked headless on this build: AddBagItem(114), then the calls useFieldItem makes - VarSet(0x4021, 100: gItemsInfo
        // [114]'s holdEffectParam), RemoveBagItem - the bag back to none, the counter 99 after a step.
        val fns = hasepFieldItems(HasepSymbols.parse(rom)!!, rom)
        assertEquals(setOf(114, 115, 116), fns.repelItems)
        assertEquals(0x8000, fns.repelLureBit)
        assertEquals(0x1D, fns.itemParamOff)
        assertEquals(0x2C, fns.itemSize)
        val support = fieldItemSupport(fns, rom)!!
        assertEquals(true, support.canUse(114))
        assertEquals(true, support.canUse(116))
        assertEquals(false, support.canUse(28))   // POTION: no USE on a Pokémon in the expansion hacks
        assertEquals(false, support.canTake)
        // A Lure's steps aren't a Repel's: the bit set means none left for a Repel.
        assertEquals(0, fns.repelSteps(0x8000 or 50))
        assertEquals(50, fns.repelSteps(50))
    }
}

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
        assertEquals(true, com.pokedaisy.app.CompanionSupport.isSupported(rom(head)))
    }

    @Test
    fun `the same size of BPEE hack without the table is not`() {
        assertEquals(false, com.pokedaisy.app.CompanionSupport.isSupported(rom(ByteArray(head.size))))
    }
}
