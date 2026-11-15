<script lang="ts">
  import { ChevronDown, CircleAlert, ClipboardPaste, FileText, Ticket } from "lucide-svelte";
  import { onMount } from "svelte";
  import { describeError, transitPort } from "../../lib/data";
  import { intake, parseText, parseTicketTrips, type IntakeDraft } from "../../lib/intake/intake";
  import type { Parsed } from "../../lib/intake/parsed";
  import { browserPdfText } from "../../lib/intake/pdfText";
  import { takeSharedIntake } from "../../lib/intake/shared";
  import Banner from "../common/Banner.svelte";

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
  /** A quiet row until it is asked for; something being read, or an error, opens it. */
  let open = $state(false);
  const shown = $derived(open || busy !== null || error !== "");

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

<section class="rounded-3xl bg-card shadow-card">
  <button
    type="button"
    class="flex min-h-12 w-full items-center gap-3 px-4 text-left"
    aria-expanded={shown}
    aria-controls="intake-body"
    onclick={() => (open = !shown)}
  >
    <Ticket class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
    <span class="t-body min-w-0 flex-1 text-ink">Add from a ticket or share</span>
    <ChevronDown class="h-5 w-5 shrink-0 text-muted transition-transform {shown ? 'rotate-180' : ''}" aria-hidden="true" />
  </button>

  <div id="intake-body" class="space-y-2 px-4 pb-4" hidden={!shown}>
    {#if busy}
      <!-- Reading or looking up: nothing to type or choose until it is done. -->
      <div class="flex items-center justify-center gap-3 py-6" role="status">
        <span class="loading loading-spinner text-brand"></span>
        <span class="t-meta">{busy === "matching" ? "Looking up the connection…" : "Reading…"}</span>
      </div>
    {:else}
      <form class="space-y-2" onsubmit={readText}>
        <textarea
          class="textarea t-body min-h-24 w-full rounded-2xl border-transparent bg-well"
          placeholder={"Erlangen → Leuven\nFr. 14.11.2025\n\nRE19 (4901)\nAb 06:29 Erlangen, Gleis 4\n…"}
          bind:value={text}
          aria-label="Share text from DB Navigator"
        ></textarea>
        <div class="flex flex-wrap items-center gap-2">
          <button type="submit" class="btn btn-sm h-10 rounded-full border-transparent bg-well px-4 text-ink" disabled={!text.trim()}>
            <ClipboardPaste class="h-4 w-4" aria-hidden="true" />
            Read the text
          </button>
          <button type="button" class="btn btn-ghost btn-sm h-10 rounded-full px-4 text-brand" onclick={() => fileInput?.click()}>
            <FileText class="h-4 w-4" aria-hidden="true" />
            Choose a ticket PDF
          </button>
        </div>
        <p class="t-meta">DB Navigator's "Share" text, or a DB or Trenitalia ticket PDF. Read on this device.</p>
      </form>
    {/if}

    {#if error}
      <Banner tone="bad" icon={CircleAlert} title={error} />
    {/if}
  </div>
  <input bind:this={fileInput} type="file" accept="application/pdf,.pdf" class="hidden" onchange={onFile} />
</section>
