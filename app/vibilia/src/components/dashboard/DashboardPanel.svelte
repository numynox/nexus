<script lang="ts">
  import dayjs from "dayjs";
  import { Plus, RefreshCw } from "lucide-svelte";
  import {
    fetchFuelPricePlotHistory,
    fetchFuelStationsCurrentPrices,
    invokeRefreshFuelPrices,
  } from "../../lib/data";
  import { getDashboardPreviousDays } from "../../lib/storage";
  import { preferredFuelType } from "../../lib/stores";
  import PriceChart from "./PriceChart.svelte";
  import StationList from "./StationList.svelte";

  let { onRefuel, priceBucketMinutes = 10 } = $props();

  let stations = $state<any[]>([]);
  let history = $state<any[]>([]);
  let loading = $state(true);
  let refreshing = $state(false);
  let previousDays = $state(getDashboardPreviousDays());

  async function fetchData() {
    loading = true;
    const st = await fetchFuelStationsCurrentPrices($preferredFuelType);

    stations = st.map((s: any) => ({
      ...s,
      currentPrice:
        s.current_price === null || s.current_price === undefined
          ? undefined
          : Number(s.current_price),
    }));

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

  async function manualRefresh() {
    refreshing = true;
    try {
      await invokeRefreshFuelPrices();
      await fetchData();
    } catch (e) {
      console.error(e);
    } finally {
      refreshing = false;
    }
  }

  $effect(() => {
    previousDays;
    if ($preferredFuelType) fetchData();
  });
</script>

<div class="space-y-8 animate-in fade-in duration-500">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-3xl font-black text-base-content">Dashboard</h1>
      <p class="text-sm text-base-content/60">
        Minimum {$preferredFuelType} price by time of day
      </p>
    </div>
    <div class="flex gap-2">
      <button
        class="btn btn-circle btn-ghost btn-sm"
        onclick={manualRefresh}
        disabled={refreshing}
      >
        <RefreshCw class="w-4 h-4 {refreshing ? 'animate-spin' : ''}" />
      </button>
      <button
        class="btn btn-primary btn-sm rounded-full gap-2"
        onclick={onRefuel}
      >
        <Plus class="w-4 h-4" />
        <span class="hidden sm:inline">Refuel</span>
      </button>
    </div>
  </div>

  {#if loading && !refreshing}
    <div class="h-64 flex items-center justify-center">
      <span class="loading loading-dots loading-lg text-primary"></span>
    </div>
  {:else}
    <div
      class="card bg-base-200 shadow-xl overflow-hidden border border-primary/5"
    >
      <div class="card-body p-2 sm:p-6 h-80 sm:h-96">
        <PriceChart {history} {previousDays} />
      </div>
    </div>

    <div class="space-y-4">
      <h2 class="text-xl font-bold px-1">Stations</h2>
      <StationList {stations} />
    </div>
  {/if}
</div>
