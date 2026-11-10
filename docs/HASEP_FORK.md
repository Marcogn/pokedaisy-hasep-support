# The Hasep fork

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy

This fork adds support for Hasep (Marcogn/pokehns-expansion-plus), a fork of Pokémon Heart and Soul. Everything else
is PokeDaisy's, merged in as upstream releases. Keep the fork's own changes small and in their own files, so the merges
stay mechanical.

## Why a table in the ROM

PokeDaisy recognises a ROM hack by its SHA-1 and reads it through addresses found by hand for that exact build
(`gamedata/gen3/hacks/heart_and_soul.toml`). Every Hasep build moves the RAM and the code (party +0x58, bag +0xC,
the code and the ROM tables by 0x20000 and more against Heart and Soul 2.0.6), so no fixed address list can follow its
releases. Instead the ROM carries a table the linker fills in (Hasep's `include/hasep_symbols.h`): a `HASEPSYM` header
right after the GF / RHH headers, then `{char name[44]; u32 value}` entries. The app reads the addresses by name.

The struct layouts are Heart and Soul 2.0.6's, pinned in the ROM by `STATIC_ASSERT`s behind a layout version
(`HasepSymbols.LAYOUT_VERSION`). So Hasep runs as `GameKind.HEART_AND_SOUL`: HnS's config, dex, guide, maps, art and
field tools, with the addresses swapped. A ROM with a newer layout or format than the app knows is not read at all
(`HasepSymbols.parse` returns null) rather than read wrong.

## What the fork changes

| Where | What |
| --- | --- |
| `companion/data/Hasep.kt` | the table's parser, the config (`hasepConfig`), field items / PC / FLY (`hasepFieldItems`), FLASH (`hasepFlash`) |
| `companion/data/Poller.kt` | `detect`: a large BPEE ROM with the table is Hasep, before the SHA-1 list; `runningHasep` cleared for any other ROM |
| `companion/data/Flash.kt` | `flashCandidatesFor`: Hasep's FLASH |
| `companion/data/MonArt.kt` | the follower art: gSpeciesInfo from the table |
| `PokeDaisyActivity.kt` | the battle POKéMON switch: gPartyMenu / gPlayerParty from the table |
| `AppUpdater.kt`, `AppUpdate.kt` | updates from this fork's releases; a notice once per new upstream release |
| `app/build.gradle.kts`, `strings.xml` | `applicationId` `com.pokedaisy.hasep`, version `<upstream>.<n>`, name "PokeDaisy Hasep" |
| `companion/data/ActiveTables.kt` | the three item names Hasep appended (`itemNamesHasep`, ids 901 to 903) |
| `companion/WhatsNew.kt` | the fork's own release notes, newest first |
| `NOTICE`, `README.md`, docs/manual, `docs/ROM_FEATURES.md`, `website/src/data/compat.json` | the modification notice, Hasep's rows |
| `app/src/test/.../HasepTest.kt`, `fixtures/hasep/`, `fixtures/hasep_battle/` | the table, a new game and a trainer battle read through it |

The code CRCs the field tools check (PC, FLY, FLASH, Repels) are computed from the running ROM itself: the table, not a
known build, vouches for those functions.

Not on Hasep yet: **EXPAND** (`pk_wide_games.h` is a native table of 36 addresses per ROM CRC; feeding it from the
table needs a JNI setter). FLY and FLASH are wired to the table but not tried yet; PC anywhere was, headless.

## Versions

`versionName` is the upstream version plus a fourth part: `1.5.0.1` is the first fork build on PokeDaisy 1.5.0, and
after merging PokeDaisy 1.6.0 the next one is `1.6.0.1`. `versionCode` is upstream's times 100 plus that part
(PokeDaisy 1.5.0 is 19, so 1901). `AppUpdater.upstreamBase` takes the first three parts back, which is what the
upstream notice compares against `lidor30/pokedaisy`'s releases.

## Merging upstream

```sh
git remote add upstream https://github.com/lidor30/pokedaisy   # once
git fetch upstream
git merge upstream/main
# conflicts: keep upstream's side, then put the fork's lines above back in;
# versionCode / versionName: upstream's new ones turned into the fork's scheme
make gamedata && make test-quick
```

## Releasing

The upstream `release.yml` works unchanged: push a tag `v<versionName>` from `main`. It runs CI, builds the APK signed
with the fork's key and puts up a **draft** release; publish it on GitHub and the app offers it as an update (drafts
and pre-releases are skipped). The ROM-backed tests are skipped without a ROM runner. It needs, once, a `release`
environment with the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` and the variable
`RELEASE_CERT_SHA256` (docs/RELEASING.md). The website workflow fails in the fork for lack of its deploy secrets: that's
expected, the fork has no website.
