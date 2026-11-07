/**
 * This browser's position on a live trip's page, while it is open and the
 * traveller asked for it (storage.ts: getTripLocation). The web's only
 * device signal: no train Wi-Fi (the portals are out of a page's reach) and
 * nothing in the background. Never stored, never sent.
 */
import type { Position } from "./travelday";

/** Off, being asked for, followed, refused by the browser, or not possible here. */
export type LocationState = "off" | "asking" | "on" | "denied" | "unsupported";

/** Older fixes are ignored, as on Android (LiveSignals.FRESH). */
export const FRESH_MS = 3 * 60_000;

export interface Fix {
  position: Position;
  at: number;
}

/**
 * Follows the position until the returned function is called. Speed is the
 * fix's own, else worked out from the previous fix.
 */
export function watchPosition(onFix: (fix: Fix) => void, onState: (state: LocationState) => void): () => void {
  if (typeof navigator === "undefined" || !("geolocation" in navigator)) {
    onState("unsupported");
    return () => {};
  }
  onState("asking");
  let last: { lat: number; lon: number; at: number } | null = null;
  const id = navigator.geolocation.watchPosition(
    (p) => {
      const at = p.timestamp || Date.now();
      const { latitude: lat, longitude: lon, speed } = p.coords;
      let speedKmh: number | null = speed !== null && !Number.isNaN(speed) ? speed * 3.6 : null;
      if (speedKmh === null && last && at > last.at) {
        const dLat = ((lat - last.lat) * Math.PI) / 180;
        const dLon = ((lon - last.lon) * Math.PI) / 180;
        const h = Math.sin(dLat / 2) ** 2 + Math.cos((last.lat * Math.PI) / 180) * Math.cos((lat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
        const metres = 2 * 6_371_000 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        speedKmh = (metres / ((at - last.at) / 1000)) * 3.6;
      }
      last = { lat, lon, at };
      onState("on");
      onFix({ position: { lat, lon, speedKmh }, at });
    },
    (error) => onState(error.code === error.PERMISSION_DENIED ? "denied" : "asking"),
    { enableHighAccuracy: true, maximumAge: 15_000, timeout: 60_000 },
  );
  return () => navigator.geolocation.clearWatch(id);
}
