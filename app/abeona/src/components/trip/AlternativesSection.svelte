<script lang="ts">
  import { CircleCheck, CircleHelp, CircleX, RefreshCw, Shuffle } from "lucide-svelte";
  import { describeError, fetchAlternatives, type Trip } from "../../lib/data";
  import { formatDuration, formatTime } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import type { AlternativesOrigin, AlternativesReason } from "../../lib/travelday";
  import type { Alternative, Allowance, Validity } from "../../lib/transit";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    trip: Trip;
    origin: AlternativesOrigin | null;
    reason: AlternativesReason | null;
    /** Changes whenever the trip's live values were refreshed: one search per refresh. */
    refreshKey: string | null;
    /** The browser's position saw a short change missed: the server lifts the binding. */
    connectionMissed?: boolean;
    /** Follow this connection from now on. */
    onTake?: (legs: Alternative["legs"]) => void;
  }

  let { trip, origin, reason, refreshKey, connectionMissed = false, onTake }: Props = $props();

  /** A connection the ticket may not cover waits here for the traveller's yes. */
  let confirming = $state<Alternative | null>(null);
  let confirmDialog: HTMLDialogElement | undefined = $state();

  function choose(alt: Alternative) {
    if (!onTake) return;
    if (alt.validity === "valid") return onTake(alt.legs);
    confirming = alt;
    confirmDialog?.showModal();
  }

  function confirmTake() {
    const alt = confirming;
    confirmDialog?.close();
    confirming = null;
    if (alt && onTake) onTake(alt.legs);
  }

  const liftedBecause: Record<string, string> = {
    impossible_connection: "a change cannot be made",
    missed_connection: "you missed a short change",
    cancelled: "a train is cancelled",
    delay: "you are expected 20 minutes late",
  };

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
      case "impossible":
        return "A change on your trip is impossible";
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
      const result = await fetchAlternatives(trip, origin, origin.time, connectionMissed);
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
                <span class="inline-flex items-baseline gap-1.5 text-lg font-semibold text-ink">
                  <ShiftedTime planned={s.departure.planned} current={s.departure.current} block={false} cancelled={s.cancelled} />
                  –
                  <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} block={false} cancelled={s.cancelled} />
                </span>
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
              {#if onTake}
                <button
                  class="btn btn-sm mt-2 rounded-full {alt.validity === 'valid' ? 'btn-primary' : 'btn-ghost border-base-300'}"
                  onclick={() => choose(alt)}
                >
                  {alt.validity === "valid" ? "Take this connection" : "Take it anyway…"}
                </button>
              {/if}
            </li>
          {/each}
        </ul>
      {/if}
      <p class="mt-4 text-xs leading-relaxed text-base-content/55">
        {allowance?.hint ?? "Check the terms of your ticket before you board another train."}
        {#if allowance?.binding_lifted}
          The train binding is lifted{allowance.binding_lifted_reason ? `: ${liftedBecause[allowance.binding_lifted_reason] ?? "other trains on your route count"}` : ""}.
        {/if}
      </p>
    {:else if !loading}
      <p class="mt-3 text-sm text-base-content/60">
        Any time you like: connections from {origin.name}, marked by whether your ticket covers them.
      </p>
    {/if}
  </section>
{/if}

<dialog bind:this={confirmDialog} class="modal modal-bottom sm:modal-middle" onclose={() => (confirming = null)}>
  <div class="modal-box rounded-t-[2rem] sm:rounded-[2rem]">
    <h3 class="text-lg font-semibold text-ink">
      {confirming?.validity === "invalid" ? "Your ticket does not cover this" : "Your ticket may not cover this"}
    </h3>
    <p class="mt-2 text-sm text-base-content/70">
      {confirming?.validity_note || "Abeona cannot tell whether your ticket covers this connection."}
    </p>
    <p class="mt-2 text-sm text-base-content/70">You may need to buy a ticket for it. Abeona follows this connection from now on.</p>
    <div class="modal-action">
      <form method="dialog"><button class="btn btn-ghost rounded-full">Cancel</button></form>
      <button class="btn btn-primary rounded-full" onclick={confirmTake}>I understand, take it</button>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
