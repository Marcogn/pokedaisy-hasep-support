// The newest release (not a pre-release), as GitHub's /repos/{owner}/{repo}/releases/latest gives it (releases.js).
import { latestRelease } from '../../../../lib/releases.js';

export function GET({ site }) {
  const origin = (site ?? new URL('https://pokedaisy.com')).origin;
  return new Response(JSON.stringify(latestRelease(origin)), { headers: { 'Content-Type': 'application/json; charset=utf-8' } });
}
