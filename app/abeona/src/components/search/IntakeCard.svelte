<script lang="ts">
  import { ClipboardPaste, FileText, Ticket } from "lucide-svelte";
  import { onMount } from "svelte";
  import { describeError, transitPort } from "../../lib/data";
  import { intake, parseText, parseTicketTrips, type IntakeDraft } from "../../lib/intake/intake";
  import type { Parsed } from "../../lib/intake/parsed";
  import { browserPdfText } from "../../lib/intake/pdfText";
  import { takeSharedIntake } from "../../lib/intake/shared";

  interface Props {
    /**
     * A share or ticket was read and looked up: open the save sheet with it.
     * A ticket for both directions gives two drafts, saved one after the other.
     */
    onDrafts: (drafts: IntakeDraft[], file: File | null) => void;
  }

  let { onDrafts }: Props = $props();

  let text = $state("");
  let busy = $state<"reading" | "matching" | null>(null);
  let error = $state("");
  let fileInput: HTMLInputElement | undefined = $state();

  async function run(source: "share" | "pdf", read: () => Promise<Parsed[]>, file: File | null) {
    if (busy) return;
    error = "";
    busy = "reading";
    try {
      const trips = (await read()).filter((p) => p.legs.length > 0);
      if (trips.length === 0) {
        error =
          source === "pdf"
            ? "Abeona could not find a journey in this PDF. It reads DB Online-Tickets and Trenitalia tickets."
            : "Abeona could not find a journey in this text. Paste what DB Navigator's \"Share\" gives you.";
        return;
      }
      busy = "matching";
      const drafts: IntakeDraft[] = [];
      for (const parsed of trips) drafts.push(await intake(parsed, source, transitPort));
      onDrafts(drafts, file);
      text = "";
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = null;
    }
  }

  function readText(event?: Event) {
    event?.preventDefault();
    const value = text.trim();
    if (value) {
      void run(
        "share",
        async () => {
          const parsed = parseText(value);
          return parsed ? [parsed] : [];
        },
        null,
      );
    }
  }

  function readPdf(file: File) {
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      error = "Choose the ticket as a PDF.";
      return;
    }
    void run(
      "pdf",
      async () => {
        const extracted = await browserPdfText(file);
        return parseTicketTrips(extracted);
      },
      file,
    );
  }

  function onFile(event: Event) {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = "";
    if (file) readPdf(file);
  }

  // Something shared to the installed app (Web Share Target, via the service worker).
  onMount(() => {
    void takeSharedIntake().then((shared) => {
      if (!shared) return;
      if (shared.file) readPdf(shared.file);
      else if (shared.text) {
        text = shared.text;
        readText();
      }
    });
  });
</script>

<section class="rounded-[2rem] bg-card p-5 shadow-card sm:p-7" aria-labelledby="intake-title">
  <div class="flex items-start gap-3">
    <span class="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-mint text-primary"><Ticket class="h-5 w-5" /></span>
    <div>
      <h2 id="intake-title" class="font-semibold text-ink">Add from a ticket or a share</h2>
      <p class="mt-0.5 text-sm text-base-content/60">
        Paste what DB Navigator's "Share" gives you, or choose a DB or Trenitalia ticket PDF. It is read on this device.
      </p>
    </div>
  </div>

  {#if busy}
    <!-- Reading or looking up: nothing to type or choose until it is done. -->
    <div class="mt-6 flex flex-col items-center gap-3 py-6" role="status">
      <span class="loading loading-spinner loading-lg text-primary"></span>
      <span class="text-sm text-base-content/60">{busy === "matching" ? "Looking up the connection…" : "Reading…"}</span>
    </div>
  {:else}
    <form class="mt-4 space-y-3" onsubmit={readText}>
      <textarea
        class="textarea min-h-24 w-full rounded-2xl bg-base-100 text-sm"
        placeholder={"Erlangen → Leuven\nFr. 14.11.2025\n\nRE19 (4901)\nAb 06:29 Erlangen, Gleis 4\n…"}
        bind:value={text}
        aria-label="Share text from DB Navigator"
      ></textarea>
      <div class="flex flex-wrap items-center gap-2">
        <button type="submit" class="btn btn-primary rounded-full" disabled={!text.trim()}>
          <ClipboardPaste class="h-4 w-4" />
          Read the text
        </button>
        <button type="button" class="btn btn-ghost rounded-full text-primary" onclick={() => fileInput?.click()}>
          <FileText class="h-4 w-4" />
          Choose a ticket PDF
        </button>
      </div>
    </form>
  {/if}
  <input bind:this={fileInput} type="file" accept="application/pdf,.pdf" class="hidden" onchange={onFile} />

  {#if error}
    <p class="mt-3 rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">{error}</p>
  {/if}
</section>
