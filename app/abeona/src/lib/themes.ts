import { THEMES, type ThemeSet } from "@nexus/ui";

/**
 * Abeona brings its own two themes, defined in styles/global.css, and "auto"
 * resolves to them rather than to the shared lofi/dark pair. The curated
 * shared set stays available in the picker after them.
 */
export const ABEONA_THEMES: ThemeSet = {
  themes: [
    { name: "abeona", label: "Abeona", dark: false },
    { name: "abeona-dark", label: "Abeona Night", dark: true },
    ...THEMES,
  ],
  autoLight: "abeona",
  autoDark: "abeona-dark",
};
