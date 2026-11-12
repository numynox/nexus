/**
 * A Trenitalia ticket's text layer → fare, class, travellers, rides and price.
 * Read on the device like DB's (decisions/2026-11-12-abeona-trenitalia-and-two-way-tickets.md).
 * Mirrored in Kotlin (domain/intake/TrenitaliaTicket.kt); both run the cases
 * in intake.fixtures.json ("trenitalia").
 *
 * One PDF holds one ticket per passenger, each on a page of its own and each
 * with its own price; the rides they share are one ride, the prices add up.
 * The sample is German (bought on bahn.de); the Italian and English labels
 * are accepted where cheap to guess.
 *
 * Read by labels, not by columns: pdf.js and PdfBox put the station row, the
 * coach and the seat in another order than `pdftotext -layout` does, so the
 * stations come from the title ("REISE von … Nach … am …") and every other
 * value from its "Label: value".
 */
import { hhmm, isoDate, lines, nextDay, squash } from "./common.ts";
import { fareOf } from "./fare.ts";
import type { ParsedLeg, ParsedTicket } from "./parsed.ts";

const TITLE =
  /\b(?:REISE|VIAGGIO|JOURNEY|TRIP)\s+(?:von|da|from)\s+(.+?)\s+(?:nach|a|to)\s+(.+?)\s+(?:am|il|on)\s+(\d{1,2})\/(\d{1,2})\/(\d{4})\b/i;
const TRAIN = /\b(?:Zug|Treno|Train)\s*:\s*([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ. ]*?)\s+(\d{1,5})\b/i;
const TIME = /\b(?:Uhrzeit|Ora|Orario|Time)\s*:?\s*(\d{1,2}:\d{2})\s*-\s*(\d{1,2})\/(\d{1,2})\/(\d{4})/gi;
const COACH = /\b(?:Wagen|Carrozza|Coach)\s*:\s*(\w{1,3})\b/i;
const SEATS = /\b(?:Sitzpl[äa]tze?|Posti|Posto|Seats?)\s*:\s*(\d{1,3}[A-Z]?(?:\s*,\s*\d{1,3}[A-Z]?)*)/i;
const SERVICE = /\b(?:Serviceleistung|Servizio|Service)\s*:\s*(?:([12])\s*°\s*)?([A-Za-z]+)?/i;
const AMOUNT = /\b(?:Betrag|Importo|Amount|Prezzo|Price)\s*\*?\s*:\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|\d+[.,]\d{2})\s*(?:€|EUR)/i;
// The offers on the sample and their siblings; anything else is read as "other".
const OFFER = /\b(Super Economy|Economy|Base)\b/;

/** Trenitalia's train categories → the short names the timetable uses ("FR 9715"). */
const PRODUCTS: [RegExp, string][] = [
  [/^frecciarossa/i, "FR"],
  [/^frecciargento/i, "FA"],
  [/^frecciabianca/i, "FB"],
  [/^intercity\s*notte/i, "ICN"],
  [/^intercity/i, "IC"],
  [/^eurocity/i, "EC"],
  [/^euronight/i, "EN"],
  [/^regionale\s*veloce/i, "RV"],
  [/^regionale/i, "R"],
];

function productOf(category: string): string {
  const name = squash(category);
  for (const [pattern, short] of PRODUCTS) if (pattern.test(name)) return short;
  return name.toUpperCase();
}

/** Whether the text looks like a Trenitalia ticket: the operator's name, a journey title and a train. */
export function isTrenitaliaTicket(text: string): boolean {
  return /trenitalia/i.test(text) && TITLE.test(text) && TRAIN.test(text);
}

/** One passenger's ticket: one page of the PDF. */
interface Block {
  leg: ParsedLeg;
  coach: string | null;
  seats: string[];
  offer: string | null;
  travelClass: 1 | 2 | null;
  cents: number | null;
}

/** "15.90" or "1.234,50" → cents: the last two digits are the cents, whatever separates them. */
function amountCents(raw: string): number {
  return Number(raw.replace(/\D/g, ""));
}

function classOf(digit: string | undefined, level: string | undefined): 1 | 2 | null {
  if (digit) return Number(digit) as 1 | 2;
  if (!level) return null;
  if (/^(executive|business|prima)$/i.test(level)) return 1;
  if (/^(standard|seconda)$/i.test(level)) return 2;
  return null;
}

function readBlock(text: string): Block | null {
  const title = TITLE.exec(text);
  const train = TRAIN.exec(text);
  if (!title || !train) return null;
  const times = [...text.matchAll(TIME)];
  const departure = times[0] ? hhmm(times[0][1]) : null;
  const arrival = times[1] ? hhmm(times[1][1]) : null;
  const date = times[0] ? isoDate(times[0][2], times[0][3], times[0][4]) : isoDate(title[3], title[4], title[5]);
  if (!departure || !arrival || !date) return null;
  const printedArrivalDate = times[1] ? isoDate(times[1][2], times[1][3], times[1][4]) : null;
  const arrivalDate = printedArrivalDate ?? (arrival < departure ? nextDay(date) : date);

  const product = productOf(train[1]);
  const service = SERVICE.exec(text);
  const amount = AMOUNT.exec(text);
  return {
    leg: {
      line: `${product} ${train[2]}`,
      product,
      number: train[2],
      headsign: null,
      from: squash(title[1]),
      to: squash(title[2]),
      date,
      departure,
      arrival_date: arrivalDate,
      arrival,
      from_platform: null,
      to_platform: null,
      reservation: null,
    },
    coach: COACH.exec(text)?.[1] ?? null,
    seats: SEATS.exec(text)?.[1].split(",").map((s) => s.trim()) ?? [],
    offer: OFFER.exec(text)?.[1] ?? null,
    travelClass: service ? classOf(service[1], service[2]) : null,
    cents: amount ? amountCents(amount[1]) : null,
  };
}

/** "Coach 7, seats 10C, 10D". */
function reservationOf(coach: string | null, seats: string[]): string | null {
  const parts: string[] = [];
  if (coach) parts.push(`Coach ${coach}`);
  if (seats.length > 0) parts.push(`${seats.length === 1 ? "seat" : "seats"} ${seats.join(", ")}`);
  return parts.length > 0 ? parts.join(", ") : null;
}

/** Null when the text is not a Trenitalia ticket Abeona can read a ride from. */
export function parseTrenitaliaTicket(text: string): ParsedTicket | null {
  // Every journey title starts a passenger's ticket.
  const all = lines(text);
  const starts = all.map((l, i) => (TITLE.test(l) ? i : -1)).filter((i) => i >= 0);
  const blocks = starts
    .map((start, n) => readBlock(all.slice(start, starts[n + 1] ?? all.length).join("\n")))
    .filter((b): b is Block => b !== null);
  if (blocks.length === 0) return null;

  // The same ride on several passengers' tickets is one ride.
  const rides = new Map<string, { leg: ParsedLeg; coach: string | null; seats: string[]; passengers: number }>();
  for (const b of blocks) {
    const key = `${b.leg.line}|${b.leg.date}|${b.leg.departure}`;
    const ride = rides.get(key);
    if (ride) {
      ride.passengers++;
      for (const seat of b.seats) if (!ride.seats.includes(seat)) ride.seats.push(seat);
      ride.coach ??= b.coach;
    } else {
      rides.set(key, { leg: b.leg, coach: b.coach, seats: [...b.seats], passengers: 1 });
    }
  }
  const legs = [...rides.values()]
    .map((r) => ({ ...r.leg, reservation: reservationOf(r.coach, r.seats) }))
    .sort((a, b) => `${a.date} ${a.departure}`.localeCompare(`${b.date} ${b.departure}`));
  const travellers = Math.max(...[...rides.values()].map((r) => r.passengers));

  const fareName = blocks.find((b) => b.offer)?.offer ?? null;
  const fare = fareOf(fareName);
  const priced = blocks.filter((b) => b.cents !== null);
  const reservations = legs.filter((l) => l.reservation).map((l) => `${l.line}: ${l.reservation}`);

  return {
    kind: "ticket",
    operator: "trenitalia",
    fare_name: fareName,
    fare_type: fare.fare_type,
    direction: null,
    travel_class: blocks.find((b) => b.travelClass !== null)?.travelClass ?? null,
    travellers,
    // No BahnCards on Trenitalia: one passenger each, none with a discount.
    discounts: Array.from({ length: travellers }, () => null),
    train_binding: fare.binding,
    booked_lines: fare.binding ? legs.map((l) => l.line) : [],
    origin: legs[0].from,
    destination: legs[legs.length - 1].to,
    date: legs[0].date,
    legs,
    // What the PDF cost: every passenger's ticket in it.
    price_cents: priced.length > 0 ? priced.reduce((sum, b) => sum + b.cents!, 0) : null,
    reservation: reservations.length > 0 ? reservations.join("; ") : null,
  };
}
