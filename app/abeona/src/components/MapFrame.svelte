<script lang="ts">
  /**
   * A Leaflet map on the basemap, as a still preview: it takes no gestures, so
   * it never steals a scroll or a swipe. A tap opens it full screen, where it
   * pans and zooms until it is closed (✕ or Escape). The same map instance
   * moves between the two, so nothing is drawn twice. Android's TripMap.kt
   * behaves the same.
   */
  import "leaflet/dist/leaflet.css";
  import { Maximize2, X } from "lucide-svelte";
  import { onMount } from "svelte";
  import { basemap } from "../lib/basemap";

  type Leaflet = typeof import("leaflet");

  interface Props {
    /** Size and shape of the preview, e.g. "h-72 rounded-3xl". */
    class?: string;
    /** What the map shows, for the button that opens it. */
    label: string;
    /** Called once the map exists: add layers and draw. */
    onready: (L: Leaflet, map: import("leaflet").Map) => void;
    /** Fit the camera to what is drawn; called again after each resize. */
    fit: () => void;
  }

  let { class: frameClass = "", label, onready, fit }: Props = $props();

  let container: HTMLDivElement | undefined = $state();
  let full = $state(false);
  let map: import("leaflet").Map | null = null;
  let zoom: import("leaflet").Control.Zoom | null = null;

  const HANDLERS = ["dragging", "touchZoom", "doubleClickZoom", "scrollWheelZoom", "boxZoom", "keyboard"] as const;

  function setInteractive(on: boolean) {
    if (!map) return;
    for (const name of HANDLERS) {
      const handler = map[name];
      if (on) handler.enable();
      else handler.disable();
    }
    if (on) zoom?.addTo(map);
    else zoom?.remove();
  }

  function open() {
    full = true;
  }

  function close() {
    full = false;
  }

  onMount(() => {
    let disposed = false;
    void (async () => {
      const L = await import("leaflet");
      if (disposed || !container || map) return;
      map = L.map(container, { zoomControl: false, attributionControl: true });
      zoom = L.control.zoom({ position: "bottomright" });
      const b = basemap();
      L.tileLayer(b.url, { attribution: b.attribution, subdomains: b.subdomains, maxZoom: b.maxZoom }).addTo(map);
      setInteractive(false);
      onready(L, map);
    })();
    return () => {
      disposed = true;
      map?.remove();
      map = null;
      zoom = null;
    };
  });

  $effect(() => {
    const on = full;
    setInteractive(on);
    // The container changed size: let Leaflet measure it, then fit again.
    requestAnimationFrame(() => {
      map?.invalidateSize();
      fit();
    });
    if (!on) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  });
</script>

<div class={full ? "fixed inset-0 z-[1000] bg-base-200" : `relative overflow-hidden bg-base-200 ${frameClass}`}>
  <div class="map-frame h-full w-full" bind:this={container}></div>
  {#if full}
    <button
      type="button"
      class="btn btn-circle absolute left-4 top-4 z-[1001] bg-card shadow-card"
      style="margin-top: env(safe-area-inset-top)"
      aria-label="Close the map"
      onclick={close}
    >
      <X class="h-5 w-5" />
    </button>
  {:else}
    <!-- Over the map, which would otherwise take the touch itself. -->
    <button type="button" class="absolute inset-0 z-[500] cursor-zoom-in" aria-label={`Open ${label} full screen`} onclick={open}>
      <span class="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-full bg-card/90 text-ink shadow-card">
        <Maximize2 class="h-4 w-4" />
      </span>
    </button>
  {/if}
</div>

<style>
  .map-frame :global(.leaflet-container) {
    font: inherit;
    background: var(--color-base-200);
  }
  .map-frame :global(.abeona-map-label) {
    border: none;
    border-radius: 999px;
    padding: 2px 10px;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-base-content);
    background: var(--ab-card);
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.12);
  }
  .map-frame :global(.abeona-map-label::before) {
    display: none;
  }
  .map-frame :global(.leaflet-control-zoom) {
    border: none;
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.12);
    border-radius: 999px;
    overflow: hidden;
  }
</style>
