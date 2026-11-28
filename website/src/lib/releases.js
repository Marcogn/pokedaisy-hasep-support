// The releases, kept with the site since GitHub stopped hosting them (2026-11-26): each release's notes in
// src/data/releases.json (newest first), its files in Cloudflare R2 (dl.pokedaisy.com/<tag>/<file>), listed by
// src/data/release-files.json (scripts/release-files.mjs writes it as it uploads them). Every link to a file is the download
// counter, /dl/<tag>/<file> (functions/downloads.js), which redirects to R2. The app's updater reads the same list through
// /api/releases/v1/releases.json (GitHub's release JSON shape, so AppUpdater parses it unchanged).
import data from '../data/releases.json' with { type: 'json' };
import files from '../data/release-files.json' with { type: 'json' };

export const SITE = 'https://pokedaisy.com';
/** Where a release's files are linked: /dl/<tag>/<file>, the download counter (functions/downloads.js), which redirects
 *  to the file in R2. */
export const countedPath = (tag) => `/dl/${tag}/`;
/** A release's place on the releases page. */
export const releasePath = (tag) => `/releases/${tag}/`;
/** Where the release files are served (the R2 bucket's domain); links go through /dl/ instead, but a picture (the CIA's
 *  QR code) is shown from here. */
export const FILES_ORIGIN = 'https://dl.pokedaisy.com';

// A release's files, as uploaded (release-files.json).
function filesOf(tag) {
  return (files.releases[tag] ?? []).map(({ name, size, sha256 }) => ({ name, size, sha256, url: `${countedPath(tag)}${encodeURIComponent(name)}` }));
}

let raw;

/** A file's media type by its extension, as GitHub's assets carry it. */
const TYPES = { apk: 'application/vnd.android.package-archive', zip: 'application/zip', gz: 'application/gzip', png: 'image/png' };
const typeOf = (name) => TYPES[name.split('.').pop().toLowerCase()] ?? 'application/octet-stream';
/** A stable number for an id field (GitHub clients expect one): from the text, never changing. */
const idOf = (text) => [...text].reduce((h, c) => (Math.imul(h, 31) + c.charCodeAt(0)) >>> 0, 7);

/** Every release in GitHub's release JSON shape (pre-releases too; drafts never get here), newest first, with [origin]
 *  in front of its links: the app's updater and other apps read it as they read GitHub's (/api/releases/v1/, the
 *  manual's "Release API"). Each file's link is the download counter. */
export function rawReleases(origin = SITE) {
  if (raw && origin === SITE) return raw;
  const list = data.releases.map((r) => {
    const files = filesOf(r.tag);
    const source = files.find((a) => a.name.endsWith('-source.tar.gz'));
    return {
      id: idOf(r.tag),
      tag_name: r.tag,
      name: r.name ?? r.tag,
      created_at: r.date,
      published_at: r.date,
      draft: false,
      prerelease: !!r.prerelease,
      body: r.body ?? '',
      html_url: `${origin}${releasePath(r.tag)}`,
      tarball_url: source ? `${origin}${source.url}` : null,
      assets: files.map((a) => ({
        id: idOf(`${r.tag}/${a.name}`),
        name: a.name,
        label: null,
        content_type: typeOf(a.name),
        state: 'uploaded',
        size: a.size,
        created_at: r.date,
        updated_at: r.date,
        browser_download_url: `${origin}${a.url}`,
      })),
    };
  });
  if (origin === SITE) raw = list;
  return list;
}

/** The newest release that isn't a pre-release, as GitHub's releases/latest gives it. */
export const latestRelease = (origin = SITE) => rawReleases(origin).find((r) => !r.prerelease) ?? null;

let cached;

/** The published releases (no pre-releases), newest first, as the pages use them. */
export async function getReleases() {
  if (cached) return cached;
  cached = data.releases
    .filter((r) => !r.prerelease)
    .map((r) => {
      const assets = filesOf(r.tag);
      return {
        tag: r.tag,
        version: r.tag.replace(/^v/, ''),
        name: r.name || r.tag,
        date: r.date,
        url: releasePath(r.tag),
        body: r.body ?? '',
        apk: assets.find((a) => a.name.endsWith('.apk'))?.url,
        // Every file on it (the APK, the Linux packages, the 3DS .3dsx...): the download cards pick theirs (downloads.js).
        assets,
      };
    });
  return cached;
}

/** Every file's downloads across [list] (GitHub's release JSON: pre-releases too, never drafts), summed. */
export function releaseDownloads(list) {
  return list
    .filter((r) => !r.draft)
    .flatMap((r) => r.assets ?? [])
    .reduce((n, a) => n + (a.download_count ?? 0), 0);
}

/** The downloads counted while GitHub hosted the files (releases.json's downloadsOnGitHub). If the releases go back to
 *  GitHub, this becomes GitHub's live count instead (which already holds this number): never both. */
export const downloadsBefore = () => data.downloadsOnGitHub ?? 0;

/** The site's own count (the download counter's sum), the page's /api/downloads/v1/count.json. */
export const COUNT_API = '/api/downloads/v1/count.json';

let downloads;

/** Every download: the ones counted on GitHub plus the site's own, read at build time (the page refreshes it in the
 *  browser); offline, GitHub's alone. */
export async function getDownloads() {
  if (downloads !== undefined) return downloads;
  downloads = downloadsBefore();
  try {
    const res = await fetch(SITE + COUNT_API, { headers: { 'User-Agent': 'pokedaisy-website' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    downloads += Number((await res.json()).site) || 0;
  } catch (e) {
    console.warn(`[releases] couldn't read the site's download count: ${e.message}`);
  }
  return downloads;
}

/** A release's date in the page's language ([locale]: src/i18n's; English keeps the site's 7 NOV 2026). */
export function formatDate(iso, locale = 'en-GB') {
  return new Date(iso).toLocaleDateString(locale === 'en-US' ? 'en-GB' : locale, { day: 'numeric', month: 'short', year: 'numeric' }).toUpperCase();
}

// Quotes too: a release body is text typed into releases.json, and a `"` in a link's URL would otherwise end its href.
const escape = (s) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

function inline(s) {
  return escape(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\((https:\/\/[^)\s]+)\)/g, '<a href="$2" rel="noopener nofollow">$1</a>');
}

/** The release notes' markdown: ## headings, - bullets, **bold**, `code`, links. */
export function markdown(md) {
  let html = '';
  let inList = false;
  for (const raw of md.replace(/\r/g, '').split('\n')) {
    const line = raw.trimEnd();
    const bullet = line.match(/^\s*[-*] (.*)/);
    if (!bullet && inList) { html += '</ul>'; inList = false; }
    if (bullet) {
      if (!inList) { html += '<ul>'; inList = true; }
      html += `<li>${inline(bullet[1])}</li>`;
    } else if (/^#{1,6} /.test(line)) {
      html += `<h4>${inline(line.replace(/^#+ /, ''))}</h4>`;
    } else if (line.trim()) {
      html += `<p>${inline(line)}</p>`;
    }
  }
  if (inList) html += '</ul>';
  return html;
}

/** The first bullets under a release's "New" heading (or its first bullets), for the home page. */
export function highlights(md, n = 4) {
  const lines = md.replace(/\r/g, '').split('\n');
  const from = lines.findIndex((l) => /^#+ New/i.test(l));
  const bullets = lines.slice(from + 1).filter((l) => /^\s*[-*] /.test(l)).map((l) => l.replace(/^\s*[-*] /, ''));
  return bullets.slice(0, n).map(inline);
}
