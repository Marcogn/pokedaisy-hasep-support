// The releases API: every release (betas too, newest first) in GitHub's release JSON shape, so the app's updater
// (AppUpdater.kt) reads it as it read GitHub's: tag_name, prerelease, body, html_url and each file's name, size and
// browser_download_url. Built with the site from src/data/releases.json and src/data/release-files.json (releases.js).
import { rawReleases } from '../../../../lib/releases.js';

export function GET({ site }) {
  const origin = (site ?? new URL('https://pokedaisy.com')).origin;
  return new Response(JSON.stringify(rawReleases(origin)), { headers: { 'Content-Type': 'application/json; charset=utf-8' } });
}
