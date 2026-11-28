// The newest beta at build time (never in the browser: it reads the release's files): releases.js' list, that tag's
// compat.json and ROM_FEATURES.md (in website/release-files/<tag>/ beside its files on the machine that builds the site,
// docs/RELEASING.md), and what it reads on top of this build's games (beta.js).
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { rawReleases } from './releases.js';
import { betaAdditions, pickBeta } from './beta.js';
import { parseRomFeatures } from './romFeatures.js';
import compat from '../data/compat.json' with { type: 'json' };

/** The beta's ROM_FEATURES.md, parsed; null when its shape is one this build can't read. */
function features(md) {
  try { return parseRomFeatures(md); } catch { return null; }
}

let beta;

/**
 * The newest beta (a pre-release newer than the newest release) and what it supports on top of this build's
 * compat.json, read from that tag's compat.json beside its files (beta.js): { version, url, apk, add, features } (features:
 * that tag's docs/ROM_FEATURES.md, parsed). Null when there's none or it can't be read: the site then lists the
 * release's games alone.
 */
export async function getBeta() {
  if (beta !== undefined) return beta;
  beta = null;
  const pick = pickBeta(rawReleases(''));
  if (!pick) return beta;
  try {
    const dir = join(process.cwd(), 'release-files', pick.tag_name);
    const version = pick.tag_name.replace(/^v/, '');
    const apk = pick.assets.find((a) => a.name.endsWith('.apk'));
    // Its ROM_FEATURES.md too, for the beta games' details window (the release's has no rows for them).
    let doc = null;
    try { doc = readFileSync(join(dir, 'ROM_FEATURES.md'), 'utf8'); } catch {}
    beta = {
      version,
      url: pick.html_url,
      apk: apk && { url: apk.browser_download_url, size: apk.size },
      add: betaAdditions(compat, JSON.parse(readFileSync(join(dir, 'compat.json'), 'utf8')), version),
      features: doc ? features(doc) : null,
    };
  } catch (e) {
    console.warn(`[beta] couldn't read the beta's games (${pick.tag_name}): ${e.message}`);
  }
  return beta;
}

