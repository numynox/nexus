<script lang="ts">
  import dayjs from "dayjs";
  import { Plus } from "lucide-svelte";
  import {
    fetchFuelPricePlotHistory,
    fetchFuelStationsCurrentPrices,
    fetchLatestAccessibleRefuelEvent,
    type LastRefuelEventPoint,
  } from "../../lib/data";
  import {
    getFuelPricePreviousDays,
    getLastSelectedCarId,
  } from "../../lib/storage";
  import { preferredFuelType } from "../../lib/stores";
  import FuelLevelEstimateBadge from "./FuelLevelEstimateBadge.svelte";
  import PriceChart from "./PriceChart.svelte";
  import StationList from "./StationList.svelte";

  let { onRefuel, priceBucketMinutes = 10 } = $props();
  const selectedCarId = getLastSelectedCarId();

  let stations = $state<any[]>([]);
  let history = $state<any[]>([]);
  let lastRefuelPoint = $state<LastRefuelEventPoint | null>(null);
  let loading = $state(true);
  let previousDays = $state(getFuelPricePreviousDays());

  async function fetchData() {
    loading = true;
    const [st, fetchedLastRefuelPoint] = await Promise.all([
      fetchFuelStationsCurrentPrices($preferredFuelType),
      fetchLatestAccessibleRefuelEvent(),
    ]);

    stations = st.map((s: any) => ({
      ...s,
      currentPrice:
        s.current_price === null || s.current_price === undefined
          ? undefined
          : Number(s.current_price),
      discount:
        s.discount === null || s.discount === undefined
          ? 0
          : Number(s.discount),
    }));
    lastRefuelPoint = fetchedLastRefuelPoint;

    const sinceIso = dayjs()
      .startOf("day")
      .subtract(previousDays, "day")
      .toISOString();
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    history = await fetchFuelPricePlotHistory(
      $preferredFuelType,
      sinceIso,
      priceBucketMinutes,
      timeZone,
    );
    loading = false;
  }

  $effect(() => {
    previousDays;
    if ($preferredFuelType) fetchData();
  });
</script>

<div class="space-y-8 animate-in fade-in duration-500">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-3xl font-black text-base-content">Fuel Price</h1>
      <p class="text-sm text-base-content/60">
        Minimum <span class="text-primary">{$preferredFuelType} (–)</span>
        price by time of day and
        <span class="text-primary">last refuel price (•)</span>.
      </p>
    </div>
    <div class="flex items-center gap-2 self-start lg:gap-4">
      <FuelLevelEstimateBadge carId={selectedCarId} />
      <button
        class="btn btn-primary btn-sm rounded-full gap-2"
        onclick={onRefuel}
      >
        <Plus class="w-4 h-4" />
        <span class="hidden sm:inline">Refuel</span>
      </button>
    </div>
  </div>

  {#if loading}
    <div class="h-64 flex items-center justify-center">
      <span class="loading loading-dots loading-lg text-primary"></span>
    </div>
  {:else}
    <div
      class="card bg-base-200 shadow-xl overflow-hidden border border-primary/5"
    >
      <div class="card-body p-2 sm:p-6 h-80 sm:h-96">
        <PriceChart {history} {previousDays} {lastRefuelPoint} />
      </div>
    </div>

    <div class="space-y-4">
      <h2 class="text-xl font-bold px-1">My Stations</h2>
      <StationList {stations} />
    </div>
  {/if}
</div>
