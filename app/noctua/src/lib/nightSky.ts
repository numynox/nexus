/*
 * The sky above the owl at the end of the feed, worked out without drawing
 * anything. Positions are in the owl scene's units — 360 wide, 470 tall, the
 * branch near its foot — the same as the Android app's
 * (android/noctua/.../ui/feed/owl/NightSky.kt), which this mirrors.
 */

const KNOWN_NEW_MOON = Date.UTC(2000, 0, 6, 18, 14);
const SYNODIC_DAYS = 29.530588853;

/** Where the moon is in its cycle: 0 is new, 0.5 full, just under 1 new again. */
export function moonPhase(at: Date): number {
  const days = (at.getTime() - KNOWN_NEW_MOON) / 86_400_000;
  return (((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS) / SYNODIC_DAYS;
}

/** How much of the disc is lit, 0 to 1. */
export function moonLit(phase: number): number {
  return (1 - Math.cos(2 * Math.PI * phase)) / 2;
}

const PHASE_NAMES = [
  "New moon",
  "Waxing crescent",
  "First quarter",
  "Waxing gibbous",
  "Full moon",
  "Waning gibbous",
  "Last quarter",
  "Waning crescent",
];

export function moonPhaseName(phase: number): string {
  return PHASE_NAMES[Math.round(phase * 8) % 8];
}

export const MOON = { x: 282, y: 78, r: 24 };

/**
 * The lit part of the moon as SVG path data: a half circle on the lit side,
 * and a half ellipse for the terminator that bulges towards the dark side for
 * a crescent and away from it past a quarter. Waxing is lit on the right.
 */
export function moonLitPath(phase: number, cx = MOON.x, cy = MOON.y, r = MOON.r): string {
  const rx = Math.abs(Math.cos(2 * Math.PI * phase)) * r;
  const waxing = phase < 0.5;
  const crescent = phase < 0.25 || phase > 0.75;
  const outer = waxing ? 1 : 0;
  const inner = waxing ? (crescent ? 0 : 1) : crescent ? 1 : 0;
  return `M ${cx} ${cy - r} A ${r} ${r} 0 0 ${outer} ${cx} ${cy + r} A ${rx.toFixed(2)} ${r} 0 0 ${inner} ${cx} ${cy - r} Z`;
}

/** Night is when the owl is awake: from seven in the evening to seven in the morning. */
export function isNight(at: Date): boolean {
  const hour = at.getHours();
  return hour < 7 || hour >= 19;
}

/** A small seeded generator (mulberry32), so a star's place depends only on its article. */
export function seeded(seed: number): () => number {
  let state = seed | 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export interface SkyPoint {
  x: number;
  y: number;
}

/**
 * A star per article read today, in reading order, each placed from its
 * article's id: the stars already up stay put when a new one joins. Each keeps
 * clear of the moon, the owl's head and the stars before it, until the sky is
 * too full for that.
 */
export function constellation(articleIds: string[]): SkyPoint[] {
  const placed: SkyPoint[] = [];
  for (const id of articleIds) {
    const random = seeded(Number(id) * 7919 + 17);
    let point: SkyPoint;
    let tries = 0;
    do {
      point = { x: 26 + random() * 308, y: 58 + random() * 178 };
      tries++;
    } while (tries < 60 && !clear(point, placed));
    placed.push(point);
  }
  return placed;
}

function clear(p: SkyPoint, placed: SkyPoint[]): boolean {
  return (
    Math.hypot(p.x - MOON.x, p.y - MOON.y) >= 48 &&
    !(p.x >= 105 && p.x <= 255 && p.y > 205) &&
    placed.every((q) => Math.hypot(q.x - p.x, q.y - p.y) >= 34)
  );
}
