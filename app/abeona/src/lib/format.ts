/**
 * Display formatting. Times are shown in the browser's time zone — every
 * country Abeona covers is on Central European Time, so that is the stop's
 * local time in practice.
 */
import { delayMinutes } from "./transfer";
import type { Leg } from "./transit";

const LOCALE = "en-GB";

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "–";
  return new Date(iso).toLocaleTimeString(LOCALE, { hour: "2-digit", minute: "2-digit" });
}

/** "Mon 3 Nov" */
export function formatShortDate(iso: string | null | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString(LOCALE, { weekday: "short", day: "numeric", month: "short" });
}

/** "Monday, 3 November 2026" */
export function formatLongDate(iso: string | null | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString(LOCALE, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** "Today", "Tomorrow", "In 5 days", or the short date. */
export function formatRelativeDay(iso: string | null | undefined, now = new Date()): string {
  if (!iso) return "";
  const days = Math.round((startOfDay(new Date(iso)) - startOfDay(now)) / 86_400_000);
  if (days === 0) return "Today";
  if (days === 1) return "Tomorrow";
  if (days === -1) return "Yesterday";
  if (days > 1 && days < 7) return `In ${days} days`;
  return formatShortDate(iso);
}

/** "2 h 05 min", "45 min" */
export function formatDuration(minutes: number): string {
  const m = Math.max(0, Math.round(minutes));
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rest = m % 60;
  return rest === 0 ? `${h} h` : `${h} h ${String(rest).padStart(2, "0")} min`;
}

export function minutesBetween(a: string | null | undefined, b: string | null | undefined): number {
  if (!a || !b) return 0;
  return Math.round((Date.parse(b) - Date.parse(a)) / 60_000);
}

/** "+7" for a delay, "−2" for running early, "" when on time. */
export function formatDelay(minutes: number): string {
  if (minutes > 0) return `+${minutes}`;
  if (minutes < 0) return `−${Math.abs(minutes)}`;
  return "";
}

export function stopDelay(stop: { planned: string | null; current: string | null }): number {
  return delayMinutes(stop.planned, stop.current);
}

/** Whether the current platform differs from the planned one. */
export function platformChanged(stop: { planned_platform: string | null; platform: string | null }): boolean {
  return (
    !!stop.platform &&
    !!stop.planned_platform &&
    stop.platform.trim().toUpperCase() !== stop.planned_platform.trim().toUpperCase()
  );
}

export function displayPlatform(stop: { planned_platform: string | null; platform: string | null }): string | null {
  return stop.platform ?? stop.planned_platform;
}

/** What a chip should say for a leg: the line name, else the product. */
export function legLabel(leg: Pick<Leg, "mode" | "line_name" | "product">): string {
  if (leg.line_name) return leg.line_name;
  if (leg.product) return leg.product;
  if (leg.mode === "bus") return "Bus";
  if (leg.mode === "walk") return "Walk";
  return "Train";
}

const LONG_DISTANCE = ["ICE", "IC", "EC", "ECE", "RJ", "RJX", "TGV", "EST", "NJ", "EN", "FLX", "WB", "IR", "FR", "FA", "EC/IC"];

/** Visual family of a product: long-distance trains, regional, urban, bus. */
export function productFamily(leg: Pick<Leg, "mode" | "product">): "long" | "regional" | "urban" | "bus" | "walk" {
  if (leg.mode === "walk") return "walk";
  if (leg.mode === "bus") return "bus";
  const p = (leg.product ?? "").toUpperCase();
  if (LONG_DISTANCE.includes(p)) return "long";
  if (p === "S" || p === "U" || p === "TRAM" || p === "STR") return "urban";
  return "regional";
}

export const FARE_TYPES = [
  { value: "flex", label: "Flex fare" },
  { value: "saver", label: "Saver fare" },
  { value: "regional", label: "Regional ticket" },
  { value: "deutschlandticket", label: "Deutschlandticket" },
  { value: "bahncard100", label: "BahnCard 100" },
  { value: "other", label: "Other" },
] as const;

export type FareType = (typeof FARE_TYPES)[number]["value"];

export function fareLabel(value: string | null | undefined): string {
  return FARE_TYPES.find((f) => f.value === value)?.label ?? "Other";
}

export function formatPrice(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return "";
  return (cents / 100).toLocaleString(LOCALE, { style: "currency", currency: "EUR" });
}

export function formatNumber(value: number, digits = 0): string {
  return value.toLocaleString(LOCALE, { maximumFractionDigits: digits, minimumFractionDigits: digits });
}

/** A yyyy-mm-dd date (no time, no zone) as a local Date at midnight. */
export function parseDay(day: string): Date {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(y!, (m ?? 1) - 1, d ?? 1);
}

/** yyyy-mm-dd → "12 Mar 2027" */
export function formatDay(day: string | null | undefined): string {
  if (!day) return "";
  return parseDay(day).toLocaleDateString(LOCALE, { day: "numeric", month: "short", year: "numeric" });
}

/** A local Date → yyyy-mm-dd, for date inputs. */
export function toDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** "BahnCard 50 · 2nd class" */
export function bahnCardLabel(card: { kind: number; travel_class: number }): string {
  return `BahnCard ${card.kind} · ${card.travel_class === 1 ? "1st" : "2nd"} class`;
}

/** When a subscription has to be cancelled by: "Cancel by 14 Feb 2027 (in 9 days)". */
export function deadlineText(deadline: { deadline: string; days_left: number }): string {
  const days = deadline.days_left;
  const when =
    days < 0 ? `${-days} ${-days === 1 ? "day" : "days"} ago` : days === 0 ? "today" : days === 1 ? "tomorrow" : `in ${days} days`;
  return days < 0
    ? `The cancellation deadline was ${formatDay(deadline.deadline)} (${when})`
    : `Cancel by ${formatDay(deadline.deadline)} (${when})`;
}

export const BAHNCARD_DISCOUNTS = [
  { value: null, label: "None" },
  { value: 25, label: "BahnCard 25" },
  { value: 50, label: "BahnCard 50" },
] as const;
