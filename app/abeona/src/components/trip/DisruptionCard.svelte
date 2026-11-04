<script lang="ts">
  import { Route, Undo2 } from "lucide-svelte";
  import { describeError, fetchAlternatives, recordDisruption, type Trip } from "../../lib/data";
  import { manualReplacement } from "../../lib/compensation";
  import { formatTime, legLabel } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import type { Alternative, Leg } from "../../lib/transit";
  import LegChip from "../common/LegChip.svelte";

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

<section class="rounded-3xl bg-card p-5 shadow-card sm:p-6" id="what-happened">
  <div class="flex items-center gap-2">
    <Route class="h-5 w-5 text-primary" />
    <h2 class="font-semibold text-ink">What happened</h2>
  </div>

  {#if !editing}
    {#if missed}
      <p class="mt-3 text-sm text-base-content/75">
        Missed or cancelled: <span class="font-semibold text-ink">{legLabel(missed)}</span> from {missed.from.name}.
      </p>
      {#if replacement.length > 0}
        <p class="mt-2 text-xs font-semibold uppercase tracking-wider text-base-content/55">Taken instead</p>
        <ul class="mt-1.5 space-y-1.5">
          {#each replacement as ride, i (i)}
            <li class="flex flex-wrap items-center gap-2 text-sm">
              <LegChip leg={ride} />
              <span class="text-base-content/70">{ride.from.name} → {ride.to.name}</span>
              <span class="tabular ml-auto font-medium">arr. {formatTime(ride.to.current ?? ride.to.planned)}</span>
            </li>
          {/each}
        </ul>
      {:else}
        <p class="mt-2 text-sm text-amber-ink">Add the train you took instead, so the delay can be worked out.</p>
      {/if}
      <div class="mt-4 flex gap-2">
        <button class="btn btn-sm rounded-full border-base-300 bg-card" onclick={start} disabled={busy}>Change</button>
        <button class="btn btn-ghost btn-sm rounded-full" onclick={clear} disabled={busy}><Undo2 class="h-4 w-4" /> Clear</button>
      </div>
    {:else}
      <p class="mt-3 text-sm text-base-content/70">
        Missed a change, or a train was cancelled? Record the trains you took instead: the delay and the compensation follow your real arrival.
      </p>
      <button class="btn btn-sm mt-4 rounded-full border-base-300 bg-card" onclick={start}>Record a missed connection</button>
    {/if}
  {:else}
    <p class="mt-4 text-xs font-semibold uppercase tracking-wider text-base-content/55">Which train did you miss?</p>
    <div class="mt-2 flex flex-wrap gap-1.5">
      {#each rides as ride (ride.idx)}
        <button
          class="rounded-full border px-3 py-1 text-sm font-medium transition {missedIdx === ride.idx ? 'border-primary bg-mint text-primary' : 'border-base-300 text-base-content/70'}"
          onclick={() => {
            missedIdx = ride.idx;
            alternatives = null;
          }}
        >{legLabel(ride.leg)} · {formatTime(ride.leg.from.planned)}</button>
      {/each}
    </div>

    <p class="mt-5 text-xs font-semibold uppercase tracking-wider text-base-content/55">What did you take instead?</p>
    <button class="btn btn-primary btn-sm mt-2 rounded-full" onclick={search} disabled={searching || missedIdx === null}>
      {#if searching}<span class="loading loading-spinner loading-xs"></span>{/if}
      Find the connections from there
    </button>
    {#if alternatives}
      {#if alternatives.length === 0}
        <p class="mt-3 text-sm text-base-content/60">No connections found. Enter your train below.</p>
      {:else}
        <ul class="mt-3 space-y-2">
          {#each alternatives as alt, i (i)}
            {@const s = summarise(alt.legs)}
            <li>
              <button
                class="w-full rounded-2xl border border-base-300/70 px-4 py-2.5 text-left transition hover:border-primary hover:bg-mint/40"
                onclick={() => save(alt.legs)}
                disabled={busy}
              >
                <span class="tabular font-semibold text-ink">{formatTime(s.departure.planned)} – {formatTime(s.arrival.current ?? s.arrival.planned)}</span>
                <span class="ml-2 inline-flex flex-wrap gap-1 align-middle">
                  {#each s.rides as ride, r (r)}<LegChip leg={ride} />{/each}
                </span>
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    {/if}

    <form class="mt-4 grid gap-2 sm:grid-cols-[1fr_auto_auto] sm:items-end" onsubmit={saveManual}>
      <label class="form-control">
        <span class="mb-1 text-xs font-semibold text-base-content/60">Or by hand: the train</span>
        <input class="input input-bordered input-sm rounded-xl" placeholder="IC 2063" bind:value={line} />
      </label>
      <label class="form-control">
        <span class="mb-1 text-xs font-semibold text-base-content/60">Arrived at {trip.destination_name}</span>
        <input class="input input-bordered input-sm rounded-xl" type="datetime-local" bind:value={arrivedAt} />
      </label>
      <button class="btn btn-primary btn-sm rounded-full" disabled={busy || missedIdx === null}>Save</button>
    </form>
    <button class="btn btn-ghost btn-sm mt-2 rounded-full" onclick={() => (editing = false)}>Cancel</button>
  {/if}

  {#if error}
    <p class="mt-3 text-sm text-error" role="alert">{error}</p>
  {/if}
</section>
