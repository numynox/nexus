# ADR: Passenger-rights compensation as rows, one verdict in SQL

**Date:** 2026-11-03
**Status:** Accepted

## Context
#38 phase 5 asks what a late trip is worth, a German text for DB's
passenger-rights form and a tracker of claims. The rules (EU Regulation
2021/782 as applied by DB) have thresholds, flat rates for season tickets,
a minimum payout, a deadline and a force-majeure exclusion — and they change
(DB raised the taxi cap, the Deutschlandticket's price moves every year).
Installed APKs lag behind the web by weeks, so the rules must not live in
either client. The fare rules of phase 4 set the pattern
(`2026-11-03-abeona-fare-rules-and-device-signals.md`).

## Decision
- **`abeona_compensation_rules` holds the figures**, one row per rule with a
  `kind` (tier, flat, minimum, deadline, night, force_majeure) and an English
  `note`/`hint` both clients show as is. Reference data: read by signed-in
  users, written by migrations only.
- **`abeona_compensation(trip_id) → jsonb` is the one verdict**: eligible,
  not_eligible, below_minimum, unknown_price, unknown_arrival (a ride missed
  or cancelled and nothing recorded instead), with the amount, the tier,
  reasons, hints, the deadline and the German summary for the form. Security
  invoker, so RLS is the access check; it writes nothing.
- **Seeded figures — every one to verify against fahrgastrechte.info and
  bahn.de before relying on it** (taken in November 2026):
  - ≥ 60 min late at the destination: 25 % of the fare; ≥ 120 min: 50 %.
  - Payouts under €4 are not made; season tickets may pool cases.
  - Deutschlandticket: €1.50 per case, at most 25 % of the ticket's value
    (the trip's price, else €63, the 2026 monthly price).
  - BahnCard 100: €10 per case in 2nd class, €15 in 1st, at most 25 % of the
    card's value.
  - Claim within three months (counted from the planned arrival's day).
  - No compensation for force majeure (extreme weather, natural disaster,
    a person on the track); strikes by railway staff are not.
  - Arriving between midnight and 5 am at least 60 minutes late: taxi up to
    €120 or a hotel — shown as a hint, never computed.
- **The trip says what happened**: `force_majeure` (the traveller ticks it),
  `missed_leg_idx` and `replacement_legs` (contract-shaped legs, picked from a
  connection search at the missed ride's stop and time, or one entered by
  hand). The real arrival is the replacement's.
- **Claims are rows per trip** (`abeona_claims`, open → claimed → paid or
  rejected) whose access follows the trip's household, like BahnCards follow
  their traveller; `abeona_claims_overview(year)` lists what is to claim,
  what waits for DB, and what was earned back.

## Consequences
- A correction is a migration (`update abeona_compensation_rules …`), not a
  release. The caps are applied per case only; the cumulative caps over a
  month or a card's validity are shown as hints, not tracked.
- Abeona never files a claim: the user copies the text into DB's form. The
  verdict is advice, and both clients say so.
- Prices are only as good as what the user entered; without one the verdict
  is unknown_price and the card asks for it.
- The night hint keys on the planned arrival time only; a cancelled last
  train in the evening is not detected.
