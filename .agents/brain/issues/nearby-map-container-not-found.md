# Leaflet raises "Map container not found" on the nearby fuel page

**Status:** Open
**Discovered:** 2026-09-04

## Problem
Loading `/nexus/vibilia/fuel-price/nearby` logs an uncaught rejection from
`NearbyStationsMap.svelte:196`:

```
Error: Map container not found.
```

The map itself renders, so this is an extra initialisation attempt against a
container that is not in the DOM (yet, or any more) — a mount/teardown race, not
a missing element.

Confirmed **pre-existing**, not a side effect of the Astro 7 upgrade: the same
error appears when running the pre-upgrade code (Astro 5) against the same page.

## Impact
Cosmetic today: an uncaught promise rejection in the console on every load of
that page. It would become real if the failing init is the one that was supposed
to attach a later feature (markers, a resize handler), or if a second Leaflet
instance leaks.

## Workaround
None needed.

## Resolution
Guard the Leaflet init on the container being mounted (and on not already
holding a map), and cancel any pending init in the component's teardown.
