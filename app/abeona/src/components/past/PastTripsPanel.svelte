<script lang="ts">
  /**
   * Every trip that has arrived, newest first, by month, each opening its trip
   * page (the ticket, compensation, what happened). Reached from the
   * dashboard's recent trips. Android's PastTripsScreen is the same.
   */
  import { ArrowLeft, Ban, CircleAlert, CircleCheck, Shuffle, Timer } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import { describeError, fetchPastTrips, type Trip } from "../../lib/data";
  import { tripFollowedLegs } from "../../lib/followed";
  import { summarise } from "../../lib/journey";
  import Banner from "../common/Banner.svelte";
  import EmptyState from "../common/EmptyState.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";
  import StatusChip from "../common/StatusChip.svelte";

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

  /** How a trip ended, as travelled: a connection followed instead counts. */
  function outcome(trip: Trip) {
    const s = summarise(tripFollowedLegs(trip));
    const dropped = trip.legs.some((l) => !!l.dropped_at);
    return {
      delay: s.arrivalDelay,
      cancelled: s.cancelled,
      // A ride no longer in the timetable: adjusted when a connection was taken instead.
      changed: dropped ? (trip.missed_leg_idx !== null ? "adjusted" : "timetable") : null,
    };
  }

  function day(trip: Trip): string {
    return new Date(trip.planned_departure).toLocaleDateString("en-GB", { weekday: "short", day: "numeric" });
  }

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

<section class="mx-auto max-w-3xl space-y-3">
  <div class="flex items-center gap-2 pt-2">
    <a href={`${base}/`} class="btn btn-circle btn-ghost btn-sm" aria-label="Back to trips"><ArrowLeft class="h-6 w-6" aria-hidden="true" /></a>
    <h1 class="t-headline text-ink">Past trips</h1>
  </div>

  {#if trips.length === 0 && loading}
    <div class="h-40 animate-pulse rounded-3xl bg-well" aria-busy="true"></div>
  {:else if trips.length === 0 && !error}
    <div class="overflow-hidden rounded-3xl bg-card pb-6 shadow-card">
      <EmptyState title="No past trips yet" />
    </div>
  {/if}

  {#each months as month (month.label)}
    <div class="space-y-2">
      <SectionHeader title={month.label}>
        <span class="t-meta">{month.trips.length} {month.trips.length === 1 ? "trip" : "trips"}</span>
      </SectionHeader>
      <ul class="divide-y divide-line rounded-3xl bg-card px-4 shadow-card">
        {#each month.trips as trip (trip.id)}
          {@const o = outcome(trip)}
          <li>
            <a
              href={`${base}/trip?id=${encodeURIComponent(trip.id)}`}
              class="grid min-h-12 grid-cols-[3.25rem_minmax(0,1fr)_auto] items-center gap-x-2"
            >
              <span class="t-meta">{day(trip)}</span>
              <span class="t-body truncate text-ink">
                {trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}
                {#if trip.title}<span class="t-meta">{trip.origin_name} → {trip.destination_name}</span>{/if}
              </span>
              <span class="flex items-center gap-1">
                {#if o.changed === "adjusted"}
                  <StatusChip tone="quiet" icon={Shuffle} title="Adjusted: another connection was taken" />
                {:else if o.changed}
                  <StatusChip tone="bad" icon={CircleAlert} title="Timetable changed" />
                {/if}
                {#if o.cancelled}
                  <StatusChip tone="bad" icon={Ban} label="Cancelled" />
                {:else if o.delay > 0}
                  <StatusChip tone="watch" icon={Timer} label={`+${o.delay}`} title={`Arrived ${o.delay} min late`} />
                {:else}
                  <StatusChip tone="ok" icon={CircleCheck} title="Arrived on time" />
                {/if}
              </span>
            </a>
          </li>
        {/each}
      </ul>
    </div>
  {/each}

  {#if error}<Banner tone="bad" icon={CircleAlert} title={error} />{/if}

  {#if trips.length > 0 && more}
    <button type="button" class="btn btn-ghost h-12 w-full rounded-full text-brand" disabled={loading} onclick={loadMore}>
      {#if loading}<span class="loading loading-spinner loading-sm"></span>{/if}
      Older trips
    </button>
  {/if}
</section>
