import type { AuthChangeEvent, Session } from "@supabase/supabase-js";
import { getSupabaseClient } from "./supabase";

interface RefuelEventInsert {
  car_id: string;
  user_id: string;
  mileage: number;
  liters: number;
  total_price: number;
  fuel_level_after: number;
  price_per_liter_calculated: number;
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

export async function signUp(email: string, password: string) {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase.auth.signUp({ email, password });

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

export async function fetchCarsForUser(userId: string): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data: ownedCars, error: ownedError } = await supabase
    .from("cars")
    .select("*")
    .eq("owner_id", userId);

  if (ownedError) throw ownedError;

  const { data: sharedCars, error: sharedError } = await supabase
    .from("cars")
    .select("*, car_access!inner(user_id)")
    .eq("car_access.user_id", userId);

  if (sharedError) throw sharedError;

  const merged = [...(ownedCars || []), ...(sharedCars || [])];
  const uniqueById = new Map(merged.map((car: any) => [car.id, car]));
  return Array.from(uniqueById.values());
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
): Promise<any> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("cars")
    .insert([
      {
        name,
        tank_capacity: tankCapacity,
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

export async function findProfileById(
  profileId: string,
): Promise<{ id: string }> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("profiles")
    .select("id")
    .eq("id", profileId)
    .single();

  if (error) throw error;
  return data;
}

export async function grantCarAccess(carId: string, userId: string) {
  const supabase = getSupabaseClient();
  const { error } = await supabase
    .from("car_access")
    .insert([{ car_id: carId, user_id: userId }]);

  if (error) throw error;
}

export async function fetchRefuelEventsForCar(carId: string): Promise<any[]> {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("refuel_events")
    .select("*")
    .eq("car_id", carId)
    .order("timestamp", { ascending: false });

  if (error) throw error;
  return data || [];
}

export async function createRefuelEvent(payload: RefuelEventInsert) {
  const supabase = getSupabaseClient();
  const { error } = await supabase.from("refuel_events").insert([payload]);

  if (error) throw error;
}
