captured: 2026-11-10 (headless, native-capture/mgba_dump)
rom: Hasep (Marcogn/pokehns-expansion-plus) at a3439fc6 (the table with gEnemyParty, gPlayerAvatar, the Repels and
     SHINY_ODDS), `make hns`, sha1 a997b65e39527670ff7f676ec5b6988c519c140d
state: fixtures/hasep's new game played again on this build, then `setwildbattle SPECIES_PIKACHU 5; end` written to
       gHasepAppScratch + 4 and run by ScriptContext_SetupScript, 30 frames later: the foe made in gEnemyParty, no battle
       yet (the moment SHINY ODDS rewrites it). The game's GetMonData(foe, MON_DATA_IS_SHINY) read 0 here.
rom_head.bin: the ROM's first 0x2000 bytes with everything but the HASEPSYM table zeroed.
