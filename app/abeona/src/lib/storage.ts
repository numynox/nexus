import { getTheme as readTheme, setTheme as writeTheme } from "@nexus/ui";
import { ABEONA_THEMES } from "./themes";
import { DEFAULT_STATION_MINUTES } from "./travelday";

/**
 * Device-local preferences. Trips and travellers live in Postgres, shared by
 * the household; what is here belongs to this browser only, and every value is
 * checked on read.
 */
const STORAGE_KEYS = {
  THEME: "abeona_theme",
  SEARCH: "abeona_search",
  STATION_MINUTES: "abeona_station_minutes",
  TRIP_LOCATION: "abeona_trip_location",
} as const;

export function getTheme(): string {
  return readTheme(STORAGE_KEYS.THEME, ABEONA_THEMES);
}

export function setTheme(theme: string): void {
  writeTheme(STORAGE_KEYS.THEME, theme, ABEONA_THEMES);
}

/** The last search's stations and options, so the form opens where it was left. */
export interface SavedSearch {
  from: { id: string; name: string } | null;
  to: { id: string; name: string } | null;
  regionalOnly: boolean;
}

function readJson(key: string): unknown {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeJson(key: string, value: unknown): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage full or disabled: the preference is simply not remembered.
  }
}

function asStation(value: unknown): { id: string; name: string } | null {
  if (!value || typeof value !== "object") return null;
  const { id, name } = value as Record<string, unknown>;
  if (typeof id !== "string" || typeof name !== "string" || !id || !name) {
    return null;
  }
  return { id, name };
}

export function getSavedSearch(): SavedSearch {
  const raw = readJson(STORAGE_KEYS.SEARCH) as Record<string, unknown> | null;
  return {
    from: asStation(raw?.from),
    to: asStation(raw?.to),
    regionalOnly: raw?.regionalOnly === true,
  };
}

export function setSavedSearch(search: SavedSearch): void {
  writeJson(STORAGE_KEYS.SEARCH, search);
}

/** The choices for the time to the station, in minutes; the same on Android. */
export const STATION_MINUTES_CHOICES = [5, 10, 15, 20, 30, 45, 60] as const;

/**
 * How long this device's person needs from the door to the platform: the
 * "time to leave" is the first train's departure minus this. Per device,
 * because it depends on where the device usually is.
 */
export function getStationMinutes(): number {
  const raw = readJson(STORAGE_KEYS.STATION_MINUTES);
  return typeof raw === "number" && Number.isInteger(raw) && raw >= 0 && raw <= 180 ? raw : DEFAULT_STATION_MINUTES;
}

export function setStationMinutes(minutes: number): void {
  writeJson(STORAGE_KEYS.STATION_MINUTES, Math.max(0, Math.min(180, Math.round(minutes))));
}

/**
 * Use this browser's position on a live trip's page, while it is open: the
 * dashboard then shows where the traveller is and can tell the station from
 * the train. Off until turned on; the position never leaves the browser.
 */
export function getTripLocation(): boolean {
  return readJson(STORAGE_KEYS.TRIP_LOCATION) === true;
}

export function setTripLocation(on: boolean): void {
  writeJson(STORAGE_KEYS.TRIP_LOCATION, on);
}
