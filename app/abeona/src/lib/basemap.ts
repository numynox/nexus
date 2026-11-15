/**
 * The basemap both of Abeona's maps draw on. Same choice as Vibilia
 * (decisions/2026-09-05-carto-basemap-api-key.md): CARTO with the public
 * build-time key, OpenStreetMap without one. In a dark theme the map is dark
 * too, tuned so roads, water and names still stand out
 * (decisions/2026-11-15-abeona-dark-map.md); `filter` is applied to the tiles
 * only, never to the route drawn on them. Android's TripMap.kt applies the same
 * numbers as MapLibre raster paint.
 */
export function basemap(dark = false) {
  const cartoKey = import.meta.env.PUBLIC_CARTO_API_KEY;
  if (cartoKey) {
    return {
      url: `https://{s}.basemaps.cartocdn.com/${dark ? "dark_all" : "light_all"}/{z}/{x}/{y}{r}.png?key=${cartoKey}`,
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains: "abcd",
      maxZoom: 20,
      // Dark Matter lifted out of black: 0.16 + 0.84 × the tile, so land, roads and labels part.
      filter: dark ? "contrast(0.724) brightness(1.16)" : "none",
    };
  }
  return {
    url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    subdomains: "abc",
    maxZoom: 19,
    // OpenStreetMap has no dark style: inverted with its hues kept, colours calmed, dimmed.
    filter: dark ? "invert(0.9) hue-rotate(180deg) saturate(0.5) brightness(0.9)" : "none",
  };
}

/** Whether the page shows a dark theme now (Abeona's own or a shared one). */
export function isDarkTheme(): boolean {
  return getComputedStyle(document.documentElement).colorScheme.includes("dark");
}

/** A theme colour from the stylesheet, for drawing on the map. */
export function cssColor(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/** "DE" → "Germany", in English. */
export function countryName(code: string): string {
  try {
    return new Intl.DisplayNames(["en"], { type: "region" }).of(code) ?? code;
  } catch {
    return code;
  }
}

/** "DE" → 🇩🇪 */
export function countryFlag(code: string): string {
  if (!/^[A-Z]{2}$/.test(code)) return "";
  return String.fromCodePoint(...[...code].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}
