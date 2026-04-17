<script lang="ts">
  import dayjs from "dayjs";
  import { Car, ChevronDown } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    fetchCarKmPerMonth,
    fetchCarRefuelPlotEvents,
    fetchCarRefuelStatistics,
    fetchCarRefuelYearBounds,
    fetchCarsForUser,
    fetchFuelPriceWeeklyMinima,
  } from "../../lib/data";
  import {
    clearLastSelectedCarId,
    getLastSelectedCarId,
    setLastSelectedCarId,
  } from "../../lib/storage";
  import { session } from "../../lib/stores";
  import VehicleStatsChart from "./VehicleStatsChart.svelte";

  const VehicleStatsChartAny = VehicleStatsChart as any;

  type RangeValue = "1y" | "3y" | "5y" | "overall" | `year:${number}`;

  interface RangeOption {
    value: RangeValue;
    label: string;
  }

  let cars = $state<any[]>([]);
  let selectedCar = $state<any | null>(null);
  let loadingCars = $state(true);
  let loadingStats = $state(true);
  let loadingPlots = $state(true);
  let stats = $state<any | null>(null);
  let plotEvents = $state<any[]>([]);
  let kmPerMonth = $state<any[]>([]);
  let weeklyMarketMinPrices = $state<any[]>([]);
  let yearOptions = $state<number[]>([]);
  let selectedRange = $state<RangeValue>("1y");
  let overallSinceIso = $state<string | null>(null);
  let showAllCards = $state(false);

  let statsLoadToken = 0;
  let plotsLoadToken = 0;

  const yearNow = dayjs().year();

  onMount(() => {
    fetchCars();
  });

  function rangeOptions(): RangeOption[] {
    return [
      { value: "1y", label: "A year ago" },
      { value: "3y", label: "Three years ago" },
      { value: "5y", label: "Five years ago" },
      { value: "overall", label: "Overall" },
      ...yearOptions.map((year) => ({
        value: `year:${year}` as RangeValue,
        label: String(year),
      })),
    ];
  }

  function resolveRange(value: RangeValue): {
    sinceIso: string;
    untilIso: string;
  } {
    const now = dayjs();

    if (value === "1y") {
      return {
        sinceIso: now.subtract(1, "year").startOf("day").toISOString(),
        untilIso: now.toISOString(),
      };
    }

    if (value === "3y") {
      return {
        sinceIso: now.subtract(3, "year").startOf("day").toISOString(),
        untilIso: now.toISOString(),
      };
    }

    if (value === "5y") {
      return {
        sinceIso: now.subtract(5, "year").startOf("day").toISOString(),
        untilIso: now.toISOString(),
      };
    }

    if (value === "overall") {
      return {
        sinceIso:
          overallSinceIso ||
          now.subtract(50, "year").startOf("day").toISOString(),
        untilIso: now.toISOString(),
      };
    }

    const year = Number(value.replace("year:", ""));
    const since = dayjs().year(year).startOf("year");
    const until =
      year >= yearNow ? dayjs() : dayjs().year(year).endOf("year").endOf("day");

    return {
      sinceIso: since.toISOString(),
      untilIso: until.toISOString(),
    };
  }

  function selectCar(car: any) {
    selectedCar = car;
    setLastSelectedCarId(car.id);
    showAllCards = false;
  }

  async function fetchCars() {
    loadingCars = true;

    if (!$session?.user?.id) {
      cars = [];
      selectedCar = null;
      loadingCars = false;
      return;
    }

    cars = await fetchCarsForUser($session.user.id);

    const persistedCarId = getLastSelectedCarId();
    const resolvedCar =
      cars.find((car) => car.id === persistedCarId) || cars[0] || null;

    selectedCar = resolvedCar;

    if (resolvedCar) {
      setLastSelectedCarId(resolvedCar.id);
    } else {
      clearLastSelectedCarId();
    }

    loadingCars = false;
  }

  async function fetchCarStatsAndBounds(carId: string) {
    const token = ++statsLoadToken;
    loadingStats = true;

    const [nextStats, bounds] = await Promise.all([
      fetchCarRefuelStatistics(carId),
      fetchCarRefuelYearBounds(carId),
    ]);

    if (token !== statsLoadToken) return;

    stats = nextStats;

    const minYear = Number(bounds.min_year);
    const maxYear = Number(bounds.max_year);
    if (Number.isFinite(minYear) && Number.isFinite(maxYear)) {
      const years: number[] = [];
      for (let year = maxYear; year >= minYear; year -= 1) {
        years.push(year);
      }
      yearOptions = years;
      overallSinceIso = dayjs().year(minYear).startOf("year").toISOString();
    } else {
      yearOptions = [];
      overallSinceIso = null;
    }

    if (
      selectedRange.startsWith("year:") &&
      !yearOptions.includes(Number(selectedRange.replace("year:", "")))
    ) {
      selectedRange = "1y";
    }

    loadingStats = false;
  }

  async function fetchPlots(carId: string, range: RangeValue) {
    const token = ++plotsLoadToken;
    loadingPlots = true;
    const { sinceIso, untilIso } = resolveRange(range);
    const fuelType = selectedCar?.preferred_fuel_type || "E10";
    const timeZone =
      typeof Intl !== "undefined"
        ? Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
        : "UTC";

    const [eventRows, monthlyRows, weeklyRows] = await Promise.all([
      fetchCarRefuelPlotEvents(carId, sinceIso, untilIso),
      fetchCarKmPerMonth(carId, sinceIso, untilIso),
      fetchFuelPriceWeeklyMinima(fuelType, sinceIso, untilIso, timeZone),
    ]);

    if (token !== plotsLoadToken) return;

    plotEvents = eventRows;
    kmPerMonth = monthlyRows;
    weeklyMarketMinPrices = weeklyRows;
    loadingPlots = false;
  }

  $effect(() => {
    const carId = selectedCar?.id;
    if (!carId) return;

    fetchCarStatsAndBounds(carId);
  });

  $effect(() => {
    const carId = selectedCar?.id;
    const range = selectedRange;
    if (!carId) return;

    fetchPlots(carId, range);
  });

  function asNumber(value: any): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function fmt(
    value: any,
    decimals = 0,
    options: { useThousandsSeparator?: boolean } = {},
  ): string {
    const { useThousandsSeparator = false } = options;
    const numeric = asNumber(value);
    const fixed = numeric.toFixed(decimals);

    if (!useThousandsSeparator) {
      return fixed;
    }

    const [intPart, decimalPart] = fixed.split(".");
    const sign = intPart.startsWith("-") ? "-" : "";
    const intAbs = sign ? intPart.slice(1) : intPart;
    const grouped = intAbs.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
    return decimalPart !== undefined
      ? `${sign}${grouped}.${decimalPart}`
      : `${sign}${grouped}`;
  }

  function fmtEuro(value: any): string {
    return `${fmt(value, 2)}€`;
  }

  function fmtKm(value: any, useThousandsSeparator = false): string {
    return `${fmt(value, 0, { useThousandsSeparator })} km`;
  }

  function fmtLiters(value: any): string {
    return `${fmt(value, 1)} L`;
  }

  const consumptionPoints = $derived.by(() =>
    plotEvents
      .filter((entry) => {
        const value = Number(entry.consumption_l_per_100km);
        return Number.isFinite(value) && value > 0;
      })
      .map((entry) => ({
        x: dayjs(entry.fueled_at).valueOf(),
        y: Number(entry.consumption_l_per_100km),
      })),
  );

  const pricePoints = $derived.by(() =>
    plotEvents
      .filter((entry) => Number.isFinite(Number(entry.price_per_liter)))
      .map((entry) => ({
        x: dayjs(entry.fueled_at).valueOf(),
        y: Number(entry.price_per_liter),
      })),
  );

  const weeklyMarketMinPricePoints = $derived.by(() =>
    weeklyMarketMinPrices
      .filter((entry) => Number.isFinite(Number(entry.min_price)))
      .map((entry) => ({
        x: dayjs(entry.week_start).valueOf(),
        y: Number(entry.min_price),
      })),
  );

  const mileagePoints = $derived.by(() =>
    plotEvents
      .filter((entry) => Number.isFinite(Number(entry.mileage)))
      .map((entry) => ({
        x: dayjs(entry.fueled_at).valueOf(),
        y: Number(entry.mileage),
      })),
  );

  const kmPerMonthPoints = $derived.by(() =>
    kmPerMonth
      .filter((entry) => Number.isFinite(Number(entry.km_driven)))
      .map((entry) => ({
        x: dayjs(entry.month_key).valueOf(),
        y: Number(entry.km_driven),
      })),
  );
</script>

<div class="space-y-6 animate-in slide-in-from-right duration-500">
  {#if loadingCars}
    <div class="h-32 flex items-center justify-center">
      <span class="loading loading-spinner text-primary"></span>
    </div>
  {:else if selectedCar}
    <div class="flex items-center justify-between gap-3">
      {#if cars.length > 1}
        <div class="flex flex-col items-start gap-1">
          {#if selectedCar.plate}
            <span
              class="inline-flex items-center rounded border-2 border-base-content/60 bg-base-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content"
            >
              {selectedCar.plate}
            </span>
          {/if}
          <div class="dropdown">
            <button
              type="button"
              tabindex="0"
              class="btn btn-ghost btn-sm px-0 min-h-0 h-auto normal-case hover:outline hover:outline-2 hover:outline-base-content/20"
            >
              <span
                class="flex items-center gap-2 text-3xl font-black tracking-tight text-base-content"
              >
                {selectedCar.name}
                <ChevronDown class="w-5 h-5" />
              </span>
            </button>
            <ul
              class="menu dropdown-content z-[1] mt-2 w-64 rounded-box bg-base-200 p-2 shadow-xl border border-base-content/10"
            >
              {#each cars as carOption (carOption.id)}
                {#if carOption.id !== selectedCar.id}
                  <li>
                    <button type="button" onclick={() => selectCar(carOption)}>
                      <span class="font-semibold">{carOption.name}</span>
                    </button>
                  </li>
                {/if}
              {/each}
            </ul>
          </div>
        </div>
      {:else}
        <div class="flex flex-col items-start gap-1">
          {#if selectedCar.plate}
            <span
              class="inline-flex items-center rounded border-2 border-base-content/60 bg-base-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content"
            >
              {selectedCar.plate}
            </span>
          {/if}
          <h1 class="text-3xl font-black text-base-content tracking-tight">
            {selectedCar.name}
          </h1>
        </div>
      {/if}
    </div>

    {#if loadingStats}
      <div class="h-24 flex items-center justify-center">
        <span class="loading loading-spinner text-primary"></span>
      </div>
    {:else}
      <div class="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <section class="rounded-2xl border border-success/20 bg-success/5 p-4">
          <h2
            class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
          >
            Amount of fuel consumed
          </h2>
          <div class="mt-1 text-3xl font-black tracking-tight text-success">
            {fmtLiters(stats?.fuel_used_total_l)}
          </div>
          <div class="mt-3 space-y-1 text-sm text-base-content/75">
            <div class="flex items-baseline justify-between gap-3">
              <span>This year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtLiters(stats?.fuel_used_this_year_l)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Last year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtLiters(stats?.fuel_used_last_year_l)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / month</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtLiters(stats?.fuel_used_avg_month_l)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / day</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtLiters(stats?.fuel_used_avg_day_l)}</span
              >
            </div>
          </div>
        </section>

        <section class="rounded-2xl border border-info/20 bg-info/5 p-4">
          <h2
            class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
          >
            Average consumption
          </h2>
          <div class="mt-1 text-3xl font-black tracking-tight text-info">
            <span class="whitespace-nowrap tabular-nums"
              >{fmt(stats?.avg_consumption_l_per_100km, 2)}
              <span class="text-base font-bold">L/100km</span></span
            >
          </div>
          <div class="mt-3 space-y-1 text-sm text-base-content/75">
            <div class="flex items-baseline justify-between gap-3">
              <span>Lowest</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmt(stats?.min_consumption_l_per_100km, 2)}
                <span class="hidden sm:inline">L/100km</span></span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Highest</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmt(stats?.max_consumption_l_per_100km, 2)}
                <span class="hidden sm:inline">L/100km</span></span
              >
            </div>
          </div>
        </section>

        <section
          class={`rounded-2xl border border-warning/20 bg-warning/5 p-4 ${showAllCards ? "" : "hidden sm:block"}`}
        >
          <h2
            class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
          >
            Fuel costs
          </h2>
          <div class="mt-1 text-3xl font-black tracking-tight text-warning">
            {fmtEuro(stats?.fuel_cost_total_eur)}
          </div>
          <div class="mt-3 space-y-1 text-sm text-base-content/75">
            <div class="flex items-baseline justify-between gap-3">
              <span>This year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.fuel_cost_this_year_eur)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Last year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.fuel_cost_last_year_eur)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / month</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.fuel_cost_avg_month_eur)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / day</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.fuel_cost_avg_day_eur)}</span
              >
            </div>
          </div>
        </section>

        <section
          class={`rounded-2xl border border-secondary/20 bg-secondary/5 p-4 ${showAllCards ? "" : "hidden sm:block"}`}
        >
          <h2
            class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
          >
            Cost per kilometer
          </h2>
          <div class="mt-1 text-3xl font-black tracking-tight text-secondary">
            <span class="whitespace-nowrap tabular-nums"
              >{fmt(stats?.avg_cost_per_km_eur, 3)}
              <span class="text-base font-bold">€/km</span></span
            >
          </div>
          <div class="mt-3 space-y-1 text-sm text-base-content/75">
            <div class="flex items-baseline justify-between gap-3">
              <span>Lowest</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmt(stats?.min_cost_per_km_eur, 3)}
                <span class="hidden sm:inline">€/km</span></span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Highest</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmt(stats?.max_cost_per_km_eur, 3)}
                <span class="hidden sm:inline">€/km</span></span
              >
            </div>
          </div>
        </section>

        <section
          class={`rounded-2xl border border-primary/20 bg-primary/5 p-4 ${showAllCards ? "" : "hidden sm:block"}`}
        >
          <h2
            class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
          >
            Kilometers driven
          </h2>
          <div class="mt-1 text-3xl font-black tracking-tight text-primary">
            {fmtKm(stats?.driven_total_km)}
          </div>
          <div class="mt-3 space-y-1 text-sm text-base-content/75">
            <div class="flex items-baseline justify-between gap-3">
              <span>This year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtKm(stats?.driven_this_year_km)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Last year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtKm(stats?.driven_last_year_km)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / month</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtKm(stats?.driven_avg_month_km)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / day</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtKm(stats?.driven_avg_day_km)}</span
              >
            </div>
          </div>
        </section>

        <section
          class={`rounded-2xl border border-accent/20 bg-accent/5 p-4 ${showAllCards ? "" : "hidden sm:block"}`}
        >
          <h2
            class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
          >
            Expenses
          </h2>
          <div class="mt-1 text-3xl font-black tracking-tight text-accent">
            {fmtEuro(stats?.expense_total_eur)}
          </div>
          <div class="mt-3 space-y-1 text-sm text-base-content/75">
            <div class="flex items-baseline justify-between gap-3">
              <span>This year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.expense_this_year_eur)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Last year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.expense_last_year_eur)}</span
              >
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <span>Avg / year</span>
              <span
                class="font-semibold text-base-content whitespace-nowrap tabular-nums"
                >{fmtEuro(stats?.expense_avg_year_eur)}</span
              >
            </div>
          </div>
        </section>
      </div>

      {#if !showAllCards}
        <div class="sm:hidden">
          <button
            type="button"
            class="btn btn-outline btn-sm w-full"
            onclick={() => {
              showAllCards = true;
            }}
          >
            Show more
          </button>
        </div>
      {/if}

      <div class="flex justify-end">
        <div class="form-control min-w-[12rem]">
          <select
            id="stats-range-select"
            class="select select-bordered select-sm"
            bind:value={selectedRange}
            aria-label="Plot range"
          >
            {#each rangeOptions() as option (option.value)}
              <option value={option.value}>{option.label}</option>
            {/each}
          </select>
        </div>
      </div>
    {/if}

    {#if loadingPlots}
      <div class="h-24 flex items-center justify-center">
        <span class="loading loading-spinner text-primary"></span>
      </div>
    {:else}
      <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <VehicleStatsChart
          title="Average consumption over time"
          subtitle="Computed from refuel intervals"
          points={consumptionPoints}
          plotStyle="stepped-line"
          yDecimals={2}
          yUnit="L/100km"
          showAverageLine={true}
        />

        <VehicleStatsChartAny
          title="Fuel prices over time"
          subtitle="Price per liter (-) and weekly market low (--)"
          points={pricePoints}
          secondaryPoints={weeklyMarketMinPricePoints}
          secondaryLabel="Weekly market low"
          plotStyle="stepped-line"
          yDecimals={3}
          yUnit="€/L"
          showAverageLine={true}
        />

        <VehicleStatsChart
          title="Kilometers driven per month"
          subtitle="Aggregated monthly driven distance"
          points={kmPerMonthPoints}
          plotStyle="bar"
          yDecimals={0}
          yUnit="km"
          showAverageLine={true}
        />

        <VehicleStatsChart
          title="Total mileage over time"
          subtitle="Odometer value at each refuel"
          points={mileagePoints}
          yDecimals={0}
          yUnit="km"
        />
      </div>
    {/if}
  {:else}
    <div
      class="text-center py-16 bg-base-200/50 rounded-3xl border-2 border-dashed border-base-content/10"
    >
      <Car class="w-16 h-16 mx-auto text-base-content/10 mb-4" />
      <p class="text-base-content/40 font-medium">No vehicles found.</p>
      <p class="text-xs text-base-content/30 mt-1">
        Add one in Settings to get started.
      </p>
    </div>
  {/if}
</div>
