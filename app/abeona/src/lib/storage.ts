import { getTheme as readTheme, setTheme as writeTheme } from "@nexus/ui";
import { ABEONA_THEMES } from "./themes";

/**
 * Device-local preferences. Trips and travellers live in Postgres, shared by
 * the household; what is here belongs to this browser only, and every value is
 * checked on read.
 */
const STORAGE_KEYS = {
  THEME: "abeona_theme",
  SEARCH: "abeona_search",
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
