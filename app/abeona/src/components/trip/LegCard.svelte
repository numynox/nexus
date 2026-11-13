<script lang="ts">
  import { ChevronDown } from "lucide-svelte";
  import { delayMinutes } from "../../lib/transfer";
  import {
    displayPlatform,
    formatDuration,
    formatTime,
    minutesBetween,
    platformChanged,
    productFamily,
  } from "../../lib/format";
  import type { Leg, StopTime } from "../../lib/transit";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    leg: Leg;
  }

  let { leg }: Props = $props();

  let expanded = $state(false);

  const family = $derived(productFamily(leg));
  const rail = $derived(family === "long" ? "bg-neutral" : family === "urban" ? "bg-info" : "bg-primary");
  const ring = $derived(family === "long" ? "border-neutral" : family === "urban" ? "border-info" : "border-primary");
  const stops = $derived(leg.stopovers ?? []);
</script>

{#snippet stopRow(stop: StopTime, kind: "dep" | "arr")}
  {@const delay = delayMinutes(stop.planned, stop.current)}
  {@const platform = displayPlatform(stop)}
  {@const changed = platformChanged(stop)}
  <div class="grid grid-cols-[5.75rem_1.5rem_1fr_auto] items-start gap-x-2">
    <div class="text-right">
      <ShiftedTime planned={stop.planned} current={stop.current} cancelled={leg.cancelled} class="text-2xl font-semibold leading-none tracking-tight text-ink" />
    </div>
    <div class="relative flex h-full min-h-6 justify-center self-stretch">
      <!-- the rail continues below a departure and above an arrival -->
      <span
        class="absolute left-1/2 w-1 -translate-x-1/2 {rail} opacity-80"
        style={kind === "dep" ? "top: 0.75rem; bottom: 0" : "top: 0; height: 0.75rem"}
      ></span>
      <span class="relative mt-1 h-4 w-4 rounded-full border-[3.5px] bg-card {ring}"></span>
    </div>
    <div class="min-w-0 pt-0.5">
      <p class="font-semibold leading-snug text-ink">{stop.name}</p>
    </div>
    <div class="text-right">
      {#if platform}
        <span
          class="tabular inline-flex flex-col items-center rounded-xl px-2.5 py-1 leading-none
            {changed ? 'bg-amber-soft text-amber-ink' : 'bg-base-200 text-ink'}"
          title={changed ? `Platform changed from ${stop.planned_platform}` : "Platform"}
        >
          <span class="text-[0.6rem] font-semibold uppercase tracking-wider opacity-70">Pl.</span>
          <span class="text-lg font-semibold">{platform}</span>
        </span>
        {#if changed}
          <p class="mt-1 text-[0.7rem] text-base-content/50 line-through">{stop.planned_platform}</p>
        {/if}
      {/if}
    </div>
  </div>
  <span class="sr-only">{kind === "dep" ? "Departure" : "Arrival"}</span>
{/snippet}

<article class="rounded-3xl bg-card p-4 shadow-card sm:p-5 {leg.cancelled ? 'ring-2 ring-error/30' : ''}">
  <header class="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1.5">
    <LegChip {leg} size="md" />
    {#if leg.headsign}
      <span class="min-w-0 truncate text-sm text-base-content/70">to {leg.headsign}</span>
    {/if}
    {#if leg.cancelled}
      <span class="inline-flex items-center rounded-full bg-error/12 px-2 py-0.5 text-xs font-semibold text-error">Cancelled</span>
    {/if}
    {#if leg.operator}
      <span class="ml-auto text-xs text-base-content/45">{leg.operator}</span>
    {/if}
  </header>

  {@render stopRow(leg.from, "dep")}

  <div class="grid grid-cols-[5.75rem_1.5rem_1fr] gap-x-2">
    <div></div>
    <div class="flex justify-center"><span class="w-1 rounded-full {rail} opacity-80"></span></div>
    <div class="py-3">
      {#if stops.length > 0}
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-full py-1 text-sm font-medium text-base-content/60 hover:text-primary"
          aria-expanded={expanded}
          onclick={() => (expanded = !expanded)}
        >
          {stops.length} {stops.length === 1 ? "stop" : "stops"} · {formatDuration(minutesBetween(leg.from.planned, leg.to.planned))}
          <ChevronDown class="h-4 w-4 transition-transform {expanded ? 'rotate-180' : ''}" />
        </button>
      {:else}
        <p class="py-1 text-sm text-base-content/55">
          Non-stop · {formatDuration(minutesBetween(leg.from.planned, leg.to.planned))}
        </p>
      {/if}
    </div>
  </div>

  {#if expanded}
    <ol class="mb-2">
      {#each stops as stop, i (i)}
        {@const planned = stop.planned_arrival ?? stop.planned_departure}
        {@const current = stop.arrival ?? stop.departure}
        <li class="grid grid-cols-[5.75rem_1.5rem_1fr] items-center gap-x-2 py-1.5 text-sm">
          <span class="text-right text-base-content/70"><ShiftedTime {planned} {current} block={false} cancelled={stop.cancelled} /></span>
          <span class="relative flex h-full justify-center">
            <span class="absolute inset-y-[-0.375rem] w-1 {rail} opacity-80"></span>
            <span class="relative mt-[0.35rem] h-2 w-2 rounded-full bg-card ring-2 ring-current {family === 'long' ? 'text-neutral' : 'text-primary'}"></span>
          </span>
          <span class="truncate {stop.cancelled ? 'text-base-content/45 line-through' : 'text-base-content/80'}">{stop.name}</span>
        </li>
      {/each}
    </ol>
  {/if}

  {@render stopRow(leg.to, "arr")}
</article>
