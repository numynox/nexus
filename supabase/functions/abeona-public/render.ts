// What a share link shows, rendered from abeona_shared_trip(): a calendar
// file (one event per ride) or the status of the trip for whoever picks the
// traveller up. Pure functions, so the tests can pin them.

/** A stop as abeona_shared_trip() hands it over. */
export interface SharedStop {
  name: string;
  planned: string | null;
  current: string | null;
  planned_platform: string | null;
  platform: string | null;
}

export interface SharedLeg {
  uid: string;
  mode: string;
  product: string | null;
  line_name: string | null;
  headsign: string | null;
  from: SharedStop;
  to: SharedStop;
  cancelled: boolean;
  realtime: boolean;
  final: boolean;
}

export interface SharedTrip {
  kind: "ics" | "status";
  expires_at: string;
  trip: {
    uid: string;
    origin: string;
    destination: string;
    planned_departure: string;
    planned_arrival: string;
    cancelled: boolean;
    final: boolean;
    refreshed_at: string | null;
  };
  legs: SharedLeg[];
}

const ZONE = "Europe/Berlin";

const ms = (iso: string | null | undefined) => (iso ? Date.parse(iso) : NaN);
const expected = (s: SharedStop) => s.current ?? s.planned;

/** Whole minutes late, never negative; 0 without a live time. */
export function delayOf(stop: SharedStop): number {
  if (!stop.current || !stop.planned) return 0;
  return Math.max(0, Math.round((ms(stop.current) - ms(stop.planned)) / 60000));
}

function hhmm(iso: string | null): string {
  if (!iso) return "–";
  return new Intl.DateTimeFormat("en-GB", { timeZone: ZONE, hour: "2-digit", minute: "2-digit" }).format(new Date(iso));
}

function rides(legs: SharedLeg[]): SharedLeg[] {
  return legs.filter((l) => l.mode !== "walk");
}

// ── Calendar ─────────────────────────────────────────────────────────────

function icsDate(iso: string): string {
  return new Date(iso).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

function icsText(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/;/g, "\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");
}

/** RFC 5545 folding: lines of at most 75 octets, continued with a space. */
function fold(line: string): string {
  const bytes = new TextEncoder().encode(line);
  if (bytes.length <= 75) return line;
  const out: string[] = [];
  let current = "";
  let size = 0;
  for (const ch of line) {
    const n = new TextEncoder().encode(ch).length;
    if (size + n > (out.length === 0 ? 75 : 74)) {
      out.push(current);
      current = "";
      size = 0;
    }
    current += ch;
    size += n;
  }
  out.push(current);
  return out.join("\r\n ");
}

function platformText(stop: SharedStop): string {
  const p = stop.platform ?? stop.planned_platform;
  return p ? `, platform ${p}` : "";
}

/**
 * The trip as a calendar: one event per ride at its expected times, with
 * the delay in the title. A calendar that subscribes refetches it, so the
 * times follow the live values while the link lives.
 */
export function toIcs(shared: SharedTrip, now = new Date()): string {
  const stamp = icsDate(now.toISOString());
  // Monotonic, so a calendar takes each refetch as the newer version.
  const sequence = Math.floor(now.getTime() / 60000) - 29_000_000;
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Nexus//Abeona//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    `X-WR-CALNAME:${icsText(`${shared.trip.origin} → ${shared.trip.destination}`)}`,
    "REFRESH-INTERVAL;VALUE=DURATION:PT15M",
    "X-PUBLISHED-TTL:PT15M",
  ];
  for (const leg of rides(shared.legs)) {
    const start = expected(leg.from);
    const end = expected(leg.to);
    if (!start || !end) continue;
    const delay = delayOf(leg.to);
    const line = leg.line_name ?? leg.product ?? "Train";
    const cancelled = leg.cancelled || shared.trip.cancelled;
    const summary = `${cancelled ? "Cancelled: " : ""}${line} ${leg.from.name} → ${leg.to.name}${delay > 0 ? ` (+${delay})` : ""}`;
    const description = [
      `${leg.from.name} ${hhmm(leg.from.planned)}${platformText(leg.from)}`,
      `${leg.to.name} ${hhmm(leg.to.planned)}${platformText(leg.to)}`,
      leg.from.current || leg.to.current
        ? `Expected ${hhmm(expected(leg.from))}–${hhmm(expected(leg.to))}${delay > 0 ? `, ${delay} min late` : ", on time"}`
        : "Timetable times; no live data.",
      leg.headsign ? `Towards ${leg.headsign}` : null,
      "Shared from Abeona. Times in Europe/Berlin.",
    ].filter(Boolean).join("\n");
    lines.push(
      "BEGIN:VEVENT",
      `UID:${leg.uid}@abeona.nexus`,
      `DTSTAMP:${stamp}`,
      `SEQUENCE:${sequence}`,
      `DTSTART:${icsDate(start)}`,
      `DTEND:${icsDate(end)}`,
      `SUMMARY:${icsText(summary)}`,
      `LOCATION:${icsText(`${leg.from.name}${platformText(leg.from)}`)}`,
      `DESCRIPTION:${icsText(description)}`,
      `STATUS:${cancelled ? "CANCELLED" : "CONFIRMED"}`,
      "TRANSP:OPAQUE",
      "END:VEVENT",
    );
  }
  lines.push("END:VCALENDAR");
  return lines.map(fold).join("\r\n") + "\r\n";
}

// ── Status ───────────────────────────────────────────────────────────────

export type ShareState = "upcoming" | "on_board" | "changing" | "arrived" | "cancelled";

export interface StatusLeg {
  line_name: string | null;
  product: string | null;
  from: SharedStop;
  to: SharedStop;
  cancelled: boolean;
  delay_minutes: number;
}

/** The status page's JSON; the page (app/abeona/src/pages/status.astro) only formats it. */
export interface ShareStatus {
  origin: string;
  destination: string;
  state: ShareState;
  /** The ride the traveller is on, or the next one; the last one once arrived. */
  current: StatusLeg | null;
  planned_arrival: string;
  expected_arrival: string;
  delay_minutes: number;
  /** Whether any time here is live, not just the timetable. */
  live: boolean;
  final: boolean;
  legs: StatusLeg[];
  updated_at: string;
  expires_at: string;
}

function statusLeg(leg: SharedLeg): StatusLeg {
  return {
    line_name: leg.line_name,
    product: leg.product,
    from: leg.from,
    to: leg.to,
    cancelled: leg.cancelled,
    delay_minutes: delayOf(leg.to),
  };
}

export function toStatus(shared: SharedTrip, now = new Date(), updatedAt = now.toISOString()): ShareStatus {
  const list = rides(shared.legs);
  const t = now.getTime();
  let state: ShareState = "arrived";
  let current: SharedLeg | null = list[list.length - 1] ?? null;

  if (shared.trip.cancelled) {
    state = "cancelled";
    current = list[0] ?? null;
  } else {
    for (let i = 0; i < list.length; i++) {
      const dep = ms(expected(list[i].from));
      const arr = ms(expected(list[i].to));
      if (t < dep) {
        state = i === 0 ? "upcoming" : "changing";
        current = list[i];
        break;
      }
      if (t <= arr) {
        state = "on_board";
        current = list[i];
        break;
      }
    }
  }

  const last = list[list.length - 1];
  const plannedArrival = last?.to.planned ?? shared.trip.planned_arrival;
  const expectedArrival = (last && expected(last.to)) ?? plannedArrival;
  return {
    origin: shared.trip.origin,
    destination: shared.trip.destination,
    state,
    current: current ? statusLeg(current) : null,
    planned_arrival: plannedArrival,
    expected_arrival: expectedArrival,
    delay_minutes: last ? delayOf(last.to) : 0,
    live: list.some((l) => l.realtime || l.from.current !== null || l.to.current !== null),
    final: shared.trip.final,
    legs: list.map(statusLeg),
    updated_at: updatedAt,
    expires_at: shared.expires_at,
  };
}
