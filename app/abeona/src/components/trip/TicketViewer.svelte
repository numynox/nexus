<script lang="ts">
  /**
   * The ticket at the gate (abeona-design.md §9, Ticket): white, the screen
   * kept on, the code first and the PDF on request, the BahnCards it needs
   * one tap apart. Under the code, or beside it with the phone sideways,
   * the rides it is for with their seats.
   */
  import { ExternalLink, FileText, LogOut, QrCode, X } from "lucide-svelte";
  import { onMount, untrack } from "svelte";
  import { describeError, downloadTicket } from "../../lib/data";
  import { extractTicketCode } from "../../lib/ticketCode";
  import type { Leg } from "../../lib/transit";
  import { keepScreenOn } from "../../lib/wakeLock";
  import LegChip from "../common/LegChip.svelte";
  import Seat from "../common/Seat.svelte";
  import StopRow from "../common/StopRow.svelte";

  interface Props {
    path: string;
    title: string;
    /** "Open DB Navigator" beside the ticket; left out for a BahnCard, which DB Navigator cannot open directly. */
    navigatorUrl?: string;
    /** What the file is, for screen readers: "Ticket" or "BahnCard". */
    label?: string;
    /** A line under the header, e.g. where the file counts as proof. */
    note?: string;
    /** More to show at the gate with it, one tap apart: the BahnCards the ticket was booked with. */
    extras?: { key: string; label: string; path: string }[];
    /** The rides the ticket is for: shown with their seats beside the ticket itself, not with a BahnCard. */
    rides?: Leg[];
    onClose: () => void;
  }

  let { path, title, navigatorUrl, label = "Ticket", note, extras = [], rides = [], onClose }: Props = $props();

  /** Which file is shown: the main one (`path`), or one of `extras`. */
  let current = $state("main");
  const documents = $derived([{ key: "main", label, path }, ...extras]);
  const shownPath = $derived(documents.find((d) => d.key === current)?.path ?? path);

  let pages: HTMLDivElement | undefined = $state();
  let loading = $state(true);
  let error = $state("");
  let imageUrl = $state("");
  /** The PDF's barcode on its own, shown first; the whole PDF on request. */
  let codeUrl = $state("");
  let showPdf = $state(false);
  /** The rides go with the ticket itself; a BahnCard tab shows the card alone. */
  const showRides = $derived(rides.length > 0 && current === "main");
  /** What is above the code (top bar, tabs, the note), so that sideways the code fits the height. */
  const chromeRem = $derived(5.5 + (documents.length > 1 ? 3.25 : 0) + (note ? 2.25 : 0));

  async function renderPdf(blob: Blob, target: HTMLDivElement, current: () => boolean) {
    // pdf.js is heavy: loaded only when a ticket is opened.
    const pdfjs = await import("pdfjs-dist");
    const worker = await import("pdfjs-dist/build/pdf.worker.min.mjs?url");
    pdfjs.GlobalWorkerOptions.workerSrc = worker.default;

    const doc = await pdfjs.getDocument({ data: new Uint8Array(await blob.arrayBuffer()) }).promise;
    // The code first, so the PDF does not flash up before it.
    const code = (await extractTicketCode(doc, pdfjs.OPS).catch(() => null)) ?? "";
    if (!current()) return;
    codeUrl = code;
    const width = Math.min(target.clientWidth || window.innerWidth, 900);
    const ratio = Math.max(window.devicePixelRatio || 1, 2);

    for (let n = 1; n <= doc.numPages; n++) {
      if (!current()) return;
      const page = await doc.getPage(n);
      const base = page.getViewport({ scale: 1 });
      const scale = (width / base.width) * ratio;
      const viewport = page.getViewport({ scale });
      const canvas = document.createElement("canvas");
      canvas.width = Math.floor(viewport.width);
      canvas.height = Math.floor(viewport.height);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${Math.floor(viewport.height / ratio)}px`;
      canvas.className = "mx-auto block bg-white shadow-sm";
      target.appendChild(canvas);
      // White behind every page: QR and Aztec codes need the contrast.
      await page.render({ canvas, viewport, background: "white" }).promise;
    }
  }

  onMount(() => {
    // The screen stays on at the gate, also after a look at another app.
    const release = keepScreenOn();

    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";

    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      release();
      if (imageUrl) URL.revokeObjectURL(imageUrl);
    };
  });

  // The file shown, again whenever another is chosen.
  let loadId = 0;
  $effect(() => {
    const target = shownPath;
    untrack(() => load(target));
  });

  function load(target: string) {
    const id = ++loadId;
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    imageUrl = "";
    codeUrl = "";
    showPdf = false;
    error = "";
    loading = true;
    pages?.replaceChildren();
    void (async () => {
      try {
        const blob = await downloadTicket(target);
        if (id !== loadId) return;
        if (blob.type === "application/pdf" || target.toLowerCase().endsWith(".pdf")) {
          if (pages) await renderPdf(blob, pages, () => id === loadId);
        } else {
          imageUrl = URL.createObjectURL(blob);
        }
      } catch (e) {
        if (id === loadId) error = describeError(e);
      } finally {
        if (id === loadId) loading = false;
      }
    })();
  }
</script>

{#snippet rideList()}
  <!-- Which train, from where and when, and where to sit: what the traveller looks for with the ticket open. -->
  <ul class="divide-y divide-line" aria-label="Rides">
    {#each rides as ride, i (i)}
      <li class="py-1.5">
        <StopRow planned={ride.from.planned} current={ride.from.current} name={ride.from.name} platformPlanned={ride.from.planned_platform} platformCurrent={ride.from.platform}>
          {#snippet line()}<LegChip leg={ride} />{/snippet}
          <Seat coach={ride.coach} seats={ride.seats} class="text-ink" />
        </StopRow>
        <StopRow planned={ride.to.planned} current={ride.to.current} name={ride.to.name} platformPlanned={ride.to.planned_platform} platformCurrent={ride.to.platform} muted>
          {#snippet mark()}<LogOut class="h-4 w-4" aria-label="Get off at" />{/snippet}
        </StopRow>
      </li>
    {/each}
  </ul>
{/snippet}

<div
  data-theme="abeona"
  class="fixed inset-0 z-[100] flex flex-col bg-white text-ink"
  style="padding-left: env(safe-area-inset-left); padding-right: env(safe-area-inset-right); padding-bottom: env(safe-area-inset-bottom)"
  role="dialog"
  aria-modal="true"
  aria-label={label}
>
  <header class="flex items-center gap-3 border-b border-line px-4 py-3" style="padding-top: max(0.75rem, env(safe-area-inset-top))">
    <button class="btn btn-circle btn-ghost btn-sm text-ink" aria-label="Close {label.toLowerCase()}" onclick={onClose}>
      <X class="h-5 w-5" />
    </button>
    <p class="t-title min-w-0 flex-1 truncate">{title}</p>
    {#if navigatorUrl}
      <a href={navigatorUrl} target="_blank" rel="noopener" class="btn btn-sm rounded-full border-line bg-white text-brand">
        <ExternalLink class="h-4 w-4" /> DB Navigator
      </a>
    {/if}
  </header>
  {#if documents.length > 1}
    <div class="flex gap-2 overflow-x-auto border-b border-line px-4 py-2">
      {#each documents as d (d.key)}
        <button
          type="button"
          class="btn btn-sm shrink-0 rounded-full {current === d.key ? 'border-none bg-brand-soft text-brand' : 'border-line bg-white text-ink'}"
          aria-pressed={current === d.key}
          onclick={() => (current = d.key)}
        >{d.label}</button>
      {/each}
    </div>
  {/if}
  {#if note}
    <p class="t-meta border-b border-line bg-watch-soft px-4 py-2 text-center text-watch-ink">{note}</p>
  {/if}
  <div class="flex-1 overflow-auto bg-white px-3 py-4">
    {#if loading && !codeUrl}
      <div class="flex h-full items-center justify-center"><span class="loading loading-spinner loading-lg text-brand"></span></div>
    {/if}
    {#if error}
      <p class="mx-auto max-w-md rounded-2xl bg-bad-soft px-4 py-3 text-sm text-bad-ink">{error}</p>
    {/if}
    {#if imageUrl}
      <img src={imageUrl} alt={label} class="mx-auto block h-auto max-w-full bg-white" />
    {/if}
    {#if codeUrl && !showPdf}
      <!-- Upright: the code, the PDF button, the rides. Sideways: the code as tall as it fits on the left, the rides and the button on the right. -->
      <div
        class="flex min-h-full flex-col items-center justify-center gap-6 {showRides ? 'landscape:grid landscape:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] landscape:grid-rows-[1fr_auto] landscape:items-center landscape:gap-x-6 landscape:gap-y-3' : ''}"
        style="--code-max: calc(100dvh - {chromeRem}rem)"
      >
        <!-- Pixelated: every module of the code stays sharp when scaled up. -->
        <img
          src={codeUrl}
          alt="Ticket barcode"
          class="block aspect-square w-full max-w-[min(32rem,70vh)] bg-white p-4 [image-rendering:pixelated] {showRides ? 'landscape:row-span-2 landscape:max-w-[min(32rem,var(--code-max))] landscape:justify-self-center' : ''}"
        />
        <button type="button" class="btn rounded-full border-line bg-white text-ink {showRides ? 'landscape:col-start-2 landscape:row-start-2 landscape:justify-self-start' : ''}" onclick={() => (showPdf = true)}>
          <FileText class="h-4 w-4" /> Show PDF
        </button>
        {#if showRides}
          <div class="w-full max-w-md landscape:col-start-2 landscape:row-start-1 landscape:self-end">{@render rideList()}</div>
        {/if}
      </div>
    {/if}
    <div bind:this={pages} class="space-y-4" class:hidden={codeUrl && !showPdf}></div>
    {#if showRides && !loading && !error && !(codeUrl && !showPdf)}
      <!-- No code on its own (an image, or the whole PDF): the rides after the ticket. -->
      <div class="mx-auto mt-4 w-full max-w-md">{@render rideList()}</div>
    {/if}
    {#if codeUrl && showPdf}
      <div class="sticky bottom-2 mt-4 flex justify-center">
        <button type="button" class="btn rounded-full border-none bg-brand-soft text-brand shadow-card" onclick={() => (showPdf = false)}>
          <QrCode class="h-4 w-4" /> Show code
        </button>
      </div>
    {/if}
  </div>
</div>
