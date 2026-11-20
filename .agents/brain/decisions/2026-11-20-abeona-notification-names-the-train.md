# ADR: Abeona's on-board notification names the train in its title

**Date:** 2026-11-20
**Status:** Accepted

## Context
On a real ride the on-board notification read "Change at Frankfurt (Main)
Flughafen Fernbahnh…" (cut off), then "Arrives 10:19 · Pl. Fern 7", "Then
ICE 16 · Pl. Fern 7 · 10:48 · 29 min to change (Safe)", "Next: Hanau
Hauptbahnhof 09:41 · 3 stops left · Live 08:53". The long station name took
the title and was cut; the arrival time and platform, the facts to act on,
were a line lower; "(Safe)" said nothing; and the stops left sat between two
unrelated facts. The owner asked for the lines below, word for word.

## Decision
On board, `CompanionText.kt`:
1. Title: the train, when it arrives where the next thing happens, the
   platform there and the time: "ICE 728 arrives in 1 h 25 min · Pl. Fern 7 ·
   10:19", with "(+6)" after the time when late.
2. "Then ICE 16 · Pl. Fern 7 · 10:48 · 29 min change", with "(Tight)",
   "(At risk)" or "(Impossible)" after it; a safe change goes without saying.
3. The next stop: "Next: Hanau Hauptbahnhof 09:41" ("Now at …" while standing
   at one), without the count.
4. Last, small: "3 stops left · 97 km/h", then the freshness ("Live 09:01").
- The last ride has no "Then" line and its title names no place, so the
  destination leads the lines ("Bamberg").
- Before leaving, at the station, changing, missed and arrived are unchanged.

## Consequences
- Supersedes the "Most urgent first" bullet of
  `2026-11-13-abeona-notification-leads-with-the-next-thing.md`; its other
  decisions (no chronometer, freshness at the end) hold.
- The title no longer names the station of the change: the "Then" line and
  the trip screen do. A collapsed notification shows the title and the
  "Then" line (or, on the last ride, the destination).
- The title is redrawn every 15 s and is at most that far behind.
