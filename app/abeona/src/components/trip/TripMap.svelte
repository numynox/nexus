<script lang="ts">
  import { cssColor } from "../../lib/basemap";
  import { productFamily } from "../../lib/format";
  import type { MapMarker } from "../../lib/progress";
  import type { Leg } from "../../lib/transit";
  import MapFrame from "../MapFrame.svelte";

  interface Props {
    legs: Leg[];
    /** The traveller on the map; moves without the trip being drawn again. */
    marker?: MapMarker | null;
    /** The ride to frame (the one on board) instead of the whole trip. */
    focus?: Leg | null;
  }

  let { legs, marker = null, focus = null }: Props = $props();
  let markerLayer: import("leaflet").LayerGroup | null = null;

  let leaflet: typeof import("leaflet") | null = null;
  let map: import("leaflet").Map | null = null;
  let layer: import("leaflet").LayerGroup | null = null;
  let bounds: import("leaflet").LatLngBounds | null = null;

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
    const drawn = L.latLngBounds([]);

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
      pts.forEach((p) => drawn.extend(p));

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
      drawn.extend([p.lat, p.lon]);
    }

    // Only the first drawing moves the camera: a live redraw keeps where the
    // user panned to.
    const first = bounds === null;
    bounds = drawn.isValid() ? drawn : null;
    if (first) fit();
  }

  /** The ride in focus and the traveller on it, else the whole trip. */
  function frame(): import("leaflet").LatLngBounds | null {
    const L = leaflet;
    if (!L) return bounds;
    const pts = focus ? pointsOf(focus) : [];
    if (pts.length < 2) return bounds;
    const b = L.latLngBounds(pts);
    if (marker) b.extend([marker.lat, marker.lon]);
    return b;
  }

  function fit() {
    const b = frame();
    if (map && b) map.fitBounds(b, { padding: [36, 36], maxZoom: 13 });
  }

  function drawMarker() {
    const L = leaflet;
    if (!L || !map || !markerLayer) return;
    markerLayer.clearLayers();
    if (!marker) return;
    const primary = cssColor("--color-primary", "#1f6f5c");
    const card = cssColor("--ab-card", "#ffffff");
    const at: [number, number] = [marker.lat, marker.lon];
    L.circleMarker(at, { radius: 18, stroke: false, fillColor: primary, fillOpacity: 0.22, interactive: false }).addTo(markerLayer);
    L.circleMarker(at, {
      radius: 8,
      color: marker.kind === "timetable" ? primary : card,
      weight: 3,
      fillColor: marker.kind === "timetable" ? card : primary,
      fillOpacity: 1,
    })
      .bindTooltip(marker.kind === "timetable" ? "Where the timetable puts your train" : "You are here", {
        direction: "top",
        offset: [0, -8],
        className: "abeona-map-label",
      })
      .addTo(markerLayer);
    // Follow the traveller when they leave the view.
    if (!map.getBounds().pad(-0.1).contains(at)) fit();
  }

  function ready(L: typeof import("leaflet"), created: import("leaflet").Map) {
    leaflet = L;
    map = created;
    layer = L.layerGroup().addTo(created);
    markerLayer = L.layerGroup().addTo(created);
    draw();
    drawMarker();
  }

  $effect(() => {
    // Redraw when live values change the legs (cancellations).
    void legs;
    draw();
  });

  $effect(() => {
    void marker;
    void focus;
    drawMarker();
  });
</script>

<MapFrame class="h-72 rounded-3xl shadow-card sm:h-80" label="the trip's map" onready={ready} {fit} />
