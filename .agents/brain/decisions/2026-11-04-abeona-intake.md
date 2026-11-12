# ADR: Abeona's intake — parsed on the device, matched ride by ride, the price confirmed

**Date:** 2026-11-04
**Status:** Accepted — extended to Trenitalia tickets and to DB tickets for both directions by `2026-11-12-abeona-trenitalia-and-two-way-tickets.md`

## Context
#37 phase 2: a trip from a DB Navigator share text or a DB ticket PDF
instead of a search. The share link's `vbid` can only be resolved by bahn.de,
which refuses our servers (`2026-11-02-abeona-transit-providers.md`), and a
ticket holds a name and an order number that have no business on a server.
The user supplied real samples (two shares, one ticket), anonymised, which
showed three things the guesses would have missed: regional lines print their
train number in brackets ("RE19 (4901)"), one share has no platforms at all,
and the ticket is the return half of a booking (whose "Gesamtpreis" was at
first taken for the whole booking's; see below).

## Decision
- **Parsing happens on the device, never in an Edge Function.** Twin parsers
  (`app/abeona/src/lib/intake/`, `android/abeona/.../domain/intake/`) for the
  share text and DB's Online-Ticket. The PDF's text comes from pdf.js on the
  web and PdfBox-Android on the phone, laid out in lines; the ticket parser
  reads by regular expressions that allow one space where pdftotext keeps
  columns apart. One parser per operator; ÖBB, SBB and others later.
- **One set of fixtures for both clients.** `intake.fixtures.json` holds the
  expected parse of every sample in `intake/fixtures/`, the fare names, and
  the matching cases; Gradle copies it and the samples onto the Android test
  classpath. The user's real PDF is not committed: `synthetic-ticket.pdf`,
  generated from the anonymised text by `make-synthetic-pdf.mjs`, is what
  both extraction tests read.
- **Matching through the existing `abeona-transit` actions,** no new server
  code: station names → `locations` (first answer), then one `journeys`
  search for the whole trip; a journey whose rides have the same lines and
  departure and arrival minutes is taken whole. Otherwise ride by ride, each
  between its printed stations at its printed time, because Transitous often
  routes the same trains with another change (checked 2026-11-03: Erlangen →
  Leuven, the outward sample's trains on a day in November 2026, changed at Frankfurt Flughafen where DB said Hbf; ride
  by ride, all four rides matched). A ride nothing matches is saved as printed:
  no route, no trip id, no live updates. The sheet says how many matched.
  Times are read in Europe/Berlin.
- **Saved as `source` 'share' or 'pdf'** (both allowed since the foundation
  migration), or 'import' when the trip is already over. A ticket PDF is
  attached to the trip's wallet after the save; a failed upload leaves the
  trip without it.
- **The price is the ticket's own.** A ticket's "Gesamtpreis" is what that
  ticket cost, also when it is one direction of a return booking: DB issues
  one ticket per direction, each with its own price. It goes into
  `price_cents` as printed. (At first a return half was taken to print the
  whole booking, and the sheet asked "Half or all of it?" before saving; the
  user, who books these, corrected that on 2026-11-04 and the question was
  removed.) The ticket's traveller count is a hint beside "Who travels", not a
  selection.
- **Entry points:** Android takes `ACTION_SEND` for `text/plain` and
  `application/pdf`, and Search has "Add from a ticket or share". The web has
  the same card on the search page and a Web Share Target: the manifest POSTs
  to `share-target`, the service worker keeps the text and file in its cache
  and opens `search?intake=shared`.

## Consequences
- The parsers know one sample of each kind. An English DB Navigator, a
  Flexpreis with a seat reservation, or a ticket with both directions in one
  PDF have not been seen; the words are accepted where cheap to guess, and
  each new sample should become a fixture first.
- A ride saved as printed has no `trip_ref`, so neither the clients nor the
  snapshot job can refresh it; it stays as planned.
- Matching costs up to one station lookup per distinct station and one search
  per ride (9 requests for four rides), all through the response cache.
- The Web Share Target only appears once the app is installed (Chrome), and
  only works while the service worker is running.
- PdfBox-Android adds a few MB to the APK; JPEG 2000 support is left out
  (`proguard-rules.pro`).
