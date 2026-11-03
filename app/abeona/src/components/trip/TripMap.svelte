<script lang="ts">
  import "leaflet/dist/leaflet.css";
  import { onMount } from "svelte";
  import { basemap, cssColor } from "../../lib/basemap";
  import { productFamily } from "../../lib/format";
  import type { Leg } from "../../lib/transit";

  interface Props {
    legs: Leg[];
  }

  let { legs }: Props = $props();

  let container: HTMLDivElement | undefined = $state();
  let leaflet: typeof import("leaflet") | null = null;
  let map: import("leaflet").Map | null = null;
  let layer: import("leaflet").LayerGroup | null = null;

  /** The leg's line: its route, else straight through its stops. */
  function pointsOf(leg: Leg): [number, number][] {
    if (leg.polyline && leg.polyline.length > 1) return leg.polyline.map(([lon, lat]) => [lat, lon]);
    const stops = [leg.from, ...leg.stopovers, leg.to];
    return stops
      .filter((s) => typeof s.lat === "number" && typeof s.lon === "number")
      .map((s) => [s.lat as number, s.lon as number]);
  }

  function draw() {
    const L = leaflet;
    if (!L || !map || !layer) return;
    layer.clearLayers();

    const primary = cssColor("--color-primary", "#1f6f5c");
    const ink = cssColor("--color-neutral", "#12313a");
    const card = cssColor("--ab-card", "#ffffff");
    const amber = "#f2b84b";
    const bounds = L.latLngBounds([]);

    for (const leg of legs) {
      const pts = pointsOf(leg);
      if (pts.length < 2) continue;
      const family = productFamily(leg);
      const color = family === "walk" ? ink : family === "long" ? ink : primary;
      // A pale casing under each ride keeps it readable on any basemap.
      if (family !== "walk") {
        L.polyline(pts, { color: card, weight: 9, opacity: 0.9, lineCap: "round", lineJoin: "round" }).addTo(layer);
      }
      L.polyline(pts, {
        color: leg.cancelled ? cssColor("--color-error", "#c4553b") : color,
        weight: family === "walk" ? 3 : 5,
        opacity: family === "walk" ? 0.6 : 0.95,
        dashArray: family === "walk" ? "2 8" : undefined,
        lineCap: "round",
        lineJoin: "round",
      }).addTo(layer);
      pts.forEach((p) => bounds.extend(p));

      for (const stop of leg.stopovers) {
        if (typeof stop.lat !== "number" || typeof stop.lon !== "number") continue;
        L.circleMarker([stop.lat, stop.lon], {
          radius: 3,
          color: family === "long" ? ink : primary,
          weight: 2,
          fillColor: card,
          fillOpacity: 1,
        })
          .bindTooltip(stop.name, { direction: "top", offset: [0, -4] })
          .addTo(layer);
      }
    }

    // Origin, changes and destination: bigger dots, the ends in amber/green.
    const rides = legs.filter((l) => l.mode !== "walk");
    const ends = rides.flatMap((l, i) => {
      const out: { lat: number | null; lon: number | null; name: string; kind: "start" | "change" | "end" }[] = [];
      if (i === 0) out.push({ ...l.from, kind: "start" });
      out.push({ ...l.to, kind: i === rides.length - 1 ? "end" : "change" });
      return out;
    });
    for (const p of ends) {
      if (typeof p.lat !== "number" || typeof p.lon !== "number") continue;
      L.circleMarker([p.lat, p.lon], {
        radius: p.kind === "change" ? 6 : 8,
        color: p.kind === "change" ? ink : card,
        weight: 3,
        fillColor: p.kind === "start" ? primary : p.kind === "end" ? amber : card,
        fillOpacity: 1,
      })
        .bindTooltip(p.name, { direction: "top", offset: [0, -6], className: "abeona-map-label" })
        .addTo(layer);
      bounds.extend([p.lat, p.lon]);
    }

    if (bounds.isValid()) map.fitBounds(bounds, { padding: [36, 36], maxZoom: 13 });
  }

  onMount(() => {
    let disposed = false;
    void (async () => {
      const L = await import("leaflet");
      if (disposed || !container || map) return;
      leaflet = L;
      map = L.map(container, { zoomControl: false, scrollWheelZoom: false, attributionControl: true });
      L.control.zoom({ position: "topright" }).addTo(map);
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
    // Redraw when live values change the legs (cancellations).
    void legs;
    draw();
  });
</script>

<div class="trip-map relative h-72 overflow-hidden rounded-3xl bg-base-200 shadow-card sm:h-80" bind:this={container}></div>

<style>
  .trip-map :global(.leaflet-container) {
    font: inherit;
    background: var(--color-base-200);
  }
  .trip-map :global(.abeona-map-label) {
    border: none;
    border-radius: 999px;
    padding: 2px 10px;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-base-content);
    background: var(--ab-card);
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.12);
  }
  .trip-map :global(.abeona-map-label::before) {
    display: none;
  }
  .trip-map :global(.leaflet-control-zoom a) {
    border-radius: 999px;
  }
  .trip-map :global(.leaflet-control-zoom) {
    border: none;
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.12);
    border-radius: 999px;
    overflow: hidden;
  }
</style>
