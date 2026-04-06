import type { Session } from "@supabase/supabase-js";
import { writable } from "svelte/store";
import { getPreferredFuelType } from "./storage";

export const session = writable<Session | null>(null);
export const preferredFuelType = writable<string>(getPreferredFuelType());
