<script lang="ts">
  import dayjs from "dayjs";
  import { Bluetooth, CalendarDays } from "lucide-svelte";
  import {
    fetchFuelLevelEstimateForCar,
    type FuelLevelEstimate,
  } from "../../lib/data";

  /**
   * How full the tank is, and what follows from it — the Android app's fuel
   * card: how far it goes, what filling up costs at the cheapest of our
   * stations, when it runs dry, the odometer to note at the pump. Facts, not
   * prose; the badge keeps the short version for the Nearby page.
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
  const days = (value: number) => {
    const whole = Math.max(value > 0 ? 1 : 0, Math.floor(value));
    return whole === 1 ? "1 day" : `${whole} days`;
  };

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

  const facts = $derived.by(() => {
    if (!estimate) return [];
    const toFull = Math.max(0, estimate.tankCapacity - estimate.remainingLiters);
    const list: { label: string; value: string; detail?: string }[] = [
      {
        label: "Fill up",
        value:
          liters(toFull) +
          (cheapest ? ` · ${number(toFull * cheapest.price, 2)} €` : ""),
        detail: cheapest
          ? `${cheapest.name} · ${number(cheapest.price, 3)} €/L`
          : undefined,
      },
    ];
    const daysLeft = perDay ? estimate.remainingLiters / perDay : null;
    if (daysLeft !== null && daysLeft <= 365) {
      list.push({
        label: "Empty around",
        value: dayjs().add(daysLeft, "day").format("ddd D MMM"),
        detail: daysLeft < 1 ? "today" : `in ${days(daysLeft)}`,
      });
    }
    list.push({
      label: "Since refuel",
      value: days(estimate.daysSinceLastRefuel),
      detail: [
        estimate.estimatedKm !== null ? `≈ ${km(estimate.estimatedKm)}` : null,
        `${liters(estimate.startingLiters - estimate.remainingLiters)} used`,
      ]
        .filter(Boolean)
        .join(" · "),
    });
    if (estimate.estimatedMileage !== null && estimate.lastMileage !== null) {
      list.push({
        label: "Odometer",
        value: `≈ ${km(estimate.estimatedMileage)}`,
        detail: `last logged ${km(estimate.lastMileage)}`,
      });
    }
    list.push(
      estimate.method === "drive"
        ? {
            label: "Consumption",
            value:
              estimate.litersPer100Km !== null
                ? `${number(estimate.litersPer100Km, 1)} L/100 km`
                : "—",
            detail:
              `${number(estimate.drivingHours ?? 0, 1)} h driven` +
              (estimate.speedAssumed ? " · speed assumed" : ""),
          }
        : {
            label: "Consumption",
            value: `${number(estimate.averageConsumptionLitersPerDay ?? 0, 1)} L a day`,
            detail: "by the calendar",
          },
    );
    return list;
  });
</script>

{#if estimate}
  <section class="card bg-base-200 shadow-sm border border-primary/5">
    <div class="card-body gap-4 p-4 sm:p-6">
      <div class="flex items-center gap-5">
        <div
          class="radial-progress shrink-0 bg-base-300 text-sm font-bold {tone}"
          style="--value: {Math.round(estimate.fillRatio * 100)}; --size: 4.5rem; --thickness: 0.5rem;"
          role="progressbar"
          aria-valuenow={Math.round(estimate.fillRatio * 100)}
          aria-label="Estimated fuel level"
        >
          {Math.round(estimate.fillRatio * 100)}%
        </div>
        <div class="min-w-0">
          <p class="text-2xl font-black tabular-nums">
            {liters(estimate.remainingLiters)}
            <span class="text-base font-semibold text-base-content/50">
              of {liters(estimate.tankCapacity)}
            </span>
          </p>
          {#if estimate.rangeKm !== null}
            <p class="font-semibold tabular-nums {tone}">
              ≈ {km(estimate.rangeKm)} range
            </p>
          {/if}
        </div>
        <div class="ml-auto self-start text-base-content/40" title={estimate.method === "drive" ? "From driving time recorded over Bluetooth" : "From average daily use"}>
          {#if estimate.method === "drive"}
            <Bluetooth class="h-4 w-4" />
          {:else}
            <CalendarDays class="h-4 w-4" />
          {/if}
        </div>
      </div>

      <dl
        class="grid grid-cols-2 gap-x-6 gap-y-3 border-t border-base-content/10 pt-4 sm:grid-cols-3 lg:grid-cols-5"
      >
        {#each facts as fact (fact.label)}
          <div class="min-w-0">
            <dt class="text-xs text-base-content/60">{fact.label}</dt>
            <dd class="truncate font-bold tabular-nums">{fact.value}</dd>
            {#if fact.detail}
              <dd class="truncate text-xs tabular-nums text-base-content/60">
                {fact.detail}
              </dd>
            {/if}
          </div>
        {/each}
      </dl>
    </div>
  </section>
{/if}
