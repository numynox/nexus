# ADR: Abeona's ongoing notification leads with the next thing

**Date:** 2026-11-13
**Status:** Accepted

## Context
On a real ride the live notification read "Hanau Hauptbahnhof in 2 h 15 min ·
2:14:41 / Train Wi-Fi · Live 17:32 / Next: Würzburg Hbf … / Arrives 19:47 ·
Pl. 6 (+6) / ICE 528 · 127 km/h". The time left was there twice (title and
the header's to-the-second chronometer from
`2026-11-07-abeona-companion-dashboard.md`), the source of the position took
the header, and the arrival with its delay came after the next stop.

## Decision
- **No chronometer.** The title counts down to the minute and is redrawn on
  every 15 s tick; the header shows no time.
- **No source in the notification.** The header says only how fresh the
  live values are ("Live 17:32", "Not updated since …"). What placed the
  traveller stays on the dashboard's source pill.
- **Most urgent first.** On board, the first line (all a collapsed
  notification shows) is the arrival where the next thing happens, with its
  delay and platform; then the next train with its delay and the minutes to
  change; then the stops on the way (left out when only the stop the title
  names is left); then the train and its speed.

## Consequences
Between ticks the title can be up to 15 s behind, which is fine to the
minute. The status-bar chip of a Live Update is unchanged.
