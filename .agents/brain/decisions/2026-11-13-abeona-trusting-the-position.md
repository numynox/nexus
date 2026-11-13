# ADR: Abeona — when the position may overrule the clock

**Date:** 2026-11-13
**Status:** Accepted

## Context
The first test on real trains (2026-11-13) showed both ways the position
rules of `2026-11-03-abeona-fare-rules-and-device-signals.md` go wrong:

- **"Missed" for three stops while on board.** The phone asked the fused
  provider at balanced power, which in a train answers mostly from Wi-Fi.
  The train's own access points travel with it, so a moving phone can be
  pinned to the station the train last stood at. Such fixes have no speed,
  an unknown speed counted as standing still, and any accuracy counted —
  so "standing at the first stop after it left" was concluded from missing
  evidence. Fixes were also stamped when delivered, not when measured.
- **Location and timetable alternating on an S-Bahn.** "On board" was only
  confirmed above 30 km/h, so every stop dropped back to the clock; the
  50 m update filter also let the last fix go stale during a stop.

## Decision
The position confirms easily and contradicts only on strong evidence:

- Fixes less accurate than **100 m** are ignored altogether.
- **Missed** needs a fix of **50 m** or better with a **known** speed of at
  most **10 km/h**, at the ride's first stop, 2 min after it left. Anything
  else at the first stop says nothing (the clock decides).
- **On board** is confirmed **along the ride at any speed** — within 1 km
  of its line, or near one of its later stops — except at its first stop,
  where it still takes more than 30 km/h. A train stands at its stops.
- Android asks the **GPS provider** first (fused, then network, only
  without it or with approximate location only), delivers every fix every
  30 s, and stamps each fix with when it was measured. Both clients work
  speed out from two fixes only when both are accurate to 50 m.

The rules stay in `travelday.ts` and `domain/TravelDay.kt`, pinned by
`travelday.fixtures.json`.

## Consequences
- Where GPS does not reach into the carriage, there is simply no position
  and the travel day follows the clock — the intended degradation.
- A real missed train needs a GPS fix at the platform, so "missed" can come
  a little later than before, and never from Wi-Fi or cell positions.
- Still stateless: each check decides from the latest fix. If flapping
  remains, the next step is remembering the last confirmed state.
- GPS every 30 s costs some battery, only while the companion runs.
