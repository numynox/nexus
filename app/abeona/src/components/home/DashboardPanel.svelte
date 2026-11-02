<script lang="ts">
  import { Plus } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import {
    describeError,
    fetchPastTrips,
    fetchTrip,
    fetchUpcomingTrips,
    fetchYearStatistics,
    refreshTripLive,
    type Statistics,
    type Trip,
  } from "../../lib/data";
  import { hasArrived, isLiveWindow } from "../../lib/journey";
  import Illustration from "../common/Illustration.svelte";
  import HistoryCard from "./HistoryCard.svelte";
  import TripHeroCard from "./TripHeroCard.svelte";
  import TripListItem from "./TripListItem.svelte";

  const base = getBaseUrl();
  const year = new Date().getFullYear();

  let loading = $state(true);
  let error = $state("");
  let upcoming = $state<Trip[]>([]);
  let recent = $state<Trip[]>([]);
  let stats = $state<Statistics | null>(null);
  let refreshing = $state(false);

  const next = $derived(upcoming[0] ?? null);
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
      const [trips, past, year_stats] = await Promise.all([
        fetchUpcomingTrips(),
        fetchPastTrips(3),
        fetchYearStatistics(year),
      ]);
      upcoming = trips.filter((t) => !hasArrived(t.legs));
      recent = past;
      stats = year_stats;
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
    void refreshNext();
  }

  // The hero trip gets fresh live values when it is today's; the rest keep
  // what was last saved until they are opened.
  async function refreshNext() {
    const trip = upcoming[0];
    if (!trip || !isLiveWindow(trip.legs)) return;
    refreshing = true;
    try {
      if (await refreshTripLive(trip)) {
        const fresh = await fetchTrip(trip.id);
        if (fresh) upcoming = [fresh, ...upcoming.slice(1)];
      }
    } catch {
      // Live values are a bonus here; the trip page shows refresh errors.
    } finally {
      refreshing = false;
    }
  }

  onMount(() => {
    void load();
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
      <button class="btn btn-sm rounded-full" onclick={load}>Try again</button>
    </div>
  {/if}

  {#if loading}
    <div class="space-y-4" aria-busy="true">
      <div class="h-80 animate-pulse rounded-[2rem] bg-mint"></div>
      <div class="h-24 animate-pulse rounded-3xl bg-base-200"></div>
      <div class="h-24 animate-pulse rounded-3xl bg-base-200"></div>
    </div>
  {:else if !error}
    {#if next}
      <TripHeroCard trip={next} href={tripHref(next.id)} {refreshing} />
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

    <HistoryCard {year} {stats} {recent} reviewHref={`${base}/review`} {tripHref} />
  {/if}
</div>
