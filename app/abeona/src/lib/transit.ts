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
