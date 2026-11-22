# ADR: Abeona shows the coach and seat from the ticket, not the coach order

**Date:** 2026-11-22
**Status:** Accepted

## Context
Where to stand on the platform is the feature German travellers missed most.
DB Navigator's 2023 redesign dropped the coach-position display, and the app's
Play rating fell to 2.2. SBB, SJ, NS, Vy and the Japanese and Korean apps all
show the coach position.

The data for it is DB's coach order (`Wagenreihung`). We researched every
source on 2026-11-22, reading the code of travelynx, DB-Infoscreen, bahn.expert
and KDE Itinerary:
- **`ist-wr.noncd.db.de`:** dead since 2024.
- **bahn.de's `web/api/reisebegleitung/wagenreihung/vehicle-sequence`:**
  - It answered `403 OPS_BLOCKED` from our datacenter.
  - Maintainers report blocking by IP range and TLS fingerprint, changing every
    few weeks.
  - DB-Infoscreen switched it off by default in November 2026.
  - bahn.de's terms forbid automated extraction.
- **DB's app API:** blocked the same way.
- **RIS::Transports, the official API:** only for rail operators and third
  parties DB approves.

Abeona has neither a reliable nor a permitted source for the coach order.

The reservation on the ticket is reliable. DB prints it per ride in the stop
table ("1 Sitzplatz, Wg. 24, Pl. 66, 1 Fenster, Großraum, …"), sometimes
wrapped onto the arrival row. Trenitalia prints the coach and seats. Intake
already reads that column, on the device.

## Decision
- `abeona_legs` gains `coach` and `seats`, as printed ("24", "71 73",
  "45-46"). `abeona_save_trip` accepts them and keeps them when a client
  re-saves a trip without them.
- `seatOf(reservation)` on both clients (`lib/intake/seat.ts`,
  `domain/intake/Seat.kt`) reads DB's "Wg. … Pl. …" and Trenitalia's "Coach …,
  seats …", tested by the shared `seats` cases in `intake.fixtures.json`. The DB
  parser joins a ride's note across its two rows.
- The edit sheet lets the traveller type or correct them per ride. They are
  written directly under the legs' RLS.
- Shown as "Coach 24 · Seat 66" with a seat icon:
  - under the Board step, on the next-train row and on expanded later rides;
  - on the timeline and on the ticket;
  - in the notification before boarding.
- No coach order, platform sectors or crowding.

## Consequences
- The traveller still looks at the platform's coach-position sign to find
  where coach 24 stops. Abeona says which coach, not where it stops.
- A trip saved from a search or a share has no seat until it is typed in.
- If DB ever grants RIS::Transports access, the order can be added as its own
  module. The seat on the ride stays as it is.
