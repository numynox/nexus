<script lang="ts">
  /**
   * Other connections from where the traveller is, each marked by whether
   * the ticket covers it: a sheet (abeona-design.md §9, Alternatives). It
   * searches by itself when something broke; the live card's button, the
   * banner or the ⋮ menu opens it.
   */
  import { ArrowRightLeft, Ban, CircleCheck, CircleHelp, Clock, RefreshCw, Shuffle, X } from "lucide-svelte";
  import { describeError, fetchAlternatives, type Trip } from "../../lib/data";
  import { formatDuration, formatTime } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import type { AlternativesOrigin, AlternativesReason } from "../../lib/travelday";
  import type { Alternative, Allowance, Validity } from "../../lib/transit";
  import Fact from "../common/Fact.svelte";
  import LegChip from "../common/LegChip.svelte";
  import PlatformBox from "../common/PlatformBox.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";

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
    sheet?.close();
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
  let sheet: HTMLDialogElement | undefined = $state();

  const headline = $derived.by(() => {
    switch (reason) {
      case "missed":
        return "Looks like you missed a connection";
      case "cancelled":
        return "A train on your trip is cancelled";
      case "dropped":
        return "A train on your trip no longer runs as booked";
      case "impossible":
        return "A change on your trip is impossible";
      case "at_risk":
        return "A change on your trip is at risk";
      default:
        return "Alternatives";
    }
  });

  const meta = {
    valid: { label: "Covered by your ticket", short: "Covered", tone: "ok", icon: CircleCheck },
    check: { label: "Check the terms", short: "Check terms", tone: "watch", icon: CircleHelp },
    invalid: { label: "Needs another ticket", short: "Other ticket", tone: "bad", icon: Ban },
  } as const satisfies Record<Validity, unknown>;

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
    sheet?.showModal();
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

<dialog bind:this={sheet} id="alternatives" class="modal modal-bottom sm:modal-middle" aria-labelledby="alternatives-title">
  <div class="modal-box max-h-[85dvh] rounded-t-3xl bg-card p-4 sm:rounded-3xl">
    {#if origin}
      <div class="flex items-start gap-2">
        <div class="min-w-0 flex-1">
          <h2 id="alternatives-title" class="t-title {reason ? 'text-bad-ink' : 'text-ink'}">{headline}</h2>
          <p class="t-meta">{searchedFrom ?? origin.name} → {trip.destination_name}</p>
        </div>
        {#if alternatives}
          <button class="btn btn-ghost btn-circle btn-sm" onclick={search} disabled={loading} aria-label="Search again" title="Search again">
            {#if loading}<span class="loading loading-spinner loading-xs"></span>{:else}<RefreshCw class="h-5 w-5" aria-hidden="true" />{/if}
          </button>
        {/if}
        <form method="dialog"><button class="btn btn-ghost btn-circle btn-sm" aria-label="Close"><X class="h-5 w-5" aria-hidden="true" /></button></form>
      </div>

      {#if error}
        <p class="t-meta mt-3 rounded-2xl bg-bad-soft px-4 py-3 text-bad-ink" role="alert">Could not search: {error}</p>
      {/if}

      {#if alternatives}
        {#if alternatives.length === 0}
          <p class="t-body mt-3 text-muted">No connections found from here. DB Navigator may know more.</p>
        {:else}
          <ul class="mt-2 divide-y divide-line">
            {#each alternatives as alt, i (i)}
              {@const s = summarise(alt.legs)}
              {@const first = s.rides[0]}
              <li class="py-2">
                <div class="flex items-center gap-2">
                  <span class="t-title inline-flex items-baseline gap-1.5 text-ink">
                    <ShiftedTime planned={s.departure.planned} current={s.departure.current} block={false} cancelled={s.cancelled} />
                    <span class="text-muted" aria-label="to">→</span>
                    <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} block={false} cancelled={s.cancelled} />
                  </span>
                  <StatusChip tone={meta[alt.validity].tone} icon={meta[alt.validity].icon} label={meta[alt.validity].short} title={meta[alt.validity].label} class="ml-auto" />
                </div>
                <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
                  <Fact icon={Clock} label="Duration" value={formatDuration(s.plannedMinutes)} />
                  <Fact icon={ArrowRightLeft} label={s.changes === 0 ? "Direct, changes" : "Changes"} value={s.changes} />
                  <span class="flex flex-wrap items-center gap-1">
                    {#each s.rides as ride, r (r)}<LegChip leg={ride} />{/each}
                  </span>
                  <span class="t-meta min-w-0 truncate">{s.departure.name}</span>
                  {#if first}<PlatformBox planned={first.from.planned_platform} current={first.from.platform} />{/if}
                  {#if onTake}
                    <button class="btn btn-sm ml-auto rounded-full {alt.validity === 'valid' ? 'btn-primary' : 'btn-ghost border border-line text-ink'}" onclick={() => choose(alt)}>
                      {alt.validity === "valid" ? "Take" : "Take anyway…"}
                    </button>
                  {/if}
                </div>
                {#if alt.validity !== "valid" && alt.validity_note}
                  <p class="t-meta mt-1">{alt.validity_note}</p>
                {/if}
              </li>
            {/each}
          </ul>
        {/if}
        <!-- The hint once, under the list. -->
        <p class="t-meta mt-2">
          {allowance?.hint ?? "Check the terms of your ticket before you board another train."}
          {#if allowance?.binding_lifted}
            The train binding is lifted{allowance.binding_lifted_reason ? `: ${liftedBecause[allowance.binding_lifted_reason] ?? "other trains on your route count"}` : ""}.
          {/if}
        </p>
      {:else if loading}
        <div class="mt-3 space-y-2" aria-busy="true">
          {#each [0, 1, 2] as n (n)}<div class="h-14 animate-pulse rounded-2xl bg-well"></div>{/each}
        </div>
      {:else}
        <button class="btn btn-primary mt-4 w-full rounded-full" onclick={search}><Shuffle class="h-4 w-4" aria-hidden="true" /> Find alternatives</button>
      {/if}
    {/if}
  </div>
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>

<dialog bind:this={confirmDialog} class="modal modal-bottom sm:modal-middle" onclose={() => (confirming = null)}>
  <div class="modal-box rounded-t-3xl bg-card sm:rounded-3xl">
    <h3 class="t-title text-ink">
      {confirming?.validity === "invalid" ? "Your ticket does not cover this" : "Your ticket may not cover this"}
    </h3>
    <p class="t-body mt-2 text-muted">
      {confirming?.validity_note || "Abeona cannot tell whether your ticket covers this connection."}
    </p>
    <p class="t-body mt-2 text-muted">You may need to buy a ticket for it. Abeona follows this connection from now on.</p>
    <div class="modal-action">
      <form method="dialog"><button class="btn btn-ghost rounded-full">Cancel</button></form>
      <button class="btn btn-primary rounded-full" onclick={confirmTake}>I understand, take it</button>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
