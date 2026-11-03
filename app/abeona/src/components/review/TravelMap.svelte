<script lang="ts">
  import "leaflet/dist/leaflet.css";
  import { onMount } from "svelte";
  import { basemap, cssColor } from "../../lib/basemap";
  import type { TravelMapLine } from "../../lib/data";

  interface Props {
    lines: TravelMapLine[];
  }

  let { lines }: Props = $props();

  let container: HTMLDivElement | undefined = $state();
  let leaflet: typeof import("leaflet") | null = null;
  let map: import("leaflet").Map | null = null;
  let layer: import("leaflet").LayerGroup | null = null;

  /** Every pair of stations ridden in the year: thicker the more often, stations as dots. */
  function draw() {
    const L = leaflet;
    if (!L || !map || !layer) return;
    layer.clearLayers();
    const primary = cssColor("--color-primary", "#1f6f5c");
    const ink = cssColor("--color-neutral", "#12313a");
    const card = cssColor("--ab-card", "#ffffff");
    const bounds = L.latLngBounds([]);
    const most = Math.max(1, ...lines.map((l) => l.count));

    for (const line of [...lines].sort((a, b) => a.count - b.count)) {
      const pts = line.polyline.map(([lon, lat]) => [lat, lon] as [number, number]);
      if (pts.length < 2) continue;
      const weight = 3 + Math.round((line.count / most) * 4);
      L.polyline(pts, { color: card, weight: weight + 4, opacity: 0.85, lineCap: "round", lineJoin: "round" }).addTo(layer);
      L.polyline(pts, { color: primary, weight, opacity: 0.9, lineCap: "round", lineJoin: "round" })
        .bindTooltip(`${line.from.name} – ${line.to.name} · ${line.count}×`, { sticky: true, className: "abeona-map-label" })
        .addTo(layer);
      pts.forEach((p) => bounds.extend(p));
    }
    const seen = new Set<string>();
    for (const line of lines) {
      for (const stop of [line.from, line.to]) {
        if (seen.has(stop.name)) continue;
        seen.add(stop.name);
        L.circleMarker([stop.lat, stop.lon], { radius: 4, color: ink, weight: 2, fillColor: card, fillOpacity: 1 })
          .bindTooltip(stop.name, { direction: "top", offset: [0, -4], className: "abeona-map-label" })
          .addTo(layer);
      }
    }
    if (bounds.isValid()) map.fitBounds(bounds, { padding: [24, 24], maxZoom: 10 });
  }

  onMount(() => {
    let disposed = false;
    void (async () => {
      const L = await import("leaflet");
      if (disposed || !container || map) return;
      leaflet = L;
      map = L.map(container, { zoomControl: false, scrollWheelZoom: false, attributionControl: true });
      const b = basemap();
      L.tileLayer(b.url, { attribution: b.attribution, subdomains: b.subdomains, maxZoom: b.maxZoom }).addTo(map);
      layer = L.layerGroup().addTo(map);
      draw();
    })();
    return () => {
      disposed = true;
      map?.remove();
      map = null;
      layer = null;
      leaflet = null;
    };
  });

  $effect(() => {
    void lines;
    draw();
  });
</script>

<div class="travel-map h-full min-h-56 w-full overflow-hidden rounded-3xl bg-base-200" bind:this={container}></div>

<style>
  .travel-map :global(.leaflet-container) {
    font: inherit;
    background: var(--color-base-200);
  }
  .travel-map :global(.abeona-map-label) {
    border: none;
    border-radius: 999px;
    padding: 2px 10px;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-base-content);
    background: var(--ab-card);
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.12);
  }
  .travel-map :global(.abeona-map-label::before) {
    display: none;
  }
</style>
