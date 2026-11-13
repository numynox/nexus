# ADR: Abeona records what a ticket needs, not who travels

**Date:** 2026-11-13
**Status:** Accepted

## Context
Abeona tracked travellers: people in the household (with or without an
account), each with their own BahnCards, put on each trip with the discount
their share of the fare was bought with, and checked by trip warnings. In
use this was bookkeeping nobody wanted. A ticket prints persons, not names,
so every save asked who was who. What matters on the train is how many people
the ticket is for, which BahnCards it needs, and the cards themselves.
Requested in #62.

## Decision
- **A trip records its ticket**: `traveller_count` (null: unknown) and
  `bahncard_discounts` (one per card, ascending) on `abeona_trips`. They are
  read from the ticket by the parser (`ticketSummary`, `lib/ticketNeeds.ts`
  and `domain/TicketNeeds.kt`, pinned by `intake.fixtures.json`), editable
  when saving, and from the trip's ⋮ menu. The trip page shows them quietly,
  as icons.
- **BahnCards belong to the household**, any number, each with an optional
  name. They keep their validity, card file and cancellation reminders. The
  ticket viewer shows the cards the ticket needs (`cardsForTrip`): one per
  discount, the same kind first, else the smallest that covers it. A
  BahnCard 100 fare needs one per traveller.
- **Gone:** `abeona_travellers`, `abeona_trip_travellers`, the trips' single
  `bahncard_discount`, the trip warnings and their RPCs, and the travellers
  in Settings. Households and sharing stay as they were.
- **Not backwards compatible, on purpose.** The migration drops what older
  APKs read: until updated, they lose their traveller features and may fail
  to save trips. Decided for #62, against the usual "installed apps lag
  behind" rule, since the household updates its APKs.
- Share options moved into a sheet opened from the trip's ⋮ menu.

## Consequences
- A household with several cards of one kind sees whichever the viewer
  picks first. The card name says whose it is.
- No check that the household holds the BahnCards a ticket needs. The trip
  warnings did that; their messages were the most visible part of tracking
  travellers.
- Supersedes `2026-11-04-abeona-discount-per-traveller.md`, and the
  "cards belong to travellers" part of
  `2026-11-03-abeona-bahncard-files-follow-the-card.md`. Its file path rule
  (`<household_id>/bahncards/<card_id>/…`, access follows the card) stands.
