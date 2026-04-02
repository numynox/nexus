<script lang="ts">
  import { Plus, RefreshCw } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    fetchFuelPriceHistory,
    fetchFuelStationsWithPrices,
    invokeRefreshFuelPrices,
  } from "../../lib/data";
  import { preferredFuelType } from "../../lib/stores";
  import PriceChart from "./PriceChart.svelte";
  import StationList from "./StationList.svelte";

  let { onRefuel } = $props();

  let stations = $state<any[]>([]);
  let history = $state<any[]>([]);
  let loading = $state(true);
  let refreshing = $state(false);

  onMount(() => {
    fetchData();
  });

  async function fetchData() {
    loading = true;
    const st = await fetchFuelStationsWithPrices();

    stations = st
      .map((s: any) => {
        const latestPrices = s.fuel_prices
          .filter((p: any) => p.fuel_type === $preferredFuelType)
          .sort(
            (a: any, b: any) =>
              new Date(b.checked_at).getTime() -
              new Date(a.checked_at).getTime(),
          );
        return { ...s, currentPrice: latestPrices[0]?.price };
      })
      .sort(
        (a: any, b: any) => (a.currentPrice || 999) - (b.currentPrice || 999),
      );

    const seventyTwoHoursAgo = new Date(
      Date.now() - 72 * 60 * 60 * 1000,
    ).toISOString();
    history = await fetchFuelPriceHistory(
      $preferredFuelType,
      seventyTwoHoursAgo,
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
    if ($preferredFuelType) fetchData();
  });
</script>

<div class="space-y-8 animate-in fade-in duration-500">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-3xl font-black text-base-content">Dashboard</h1>
      <p class="text-sm text-base-content/60">
        Lowest prices for {$preferredFuelType} (last 72h)
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
        <PriceChart {history} />
      </div>
    </div>

    <div class="space-y-4">
      <h2 class="text-xl font-bold px-1">Stations</h2>
      <StationList {stations} />
    </div>
  {/if}
</div>
