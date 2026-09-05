# ADR: CARTO basemaps with a public key, OpenStreetMap as the fallback

**Date:** 2026-09-05
**Status:** Accepted

## Context
In August 2026 CARTO began requiring an API key for `basemaps.cartocdn.com`.
The request does not fail: the tile comes back HTTP 200 with
**"API KEY REQUIRED"** stamped diagonally across it. Vibilia's nearby map used
those tiles keylessly, so every visit showed a watermarked map — the least
professional thing in the repo, and invisible in logs because nothing errored.

Three options were weighed: a keyless replacement with a similar muted look
(Esri's canvas basemaps, which work today but could tighten the same way),
OpenStreetMap's standard tiles (keyless, community-run, durable, but bright and
colourful against a dark UI), or staying on CARTO with a key.

## Decision
Stay on CARTO. The key is requested at
[carto.com/basemaps/apikey](https://carto.com/basemaps/apikey/) — free within
5 million tile requests a month, no CARTO account required — and is passed as
`?key=` on the existing tile URL.

It is a **public** credential: it ships in the browser bundle like the Supabase
anon key, so it is a build-time `PUBLIC_CARTO_API_KEY` and a GitHub repository
variable, restricted by domain at CARTO's end rather than kept secret.

When the variable is unset, the map falls back to OpenStreetMap's standard
tiles with matching attribution. Local development and any unconfigured
environment then look correct rather than watermarked, and the fallback needs no
signup.

## Consequences
- Changing or rotating the key needs a rebuild — there is no runtime
  configuration in a static site.
- Attribution for CARTO and OpenStreetMap must stay visible; it is a condition
  of the free tier, and the attribution control switches with the provider.
- **CARTO is retiring the raster PNG basemaps.** No date has been given, but
  they have said data updates may stop and that "the gap will widen over time".
  This buys a working map now, not permanently. When it goes, the choice is
  their vector basemaps (which would mean MapLibre instead of plain Leaflet) or
  the keyless providers rejected here.
- Exceeding 5M tiles a month is not a realistic risk for personal use; CARTO
  say they get in touch rather than cutting access.
