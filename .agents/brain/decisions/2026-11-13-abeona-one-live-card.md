# ADR: Abeona's trip page shows one live card

**Date:** 2026-11-13
**Status:** Accepted

## Context
On a real ride the live trip screen was crowded. The header's times, the
first card (final arrival) and the ride card said the same things three
times: Hanau 19:41 +6 in the header, 19:47 +6 on the card, 19:47 in the stop
list. Four buttons sat between the cards. Delays looked different in
different places: the header showed the planned time with a badge, the card
the expected time with a badge, and the timeline the planned time with the
new one in small amber.

## Decision
- **One live card above the map** on both clients (`LiveCard`,
  `LiveDashboard.svelte`), designed in a mockup first. Above it only what
  does not change: date, price, title. Below: the map, then everything that
  is not live. The binding lifted, a connection followed instead and the
  travellers' warnings go inside the card while live.
- **Most urgent first**: the state and source pill; the one thing to know
  with its time, delay and countdown; the ride on a rail; the next stop
  (left out when the only stop left is the one the card leads with); after
  this ride the next train and where the trip ends; then how fresh it is,
  and the speed from the train's Wi-Fi, small. The separate change card and
  the journey strip are gone: their times repeated the card's.
- **Icons over words** where an icon says it: flag (get off), swap (change),
  stops left, next stop, refresh (updated).
- **Actions in the top bar**: the ticket stays one tap away (inspections);
  DB Navigator, alternatives, the companion's start/stop and delete go
  under ⋮. The card keeps its own "See alternatives" when something went
  wrong.
- **One delay format** (`ShiftedTime` on both clients, glossary: delay)
  everywhere a time has a delay: lists, search, alternatives, timeline,
  hero cards, the widget.

## Consequences
The time a stop's planned value comes from for the next stop is looked up
in the leg's stopovers (ride progress carries only expected times). The trip
page outside the live window keeps its header with the times and pills.
