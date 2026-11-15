<script lang="ts">
  import { CircleAlert, FileUp, Plus } from "lucide-svelte";
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
  import Banner from "../common/Banner.svelte";
  import EmptyState from "../common/EmptyState.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";
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

<div class="space-y-6">
  <!-- The date, and the way to a new trip; with no trip ahead the empty state has the button. -->
  <header class="flex min-h-10 items-center justify-between gap-4 px-1">
    <h1 class="sr-only">Trips</h1>
    <p class="t-meta">{new Date().toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" })}</p>
    {#if loading || next}
      <a href={`${base}/search`} class="btn btn-primary btn-sm rounded-full"><Plus class="h-4 w-4" aria-hidden="true" /> Plan</a>
    {/if}
  </header>

  {#if error}
    <Banner tone="bad" icon={CircleAlert} title={error}>
      {#snippet action()}
        <button class="btn btn-sm rounded-full" onclick={() => load().then(refreshLive)}>Try again</button>
      {/snippet}
    </Banner>
  {/if}

  {#if loading}
    <div class="space-y-3" aria-busy="true">
      <div class="h-64 animate-pulse rounded-3xl bg-brand-soft"></div>
      <div class="h-14 animate-pulse rounded-3xl bg-well"></div>
      <div class="h-14 animate-pulse rounded-3xl bg-well"></div>
    </div>
  {:else if !error}
    {#if deadlines.length > 0}
      <DeadlineBanner {deadlines} settingsHref={`${base}/settings#bahncards`} />
    {/if}

    {#if next}
      <TripHeroCard trip={{ ...next, legs: nextLegs }} href={tripHref(next.id)} refreshing={refreshingIds.has(next.id)} {day} {stationMinutes} {now} />
    {:else}
      <EmptyState title="No trips ahead">
        <a href={`${base}/search`} class="btn btn-primary rounded-full px-6"><Plus class="h-4 w-4" aria-hidden="true" /> Plan a trip</a>
        <a href={`${base}/search`} class="btn btn-ghost rounded-full text-brand"><FileUp class="h-4 w-4" aria-hidden="true" /> Add from a ticket</a>
      </EmptyState>
    {/if}

    {#if later.length > 0}
      <section class="space-y-2">
        <SectionHeader title="Upcoming" />
        <div class="divide-y divide-line overflow-hidden rounded-3xl bg-card shadow-card">
          {#each later as trip (trip.id)}
            <TripListItem {trip} href={tripHref(trip.id)} />
          {/each}
        </div>
      </section>
    {/if}

    <HistoryCard {year} {stats} {recent} reviewHref={`${base}/review`} pastHref={`${base}/past`} />
  {/if}
</div>
