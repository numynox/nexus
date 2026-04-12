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
  import {
    createRefuelEvent,
    fetchFuelStations,
    updateRefuelEvent,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  let {
    car,
    previousMileage = null,
    nextMileage = null,
    existingEvent = null,
    submitLabel = null,
    onSuccess,
    onCancel,
  } = $props<{
    car: any;
    previousMileage?: number | null;
    nextMileage?: number | null;
    existingEvent?: any | null;
    submitLabel?: string | null;
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
  let userLocation: { lat: number; lng: number } | null = $state(null);

  function formatDateTimeLocal(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours(),
    )}:${pad(date.getMinutes())}`;
  }

  function stationLabel(station: any, distanceInMeters?: number): string {
    const brand = station.brand || "";
    const place = station.place || "";
    const street = station.street || "";
    const houseNumber = station.house_number || "";
    const baseLabel = `${brand} ${place} (${street} ${houseNumber})`
      .replace(/\s+/g, " ")
      .trim();

    if (distanceInMeters !== undefined) {
      const distanceStr =
        distanceInMeters < 1000
          ? `${Math.round(distanceInMeters)}m`
          : `${(distanceInMeters / 1000).toFixed(1)}km`;
      return `[${distanceStr}] ${baseLabel}`;
    }

    return baseLabel;
  }

  function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const R = 6371000; // Earth's radius in meters
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  async function autoSelectNearestStation(stationsList: any[]) {
    // Only auto-select if no existing event (new refuel entry)
    if (existingEvent) return;

    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          timeout: 5000,
          enableHighAccuracy: true,
        });
      });

      const userLat = position.coords.latitude;
      const userLng = position.coords.longitude;

      // Store user location for rendering distances
      userLocation = { lat: userLat, lng: userLng };

      // Calculate distances and filter stations within 500m
      const stationsWithDistance = stationsList
        .map((station) => ({
          ...station,
          distance: calculateDistance(userLat, userLng, station.lat, station.lng),
        }))
        .filter((station) => station.distance <= 500)
        .sort((a, b) => a.distance - b.distance);

      // Auto-select the closest station if any are within 500m
      if (stationsWithDistance.length > 0) {
        selectedFuelStationId = stationsWithDistance[0].id;
      }
    } catch (error) {
      // Silently fail - geolocation may not be available or user denied permission
      console.debug("Geolocation unavailable or denied:", error);
    }
  }

  function applyInitialFormValues() {
    if (!existingEvent) {
      fueledAtLocal = formatDateTimeLocal(new Date());
      selectedFuelStationId = "__unknown__";
      return;
    }

    mileage = existingEvent.mileage ?? 0;
    liters = Number(existingEvent.liters ?? 0);
    totalPrice = Number(existingEvent.total_price ?? 0);

    const levelPercent = Math.round(
      ((Number(existingEvent.fuel_level_after) || 0) / car.tank_capacity) * 100,
    );

    fuelLevelPercent = Math.min(100, Math.max(0, levelPercent));
    isFull = fuelLevelPercent >= 100;
    fueledAtLocal = formatDateTimeLocal(
      new Date(
        existingEvent.fueled_at || existingEvent.timestamp || new Date(),
      ),
    );
    selectedFuelStationId =
      existingEvent.fuel_station_id ||
      existingEvent.fuel_station?.id ||
      "__unknown__";
  }

  onMount(async () => {
    applyInitialFormValues();
    try {
      stations = await fetchFuelStations();
      await autoSelectNearestStation(stations);
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
          : previousMileage !== null && mileage <= previousMileage
            ? `Must be higher than the previous entry.`
            : nextMileage !== null && mileage >= nextMileage
              ? `Must be lower than the next entry (${nextMileage.toLocaleString()} km).`
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
      (previousMileage === null || mileage > previousMileage) &&
      (nextMileage === null || mileage < nextMileage) &&
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

    loading = true;

    // Logic: Convert percentage to liters (current level in tank after refuel)
    // Percentage * tank_capacity
    const levelInLiters = isFull
      ? car.tank_capacity
      : (fuelLevelPercent / 100) * car.tank_capacity;
    const pricePerLiter = totalPrice / liters;
    const fueledAtIso = new Date(fueledAtLocal).toISOString();
    const payload = {
      mileage,
      liters,
      total_price: totalPrice,
      fuel_level_after: levelInLiters,
      price_per_liter_calculated: pricePerLiter,
      fueled_at: fueledAtIso,
      fuel_station_id:
        selectedFuelStationId === "__unknown__" ? null : selectedFuelStationId,
    };

    try {
      if (existingEvent?.id) {
        await updateRefuelEvent(existingEvent.id, payload);
      } else {
        const userId = $session?.user?.id;
        if (!userId) {
          alert("You must be signed in to add a refuel entry.");
          loading = false;
          return;
        }

        await createRefuelEvent({
          car_id: car.id,
          user_id: userId,
          ...payload,
        });
      }

      onSuccess();
    } catch (error) {
      alert(error instanceof Error ? error.message : String(error));
    } finally {
      loading = false;
    }
  }

  const kmSinceLastRefuel = $derived(
    previousMileage !== null && mileage > previousMileage
      ? mileage - previousMileage
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

  const numTiles = $derived(previousMileage !== null ? 4 : 3);

  const primaryActionLabel = $derived(
    submitLabel || (existingEvent ? "Update Refuel" : "Save Refuel"),
  );

  // Sort stations by distance if location is available
  const sortedStations = $derived.by(() => {
    if (!userLocation) return stations;

    return [...stations].sort((a, b) => {
      const distA = calculateDistance(userLocation.lat, userLocation.lng, a.lat, a.lng);
      const distB = calculateDistance(userLocation.lat, userLocation.lng, b.lat, b.lng);
      return distA - distB;
    });
  });

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
  <!-- Input Grid (2 columns on mobile and desktop) -->
  <div class="grid grid-cols-2 gap-4">
    <!-- Mileage Input (Row 1, Col 1) -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="mileage">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
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
        onfocus={(e) => e.target.select()}
        class="input input-bordered input-sm w-full {mileageError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if previousMileage !== null}
        <p class="text-xs text-base-content/50 mt-1">
          Previous: {previousMileage.toLocaleString()} km
        </p>
      {/if}
      {#if nextMileage !== null}
        <p class="text-xs text-base-content/50 mt-1">
          Next: {nextMileage.toLocaleString()} km
        </p>
      {/if}
      {#if mileageError}
        <p class="text-xs text-error mt-1">{mileageError}</p>
      {/if}
    </div>

    <!-- Amount Refueled Input (Row 1, Col 2) -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="liters">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
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
        onfocus={(e) => e.target.select()}
        class="input input-bordered input-sm w-full {litersError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if litersError}
        <p class="text-xs text-error mt-1">{litersError}</p>
      {/if}
    </div>

    <!-- Price Input (Row 2, Col 1) -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="totalPrice">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
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
        onfocus={(e) => e.target.select()}
        class="input input-bordered input-sm w-full {priceError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if priceError}
        <p class="text-xs text-error mt-1">{priceError}</p>
      {/if}
    </div>

    <!-- Date (Row 2, Col 2) -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="fueledAt">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
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

    <!-- Fuel Level Input (Row 3, Col 1) -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="fuelLevel">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <Rows3 class="w-4 h-4 text-success flex-shrink-0" />
          Fuel level (after)
        </span>
      </label>
      <input
        type="range"
        id="fuelLevel"
        min="0"
        max="100"
        bind:value={fuelLevelPercent}
        class="range range-primary range-sm w-full"
        disabled={isFull}
      />
      <label class="label cursor-pointer gap-2 mt-2 py-0 px-0 w-full">
        <div class="flex items-center gap-2">
          <input
            type="checkbox"
            id="isFull"
            bind:checked={isFull}
            class="checkbox checkbox-primary checkbox-sm"
          />
          <span class="label-text text-sm">Full</span>
        </div>
        <span class="text-sm font-bold text-primary flex-shrink-0 ml-auto">
          {fuelLevelPercent}%
        </span>
      </label>
      {#if fuelLevelError}
        <p class="text-xs text-error mt-1">{fuelLevelError}</p>
      {/if}
    </div>

    <!-- Fuel Station (Row 3, Col 2) -->
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="fuelStation">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
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
        {#each sortedStations as station (station.id)}
          {@const distance = userLocation
            ? calculateDistance(userLocation.lat, userLocation.lng, station.lat, station.lng)
            : undefined}
          <option value={station.id}>{stationLabel(station, distance)}</option>
        {/each}
      </select>
    </div>
  </div>

  <!-- Calculated Info -->
  <div
    class="grid grid-cols-2 gap-2 bg-base-100 p-3 rounded-lg {numTiles === 4
      ? 'sm:grid-cols-4'
      : 'sm:grid-cols-3'}"
  >
    {#if previousMileage !== null}
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
    {#if previousMileage !== null}
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
  <div class="flex gap-2 border-t border-base-200">
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
      {primaryActionLabel}
    </button>
  </div>
</form>
