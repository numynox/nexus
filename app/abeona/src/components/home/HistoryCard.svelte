<script lang="ts">
  import { ArrowRight } from "lucide-svelte";
  import type { Statistics, Trip } from "../../lib/data";
  import { formatNumber } from "../../lib/format";
  import Illustration from "../common/Illustration.svelte";
  import TripListItem from "./TripListItem.svelte";

  interface Props {
    year: number;
    stats: Statistics | null;
    recent: Trip[];
    reviewHref: string;
    pastHref: string;
    tripHref: (id: string) => string;
  }

  let { year, stats, recent, reviewHref, pastHref, tripHref }: Props = $props();

  const punctuality = $derived(
    stats?.punctual_share === null || stats?.punctual_share === undefined
      ? "–"
      : `${Math.round(stats.punctual_share * 100)}%`,
  );
</script>

<section class="overflow-hidden rounded-[2rem] bg-card shadow-card">
  <div class="grid gap-6 p-6 sm:p-8 md:grid-cols-[1fr_14rem] md:items-center">
    <div>
      <div class="flex items-baseline justify-between gap-4">
        <div>
          <h2 class="text-xl font-semibold text-ink">History</h2>
          <p class="text-sm text-base-content/60">{year} so far</p>
        </div>
        <a href={reviewHref} class="inline-flex items-center gap-1 text-sm font-semibold text-primary hover:underline">
          Year in review <ArrowRight class="h-4 w-4" />
        </a>
      </div>

      <dl class="mt-6 grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-4">
        <div>
          <dt class="text-xs font-medium uppercase tracking-wider text-base-content/55">Trips</dt>
          <dd class="tabular mt-1 text-3xl font-semibold text-ink">{stats ? formatNumber(stats.trips) : "–"}</dd>
        </div>
        <div>
          <dt class="text-xs font-medium uppercase tracking-wider text-base-content/55">Kilometres</dt>
          <dd class="tabular mt-1 text-3xl font-semibold text-ink">{stats ? formatNumber(stats.km) : "–"}</dd>
        </div>
        <div>
          <dt class="text-xs font-medium uppercase tracking-wider text-base-content/55">Delay</dt>
          <dd class="tabular mt-1 text-3xl font-semibold {stats && stats.delay_minutes > 0 ? 'text-amber-ink' : 'text-ink'}">
            {stats ? formatNumber(stats.delay_minutes) : "–"}<span class="ml-1 text-base font-medium text-base-content/55">min</span>
          </dd>
        </div>
        <div>
          <dt class="text-xs font-medium uppercase tracking-wider text-base-content/55">On time</dt>
          <dd class="tabular mt-1 text-3xl font-semibold text-ink">{punctuality}</dd>
        </div>
      </dl>
    </div>

    <a href={reviewHref} class="hidden md:block" aria-label="Year in review">
      <Illustration name="year-review" class="w-full" />
    </a>
  </div>

  {#if recent.length > 0}
    <div class="border-t border-base-200 px-4 pt-4 pb-4 sm:px-6">
      <p class="px-2 pb-3 text-xs font-semibold uppercase tracking-wider text-base-content/55">Recent trips</p>
      <div class="space-y-2.5">
        {#each recent as trip (trip.id)}
          <TripListItem {trip} href={tripHref(trip.id)} muted />
        {/each}
      </div>
      <a href={pastHref} class="mt-3 inline-flex items-center gap-1 px-2 text-sm font-semibold text-primary hover:underline">
        All past trips <ArrowRight class="h-4 w-4" />
      </a>
    </div>
  {/if}
</section>
