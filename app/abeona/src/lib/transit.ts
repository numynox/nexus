/**
 * The abeona-transit Edge Function's JSON shapes, written by hand.
 *
 * This mirrors supabase/functions/abeona-transit/contract.ts, and the Kotlin
 * copy in android/abeona (data/Transit.kt). Change all three together. Times
 * are ISO-8601 instants in UTC.
 */

export type Provider = "transitous";

export interface Station {
  id: string;
  name: string;
  lat: number;
  lon: number;
  country: string | null;
}

/** A stop as seen from one leg: where the leg starts, ends, or passes. */
export interface StopTime {
  id: string | null;
  name: string;
  lat: number | null;
  lon: number | null;
  /** Scheduled time at this stop (departure for a leg's origin, arrival otherwise). */
  planned: string | null;
  /** Current estimate; null when the provider has no realtime data for it. */
  current: string | null;
  planned_platform: string | null;
  platform: string | null;
  cancelled: boolean;
  /** ISO 3166 alpha-2 from the stop's time zone or id; absent on older answers. */
  country?: string | null;
}

/** An intermediate stop of a leg. */
export interface Stopover {
  id: string | null;
  name: string;
  lat: number | null;
  lon: number | null;
  planned_arrival: string | null;
  arrival: string | null;
  planned_departure: string | null;
  departure: string | null;
  cancelled: boolean;
}

export type LegMode = "train" | "bus" | "walk" | "other";

export interface Leg {
  mode: LegMode;
  /** ICE, IC, EC, RJX, RE, RB, S, … — null for walks or when unknown. */
  product: string | null;
  /** What the board shows, e.g. "ICE 932" or "RE 4". */
  line_name: string | null;
  /** The provider's trip id, enough to refresh this leg on its own. */
  trip_ref: string | null;
  headsign: string | null;
  operator: string | null;
  from: StopTime;
  to: StopTime;
  stopovers: Stopover[];
  /** [[lon, lat], …], simplified to a few hundred points at most. */
  polyline: [number, number][] | null;
  cancelled: boolean;
  realtime: boolean;
  /**
   * Saved legs only: when a ride saved without the timetable was found to be
   * missing from it on a date it covers (intake/match.ts checkSavedLeg). Such
   * a ride is also `cancelled`.
   */
  dropped_at?: string | null;
}

export interface Journey {
  provider: Provider;
  /** Hands the whole journey back to the provider for a refresh. */
  refresh_token: string | null;
  legs: Leg[];
}

export interface JourneysRequest {
  from: string;
  to: string;
  /** ISO instant; defaults to now. */
  time?: string;
  arrive_by?: boolean;
  /** "all" trains, or "regional" (no IC/ICE/EC). */
  products?: "all" | "regional";
  /** Opaque, from a previous answer's next/previous cursor. */
  cursor?: string;
}

export interface JourneysResponse {
  journeys: Journey[];
  next_cursor: string | null;
  previous_cursor: string | null;
}

/** What a refresh needs to find each saved leg again when the token fails. */
export interface RefreshLegRef {
  trip_ref: string | null;
  from_id: string | null;
  to_id: string | null;
  from_name: string;
  to_name: string;
}

export interface RefreshResponse {
  journey: Journey | null;
  /** Index-aligned with the saved legs; null = no update for that leg. */
  legs: (Leg | null)[];
}

/**
 * What a trip's ticket covers right now (abeona_allowed_products, from the
 * rules in abeona_fare_rules and the trip's last saved live values).
 */
export interface Allowance {
  trip_id: string;
  fare_type: string;
  /** False when Abeona has no rule for the fare type: every alternative is "check". */
  known: boolean;
  /** IC, ICE, EC and the like are covered at all. */
  long_distance: boolean;
  excluded_products: string[];
  /** The train binding holds: only the booked trains count. */
  train_bound: boolean;
  /** The ticket was bound and DB has lifted the binding (delay, cancellation or a broken connection). */
  binding_lifted: boolean;
  /** Why it was lifted; null while it holds, absent from an older database. */
  binding_lifted_reason?: "delay" | "cancelled" | "impossible_connection" | "missed_connection" | null;
  arrival_delay_minutes: number;
  booked_lines: string[];
  /** English, shown as is; it ends in "check the terms". */
  hint: string;
}

/** valid: the ticket covers it · check: Abeona cannot tell · invalid: needs another ticket. */
export type Validity = "valid" | "check" | "invalid";

/** A connection that could replace the rest of a trip, marked against its ticket. */
export interface Alternative extends Journey {
  validity: Validity;
  /** Why, in English, shown as is. */
  validity_note: string;
}

export interface AlternativesResponse {
  allowance: Allowance | null;
  /** Ranked by arrival, then by number of changes. */
  alternatives: Alternative[];
}
