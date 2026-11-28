// The website's download counter (since GitHub stopped hosting the releases, 2026-11-26). Every download link on the
// site and in the app's releases list is /dl/<tag>/<file> (Hosting rewrites it to the `downloads` function in index.js):
// the function adds one to a Firestore count and redirects to the file itself, in Cloudflare R2 (FILES_ORIGIN/<tag>/<file>,
// the `pokedaisy-downloads` bucket on dl.pokedaisy.com: free egress, where a CDN for big files belongs). A file only
// counts once it's known to exist there, so made-up names can't grow the counts. The counts are spread over
// SHARDS documents (`downloadCounts/<n>`: `total` and `files.<tag>/<file>`), since one document takes only about one
// write a second; /api/downloads/v1/count.json sums them for the site, which adds the downloads counted on GitHub
// before (releases.json's downloadsOnGitHub). Pure helpers here, tested by website/test/downloads.test.js.

export const SHARDS = 20;
/** Where the release files are served: the R2 bucket's own domain. */
export const FILES_ORIGIN = 'https://dl.pokedaisy.com';
/** Until [until], downloads go to a copy of the files on Firebase Hosting (website/public/downloads/<tag>/, gitignored)
 *  instead: DNS resolvers that cached pokedaisy.com's old nameservers (before Cloudflare, 2026-11-26) don't know
 *  dl.pokedaisy.com for up to two days (the .com NS TTL), but find pokedaisy.com. After it, delete that copy and deploy. */
export const FALLBACK = { origin: 'https://pokedaisy.com/downloads', until: Date.parse('2026-11-28T15:00:00Z') };

/** The origin to send a download to at [now]. */
export const filesOrigin = (now = Date.now(), fallback = FALLBACK) => (fallback.origin && now < fallback.until ? fallback.origin : FILES_ORIGIN);

const TAG = /^v\d{1,3}(\.\d{1,3}){2,3}(-[a-z]{1,10}\.\d{1,3})?$/;
const FILE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/;
/** What counts as a download: the app's builds and the source, never a picture (the 3DS QR code). */
const DOWNLOAD = /\.(apk|zip|3dsx|cia|tar\.gz)$/i;

/** /dl/<tag>/<file>(/) -> { tag, file } when both are a release file's shape, else null. */
export function parseDownload(path) {
  const m = /^\/dl\/([^/]+)\/([^/]+)\/?$/.exec(path ?? '');
  if (!m) return null;
  let tag, file;
  try { tag = decodeURIComponent(m[1]); file = decodeURIComponent(m[2]); } catch { return null; }
  if (!TAG.test(tag) || !FILE.test(file) || file.includes('..')) return null;
  return { tag, file, counts: DOWNLOAD.test(file) };
}

/** The file's path under FILES_ORIGIN. */
export const filePath = ({ tag, file }) => `/${tag}/${encodeURIComponent(file)}`;

/** The key a file is counted under in a shard's `files` map. */
export const fileKey = ({ tag, file }) => `${tag}/${file}`;

/** The shards' documents summed: { total, files: { '<tag>/<file>': n }, tags: { '<tag>': n } }. */
export function sumShards(docs) {
  const out = { total: 0, files: {}, tags: {} };
  for (const d of docs) {
    out.total += Number(d?.total) || 0;
    for (const [k, n] of Object.entries(d?.files ?? {})) {
      const v = Number(n) || 0;
      out.files[k] = (out.files[k] ?? 0) + v;
      const tag = k.split('/')[0];
      out.tags[tag] = (out.tags[tag] ?? 0) + v;
    }
  }
  return out;
}
