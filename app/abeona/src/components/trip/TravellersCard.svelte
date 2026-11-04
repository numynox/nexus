<script lang="ts">
  import { Users } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    describeError,
    fetchTravellers,
    fetchTripTravellers,
    saveTripTravellers,
    type BahnCardKind,
    type Traveller,
    type Trip,
    type TripTraveller,
  } from "../../lib/data";
  import TravellerPicker from "./TravellerPicker.svelte";

  interface Props {
    trip: Trip;
    /** After anything here changed, so the warnings can be asked again. */
    onChanged: (trip: Trip) => void;
  }

  let { trip, onChanged }: Props = $props();

  let travellers = $state<Traveller[]>([]);
  let onTrip = $state<TripTraveller[]>([]);
  let loading = $state(true);
  let busy = $state(false);
  let error = $state("");

  const isBahnCard100 = $derived(trip.fare_type === "bahncard100");
  const showDiscount = $derived(!isBahnCard100 && trip.fare_type !== "deutschlandticket");

  onMount(async () => {
    try {
      const [all, riders] = await Promise.all([fetchTravellers(), fetchTripTravellers(trip.id)]);
      travellers = all;
      onTrip = riders;
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  });

  /** The discount every traveller shares, as the database keeps it on the trip; else null. */
  function shared(list: TripTraveller[]): BahnCardKind | null {
    const kinds = new Set(list.map((t) => t.bahncard_discount));
    return kinds.size === 1 ? [...kinds][0] : null;
  }

  async function save(next: TripTraveller[]) {
    if (busy) return;
    const before = onTrip;
    onTrip = next;
    busy = true;
    error = "";
    try {
      await saveTripTravellers(trip.id, next);
      onChanged({ ...trip, bahncard_discount: shared(next) });
    } catch (e) {
      onTrip = before;
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  function toggle(t: Traveller) {
    // Someone added joins without a discount until it is chosen.
    void save(
      onTrip.some((r) => r.traveller_id === t.id)
        ? onTrip.filter((r) => r.traveller_id !== t.id)
        : [...onTrip, { traveller_id: t.id, bahncard_discount: null }],
    );
  }

  function choose(id: string, discount: BahnCardKind | null) {
    if (onTrip.find((r) => r.traveller_id === id)?.bahncard_discount === discount) return;
    void save(onTrip.map((r) => (r.traveller_id === id ? { ...r, bahncard_discount: discount } : r)));
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
    <div class="mt-4">
      <TravellerPicker {travellers} selected={onTrip} {showDiscount} disabled={busy} onToggle={toggle} onDiscount={choose} />
    </div>
    {#if !showDiscount}
      <p class="mt-3 px-1 text-sm text-base-content/60">
        {isBahnCard100 ? "Every traveller needs a BahnCard 100." : "No BahnCard discount with a Deutschlandticket."}
      </p>
    {:else if onTrip.length > 0}
      <p class="mt-2 px-1 text-xs text-base-content/55">The BahnCard discount each one's share was bought with.</p>
    {/if}
  {/if}

  {#if error}<p class="mt-3 text-sm text-error" role="alert">{error}</p>{/if}
</section>
