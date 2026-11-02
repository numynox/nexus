/**
 * The rules both clients apply to live values: delays, transfer confidence and
 * whether a ticket's train binding is lifted.
 *
 * Written twice on purpose — here and in android/abeona (TransferRules.kt) —
 * and the binding rule a third time in SQL (abeona_train_binding_lifted).
 * Change them together (.agents/brain/decisions/2026-11-02-abeona-train-companion.md).
 *
 * Kept free of runtime imports so `node --test` can run it as it is
 * (src/lib/transfer.test.ts, `npm run test:abeona`).
 */
import type { Leg } from "./transit";

export type Confidence = "safe" | "tight" | "at_risk";

/** The part of a leg the rules read; saved legs and search results both fit. */
export type RuleLeg = Pick<Leg, "mode" | "cancelled"> & {
  from: Pick<Leg["from"], "id" | "planned" | "current" | "planned_platform" | "platform">;
  to: Pick<Leg["to"], "id" | "planned" | "current" | "planned_platform" | "platform">;
};

export interface Transfer {
  /** Index of the arriving leg. */
  fromIndex: number;
  /** Index of the next non-walk leg, the one to catch. */
  toIndex: number;
  availableMinutes: number;
  requiredMinutes: number;
  marginMinutes: number;
  /** Planned duration of the walk legs in between, 0 without any. */
  walkMinutes: number;
  /** Whether the change is between two different stops. */
  changesStation: boolean;
  confidence: Confidence;
}

const MINUTE = 60_000;

function ms(value: string | null | undefined): number | null {
  if (!value) return null;
  const t = Date.parse(value);
  return Number.isNaN(t) ? null : t;
}

/** round((current - planned) / 1 min); 0 when either is missing. */
export function delayMinutes(
  planned: string | null | undefined,
  current: string | null | undefined,
): number {
  const p = ms(planned);
  const c = ms(current);
  if (p === null || c === null) return 0;
  return Math.round((c - p) / MINUTE);
}

/** The best time known for a stop: the current estimate, else the plan. */
export function effectiveTime(stop: { planned: string | null; current: string | null }): string | null {
  return stop.current ?? stop.planned;
}

/**
 * "Gleis 7 ", "platform 7" and "7" are the same platform. Null when there is
 * nothing to compare.
 */
export function normalisePlatform(value: string | null | undefined): string | null {
  if (value === null || value === undefined) return null;
  let p = value.trim().toUpperCase();
  for (const prefix of ["GLEIS ", "PLATFORM "]) {
    if (p.startsWith(prefix)) p = p.slice(prefix.length).trim();
  }
  return p === "" ? null : p;
}

function platformOf(stop: { platform: string | null; planned_platform: string | null }): string | null {
  return normalisePlatform(stop.platform ?? stop.planned_platform);
}

/** Planned minutes of a leg, 0 when a time is missing. */
function plannedMinutes(leg: RuleLeg): number {
  const a = ms(leg.from.planned);
  const b = ms(leg.to.planned);
  if (a === null || b === null) return 0;
  return Math.max(0, Math.round((b - a) / MINUTE));
}

/**
 * The transfer between arriving leg `a` and the leg `b` to catch, with the
 * walk legs between them.
 *
 * - available = (b's departure, current else planned) − (a's arrival, current else planned)
 * - required  = walk minutes + 3 when there is a walk between them or the stop
 *               changes; else 2 on the same platform; else 5. Platforms are
 *               the current one else the planned one, normalised; an unknown
 *               platform never counts as the same.
 * - confidence: either leg cancelled → at_risk; margin < 0 → at_risk;
 *               margin < 3 → tight; else safe.
 */
export function assessTransfer(
  a: RuleLeg,
  b: RuleLeg,
  walks: RuleLeg[] = [],
  indices: { fromIndex: number; toIndex: number } = { fromIndex: 0, toIndex: 1 },
): Transfer {
  const arrival = ms(effectiveTime(a.to));
  const departure = ms(effectiveTime(b.from));
  const availableMinutes =
    arrival === null || departure === null ? 0 : Math.round((departure - arrival) / MINUTE);

  const walkMinutes = walks.reduce((sum, w) => sum + plannedMinutes(w), 0);
  const changesStation = walks.length > 0 || a.to.id !== b.from.id;

  let requiredMinutes: number;
  if (changesStation) {
    requiredMinutes = walkMinutes + 3;
  } else {
    const pa = platformOf(a.to);
    const pb = platformOf(b.from);
    requiredMinutes = pa !== null && pa === pb ? 2 : 5;
  }

  const marginMinutes = availableMinutes - requiredMinutes;

  let confidence: Confidence;
  if (a.cancelled || b.cancelled || marginMinutes < 0) confidence = "at_risk";
  else if (marginMinutes < 3) confidence = "tight";
  else confidence = "safe";

  return {
    ...indices,
    availableMinutes,
    requiredMinutes,
    marginMinutes,
    walkMinutes,
    changesStation,
    confidence,
  };
}

/** Every transfer of a journey: between each non-walk leg and the next one. */
export function transfers(legs: RuleLeg[]): Transfer[] {
  const result: Transfer[] = [];
  let previous = -1;
  for (let i = 0; i < legs.length; i++) {
    if (legs[i].mode === "walk") continue;
    if (previous >= 0) {
      const walks = legs.slice(previous + 1, i).filter((l) => l.mode === "walk");
      result.push(
        assessTransfer(legs[previous], legs[i], walks, { fromIndex: previous, toIndex: i }),
      );
    }
    previous = i;
  }
  return result;
}

/** The worst confidence among the transfers; null without any transfer. */
export function overallConfidence(list: Transfer[]): Confidence | null {
  if (list.length === 0) return null;
  if (list.some((t) => t.confidence === "at_risk")) return "at_risk";
  if (list.some((t) => t.confidence === "tight")) return "tight";
  return "safe";
}

/** Arrival delay of the last non-walk leg; 0 without one. */
export function finalArrivalDelay(legs: RuleLeg[]): number {
  for (let i = legs.length - 1; i >= 0; i--) {
    if (legs[i].mode !== "walk") return delayMinutes(legs[i].to.planned, legs[i].to.current);
  }
  return 0;
}

const UNBOUND_FARES = ["regional", "deutschlandticket", "bahncard100"];

/** Mirrors SQL abeona_train_binding_lifted(). */
export function trainBindingLifted(
  fareType: string | null | undefined,
  trainBinding: boolean | null | undefined,
  arrivalDelayMinutes: number | null | undefined,
): boolean {
  return (
    trainBinding === true &&
    !UNBOUND_FARES.includes(fareType ?? "other") &&
    (arrivalDelayMinutes ?? 0) >= 20
  );
}
