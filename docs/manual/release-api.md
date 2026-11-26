---
title: Release API
summary: PokeDaisy's releases and downloads as JSON for app stores, launchers and tools, in the same shape as GitHub's releases API.
section: help
order: 5
keywords: [api, release api, releases json, latest release, developer, app store, launcher, emuhub, obtainium, github api, json, download link, automation, update check]
---

PokeDaisy's releases live on this website, not on GitHub. If your app store, launcher or tool used GitHub's releases API to find PokeDaisy, the same data is here, in the same JSON shape, so most code only needs a new address.

## Endpoints

| What | Address | Like GitHub's |
| --- | --- | --- |
| Every release, newest first (betas too) | `https://pokedaisy.com/api/releases/v1/releases.json` | `/repos/{owner}/{repo}/releases` |
| The newest release (no betas) | `https://pokedaisy.com/api/releases/v1/latest.json` | `/repos/{owner}/{repo}/releases/latest` |
| One release by its tag | `https://pokedaisy.com/api/releases/v1/tags/v1.6.2.2.json` | `/repos/{owner}/{repo}/releases/tags/{tag}` |

No key or account is needed, and any website may read them from a browser too. They change when a release goes out, and you may get a copy up to five minutes old.

## What a release holds

Each release has the fields you know from GitHub: `id`, `tag_name` (like `v1.6.2.2`), `name`, `published_at`, `prerelease`, `draft`, `body` (the release notes, in Markdown), `html_url` (its page on this site) and `assets`, the release's files. Each file has its `name`, `size`, `content_type` and `browser_download_url`.

To find a platform's file, match its name the way you would on GitHub: the Android app is `PokeDaisy-<version>.apk`, the Linux packages are `pokedaisy-linux-x86_64.zip` and the others in [Install on Linux](linux-install.md), and the 3DS files are `pokedaisy.3dsx` and `pokedaisy.cia`.

## Download links

Use each file's `browser_download_url` as it is. It goes through the site's download counter, which counts the download and then sends you on to the file, so follow redirects. Please don't copy files elsewhere or link to them another way: that's how PokeDaisy knows how many people play it.

Releases from before the move (1.6.2.1 and older) have their notes but no files here.

Questions, or something you need that isn't there? Write on the [feedback page](https://pokedaisy.com/feedback/#feature) or ask on [PokeDaisy's Discord](https://discord.gg/vcmyKZZV5q).
