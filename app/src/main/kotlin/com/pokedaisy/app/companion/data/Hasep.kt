package com.pokedaisy.app.companion.data

import kotlin.concurrent.Volatile

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
            val magic = MAGIC.encodeToByteArray()
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
                val name = table.decodeToString(off, off + nameLen)
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
private fun crc(read: (Long, Int) -> ByteArray, fn: Long): Long = crc32(read(fn and 1L.inv(), 32))

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
    ).let { c ->
        // From Hasep 2.0.6.PLUS.11 on (a table without these names reads as before): the battle's FOE TEAM and SHINY
        // ODDS through gEnemyParty, and the shiny check the game draws (SHINY_ODDS, XOR the forced-shiny bit at +0x1F).
        if (!s.has("gEnemyParty") || !s.has("SHINY_ODDS")) c else c.copy(
            enemyParty = s["gEnemyParty"],
            battlerPartyIndexes = s["gBattlerPartyIndexes"],
            battleStructPtr = s["gBattleStruct"],
            monToSwitchIntoOff = BATTLE_STRUCT_MON_TO_SWITCH_INTO,
            shinyBelow = s["SHINY_ODDS"].toInt(),
            partyMonLayout = c.partyMonLayout.copy(shinyModifier = BOX_MON_SHINY_MODIFIER),
        )
    }
}

/** gBattleStruct->monToSwitchIntoId and BoxPokemon's `hpLost:14, shinyModifier:1` high byte (the ROM asserts both). */
private const val BATTLE_STRUCT_MON_TO_SWITCH_INTO = 0xE5
private const val BOX_MON_SHINY_MODIFIER = 0x1F

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
        // The Repels from the bag, SoulGold's way (bagActions stays false): expansion's ids and Lure bit, the steps at
        // gItemsInfo's holdEffectParam (+0x1D). Hasep keeps no VAR_LAST_REPEL_LURE_USED (the ROM asserts it).
        repelItems = if (s.has("ITEM_REPEL")) setOf(s["ITEM_REPEL"], s["ITEM_SUPER_REPEL"], s["ITEM_MAX_REPEL"]).map { it.toInt() }.toSet()
            else base.repelItems,
        repelLureBit = if (s.has("REPEL_LURE_MASK")) s["REPEL_LURE_MASK"].toInt() else base.repelLureBit,
        itemParamOffset = ITEM_HOLD_EFFECT_PARAM,
        items = s["gItemsInfo"],
        codeCrcs = fns.map { crc(read, it) },
        party = party,
        pc = pc,
        fly = fly,
        // FREE FLIGHT / the scenic FLY (PokeDaisy 1.6.0): Heart and Soul's record is at 2.0.6's fixed addresses, which
        // Hasep moves, so only from a table that carries them (PLUS.12 on); none before, and FLY is the game's own warp.
        flight = hasepFlight(s, read),
    )
}

/** gItemsInfo's holdEffectParam (the ROM asserts it). */
private const val ITEM_HOLD_EFFECT_PARAM = 0x1D

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

/** The table's names FREE FLIGHT needs beyond FLY's (Hasep 2.0.6.PLUS.12 on); a table without one of them has no flight. */
private val FLIGHT_SYMBOLS = listOf(
    "gObjectEvents", "gPlayerAvatar", "gSprites", "gBackupMapLayout", "sMapConnectionFlags", "gSaveBlock1Ptr",
    "gSaveBlock2Ptr", "gMain", "CB2_Overworld", "sLockFieldControls", "gPaletteFade", "LockPlayerFieldControls",
    "UnlockPlayerFieldControls", "CreateSprite", "DestroySprite", "StartSpriteAnim", "SpriteCallbackDummy",
    "gFieldEffectObjectTemplate_Bird", "LoadPlayerObjectEventPalette", "MapGridGetMetatileBehaviorAt",
    "MapGridGetCollisionAt", "MapGridGetElevationAt", "GetObjectEventIdByPosition", "PlaySE", "gSpriteCoordOffsetX",
    "gSpriteCoordOffsetY", "gMapHeader", "CurrentMapDrawMetatileAt", "DrawWholeMapView", "gMapGroups", "MAP_GROUP_COUNT",
    "MAP_GROUPS_COUNT", "sHealLocations", "HEAL_LOCATION_SIZE", "HEAL_LOCATION_COUNT", "RemoveFollowingPokemon",
    "UpdateFollowingPokemon", "gHasepAppScratch",
)

/**
 * [FLIGHT_HEART_AND_SOUL] (FREE FLIGHT, the scenic FLY, FLY ON) at Hasep's addresses, or null on a table without them.
 * The movements, sounds and landing ground stay Heart and Soul's: the ROM asserts them (src/hasep_symbols.c). Map groups
 * past the first with no list (gMapGroups' NULLs, as in Heart and Soul) are left out.
 */
fun hasepFlight(s: HasepSymbols, read: (Long, Int) -> ByteArray): FlightFns? {
    if (FLIGHT_SYMBOLS.any { !s.has(it) }) return null
    val groups = s["MAP_GROUPS_COUNT"].toInt()
    if (groups !in 1..255) return null
    val counts = read(s["MAP_GROUP_COUNT"], groups)
    val lists = read(s["gMapGroups"], 4 * groups)
    val sizes = (0 until groups).map { g ->
        val list = (0..3).fold(0L) { v, k -> v or ((lists[4 * g + k].toLong() and 0xFF) shl (8 * k)) }
        list to (counts[g].toInt() and 0xFF)
    }.takeWhile { (list, n) -> n > 0 && list in 0x08000000L until 0x0A000000L }.map { it.second }
    if (sizes.isEmpty()) return null
    val f = FLIGHT_HEART_AND_SOUL.copy(
        objectEvents = s["gObjectEvents"],
        playerAvatar = s["gPlayerAvatar"],
        sprites = s["gSprites"],
        vmap = s["gBackupMapLayout"],
        connectionFlags = s["sMapConnectionFlags"],
        saveBlock1Ptr = s["gSaveBlock1Ptr"],
        saveBlock2Ptr = s["gSaveBlock2Ptr"],
        main = s["gMain"],
        cb2Overworld = s.code("CB2_Overworld"),
        lockByte = s["sLockFieldControls"],
        paletteFade = s["gPaletteFade"],
        lockControls = s.code("LockPlayerFieldControls"),
        unlockControls = s.code("UnlockPlayerFieldControls"),
        createSprite = s.code("CreateSprite"),
        destroySprite = s.code("DestroySprite"),
        startSpriteAnim = s.code("StartSpriteAnim"),
        spriteDummy = s["SpriteCallbackDummy"],   // written as a sprite's callback: the Thumb bit stays
        birdTemplate = s["gFieldEffectObjectTemplate_Bird"],
        birdPalette = s.code("LoadPlayerObjectEventPalette"),
        behaviorAt = s.code("MapGridGetMetatileBehaviorAt"),
        collisionAt = s.code("MapGridGetCollisionAt"),
        elevationAt = s.code("MapGridGetElevationAt"),
        objectAt = s.code("GetObjectEventIdByPosition"),
        playSe = s.code("PlaySE"),
        spriteOffset = s["gSpriteCoordOffsetX"],
        spriteOffsetY = s["gSpriteCoordOffsetY"],
        mapHeader = s["gMapHeader"],
        drawMetatileAt = s.code("CurrentMapDrawMetatileAt"),
        drawView = s.code("DrawWholeMapView"),
        mapGroups = s["gMapGroups"],
        mapGroupSizes = sizes,
        healLocations = s["sHealLocations"],
        healLocationSize = s["HEAL_LOCATION_SIZE"].toInt(),
        healLocationCount = s["HEAL_LOCATION_COUNT"].toInt(),
        followerRemove = s.code("RemoveFollowingPokemon"),
        followerUpdate = s.code("UpdateFollowingPokemon"),
        scratch = s["gHasepAppScratch"],
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

/**
 * Hasep's ARCEUS FORMULAS (from 2.0.6.PLUS.12, OFF by default): every Pokémon's stats follow Pokémon Legends: Arceus,
 * so a PC box Pokémon's stats can't be worked out the classic way ([gen3Stats]). Whether it's on, read live: the table's
 * gSaveBlock3Ptr, then the byte at SAVEBLOCK3_EXTRA_OPTIONS_OFFSET, bit ARCEUS_FORMULAS_MASK. False for an older table.
 */
fun hasepArceusFormulasOn(s: HasepSymbols, read: (Long, Int) -> ByteArray): Boolean {
    if (!s.has("gSaveBlock3Ptr") || !s.has("SAVEBLOCK3_EXTRA_OPTIONS_OFFSET") || !s.has("ARCEUS_FORMULAS_MASK")) return false
    val b = read(s["gSaveBlock3Ptr"], 4)
    val sb3 = (0..3).fold(0L) { v, k -> v or ((b[k].toLong() and 0xFF) shl (8 * k)) }
    if (sb3 !in 0x02000000L until 0x04000000L) return false
    val options = read(sb3 + s["SAVEBLOCK3_EXTRA_OPTIONS_OFFSET"], 1)[0].toLong() and 0xFF
    return options and s["ARCEUS_FORMULAS_MASK"] != 0L
}

/** Legends: Arceus' effort level multipliers, 0 to 10 (Hasep's src/pokemon.c, from PKHeX's PA8.cs). */
private val ARCEUS_EFFORT_MULTIPLIERS = intArrayOf(0, 2, 3, 4, 7, 8, 9, 14, 15, 16, 25)

/** Hasep's effort level from what a Pokémon has: the IV's (20 and up 1, 26 up 2, 31 gives 3), plus EV * 7 / 252, at most 10. */
private fun arceusEffortLevel(iv: Int, ev: Int): Int =
    minOf(10, (if (iv >= 31) 3 else if (iv >= 26) 2 else if (iv >= 20) 1 else 0) + ev * 7 / 252)

/** The BIOS Sqrt: the integer square root, rounded down. */
private fun isqrt(x: Long): Long {
    var r = kotlin.math.sqrt(x.toDouble()).toLong()
    while ((r + 1) * (r + 1) <= x) r++
    while (r * r > x) r--
    return r
}

/** round((sqrt(base) * multiplier + level) / 2.5), half away from zero, with the root in 1/4096ths: the game's integers. */
private fun arceusEffortBonus(base: Int, effort: Int, level: Int): Int {
    val v = isqrt(base.toLong() shl 24) * ARCEUS_EFFORT_MULTIPLIERS[effort] + (level.toLong() shl 12)
    return ((v * 2 + (5 shl 12) / 2) / (5 shl 12)).toInt()
}

/**
 * CalculateMonStats with ARCEUS FORMULAS on, in [gen3Stats]' order and arguments. The two base terms are single-precision
 * floats on purpose, as in the game (and Legends: Arceus): doubles come out 1 higher for some bases and levels. The
 * nature goes on the base term, before the effort bonus.
 */
internal fun hasepArceusStats(species: Int, base: IntArray, ivs: List<Int>, evs: List<Int>, nature: Int, level: Int): List<Int> =
    List(6) { s ->
        val bonus = arceusEffortBonus(base[s], arceusEffortLevel(ivs[s], evs[s]), level)
        if (s == STAT_HP) {
            if (species == 303) 1   // Shedinja
            else ((level.toFloat() / 100f + 1f) * base[s].toFloat() + level.toFloat()).toInt() + bonus
        } else {
            val n = ((level.toFloat() / 50f + 1f) * base[s].toFloat() / 1.5f).toInt()
            val withNature = when (s) {
                natureRaises(nature) -> n * 110 / 100
                natureLowers(nature) -> n * 90 / 100
                else -> n
            }
            withNature + bonus
        }
    }
