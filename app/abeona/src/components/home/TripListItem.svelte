<script lang="ts">
  import { ChevronRight } from "lucide-svelte";
  import type { Trip } from "../../lib/data";
  import { formatDuration, formatTime } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import DelayBadge from "../common/DelayBadge.svelte";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    trip: Trip;
    href: string;
    muted?: boolean;
  }

  let { trip, href, muted = false }: Props = $props();

  const s = $derived(summarise(trip.legs));
  const when = $derived(new Date(trip.planned_departure));
</script>

<a
  {href}
  class="flex items-center gap-4 rounded-3xl bg-card p-3.5 pr-4 shadow-card transition hover:-translate-y-px hover:shadow-md"
>
  <div class="flex w-14 shrink-0 flex-col items-center rounded-2xl py-2 {muted ? 'bg-base-200' : 'bg-mint'}">
    <span class="text-[0.65rem] font-semibold uppercase tracking-wider {muted ? 'text-base-content/60' : 'text-primary'}">
      {when.toLocaleDateString("en-GB", { month: "short" })}
    </span>
    <span class="tabular text-xl font-semibold leading-tight text-ink">{when.getDate()}</span>
    <span class="text-[0.65rem] text-base-content/55">{when.toLocaleDateString("en-GB", { weekday: "short" })}</span>
  </div>

  <div class="min-w-0 flex-1">
    <p class="truncate font-semibold text-ink">
      {trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}
    </p>
    {#if trip.title}
      <p class="truncate text-xs text-base-content/60">{trip.origin_name} → {trip.destination_name}</p>
    {/if}
    <p class="tabular mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-base-content/70">
      <span class="whitespace-nowrap">{formatTime(s.departure.planned)} – {formatTime(s.arrival.planned)}</span>
      <span class="text-base-content/30">·</span>
      <span class="whitespace-nowrap">{formatDuration(s.plannedMinutes)}</span>
      <DelayBadge minutes={s.arrivalDelay} cancelled={s.cancelled} />
    </p>
    <div class="mt-2 flex flex-wrap gap-1">
      {#each s.rides as ride, i (i)}
        <LegChip leg={ride} />
      {/each}
    </div>
  </div>

  <ChevronRight class="h-5 w-5 shrink-0 text-base-content/30" />
</a>
