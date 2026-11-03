// A short-lived cache of Transitous answers in public.abeona_transit_cache,
// written and read with the service role. A station search is kept for a
// day, a journey search for two minutes, a refresh for one: long enough that
// a household watching one trip, a status page reloaded at the station or a
// calendar refetching costs Transitous one request, short enough that a
// delay is never more than a minute stale.
//
// The cache is best effort: a failing read is a miss, a failing write is
// ignored. Without the service role key there is no cache at all.

/** Seconds a provider path's answer stays fresh. */
export const TTL_SECONDS: Record<string, number> = {
  "/api/v1/geocode": 24 * 3600,
  "/api/v6/plan": 120,
  "/api/v6/refresh-itinerary": 60,
  "/api/v6/trip": 60,
};

export interface TransitCache {
  get(key: string, maxAgeSeconds: number): Promise<unknown | undefined>;
  put(key: string, payload: unknown): Promise<void>;
}

/** sha-256 of the path and the sorted parameters, hex. */
export async function cacheKey(path: string, params: Record<string, string | undefined>): Promise<string> {
  const canonical = path + "?" + Object.entries(params)
    .filter(([, v]) => v !== undefined)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v as string)}`)
    .join("&");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(canonical));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** The supabase-js calls the cache makes; a service-role client. */
export interface CacheDatabase {
  // deno-lint-ignore no-explicit-any
  from(table: string): any;
}

export function databaseCache(db: CacheDatabase, now: () => number = Date.now): TransitCache {
  return {
    async get(key, maxAgeSeconds) {
      try {
        const since = new Date(now() - maxAgeSeconds * 1000).toISOString();
        const { data, error } = await db
          .from("abeona_transit_cache")
          .select("payload")
          .eq("key", key)
          .gte("fetched_at", since)
          .maybeSingle();
        if (error || !data) return undefined;
        return data.payload;
      } catch {
        return undefined;
      }
    },
    async put(key, payload) {
      try {
        await db
          .from("abeona_transit_cache")
          .upsert({ key, payload, fetched_at: new Date(now()).toISOString() }, { onConflict: "key" });
      } catch {
        // A cache that cannot be written is no cache; the answer still goes out.
      }
    },
  };
}

/** A cache in memory, for tests. */
export function memoryCache(now: () => number = Date.now): TransitCache & { entries: Map<string, { at: number; payload: unknown }> } {
  const entries = new Map<string, { at: number; payload: unknown }>();
  return {
    entries,
    get(key, maxAgeSeconds) {
      const hit = entries.get(key);
      return Promise.resolve(hit && now() - hit.at <= maxAgeSeconds * 1000 ? hit.payload : undefined);
    },
    put(key, payload) {
      entries.set(key, { at: now(), payload });
      return Promise.resolve();
    },
  };
}
