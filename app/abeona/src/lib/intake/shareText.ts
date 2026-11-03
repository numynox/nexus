/**
 * DB Navigator's "Teilen" text for a connection → the rides it lists.
 * Mirrored in Kotlin (domain/intake/ShareText.kt); both run the cases in
 * intake.fixtures.json against the real, anonymised samples in fixtures/.
 *
 * The shape, as DB Navigator wrote it in November 2026:
 *
 *   Erlangen → Leuven
 *   Fr. 14.11.2025
 *
 *   RE19 (4901)
 *   Nach Nürnberg Hbf                    ← optional
 *   Ab 06:29 Erlangen, Gleis 4           ← ", Gleis …" optional
 *   An 06:48 Nürnberg Hbf, Gleis 4 C-F   ← sectors are dropped
 *   …
 *   Verbindung ansehen: https://www.bahn.de/buchung/start?vbid=…
 *
 * The English words ("Dep", "Arr", "To", "Platform") are accepted too, but no
 * English sample has been seen yet.
 */
import { hhmm, isoDate, lines, nextDay, platformOf, productOf, squash } from "./common.ts";
import type { ParsedLeg, ParsedShare } from "./parsed.ts";

const DEPART = /^(?:ab|dep\.?|departure)\s+(\d{1,2}:\d{2})\s+(.+)$/i;
const ARRIVE = /^(?:an|arr\.?|arrival)\s+(\d{1,2}:\d{2})\s+(.+)$/i;
const HEADSIGN = /^(?:nach|to|towards|direction)\s+(.+)$/i;
const PLATFORM = /^(.*?),\s*(?:gleis|platform|track|pl\.|bstg\.?)\s+(.+)$/i;
const DATE = /(\d{1,2})\.(\d{1,2})\.(\d{4})/;
const LINK = /https?:\/\/\S+/;
const HEADER = /^(.+?)\s*(?:→|->|➞|⇒)\s*(.+)$/;

/** "RE19 (4901)" → line "RE19", number "4901". */
function lineOf(raw: string): { line: string; number: string | null } {
  const match = /^(.*?)\s*\((\d{1,6})\)\s*$/.exec(raw);
  return match ? { line: squash(match[1]), number: match[2] } : { line: squash(raw), number: null };
}

function stationAndPlatform(rest: string): { station: string; platform: string | null } {
  const match = PLATFORM.exec(rest);
  return match
    ? { station: squash(match[1]), platform: platformOf(match[2]) }
    : { station: squash(rest), platform: null };
}

/** Whether the text looks like a DB Navigator share at all: an "Ab …" and an "An …" line. */
export function isShareText(text: string): boolean {
  const all = lines(text).map((l) => l.trim());
  return all.some((l) => DEPART.test(l)) && all.some((l) => ARRIVE.test(l));
}

/** Null when the text has no ride in it. */
export function parseShareText(text: string): ParsedShare | null {
  const all = lines(text).map((l) => l.trim());
  let origin: string | null = null;
  let destination: string | null = null;
  let date: string | null = null;
  let link: string | null = null;

  const legs: ParsedLeg[] = [];
  /** The last line that was none of the known kinds: the next ride's line name. */
  let label: string | null = null;
  let headsign: string | null = null;
  let departure: { time: string; station: string; platform: string | null } | null = null;
  // Times only ever move forward; one that goes back has crossed midnight.
  let day: string | null = null;
  let lastTime = "";

  const advance = (time: string) => {
    if (day && lastTime && time < lastTime) day = nextDay(day);
    lastTime = time;
    return day ?? "";
  };

  for (const line of all) {
    if (line === "") continue;
    const url = LINK.exec(line);
    if (url) {
      link ??= url[0];
      continue;
    }
    if (origin === null && legs.length === 0 && !departure) {
      const header = HEADER.exec(line);
      if (header) {
        origin = squash(header[1]);
        destination = squash(header[2]);
        continue;
      }
    }
    if (date === null) {
      const d = DATE.exec(line);
      if (d && !DEPART.test(line) && !ARRIVE.test(line)) {
        date = isoDate(d[1], d[2], d[3]);
        day = date;
        continue;
      }
    }
    const dep = DEPART.exec(line);
    if (dep) {
      const time = hhmm(dep[1]);
      if (!time) continue;
      const { station, platform } = stationAndPlatform(dep[2]);
      departure = { time, station, platform };
      continue;
    }
    const arr = ARRIVE.exec(line);
    if (arr) {
      const time = hhmm(arr[1]);
      if (!time || !departure || !label) {
        departure = null;
        continue;
      }
      const { station, platform } = stationAndPlatform(arr[2]);
      const { line: name, number } = lineOf(label);
      const depDate = advance(departure.time);
      const arrDate = advance(time);
      legs.push({
        line: name,
        product: productOf(name),
        number,
        headsign,
        from: departure.station,
        to: station,
        date: depDate,
        departure: departure.time,
        arrival_date: arrDate,
        arrival: time,
        from_platform: departure.platform,
        to_platform: platform,
        reservation: null,
      });
      label = null;
      headsign = null;
      departure = null;
      continue;
    }
    const towards = HEADSIGN.exec(line);
    if (towards && label && !departure) {
      headsign = squash(towards[1]);
      continue;
    }
    // Anything else names the next ride (and a stray line is overwritten by it).
    if (!departure) {
      label = line;
      headsign = null;
    }
  }

  if (legs.length === 0 || date === null) return null;
  const vbid = link ? (/[?&#]vbid=([^&#\s]+)/.exec(link)?.[1] ?? null) : null;
  return {
    kind: "share",
    origin: origin ?? legs[0].from,
    destination: destination ?? legs[legs.length - 1].to,
    date,
    legs,
    link,
    vbid,
  };
}
