// The hero's handhelds show the real shots at 120-350 CSS pixels, from 1240 / 1280 px originals. A browser scales an
// image that sits in its own layer (the crossfade's opacity) with plain bilinear sampling, so a 5-10x step left the
// companion's text and the overworld's edges jagged. This writes copies already scaled down with a proper filter
// (lanczos) at a few widths into public/media/hero/, which Home.astro's `heroShot` offers through srcset; the browser
// then scales at most ~1.5x. The flight clip on the Thor's top screen gets a smaller copy the same way (ffmpeg).
// Rerun after changing a shot the hero uses: `node scripts/hero-shots.mjs`.
import { execFileSync } from 'node:child_process';
import { mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const media = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'media');
const out = join(media, 'hero');
mkdirSync(out, { recursive: true });

/** Kept in step with Home.astro's HERO_WIDTHS and the shots its hero shows. */
export const WIDTHS = [200, 320, 480, 720];
const SHOTS = [
  'companion-party', 'companion-dex', 'companion-map', 'companion-bag', 'companion-guide',
  'soulgold-expand', 'rogue-expand', 'lazarus-expand', 'monomyth-expand', 'mariomon-expand',
  'free-flight-poster',
];

for (const name of SHOTS) {
  for (const w of WIDTHS) {
    await sharp(join(media, `${name}.webp`))
      .resize({ width: w, kernel: 'lanczos3' })
      .webp({ quality: 88, effort: 6 })
      .toFile(join(out, `${name}-${w}.webp`));
  }
  console.log(`${name}: ${WIDTHS.join(', ')}`);
}

// The flight: 720 px wide (the Thor's top screen is at most ~350 CSS px), no sound, faststart for a quick first frame.
const clip = join(out, 'free-flight-720.mp4');
if (!existsSync(clip) || process.argv.includes('--video')) {
  execFileSync('ffmpeg', ['-y', '-v', 'error', '-i', join(media, 'free-flight.mp4'),
    '-vf', 'scale=720:-2:flags=lanczos', '-c:v', 'libx264', '-crf', '26', '-preset', 'slow', '-pix_fmt', 'yuv420p',
    '-an', '-movflags', '+faststart', clip], { stdio: 'inherit' });
  console.log('free-flight-720.mp4');
}
