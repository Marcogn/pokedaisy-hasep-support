// A release's files: records them in src/data/release-files.json (names and sizes, which the site lists: releases.js)
// and, with --upload, puts them in the R2 bucket that serves them (dl.pokedaisy.com/<tag>/<file>), through wrangler
// (`npx wrangler login` once first). The files themselves sit in website/release-files/<tag>/ on the machine that
// releases (gitignored, never committed). docs/RELEASING.md's note at the top has the steps.
//   node scripts/release-files.mjs v1.6.2.2 [--upload]
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

export const BUCKET = 'pokedaisy-downloads';
const TYPES = {
  apk: 'application/vnd.android.package-archive', zip: 'application/zip', gz: 'application/gzip',
  png: 'image/png', '3dsx': 'application/octet-stream', cia: 'application/octet-stream',
};

const [tag, ...flags] = process.argv.slice(2);
if (!/^v\d+(\.\d+){2,3}(-[a-z]+\.\d+)?$/.test(tag ?? '')) {
  console.error('usage: node scripts/release-files.mjs v<version> [--upload]');
  process.exit(1);
}
const root = new URL('..', import.meta.url).pathname;
const dir = join(root, 'release-files', tag);
const files = readdirSync(dir).filter((n) => !n.startsWith('.') && statSync(join(dir, n)).isFile()).sort();
if (!files.length) throw new Error(`no files in ${dir}`);

const entries = files.map((name) => {
  const bytes = readFileSync(join(dir, name));
  return { name, size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
});

if (flags.includes('--upload')) {
  for (const f of entries) {
    const type = TYPES[f.name.split('.').pop().toLowerCase()] ?? 'application/octet-stream';
    console.log(`uploading ${tag}/${f.name} (${(f.size / 1048576).toFixed(1)} MB)`);
    execFileSync('npx', ['--yes', 'wrangler', 'r2', 'object', 'put', `${BUCKET}/${tag}/${f.name}`, '--remote',
      '--file', join(dir, f.name), '--content-type', type, '--cache-control', 'public, max-age=31536000, immutable'],
    { stdio: 'inherit' });
  }
}

const manifest = join(root, 'src', 'data', 'release-files.json');
const all = JSON.parse(readFileSync(manifest, 'utf8'));
all.releases[tag] = entries;
writeFileSync(manifest, JSON.stringify(all, null, 2) + '\n');
console.log(`${files.length} files of ${tag} in release-files.json`);
