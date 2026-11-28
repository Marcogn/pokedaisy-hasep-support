// One release by its tag, as GitHub's /repos/{owner}/{repo}/releases/tags/{tag} gives it (releases.js).
import { rawReleases } from '../../../../../lib/releases.js';

export function getStaticPaths() {
  return rawReleases().map((r) => ({ params: { tag: r.tag_name } }));
}

export function GET({ site, params }) {
  const origin = (site ?? new URL('https://pokedaisy.com')).origin;
  const release = rawReleases(origin).find((r) => r.tag_name === params.tag);
  return new Response(JSON.stringify(release), { headers: { 'Content-Type': 'application/json; charset=utf-8' } });
}
