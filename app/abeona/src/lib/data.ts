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

/**
 * The only module that talks to Supabase. Re-exported so call sites keep
 * importing error wording from the data layer, like the other apps.
 */
export { describeError };

// ── Types ──────────────────────────────────────────────────────────

export type FareTypeValue =
  | "flex"
  | "saver"
  | "regional"
  | "deutschlandticket"
  | "bahncard100"
  | "other";

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
  /** The BahnCard discount the fare was bought with; a BahnCard 100 is a fare_type. */
  bahncard_discount: BahnCardKind | null;
  ticket_path: string | null;
  refreshed_at: string | null;
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

export interface Traveller {
  id: string;
  household_id: string;
  name: string;
  user_id: string | null;
  created_at: string;
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
}

export interface SaveTripInput {
  title?: string | null;
  fare_type: FareTypeValue;
  train_binding: boolean;
  travel_class: 1 | 2;
  price_cents?: number | null;
  bahncard_discount?: BahnCardKind | null;
  /** Who travels. Left out, the trip gets the caller's own traveller. */
  traveller_ids?: string[];
}

export type BahnCardKind = 25 | 50 | 100;

/** abeona_bahncards: belongs to a traveller, and through them to a household. */
export interface BahnCard {
  id: string;
  traveller_id: string;
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
  "traveller_id" | "kind" | "travel_class" | "valid_from" | "valid_to" | "card_number" | "subscription" | "notice_weeks"
>;

/** A row of abeona_bahncard_deadlines(). */
export interface BahnCardDeadline {
  card_id: string;
  traveller_id: string;
  traveller_name: string;
  kind: BahnCardKind;
  travel_class: 1 | 2;
  valid_to: string;
  notice_weeks: number;
  /** yyyy-mm-dd: valid_to minus the notice period. */
  deadline: string;
  /** Negative once the deadline has passed. */
  days_left: number;
}

/** A row of abeona_trip_warnings(); the message is shown as is. */
export interface TripWarning {
  kind: "no_travellers" | "no_card" | "expired" | "not_yet_valid" | "class";
  traveller_id: string | null;
  traveller_name: string | null;
  card_id: string | null;
  message: string;
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

// ── Travellers ─────────────────────────────────────────────────────

export async function fetchTravellers(): Promise<Traveller[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_travellers")
    .select("*")
    .eq("household_id", household)
    .order("created_at", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Traveller[];
}

export async function addTraveller(name: string): Promise<Traveller> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_travellers")
    .insert({ household_id: household, name: name.trim() })
    .select()
    .single();
  if (error) throw error;
  return data as Traveller;
}

export async function renameTraveller(id: string, name: string): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("abeona_travellers").update({ name: name.trim() }).eq("id", id);
  if (error) throw error;
}

export async function deleteTraveller(id: string): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("abeona_travellers").delete().eq("id", id);
  if (error) throw error;
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
export async function fetchPastTrips(limit = 5): Promise<Trip[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_trips")
    .select(TRIP_SELECT)
    .eq("household_id", household)
    .lt("planned_arrival", new Date().toISOString())
    .order("planned_departure", { ascending: false })
    .limit(limit);
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

/** Saves a searched journey as a new trip; returns its id. */
export async function saveTrip(journey: Journey, input: SaveTripInput): Promise<string> {
  await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_save_trip", {
    p_trip: {
      title: input.title?.trim() || null,
      source: "search",
      provider: journey.provider,
      refresh_token: journey.refresh_token,
      fare_type: input.fare_type,
      train_binding: input.train_binding,
      travel_class: input.travel_class,
      price_cents: input.price_cents ?? null,
      bahncard_discount: input.bahncard_discount ?? null,
      ...(input.traveller_ids ? { traveller_ids: input.traveller_ids } : {}),
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

/** The BahnCard discount of the trip's fare; null for none. */
export async function setTripBahncardDiscount(tripId: string, discount: BahnCardKind | null): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("abeona_trips")
    .update({ bahncard_discount: discount, updated_at: new Date().toISOString() })
    .eq("id", tripId);
  if (error) throw error;
}

/** The ids of the travellers on a trip. */
export async function fetchTripTravellerIds(tripId: string): Promise<string[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.from("abeona_trip_travellers").select("traveller_id").eq("trip_id", tripId);
  if (error) throw error;
  return ((data ?? []) as { traveller_id: string }[]).map((r) => r.traveller_id);
}

export async function setTripTravellers(tripId: string, travellerIds: string[]): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.rpc("abeona_set_trip_travellers", { p_trip_id: tripId, p_traveller_ids: travellerIds });
  if (error) throw error;
}

/** What is wrong with the trip's BahnCards; the same rows the app shows. */
export async function fetchTripWarnings(tripId: string): Promise<TripWarning[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("abeona_trip_warnings", { p_trip_id: tripId });
  if (error) throw error;
  return (data ?? []) as TripWarning[];
}

// ── BahnCards ──────────────────────────────────────────────────────

/** Every card of the household's travellers, soonest-ending last. */
export async function fetchBahnCards(): Promise<BahnCard[]> {
  const household = await ensureHousehold();
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("abeona_bahncards")
    .select("*, abeona_travellers!inner(household_id)")
    .eq("abeona_travellers.household_id", household)
    .order("valid_to", { ascending: false });
  if (error) throw error;
  return ((data ?? []) as (BahnCard & { abeona_travellers?: unknown })[]).map(({ abeona_travellers: _, ...card }) => card);
}

/** Inserts a card, or updates it when an id is given. */
export async function saveBahnCard(input: BahnCardInput, id?: string): Promise<BahnCard> {
  const sb = getSupabaseClient();
  const row = { ...input, card_number: input.card_number?.trim() || null };
  const query = id
    ? sb.from("abeona_bahncards").update({ ...row, updated_at: new Date().toISOString() }).eq("id", id)
    : sb.from("abeona_bahncards").insert(row);
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

  if (updates.length === 0) return false;

  const sb = getSupabaseClient();
  const { error } = await sb.rpc("abeona_update_trip_live", { p_trip_id: trip.id, p_legs: updates });
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
  };
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
