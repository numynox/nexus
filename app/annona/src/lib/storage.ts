import {
  getTheme as readTheme,
  setTheme as writeTheme,
} from "@nexus/ui";

const isBrowser = (): boolean => typeof window !== "undefined";

const STORAGE_KEYS = {
  THEME: "annona_theme",
} as const;

function getStorageItem<T>(key: string, fallback: T): T {
  if (!isBrowser()) return fallback;
  try {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : fallback;
  } catch {
    return fallback;
  }
}

function setStorageItem<T>(key: string, value: T): void {
  if (!isBrowser()) return;
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    console.warn("Failed to save to localStorage:", error);
  }
}

export function getTheme(): string {
  return readTheme(STORAGE_KEYS.THEME);
}

export function setTheme(theme: string): void {
  writeTheme(STORAGE_KEYS.THEME, theme);
}

