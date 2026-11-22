/**
 * The arrival postcard (decisions/2026-11-22-abeona-arrival-postcard.md):
 * what a finished trip adds up to, as figures the card shows and the image
 * it shares. The figures come from abeona_trip_summary, so Android shows the
 * same ones; the image is drawn here, in the browser, and goes nowhere by
 * itself.
 */
import type { TripSummary } from "./data";
import { formatDurationShort, formatNumber } from "./format";

export type FigureKind = "distance" | "on_board" | "co2" | "speed" | "arrival";

export interface PostcardFigure {
  kind: FigureKind;
  /** "220 km", "2 h 29", "33 kg", "287 km/h", "+22 min", "On time". */
  value: string;
  /** "travelled", "on board", "CO₂ saved", "top speed", "at the end". */
  label: string;
  /** The arrival is a status: late in watch, on time in ok. The rest is ink. */
  tone: "ink" | "watch" | "ok";
}

/** A delay under this many minutes counts as on time, as in the year in review. */
export const ON_TIME_MINUTES = 6;

/** The figures in their order; one that is 0 or unknown is left out, the arrival never. */
export function postcardFigures(s: TripSummary): PostcardFigure[] {
  const out: PostcardFigure[] = [];
  if (s.km > 0) out.push({ kind: "distance", value: `${formatNumber(s.km)} km`, label: "travelled", tone: "ink" });
  if (s.minutes_on_train > 0) out.push({ kind: "on_board", value: formatDurationShort(s.minutes_on_train), label: "on board", tone: "ink" });
  const co2 = Math.round(s.co2_saved_kg * 10) / 10;
  if (co2 > 0) out.push({ kind: "co2", value: `${formatNumber(co2, co2 < 10 ? 1 : 0)} kg`, label: "CO₂ saved", tone: "ink" });
  if (s.top_speed_kmh !== null && s.top_speed_kmh > 0) out.push({ kind: "speed", value: `${Math.round(s.top_speed_kmh)} km/h`, label: "top speed", tone: "ink" });
  const late = Math.round(s.delay_minutes);
  out.push(
    late >= ON_TIME_MINUTES
      ? { kind: "arrival", value: `+${late} min`, label: "at the end", tone: "watch" }
      : { kind: "arrival", value: "On time", label: "at the end", tone: "ok" },
  );
  return out;
}

// ── The image ──────────────────────────────────────────────────────

export const POSTCARD_WIDTH = 1080;
export const POSTCARD_HEIGHT = 1350;

export interface PostcardDrawing {
  /** The arrival scene as drawn on the page (Scene.svelte, state arrived): serialised, then scaled to the full width. */
  scene: SVGSVGElement;
  route: string;
  date: string;
  figures: PostcardFigure[];
  /** Each figure's icon as the card draws it, in the figures' order. */
  icons: (SVGSVGElement | null)[];
}

/** A CSS colour, var() and color-mix() included, as the browser resolves it: canvas cannot read our tokens. */
function resolved(css: string): string {
  const probe = document.createElement("span");
  probe.style.color = css;
  probe.style.display = "none";
  document.body.appendChild(probe);
  const value = getComputedStyle(probe).color;
  probe.remove();
  return value;
}

async function svgImage(svg: SVGSVGElement, width: number, height: number, colour?: string): Promise<HTMLImageElement> {
  const copy = svg.cloneNode(true) as SVGSVGElement;
  copy.setAttribute("xmlns", "http://www.w3.org/2000/svg");
  copy.setAttribute("width", String(width));
  copy.setAttribute("height", String(height));
  // Icons draw in currentColor, which an image of its own does not inherit.
  if (colour) copy.setAttribute("color", colour);
  const source = new XMLSerializer().serializeToString(copy);
  const image = new Image();
  image.decoding = "async";
  image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(source)}`;
  await image.decode();
  return image;
}

/** Lines of at most `width`, at most `max` of them, the last cut with an ellipsis. */
function wrap(ctx: CanvasRenderingContext2D, text: string, width: number, max: number): string[] {
  const words = text.split(/(?<=[\s/)→-])/);
  const lines: string[] = [];
  let line = "";
  for (const word of words) {
    const next = line + word;
    if (ctx.measureText(next.trimEnd()).width <= width || line === "") line = next;
    else {
      lines.push(line.trimEnd());
      line = word.trimStart();
    }
  }
  if (line) lines.push(line.trimEnd());
  if (lines.length <= max) return lines;
  const kept = lines.slice(0, max);
  let last = kept[max - 1];
  while (last.length > 1 && ctx.measureText(`${last}…`).width > width) last = last.slice(0, -1);
  kept[max - 1] = `${last.trimEnd()}…`;
  return kept;
}

/** The largest font size from `max` down to `min` at which `text` fits `width`. */
function fitted(ctx: CanvasRenderingContext2D, text: string, weight: number, font: string, max: number, min: number, width: number): number {
  let size = max;
  ctx.font = `${weight} ${size}px ${font}`;
  while (size > min && ctx.measureText(text).width > width) {
    size -= 2;
    ctx.font = `${weight} ${size}px ${font}`;
  }
  return size;
}

/**
 * The postcard as a 1080 × 1350 PNG in the page's theme: the arrival scene
 * across the top, the route and the date, the figures in three columns, a
 * small wordmark.
 */
export async function drawPostcard(p: PostcardDrawing): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = POSTCARD_WIDTH;
  canvas.height = POSTCARD_HEIGHT;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("This browser cannot draw the postcard.");

  const colour = {
    paper: resolved("var(--color-base-100)"),
    ink: resolved("var(--ab-ink)"),
    muted: resolved("var(--ab-muted)"),
    brand: resolved("var(--ab-brand)"),
    watch: resolved("var(--ab-watch-ink)"),
    ok: resolved("var(--ab-ok-ink)"),
  };
  const font = getComputedStyle(document.body).fontFamily || "system-ui, sans-serif";
  const W = POSTCARD_WIDTH;
  const pad = 72;

  ctx.fillStyle = colour.paper;
  ctx.fillRect(0, 0, W, POSTCARD_HEIGHT);

  // The scene keeps its proportions: as wide as the card, as tall as that makes it.
  const box = p.scene.viewBox.baseVal;
  const sceneHeight = Math.round(W * (box && box.width ? box.height / box.width : 0.5));
  ctx.drawImage(await svgImage(p.scene, W, sceneHeight), 0, 0, W, sceneHeight);

  let y = sceneHeight + 90;
  ctx.textBaseline = "alphabetic";
  ctx.fillStyle = colour.ink;
  ctx.font = `600 60px ${font}`;
  for (const line of wrap(ctx, p.route, W - 2 * pad, 2)) {
    ctx.fillText(line, pad, y);
    y += 72;
  }
  ctx.fillStyle = colour.muted;
  ctx.font = `500 38px ${font}`;
  ctx.fillText(p.date, pad, y - 12);
  y += 40;

  const columns = 3;
  const cell = (W - 2 * pad) / columns;
  const tone = { ink: colour.brand, watch: colour.watch, ok: colour.ok };
  const icons = await Promise.all(p.icons.map((icon, i) => (icon ? svgImage(icon, 56, 56, tone[p.figures[i].tone]).catch(() => null) : null)));
  p.figures.forEach((figure, i) => {
    const x = pad + (i % columns) * cell;
    const top = y + Math.floor(i / columns) * 220;
    const icon = icons[i];
    if (icon) ctx.drawImage(icon, x, top, 56, 56);
    ctx.fillStyle = figure.tone === "ink" ? colour.ink : tone[figure.tone];
    // A long value ("287 km/h") gets smaller rather than cut.
    fitted(ctx, figure.value, 600, font, 64, 40, cell - 24);
    ctx.fillText(wrap(ctx, figure.value, cell - 24, 1)[0] ?? "", x, top + 56 + 76);
    ctx.fillStyle = colour.muted;
    ctx.font = `500 34px ${font}`;
    ctx.fillText(wrap(ctx, figure.label, cell - 24, 1)[0] ?? "", x, top + 56 + 76 + 48);
  });

  ctx.fillStyle = colour.brand;
  ctx.font = `700 36px ${font}`;
  ctx.fillText("Abeona", pad, POSTCARD_HEIGHT - 64);

  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("The postcard could not be drawn."))), "image/png"),
  );
}
