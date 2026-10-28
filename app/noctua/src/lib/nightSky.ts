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

/** Where the moon by night, or the sun by day, sits in the scene. */
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

/** A small seeded generator (mulberry32), so the stars are the same every night. */
function seeded(seed: number): () => number {
  let state = seed | 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export interface SkyStar {
  x: number;
  y: number;
  r: number;
  /** Seconds into its twinkle, so they do not all pulse together. */
  delay: number;
}

/**
 * The night's stars: the same few dots every night, over the sky above the
 * branch — some higher than the scene, for a tall box — clear of the moon and
 * the owl's head.
 */
export const SKY_STARS: SkyStar[] = (() => {
  const random = seeded(7);
  const stars: SkyStar[] = [];
  while (stars.length < 34) {
    const x = 12 + random() * 336;
    const y = -320 + random() * 560;
    const r = 0.7 + random() * 0.9;
    const delay = random() * 4;
    const clear =
      Math.hypot(x - MOON.x, y - MOON.y) > 46 &&
      !(x >= 110 && x <= 250 && y > 190) &&
      stars.every((s) => Math.hypot(s.x - x, s.y - y) >= 26);
    if (clear) stars.push({ x, y, r, delay });
  }
  return stars;
})();

/** The branch comes in from beyond the left edge and ends in a tip right of the owl. */
export const BRANCH_START_X = -220;
export const BRANCH_TIP_X = 326;

/** Where the branch's centre line runs at `x`, sagging by `bend` under the owl. */
export function branchY(x: number, bend: number): number {
  const t = (x + 220) / 800;
  return (1 - t) ** 2 * 436 + 2 * t * (1 - t) * (418 + 2 * bend) + t * t * 402;
}

/** How thick the branch is at `x`: stout where it comes in, slender towards its tip. */
export function branchWidth(x: number): number {
  const t = Math.min(1, Math.max(0, (x - BRANCH_START_X) / (BRANCH_TIP_X - BRANCH_START_X)));
  return 15 - 9 * t;
}

/** The branch's outline as SVG path data: tapering, with a rounded tip. */
export function branchPath(bend: number): string {
  const steps = 48;
  const xs = Array.from(
    { length: steps + 1 },
    (_, i) => BRANCH_START_X + ((BRANCH_TIP_X - BRANCH_START_X) * i) / steps,
  );
  const top = xs.map((x) => `${x.toFixed(1)} ${(branchY(x, bend) - branchWidth(x) / 2).toFixed(1)}`);
  const bottom = [...xs]
    .reverse()
    .map((x) => `${x.toFixed(1)} ${(branchY(x, bend) + branchWidth(x) / 2).toFixed(1)}`);
  const tipY = branchY(BRANCH_TIP_X, bend);
  const tip = `Q ${BRANCH_TIP_X + 7} ${tipY.toFixed(1)} ${BRANCH_TIP_X} ${(tipY + branchWidth(BRANCH_TIP_X) / 2).toFixed(1)}`;
  return `M ${top.join(" L ")} ${tip} L ${bottom.join(" L ")} Z`;
}

/** The lighter line along the branch's upper edge. */
export function branchHighlight(bend: number): string {
  const points: string[] = [];
  for (let x = -200; x < BRANCH_TIP_X - 12; x += 12) {
    points.push(`${x} ${(branchY(x, bend) - branchWidth(x) / 2 + 1.6).toFixed(1)}`);
  }
  return `M ${points.join(" L ")}`;
}
