/**
 * A DB Online-Ticket's text layer → fare, class, travellers, BahnCard,
 * binding, rides and price. DB's PDFs are text PDFs; the text comes from
 * pdf.js on the web (pdfText.ts) and PdfBox on Android, never from a server.
 * Mirrored in Kotlin (domain/intake/DbTicket.kt); both run the cases in
 * intake.fixtures.json.
 *
 * Read line by line and tolerant of spacing: `pdftotext -layout` keeps the
 * columns apart with runs of spaces, pdf.js and PdfBox may leave just one.
 * One operator only; ÖBB, SBB, Trenitalia and SNCF would each be a parser
 * of their own.
 */
import { cents, hhmm, isoDate, lines, nextDay, platformOf, squash } from "./common.ts";
import { fareOf } from "./fare.ts";
import type { ParsedLeg, ParsedTicket } from "./parsed.ts";

const CLASS = /\b([12])\.\s*Klasse\b/;
const TRAVELLERS = /^Reisende\b(.*)$/i;
const PERSONS = /(\d+)\s+(?:Person|Personen|Erwachsene|Erwachsener|Reisende)\b/i;
const BAHNCARD = /(?:(\d+)\s*x?\s*)?(?:BC|BahnCard)\s?(25|50|100)\b/gi;
const BINDING_START = /^Zugbindung\b/i;
const BOUND_TRAIN = /([A-Z]{1,5}\s?\d{1,6}),\s*(\d{1,2}:\d{2})\s*Uhr(?:\s+am\s+(\d{1,2})\.(\d{1,2})\.(\d{4}))?/g;
const PRICE = /(?:Gesamtpreis|Summe|Endpreis)\s*:?\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2})\s*(?:€|EUR)/i;
const TABLE_HEAD = /^Halt\s+Datum\s+Zeit\b/i;
const TABLE_TITLE = /Reiseverbindung.*?(Hinfahrt|Rückfahrt)?\s+am\s+(\d{1,2})\.(\d{1,2})\.(\d{4})/i;
const ROW = /^(.+?)\s+(\d{1,2})\.(\d{1,2})\.(\d{2,4})?\s+(ab|an)\s+(\d{1,2}:\d{2})\s*(.*)$/;
const ROW_REST = /^(?:(\d{1,3}[a-z]?)(?=\s|$)\s*)?(?:([A-Z][A-Za-z]{0,4}\s?\d{1,6})(?=\s|$)\s*)?(.*)$/;
const DIRECTION = /\((Hinfahrt|Rückfahrt)\)/i;
const ANY_DATE = /(\d{1,2})\.(\d{1,2})\.(\d{4})/;

/** Whether the text looks like a DB ticket: a "Klasse" and a fare or a stop table. */
export function isDbTicket(text: string): boolean {
  const all = lines(text);
  return all.some((l) => CLASS.test(l)) && (all.some((l) => TABLE_HEAD.test(l.trim())) || /Online-Ticket|Fahrkarte/i.test(text));
}

interface Row {
  station: string;
  date: string;
  kind: "ab" | "an";
  time: string;
  platform: string | null;
  product: string | null;
  note: string | null;
}

/** "16.11." in a table that belongs to 2026: the year from the context, moving on across New Year. */
function rowDate(day: string, month: string, year: string | undefined, context: string | null): string | null {
  if (year) return isoDate(day, month, year);
  if (!context) return null;
  const y = Number(context.slice(0, 4));
  const contextMonth = Number(context.slice(5, 7));
  return isoDate(day, month, String(Number(month) < contextMonth ? y + 1 : y));
}

/** Null when the text is not a ticket Abeona can read anything from. */
export function parseDbTicket(text: string): ParsedTicket | null {
  const all = lines(text).map((l) => l.trim());

  // The fare is the title right above "Klasse …".
  let fareLine: string | null = null;
  const classIdx = all.findIndex((l) => /^Klasse\b/i.test(l) && CLASS.test(l));
  if (classIdx > 0) {
    for (let i = classIdx - 1; i >= 0; i--) {
      if (all[i] !== "") {
        fareLine = all[i];
        break;
      }
    }
  }
  if (!fareLine) fareLine = all.find((l) => l.length < 60 && fareOf(l).fare_type !== "other") ?? null;
  const direction = fareLine ? DIRECTION.exec(fareLine)?.[1] ?? null : null;
  const fareName = fareLine ? squash(fareLine.replace(DIRECTION, "")) : null;
  const fare = fareOf(fareName);

  const classMatch = (classIdx >= 0 ? CLASS.exec(all[classIdx]) : null) ?? all.map((l) => CLASS.exec(l)).find(Boolean) ?? null;
  const travelClass = classMatch ? (Number(classMatch[1]) as 1 | 2) : null;

  let travellers: number | null = null;
  const discounts: (25 | 50 | null)[] = [];
  const travellersLine = all.map((l) => TRAVELLERS.exec(l)).find(Boolean);
  if (travellersLine) {
    const rest = travellersLine[1];
    const persons = PERSONS.exec(rest);
    travellers = persons ? Number(persons[1]) : null;
    // "mit 1 BC25, 1 BC50": every group, in print order. A BahnCard 100 is a
    // fare of its own, not a discount.
    for (const card of rest.matchAll(BAHNCARD)) {
      if (card[2] === "100") continue;
      const count = card[1] ? Number(card[1]) : 1;
      for (let i = 0; i < count; i++) discounts.push(Number(card[2]) as 25 | 50);
    }
    while (travellers !== null && discounts.length < travellers) discounts.push(null);
  }

  // "Zugbindung  ICE 17, 15:14 Uhr am 16.11.2025" and the lines below it.
  const booked: string[] = [];
  let bindingDate: string | null = null;
  const bindingIdx = all.findIndex((l) => BINDING_START.test(l));
  if (bindingIdx >= 0) {
    for (let i = bindingIdx; i < all.length; i++) {
      const found = [...all[i].matchAll(BOUND_TRAIN)];
      if (found.length === 0) break;
      for (const m of found) {
        booked.push(squash(m[1]));
        if (!bindingDate && m[3]) bindingDate = isoDate(m[3], m[4], m[5]);
      }
    }
  }

  let price: number | null = null;
  for (const l of all) {
    const m = PRICE.exec(l);
    if (m) {
      price = cents(m[1]);
      break;
    }
  }

  // The stop table, and the date its title gives.
  let tableDate: string | null = null;
  for (const l of all) {
    const m = TABLE_TITLE.exec(l);
    if (m) {
      tableDate = isoDate(m[2], m[3], m[4]);
      break;
    }
  }
  const context = tableDate ?? bindingDate ?? (() => {
    for (const l of all) {
      const m = ANY_DATE.exec(l);
      if (m) return isoDate(m[1], m[2], m[3]);
    }
    return null;
  })();

  const rows: Row[] = [];
  const headIdx = all.findIndex((l) => TABLE_HEAD.test(l));
  if (headIdx >= 0) {
    for (let i = headIdx + 1; i < all.length; i++) {
      const m = ROW.exec(all[i]);
      if (!m) {
        if (rows.length > 0) break;
        continue;
      }
      const time = hhmm(m[6]);
      const date = rowDate(m[2], m[3], m[4], context);
      if (!time || !date) continue;
      const rest = ROW_REST.exec(m[7].trim());
      rows.push({
        station: squash(m[1]),
        date,
        kind: m[5] as "ab" | "an",
        time,
        platform: rest?.[1] ? platformOf(rest[1]) : null,
        product: rest?.[2] ? squash(rest[2]) : null,
        note: rest?.[3] ? squash(rest[3]) || null : null,
      });
    }
  }

  const legs: ParsedLeg[] = [];
  let open: Row | null = null;
  for (const row of rows) {
    if (row.kind === "ab") {
      open = row;
      continue;
    }
    if (!open) continue;
    const line = open.product ?? booked[legs.length] ?? "";
    // A ride that runs past midnight without a date of its own on the arrival row.
    const arrivalDate = row.date < open.date || (row.date === open.date && row.time < open.time) ? nextDay(open.date) : row.date;
    legs.push({
      line,
      product: /^([A-Z]{1,5})/.exec(line)?.[1] ?? null,
      number: null,
      headsign: null,
      from: open.station,
      to: row.station,
      date: open.date,
      departure: open.time,
      arrival_date: arrivalDate,
      arrival: row.time,
      from_platform: open.platform,
      to_platform: row.platform,
      reservation: open.note,
    });
    open = null;
  }

  if (!fareName && legs.length === 0) return null;
  const reservations = legs.filter((l) => l.reservation).map((l) => `${l.line}: ${l.reservation}`);

  return {
    kind: "ticket",
    operator: "db",
    fare_name: fareName,
    fare_type: fare.fare_type,
    direction: direction ? (direction.toLowerCase() === "hinfahrt" ? "outward" : "return") : null,
    travel_class: travelClass,
    travellers,
    discounts,
    train_binding: booked.length > 0 || fare.binding,
    booked_lines: booked,
    origin: legs[0]?.from ?? null,
    destination: legs[legs.length - 1]?.to ?? null,
    date: legs[0]?.date ?? tableDate ?? bindingDate,
    legs,
    price_cents: price,
    // One half of a return booking prints the booking's total: the user says what this trip cost.
    price_scope: direction ? "booking" : "trip",
    reservation: reservations.length > 0 ? reservations.join("; ") : null,
  };
}
