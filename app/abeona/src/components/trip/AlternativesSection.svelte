<script lang="ts">
  import { CircleCheck, CircleHelp, CircleX, RefreshCw, Shuffle } from "lucide-svelte";
  import { describeError, fetchAlternatives, type Trip } from "../../lib/data";
  import { formatDuration, formatTime } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import type { AlternativesOrigin, AlternativesReason } from "../../lib/travelday";
  import type { Alternative, Allowance, Validity } from "../../lib/transit";
  import DelayBadge from "../common/DelayBadge.svelte";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    trip: Trip;
    origin: AlternativesOrigin | null;
    reason: AlternativesReason | null;
    /** Changes whenever the trip's live values were refreshed: one search per refresh. */
    refreshKey: string | null;
  }

  let { trip, origin, reason, refreshKey }: Props = $props();

  let loading = $state(false);
  let error = $state("");
  let alternatives = $state<Alternative[] | null>(null);
  let allowance = $state<Allowance | null>(null);
  let searchedFrom = $state<string | null>(null);
  let lastKey: string | null = null;
  let section: HTMLElement | undefined = $state();

  const headline = $derived.by(() => {
    switch (reason) {
      case "missed":
        return "Looks like you missed a connection";
      case "cancelled":
        return "A train on your trip is cancelled";
      case "at_risk":
        return "A change on your trip is at risk";
      default:
        return "Alternatives";
    }
  });

  const meta: Record<Validity, { label: string; tone: string }> = {
    valid: { label: "Covered by your ticket", tone: "bg-mint text-primary" },
    check: { label: "Check the terms", tone: "bg-sky text-ink" },
    invalid: { label: "Needs another ticket", tone: "bg-base-200 text-base-content/70" },
  };

  export async function search() {
    if (!origin || loading) return;
    loading = true;
    error = "";
    try {
      const result = await fetchAlternatives(trip, origin, origin.time);
      alternatives = result.alternatives;
      allowance = result.allowance;
      searchedFrom = `${origin.name}, ${formatTime(origin.time)}`;
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  export function reveal() {
    section?.scrollIntoView({ behavior: "smooth", block: "start" });
    if (!alternatives) void search();
  }

  // By itself only when something is wrong, and once per refresh of the trip.
  $effect(() => {
    if (reason && origin && refreshKey !== lastKey) {
      lastKey = refreshKey;
      void search();
    }
  });
</script>

{#if origin}
  <section bind:this={section} id="alternatives" class="scroll-mt-6 rounded-3xl bg-card p-5 shadow-card sm:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0">
        <h2 class="text-lg font-semibold {reason ? 'text-amber-ink' : 'text-ink'}">{headline}</h2>
        <p class="mt-0.5 text-sm text-base-content/60">
          {searchedFrom ? `From ${searchedFrom} to ${trip.destination_name}` : `From ${origin.name} to ${trip.destination_name}, checked against your ticket`}
        </p>
      </div>
      <button class="btn btn-sm rounded-full {alternatives ? 'btn-ghost text-primary' : 'btn-primary'}" onclick={search} disabled={loading}>
        {#if loading}
          <span class="loading loading-spinner loading-xs"></span>
        {:else if alternatives}
          <RefreshCw class="h-4 w-4" />
        {:else}
          <Shuffle class="h-4 w-4" />
        {/if}
        {alternatives ? "Search again" : "Find alternatives"}
      </button>
    </div>

    {#if error}
      <p class="mt-4 rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">Could not search: {error}</p>
    {/if}

    {#if alternatives}
      {#if alternatives.length === 0}
        <p class="mt-4 text-sm text-base-content/70">No connections found from here. DB Navigator may know more.</p>
      {:else}
        <ul class="mt-4 space-y-2">
          {#each alternatives as alt, i (i)}
            {@const s = summarise(alt.legs)}
            <li class="rounded-2xl border border-base-300/70 px-4 py-3 {alt.validity === 'invalid' ? 'opacity-60' : ''}">
              <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span class="tabular text-lg font-semibold text-ink">
                  {formatTime(s.departure.current ?? s.departure.planned)} – {formatTime(s.arrival.current ?? s.arrival.planned)}
                </span>
                <DelayBadge minutes={s.arrivalDelay} cancelled={s.cancelled} />
                <span class="text-sm text-base-content/60">
                  {formatDuration(s.plannedMinutes)} · {s.changes === 0 ? "direct" : s.changes === 1 ? "1 change" : `${s.changes} changes`}
                </span>
                <span class="ml-auto inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold {meta[alt.validity].tone}">
                  {#if alt.validity === "valid"}<CircleCheck class="h-3.5 w-3.5" />{:else if alt.validity === "check"}<CircleHelp class="h-3.5 w-3.5" />{:else}<CircleX class="h-3.5 w-3.5" />{/if}
                  {meta[alt.validity].label}
                </span>
              </div>
              <div class="mt-2 flex flex-wrap items-center gap-1.5">
                {#each s.rides as ride, r (r)}
                  {#if r > 0}<span class="text-base-content/35">›</span>{/if}
                  <LegChip leg={ride} />
                {/each}
                <span class="ml-1 truncate text-xs text-base-content/55">from {s.departure.name}{s.rides[0]?.from.platform ? `, platform ${s.rides[0].from.platform}` : ""}</span>
              </div>
              {#if alt.validity !== "valid"}
                <p class="mt-1.5 text-xs text-base-content/60">{alt.validity_note}</p>
              {/if}
            </li>
          {/each}
        </ul>
      {/if}
      <p class="mt-4 text-xs leading-relaxed text-base-content/55">
        {allowance?.hint ?? "Check the terms of your ticket before you board another train."}
        {#if allowance?.binding_lifted}The binding is lifted at {allowance.arrival_delay_minutes} min expected delay or a cancellation.{/if}
      </p>
    {:else if !loading}
      <p class="mt-3 text-sm text-base-content/60">
        Any time you like: connections from {origin.name}, marked by whether your ticket covers them.
      </p>
    {/if}
  </section>
{/if}
