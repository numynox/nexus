# ADR: Abeona — impossible changes lift the binding, and alternatives can be taken

**Date:** 2026-11-09
**Status:** Accepted

## Context
A change with no time left was shown as "at risk", like one with a minute too
few. DB's train binding was only lifted at 20 minutes' delay or a
cancellation, so a Sparpreis traveller whose connection could not work was
told only their booked trains counted. And the alternatives could be looked
at but not taken: Abeona kept following a plan the traveller had given up.

A source for "the connecting train waits" was considered and dropped: it is
not in Transitous, and DB's own interfaces are unofficial.

## Decision
- **Impossible** is a fourth transfer confidence: 0 minutes or less between
  the arrival and the next departure, by the live times. A cancelled ride
  stays "at risk". Impossible ranks above at risk as a reason to offer
  alternatives.
- **A broken connection lifts the binding**, besides the delay and a
  cancellation: an impossible change, or a change of less than 5 minutes
  (live, at the moment) that the phone's position saw missed
  (`missedShortConnection`; position only, not the train's Wi-Fi or the
  clock). The binding is judged on the booked journey. The server applies
  the same in `abeona_allowed_products(p_trip_id, p_connection_missed)`; the
  client says the miss, since only it knows; `binding_lifted_reason` names
  the cause and the alternatives' notes repeat it.
- **Alternatives can be taken.** "Take this connection" on a covered one,
  "Take it anyway…" with a confirmation ("I understand, take it") on one the
  ticket does not or may not cover. The choice is stored in the existing
  `missed_leg_idx` / `replacement_legs` (the record "What happened" already
  reads), not a new table: during the trip it is the plan from there on.
  `followedLegs` builds the journey as travelled and cuts the ride left early
  at the stop the replacement starts from; `takeConnection` splices a second
  choice into the first. "Back to the plan" clears it.
- **The replacement is refreshed leg by leg** (by each ride's trip ref; no
  refresh token belongs to it) and written back as JSON by the client, both
  clients and the live companion, which also takes a connection taken on the
  trip screen at once.

## Consequences
- The migration and the Edge Function are not deployed by CI. Deploy the
  migration first: the new Edge Function falls back to the old call on a
  database without it, and older clients keep calling
  `abeona_allowed_products(p_trip_id)`.
- The final snapshot job freezes `abeona_legs`, not `replacement_legs`: the
  replacement keeps the last live values a client saw, which the companion
  refreshes until 30 minutes after arrival. Compensation reads that arrival.
- Taking a connection is a household-wide change: everyone's trip page and
  companion follow it.
- Rules written twice and pinned by fixtures: `transfer.fixtures.json`,
  `travelday.fixtures.json` (`missedShortConnection`), `followed.fixtures.json`.
