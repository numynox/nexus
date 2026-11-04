<script lang="ts">
  import { ExternalLink, X } from "lucide-svelte";
  import { onMount } from "svelte";
  import { describeError, downloadTicket } from "../../lib/data";

  interface Props {
    path: string;
    title: string;
    navigatorUrl: string;
    /** What the file is, for screen readers: "Ticket" or "BahnCard". */
    label?: string;
    /** A line under the header, e.g. where the file counts as proof. */
    note?: string;
    onClose: () => void;
  }

  let { path, title, navigatorUrl, label = "Ticket", note, onClose }: Props = $props();

  let pages: HTMLDivElement | undefined = $state();
  let loading = $state(true);
  let error = $state("");
  let imageUrl = $state("");

  async function renderPdf(blob: Blob, target: HTMLDivElement) {
    // pdf.js is heavy: loaded only when a ticket is opened.
    const pdfjs = await import("pdfjs-dist");
    const worker = await import("pdfjs-dist/build/pdf.worker.min.mjs?url");
    pdfjs.GlobalWorkerOptions.workerSrc = worker.default;

    const doc = await pdfjs.getDocument({ data: new Uint8Array(await blob.arrayBuffer()) }).promise;
    const width = Math.min(target.clientWidth || window.innerWidth, 900);
    const ratio = Math.max(window.devicePixelRatio || 1, 2);

    for (let n = 1; n <= doc.numPages; n++) {
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
    let revoked = "";
    const nav = navigator as Navigator & { wakeLock?: { request: (type: "screen") => Promise<{ release: () => Promise<void> }> } };
    nav.wakeLock?.request("screen").then((lock) => (wakeLock = lock)).catch(() => {});

    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";

    void (async () => {
      try {
        const blob = await downloadTicket(path);
        if (blob.type === "application/pdf" || path.toLowerCase().endsWith(".pdf")) {
          if (pages) await renderPdf(blob, pages);
        } else {
          imageUrl = URL.createObjectURL(blob);
          revoked = imageUrl;
        }
      } catch (e) {
        error = describeError(e);
      } finally {
        loading = false;
      }
    })();

    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      wakeLock?.release().catch(() => {});
      if (revoked) URL.revokeObjectURL(revoked);
    };
  });
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
  {#if note}
    <p class="border-b border-black/5 bg-[#fdf3dd] px-4 py-2 text-center text-sm text-[#5c4210]">{note}</p>
  {/if}
  <div class="flex-1 overflow-auto bg-white px-3 py-4">
    {#if loading}
      <div class="flex h-full items-center justify-center"><span class="loading loading-spinner loading-lg text-[#1f6f5c]"></span></div>
    {/if}
    {#if error}
      <p class="mx-auto max-w-md rounded-2xl bg-[#c4553b]/10 px-4 py-3 text-sm text-[#c4553b]">{error}</p>
    {/if}
    {#if imageUrl}
      <img src={imageUrl} alt={label} class="mx-auto block h-auto max-w-full bg-white" />
    {/if}
    <div bind:this={pages} class="space-y-4"></div>
  </div>
</div>
