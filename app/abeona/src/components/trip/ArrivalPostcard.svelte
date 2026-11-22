<script lang="ts">
  /**
   * The postcard of a trip that has arrived (decisions/2026-11-22-abeona-arrival-postcard.md):
   * the route and the day, then what it added up to as figures, icon over
   * value over a small label, from abeona_trip_summary. Below compensation,
   * which always comes first; the trip page leaves it out while something
   * is broken. Share makes a 1080 × 1350 image of it, with the arrival scene
   * on top, and hands it to the system's share sheet, else downloads it.
   * Nothing is posted anywhere by itself.
   */
  import { Flag, Gauge, Leaf, Route, Share2, TrainFront } from "lucide-svelte";
  import { onMount, tick } from "svelte";
  import { fetchTripSummary, type Trip, type TripSummary } from "../../lib/data";
  import { formatLongDate, legLabel } from "../../lib/format";
  import { drawPostcard, postcardFigures, type FigureKind } from "../../lib/postcard";
  import { trainKind } from "../../lib/scene";
  import type { Leg } from "../../lib/transit";
  import type { IconComponent } from "../common/icon";
  import Scene from "../common/Scene.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";

  interface Props {
    trip: Trip;
    /** The legs as travelled: the scene is the last ride's train, at the destination, at its arrival. */
    legs: Leg[];
  }

  let { trip, legs }: Props = $props();

  let summary = $state<TripSummary | null>(null);
  let loading = $state(true);
  /** The image, made ahead: the share sheet only opens straight from the tap. */
  let file = $state<File | null>(null);
  let drawing = $state(false);
  let sceneBox: HTMLDivElement | undefined = $state();
  let figureList: HTMLUListElement | undefined = $state();

  const route = $derived(`${trip.origin_name} → ${trip.destination_name}`);
  const date = $derived(formatLongDate(trip.planned_departure));
  const figures = $derived(summary ? postcardFigures(summary) : []);
  const lastRide = $derived(legs.findLast((l) => l.mode !== "walk") ?? null);
  const arrivedAt = $derived(lastRide ? Date.parse(lastRide.to.current ?? lastRide.to.planned ?? "") : NaN);

  const icons: Record<FigureKind, IconComponent> = { distance: Route, on_board: TrainFront, co2: Leaf, speed: Gauge, arrival: Flag };
  const tones = { ink: "text-ink", watch: "text-watch-ink", ok: "text-ok-ink" } as const;

  /** Again after "What happened" changed the trip: the delay at the end may differ. */
  export async function reload() {
    try {
      summary = await fetchTripSummary(trip.id);
    } catch {
      // Quietly: without its figures there is no postcard, and nothing else depends on it.
      summary = null;
    } finally {
      loading = false;
    }
    file = null;
    if (summary) void prepare();
  }

  onMount(() => {
    void reload();
  });

  const frame = () => new Promise((resolve) => requestAnimationFrame(resolve));

  /** Draws the image from the scene (mounted off-screen just for this, so it does not animate the rest of the time) and the card's own icons. */
  async function prepare() {
    if (drawing) return;
    drawing = true;
    try {
      await tick();
      // The scene reads the theme once mounted: let it draw again first.
      await frame();
      await frame();
      const scene = sceneBox?.querySelector("svg");
      if (!scene || figures.length === 0) return;
      const iconOf = (i: number) => figureList?.children[i]?.querySelector("svg") ?? null;
      const blob = await drawPostcard({ scene, route, date, figures, icons: figures.map((_, i) => iconOf(i)) });
      const day = new Date(trip.planned_departure).toLocaleDateString("sv-SE");
      file = new File([blob], `abeona-${day}.png`, { type: "image/png" });
    } catch {
      // Without the image, Share draws it again when tapped.
      file = null;
    } finally {
      drawing = false;
    }
  }

  function download(f: File) {
    const url = URL.createObjectURL(f);
    const a = document.createElement("a");
    a.href = url;
    a.download = f.name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30_000);
  }

  async function share() {
    if (!file) await prepare();
    const f = file;
    if (!f) return;
    const data: ShareData = { files: [f], title: route };
    if (navigator.canShare?.(data)) {
      try {
        await navigator.share(data);
      } catch (e) {
        // Closing the share sheet is no reason to download it.
        if ((e as DOMException)?.name !== "AbortError") download(f);
      }
    } else {
      download(f);
    }
  }
</script>

{#if summary && figures.length > 0}
  <section class="space-y-2" id="postcard">
    <SectionHeader title="Postcard">
      <button
        type="button"
        class="btn btn-ghost btn-circle btn-sm -mr-1 h-10 w-10 text-brand"
        aria-label="Share postcard"
        title="Share"
        onclick={share}
      >
        <Share2 class="h-6 w-6" aria-hidden="true" />
      </button>
    </SectionHeader>
    <div class="rounded-3xl bg-card p-4 shadow-card">
      <!-- "Frankfurt(Main)Hbf" may wrap after its bracket rather than anywhere. -->
      <p class="t-title text-ink [overflow-wrap:anywhere]">{route.replace(/([)/])(?=\S)/g, "$1\u200b")}</p>
      <p class="t-meta">{date}</p>
      <ul bind:this={figureList} class="mt-3 grid grid-cols-[repeat(auto-fill,minmax(7.5rem,1fr))] gap-x-3 gap-y-4">
        {#each figures as figure (figure.kind)}
          {@const Icon = icons[figure.kind]}
          <li class="min-w-0">
            <Icon class="h-5 w-5 {figure.tone === 'ink' ? 'text-brand' : tones[figure.tone]}" aria-hidden="true" />
            <p class="t-title tabular mt-1 [overflow-wrap:anywhere] {tones[figure.tone]}">{figure.value}</p>
            <p class="t-meta">{figure.label}</p>
          </li>
        {/each}
      </ul>
    </div>
  </section>
{:else if loading}
  <div class="h-40 animate-pulse rounded-3xl bg-well motion-reduce:animate-none" aria-hidden="true"></div>
{/if}

{#if drawing}
  <!-- The scene for the image only: off-screen, out of the accessibility tree, gone once drawn. -->
  <div bind:this={sceneBox} class="pointer-events-none fixed top-0 -left-[200vw] w-[400px]" aria-hidden="true" inert>
    <Scene
      state="arrived"
      size="full"
      train={lastRide ? trainKind(lastRide.product, legLabel(lastRide), lastRide.mode === "bus") : undefined}
      at={Number.isNaN(arrivedAt) ? undefined : arrivedAt}
      lat={lastRide?.to.lat}
      lon={lastRide?.to.lon}
    />
  </div>
{/if}
