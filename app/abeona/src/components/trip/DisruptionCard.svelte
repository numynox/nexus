<script lang="ts">
  import { Ban, Flag, Pencil, Route, Shuffle, Undo2 } from "lucide-svelte";
  import { describeError, fetchAlternatives, recordDisruption, type Trip } from "../../lib/data";
  import { manualReplacement } from "../../lib/compensation";
  import { formatTime, legLabel } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import type { Alternative, Leg } from "../../lib/transit";
  import LegChip from "../common/LegChip.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";
  import StatusChip from "../common/StatusChip.svelte";
  import StopRow from "../common/StopRow.svelte";

  interface Props {
    trip: Trip;
    /** After something was recorded or cleared, with the trip as it now stands. */
    onChanged: (trip: Trip) => void;
  }

  let { trip, onChanged }: Props = $props();

  let editing = $state(false);
  let missedIdx = $state<number | null>(null);
  let alternatives = $state<Alternative[] | null>(null);
  let searching = $state(false);
  let busy = $state(false);
  let error = $state("");
  let line = $state("");
  let arrivedAt = $state("");

  const rides = $derived(trip.legs.map((leg, idx) => ({ leg, idx })).filter((r) => r.leg.mode !== "walk"));
  const lastRide = $derived(rides[rides.length - 1]?.leg);
  const missed = $derived(trip.missed_leg_idx !== null ? trip.legs[trip.missed_leg_idx] : null);
  const replacement = $derived((trip.replacement_legs ?? []).filter((l) => l.mode !== "walk"));

  /** yyyy-mm-ddThh:mm in local time, for a datetime-local input. */
  function localInput(iso: string | null): string {
    if (!iso) return "";
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function start() {
    editing = true;
    missedIdx = trip.missed_leg_idx ?? rides[rides.length > 1 ? 1 : 0]?.idx ?? null;
    alternatives = null;
    line = "";
    arrivedAt = localInput(lastRide?.to.current ?? lastRide?.to.planned ?? null);
  }

  async function search() {
    if (missedIdx === null) return;
    const leg = trip.legs[missedIdx];
    searching = true;
    error = "";
    try {
      const result = await fetchAlternatives(trip, leg.from, leg.from.planned ?? new Date().toISOString());
      alternatives = result.alternatives;
    } catch (e) {
      error = describeError(e);
    } finally {
      searching = false;
    }
  }

  async function save(legs: Leg[] | null) {
    busy = true;
    error = "";
    try {
      await recordDisruption(trip.id, missedIdx, legs);
      onChanged({ ...trip, missed_leg_idx: missedIdx, replacement_legs: legs && legs.length > 0 ? legs : null });
      editing = false;
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  function saveManual(event: SubmitEvent) {
    event.preventDefault();
    if (missedIdx === null || !lastRide) return;
    const arrival = new Date(arrivedAt);
    if (Number.isNaN(arrival.getTime())) {
      error = "When did you arrive?";
      return;
    }
    void save([manualReplacement(trip.legs[missedIdx], lastRide, line, arrival.toISOString())]);
  }

  async function clear() {
    busy = true;
    error = "";
    try {
      await recordDisruption(trip.id, null, null);
      onChanged({ ...trip, missed_leg_idx: null, replacement_legs: null });
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }
</script>

<section class="space-y-2" id="what-happened">
  <SectionHeader title="What happened" />
  <div class="space-y-3 rounded-3xl bg-card p-4 shadow-card">
  {#if !editing}
    {#if missed}
      <!-- What broke, struck through; then the trains taken instead, in the row grid. -->
      <div class="flex flex-wrap items-center gap-2">
        <StatusChip tone="bad" icon={Ban} label="Missed or cancelled" />
        <span class="t-body text-muted line-through">{legLabel(missed)}</span>
        <span class="t-meta">{missed.from.name}</span>
      </div>
      {#if replacement.length > 0}
        <div>
          {#each replacement as ride, i (i)}
            <StopRow planned={ride.to.planned} current={ride.to.current} name={`${ride.from.name} → ${ride.to.name}`}>
              {#snippet mark()}<Flag class="h-5 w-5" aria-label="Arrived" />{/snippet}
              {#snippet line()}<LegChip leg={ride} />{/snippet}
            </StopRow>
          {/each}
        </div>
      {:else}
        <p class="t-meta text-watch-ink">Add the train you took instead, so the delay can be worked out.</p>
      {/if}
      <div class="flex gap-2">
        <button class="btn btn-ghost btn-sm rounded-full border border-line text-ink" onclick={start} disabled={busy}><Pencil class="h-4 w-4" aria-hidden="true" /> Change</button>
        <button class="btn btn-ghost btn-sm rounded-full text-muted" onclick={clear} disabled={busy}><Undo2 class="h-4 w-4" aria-hidden="true" /> Clear</button>
      </div>
    {:else}
      <button class="flex min-h-12 w-full items-center gap-3 rounded-2xl bg-well px-3 text-left transition hover:bg-brand-soft" onclick={start}>
        <Route class="h-5 w-5 shrink-0 text-brand" aria-hidden="true" />
        <span class="min-w-0">
          <span class="t-body block font-semibold text-ink">Record a missed connection</span>
          <span class="t-meta block">The delay and the compensation follow your real arrival.</span>
        </span>
      </button>
    {/if}
  {:else}
    <p class="t-meta">Which train did you miss?</p>
    <div class="flex flex-wrap gap-2">
      {#each rides as ride (ride.idx)}
        <button
          class="tabular min-h-8 rounded-full px-3 text-[0.8125rem] font-semibold transition {missedIdx === ride.idx ? 'bg-brand-soft text-brand' : 'bg-well text-muted'}"
          aria-pressed={missedIdx === ride.idx}
          onclick={() => {
            missedIdx = ride.idx;
            alternatives = null;
          }}
        >{legLabel(ride.leg)} · {formatTime(ride.leg.from.planned)}</button>
      {/each}
    </div>

    <p class="t-meta">What did you take instead?</p>
    <button class="btn btn-ghost btn-sm rounded-full border border-line text-ink" onclick={search} disabled={searching || missedIdx === null}>
      {#if searching}<span class="loading loading-spinner loading-xs"></span>{:else}<Shuffle class="h-4 w-4" aria-hidden="true" />{/if}
      Find the connections from there
    </button>
    {#if alternatives}
      {#if alternatives.length === 0}
        <p class="t-meta">No connections found. Enter your train below.</p>
      {:else}
        <ul class="divide-y divide-line">
          {#each alternatives as alt, i (i)}
            {@const s = summarise(alt.legs)}
            <li>
              <button class="flex min-h-12 w-full flex-wrap items-center gap-x-3 gap-y-1 py-1 text-left transition hover:bg-well" onclick={() => save(alt.legs)} disabled={busy}>
                <span class="tabular t-body font-semibold text-ink">{formatTime(s.departure.planned)} → {formatTime(s.arrival.current ?? s.arrival.planned)}</span>
                <span class="inline-flex flex-wrap gap-1">
                  {#each s.rides as ride, r (r)}<LegChip leg={ride} />{/each}
                </span>
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    {/if}

    <form class="grid gap-2 sm:grid-cols-[1fr_auto_auto] sm:items-end" onsubmit={saveManual}>
      <label>
        <span class="t-meta mb-1 block">Or by hand: the train</span>
        <input class="input input-sm w-full rounded-xl border-line bg-well" placeholder="IC 2063" bind:value={line} />
      </label>
      <label>
        <span class="t-meta mb-1 block">Arrived at {trip.destination_name}</span>
        <input class="input input-sm w-full rounded-xl border-line bg-well" type="datetime-local" bind:value={arrivedAt} />
      </label>
      <button class="btn btn-primary btn-sm rounded-full" disabled={busy || missedIdx === null}>Save</button>
    </form>
    <button class="btn btn-ghost btn-sm rounded-full text-muted" onclick={() => (editing = false)}>Cancel</button>
  {/if}

  {#if error}
    <p class="t-meta text-bad-ink" role="alert">{error}</p>
  {/if}
  </div>
</section>
