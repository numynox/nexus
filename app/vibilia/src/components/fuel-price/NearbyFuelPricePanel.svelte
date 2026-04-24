<script lang="ts">
  import { LocateFixed, RefreshCw } from "lucide-svelte";
  import { onMount } from "svelte";
  import { fetchNearbyFuelStations } from "../../lib/data";
  import {
    clearNearbyFuelSearchCache,
    getNearbyFuelSearchCache,
    setNearbyFuelSearchCache,
  } from "../../lib/storage";
  import { preferredFuelType } from "../../lib/stores";
  import StationList from "./StationList.svelte";

  interface LocationCoordinates {
    lat: number;
    lng: number;
  }

  let { searchRadiusKm = 3 } = $props();

  let stations = $state<any[]>([]);
  let userLocation = $state<LocationCoordinates | null>(null);
  let loadingLocation = $state(false);
  let loadingStations = $state(false);
  let errorMessage = $state<string | null>(null);
  let resolvingLocation = $state(false);
  let loadToken = 0;
  let forceRefreshRequested = false;

  const CACHE_MAX_AGE_MS = 15 * 60 * 1000;

  function formatRadius(radiusKm: number): string {
    return Number.isInteger(radiusKm) ? `${radiusKm}` : radiusKm.toFixed(1);
  }

  function normalizeStations(input: any[]): any[] {
    return input.map((station) => ({
      ...station,
      currentPrice:
        typeof station?.currentPrice === "number"
          ? station.currentPrice
          : Number.isFinite(Number(station?.currentPrice))
            ? Number(station.currentPrice)
            : undefined,
      discount:
        typeof station?.discount === "number"
          ? station.discount
          : Number.isFinite(Number(station?.discount))
            ? Number(station.discount)
            : 0,
    }));
  }

  function formatGeolocationError(error: unknown): string {
    if (
      typeof error === "object" &&
      error !== null &&
      "code" in error &&
      typeof error.code === "number"
    ) {
      if (error.code === 1) return "Location permission was denied.";
      if (error.code === 2) return "Current location is unavailable.";
      if (error.code === 3) return "Location lookup timed out.";
    }

    if (error instanceof Error && error.message) {
      return error.message;
    }

    return "Unable to access your current location.";
  }

  function getFreshCachedStations(): any[] | null {
    const cache = getNearbyFuelSearchCache();
    if (!cache) return null;
    if (cache.fuelType !== $preferredFuelType) return null;
    if (Math.abs(cache.radiusKm - searchRadiusKm) > 0.000001) return null;
    if (Date.now() - cache.cachedAt > CACHE_MAX_AGE_MS) return null;
    return normalizeStations(cache.stations as any[]);
  }

  async function locateUser(
    options: { showLoadingIndicator?: boolean } = {},
  ): Promise<LocationCoordinates | null> {
    const { showLoadingIndicator = true } = options;

    if (typeof navigator === "undefined" || !navigator.geolocation) {
      userLocation = null;
      stations = [];
      errorMessage = "Geolocation is not available on this device.";
      return null;
    }

    resolvingLocation = true;
    loadingLocation = showLoadingIndicator;
    errorMessage = null;

    try {
      const position = await new Promise<GeolocationPosition>(
        (resolve, reject) => {
          navigator.geolocation.getCurrentPosition(resolve, reject, {
            timeout: 10000,
            enableHighAccuracy: true,
            maximumAge: 60000,
          });
        },
      );

      const location = {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
      };
      userLocation = location;
      return location;
    } catch (error) {
      userLocation = null;
      stations = [];
      errorMessage = formatGeolocationError(error);
      return null;
    } finally {
      resolvingLocation = false;
      loadingLocation = false;
    }
  }

  async function loadNearbyStations(
    location: LocationCoordinates,
    options: { forceRefresh?: boolean } = {},
  ) {
    const token = ++loadToken;
    const { forceRefresh = false } = options;

    if (!forceRefresh) {
      const cachedStations = getFreshCachedStations();
      if (cachedStations) {
        stations = cachedStations;
        errorMessage = null;
        loadingStations = false;
        return;
      }
    }

    loadingStations = true;
    errorMessage = null;

    try {
      const nextStations = await fetchNearbyFuelStations({
        lat: location.lat,
        lng: location.lng,
        fuelType: $preferredFuelType,
        radiusKm: searchRadiusKm,
      });

      if (token !== loadToken) return;
      const normalizedStations = normalizeStations(nextStations);
      stations = normalizedStations;
      setNearbyFuelSearchCache({
        cachedAt: Date.now(),
        fuelType: $preferredFuelType,
        radiusKm: searchRadiusKm,
        stations: normalizedStations,
      });
    } catch (error) {
      if (token !== loadToken) return;
      stations = [];
      clearNearbyFuelSearchCache();
      errorMessage =
        error instanceof Error && error.message
          ? error.message
          : "Nearby station lookup failed.";
    } finally {
      if (token === loadToken) {
        loadingStations = false;
      }
    }
  }

  async function refreshLocationAndStations(forceRefresh = false) {
    forceRefreshRequested = forceRefresh;

    if (!forceRefresh) {
      const cachedStations = getFreshCachedStations();
      if (cachedStations) {
        stations = cachedStations;
        errorMessage = null;
        void locateUser({ showLoadingIndicator: false });
        return;
      }
    }

    await locateUser();
  }

  onMount(() => {
    refreshLocationAndStations();
  });

  $effect(() => {
    $preferredFuelType;
    searchRadiusKm;
    if (userLocation) {
      const shouldForceRefresh = forceRefreshRequested;
      forceRefreshRequested = false;
      loadNearbyStations(userLocation, { forceRefresh: shouldForceRefresh });
    }
  });
</script>

<div class="space-y-6 animate-in fade-in duration-500">
  <div class="flex items-start justify-between gap-4">
    <div>
      <h1 class="text-3xl font-black text-base-content">Nearby Fuel Price</h1>
      <p class="text-sm text-base-content/60">
        Nearby <span class="text-primary">{$preferredFuelType}</span> prices
        within
        <span class="text-primary">{formatRadius(searchRadiusKm)} km</span> of your
        current location.
      </p>
    </div>

    <button
      type="button"
      class="btn btn-primary btn-sm rounded-full gap-2"
      onclick={() => refreshLocationAndStations(true)}
      disabled={loadingLocation || loadingStations}
    >
      {#if loadingLocation || loadingStations}
        <span class="loading loading-spinner loading-xs"></span>
      {:else}
        <RefreshCw class="w-4 h-4" />
      {/if}
      <span>Refresh</span>
    </button>
  </div>

  {#if errorMessage}
    <div class="alert alert-warning shadow-sm">
      <LocateFixed class="w-4 h-4" />
      <span>{errorMessage}</span>
    </div>
  {/if}

  {#if !userLocation && !resolvingLocation}
    <div class="badge badge-outline badge-warning badge-sm">
      No location available
    </div>
  {/if}

  {#if loadingLocation || loadingStations}
    <div class="h-64 flex items-center justify-center">
      <span class="loading loading-dots loading-lg text-primary"></span>
    </div>
  {:else}
    <div class="space-y-4">
      <h2 class="text-xl font-bold px-1">Nearby Stations</h2>
      <StationList {stations} expandable={false} />
    </div>
  {/if}
</div>
