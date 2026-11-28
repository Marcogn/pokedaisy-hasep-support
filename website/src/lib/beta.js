// Games the newest beta supports that the release doesn't yet. The site deploys with a release (its compat.json is that
// release's), and the build reads the newest pre-release's compat.json from GitHub (releases.js' getBeta): what it has
// on top goes into the games table, the compatibility list and the ROM check, each entry marked with the beta's version
// so the page can say "in the beta" and show how to get it. Pure functions: the ROM check runs them in the browser too.

/** "1.6.2-beta.1" -> [[1, 6, 2], ['beta', 1]]; a release has no second part. */
function parse(version) {
  const [base, pre] = String(version).replace(/^v/, '').split('-');
  return [base.split('.').map(Number), pre ? pre.split('.').map((p) => (/^\d+$/.test(p) ? Number(p) : p)) : null];
}

/** Semver order: 1.6.1 < 1.6.2-beta.1 < 1.6.2-beta.2 < 1.6.2-rc.1 < 1.6.2. Negative when a comes first. */
export function compareVersions(a, b) {
  const [ab, ap] = parse(a);
  const [bb, bp] = parse(b);
  for (let i = 0; i < Math.max(ab.length, bb.length); i++) {
    const d = (ab[i] ?? 0) - (bb[i] ?? 0);
    if (d) return d;
  }
  if (!ap || !bp) return (ap ? -1 : 0) - (bp ? -1 : 0);
  for (let i = 0; i < Math.max(ap.length, bp.length); i++) {
    if (ap[i] === undefined || bp[i] === undefined) return ap[i] === undefined ? -1 : 1;
    if (ap[i] === bp[i]) continue;
    if (typeof ap[i] === 'number' && typeof bp[i] === 'number') return ap[i] - bp[i];
    return String(ap[i]) < String(bp[i]) ? -1 : 1;
  }
  return 0;
}

/**
 * The newest pre-release newer than the newest release, from GitHub's releases list (drafts skipped), or null: once
 * its version is out, the site has nothing to call a beta.
 */
export function pickBeta(list) {
  const shown = list.filter((r) => !r.draft);
  const stable = shown.filter((r) => !r.prerelease).map((r) => r.tag_name).sort(compareVersions).pop();
  const betas = shown.filter((r) => r.prerelease && (!stable || compareVersions(r.tag_name, stable) > 0));
  return betas.sort((a, b) => compareVersions(a.tag_name, b.tag_name)).pop() ?? null;
}

const KEYS = {
  hacks: (h) => h.sha1,
  gameBoy: (g) => g.sha1,
  ds: (g) => `${g.code}/${g.rev}`,
  dsHacks: (h) => `${h.code}/${h.rev}/${h.offset}/${h.word}`,
  retail: (r) => r.code,
};

/**
 * What [beta] (the pre-release's compat.json) supports on top of [data] (the release's), each entry with `beta`:
 * [version]. A games-table row is new when its game and version aren't in the release's table; a row that replaced
 * one of the release's (the same game, its version line rewritten) stays the release's, since the release still
 * plays what that row says.
 */
export function betaAdditions(data, beta, version) {
  const add = { version, titles: {}, hackCodes: {}, baseCodes: {}, rows: [] };
  for (const [list, key] of Object.entries(KEYS)) {
    const have = new Set((data[list] ?? []).map(key));
    add[list] = (beta[list] ?? []).filter((e) => !have.has(key(e))).map((e) => ({ ...e, beta: version }));
  }
  for (const map of ['titles', 'hackCodes', 'baseCodes']) {
    for (const [k, v] of Object.entries(beta[map] ?? {})) if (!(k in (data[map] ?? {}))) add[map][k] = v;
  }
  const rowKey = (r) => `${r.game}|${r.version}`;
  const ours = new Set(data.matrix.rows.map(rowKey));
  const theirs = new Set(beta.matrix.rows.map(rowKey));
  const replaced = new Set(data.matrix.rows.filter((r) => !theirs.has(rowKey(r))).map((r) => r.game));
  // Columns by name, in case the beta's table has others.
  const cols = data.matrix.columns.map((c) => beta.matrix.columns.indexOf(c));
  add.rows = beta.matrix.rows
    .filter((r) => !ours.has(rowKey(r)) && !replaced.has(r.game))
    .map((r) => ({ ...r, cells: cols.map((i) => (i < 0 ? 'no' : r.cells[i])), beta: version }));
  return add;
}

/** [data] with [add] ([betaAdditions]) on top; [data] itself when there's no beta. */
export function withBeta(data, add) {
  if (!add) return data;
  const out = { ...data, betaVersion: add.version, betaTitles: Object.fromEntries(Object.keys(add.titles).map((k) => [k, add.version])) };
  for (const list of Object.keys(KEYS)) out[list] = [...(data[list] ?? []), ...add[list]];
  for (const map of ['titles', 'hackCodes', 'baseCodes']) out[map] = { ...data[map], ...add[map] };
  out.matrix = { ...data.matrix, rows: [...data.matrix.rows, ...add.rows] };
  return out;
}

/** How many games and versions (games-table rows) [add] brings. */
export const betaGameCount = (add) => add?.rows?.length ?? 0;
