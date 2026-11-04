<script lang="ts">
  import { cssColor } from "../../lib/basemap";
  import type { TravelMapLine } from "../../lib/data";
  import MapFrame from "../MapFrame.svelte";

  interface Props {
    lines: TravelMapLine[];
  }

  let { lines }: Props = $props();

  let leaflet: typeof import("leaflet") | null = null;
  let map: import("leaflet").Map | null = null;
  let layer: import("leaflet").LayerGroup | null = null;
  let bounds: import("leaflet").LatLngBounds | null = null;

  /** Every pair of stations ridden in the year: thicker the more often, stations as dots. */
  function draw() {
    const L = leaflet;
    if (!L || !map || !layer) return;
    layer.clearLayers();
    const primary = cssColor("--color-primary", "#1f6f5c");
    const ink = cssColor("--color-neutral", "#12313a");
    const card = cssColor("--ab-card", "#ffffff");
    const drawn = L.latLngBounds([]);
    const most = Math.max(1, ...lines.map((l) => l.count));

    for (const line of [...lines].sort((a, b) => a.count - b.count)) {
      const pts = line.polyline.map(([lon, lat]) => [lat, lon] as [number, number]);
      if (pts.length < 2) continue;
      const weight = 3 + Math.round((line.count / most) * 4);
      L.polyline(pts, { color: card, weight: weight + 4, opacity: 0.85, lineCap: "round", lineJoin: "round" }).addTo(layer);
      L.polyline(pts, { color: primary, weight, opacity: 0.9, lineCap: "round", lineJoin: "round" })
        .bindTooltip(`${line.from.name} – ${line.to.name} · ${line.count}×`, { sticky: true, className: "abeona-map-label" })
        .addTo(layer);
      pts.forEach((p) => drawn.extend(p));
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
    // Only the first drawing moves the camera: a live redraw keeps where the
    // user panned to.
    const first = bounds === null;
    bounds = drawn.isValid() ? drawn : null;
    if (first) fit();
  }

  function fit() {
    if (map && bounds) map.fitBounds(bounds, { padding: [24, 24], maxZoom: 10 });
  }

  function ready(L: typeof import("leaflet"), created: import("leaflet").Map) {
    leaflet = L;
    map = created;
    layer = L.layerGroup().addTo(created);
    draw();
  }

  $effect(() => {
    void lines;
    draw();
  });
</script>

<MapFrame class="h-full min-h-56 w-full rounded-3xl" label="the map of the year" onready={ready} {fit} />
