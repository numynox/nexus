<script lang="ts">
  import "leaflet/dist/leaflet.css";
  import { onMount } from "svelte";
  import type { NearbyFuelStation } from "../../lib/data";

  interface LocationCoordinates {
    lat: number;
    lng: number;
  }

  let {
    stations = [],
    userLocation = null,
    selectedStationId = null,
    onSelectStation,
  }: {
    stations?: NearbyFuelStation[];
    userLocation?: LocationCoordinates | null;
    selectedStationId?: string | number | null;
    onSelectStation?: ((stationId: string | number) => void) | undefined;
  } = $props();

  let mapContainer: HTMLDivElement | null = null;
  let leafletModule: any = null;
  let map: any = null;
  let stationLayer: any = null;
  let userLayer: any = null;
  let stationMarkers = new Map<string, any>();
  let hasFittedBounds = false;
  let cleanupMap: (() => void) | undefined;

  function getDisplayPrice(station: NearbyFuelStation): number | null {
    return typeof station.currentPrice === "number" &&
      Number.isFinite(station.currentPrice)
      ? station.currentPrice
      : null;
  }

  function getLowestPrice(stationsList: NearbyFuelStation[]): number | null {
    const prices = stationsList
      .map((station) => getDisplayPrice(station))
      .filter((price): price is number => price !== null);

    return prices.length > 0 ? Math.min(...prices) : null;
  }

  function isLowestPrice(
    station: NearbyFuelStation,
    lowestPrice: number | null,
  ): boolean {
    const price = getDisplayPrice(station);
    return (
      lowestPrice !== null &&
      price !== null &&
      Math.abs(price - lowestPrice) < 0.000001
    );
  }

  function escapeHtml(value: string): string {
    return value
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function popupMarkup(station: NearbyFuelStation): string {
    const brand = escapeHtml(station.brand || station.name || "Station");
    const place = escapeHtml(station.place || "");
    const street = escapeHtml(
      `${station.street || ""} ${station.house_number || ""}`.trim(),
    );
    const price = getDisplayPrice(station);

    return `
      <div class="nearby-station-popup">
        <div class="nearby-station-popup__brand">${brand}</div>
        ${place ? `<div class="nearby-station-popup__place">${place}</div>` : ""}
        ${street ? `<div class="nearby-station-popup__street">${street}</div>` : ""}
        ${price !== null ? `<div class="nearby-station-popup__price">${price.toFixed(3)} €</div>` : ""}
      </div>
    `;
  }

  function formatPriceParts(price: number): {
    base: string;
    superscript: string;
  } {
    const fixed = price.toFixed(3);
    return {
      base: fixed.slice(0, -1),
      superscript: fixed.slice(-1),
    };
  }

  function createMarkerIcon(
    L: any,
    station: NearbyFuelStation,
    options: { isLowest: boolean; isSelected: boolean },
  ) {
    const price = getDisplayPrice(station);
    const priceLabel =
      price !== null
        ? formatPriceParts(price)
        : { base: "n/", superscript: "a" };
    const markerClasses = [
      "nearby-map-marker",
      options.isLowest ? "nearby-map-marker--lowest" : "",
      options.isSelected ? "nearby-map-marker--selected" : "",
    ]
      .filter(Boolean)
      .join(" ");

    return L.divIcon({
      className: markerClasses,
      html: `<div class="nearby-map-marker__pill"><span class="nearby-map-marker__base">${priceLabel.base}</span><sup class="nearby-map-marker__sup">${priceLabel.superscript}</sup></div>`,
      iconSize: [62, 28],
      iconAnchor: [31, 14],
      popupAnchor: [0, -16],
    });
  }

  function clearStationLayer(): void {
    stationLayer?.clearLayers();
    stationMarkers.clear();
  }

  function syncMapData(): void {
    if (!leafletModule || !map || !stationLayer || !userLayer) return;

    const L = leafletModule;
    clearStationLayer();
    userLayer.clearLayers();

    const boundsTargets: Array<[number, number]> = [];
    const lowestPrice = getLowestPrice(stations);

    if (userLocation) {
      boundsTargets.push([userLocation.lat, userLocation.lng]);

      L.circleMarker([userLocation.lat, userLocation.lng], {
        radius: 8,
        color: "#ffffff",
        weight: 3,
        fillColor: "#2563eb",
        fillOpacity: 1,
      }).addTo(userLayer);

      L.circle([userLocation.lat, userLocation.lng], {
        radius: 120,
        color: "#60a5fa",
        weight: 1,
        fillColor: "#93c5fd",
        fillOpacity: 0.12,
      }).addTo(userLayer);
    }

    for (const station of stations) {
      if (!Number.isFinite(station.lat) || !Number.isFinite(station.lng))
        continue;

      boundsTargets.push([station.lat as number, station.lng as number]);
      const marker = L.marker([station.lat as number, station.lng as number], {
        icon: createMarkerIcon(L, station, {
          isLowest: isLowestPrice(station, lowestPrice),
          isSelected: selectedStationId === station.id,
        }),
      })
        .bindPopup(popupMarkup(station), {
          className: "nearby-map-popup-shell",
          closeButton: false,
          offset: [0, -12],
        })
        .on("click", () => {
          onSelectStation?.(station.id);
        })
        .on("popupopen", () => {
          onSelectStation?.(station.id);
        });

      marker.addTo(stationLayer);
      stationMarkers.set(String(station.id), marker);
    }

    if (boundsTargets.length > 0) {
      const bounds = L.latLngBounds(boundsTargets);
      if (!hasFittedBounds) {
        map.fitBounds(bounds, {
          padding: [36, 36],
          maxZoom: 14,
        });
        hasFittedBounds = true;
      }
    } else {
      map.setView([49.597, 11.004], 11);
    }

    openSelectedPopup();
  }

  function openSelectedPopup(): void {
    if (!selectedStationId) return;
    const marker = stationMarkers.get(String(selectedStationId));
    if (!marker || !map) return;

    const markerLatLng = marker.getLatLng?.();
    if (markerLatLng) {
      map.panTo(markerLatLng, {
        animate: true,
      });
    }

    marker.openPopup();
  }

  onMount(() => {
    void (async () => {
      if (!mapContainer) return;

      const L = await import("leaflet");
      leafletModule = L;

      map = L.map(mapContainer, {
        zoomControl: false,
        scrollWheelZoom: true,
        attributionControl: true,
      });

      L.control.zoom({ position: "topright" }).addTo(map);

      L.tileLayer(
        "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
        {
          attribution:
            '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
          subdomains: "abcd",
          maxZoom: 20,
        },
      ).addTo(map);

      stationLayer = L.layerGroup().addTo(map);
      userLayer = L.layerGroup().addTo(map);

      cleanupMap = () => {
        stationMarkers.clear();
        map?.remove();
        map = null;
        stationLayer = null;
        userLayer = null;
        leafletModule = null;
        hasFittedBounds = false;
      };

      syncMapData();
    })();

    return () => {
      cleanupMap?.();
    };
  });

  $effect(() => {
    stations;
    userLocation;
    if (map && leafletModule) {
      syncMapData();
    }
  });

  $effect(() => {
    selectedStationId;
    if (!leafletModule || !map || !stationLayer) return;

    const lowestPrice = getLowestPrice(stations);
    for (const station of stations) {
      const marker = stationMarkers.get(String(station.id));
      if (!marker) continue;
      marker.setIcon(
        createMarkerIcon(leafletModule, station, {
          isLowest: isLowestPrice(station, lowestPrice),
          isSelected: selectedStationId === station.id,
        }),
      );
    }

    openSelectedPopup();
  });
</script>

<div
  class="card overflow-hidden border border-primary/10 bg-base-200 shadow-xl"
>
  <div class="card-body p-0">
    <div
      class="nearby-map-root relative z-0 isolate h-[24rem] w-full bg-base-300/30"
    >
      <div bind:this={mapContainer} class="h-full w-full"></div>
    </div>
  </div>
</div>

<style>
  :global(.nearby-map-root .leaflet-container) {
    z-index: 0;
  }

  :global(.nearby-map-marker) {
    background: transparent;
    border: 0;
  }

  :global(.nearby-map-marker__pill) {
    display: inline-flex;
    min-width: 3.7rem;
    justify-content: center;
    align-items: center;
    border-radius: 9999px;
    padding: 0.28rem 0.45rem 0.22rem;
    background: rgba(51, 65, 85, 0.84);
    color: rgba(255, 255, 255, 0.96);
    font-size: 0.78rem;
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.02em;
    box-shadow: 0 6px 14px rgba(15, 23, 42, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.3);
  }

  :global(.nearby-map-marker__base) {
    line-height: 1;
  }

  :global(.nearby-map-marker__sup) {
    position: relative;
    top: -0.22rem;
    margin-left: 0.02rem;
    font-size: 0.56rem;
    line-height: 1;
  }

  :global(.nearby-map-marker--lowest .nearby-map-marker__pill) {
    border: 2px solid rgb(16, 185, 129);
    box-shadow: 0 6px 14px rgba(15, 23, 42, 0.12);
  }

  :global(.nearby-map-marker--selected .nearby-map-marker__pill) {
    border: 2px solid rgb(59, 130, 246);
    box-shadow: 0 6px 14px rgba(15, 23, 42, 0.12);
    transform: translateY(-1px) scale(1.02);
  }

  :global(
    .nearby-map-marker--lowest.nearby-map-marker--selected
      .nearby-map-marker__pill
  ) {
    border: 2px solid rgb(16, 185, 129);
    outline: 2px solid rgba(59, 130, 246, 0.95);
    outline-offset: 1px;
    box-shadow: 0 6px 14px rgba(15, 23, 42, 0.12);
  }

  :global(.nearby-map-popup-shell .leaflet-popup-content-wrapper) {
    border-radius: 0.95rem;
    padding: 0;
    background: rgba(248, 250, 252, 0.96);
    border: 1px solid rgba(148, 163, 184, 0.22);
    box-shadow: 0 20px 48px rgba(15, 23, 42, 0.16);
  }

  :global(.nearby-map-popup-shell .leaflet-popup-content) {
    margin: 0;
  }

  :global(.nearby-map-popup-shell .leaflet-popup-tip) {
    background: rgba(248, 250, 252, 0.96);
    border: 1px solid rgba(148, 163, 184, 0.18);
    box-shadow: 0 12px 24px rgba(15, 23, 42, 0.1);
  }

  :global(.nearby-station-popup) {
    min-width: 12rem;
    padding: 0.75rem 0.85rem 0.8rem;
    background: transparent;
    color: rgb(15, 23, 42);
  }

  :global(.nearby-station-popup__brand) {
    font-size: 0.9rem;
    font-weight: 800;
    line-height: 1.2;
  }

  :global(.nearby-station-popup__place) {
    margin-top: 0.18rem;
    font-size: 0.78rem;
    font-weight: 600;
    color: rgba(15, 23, 42, 0.7);
  }

  :global(.nearby-station-popup__street) {
    margin-top: 0.28rem;
    font-size: 0.76rem;
    color: rgba(15, 23, 42, 0.56);
  }

  :global(.nearby-station-popup__price) {
    margin-top: 0.62rem;
    display: inline-flex;
    border-radius: 9999px;
    padding: 0.28rem 0.62rem;
    background: rgba(15, 23, 42, 0.06);
    color: rgb(5, 150, 105);
    font-size: 0.78rem;
    font-weight: 800;
  }
</style>
