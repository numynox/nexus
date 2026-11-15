/**
 * The scene (abeona-design.md §7), worked out without drawing anything, so it
 * can be tested: the sky's light from the real time of day, its colours, and
 * the land's shape. Android's ui/scene/SceneModel.kt is the same code in
 * Kotlin; scene.fixtures.json holds the cases both test suites run.
 *
 * Units: one unit is one dp or CSS pixel. x runs right; heights are measured
 * up from the bottom edge of the box, so a taller box only adds sky.
 */

export type SceneState = "idle" | "parked" | "waiting" | "riding" | "stopped" | "arrived";
export type SceneSize = "strip" | "header" | "full";

export const SCENE_HEIGHT: Record<SceneSize, number> = { strip: 56, header: 96, full: 200 };
/** How much larger than the strip's the train is drawn. */
export const TRAIN_SCALE: Record<SceneSize, number> = { strip: 1, header: 1.5, full: 2.2 };

/** How much larger than the strip's the hills and trees are drawn, growing from the rail. */
export const LAND_SCALE: Record<SceneSize, number> = { strip: 1, header: 1.35, full: 2.1 };

/** The top of the rail above the bottom edge. */
export const RAIL = 12;
/**
 * The strip's rail keeps this much clear at its ends. The train's nose marks
 * where the ride is and its cars trail behind, so the start needs room for
 * the leading car to show at the first stop.
 */
export const RAIL_START = 64;
export const RAIL_END = 22;

/**
 * The trains, in their real liveries: the one place in Abeona where red is
 * not a status. `ice` is long-distance (white, a red stripe, a long nose),
 * `regional` a red double-deck set, `suburban` a shorter red set with many
 * doors, `bus` a bus. Lengths are per car at scale 1; the nose is at x = 0
 * and the cars trail to the left.
 */
export type TrainKind = "ice" | "regional" | "suburban" | "bus";

export const TRAINS: Record<TrainKind, { cars: number; head: number; car: number; height: number; gap: number }> = {
  ice: { cars: 4, head: 44, car: 38, height: 13, gap: 1.2 },
  regional: { cars: 3, head: 38, car: 38, height: 17, gap: 1.5 },
  suburban: { cars: 3, head: 31, car: 30, height: 13, gap: 1.2 },
  bus: { cars: 1, head: 30, car: 0, height: 11, gap: 0 },
};

export function trainLength(kind: TrainKind): number {
  const t = TRAINS[kind];
  return t.head + (t.cars - 1) * (t.car + t.gap);
}

const LONG_DISTANCE = new Set(["ICE", "IC", "EC", "ECE", "RJ", "RJX", "TGV", "NJ", "EN", "FLX", "EST", "FR", "IR"]);

/**
 * Which train a ride is drawn as, from its product ("ICE", "RE", "S") or,
 * failing that, its line's name ("S 8"); a bus by its mode. Long-distance as
 * in the line chip's colours.
 */
export function trainKind(product: string | null | undefined, line: string | null | undefined, bus: boolean): TrainKind {
  if (bus) return "bus";
  const p = (product ?? "").trim().toUpperCase();
  if (LONG_DISTANCE.has(p)) return "ice";
  const name = p || (line ?? "").trim().toUpperCase();
  if (/^S(-BAHN)?(\s|\d|$)/.test(name)) return "suburban";
  if (!p && LONG_DISTANCE.has(name.split(/\s|\d/)[0])) return "ice";
  return "regional";
}
/** The land repeats after this many units. */
export const TILE = 720;
export const MAST_EVERY = 90;
export const SLEEPER_EVERY = 12;
/** How fast each layer moves against the track. */
export const PARALLAX = { far: 0.08, near: 0.25, track: 1 } as const;

/** Where the sky is reckoned when the trip names no place: Frankfurt am Main. */
export const FRANKFURT = { lat: 50.1109, lon: 8.6821 } as const;

// ------------------------------------------------------------------ the sky

const KNOWN_NEW_MOON = Date.UTC(2000, 0, 6, 18, 14);
const SYNODIC_DAYS = 29.530588853;

/** Where the moon is in its cycle: 0 new, 0.5 full. */
export function moonPhase(at: number): number {
  const days = (at - KNOWN_NEW_MOON) / 86_400_000;
  return (((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS) / SYNODIC_DAYS;
}

export function moonPhaseName(phase: number): string {
  return ["New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent"][
    Math.round(phase * 8) % 8
  ];
}

/** The lit part of a moon of radius r at (cx, cy) as SVG path data; waxing is lit on the right. */
export function moonLitPath(phase: number, cx: number, cy: number, r: number): string {
  const rx = Math.abs(Math.cos(2 * Math.PI * phase)) * r;
  const waxing = phase < 0.5;
  const crescent = phase < 0.25 || phase > 0.75;
  const outer = waxing ? 1 : 0;
  const inner = waxing ? (crescent ? 0 : 1) : crescent ? 1 : 0;
  return `M ${cx} ${cy - r} A ${r} ${r} 0 0 ${outer} ${cx} ${cy + r} A ${rx.toFixed(2)} ${r} 0 0 ${inner} ${cx} ${cy - r} Z`;
}

/**
 * Sunrise and sunset (ms) at a place on the UTC day of `at`: NOAA's
 * approximation, within about a minute. Null in polar day or night.
 */
export function sunTimes(at: number, lat: number, lon: number): { sunrise: number; sunset: number } | null {
  const d = new Date(at);
  const midnight = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  const dayOfYear = Math.floor((midnight - Date.UTC(d.getUTCFullYear(), 0, 1)) / 86_400_000) + 1;
  const g = ((2 * Math.PI) / 365) * (dayOfYear - 1);
  const eqTime =
    229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
  const decl =
    0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) +
    0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
  const rad = Math.PI / 180;
  const cosHa = Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl)) - Math.tan(lat * rad) * Math.tan(decl);
  if (cosHa < -1 || cosHa > 1) return null;
  const ha = Math.acos(cosHa) / rad;
  const minutes = (m: number) => midnight + Math.round(m * 60_000);
  return { sunrise: minutes(720 - 4 * (lon + ha) - eqTime), sunset: minutes(720 - 4 * (lon - ha) - eqTime) };
}

export interface SkyLight {
  /** 1 by day, 0 by night, in between for 40 minutes around sunrise and sunset. */
  light: number;
  /** The orange at the horizon: 1 at sunrise and sunset, 0 an hour away from both. */
  glow: number;
  /** How far the sun (by day) or the moon (by night) is across the sky, 0 left to 1 right. */
  across: number;
  night: boolean;
}

const TWILIGHT = 40 * 60_000;
const GLOW = 60 * 60_000;
const clamp01 = (v: number) => Math.max(0, Math.min(1, v));

/** The light at a moment and place. Without sun times (polar): plain day. */
export function skyLight(at: number, lat: number = FRANKFURT.lat, lon: number = FRANKFURT.lon): SkyLight {
  const times = sunTimes(at, lat, lon);
  if (!times) return { light: 1, glow: 0, across: 0.5, night: false };
  const { sunrise, sunset } = times;
  const day = sunset - sunrise;
  const night = at < sunrise || at >= sunset;
  // Twilight is centred on the event: half lit at sunrise and at sunset.
  const light = at < (sunrise + sunset) / 2 ? clamp01((at - sunrise) / TWILIGHT + 0.5) : clamp01((sunset - at) / TWILIGHT + 0.5);
  const glow = Math.max(clamp01(1 - Math.abs(at - sunrise) / GLOW), clamp01(1 - Math.abs(at - sunset) / GLOW));
  let across: number;
  if (!night) across = (at - sunrise) / day;
  else {
    const length = 86_400_000 - day;
    across = at >= sunset ? (at - sunset) / length : 1 - (sunrise - at) / length;
  }
  return { light, glow, across: clamp01(across), night };
}

// ------------------------------------------------------------------ colours

export interface ScenePalette {
  /** The sky at the horizon; it fades to nothing at the top of the box. */
  sky: string;
  glow: string;
  far: string;
  near: string;
  tree: string;
  ground: string;
  rail: string;
  mast: string;
  cloud: string;
  star: string;
  sun: string;
  moon: string;
  trainBody: string;
  /** The livery's red: the ICE's stripe, the regional and suburban bodies. */
  trainRed: string;
  /** The brand orange: the rail behind the train, passed stops, the flag. */
  accent: string;
  trainWindow: string;
  trainLine: string;
  platform: string;
  dot: string;
}

type Tones = Omit<ScenePalette, "trainRed" | "accent" | "trainLine" | "dot">;

const LIGHT_DAY: Tones = {
  sky: "#d9e7ea", glow: "#ffc48f", far: "#c7d8c6", near: "#a6c2a0", tree: "#86a983", ground: "#e7ded3", rail: "#6b5f57",
  mast: "#8d8178", cloud: "#ffffff", star: "#8e88b5", sun: "#f5a83c", moon: "#ffffff", trainBody: "#ffffff",
  trainWindow: "#2a211c", platform: "#cfc3b6",
};
const LIGHT_NIGHT: Tones = {
  sky: "#cdc9e0", glow: "#ffc48f", far: "#b7b3cf", near: "#9995bb", tree: "#7f7aa6", ground: "#ddd5d0", rail: "#6b5f57",
  mast: "#8d8178", cloud: "#ebe8f4", star: "#7c76a8", sun: "#f5a83c", moon: "#ffffff", trainBody: "#ffffff",
  trainWindow: "#ffcf8a", platform: "#c4b9b4",
};
const DARK_DAY: Tones = {
  sky: "#2c3d44", glow: "#8a4a22", far: "#30463f", near: "#3b5a4d", tree: "#4a6f5e", ground: "#322a24", rail: "#b5a79c",
  mast: "#8a7d73", cloud: "#465a61", star: "#f3eae2", sun: "#f5a83c", moon: "#f3eae2", trainBody: "#ede4dc",
  trainWindow: "#1a1512", platform: "#4a4038",
};
const DARK_NIGHT: Tones = {
  sky: "#262238", glow: "#8a4a22", far: "#2b2842", near: "#373354", tree: "#443f66", ground: "#2a2420", rail: "#b5a79c",
  mast: "#8a7d73", cloud: "#34304c", star: "#f3eae2", sun: "#f5a83c", moon: "#f3eae2", trainBody: "#ede4dc",
  trainWindow: "#ffcf8a", platform: "#40372f",
};

/** Two "#rrggbb" colours mixed: t = 0 gives a, 1 gives b. */
export function mix(a: string, b: string, t: number): string {
  const channel = (i: number) => {
    const x = parseInt(a.slice(i, i + 2), 16);
    const y = parseInt(b.slice(i, i + 2), 16);
    return Math.round(x + (y - x) * t).toString(16).padStart(2, "0");
  };
  return `#${channel(1)}${channel(3)}${channel(5)}`;
}

/**
 * The scene's colours: the theme decides how light it is (a dark theme never
 * gets a bright band), the time of day what hue. Windows are lit from dusk.
 */
export function scenePalette(dark: boolean, light: number): ScenePalette {
  const day = dark ? DARK_DAY : LIGHT_DAY;
  const night = dark ? DARK_NIGHT : LIGHT_NIGHT;
  const tones = {} as Tones;
  for (const key of Object.keys(day) as (keyof Tones)[]) tones[key] = mix(night[key], day[key], light);
  tones.trainWindow = light < 0.5 ? night.trainWindow : day.trainWindow;
  return {
    ...tones,
    trainRed: dark ? "#d9584e" : "#d9342b",
    accent: dark ? "#ff9a52" : "#f2761b",
    trainLine: dark ? "#00000000" : "#2a211c40",
    dot: dark ? "#26201c" : "#ffffff",
  };
}

// ------------------------------------------------------------------ the land

export type HillKind = "far" | "near";

const HILLS: Record<HillKind, { base: number; waves: [amplitude: number, cycles: number, shift: number][] }> = {
  far: { base: 20, waves: [[12, 2, 1.3], [7, 5, 0.4], [3, 9, 2.2]] },
  near: { base: 13, waves: [[8, 3, 4.1], [5, 7, 0.9]] },
};

/** How high the hill of a layer stands above the bottom edge at x; repeats every TILE. */
export function hillHeight(kind: HillKind, x: number): number {
  const { base, waves } = HILLS[kind];
  let h = base;
  for (const [amplitude, cycles, shift] of waves) h += amplitude * (0.5 + 0.5 * Math.sin((2 * Math.PI * cycles * x) / TILE + shift));
  return h;
}

/** A hill layer from x = 0 to `width` as a closed SVG path in a box `height` tall, sampled every 12 units. */
export function hillPath(kind: HillKind, width: number, height: number): string {
  let d = `M 0 ${height}`;
  for (let x = 0; x <= width + 12; x += 12) d += ` L ${x} ${(height - hillHeight(kind, x)).toFixed(1)}`;
  return `${d} L ${width + 12} ${height} Z`;
}

/** Where the trees stand on the near hills, within one tile. */
export const TREES: readonly { x: number; r: number }[] = [
  { x: 38, r: 5 }, { x: 52, r: 4 }, { x: 148, r: 5.5 }, { x: 236, r: 4 }, { x: 250, r: 5 }, { x: 330, r: 4.5 },
  { x: 432, r: 5 }, { x: 446, r: 4 }, { x: 540, r: 5.5 }, { x: 618, r: 4 }, { x: 632, r: 5 }, { x: 700, r: 4.5 },
];

/** The night's stars as fractions of the box (x across, y down from the top of the sky). */
export const STARS: readonly { x: number; y: number; r: number }[] = [
  { x: 0.06, y: 0.3, r: 0.9 }, { x: 0.14, y: 0.12, r: 0.7 }, { x: 0.23, y: 0.42, r: 0.8 }, { x: 0.31, y: 0.18, r: 1 },
  { x: 0.42, y: 0.34, r: 0.7 }, { x: 0.5, y: 0.1, r: 0.9 }, { x: 0.58, y: 0.4, r: 0.8 }, { x: 0.67, y: 0.2, r: 0.7 },
  { x: 0.76, y: 0.36, r: 1 }, { x: 0.84, y: 0.14, r: 0.8 }, { x: 0.93, y: 0.3, r: 0.7 },
];

/** Where the sun or moon stands: across the box, on a shallow arc. Returns x and the height above the bottom edge. */
export function lightPosition(across: number, width: number, height: number): { x: number; up: number } {
  const margin = 26;
  const x = margin + (width - 2 * margin) * across;
  const top = height - Math.min(40, height * 0.2) - 6;
  const low = Math.min(top, 34);
  // Highest in the middle of its way; in the strip there is no room to rise.
  return { x, up: low + (top - low) * Math.sin(Math.PI * across) };
}

/** How fast the land passes while riding, in units a second, from the train's speed when it is known. */
export function travelSpeed(kmh: number | null | undefined): number {
  if (kmh == null) return 36;
  return Math.max(10, Math.min(80, 10 + kmh * 0.28));
}

/** Where on the strip's rail a fraction of the ride lies: where the train's nose is. */
export function railX(fraction: number, width: number): number {
  return RAIL_START + (width - RAIL_START - RAIL_END) * clamp01(fraction);
}
