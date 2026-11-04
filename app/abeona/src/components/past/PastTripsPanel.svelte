<script lang="ts">
  /**
   * Every trip that has arrived, newest first, by month, each opening its trip
   * page (the ticket, compensation, what happened). Reached from the
   * dashboard's recent trips. Android's PastTripsScreen is the same.
   */
  import { ArrowLeft } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import { describeError, fetchPastTrips, type Trip } from "../../lib/data";
  import TripListItem from "../home/TripListItem.svelte";

  const PAGE = 30;
  const base = getBaseUrl();

  let trips = $state<Trip[]>([]);
  let loading = $state(true);
  let more = $state(true);
  let error = $state("");

  const months = $derived.by(() => {
    const groups: { label: string; trips: Trip[] }[] = [];
    for (const trip of trips) {
      const label = new Date(trip.planned_departure).toLocaleDateString("en-GB", { month: "long", year: "numeric" });
      const last = groups[groups.length - 1];
      if (last?.label === label) last.trips.push(trip);
      else groups.push({ label, trips: [trip] });
    }
    return groups;
  });

  async function loadMore() {
    loading = true;
    error = "";
    try {
      const page = await fetchPastTrips(PAGE, trips[trips.length - 1]?.planned_departure);
      trips = [...trips, ...page];
      more = page.length === PAGE;
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  onMount(() => void loadMore());
</script>

<section class="mx-auto max-w-3xl space-y-6">
  <div class="flex items-center gap-3">
    <a href={`${base}/`} class="btn btn-circle btn-ghost btn-sm" aria-label="Back to trips"><ArrowLeft class="h-5 w-5" /></a>
    <h1 class="text-2xl font-semibold text-ink">Past trips</h1>
  </div>

  {#if trips.length === 0 && loading}
    <div class="space-y-2.5" aria-busy="true">
      {#each [0, 1, 2] as i (i)}<div class="h-20 animate-pulse rounded-3xl bg-base-200"></div>{/each}
    </div>
  {:else if trips.length === 0 && !error}
    <p class="rounded-3xl bg-card p-6 text-sm text-base-content/60 shadow-card">
      No past trips yet. Once a saved trip has arrived, it moves here, with its ticket and what it is owed.
    </p>
  {/if}

  {#each months as month (month.label)}
    <div>
      <p class="px-2 pb-3 text-xs font-semibold uppercase tracking-wider text-base-content/55">
        {month.label} · {month.trips.length} {month.trips.length === 1 ? "trip" : "trips"}
      </p>
      <div class="space-y-2.5">
        {#each month.trips as trip (trip.id)}
          <TripListItem {trip} href={`${base}/trip?id=${encodeURIComponent(trip.id)}`} muted />
        {/each}
      </div>
    </div>
  {/each}

  {#if error}<p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">{error}</p>{/if}

  {#if trips.length > 0 && more}
    <button type="button" class="btn w-full rounded-full" disabled={loading} onclick={loadMore}>
      {#if loading}<span class="loading loading-spinner loading-sm"></span>{/if}
      Older trips
    </button>
  {/if}
</section>
