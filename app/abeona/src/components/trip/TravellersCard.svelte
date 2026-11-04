<script lang="ts">
  import { Check, Users } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    describeError,
    fetchTravellers,
    fetchTripTravellerIds,
    setTripBahncardDiscount,
    setTripTravellers,
    type BahnCardKind,
    type Traveller,
    type Trip,
  } from "../../lib/data";
  import { BAHNCARD_DISCOUNTS } from "../../lib/format";

  interface Props {
    trip: Trip;
    /** After anything here changed, so the warnings can be asked again. */
    onChanged: (trip: Trip) => void;
  }

  let { trip, onChanged }: Props = $props();

  let travellers = $state<Traveller[]>([]);
  let onTrip = $state<ReadonlySet<string>>(new Set());
  let loading = $state(true);
  let busy = $state(false);
  let error = $state("");

  const isBahnCard100 = $derived(trip.fare_type === "bahncard100");
  const showDiscount = $derived(!isBahnCard100 && trip.fare_type !== "deutschlandticket");

  onMount(async () => {
    try {
      const [all, ids] = await Promise.all([fetchTravellers(), fetchTripTravellerIds(trip.id)]);
      travellers = all;
      onTrip = new Set(ids);
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  });

  async function toggle(t: Traveller) {
    if (busy) return;
    const next = new Set(onTrip);
    if (next.has(t.id)) next.delete(t.id);
    else next.add(t.id);
    const before = onTrip;
    onTrip = next;
    busy = true;
    error = "";
    try {
      await setTripTravellers(trip.id, [...next]);
      onChanged(trip);
    } catch (e) {
      onTrip = before;
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  async function chooseDiscount(value: BahnCardKind | null) {
    if (busy || value === trip.bahncard_discount) return;
    busy = true;
    error = "";
    try {
      await setTripBahncardDiscount(trip.id, value);
      onChanged({ ...trip, bahncard_discount: value });
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }
</script>

<section class="rounded-3xl bg-card p-5 shadow-card" aria-label="Travellers">
  <div class="flex items-center gap-2">
    <Users class="h-5 w-5 text-primary" />
    <h2 class="font-semibold text-ink">Travellers</h2>
  </div>

  {#if loading}
    <div class="mt-4 h-10 animate-pulse rounded-full bg-base-200"></div>
  {:else}
    <div class="mt-4 flex flex-wrap gap-2">
      {#each travellers as t (t.id)}
        {@const on = onTrip.has(t.id)}
        <button
          class="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition
            {on ? 'border-primary bg-mint text-primary' : 'border-base-300 bg-card text-base-content/70 hover:border-base-content/30'}"
          aria-pressed={on}
          disabled={busy}
          onclick={() => toggle(t)}
        >
          {#if on}<Check class="h-3.5 w-3.5" />{/if}
          {t.name}
        </button>
      {/each}
    </div>
  {/if}

  <p class="mt-5 px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">BahnCard discount</p>
  {#if showDiscount}
    <div class="join w-full">
      {#each BAHNCARD_DISCOUNTS as d (d.label)}
        <button
          class="btn btn-sm join-item flex-1 {trip.bahncard_discount === d.value ? 'btn-primary' : 'bg-card'}"
          aria-pressed={trip.bahncard_discount === d.value}
          disabled={busy}
          onclick={() => chooseDiscount(d.value)}
        >{d.value === null ? "None" : d.value}</button>
      {/each}
    </div>
  {:else}
    <p class="px-1 text-sm text-base-content/60">
      {isBahnCard100 ? "Every traveller needs a BahnCard 100." : "None with a Deutschlandticket."}
    </p>
  {/if}

  {#if error}<p class="mt-3 text-sm text-error" role="alert">{error}</p>{/if}
</section>
