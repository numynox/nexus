# ADR: Abeona's maps go dark with the theme, lifted for contrast

**Date:** 2026-11-15
**Status:** Accepted

## Context
In a dark theme the web drew the light CARTO basemap, a bright block in a dark
page. Android already switched to CARTO's Dark Matter, which is so close to
black that roads, water and names nearly vanish; without a CARTO key it dimmed
OpenStreetMap's bright tiles to a murky brown. The owner asked for a dark map
on both clients that keeps some contrast.

## Decision
- The basemap stays the one `2026-09-05-carto-basemap-api-key.md` chose:
  CARTO with the key, OpenStreetMap without one. Only the dark theme changes.
- **CARTO**: Dark Matter (`dark_all`), lifted out of black so each tile
  value becomes 0.16 + 0.84 × itself. Land, roads and labels part, and the
  route on top still stands out against the lifted grey.
- **OpenStreetMap** has no dark style: its tiles are inverted (90 %) with the
  hues turned back, colours halved and dimmed, which keeps forests green,
  water blue and roads light lines on dark land.
- **The same numbers on both clients**: MapLibre raster paint on Android
  (`raster-brightness-min/max`, `raster-hue-rotate`, `raster-saturation`),
  an equivalent CSS filter on the web's tile pane only (`lib/basemap.ts`), so
  the route, stops and labels drawn on the map are never filtered. The web
  swaps tiles and filter when the theme changes.

## Consequences
- The CARTO dark look could not be checked in a session without the key (its
  tiles come back watermarked); the OpenStreetMap path was checked by
  screenshots. Look at the dark map once on a build with the key, and adjust
  the lift (0.16) in both files together if it is too grey or too black.
- Android's screenshot tests draw the schematic map, not tiles, so they do not
  show this either.
- Raster tiles cannot be restyled feature by feature; for more control the
  map would need CARTO's vector styles and MapLibre on the web too.
