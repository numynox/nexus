<script lang="ts">
  import { ArrowDownUp, ChevronDown, ChevronUp, CircleAlert, Flag, MapPin, Search } from "lucide-svelte";
  import { getBaseUrl } from "../../lib/config";
  import { describeError, searchJourneys } from "../../lib/data";
  import { formatLongDate } from "../../lib/format";
  import { getSavedSearch, setSavedSearch } from "../../lib/storage";
  import type { IntakeDraft } from "../../lib/intake/intake";
  import type { Journey } from "../../lib/transit";
  import Banner from "../common/Banner.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";
  import IntakeCard from "./IntakeCard.svelte";
  import JourneyCard from "./JourneyCard.svelte";
  import SaveSheet from "./SaveSheet.svelte";
  import StationInput from "./StationInput.svelte";

  const base = getBaseUrl();
  const saved = getSavedSearch();

  function pad(n: number) {
    return String(n).padStart(2, "0");
  }
  const now = new Date();
  // The next full quarter hour.
  const start = new Date(Math.ceil(now.getTime() / 900_000) * 900_000);

  let from = $state(saved.from);
  let to = $state(saved.to);
  let regionalOnly = $state(saved.regionalOnly);
  let date = $state(`${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}`);
  let time = $state(`${pad(start.getHours())}:${pad(start.getMinutes())}`);
  let arriveBy = $state(false);

  let journeys = $state<Journey[]>([]);
  let nextCursor = $state<string | null>(null);
  let previousCursor = $state<string | null>(null);
  let searching = $state(false);
  let paging = $state<"earlier" | "later" | null>(null);
  let searched = $state(false);
  let error = $state("");
  let chosen = $state<Journey | null>(null);
  /** Set while the save sheet shows a share or ticket that was read (intake). */
  let draft = $state<IntakeDraft | null>(null);
  let draftFile = $state<File | null>(null);
  /** The trips still to save after this one: the return of a ticket for both directions. */
  let queued = $state<IntakeDraft[]>([]);
  /** The first trip saved from the queue: where Abeona goes once all are saved. */
  let firstSaved: string | null = null;
  /** A new sheet for each queued trip, so its fields start from that trip. */
  let sheetKey = $state(0);

  function openDrafts(drafts: IntakeDraft[], file: File | null) {
    [draft, ...queued] = drafts;
    draftFile = file;
    firstSaved = null;
    chosen = draft?.match.journey ?? null;
  }

  function closeSheet() {
    chosen = null;
    draft = null;
    draftFile = null;
    queued = [];
    firstSaved = null;
  }

  const canSearch = $derived(!!from && !!to && from.id !== to.id && !!date && !!time);

  function swap() {
    [from, to] = [to, from];
  }

  function request() {
    return {
      from: from!.id,
      to: to!.id,
      time: new Date(`${date}T${time}`).toISOString(),
      arrive_by: arriveBy,
      products: (regionalOnly ? "regional" : "all") as "regional" | "all",
    };
  }

  async function search(event?: Event) {
    event?.preventDefault();
    if (!canSearch || searching) return;
    setSavedSearch({ from, to, regionalOnly });
    searching = true;
    error = "";
    try {
      const result = await searchJourneys(request());
      journeys = result.journeys;
      nextCursor = result.next_cursor;
      previousCursor = result.previous_cursor;
      searched = true;
    } catch (e) {
      error = describeError(e);
    } finally {
      searching = false;
    }
  }

  async function page(direction: "earlier" | "later") {
    const cursor = direction === "later" ? nextCursor : previousCursor;
    if (!cursor || paging || !canSearch) return;
    paging = direction;
    error = "";
    try {
      const result = await searchJourneys({ ...request(), cursor });
      if (direction === "later") {
        journeys = [...journeys, ...result.journeys];
        nextCursor = result.next_cursor;
      } else {
        journeys = [...result.journeys, ...journeys];
        previousCursor = result.previous_cursor;
      }
    } catch (e) {
      error = describeError(e);
    } finally {
      paging = null;
    }
  }

  // A compact two-way choice: the chosen side is the brand's soft surface.
  const segment = "t-meta flex h-9 items-center rounded-full px-3.5 transition-colors";
  const segmentOn = "bg-brand-soft font-semibold text-brand";

  function dayKey(j: Journey): string {
    const t = j.legs[0]?.from.planned;
    return t ? new Date(t).toDateString() : "";
  }

  function onSaved(id: string) {
    firstSaved ??= id;
    const [next, ...rest] = queued;
    if (next) {
      // The same ticket, the other direction: its own sheet, the same PDF.
      draft = next;
      queued = rest;
      chosen = next.match.journey;
      sheetKey++;
      return;
    }
    window.location.href = `${base}/trip?id=${encodeURIComponent(firstSaved)}`;
  }
</script>

<div class="mx-auto max-w-2xl space-y-3">
  <h1 class="t-headline px-1 pt-2 text-ink">Plan a trip</h1>

  <form class="space-y-2 rounded-3xl bg-card p-4 shadow-card" onsubmit={search}>
    <div class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
      <div class="space-y-2">
        <StationInput id="from" label="From" icon={MapPin} value={from} onSelect={(s) => (from = s)} />
        <StationInput id="to" label="To" icon={Flag} value={to} onSelect={(s) => (to = s)} />
      </div>
      <button type="button" class="btn btn-circle btn-ghost" aria-label="Swap stations" onclick={swap}>
        <ArrowDownUp class="h-6 w-6 text-brand" aria-hidden="true" />
      </button>
    </div>

    <div class="grid grid-cols-2 gap-2">
      <input type="date" class="input t-body h-12 w-full rounded-2xl border-transparent bg-well font-medium text-ink" aria-label="Date" bind:value={date} required />
      <input type="time" class="input t-body tabular h-12 w-full rounded-2xl border-transparent bg-well font-medium text-ink" aria-label="Time" bind:value={time} required />
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <div class="inline-flex rounded-full bg-well p-0.5" role="radiogroup" aria-label="Time refers to">
        {#each [{ v: false, l: "Depart" }, { v: true, l: "Arrive" }] as opt (opt.l)}
          <button
            type="button"
            role="radio"
            aria-checked={arriveBy === opt.v}
            class="{segment} {arriveBy === opt.v ? segmentOn : ''}"
            onclick={() => (arriveBy = opt.v)}
          >{opt.l}</button>
        {/each}
      </div>
      <div class="inline-flex rounded-full bg-well p-0.5" role="radiogroup" aria-label="Trains">
        {#each [{ v: false, l: "All", a: "All trains" }, { v: true, l: "Regional", a: "Regional only" }] as opt (opt.l)}
          <button
            type="button"
            role="radio"
            aria-checked={regionalOnly === opt.v}
            aria-label={opt.a}
            class="{segment} {regionalOnly === opt.v ? segmentOn : ''}"
            onclick={() => (regionalOnly = opt.v)}
          >{opt.l}</button>
        {/each}
      </div>
      <button type="submit" class="btn btn-primary h-12 w-full rounded-full px-6 sm:ml-auto sm:w-auto" disabled={!canSearch || searching}>
        {#if searching}<span class="loading loading-spinner loading-sm"></span>{:else}<Search class="h-5 w-5" aria-hidden="true" />{/if}
        Search
      </button>
    </div>
  </form>

  {#if error}
    <Banner tone="bad" icon={CircleAlert} title={error} />
  {/if}

  <IntakeCard onDrafts={openDrafts} />

  {#if searching && journeys.length === 0}
    <div class="space-y-3" aria-busy="true">
      {#each [0, 1, 2, 3] as i (i)}<div class="h-[5.5rem] animate-pulse rounded-3xl bg-well"></div>{/each}
    </div>
  {:else if journeys.length > 0}
    <section class="space-y-3 {searching ? 'opacity-50' : ''}" aria-label="Connections">
      {#if previousCursor}
        <button class="btn btn-ghost btn-sm mx-auto flex h-10 rounded-full text-brand" onclick={() => page("earlier")} disabled={paging !== null}>
          {#if paging === "earlier"}<span class="loading loading-spinner loading-xs"></span>{:else}<ChevronUp class="h-4 w-4" aria-hidden="true" />{/if}
          Earlier
        </button>
      {/if}
      {#each journeys as journey, i (i)}
        {#if i === 0 || dayKey(journey) !== dayKey(journeys[i - 1])}
          <SectionHeader title={formatLongDate(journey.legs[0]?.from.planned)} />
        {/if}
        <JourneyCard {journey} fromName={from?.name} toName={to?.name} onChoose={() => (chosen = journey)} />
      {/each}
      {#if nextCursor}
        <button class="btn btn-ghost btn-sm mx-auto flex h-10 rounded-full text-brand" onclick={() => page("later")} disabled={paging !== null}>
          {#if paging === "later"}<span class="loading loading-spinner loading-xs"></span>{:else}<ChevronDown class="h-4 w-4" aria-hidden="true" />{/if}
          Later
        </button>
      {/if}
    </section>
  {:else if searched}
    <div class="rounded-3xl bg-card p-4 shadow-card">
      <p class="t-title text-ink">No connections found</p>
      <p class="t-meta mt-1">Try another time, or all trains instead of regional only.</p>
    </div>
  {/if}
</div>

{#key sheetKey}
  <SaveSheet journey={chosen} intake={draft} ticketFile={draftFile} more={queued.length} onClose={closeSheet} {onSaved} />
{/key}
