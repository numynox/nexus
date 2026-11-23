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

export const SCENE_HEIGHT: Record<SceneSize, number> = { strip: 64, header: 96, full: 200 };
/** How much larger than the strip's the train is drawn. */
export const TRAIN_SCALE: Record<SceneSize, number> = { strip: 1, header: 1.5, full: 1.9 };

/** How much larger than the strip's the hills and trees are drawn, growing from the rail. */
export const LAND_SCALE: Record<SceneSize, number> = { strip: 1, header: 1.35, full: 2.1 };

/** The top of the rail above the bottom edge. */
export const RAIL = 12;
/**
 * The ride's progress bar in the strip: a thick bar in the ground under the
 * track, so the train rides above it and never hides it. The ground is this
 * deep then; the bar's centre is BAR_UP above the bottom edge.
 */
export const RAIL_WITH_BAR = 20;
export const BAR_UP = 8;
export const BAR = 6;
export const BAR_DOT = 4.6;
export const BAR_DOT_END = 6;
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
 * doors, `bus` a bus. Every train has an end car at both ends, as the real
 * sets do. Lengths are per car at scale 1 (`head` is an end car); the nose is
 * at x = 0 and the cars trail to the left.
 */
export type TrainKind = "ice" | "regional" | "suburban" | "bus";

export const TRAINS: Record<TrainKind, { cars: number; head: number; car: number; height: number; gap: number }> = {
  ice: { cars: 4, head: 44, car: 38, height: 13, gap: 1.2 },
  regional: { cars: 3, head: 38, car: 38, height: 17, gap: 1.5 },
  suburban: { cars: 3, head: 31, car: 30, height: 13, gap: 1.2 },
  bus: { cars: 1, head: 30, car: 0, height: 11, gap: 0 },
};

/** The whole set: an end car at each end (the same, mirrored) and the cars between; a bus is its one body. */
export function trainLength(kind: TrainKind): number {
  const t = TRAINS[kind];
  if (t.cars < 2) return t.head;
  return 2 * t.head + (t.cars - 2) * t.car + (t.cars - 1) * t.gap;
}

/** Where the train's nose stands off the rail: far enough right for the whole set to show when the box has the room. */
export function trainNoseX(kind: TrainKind, scale: number, width: number): number {
  return Math.min(width - 60, Math.max(width * 0.56, trainLength(kind) * scale + 12));
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
  /** The brand burgundy: the rail behind the train, passed stops, the flag. */
  accent: string;
  trainWindow: string;
  trainLine: string;
  platform: string;
  dot: string;
  /** The change's confidence on the bar's walk (safe, tight, at risk): dark enough on the ground in a light theme, light in a dark one. */
  ok: string;
  watch: string;
  bad: string;
}

type Tones = Omit<ScenePalette, "trainRed" | "accent" | "trainLine" | "dot" | "ok" | "watch" | "bad">;

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
    accent: dark ? "#f08ca8" : "#8c1d40",
    trainLine: dark ? "#00000000" : "#2a211c40",
    dot: dark ? "#26201c" : "#ffffff",
    ok: dark ? "#7fd3a8" : "#1f7a50",
    watch: dark ? "#f2c94c" : "#9a6a00",
    bad: dark ? "#ff8a7a" : "#b3261e",
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

// ------------------------------------------------------------------ the journey bar

/**
 * The strip's bar is the whole journey (abeona-design.md §7, §8). The
 * current ride, the one on board or else the one to board next, takes the
 * middle with its stops and its progress; every earlier ride is one small
 * filled dot at the left, every later one a hollow dot at the right. On foot
 * into the current ride (a change, or the way to the first train) a dashed
 * connector leads from the last ride done to it, with a walker on it as far
 * along as the time allows, in the change's confidence colour (neutral on
 * the way to the station).
 *
 * Worked out from the clock with the expected times, on every frame of the
 * scene, so the train and the walker move continuously rather than minute by
 * minute.
 */
export type BarState = "before_leaving" | "at_station" | "changing" | "on_board" | "arrived";
export type BarTone = "ok" | "watch" | "bad" | "neutral";

/** A ride as the bar reads it: its expected departure and arrival, and the expected arrivals at the stops between (ms). */
export interface BarRide {
  departure: number;
  arrival: number;
  stops: number[];
}

export interface BarJourney {
  /** The rides, walks left out. */
  rides: BarRide[];
  /** The ride on board, or the one to board next; the last one once arrived. */
  current: number;
  state: BarState;
  /** Before leaving: when to set off (ms). */
  leaveAt: number | null;
  /** Changing: the change into the current ride. */
  confidence: "safe" | "tight" | "at_risk" | "impossible" | null;
}

export interface JourneyBar {
  /** Rides before the current one: a filled dot each. */
  before: number;
  /** Rides after it: a hollow dot each. */
  after: number;
  /** The current ride's stops as fractions of it: 0, those between by time, 1. */
  stops: number[];
  /** How far along the current ride the train is: 0 until it leaves (and while waiting for it), 1 once arrived. */
  fraction: number;
  /** On foot into the current ride: how far along the time on foot, and the colour of the connector. */
  walk: { fraction: number; tone: BarTone } | null;
}

const TONE: Record<NonNullable<BarJourney["confidence"]>, BarTone> = { safe: "ok", tight: "watch", at_risk: "bad", impossible: "bad" };

/** The share of [from, to] that lies behind `now`; a span of nothing counts as a millisecond. */
function along(now: number, from: number, to: number): number {
  return clamp01((now - from) / Math.max(to - from, 1));
}

/** The bar at `now`. */
export function journeyBar(j: BarJourney, now: number): JourneyBar {
  const n = j.rides.length;
  if (n === 0) return { before: 0, after: 0, stops: [], fraction: 0, walk: null };
  const arrived = j.state === "arrived";
  const c = arrived ? n - 1 : Math.max(0, Math.min(n - 1, j.current));
  const ride = j.rides[c];
  const stops = [0, ...ride.stops.map((t) => along(t, ride.departure, ride.arrival)), 1];
  const fraction = arrived ? 1 : j.state === "on_board" ? along(now, ride.departure, ride.arrival) : 0;
  let walk: JourneyBar["walk"] = null;
  if (j.state === "before_leaving") {
    // The walker waits at the start until it is time to leave, then walks to the train.
    walk = { fraction: j.leaveAt === null ? 0 : along(now, j.leaveAt, ride.departure), tone: "neutral" };
  } else if (j.state === "changing" && c > 0) {
    walk = { fraction: along(now, j.rides[c - 1].arrival, ride.departure), tone: j.confidence ? TONE[j.confidence] : "neutral" };
  }
  return { before: c, after: n - 1 - c, stops, fraction, walk };
}

/** A ride's dot on the bar (radius), the distance between two, and from a dot to the current ride's end. */
export const RIDE_DOT = 3;
export const RIDE_PITCH = 11;
export const RIDE_GAP = 14;
/** The walk's connector: from the last ride done (or the bar's start) to the current ride. */
export const WALK_LENGTH = 40;
/** The bar keeps this much clear at both edges of the strip. */
export const BAR_PAD = 16;
/** The current ride keeps at least this share of the bar (the strip less its pads): the dots crowd closer first. */
export const SEGMENT_SHARE = 0.75;

export interface BarLayout {
  /** Every ride's stretch of the bar, left to right (x): a dot where from = to, the current ride from its first stop to its last. */
  rides: { from: number; to: number }[];
  /** The walk's connector into the current ride, when on foot. */
  walk: { from: number; to: number } | null;
}

/**
 * Where the bar's parts lie on a strip `width` wide. With nothing before it
 * and no walk, the current ride runs from RAIL_START to RAIL_END as the
 * ride's rail always did; the dots and the walk sit beside it, and crowd
 * closer when there are so many that the ride would get less than
 * SEGMENT_SHARE of the bar.
 */
export function barLayout(bar: Pick<JourneyBar, "before" | "after" | "walk">, width: number): BarLayout {
  const { before, after } = bar;
  const walking = bar.walk !== null;
  const lead = walking ? WALK_LENGTH : before > 0 ? RIDE_GAP : 0;
  const left = lead + Math.max(0, before - 1) * RIDE_PITCH;
  const right = after > 0 ? RIDE_GAP + (after - 1) * RIDE_PITCH : 0;
  const end0 = after > 0 ? width - BAR_PAD : width - RAIL_END;
  let k = 1;
  let start = Math.max(RAIL_START, BAR_PAD + left);
  const least = SEGMENT_SHARE * (width - 2 * BAR_PAD);
  if (end0 - right - start < least && left + right > 0) {
    k = clamp01((end0 - BAR_PAD - least) / (left + right));
    start = BAR_PAD + left * k;
  }
  const end = end0 - right * k;
  const rides: BarLayout["rides"] = [];
  const lastBefore = start - lead * k;
  for (let i = 0; i < before; i++) {
    const x = lastBefore - (before - 1 - i) * RIDE_PITCH * k;
    rides.push({ from: x, to: x });
  }
  rides.push({ from: start, to: end });
  for (let i = 0; i < after; i++) {
    const x = end + (RIDE_GAP + i * RIDE_PITCH) * k;
    rides.push({ from: x, to: x });
  }
  return { rides, walk: walking ? { from: start - WALK_LENGTH * k, to: start } : null };
}

/** Where on the current ride a fraction of it lies: where the train's nose is. */
export function segmentX(layout: BarLayout, current: number, fraction: number): number {
  const r = layout.rides[current];
  return r.from + (r.to - r.from) * clamp01(fraction);
}

/** How long the bar glides to a value live data moved, and morphs from one state to the next (ms). */
export const GLIDE_MS = 1000;
export const MORPH_MS = 600;
