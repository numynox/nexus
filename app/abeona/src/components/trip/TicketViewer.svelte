<script lang="ts">
  import { ExternalLink, FileText, QrCode, X } from "lucide-svelte";
  import { onMount, untrack } from "svelte";
  import { describeError, downloadTicket } from "../../lib/data";
  import { extractTicketCode } from "../../lib/ticketCode";

  interface Props {
    path: string;
    title: string;
    navigatorUrl: string;
    /** What the file is, for screen readers: "Ticket" or "BahnCard". */
    label?: string;
    /** A line under the header, e.g. where the file counts as proof. */
    note?: string;
    /** More to show at the gate with it, one tap apart: the BahnCards the ticket was booked with. */
    extras?: { key: string; label: string; path: string }[];
    onClose: () => void;
  }

  let { path, title, navigatorUrl, label = "Ticket", note, extras = [], onClose }: Props = $props();

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
    let wakeLock: { release: () => Promise<void> } | null = null;
    const nav = navigator as Navigator & { wakeLock?: { request: (type: "screen") => Promise<{ release: () => Promise<void> }> } };
    nav.wakeLock?.request("screen").then((lock) => (wakeLock = lock)).catch(() => {});

    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";

    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      wakeLock?.release().catch(() => {});
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

<div class="fixed inset-0 z-[100] flex flex-col bg-white text-[#12313a]" role="dialog" aria-modal="true" aria-label={label}>
  <header class="flex items-center gap-3 border-b border-black/5 px-4 py-3" style="padding-top: max(0.75rem, env(safe-area-inset-top))">
    <button class="btn btn-circle btn-ghost btn-sm text-[#12313a]" aria-label="Close {label.toLowerCase()}" onclick={onClose}>
      <X class="h-5 w-5" />
    </button>
    <p class="min-w-0 flex-1 truncate font-semibold">{title}</p>
    <a href={navigatorUrl} target="_blank" rel="noopener" class="btn btn-sm rounded-full border-[#1f6f5c]/30 bg-white text-[#1f6f5c]">
      <ExternalLink class="h-4 w-4" /> {label === "Ticket" ? "DB Navigator" : "Open in DB Navigator"}
    </a>
  </header>
  {#if documents.length > 1}
    <div class="flex gap-2 overflow-x-auto border-b border-black/5 px-4 py-2">
      {#each documents as d (d.key)}
        <button
          type="button"
          class="btn btn-sm shrink-0 rounded-full {current === d.key ? 'bg-[#1f6f5c] text-white' : 'border-[#12313a]/20 bg-white text-[#12313a]'}"
          aria-pressed={current === d.key}
          onclick={() => (current = d.key)}
        >{d.label}</button>
      {/each}
    </div>
  {/if}
  {#if note}
    <p class="border-b border-black/5 bg-[#fdf3dd] px-4 py-2 text-center text-sm text-[#5c4210]">{note}</p>
  {/if}
  <div class="flex-1 overflow-auto bg-white px-3 py-4">
    {#if loading && !codeUrl}
      <div class="flex h-full items-center justify-center"><span class="loading loading-spinner loading-lg text-[#1f6f5c]"></span></div>
    {/if}
    {#if error}
      <p class="mx-auto max-w-md rounded-2xl bg-[#c4553b]/10 px-4 py-3 text-sm text-[#c4553b]">{error}</p>
    {/if}
    {#if imageUrl}
      <img src={imageUrl} alt={label} class="mx-auto block h-auto max-w-full bg-white" />
    {/if}
    {#if codeUrl && !showPdf}
      <div class="flex min-h-full flex-col items-center justify-center gap-6">
        <!-- Pixelated: every module of the code stays sharp when scaled up. -->
        <img src={codeUrl} alt="Ticket barcode" class="block aspect-square w-full max-w-[min(32rem,70vh)] bg-white p-4 [image-rendering:pixelated]" />
        <button type="button" class="btn rounded-full border-[#12313a]/20 bg-white text-[#12313a]" onclick={() => (showPdf = true)}>
          <FileText class="h-4 w-4" /> Show PDF
        </button>
      </div>
    {/if}
    <div bind:this={pages} class="space-y-4" class:hidden={codeUrl && !showPdf}></div>
    {#if codeUrl && showPdf}
      <div class="sticky bottom-2 mt-4 flex justify-center">
        <button type="button" class="btn rounded-full bg-[#dff3ec] text-[#12313a] shadow-card" onclick={() => (showPdf = false)}>
          <QrCode class="h-4 w-4" /> Show code
        </button>
      </div>
    {/if}
  </div>
</div>
