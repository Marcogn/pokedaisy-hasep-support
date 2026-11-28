package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test

/**
 * Retail FireRed 1.0 (rev 0)'s DEX and GUIDE tables, read from the cart (skipped when it isn't on this machine): the
 * same data as rev 1's ([FireRedGuideRomTest]) at its own addresses. Without them a rev 0 cart had no DEX, no GUIDE
 * HERE / NEXT BOSS and no HUD WILD / REPEL STEPS (the save's vars are read with the guide tables).
 */
class FireRedRev0TablesTest {
    private val t = GUIDE_TABLES_FIRERED_REV0
    private lateinit var rom: RomFileReader

    @Before
    fun load() {
        val r = RomFileReader.load(
            System.getProperty("fireredRev0Rom") ?: (RETAIL_ROM_DIR + "Pokemon - FireRed Version (USA).gba"),
        )
        assumeNotNull(r)
        rom = r!!
        activeGame = GameKind.FIRERED
        PokedexSource.reader = rom
    }

    @Test fun retailConfigCarriesThem() {
        assertSame(GUIDE_TABLES_FIRERED_REV0, NATIVE_FIRERED_REV0_RETAIL.guideTables)
        assertSame(POKEDEX_FIRERED_REV0, NATIVE_FIRERED_REV0_RETAIL.pokedex)
    }

    @Test fun tablesMatchTheRom() {
        assertTrue(guideTablesMatchRom(rom, t))
        assertTrue(pokedexMatchesRom(rom, POKEDEX_FIRERED_REV0))
    }

    /** Route 24's grass and water, as rev 1's. */
    @Test fun route24() {
        val e = GuideRomSource.encounters(t, 3, 43)!!
        assertEquals(
            listOf(
                EncounterSlot(43, 12, 14, 25), EncounterSlot(13, 7, 7, 20), EncounterSlot(10, 7, 7, 20),
                EncounterSlot(16, 11, 13, 15), EncounterSlot(63, 8, 12, 15), EncounterSlot(14, 8, 8, 4),
                EncounterSlot(11, 8, 8, 1),
            ),
            e.grass,
        )
        assertEquals(listOf(EncounterSlot(72, 5, 40, 100)), e.water)
    }

    @Test fun brock() = assertEquals(
        listOf(TrainerMon(74, 12, 0, listOf(33, 111)), TrainerMon(95, 14, 0, listOf(33, 20, 317))),
        GuideRomSource.party(t, 414),
    )

    /** The TM / HM move list starts with FOCUS PUNCH, DRAGON CLAW, WATER PULSE. */
    @Test fun tmMoves() {
        val b = rom.readCoreMemory(POKEDEX_FIRERED_REV0.tmhmMoves, 6)
        assertEquals(listOf(264, 337, 352), (0 until 3).map { u16le(b, it * 2) })
    }
}
