---
title: Install on Windows
summary: Download PokeDaisy for Windows, start it, add your games and bring your saves from another emulator.
section: start
order: 86
keywords: [windows, pc, windows 10, windows 11, exe, dll, zip, extract, smartscreen, windows protected your pc, run anyway, sdl2, desktop]
---

PokeDaisy also runs on a Windows PC: the same program as the [Linux version](linux.md), with the same companion, HUD,
quick menu and `EXPAND`, in a window you can resize or make fullscreen. This page takes you from the download to your
first game.

> [!WARNING]
> The Windows version is new and in the betas only for now. Its menus are in English, and it has been tried on few
> PCs yet. If something goes wrong, [tell us](https://pokedaisy.com/feedback/#bug) which file and which PC you used.

## What you need

- **A 64-bit Windows PC.** Windows 10 or 11.
- **Your own games.** PokeDaisy comes with no games. Use ROMs you dumped yourself from cartridges you own: `.gba`,
  `.gb`, `.gbc` or `.nds` files, unzipped.

## Download and start it

1. Open the [Windows download on PokeDaisy's site](https://pokedaisy.com/#download-windows) and download
   `PokeDaisy-<version>-windows-x86_64.zip`.
2. Right click the zip, pick **Extract All** and extract it somewhere you can write to, for example a `PokeDaisy`
   folder in your Documents. Don't run it from inside the zip.
3. In that folder, make a folder called `roms` and copy your games into it. (PokeDaisy makes it the first time it
   starts, if you'd rather start it once first.)
4. Double click `PokeDaisy.exe`.

The first time, Windows may say **Windows protected your PC**, because the program isn't signed. Pick **More info**,
then **Run anyway**.

The game list opens: pick a game with the arrow keys or a controller's D-pad and press A (Z on a keyboard), or click
it. PokeDaisy keeps `roms`, `saves`, `states` and its settings in its own folder, next to `PokeDaisy.exe`.

The folder holds the program, its three cores and SDL2:

- `pokedaisy_gba_libretro.dll` plays Game Boy Advance games.
- `pokedaisy_gbc_libretro.dll` plays Game Boy and Game Boy Color games.
- `pokedaisy_ds_libretro.dll` plays DS games.

Keep them all together: PokeDaisy loads the cores from beside itself.

The controls, the menu and the companion work as on Linux: see [Linux, Steam Deck and handhelds](linux.md).

## Bring your saves

A game's save is in the `saves` folder next to `roms`, named after the whole ROM file name plus `.sav`: for
`FireRed.gba` that's `saves\FireRed.gba.sav`. A save from another emulator works when you copy it there under that
name, a RetroArch `.srm` included. A save named after the ROM without its extension works too: `saves\FireRed.sav` is
used for `FireRed.gba` when `saves\FireRed.gba.sav` isn't there. Keep a copy of your old save somewhere else first.

Before a game opens, PokeDaisy copies its save to `saves\pokedaisy-backups` and keeps the newest 10 copies, so an older
one is there if you need it. Save states from another emulator don't load.

## Updating

Download the newer zip and extract it over the old folder. Your games, saves, states and settings aren't in the zip, so
they stay as they are.

## If something's wrong

- **Windows says a `.dll` is missing:** the files were split up. Extract the whole zip again into one folder and start
  `PokeDaisy.exe` from there.
- **Nothing opens, or it closes at once:** extract the zip to a folder you can write to (not `Program Files`), so
  PokeDaisy can make its `roms` and `saves` folders.
- **A game isn't listed:** it has to be right in `roms` (not in a folder inside it), unzipped, and end in `.gba`,
  `.gb`, `.gbc` or `.nds`.
- **My save didn't load:** the save needs the ROM's whole file name with `.sav` added, or the ROM's name without its
  extension, in `saves`. Check `saves\pokedaisy-backups` for an older copy.
- **The controller does nothing:** SDL doesn't know it yet. Put a `gamecontrollerdb.txt` with its mapping in the
  folder, next to `roms`.

Still stuck? [Tell us](https://pokedaisy.com/feedback/#bug) which PC and file you used.
