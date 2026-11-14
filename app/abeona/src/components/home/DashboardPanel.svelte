<script lang="ts">
  import { Plus } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import {
    describeError,
    fetchBahnCardDeadlines,
    fetchPastTrips,
    fetchTrip,
    fetchUpcomingTrips,
    fetchYearStatistics,
    checkTimetable,
    refreshTripLive,
    type BahnCardDeadline,
    type Statistics,
    type Trip,
  } from "../../lib/data";
  import { hasArrived, isLiveWindow, LIVE_REFRESH_MS } from "../../lib/journey";
  import { getStationMinutes } from "../../lib/storage";
  import { travelDay } from "../../lib/travelday";
  import Illustration from "../common/Illustration.svelte";
  import DeadlineBanner from "./DeadlineBanner.svelte";
  import HistoryCard from "./HistoryCard.svelte";
  import TripHeroCard from "./TripHeroCard.svelte";
  import { tripFollowedLegs } from "../../lib/followed";
  import TripListItem from "./TripListItem.svelte";

  const base = getBaseUrl();
  const year = new Date().getFullYear();

  let loading = $state(true);
  let error = $state("");
  let upcoming = $state<Trip[]>([]);
  let recent = $state<Trip[]>([]);
  let stats = $state<Statistics | null>(null);
  let deadlines = $state<BahnCardDeadline[]>([]);
  /** Trips whose live values are being fetched right now. */
  let refreshingIds = $state<ReadonlySet<string>>(new Set());

  const next = $derived(upcoming[0] ?? null);
  /** Ticks every half minute, so the travel day's countdowns move. */
  let now = $state(Date.now());
  let stationMinutes = $state(15);
  // As travelled: a connection taken from the alternatives replaces the plan's rides.
  const nextLegs = $derived(next ? tripFollowedLegs(next) : []);
  const day = $derived(next && isLiveWindow(nextLegs, now) ? travelDay(nextLegs, now, stationMinutes) : null);
  const later = $derived(upcoming.slice(1));

  const tripHref = (id: string) => `${base}/trip?id=${encodeURIComponent(id)}`;

  function greeting(): string {
    const h = new Date().getHours();
    if (h < 5) return "Good night";
    if (h < 12) return "Good morning";
    if (h < 18) return "Good afternoon";
    return "Good evening";
  }

  async function load() {
    loading = true;
    error = "";
    try {
      const [trips, past, year_stats, due] = await Promise.all([
        fetchUpcomingTrips(),
        fetchPastTrips(3),
        fetchYearStatistics(year),
        // A reminder, not the page: a failure here must not hide the trips.
        fetchBahnCardDeadlines().catch(() => [] as BahnCardDeadline[]),
      ]);
      upcoming = trips.filter((t) => !hasArrived(t.legs));
      recent = past;
      stats = year_stats;
      deadlines = due;
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  let refreshRunning = false;

  // Every trip in its live window gets fresh live values, one after another:
  // a handful of Edge Function calls every minute, never a burst. The
  // rest keep what was last saved until they are opened.
  async function refreshLive() {
    if (refreshRunning) return;
    refreshRunning = true;
    try {
      for (const trip of upcoming.filter((t) => isLiveWindow(t.legs))) {
        refreshingIds = new Set([...refreshingIds, trip.id]);
        try {
          if (await refreshTripLive(trip)) {
            const fresh = await fetchTrip(trip.id);
            if (fresh) upcoming = upcoming.map((t) => (t.id === fresh.id ? fresh : t));
          }
        } catch {
          // Live values are a bonus here; the trip page shows refresh errors.
        } finally {
          refreshingIds = new Set([...refreshingIds].filter((id) => id !== trip.id));
        }
      }
    } finally {
      refreshRunning = false;
    }
  }

  // Every upcoming trip checked against the timetable (checkTimetable: every
  // 15 minutes to 3 hours each): a timetable change shows on the list
  // without opening the trip.
  async function checkAll() {
    for (const trip of upcoming) {
      try {
        if (await checkTimetable(trip)) {
          const fresh = await fetchTrip(trip.id);
          if (fresh) upcoming = upcoming.map((t) => (t.id === fresh.id ? fresh : t));
        }
      } catch {
        // Quietly: the trip page says more.
      }
    }
  }

  onMount(() => {
    stationMinutes = getStationMinutes();
    const tick = setInterval(() => (now = Date.now()), 15_000);
    void load().then(refreshLive).then(checkAll);
    // Like the trip page: only while someone is looking.
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") void refreshLive();
    }, LIVE_REFRESH_MS);
    return () => {
      clearInterval(timer);
      clearInterval(tick);
    };
  });
</script>

<div class="space-y-10">
  <header class="flex items-end justify-between gap-4 pt-2">
    <div>
      <p class="text-sm font-medium text-base-content/55">
        {new Date().toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" })}
      </p>
      <h1 class="mt-1 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">{greeting()}</h1>
    </div>
    <a href={`${base}/search`} class="btn btn-primary rounded-full px-5">
      <Plus class="h-4 w-4" /> <span class="hidden sm:inline">Plan a trip</span><span class="sm:hidden">Plan</span>
    </a>
  </header>

  {#if error}
    <div class="flex items-center justify-between gap-4 rounded-2xl bg-error/10 px-5 py-4 text-sm text-error" role="alert">
      <span>{error}</span>
      <button class="btn btn-sm rounded-full" onclick={() => load().then(refreshLive)}>Try again</button>
    </div>
  {/if}

  {#if loading}
    <div class="space-y-4" aria-busy="true">
      <div class="h-80 animate-pulse rounded-[2rem] bg-mint"></div>
      <div class="h-24 animate-pulse rounded-3xl bg-base-200"></div>
      <div class="h-24 animate-pulse rounded-3xl bg-base-200"></div>
    </div>
  {:else if !error}
    {#if deadlines.length > 0}
      <DeadlineBanner {deadlines} settingsHref={`${base}/settings#bahncards`} />
    {/if}

    {#if next}
      <TripHeroCard trip={{ ...next, legs: nextLegs }} href={tripHref(next.id)} refreshing={refreshingIds.has(next.id)} {day} {stationMinutes} {now} />
    {:else}
      <section class="grid items-center gap-8 rounded-[2rem] bg-card p-6 shadow-card sm:p-10 md:grid-cols-2">
        <Illustration name="empty-trips" class="w-full" />
        <div>
          <h2 class="text-2xl font-semibold tracking-tight text-ink">No trips ahead</h2>
          <p class="mt-2 text-base-content/70">
            Search a connection and keep it here — with live delays, platforms, your changes and your ticket, all in one calm place.
          </p>
          <a href={`${base}/search`} class="btn btn-primary mt-6 rounded-full px-6">
            <Plus class="h-4 w-4" /> Plan a trip
          </a>
        </div>
      </section>
    {/if}

    {#if later.length > 0}
      <section>
        <h2 class="mb-4 px-1 text-xl font-semibold text-ink">Upcoming</h2>
        <div class="space-y-3">
          {#each later as trip (trip.id)}
            <TripListItem {trip} href={tripHref(trip.id)} />
          {/each}
        </div>
      </section>
    {/if}

    <HistoryCard {year} {stats} {recent} reviewHref={`${base}/review`} pastHref={`${base}/past`} {tripHref} />
  {/if}
</div>
