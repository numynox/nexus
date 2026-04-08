<script lang="ts">
  import {
    Banknote,
    CalendarClock,
    Droplets,
    Gauge,
    MapPin,
    Rows3,
  } from "lucide-svelte";
  import { onMount } from "svelte";
  import { createRefuelEvent, fetchFuelStations } from "../../lib/data";
  import { session } from "../../lib/stores";

  let {
    car,
    lastMileage = null,
    onSuccess,
    onCancel,
  } = $props<{
    car: any;
    lastMileage?: number | null;
    onSuccess: () => void;
    onCancel: () => void;
  }>();

  let mileage = $state(0);
  let liters = $state(0);
  let totalPrice = $state(0);
  let fuelLevelPercent = $state(100);
  let isFull = $state(true);
  let fueledAtLocal = $state("");
  let stations = $state<any[]>([]);
  let stationsLoading = $state(true);
  let selectedFuelStationId = $state("__unknown__");
  let loading = $state(false);
  let submitted = $state(false);

  function formatDateTimeLocal(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours(),
    )}:${pad(date.getMinutes())}`;
  }

  function stationLabel(station: any): string {
    const brand = station.brand || "";
    const place = station.place || "";
    const street = station.street || "";
    const houseNumber = station.house_number || "";
    return `${brand} ${place} (${street} ${houseNumber})`
      .replace(/\s+/g, " ")
      .trim();
  }

  onMount(async () => {
    fueledAtLocal = formatDateTimeLocal(new Date());
    try {
      stations = await fetchFuelStations();
    } catch (error) {
      alert(error instanceof Error ? error.message : String(error));
    } finally {
      stationsLoading = false;
    }
  });

  const mileageError = $derived(
    !submitted && mileage === 0
      ? null
      : mileage <= 0
        ? "Enter a mileage greater than 0 km."
        : mileage >= 1_000_000
          ? "Mileage must be below 1,000,000 km."
          : lastMileage !== null && mileage <= lastMileage
            ? `Must be higher than the previous entry (${lastMileage.toLocaleString()} km).`
            : null,
  );

  const litersError = $derived(
    !submitted && liters === 0
      ? null
      : liters < 5
        ? "At least 5 L must be refueled."
        : liters > 200
          ? "Amount cannot exceed 200 L."
          : liters > car.tank_capacity
            ? `Amount cannot exceed the tank capacity (${car.tank_capacity} L).`
            : null,
  );

  const priceError = $derived(
    !submitted && totalPrice === 0
      ? null
      : totalPrice < 5
        ? "Price must be at least 5 \u20ac."
        : totalPrice > 500
          ? "Price cannot exceed 500 \u20ac."
          : null,
  );

  // Minimum fuel level after refuel: even if tank was empty, it must now
  // contain at least the refueled amount.
  const minFuelLevelPercent = $derived(
    liters > 0 ? Math.ceil((liters / car.tank_capacity) * 100) : 0,
  );

  const fuelLevelError = $derived(
    !isFull && liters > 0 && fuelLevelPercent < minFuelLevelPercent
      ? `Fuel level must be at least ${minFuelLevelPercent}% \u2014 you refueled ${liters} L into a ${car.tank_capacity} L tank.`
      : null,
  );

  const formIsValid = $derived(
    mileage > 0 &&
      mileage < 1_000_000 &&
      (lastMileage === null || mileage > lastMileage) &&
      liters >= 5 &&
      liters <= 200 &&
      liters <= car.tank_capacity &&
      totalPrice >= 5 &&
      totalPrice <= 500 &&
      fueledAtLocal.length > 0 &&
      (isFull || fuelLevelPercent >= minFuelLevelPercent),
  );

  async function handleSubmit(e: SubmitEvent) {
    e.preventDefault();
    submitted = true;

    if (!formIsValid) return;

    const userId = $session?.user?.id;
    if (!userId) {
      alert("You must be signed in to add a refuel entry.");
      return;
    }

    loading = true;

    // Logic: Convert percentage to liters (current level in tank after refuel)
    // Percentage * tank_capacity
    const levelInLiters = isFull
      ? car.tank_capacity
      : (fuelLevelPercent / 100) * car.tank_capacity;
    const pricePerLiter = totalPrice / liters;
    const fueledAtIso = new Date(fueledAtLocal).toISOString();

    try {
      await createRefuelEvent({
        car_id: car.id,
        user_id: userId,
        mileage,
        liters,
        total_price: totalPrice,
        fuel_level_after: levelInLiters,
        price_per_liter_calculated: pricePerLiter,
        fueled_at: fueledAtIso,
        fuel_station_id:
          selectedFuelStationId === "__unknown__"
            ? null
            : selectedFuelStationId,
      });
      onSuccess();
    } catch (error) {
      alert(error instanceof Error ? error.message : String(error));
    } finally {
      loading = false;
    }
  }

  const kmSinceLastRefuel = $derived(
    lastMileage !== null && mileage > lastMileage
      ? mileage - lastMileage
      : null,
  );

  // Fuel actually consumed = refueled liters + the unfilled portion of the tank
  // (e.g. 20% of a 50L tank = 10L if fuel level after is 80%)
  const fuelConsumed = $derived(
    liters + (1 - fuelLevelPercent / 100) * car.tank_capacity,
  );

  const consumption = $derived(
    kmSinceLastRefuel !== null && liters > 0
      ? (fuelConsumed / kmSinceLastRefuel) * 100
      : null,
  );

  const numTiles = $derived(lastMileage !== null ? 4 : 3);

  $effect(() => {
    if (isFull) fuelLevelPercent = 100;
  });

  // Clamp slider up if liters increase beyond what the current level allows
  $effect(() => {
    if (!isFull && fuelLevelPercent < minFuelLevelPercent) {
      fuelLevelPercent = minFuelLevelPercent;
    }
  });
</script>

<form onsubmit={handleSubmit} class="space-y-5">
  <!-- Input Grid (2 columns on desktop, 1 on mobile) -->
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
    <!-- Mileage Input -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="mileage">
        <span
          class="label-text font-semibold flex items-center gap-2 whitespace-nowrap"
        >
          <Gauge class="w-4 h-4 text-warning flex-shrink-0" />
          Total mileage (km)
        </span>
      </label>
      <input
        type="number"
        id="mileage"
        placeholder="45230"
        bind:value={mileage}
        class="input input-bordered input-sm w-full {mileageError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if lastMileage !== null}
        <p class="text-xs text-base-content/50 mt-1">
          Previous: {lastMileage.toLocaleString()} km
        </p>
      {/if}
      {#if mileageError}
        <p class="text-xs text-error mt-1">{mileageError}</p>
      {/if}
    </div>

    <!-- Liters Input -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="liters">
        <span
          class="label-text font-semibold flex items-center gap-2 whitespace-nowrap"
        >
          <Droplets class="w-4 h-4 text-info flex-shrink-0" />
          Amount refueled (L)
        </span>
      </label>
      <input
        type="number"
        id="liters"
        placeholder="45.50"
        step="0.01"
        bind:value={liters}
        class="input input-bordered input-sm w-full {litersError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if litersError}
        <p class="text-xs text-error mt-1">{litersError}</p>
      {/if}
    </div>

    <!-- Price Input -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="totalPrice">
        <span
          class="label-text font-semibold flex items-center gap-2 whitespace-nowrap"
        >
          <Banknote class="w-4 h-4 text-success flex-shrink-0" />
          Price (€)
        </span>
      </label>
      <input
        type="number"
        id="totalPrice"
        placeholder="68.50"
        step="0.01"
        bind:value={totalPrice}
        class="input input-bordered input-sm w-full {priceError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if priceError}
        <p class="text-xs text-error mt-1">{priceError}</p>
      {/if}
    </div>

    <!-- Fuel Level Input -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="fuelLevel">
        <span
          class="label-text font-semibold flex items-center gap-2 whitespace-nowrap"
        >
          <Rows3 class="w-4 h-4 text-success flex-shrink-0" />
          Fuel level (after)
        </span>
      </label>
      <div class="flex items-center gap-3 w-full">
        <input
          type="range"
          id="fuelLevel"
          min="0"
          max="100"
          bind:value={fuelLevelPercent}
          class="range range-primary range-sm flex-1"
          disabled={isFull}
        />
        <span
          class="text-sm font-bold text-primary w-10 text-right flex-shrink-0"
        >
          {fuelLevelPercent}%
        </span>
      </div>
      <label class="label cursor-pointer justify-start gap-2 mt-2 py-0 px-0">
        <input
          type="checkbox"
          id="isFull"
          bind:checked={isFull}
          class="checkbox checkbox-primary checkbox-sm"
        />
        <span class="label-text text-sm">Full</span>
      </label>
      {#if fuelLevelError}
        <p class="text-xs text-error mt-1">{fuelLevelError}</p>
      {/if}
    </div>

    <!-- Fuel Station -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="fuelStation">
        <span
          class="label-text font-semibold flex items-center gap-2 whitespace-nowrap"
        >
          <MapPin class="w-4 h-4 text-info flex-shrink-0" />
          Fuel station
        </span>
      </label>
      <select
        id="fuelStation"
        bind:value={selectedFuelStationId}
        class="select select-bordered select-sm w-full focus:select-primary"
        disabled={stationsLoading}
      >
        <option value="__unknown__">Other fuel station</option>
        {#each stations as station (station.id)}
          <option value={station.id}>{stationLabel(station)}</option>
        {/each}
      </select>
    </div>

    <!-- Date -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="fueledAt">
        <span
          class="label-text font-semibold flex items-center gap-2 whitespace-nowrap"
        >
          <CalendarClock class="w-4 h-4 text-primary flex-shrink-0" />
          Date
        </span>
      </label>
      <input
        type="datetime-local"
        id="fueledAt"
        bind:value={fueledAtLocal}
        class="input input-bordered input-sm w-full focus:input-primary"
        required
      />
    </div>
  </div>

  <!-- Calculated Info -->
  <div
    class="grid grid-cols-2 gap-2 bg-base-100 p-3 rounded-lg {numTiles === 4
      ? 'sm:grid-cols-4'
      : 'sm:grid-cols-3'}"
  >
    {#if lastMileage !== null}
      <div class="text-center py-2">
        <div
          class="text-xs text-base-content/60 uppercase tracking-wider font-semibold"
        >
          Distance
        </div>
        {#if kmSinceLastRefuel !== null && !mileageError}
          <div class="text-base font-bold text-warning">
            {kmSinceLastRefuel.toLocaleString()} km
          </div>
        {:else}
          <div class="text-base font-bold text-base-content/20">&mdash;</div>
        {/if}
      </div>
    {/if}
    <div class="text-center py-2">
      <div
        class="text-xs text-base-content/60 uppercase tracking-wider font-semibold"
      >
        Tank filled
      </div>
      {#if liters > 0 && !litersError}
        <div class="text-base font-bold text-info">
          {((liters / car.tank_capacity) * 100).toFixed(1)}%
        </div>
      {:else}
        <div class="text-base font-bold text-base-content/20">&mdash;</div>
      {/if}
    </div>
    <div class="text-center py-2">
      <div
        class="text-xs text-base-content/60 uppercase tracking-wider font-semibold"
      >
        Price / L
      </div>
      {#if liters > 0 && totalPrice > 0 && !litersError && !priceError}
        <div class="text-base font-bold text-success">
          {(totalPrice / liters).toFixed(3)}€
        </div>
      {:else}
        <div class="text-base font-bold text-base-content/20">&mdash;</div>
      {/if}
    </div>
    {#if lastMileage !== null}
      <div class="text-center py-2">
        <div
          class="text-xs text-base-content/60 uppercase tracking-wider font-semibold"
        >
          Consumption
        </div>
        {#if consumption !== null && !litersError && !mileageError && !fuelLevelError}
          <div class="text-base font-bold text-secondary">
            {consumption.toFixed(1)} L/100km
          </div>
        {:else}
          <div class="text-base font-bold text-base-content/20">&mdash;</div>
        {/if}
      </div>
    {/if}
  </div>

  <!-- Action Buttons -->
  <div class="flex gap-2 mt-6 pt-3 border-t border-base-200">
    <button
      type="button"
      class="btn btn-outline btn-sm flex-1"
      onclick={onCancel}
    >
      Cancel
    </button>
    <button
      type="submit"
      class="btn btn-primary btn-sm flex-1"
      disabled={loading || (submitted && !formIsValid)}
    >
      {#if loading}
        <span class="loading loading-spinner loading-sm"></span>
      {:else}
        <Droplets class="w-4 h-4" />
      {/if}
      Save
    </button>
  </div>
</form>
