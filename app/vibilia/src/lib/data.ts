import type { AuthChangeEvent, Session } from "@supabase/supabase-js";
import { getSupabaseClient } from "./supabase";

interface RefuelEventMutation {
  mileage: number;
  liters: number;
  total_price: number;
  fuel_level_after: number;
  is_full_refuel: boolean;
  missed_previous_refuel: boolean;
  price_per_liter_calculated: number;
  fueled_at: string;
  fuel_station_id: string | null;
}

interface RefuelEventInsert extends RefuelEventMutation {
  car_id: string;
  user_id: string;
}

interface RefuelEventUpdate extends RefuelEventMutation {}

interface CarExpenseInsert {
  car_id: string;
  user_id: string;
  expensed_at: string;
  title: string;
  amount: number;
  mileage: number | null;
  category: string;
  notes: string | null;
}

interface CarExpenseUpdate {
  expensed_at: string;
  title: string;
  amount: number;
  mileage: number | null;
  category: string;
  notes: string | null;
}

export interface CarMember {
  user_id: string;
  role: "owner" | "shared";
  full_name: string | null;
  email: string | null;
}

export interface LastRefuelEventPoint {
  fueled_at: string;
  price_per_liter: number;
}

export interface NearbyFuelStation {
  id: string;
  name: string;
  brand: string;
  street: string;
  house_number: string;
  place: string;
  lat?: number;
  lng?: number;
  currentPrice?: number;
  discount: number;
  whole_day: boolean;
  opening_times?: unknown;
  distanceKm?: number;
  isOpen?: boolean;
}

export async function getSession(): Promise<Session | null> {
  const supabase = getSupabaseClient();
  const {
    data: { session },
    error,
  } = await supabase.auth.getSession();

  if (error) throw error;
  return session;
}

export async function signInWithPassword(email: string, password: string) {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password,
  });

  if (error) throw error;
  return data;
}

export function onAuthStateChange(
  callback: (event: AuthChangeEvent, session: Session | null) => void,
) {
  const supabase = getSupabaseClient();
  return supabase.auth.onAuthStateChange(callback);
}

export async function signOut(): Promise<void> {
  const supabase = getSupabaseClient();
  const { error } = await supabase.auth.signOut();
  if (error) throw error;
}

export async function fetchFuelStationsCurrentPrices(
  fuelType: string,
): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc(
    "get_fuel_stations_current_prices",
    {
      p_fuel_type: fuelType,
    },
  );

  if (error) throw error;
  return data || [];
}

export async function fetchFuelStations(): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("fuel_stations")
    .select("id, brand, place, street, house_number, lat, lng")
    .order("brand", { ascending: true })
    .order("place", { ascending: true })
    .order("street", { ascending: true })
    .order("house_number", { ascending: true });

  if (error) throw error;
  return data || [];
}

export async function fetchNearbyFuelStations(params: {
  lat: number;
  lng: number;
  fuelType: string;
  radiusKm: number;
}): Promise<NearbyFuelStation[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase.functions.invoke(
    "fetch-nearby-fuel-prices",
    {
      body: params,
    },
  );

  if (error) throw error;
  return Array.isArray(data?.stations) ? data.stations : [];
}

export async function fetchFuelPricePlotHistory(
  fuelType: string,
  sinceIso: string,
  bucketMinutes: number,
  timeZone: string,
): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc(
    "get_fuel_price_plot_history",
    {
      p_fuel_type: fuelType,
      p_since: sinceIso,
      p_bucket_minutes: bucketMinutes,
      p_time_zone: timeZone,
    },
  );

  if (error) throw error;
  return data || [];
}

export async function fetchFuelPriceWeeklyMinima(
  fuelType: string,
  sinceIso: string,
  untilIso: string,
  timeZone = "UTC",
): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc(
    "get_fuel_price_weekly_minima",
    {
      p_fuel_type: fuelType,
      p_since: sinceIso,
      p_until: untilIso,
      p_time_zone: timeZone,
    },
  );

  if (error) throw error;
  return data || [];
}

export async function fetchCarsForUser(_userId: string): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("cars")
    .select("*")
    .order("created_at", { ascending: false });

  if (error) throw error;

  // RLS returns exactly the rows the current user can access.
  return data || [];
}

export async function fetchOwnedCarsForUser(userId: string): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("cars")
    .select("*")
    .eq("owner_id", userId);

  if (error) throw error;
  return data || [];
}

export async function createCarForUser(
  userId: string,
  name: string,
  tankCapacity: number,
  details?: {
    vin?: string | null;
    plate?: string | null;
    make?: string | null;
    model?: string | null;
    year?: number | null;
  },
): Promise<any> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("cars")
    .insert([
      {
        name,
        tank_capacity: tankCapacity,
        vin: details?.vin || null,
        plate: details?.plate || null,
        make: details?.make || null,
        model: details?.model || null,
        year: details?.year ?? null,
        owner_id: userId,
      },
    ])
    .select()
    .single();

  if (error) throw error;
  return data;
}

export async function deleteCarById(carId: string) {
  const supabase = getSupabaseClient();
  const { error } = await supabase.from("cars").delete().eq("id", carId);

  if (error) throw error;
}

export async function updateCarById(
  carId: string,
  payload: {
    name: string;
    tank_capacity: number;
    vin?: string | null;
    plate?: string | null;
    make?: string | null;
    model?: string | null;
    year?: number | null;
  },
): Promise<any> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("cars")
    .update(payload)
    .eq("id", carId)
    .select()
    .single();

  if (error) throw error;
  return data;
}

export async function listCarMembers(carId: string): Promise<CarMember[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc("list_car_members", {
    p_car_id: carId,
  });

  if (error) throw error;
  return data || [];
}

export async function grantCarAccess(carId: string, userId: string) {
  const supabase = getSupabaseClient();
  const { error } = await supabase
    .from("car_access")
    .insert([{ car_id: carId, user_id: userId }]);

  if (error) throw error;
}

export async function shareCarWithEmail(carId: string, email: string) {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc("share_car_with_email", {
    p_car_id: carId,
    p_email: email,
  });

  if (error) throw error;
  return data as string;
}

export async function fetchRefuelEventsForCar(carId: string): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("refuel_events")
    .select(
      "*, fuel_station:fuel_stations(id, brand, place, street, house_number)",
    )
    .eq("car_id", carId)
    .order("fueled_at", { ascending: false });

  if (error) throw error;
  return data || [];
}

export async function fetchLatestAccessibleRefuelEvent(): Promise<LastRefuelEventPoint | null> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("refuel_events")
    .select(
      "fueled_at, price_per_liter_calculated, liters, total_price, created_at",
    )
    .order("fueled_at", { ascending: false })
    .order("created_at", { ascending: false })
    .limit(1);

  if (error) throw error;

  const row = Array.isArray(data) ? (data[0] ?? null) : null;
  if (!row?.fueled_at) return null;

  const directPrice = Number(row.price_per_liter_calculated);
  const fallbackPrice = row.liters
    ? Number(row.total_price) / Number(row.liters)
    : NaN;
  const pricePerLiter = Number.isFinite(directPrice)
    ? directPrice
    : fallbackPrice;

  if (!Number.isFinite(pricePerLiter)) return null;

  return {
    fueled_at: row.fueled_at,
    price_per_liter: pricePerLiter,
  };
}

export async function fetchCarExpensesForCar(carId: string): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("car_expenses")
    .select("*")
    .eq("car_id", carId)
    .order("expensed_at", { ascending: false });

  if (error) throw error;
  return data || [];
}

export async function createCarExpense(payload: CarExpenseInsert) {
  const supabase = getSupabaseClient();
  const { error } = await supabase.from("car_expenses").insert([payload]);

  if (error) throw error;
}

export async function updateCarExpense(
  expenseId: number,
  payload: CarExpenseUpdate,
) {
  const supabase = getSupabaseClient();
  const { error } = await supabase
    .from("car_expenses")
    .update(payload)
    .eq("id", expenseId);

  if (error) throw error;
}

export async function createRefuelEvent(payload: RefuelEventInsert) {
  const supabase = getSupabaseClient();
  const { error } = await supabase.from("refuel_events").insert([payload]);

  if (error) throw error;
}

export async function updateRefuelEvent(
  eventId: number,
  payload: RefuelEventUpdate,
) {
  const supabase = getSupabaseClient();
  const { error } = await supabase
    .from("refuel_events")
    .update(payload)
    .eq("id", eventId);

  if (error) throw error;
}

export async function fetchCarRefuelYearBounds(
  carId: string,
): Promise<{ min_year: number | null; max_year: number | null }> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc(
    "get_car_refuel_year_bounds",
    {
      p_car_id: carId,
    },
  );

  if (error) throw error;
  const row = Array.isArray(data) ? data[0] : null;
  return {
    min_year: row?.min_year ?? null,
    max_year: row?.max_year ?? null,
  };
}

export async function fetchCarRefuelStatistics(carId: string): Promise<any> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc(
    "get_car_refuel_statistics",
    {
      p_car_id: carId,
    },
  );

  if (error) throw error;
  return Array.isArray(data) ? (data[0] ?? null) : null;
}

export async function fetchCarRefuelPlotEvents(
  carId: string,
  sinceIso: string,
  untilIso: string,
): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc(
    "get_car_refuel_plot_events",
    {
      p_car_id: carId,
      p_since: sinceIso,
      p_until: untilIso,
    },
  );

  if (error) throw error;
  return data || [];
}

export async function fetchCarKmPerMonth(
  carId: string,
  sinceIso: string,
  untilIso: string,
): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await (supabase as any).rpc("get_car_km_per_month", {
    p_car_id: carId,
    p_since: sinceIso,
    p_until: untilIso,
  });

  if (error) throw error;
  return data || [];
}
