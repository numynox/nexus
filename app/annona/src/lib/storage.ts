import { getTheme as readTheme, setTheme as writeTheme } from "@nexus/ui";

/**
 * Device-local preferences. Annona keeps everything else in Postgres — the
 * inventory is shared between everyone in the household — so the theme is the
 * only thing here.
 */
const STORAGE_KEYS = {
  THEME: "annona_theme",
} as const;

export function getTheme(): string {
  return readTheme(STORAGE_KEYS.THEME);
}

export function setTheme(theme: string): void {
  writeTheme(STORAGE_KEYS.THEME, theme);
}
