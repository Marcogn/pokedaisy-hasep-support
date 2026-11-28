// The download counter's pure parts (functions/downloads.js): which paths count, where they go, how shards add up.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseDownload, filePath, fileKey, sumShards, filesOrigin, FILES_ORIGIN } from '../functions/downloads.js';

test('a release file counts, anything else is refused or let through uncounted', () => {
  assert.deepEqual(parseDownload('/dl/v1.6.2.2/PokeDaisy-1.6.2.2.apk'), { tag: 'v1.6.2.2', file: 'PokeDaisy-1.6.2.2.apk', counts: true });
  assert.equal(parseDownload('/dl/v1.6.2.2/pokedaisy.cia/').counts, true);
  assert.equal(parseDownload('/dl/v1.7.0-beta.1/pokedaisy-1.7.0-beta.1-source.tar.gz').counts, true);
  assert.equal(parseDownload('/dl/v1.6.2.2/pokedaisy-cia-qr.png').counts, false);
  for (const bad of ['/dl/latest/x.apk', '/dl/v1.6/x.apk', '/dl/v1.6.2.2/../x.apk', '/dl/v1.6.2.2/a%2Fb.apk', '/dl/v1.6.2.2/.hidden.zip',
    '/dl/v1.6.2.2/', '/downloads/v1.6.2.2/x.apk', '/dl/v1.6.2.2/x.apk/y', '/dl/v1.6.2.2/%E0%A4%A.apk']) {
    assert.equal(parseDownload(bad), null, bad);
  }
});

test('a counted path goes to its file', () => {
  const d = parseDownload('/dl/v1.6.2.2/PokeDaisy-1.6.2.2-windows-x86_64.zip');
  assert.equal(filePath(d), '/v1.6.2.2/PokeDaisy-1.6.2.2-windows-x86_64.zip');
  assert.equal(fileKey(d), 'v1.6.2.2/PokeDaisy-1.6.2.2-windows-x86_64.zip');
});

test('shards add up by file and by release', () => {
  const sum = sumShards([
    { total: 3, files: { 'v1.6.2.2/a.apk': 2, 'v1.6.2.2/b.zip': 1 } },
    { total: 2, files: { 'v1.6.2.2/a.apk': 1, 'v1.7.0/a.apk': 1 } },
    {},
  ]);
  assert.deepEqual(sum, { total: 5, files: { 'v1.6.2.2/a.apk': 3, 'v1.6.2.2/b.zip': 1, 'v1.7.0/a.apk': 1 }, tags: { 'v1.6.2.2': 4, 'v1.7.0': 1 } });
});

test('the r2.dev fallback lasts until its date, then downloads go to dl.pokedaisy.com', () => {
  const fallback = { origin: 'https://pub-x.r2.dev', until: Date.parse('2026-11-28T15:00:00Z') };
  assert.equal(filesOrigin(Date.parse('2026-11-27T00:00:00Z'), fallback), 'https://pub-x.r2.dev');
  assert.equal(filesOrigin(Date.parse('2026-11-28T15:00:00Z'), fallback), FILES_ORIGIN);
  assert.equal(filesOrigin(Date.parse('2026-11-27T00:00:00Z'), { origin: null, until: fallback.until }), FILES_ORIGIN);
});
