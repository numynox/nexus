<script lang="ts">
  import dayjs from "dayjs";
  import {
    fetchFuelLevelEstimateForCar,
    type FuelLevelEstimate,
  } from "../../lib/data";

  /**
   * How full the tank is, and what follows from it — the Android app's fuel
   * card, as compact: litres left, range, the cost of filling up at the
   * cheapest of our stations, when it runs dry. The badge keeps the short
   * version for the Nearby page.
   */
  let { carId = null, stations = [] } = $props<{
    carId?: string | null;
    stations?: any[];
  }>();

  let estimate = $state<FuelLevelEstimate | null>(null);
  let loadToken = 0;

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

  const number = (value: number, decimals = 0) =>
    value.toLocaleString(undefined, {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
  const liters = (value: number) => `${number(value)} L`;
  const km = (value: number) => `${number(value)} km`;

  const cheapest = $derived.by(() => {
    let best: { name: string; price: number } | null = null;
    for (const station of stations) {
      if (station?.currentPrice === undefined) continue;
      const price = Math.max(0, station.currentPrice - (station.discount || 0));
      if (!best || price < best.price) {
        best = { name: String(station.brand || station.name || "Station"), price };
      }
    }
    return best;
  });

  const tone = $derived(
    !estimate
      ? ""
      : estimate.fillRatio < 0.15
        ? "text-error"
        : estimate.fillRatio < 0.3
          ? "text-warning"
          : "text-success",
  );

  /** Litres a calendar day: the calendar method's rate, or what this tank has used per day so far. */
  const perDay = $derived.by(() => {
    if (!estimate) return null;
    if (estimate.method === "time") {
      return estimate.averageConsumptionLitersPerDay || null;
    }
    const used = estimate.startingLiters - estimate.remainingLiters;
    return estimate.daysSinceLastRefuel >= 1 && used > 0
      ? used / estimate.daysSinceLastRefuel
      : null;
  });

  // Four facts, the most asked: what is left, how far it goes, what filling
  // up costs at the cheapest of our stations, and when it runs dry.
  const facts = $derived.by(() => {
    if (!estimate) return [];
    const toFull = Math.max(0, estimate.tankCapacity - estimate.remainingLiters);
    const list: { label: string; value: string; tone?: string }[] = [
      {
        label: "Left",
        value: `${number(estimate.remainingLiters)} of ${liters(estimate.tankCapacity)}`,
      },
    ];
    if (estimate.rangeKm !== null) {
      list.push({ label: "Range", value: km(estimate.rangeKm), tone });
    }
    list.push({
      label: "Fill up",
      value: cheapest ? `${number(toFull * cheapest.price, 2)} €` : liters(toFull),
    });
    const daysLeft = perDay ? estimate.remainingLiters / perDay : null;
    if (daysLeft !== null && daysLeft <= 365) {
      list.push({
        label: "Empty",
        value: dayjs().add(daysLeft, "day").format("ddd D MMM"),
      });
    }
    return list;
  });
</script>

{#if estimate}
  <section class="card bg-base-200 shadow-sm border border-primary/5">
    <div class="card-body flex-row items-center gap-5 p-4 sm:px-6">
      <div
        class="radial-progress shrink-0 bg-base-300 text-sm font-bold {tone}"
        style="--value: {Math.round(estimate.fillRatio * 100)}; --size: 4rem; --thickness: 0.45rem;"
        role="progressbar"
        aria-valuenow={Math.round(estimate.fillRatio * 100)}
        aria-label="Estimated fuel level"
        title={estimate.method === "drive"
          ? "From driving time recorded over Bluetooth"
          : "From average daily use"}
      >
        {Math.round(estimate.fillRatio * 100)}%
      </div>
      <dl class="grid min-w-0 flex-1 grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
        {#each facts as fact (fact.label)}
          <div class="min-w-0">
            <dt class="text-xs text-base-content/60">{fact.label}</dt>
            <dd class="truncate font-bold tabular-nums {fact.tone ?? ''}">{fact.value}</dd>
          </div>
        {/each}
      </dl>
    </div>
  </section>
{/if}
