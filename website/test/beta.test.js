import { test } from 'node:test';
import assert from 'node:assert/strict';
import { betaAdditions, betaGameCount, compareVersions, pickBeta, withBeta } from '../src/lib/beta.js';
import { verdict } from '../src/lib/compat.js';

test('versions sort as semver, a beta before its release', () => {
  const order = ['1.6.0', '1.6.1', '1.6.2-beta.1', '1.6.2-beta.2', '1.6.2-rc.1', '1.6.2', '1.10.0'];
  assert.deepEqual([...order].reverse().sort(compareVersions), order);
  assert.equal(compareVersions('v1.6.2', '1.6.2'), 0);
});

test('the beta is the newest pre-release past the newest release, none once it is out', () => {
  const r = (tag, prerelease = false, draft = false) => ({ tag_name: tag, prerelease, draft });
  assert.equal(pickBeta([r('v1.6.2-beta.2', true), r('v1.6.2-beta.1', true), r('v1.6.1')]).tag_name, 'v1.6.2-beta.2');
  assert.equal(pickBeta([r('v1.6.2'), r('v1.6.2-rc.1', true), r('v1.6.1')]), null);
  assert.equal(pickBeta([r('v1.6.2-beta.3', true, true), r('v1.6.1')]), null);
  assert.equal(pickBeta([r('v1.6.1')]), null);
});

const release = {
  titles: { a: 'Pokémon FireRed' }, hackCodes: {}, baseCodes: { BPEE: 'Pokémon Emerald' },
  retail: [], hacks: [{ sha1: 'h1', title: 'Pokémon One', version: 'v1' }], gameBoy: [], ds: [{ code: 'CPUE', rev: 1, title: 'Platinum' }], dsHacks: [],
  matrix: { columns: ['Party', 'Bag'], rows: [
    { game: 'One', version: '1', cells: ['yes', 'yes'] },
    { game: 'Platinum (DS)', version: 'USA rev 1', cells: ['yes', 'yes'] },
  ] },
};
const beta = {
  ...release,
  hacks: [...release.hacks, { sha1: 'h2', title: 'Pokémon Two', version: 'v2' }],
  ds: [...release.ds, { code: 'CPUD', rev: 0, title: 'Platin-Edition' }],
  matrix: { columns: ['Bag', 'Party', 'Map'], rows: [
    { game: 'One', version: '1', cells: ['yes', 'yes', 'no'] },
    { game: 'One', version: '1 Italian', cells: ['part', 'yes', 'no'] },
    { game: 'Platinum (DS)', version: 'USA rev 1, DE rev 0', cells: ['yes', 'yes', 'yes'] },
    { game: 'Two', version: '2', cells: ['no', 'yes', 'yes'] },
  ] },
};

test("the beta's additions: new entries and rows, a rewritten row stays the release's", () => {
  const add = betaAdditions(release, beta, '1.6.2-beta.1');
  assert.deepEqual(add.hacks, [{ sha1: 'h2', title: 'Pokémon Two', version: 'v2', beta: '1.6.2-beta.1' }]);
  assert.deepEqual(add.ds.map((g) => g.code), ['CPUD']);
  // Cells follow the release's columns.
  assert.deepEqual(add.rows.map((r) => [r.game, r.version, r.cells]), [['One', '1 Italian', ['yes', 'part']], ['Two', '2', ['yes', 'no']]]);
  assert.equal(betaGameCount(add), 2);
  const all = withBeta(release, add);
  assert.equal(all.matrix.rows.length, 4);
  assert.equal(all.hacks.length, 2);
  assert.equal(withBeta(release, null), release);
});

test('the ROM check says which beta reads a game', () => {
  const all = withBeta(release, betaAdditions(release, beta, '1.6.2-beta.1'));
  assert.deepEqual(verdict(all, { platform: 'gba', code: 'BPEE', rev: 0, size: 32 << 20, sha1: 'h2' }),
    { status: 'supported', title: 'Pokémon Two', version: 'v2', base: 'Pokémon Emerald', beta: '1.6.2-beta.1' });
  assert.deepEqual(verdict(all, { platform: 'nds', code: 'CPUD', rev: 0 }),
    { status: 'supported', title: 'Platin-Edition', family: 'Nintendo DS', beta: '1.6.2-beta.1' });
  assert.equal(verdict(all, { platform: 'gba', code: 'BPEE', rev: 0, size: 32 << 20, sha1: 'h1' }).beta, undefined);
});
