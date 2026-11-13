import type { AuthChangeEvent, Session } from "@supabase/supabase-js";
import { describeError } from "@nexus/ui/errors";
import { getSupabaseClient } from "./supabase";
import type {
  AlternativesResponse,
  Journey,
  JourneysRequest,
  JourneysResponse,
  Leg,
  LegMode,
  RefreshLegRef,
  RefreshResponse,
  Station,
  Stopover,
} from "./transit";
import type { TransitPort } from "./intake/match";

/**
 * The only module that talks to Supabase. Re-exported so call sites keep
 * importing error wording from the data layer, like the other apps.
 */
export { describeError };

// ── Types ──────────────────────────────────────────────────────────

/** abeona_trips.fare_type; defined with the fare names intake reads (intake/fare.ts). */
export type { FareTypeValue } from "./intake/fare";
import type { FareTypeValue } from "./intake/fare";

export interface Trip {
  id: string;
  household_id: string;
  created_by: string | null;
  title: string | null;
  status: "planned" | "cancelled";
  source: string;
  provider: string;
  refresh_token: string | null;
  origin_name: string;
  destination_name: string;
  planned_departure: string;
  planned_arrival: string;
  fare_type: FareTypeValue;
  train_binding: boolean;
  travel_class: 1 | 2;
  price_cents: number | null;
  /** How many people the ticket is for; null when unknown. */
  traveller_count: number | null;
  /** The BahnCard discounts the ticket was booked with, one per card, ascending. A BahnCard 100 is a fare_type. */
  bahncard_discounts: BahnCardKind[];
  ticket_path: string | null;
  refreshed_at: string | null;
  /** Set once the snapshot job froze every ride's actual values. */
  finalized_at: string | null;
  /** The ride (index into legs) the traveller missed or that was cancelled under them. */
  missed_leg_idx: number | null;
  /** The rides actually taken instead, in the contract's shape. */
  replacement_legs: Leg[] | null;
  /** The delay was caused by extreme weather or a person on the track. */
  force_majeure: boolean;
  created_at: string;
  updated_at: string;
  /** Ordered by idx, in the transit contract's shape. */
  legs: Leg[];
}

/** abeona_legs as stored. */
interface LegRow {
  id: string;
  trip_id: string;
  idx: number;
  mode: LegMode;
  product: string | null;
  line_name: string | null;
  trip_ref: string | null;
  headsign: string | null;
  operator: string | null;
  from_id: string | null;
  from_name: string;
  from_lat: number | null;
  from_lon: number | null;
  to_id: string | null;
  to_name: string;
  to_lat: number | null;
  to_lon: number | null;
  planned_departure: string;
  departure: string | null;
  planned_arrival: string;
  arrival: string | null;
  planned_departure_platform: string | null;
  departure_platform: string | null;
  planned_arrival_platform: string | null;
  arrival_platform: string | null;
  cancelled: boolean;
  realtime: boolean;
  stopovers: Stopover[] | null;
  polyline: [number, number][] | null;
  distance_km: number | null;
}

type TripRow = Omit<Trip, "legs"> & { abeona_legs?: LegRow[] };

export interface HouseholdMember {
  user_id: string;
  email: string | null;
  full_name: string | null;
  role: "owner" | "member";
  joined_at: string;
}

export interface Statistics {
  trips: number;
  km: number;
  minutes_on_train: number;
  rides: number;
  delay_minutes: number;
  /** 0..1, null without trips. */
  punctual_share: number | null;
  longest_km: number;
  max_delay_minutes: number;
  stations: number;
  top_route: { from: string; to: string; count: number } | null;
  top_station: { name: string; count: number } | null;
  products: Record<string, number>;
  by_month: { month: string; trips: number; km: number }[];
  /** CO₂ the rides saved against driving, one traveller, kg; approximate (abeona_co2_factors). */
  co2_saved_kg: number;
  co2_train_kg: number;
  co2_car_kg: number;
  /** Where the factors come from, e.g. "Umweltbundesamt (TREMOD), reference year 2022; approximate". */
  co2_source: string | null;
  /** ISO codes of the countries ridden from or to, most often first. */
  countries: string[];
  longest_trip: { trip_id: string; from: string; to: string; km: number; date: string } | null;
}

/** One line of the map of everything travelled (abeona_travel_map). */
export interface TravelMapLine {
  from: { name: string; lat: number; lon: number };
  to: { name: string; lat: number; lon: number };
  product: string | null;
  /** How often the pair of stations was ridden, either way. */
  count: number;
  /** [[lon, lat], …], thinned. */
  polyline: [number, number][];
}

export type ShareKind = "ics" | "status";

/** abeona_share_links: the token is the whole credential; revoking deletes the row. */
export interface ShareLink {
  token: string;
  trip_id: string;
  kind: ShareKind;
  expires_at: string;
  created_by: string | null;
  created_at: string;
}

/** What abeona-public answers for a status link; the status page formats it. */
export interface SharedStatus {
  origin: string;
  destination: string;
  state: "upcoming" | "on_board" | "changing" | "arrived" | "cancelled";
  current: SharedStatusLeg | null;
  planned_arrival: string;
  expected_arrival: string;
  delay_minutes: number;
  live: boolean;
  final: boolean;
  legs: SharedStatusLeg[];
  updated_at: string;
  expires_at: string;
}

export interface SharedStatusLeg {
  line_name: string | null;
  product: string | null;
  from: SharedStatusStop;
  to: SharedStatusStop;
  cancelled: boolean;
  delay_minutes: number;
}

export interface SharedStatusStop {
  name: string;
  planned: string | null;
  current: string | null;
  planned_platform: string | null;
  platform: string | null;
}

/** A trip abeona-import read from Träwelling, not saved yet. */
export interface ImportedTrip {
  external_id: string;
  origin: string;
  destination: string;
  planned_departure: string;
  planned_arrival: string;
  arrival_delay_minutes: number | null;
  legs: Leg[];
  /** A trip with the same origin and departure is saved already. */
  duplicate: boolean;
}

export interface ImportResult {
  trips: ImportedTrip[];
  statuses: number;
  skipped: number;
  truncated: boolean;
}

export interface SaveTripInput {
  title?: string | null;
  fare_type: FareTypeValue;
  train_binding: boolean;
  travel_class: 1 | 2;
  price_cents?: number | null;
  /** How many people the ticket is for; null when unknown. */
  traveller_count: number | null;
  /** The BahnCard discounts the ticket was booked with, one per card. */
  bahncard_discounts: BahnCardKind[];
  /**
   * A trip that already happened: saved as source "import", final, never
   * refreshed. The delay, in minutes at the destination, is entered by hand.
   */
  past?: { arrival_delay_minutes: number | null };
  /** Where it came from when not the search: a DB Navigator share or a ticket PDF (intake). */
  source?: "share" | "pdf";
}

export type BahnCardKind = 25 | 50 | 100;

/** abeona_bahncards: one of the household's cards. */
export interface BahnCard {
  id: string;
  household_id: string;
  /** What the household calls it; null shows the kind ("BahnCard 25"). */
  name: string | null;
  kind: BahnCardKind;
  travel_class: 1 | 2;
  /** yyyy-mm-dd */
  valid_from: string;
  /** yyyy-mm-dd, the last day it is valid. */
  valid_to: string;
  card_number: string | null;
  /** Renews unless cancelled notice_weeks before valid_to. */
  subscription: boolean;
  notice_weeks: number;
  file_path: string | null;
  created_at: string;
  updated_at: string;
}

export type BahnCardInput = Pick<
  BahnCard,
  "name" | "kind" | "travel_class" | "valid_from" | "valid_to" | "card_number" | "subscription" | "notice_weeks"
>;

/** A row of abeona_bahncard_deadlines(). */
export interface BahnCardDeadline {
  card_id: string;
  /** Null shows the kind. */
  name: string | null;
  kind: BahnCardKind;
  travel_class: 1 | 2;
  valid_to: string;
  notice_weeks: number;
  /** yyyy-mm-dd: valid_to minus the notice period. */
  deadline: string;
  /** Negative once the deadline has passed. */
  days_left: number;
}

export type CompensationVerdict = "eligible" | "not_eligible" | "below_minimum" | "unknown_price" | "unknown_arrival";

/** abeona_compensation(trip_id): reasons and hints are English sentences, shown as is. */
export interface Compensation {
  trip_id: string;
  verdict: CompensationVerdict;
  amount_cents: number | null;
  percent: number | null;
  delay_minutes: number | null;
  delay_source: "replacement" | "final" | "live" | "planned" | "unknown";
  planned_arrival: string | null;
  actual_arrival: string | null;
  threshold_minutes: number | null;
  reasons: string[];
  hints: string[];
  /** yyyy-mm-dd, the last day to claim. */
  deadline: string;
  deadline_passed: boolean;
  poolable: boolean;
  /** German text for DB's passenger-rights form; null when there is nothing to claim. */
  summary_de: string | null;
  claim_url: string;
}

export type ClaimStatus = "open" | "claimed" | "paid" | "rejected";

/** abeona_claims: one per trip, access follows the trip. */
export interface Claim {
  id: string;
  trip_id: string;
  status: ClaimStatus;
  amount_cents: number | null;
  claimed_at: string | null;
  paid_at: string | null;
  note: string | null;
}

/** A row of the claims overview. */
export interface ClaimEntry {
  trip_id: string;
  title: string | null;
  origin_name: string;
  destination_name: string;
  planned_departure: string;
  delay_minutes: number | null;
  verdict: CompensationVerdict;
  amount_cents: number | null;
  deadline: string;
  days_left: number;
  status: ClaimStatus;
}

/** abeona_claims_overview(year). */
export interface ClaimsOverview {
  to_claim: ClaimEntry[];
  waiting: ClaimEntry[];
  earned_cents: number;
  paid: number;
}

// ── Auth ───────────────────────────────────────────────────────────

export async function getSession(): Promise<Session | null> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.auth.getSession();
  if (error) throw error;
  return data.session;
}

export async function signInWithPassword(email: string, password: string): Promise<Session> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.auth.signInWithPassword({ email, password });
  if (error) throw error;
  return data.session;
}

export async function signOut(): Promise<void> {
  const sb = getSupabaseClient();
  householdPromise = null;
  const { error } = await sb.auth.signOut();
  if (error) throw error;
}

export function onAuthStateChange(callback: (event: AuthChangeEvent, session: Session | null) => void) {
  const sb = getSupabaseClient();
  return sb.auth.onAuthStateChange((event, session) => {
    if (event === "SIGNED_OUT") householdPromise = null;
    callback(event, session);
  });
}

// ── Household ──────────────────────────────────────────────────────

let householdPromise: Promise<string> | null = null;

/**
 * The caller's household id, created on first use. Called once per page load
 * (cached) before anything household-scoped; a failure is not cached, so the
 * next call tries again.
 */
export function ensureHousehold(): Promise<string> {
  if (!householdPromise) {
    const sb = getSupabaseClient();
    householdPromise = (async () => {
      const { data, error } = await sb.rpc("abeona_ensure_household");
      if (error) throw error;
      return data as string;
    })();
    householdPromise.catch(() => {
      householdPromise = null;
    });
  }
  return householdPromise;
}

export async function listHouseholdMembers(): Promise<HouseholdMember[]> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_list_household_members");
  if (error) throw error;
  return (data ?? []) as HouseholdMember[];
}

export async function inviteToHousehold(email: string): Promise<void> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { error } = await sb.rpc("abeona_invite_to_household", { p_email: email.trim() });
  if (error) throw error;
}

export async function leaveHousehold(): Promise<void> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_leave_household");
  if (error) throw error;
  householdPromise = Promise.resolve(data as string);
}

// ── Trips ──────────────────────────────────────────────────────────

function legFromRow(row: LegRow): Leg {
  return {
    mode: row.mode,
    product: row.product,
    line_name: row.line_name,
    trip_ref: row.trip_ref,
    headsign: row.headsign,
    operator: row.operator,
    from: {
      id: row.from_id,
      name: row.from_name,
      lat: row.from_lat,
      lon: row.from_lon,
      planned: row.planned_departure,
      current: row.departure,
      planned_platform: row.planned_departure_platform,
      platform: row.departure_platform,
      cancelled: row.cancelled,
    },
    to: {
      id: row.to_id,
      name: row.to_name,
      lat: row.to_lat,
      lon: row.to_lon,
      planned: row.planned_arrival,
      current: row.arrival,
      planned_platform: row.planned_arrival_platform,
      platform: row.arrival_platform,
      cancelled: row.cancelled,
    },
    stopovers: Array.isArray(row.stopovers) ? row.stopovers : [],
    polyline: Array.isArray(row.polyline) ? row.polyline : null,
    cancelled: row.cancelled,
    realtime: row.realtime,
  };
}

function tripFromRow(row: TripRow): Trip {
  const { abeona_legs, ...trip } = row;
  const legs = [...(abeona_legs ?? [])].sort((a, b) => a.idx - b.idx).map(legFromRow);
  return { ...trip, legs };
}

const TRIP_SELECT = "*, abeona_legs(*)";

/**
 * Trips that have not arrived yet, soonest first. The query keeps a few hours
 * of slack so a delayed trip stays here until its live arrival has passed.
 */
export async function fetchUpcomingTrips(): Promise<Trip[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const since = new Date(Date.now() - 6 * 3600_000).toISOString();
  const { data, error } = await sb
    .from("abeona_trips")
    .select(TRIP_SELECT)
    .eq("household_id", household)
    .eq("status", "planned")
    .gte("planned_arrival", since)
    .order("planned_departure", { ascending: true });
  if (error) throw error;
  return ((data ?? []) as TripRow[]).map(tripFromRow);
}

/** The most recent trips that have already arrived, newest first. */
export async function fetchPastTrips(limit = 5, before?: string): Promise<Trip[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  let query = sb
    .from("abeona_trips")
    .select(TRIP_SELECT)
    .eq("household_id", household)
    .lt("planned_arrival", new Date().toISOString());
  // The next page: older than the oldest shown.
  if (before) query = query.lt("planned_departure", before);
  const { data, error } = await query.order("planned_departure", { ascending: false }).limit(limit);
  if (error) throw error;
  return ((data ?? []) as TripRow[]).map(tripFromRow);
}

export async function fetchTrip(id: string): Promise<Trip | null> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.from("abeona_trips").select(TRIP_SELECT).eq("id", id).maybeSingle();
  if (error) throw error;
  return data ? tripFromRow(data as TripRow) : null;
}

/** Whether a searched journey has already arrived, so saving it records a past trip. */
export function isPastJourney(journey: Journey, now = Date.now()): boolean {
  const last = journey.legs[journey.legs.length - 1];
  const arrival = last?.to.current ?? last?.to.planned;
  return !!arrival && Date.parse(arrival) < now;
}

/** Saves a searched journey as a new trip; returns its id. */
export async function saveTrip(journey: Journey, input: SaveTripInput): Promise<string> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_save_trip", {
    p_trip: {
      title: input.title?.trim() || null,
      source: input.past ? "import" : (input.source ?? "search"),
      ...(input.past && input.past.arrival_delay_minutes !== null
        ? { arrival_delay_minutes: input.past.arrival_delay_minutes }
        : {}),
      provider: journey.provider,
      refresh_token: journey.refresh_token,
      fare_type: input.fare_type,
      train_binding: input.train_binding,
      travel_class: input.travel_class,
      price_cents: input.price_cents ?? null,
      traveller_count: input.traveller_count,
      bahncard_discounts: [...input.bahncard_discounts].sort((a, b) => a - b),
    },
    // The contract's Leg is exactly what the RPC reads.
    p_legs: journey.legs,
  });
  if (error) throw error;
  return data as string;
}

export async function deleteTrip(trip: Pick<Trip, "id" | "ticket_path">): Promise<void> {
  const sb = getSupabaseClient();
  if (trip.ticket_path) {
    // Best effort: a stray file is harmless, a trip that cannot be deleted is not.
    await sb.storage.from(TICKET_BUCKET).remove([trip.ticket_path]).catch(() => {});
  }
  const { error } = await sb.from("abeona_trips").delete().eq("id", trip.id);
  if (error) throw error;
}

/** What "Edit trip" changes: the title and the ticket's facts. */
export interface TripEdit {
  /** Blank clears it: the trip is then called by its route. */
  title: string | null;
  fare_type: FareTypeValue;
  train_binding: boolean;
  travel_class: 1 | 2;
  price_cents: number | null;
  /** How many people the ticket is for; null when unknown. */
  traveller_count: number | null;
  /** The BahnCard discounts the ticket was booked with, one per card. */
  bahncard_discounts: BahnCardKind[];
}

/** Saves "Edit trip" in one update; household members may edit any of the household's trips. */
export async function updateTrip(tripId: string, edit: TripEdit): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("abeona_trips")
    .update({
      title: edit.title?.trim() || null,
      fare_type: edit.fare_type,
      train_binding: edit.train_binding,
      travel_class: edit.travel_class,
      price_cents: edit.price_cents,
      traveller_count: edit.traveller_count,
      bahncard_discounts: [...edit.bahncard_discounts].sort((a, b) => a - b),
      updated_at: new Date().toISOString(),
    })
    .eq("id", tripId);
  if (error) throw error;
}

// ── BahnCards ──────────────────────────────────────────────────────

/** Every card of the household, the one running longest first. */
export async function fetchBahnCards(): Promise<BahnCard[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_bahncards")
    .select("*")
    .eq("household_id", household)
    .order("valid_to", { ascending: false });
  if (error) throw error;
  return (data ?? []) as BahnCard[];
}

/** Inserts a card into the caller's household, or updates it when an id is given. */
export async function saveBahnCard(input: BahnCardInput, id?: string): Promise<BahnCard> {
  const sb = getSupabaseClient();
  // A blank name is rejected by the table; null shows the kind.
  const row = { ...input, name: input.name?.trim() || null, card_number: input.card_number?.trim() || null };
  const query = id
    ? sb.from("abeona_bahncards").update({ ...row, updated_at: new Date().toISOString() }).eq("id", id)
    : sb.from("abeona_bahncards").insert({ ...row, household_id: await ensureHousehold() });
  const { data, error } = await query.select().single();
  if (error) throw error;
  return data as BahnCard;
}

export async function deleteBahnCard(card: Pick<BahnCard, "id" | "file_path">): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("abeona_bahncards").delete().eq("id", card.id);
  if (error) throw error;
  if (card.file_path) {
    await sb.storage.from(TICKET_BUCKET).remove([card.file_path]).catch(() => {});
  }
}

/**
 * Uploads the card's PDF or image to <household_id>/bahncards/<card_id>/<file>
 * and links it to the card. Access follows the card, so the household prefix
 * only matters until the card row exists.
 */
export async function uploadBahnCardFile(card: Pick<BahnCard, "id" | "file_path">, file: File): Promise<string> {
  checkTicketFile(file);
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const path = `${household}/bahncards/${card.id}/${Date.now()}-${safeFileName(file.name)}`;
  const { error: uploadError } = await sb.storage
    .from(TICKET_BUCKET)
    .upload(path, file, { contentType: file.type, upsert: false });
  if (uploadError) throw uploadError;

  const { error } = await sb.from("abeona_bahncards").update({ file_path: path, updated_at: new Date().toISOString() }).eq("id", card.id);
  if (error) {
    await sb.storage.from(TICKET_BUCKET).remove([path]).catch(() => {});
    throw error;
  }
  if (card.file_path && card.file_path !== path) {
    await sb.storage.from(TICKET_BUCKET).remove([card.file_path]).catch(() => {});
  }
  return path;
}

export async function removeBahnCardFile(card: Pick<BahnCard, "id" | "file_path">): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("abeona_bahncards").update({ file_path: null, updated_at: new Date().toISOString() }).eq("id", card.id);
  if (error) throw error;
  if (card.file_path) {
    await sb.storage.from(TICKET_BUCKET).remove([card.file_path]).catch(() => {});
  }
}

/** Subscriptions whose cancellation deadline is at most withinDays away, or passed while the card runs. */
export async function fetchBahnCardDeadlines(withinDays = BAHNCARD_DEADLINE_DAYS): Promise<BahnCardDeadline[]> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_bahncard_deadlines", { p_within_days: withinDays });
  if (error) throw error;
  return ((data ?? []) as BahnCardDeadline[]).map((d) => ({ ...d, days_left: Number(d.days_left) }));
}

/** How far ahead the dashboard and the Android reminder look. */
export const BAHNCARD_DEADLINE_DAYS = 14;

// ── After the ride ─────────────────────────────────────────────────

/** What the trip's delay is worth, by the rules in abeona_compensation_rules. */
export async function fetchCompensation(tripId: string): Promise<Compensation | null> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_compensation", { p_trip_id: tripId });
  if (error) throw error;
  if (!data) return null;
  const c = data as Compensation;
  return { ...c, reasons: c.reasons ?? [], hints: c.hints ?? [] };
}

/** The trip's claim, if one was started. */
export async function fetchClaim(tripId: string): Promise<Claim | null> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.from("abeona_claims").select("*").eq("trip_id", tripId).maybeSingle();
  if (error) throw error;
  return (data as Claim | null) ?? null;
}

/**
 * Moves the trip's claim to a status, creating it on first use. Claimed and
 * paid stamp their date; going back to open clears both.
 */
export async function setClaimStatus(tripId: string, status: ClaimStatus, amountCents: number | null): Promise<Claim> {
  const sb = getSupabaseClient();
  const now = new Date().toISOString();
  const existing = await fetchClaim(tripId);
  const row = {
    trip_id: tripId,
    status,
    amount_cents: amountCents,
    claimed_at: status === "open" ? null : existing?.claimed_at ?? now,
    paid_at: status === "paid" ? existing?.paid_at ?? now : null,
    updated_at: now,
  };
  const { data, error } = await sb.from("abeona_claims").upsert(row, { onConflict: "trip_id" }).select().single();
  if (error) throw error;
  return data as Claim;
}

export async function setTripPrice(tripId: string, priceCents: number | null): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("abeona_trips")
    .update({ price_cents: priceCents, updated_at: new Date().toISOString() })
    .eq("id", tripId);
  if (error) throw error;
}

export async function setForceMajeure(tripId: string, value: boolean): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("abeona_trips")
    .update({ force_majeure: value, updated_at: new Date().toISOString() })
    .eq("id", tripId);
  if (error) throw error;
}

/** Records the ride that was missed and what was taken instead; nulls clear it. */
export async function recordDisruption(tripId: string, missedLegIdx: number | null, replacement: Leg[] | null): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("abeona_trips")
    .update({
      missed_leg_idx: missedLegIdx,
      replacement_legs: replacement && replacement.length > 0 ? replacement : null,
      updated_at: new Date().toISOString(),
    })
    .eq("id", tripId);
  if (error) throw error;
}

/** Claims to send, claims waiting for an answer, and what was earned back in a year. */
export async function fetchClaimsOverview(year: number): Promise<ClaimsOverview> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_claims_overview", { p_year: year });
  if (error) throw error;
  const o = (data ?? {}) as Partial<ClaimsOverview>;
  const rows = (list: ClaimEntry[] | undefined) => (list ?? []).map((r) => ({ ...r, days_left: Number(r.days_left) }));
  return {
    to_claim: rows(o.to_claim),
    waiting: rows(o.waiting),
    earned_cents: Number(o.earned_cents ?? 0),
    paid: Number(o.paid ?? 0),
  };
}

// ── Live values ────────────────────────────────────────────────────

/**
 * Asks the Edge Function for the trip's current state and writes whatever came
 * back onto the saved legs (matched by position; planned values stay). Returns
 * whether anything was updated.
 */
export async function refreshTripLive(trip: Trip): Promise<boolean> {
  const legs: RefreshLegRef[] = trip.legs.map((leg) => ({
    trip_ref: leg.trip_ref,
    from_id: leg.from.id,
    to_id: leg.to.id,
    from_name: leg.from.name,
    to_name: leg.to.name,
  }));

  const result = await invokeTransit<RefreshResponse>({
    action: "refresh",
    refresh_token: trip.refresh_token,
    legs,
  });

  const updates = (result.legs ?? [])
    .map((leg, idx) =>
      leg
        ? {
            idx,
            departure: leg.from.current,
            arrival: leg.to.current,
            departure_platform: leg.from.platform,
            arrival_platform: leg.to.platform,
            cancelled: leg.cancelled,
            realtime: leg.realtime,
            stopovers: leg.stopovers,
          }
        : null,
    )
    .filter((u) => u !== null && u.idx < trip.legs.length);

  const sb = getSupabaseClient();
  if (updates.length > 0) {
    const { error } = await sb.rpc("abeona_update_trip_live", { p_trip_id: trip.id, p_legs: updates });
    if (error) throw error;
  }
  const replaced = await refreshReplacement(trip).catch(() => false);
  return updates.length > 0 || replaced;
}

/**
 * The connection taken instead (replacement_legs, a JSON column, not legs
 * rows): refreshed leg by leg through each ride's trip ref, and written back
 * as a whole. Android does the same (AbeonaRepository.refreshReplacement).
 */
async function refreshReplacement(trip: Trip): Promise<boolean> {
  const replacement = trip.replacement_legs;
  if (trip.missed_leg_idx === null || !replacement || replacement.length === 0) return false;
  const result = await invokeTransit<RefreshResponse>({
    action: "refresh",
    refresh_token: null,
    legs: replacement.map((leg) => ({
      trip_ref: leg.trip_ref,
      from_id: leg.from.id,
      to_id: leg.to.id,
      from_name: leg.from.name,
      to_name: leg.to.name,
    })),
  });
  let changed = false;
  const fresh = replacement.map((leg, i) => {
    const live = result.legs?.[i];
    if (!live) return leg;
    changed = true;
    return {
      ...leg,
      from: { ...leg.from, current: live.from.current, platform: live.from.platform ?? leg.from.platform },
      to: { ...leg.to, current: live.to.current, platform: live.to.platform ?? leg.to.platform },
      cancelled: live.cancelled,
      realtime: live.realtime,
      stopovers: live.stopovers,
    };
  });
  if (!changed) return false;
  const { error } = await getSupabaseClient().from("abeona_trips").update({ replacement_legs: fresh }).eq("id", trip.id);
  if (error) throw error;
  return true;
}

// ── Transit search (abeona-transit Edge Function) ─────────────────

export async function searchStations(query: string): Promise<Station[]> {
  const result = await invokeTransit<{ stations: Station[] }>({ action: "locations", query });
  return result.stations ?? [];
}

export async function searchJourneys(request: JourneysRequest): Promise<JourneysResponse> {
  const result = await invokeTransit<JourneysResponse>({ action: "journeys", ...request });
  return {
    journeys: result.journeys ?? [],
    next_cursor: result.next_cursor ?? null,
    previous_cursor: result.previous_cursor ?? null,
  };
}

/** The timetable as intake's matching asks it (intake/match.ts): station names, then journeys. */
export const transitPort: TransitPort = {
  stations: searchStations,
  journeys: async (from, to, time) => (await searchJourneys({ from, to, time })).journeys,
};

/**
 * Connections from `from` (a stop id, else "lat,lon") to the trip's
 * destination, marked by the Edge Function against what the trip's ticket
 * allows now. One request per refresh; the trip's live values should be
 * saved first, since the allowance reads them.
 */
export async function fetchAlternatives(
  trip: Pick<Trip, "id" | "legs">,
  from: { id: string | null; lat: number | null; lon: number | null },
  time: string,
  /** The browser's position showed a short change missed (missedShortConnection): it lifts the binding. */
  connectionMissed = false,
): Promise<AlternativesResponse> {
  const last = trip.legs[trip.legs.length - 1];
  const place = (stop: { id: string | null; lat: number | null; lon: number | null }) =>
    stop.id ?? (stop.lat !== null && stop.lon !== null ? `${stop.lat},${stop.lon}` : null);
  const origin = place(from);
  const destination = last ? place(last.to) : null;
  if (!origin || !destination) throw new Error("This trip has no stop to search from.");
  const result = await invokeTransit<AlternativesResponse>({
    action: "alternatives",
    trip_id: trip.id,
    from: origin,
    to: destination,
    time,
    ...(connectionMissed ? { connection_missed: true } : {}),
  });
  return { allowance: result.allowance ?? null, alternatives: result.alternatives ?? [] };
}

async function invokeTransit<T>(body: Record<string, unknown>): Promise<T> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.functions.invoke("abeona-transit", { body });
  if (error) throw new Error(await describeFunctionError(error));
  return data as T;
}

/**
 * Edge Functions answer errors with a JSON body; supabase-js hands back a
 * generic "non-2xx status code" and hides that body in `context`.
 */
async function describeFunctionError(error: unknown): Promise<string> {
  const context = (error as { context?: unknown })?.context;
  if (context && typeof (context as Response).json === "function") {
    try {
      const body = await (context as Response).json();
      const message = body?.error ?? body?.message;
      if (typeof message === "string" && message.trim() !== "") return message;
    } catch {
      // Not JSON, or already consumed.
    }
  }
  return describeError(error);
}

// ── Statistics ─────────────────────────────────────────────────────

export async function fetchStatistics(from: Date, to: Date): Promise<Statistics> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_statistics", {
    p_from: from.toISOString(),
    p_to: to.toISOString(),
  });
  if (error) throw error;
  const s = (data ?? {}) as Partial<Statistics>;
  return {
    trips: Number(s.trips ?? 0),
    km: Number(s.km ?? 0),
    minutes_on_train: Number(s.minutes_on_train ?? 0),
    rides: Number(s.rides ?? 0),
    delay_minutes: Number(s.delay_minutes ?? 0),
    punctual_share: s.punctual_share === null || s.punctual_share === undefined ? null : Number(s.punctual_share),
    longest_km: Number(s.longest_km ?? 0),
    max_delay_minutes: Number(s.max_delay_minutes ?? 0),
    stations: Number(s.stations ?? 0),
    top_route: s.top_route ?? null,
    top_station: s.top_station ?? null,
    products: s.products ?? {},
    by_month: (s.by_month ?? []).map((m) => ({ month: m.month, trips: Number(m.trips), km: Number(m.km ?? 0) })),
    co2_saved_kg: Number(s.co2_saved_kg ?? 0),
    co2_train_kg: Number(s.co2_train_kg ?? 0),
    co2_car_kg: Number(s.co2_car_kg ?? 0),
    co2_source: ((s as { co2_factors?: { source?: string } }).co2_factors?.source) ?? null,
    countries: Array.isArray(s.countries) ? s.countries.filter((c) => typeof c === "string") : [],
    longest_trip: s.longest_trip ? { ...s.longest_trip, km: Number(s.longest_trip.km) } : null,
  };
}

/** The household's rides that arrived in [from, to), one thinned line per pair of stations. */
export async function fetchTravelMap(from: Date, to: Date): Promise<TravelMapLine[]> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_travel_map", { p_from: from.toISOString(), p_to: to.toISOString() });
  if (error) throw error;
  return ((data ?? []) as TravelMapLine[]).map((l) => ({ ...l, count: Number(l.count) }));
}

export async function fetchYearTravelMap(year: number): Promise<TravelMapLine[]> {
  return fetchTravelMap(new Date(year, 0, 1), new Date(year + 1, 0, 1));
}

export async function fetchYearStatistics(year: number): Promise<Statistics> {
  return fetchStatistics(new Date(year, 0, 1), new Date(year + 1, 0, 1));
}

/** The years that have at least one trip, newest first, always including this one. */
export async function fetchTripYears(): Promise<number[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_trips")
    .select("planned_departure")
    .eq("household_id", household)
    .order("planned_departure", { ascending: true })
    .limit(1);
  if (error) throw error;
  const thisYear = new Date().getFullYear();
  const first = data?.[0]?.planned_departure ? new Date(data[0].planned_departure).getFullYear() : thisYear;
  const years: number[] = [];
  for (let y = thisYear; y >= Math.min(first, thisYear); y--) years.push(y);
  return years;
}

// ── Share links (abeona_share_links, opened by abeona-public) ────────

/** The trip's live share links, one per kind at most. */
export async function fetchShareLinks(tripId: string): Promise<ShareLink[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_share_links")
    .select("*")
    .eq("trip_id", tripId)
    .gt("expires_at", new Date().toISOString());
  if (error) throw error;
  return (data ?? []) as ShareLink[];
}

/** Makes a link; the database chooses the token and the expiry. */
export async function createShareLink(tripId: string, kind: ShareKind): Promise<ShareLink> {
  const sb = getSupabaseClient();
  // An expired link of the same kind still holds the (trip, kind) slot.
  await sb.from("abeona_share_links").delete().eq("trip_id", tripId).eq("kind", kind).lte("expires_at", new Date().toISOString());
  const { data, error } = await sb.from("abeona_share_links").insert({ trip_id: tripId, kind }).select().single();
  if (error) throw error;
  return data as ShareLink;
}

/** Revoking deletes the row: the link opens nothing from then on. */
export async function revokeShareLink(token: string): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("abeona_share_links").delete().eq("token", token);
  if (error) throw error;
}

function publicFunctionUrl(token: string): string {
  const base = (import.meta.env.PUBLIC_SUPABASE_URL || "").replace(/\/+$/, "");
  return `${base}/functions/v1/abeona-public?t=${token}`;
}

/**
 * Where a link points: the calendar file straight from the Edge Function
 * (webcal:// for subscribing), the status page on this site.
 */
export function shareLinkUrl(link: Pick<ShareLink, "token" | "kind">, origin: string, base: string): string {
  if (link.kind === "ics") return publicFunctionUrl(link.token);
  return `${origin}${base}/status?t=${link.token}`;
}

export function calendarSubscribeUrl(token: string): string {
  return publicFunctionUrl(token).replace(/^https?:\/\//, "webcal://");
}

/** The status page's data, without a session: the token is the credential. */
export async function fetchSharedStatus(token: string): Promise<SharedStatus> {
  if (!/^[0-9a-f]{36}$/.test(token)) throw new Error("This link is not valid.");
  const response = await fetch(publicFunctionUrl(token), { headers: { Accept: "application/json" } });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(typeof body?.error === "string" ? body.error : "This link could not be opened.");
  return body as SharedStatus;
}

// ── Imports (abeona-import) ────────────────────────────────────────

/**
 * Reads the Träwelling check-ins between two dates (yyyy-mm-dd) as trips.
 * The token goes to the Edge Function in this one request and is kept
 * nowhere — not here, not there.
 */
export async function readTraewelling(token: string, from: string, to: string): Promise<ImportResult> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.functions.invoke("abeona-import", {
    body: { action: "import_traewelling", token: token.trim(), from, to },
  });
  if (error) throw new Error(await describeFunctionError(error));
  const r = (data ?? {}) as Partial<ImportResult>;
  return { trips: r.trips ?? [], statuses: Number(r.statuses ?? 0), skipped: Number(r.skipped ?? 0), truncated: !!r.truncated };
}

/** Saves an imported trip: final from the start, never refreshed. */
export async function saveImportedTrip(trip: ImportedTrip): Promise<string> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_save_trip", {
    p_trip: {
      source: "import",
      provider: "traewelling",
      fare_type: "other",
      ...(trip.arrival_delay_minutes !== null ? { arrival_delay_minutes: trip.arrival_delay_minutes } : {}),
    },
    p_legs: trip.legs,
  });
  if (error) throw error;
  return data as string;
}

// ── Tickets (Storage) ──────────────────────────────────────────────

const TICKET_BUCKET = "abeona-tickets";
export const TICKET_TYPES = ["application/pdf", "image/png", "image/jpeg"];
export const TICKET_MAX_BYTES = 10 * 1024 * 1024;

function safeFileName(name: string): string {
  const cleaned = name
    .normalize("NFKD")
    .replace(/[^\w.-]+/g, "_")
    .replace(/_+/g, "_")
    .slice(-80);
  return cleaned || "ticket";
}

function checkTicketFile(file: File) {
  if (!TICKET_TYPES.includes(file.type)) throw new Error("The file must be a PDF, PNG or JPEG.");
  if (file.size > TICKET_MAX_BYTES) throw new Error("The file may be at most 10 MB.");
}

/** Uploads the ticket file to <household_id>/<trip_id>/<file> and links it to the trip. */
export async function uploadTicket(trip: Pick<Trip, "id" | "household_id" | "ticket_path">, file: File): Promise<string> {
  checkTicketFile(file);

  const sb = getSupabaseClient();
  const path = `${trip.household_id}/${trip.id}/${Date.now()}-${safeFileName(file.name)}`;
  const { error: uploadError } = await sb.storage
    .from(TICKET_BUCKET)
    .upload(path, file, { contentType: file.type, upsert: false });
  if (uploadError) throw uploadError;

  const { error } = await sb.from("abeona_trips").update({ ticket_path: path, updated_at: new Date().toISOString() }).eq("id", trip.id);
  if (error) {
    await sb.storage.from(TICKET_BUCKET).remove([path]).catch(() => {});
    throw error;
  }

  if (trip.ticket_path && trip.ticket_path !== path) {
    await sb.storage.from(TICKET_BUCKET).remove([trip.ticket_path]).catch(() => {});
  }
  return path;
}

/** Downloads a ticket or BahnCard file (both live in the same bucket). */
export async function downloadTicket(path: string): Promise<Blob> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.storage.from(TICKET_BUCKET).download(path);
  if (error) throw error;
  return data;
}

export async function removeTicket(trip: Pick<Trip, "id" | "ticket_path">): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("abeona_trips").update({ ticket_path: null, updated_at: new Date().toISOString() }).eq("id", trip.id);
  if (error) throw error;
  if (trip.ticket_path) {
    await sb.storage.from(TICKET_BUCKET).remove([trip.ticket_path]).catch(() => {});
  }
}
