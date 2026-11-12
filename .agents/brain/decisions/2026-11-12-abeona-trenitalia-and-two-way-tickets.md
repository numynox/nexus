# ADR: Intake reads Trenitalia tickets, and DB tickets for both directions as two trips

**Date:** 2026-11-12
**Status:** Accepted

## Context
The intake (`2026-11-04-abeona-intake.md`) read one kind of PDF: a DB
Online-Ticket for one direction. The user supplied two more real samples:

- A **Trenitalia** ticket bought on bahn.de (German labels): one Frecciarossa
  ride for two people, as one PDF with a page per passenger, each page with
  its own price, seat and ticket number. pdf.js and PdfBox give its station
  row, coach and seat in another order than `pdftotext -layout`.
- A **DB "Super Sparpreis (Hin- und Rückfahrt)"**: one ticket, one price, two
  stop tables (Hinfahrt, Rückfahrt) and a "Zugbindung" for each. The parser
  read only the first table, so the return was lost. Both tables also start
  or end with an S-Bahn the ticket does not cover.

## Decision
- **A Trenitalia parser of its own** (`trenitaliaTicket.ts`,
  `TrenitaliaTicket.kt`), read by labels rather than columns: the stations
  from the title ("REISE von … Nach … am dd/mm/yyyy"), the times from
  "Uhrzeit hh:mm - dd/mm/yyyy", the train from "Zug: Frecciarossa 9715",
  turned into the short name Transitous uses ("FR 9715"; checked
  2026-11-12 for Brescia → Verona Porta Nuova). Italian and English labels are
  accepted where cheap to guess. Pages for the same ride are one ride: the
  travellers are counted, the seats collected, the prices **added up** (what
  the PDF cost). Offers: Economy and Super Economy are saver fares, bound;
  Base is flex. Times are read in Europe/Berlin, which is Italy's offset too.
- **One trip per stop table.** `parseDbTickets` returns one parsed ticket
  per table, each with its direction, its own rides and the bound trains of
  its day; fare, class and travellers are shared. A ticket with one table
  parses exactly as before (`parseDbTicket` is the first).
- **The price of a ticket for both directions is split evenly** between the
  two trips (the first takes an odd cent). DB prints only the total; a split
  keeps each trip's compensation figure in proportion, and the user can
  correct either field before saving.
- **Saved one after the other.** Intake gives a list of drafts; the save
  sheet shows the outward trip ("then the return"), and on saving opens a
  fresh sheet for the return. Each saved trip uploads the same PDF to its own
  wallet path, so access and deletion keep following the trip
  (`2026-11-02-abeona-ticket-storage.md`). Closing the sheet drops the rest.
  Afterwards the app opens the first trip.

## Consequences
- The PDF is stored twice for a two-way ticket. Small, and simpler than a
  shared object whose deletion would need reference counting.
- The S-Bahn the DB ticket does not cover is still saved as a ride of the
  trip, with DB's note as its reservation text, as before.
- Trenitalia is known from one German sample with one ride. A ticket with a
  change, a regional ticket or the Italian original has not been seen; each
  new sample should become a fixture first. A multi-ride Trenitalia ticket
  would take its stations from each page's title, which may be the whole
  journey's.
- The fixture texts are anonymised (names, PNR, ticket and order numbers);
  `synthetic-trenitalia-ticket.pdf` (two pages) is generated from the
  Trenitalia text. The two-way DB ticket has a text fixture, no PDF.
