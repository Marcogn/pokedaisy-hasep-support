package com.pokedaisy.app.companion.data

import java.util.zip.CRC32

/**
 * Hasep (Marcogn/pokehns-expansion-plus): a fork of Pokémon Heart and Soul that carries its own addresses. Every build
 * moves the RAM and the code, so instead of a SHA-1 and hand-found addresses per release the ROM has a table the linker
 * fills in: the HASEPSYM header right after the GF / RHH headers, then 48-byte entries `{char name[44]; u32 value}` up to
 * the header's end pointer (the ROM's include/hasep_symbols.h). Functions carry the Thumb bit, like the game's pointers.
 *
 * Everything but the addresses is Heart and Soul 2.0.6's: the ROM pins the struct layouts it shares with HnS behind
 * [LAYOUT_VERSION] (STATIC_ASSERTs in src/hasep_symbols.c), so its configs are [NATIVE_HEART_AND_SOUL] and the HnS
 * records with the addresses swapped, and it runs as [GameKind.HEART_AND_SOUL] (guide, maps, art, bag and party look).
 * The code CRCs the field tools check are taken from the ROM itself: the table, not a known build, vouches for them.
 */
class HasepSymbols(private val values: Map<String, Long>, val layoutVersion: Int) {
    operator fun get(name: String): Long = values[name] ?: throw NoSuchElementException("HASEPSYM has no $name")
    fun has(name: String) = name in values
    /** A Thumb function's address with the Thumb bit cleared (where the records keep code addresses even). */
    fun code(name: String): Long = get(name) and 1L.inv()
    val size get() = values.size

    companion object {
        const val MAGIC = "HASEPSYM"
        const val FORMAT_VERSION = 1
        const val LAYOUT_VERSION = 1
        /** The header sits right after the RHH header; anything past this isn't the table. */
        const val SEARCH_BYTES = 0x1000
        private const val ROM = 0x08000000L

        /** The table of the ROM read through [read], or null when there's none (any other ROM) or it isn't one this
         * build knows ([FORMAT_VERSION] / [LAYOUT_VERSION]: a newer Hasep needs a newer app). */
        fun parse(read: (addr: Long, len: Int) -> ByteArray): HasepSymbols? {
            val head = runCatching { read(ROM, SEARCH_BYTES) }.getOrNull() ?: return null
            val magic = MAGIC.toByteArray(Charsets.US_ASCII)
            val at = (0..head.size - 20 step 4).firstOrNull { i -> magic.indices.all { head[i + it] == magic[it] } } ?: return null
            val format = u16(head, at + 8)
            val layout = u16(head, at + 10)
            val entrySize = u16(head, at + 12)
            val end = u32(head, at + 16)
            if (format != FORMAT_VERSION || entrySize < 8) return null
            val first = ROM + at + 20
            val bytes = end - first
            if (bytes <= 0 || bytes % entrySize != 0L || bytes > 0x10000) return null
            val table = runCatching { read(first, bytes.toInt()) }.getOrNull() ?: return null
            val values = LinkedHashMap<String, Long>()
            for (off in 0 until bytes.toInt() step entrySize) {
                val nameLen = (0 until entrySize - 4).firstOrNull { table[off + it] == 0.toByte() } ?: (entrySize - 4)
                val name = String(table, off, nameLen, Charsets.US_ASCII)
                values[name] = u32(table, off + entrySize - 4)
            }
            return HasepSymbols(values, layout).takeIf { layout == LAYOUT_VERSION }
        }

        private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        private fun u32(b: ByteArray, i: Int) = (0..3).fold(0L) { v, k -> v or ((b[i + k].toLong() and 0xFF) shl (8 * k)) }
    }
}

/** The running ROM's table when it's Hasep (set by Poller.detect, cleared for any other ROM). */
@Volatile var runningHasep: HasepSymbols? = null

/** Heart and Soul's item names and the three Hasep appended after HnS's last (AZURE FLUTE, 900): its
 * include/constants/items.h, appended on purpose so no other id moved. */
val itemNamesHasep: Map<Int, String> by lazy {
    itemNamesHns + mapOf(901 to "SHINY GENOME", 902 to "CANDY JAR", 903 to "TcloneR")
}

/** CRC32 of the 32 bytes at [fn] (Thumb bit cleared), as the field tools check them. */
private fun crc(read: (Long, Int) -> ByteArray, fn: Long): Long = CRC32().apply { update(read(fn and 1L.inv(), 32)) }.value

/** gSpeciesInfo / gItemsInfo field offsets, Heart and Soul 2.0.6's (the ROM asserts them). */
private const val SPECIES_ICON_SPRITE = 0x6C
private const val SPECIES_ICON_PAL_INDEX = 0x8E
private const val ITEM_NAME = 0x14
private const val ITEM_ICON_PIC = 0x24

/** [NATIVE_HEART_AND_SOUL] at Hasep's addresses, with the dex, guide, item text and field tools. */
fun hasepConfig(s: HasepSymbols, read: (Long, Int) -> ByteArray): NativeConfig {
    val species = s["gSpeciesInfo"]
    val items = s["gItemsInfo"]
    return NATIVE_HEART_AND_SOUL.copy(
        playerParty = s["gPlayerParty"],
        playerPartyCount = s["gPlayerPartyCount"],
        battleMons = s["gBattleMons"],
        battlerPositions = s["gBattlerPositions"],
        battlersCount = s["gBattlersCount"],
        battleTypeFlags = s["gBattleTypeFlags"],
        gMain = s["gMain"],
        saveBlock1Ptr = s["gSaveBlock1Ptr"],
        saveBlock2Ptr = s["gSaveBlock2Ptr"],
        objectEvents = s["gObjectEvents"],
        mapHeader = s["gMapHeader"],
        bagPockets = s["gBagPockets"],
        itemDescs = NATIVE_HEART_AND_SOUL.itemDescs?.copy(base = items + ITEM_NAME),
        monIconTable = species + SPECIES_ICON_SPRITE,
        monIconPaletteIndices = species + SPECIES_ICON_PAL_INDEX,
        monIconPaletteTable = s["gMonIconPaletteTable"],
        itemIconTable = items + ITEM_ICON_PIC,
        battlerControllerFuncs = s["gBattlerControllerFuncs"],
        handleInputChooseAction = s["HandleInputChooseAction"],
        handleInputChooseMove = s["HandleInputChooseMove"],
        completeWhenChoseItem = s["CompleteWhenChoseItem"],
        waitForMonSelection = s["WaitForMonSelection"],
        handleInputChooseTarget = s["HandleInputChooseTarget"],
        actionCursor = s["gActionSelectionCursor"],
        moveCursor = s["gMoveSelectionCursor"],
        partyMenu = s["gPartyMenu"],
        pokedex = POKEDEX_HEART_AND_SOUL.copy(
            speciesInfo = species,
            abilityNames = s["gAbilitiesInfo"],
            regionalOrder = s["sJohtoToNationalOrder"],
        ),
        guideTables = GUIDE_TABLES_HEART_AND_SOUL.copy(
            trainers = s["gTrainers[DIFFICULTY_NORMAL]"],
            wildHeaders = s["gWildMonHeaders"],
            mapGroups = s["gMapGroups"],
        ),
        fieldItems = hasepFieldItems(s, read),
    )
}

/** [FIELD_ITEMS_HEART_AND_SOUL] (Repels, PC anywhere, FLY) at Hasep's addresses; its CRCs from the ROM itself. */
fun hasepFieldItems(s: HasepSymbols, read: (Long, Int) -> ByteArray): FieldItemFns {
    val base = FIELD_ITEMS_HEART_AND_SOUL
    val scratch = s["gHasepAppScratch"]
    val fns = listOf("VarGet", "VarSet", "CheckBagHasItem", "RemoveBagItem", "ArePlayerFieldControlsLocked").map(s::code)
    val partyFns = listOf("GetMonData3", "SetMonData", "AddBagItem").map(s::code)
    val party = base.party?.copy(
        getMonData = partyFns[0], setMonData = partyFns[1], addBagItem = partyFns[2],
        party = s["gPlayerParty"], partyCount = s["gPlayerPartyCount"], scratch = scratch,
        codeCrcs = partyFns.map { crc(read, it) },
    )
    val pc = base.pc?.copy(
        setupScript = s.code("ScriptContext_SetupScript"),
        specials = s["gSpecials"],
        pcFn = s["ShowPokemonStorageSystemPC"],
        codeCrcs = listOf(crc(read, s["ScriptContext_SetupScript"]), crc(read, s["ShowPokemonStorageSystemPC"])),
        script = scratch + 4,
    )
    val fly = base.fly?.let { f ->
        val heal = f.healWarp?.copy(
            toHealLocation = s.code("SetWarpDestinationToHealLocation"),
            toMapWarp = s.code("SetWarpDestinationToMapWarp"),
            returnToField = s.code("ReturnToFieldFromFlyMapSelect"),
            saveBlock2Ptr = s["gSaveBlock2Ptr"],
        )
        val flyFns = f.copy(
            flagGet = s.code("IsFieldMoveUnlocked_Fly"),
            setUpFieldMoveFly = s.code("SetUpFieldMove_Fly"),
            getMapsecType = s.code("GetMapsecType"),
            fadeScreen = s.code("FadeScreen"),
            lockControls = s.code("LockPlayerFieldControls"),
            unlockControls = s.code("UnlockPlayerFieldControls"),
            partyMenuSlotId = s["gPartyMenu"] + PARTY_MENU_SLOT_ID,
            destinations = s["sMapHealLocations"],
            healWarp = heal,
            codeCrcs = emptyList(),
        )
        flyFns.copy(codeCrcs = flyFns.functions.map { crc(read, it) })
    }
    return base.copy(
        varGet = fns[0], varSet = fns[1], checkBagHasItem = fns[2], removeBagItem = fns[3], fieldControlsLocked = fns[4],
        repelVar = s["VAR_REPEL_STEP_COUNT"].toInt(),
        items = s["gItemsInfo"],
        codeCrcs = fns.map { crc(read, it) },
        party = party,
        pc = pc,
        fly = fly,
    )
}

/** gPartyMenu.slotId (the ROM asserts it). */
private const val PARTY_MENU_SLOT_ID = 9

/** [FLASH_HEART_AND_SOUL] at Hasep's addresses. */
fun hasepFlash(s: HasepSymbols, read: (Long, Int) -> ByteArray): FlashFns {
    val f = FLASH_HEART_AND_SOUL.copy(
        flagGet = s.code("FlagGet"),
        litFlag = s["FLAG_SYS_USE_FLASH"].toInt(),
        setUpFieldMoveFlash = s.code("SetUpFieldMove_Flash"),
        menuFieldCallback = s["gFieldCallback2"],
        postMenuFieldCallback = s["gPostMenuFieldCallback"],
        fieldCallbacks = listOf(s.code("FieldCallback_Flash"), s.code("SetUpPuzzleEffectRegisteel")),
        partyMenuSlotId = s["gPartyMenu"] + PARTY_MENU_SLOT_ID,
        mapHeader = s["gMapHeader"],
        codeCrcs = emptyList(),
    )
    return f.copy(codeCrcs = f.functions.map { crc(read, it) })
}

/** Heart and Soul 2.0.6's EXPAND row (pk_wide_games.h, CRC32 [HNS_WIDE_CRC]): Hasep's starts from it, as its engine. */
const val HNS_WIDE_CRC = 0x01713508
private val HNS_WIDE_ROW = intArrayOf(
    0x0300306c, 0x02001d0c, 0x092df5a4, 0x030041d8, 0x03000bdc, 0x020017f4, 0x0203a764, 0x0203a2de, 0x0203a2e0, 0x0203a29c,
    0x03005bd8, 0x03001fdd, 0x0850c6f4, 0x085036b0, 0x084fff58, 0x00000000, 0x08500360, 0x00000000, 0x00003014, 0x00000010,
    0x000003ff, 0x00000280, 0x0000000c, 0x00000000, 0x00000000, 0x0000ffff, 0x00000000, 0x1abc138c, 0x00000004, 0x00000001,
    0x00000224, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
)

/** The symbol behind each of pk_wide.h's first address slots (VMAP .. ELEVATION_PRIO; SPRITE_PAL_TAGS stays 0, as in HnS). */
private val WIDE_SYMBOLS = listOf(
    "gBackupMapLayout", "gMapHeader", "gMapGroups", "gSaveBlock1Ptr", "sFieldCameraOffset", "gObjectEvents", "gSprites",
    "gSpriteCoordOffsetX", "gSpriteCoordOffsetY", "sSpriteOrder", "gMain", "sLockFieldControls",
    "gObjectEventGraphicsInfoPointers", "sObjectEventSpritePalettes", "gInitialMovementTypeFacingDirections", null,
    "sElevationToPriority",
)
private const val WIDE_OBJ_GFX_COUNT = 30

/** EXPAND's row for Hasep (pk_wide.c's PK_WIDE_* slots): HnS's, its addresses and object graphics count from the table.
 * The constants (SaveBlock1's pos and offsets, the metatiles) are HnS's, and the ROM asserts the layouts behind them.
 * Null for a build whose table lacks one of the names (older Hasep releases): no EXPAND rather than a wrong one. */
fun hasepWideRow(s: HasepSymbols): IntArray? {
    if (WIDE_SYMBOLS.any { it != null && !s.has(it) } || !s.has("NUM_OBJ_EVENT_GFX")) return null
    val row = HNS_WIDE_ROW.copyOf()
    WIDE_SYMBOLS.forEachIndexed { i, name -> if (name != null) row[i] = s[name].toInt() }
    row[WIDE_OBJ_GFX_COUNT] = s["NUM_OBJ_EVENT_GFX"].toInt()
    return row
}
