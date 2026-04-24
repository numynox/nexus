<script lang="ts">
  import { Gauge } from "lucide-svelte";
  import {
    fetchFuelLevelEstimateForCar,
    type FuelLevelEstimate,
  } from "../../lib/data";

  let { carId = null } = $props<{ carId?: string | null }>();

  let estimate = $state<FuelLevelEstimate | null>(null);
  let loadToken = 0;

  function formatLiters(value: number): string {
    return `${value.toFixed(value >= 10 ? 0 : 1)} L`;
  }

  function formatPercent(value: number): string {
    return `${Math.round(value * 100)}%`;
  }

  const fillBarClassName = $derived.by(() =>
    estimate && estimate.fillRatio < 0.25 ? "bg-error" : "bg-success",
  );

  const titleText = $derived.by(() => {
    if (!estimate) return "";

    return `Estimated ${formatLiters(estimate.remainingLiters)} remaining from ${formatLiters(estimate.tankCapacity)} tank capacity. Average consumption ${estimate.averageConsumptionLitersPerDay.toFixed(2)} L/day over the last year.`;
  });

  $effect(() => {
    const nextCarId = carId;
    const token = ++loadToken;

    if (!nextCarId) {
      estimate = null;
      return;
    }

    void (async () => {
      try {
        const nextEstimate = await fetchFuelLevelEstimateForCar(nextCarId);
        if (token !== loadToken) return;
        estimate = nextEstimate;
      } catch {
        if (token !== loadToken) return;
        estimate = null;
      }
    })();
  });
</script>

{#if estimate}
  <div
    class="inline-flex items-center gap-2 rounded-full border border-base-content/10 bg-base-200/80 px-2.5 py-1.5 text-base-content/75 shadow-sm backdrop-blur lg:gap-2.5 lg:px-3 lg:py-2"
    title={titleText}
    aria-label={`Estimated fuel level ${formatPercent(estimate.fillRatio)}`}
  >
    <Gauge class="h-3.5 w-3.5 shrink-0 text-base-content/50 lg:h-4 lg:w-4" />
    <span
      class="text-[10px] font-bold uppercase tracking-[0.16em] text-base-content/40"
    >
      E
    </span>
    <div
      class="relative h-2.5 w-14 shrink-0 overflow-hidden rounded-full bg-base-300 ring-1 ring-base-content/10 lg:h-3 lg:w-20"
      aria-hidden="true"
    >
      <div
        class={`absolute inset-y-0 left-0 rounded-full transition-all duration-500 ${fillBarClassName}`}
        style={`width: ${estimate.fillRatio * 100}%`}
      ></div>
    </div>
    <span
      class="text-[10px] font-bold uppercase tracking-[0.16em] text-base-content/40"
    >
      F
    </span>
    <span
      class="hidden text-xs font-bold tabular-nums text-base-content/70 sm:inline"
    >
      {formatLiters(estimate.remainingLiters)}
    </span>
  </div>
{/if}
