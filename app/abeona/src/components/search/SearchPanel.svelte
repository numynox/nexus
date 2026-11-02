<script lang="ts">
  import { ArrowDownUp, ChevronDown, ChevronUp, Search } from "lucide-svelte";
  import { getBaseUrl } from "../../lib/config";
  import { describeError, searchJourneys } from "../../lib/data";
  import { formatLongDate } from "../../lib/format";
  import { getSavedSearch, setSavedSearch } from "../../lib/storage";
  import type { Journey } from "../../lib/transit";
  import Illustration from "../common/Illustration.svelte";
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

  function dayKey(j: Journey): string {
    const t = j.legs[0]?.from.planned;
    return t ? new Date(t).toDateString() : "";
  }

  function onSaved(id: string) {
    window.location.href = `${base}/trip?id=${encodeURIComponent(id)}`;
  }
</script>

<div class="space-y-8">
  <header class="pt-2">
    <p class="text-sm font-medium text-base-content/55">Where to?</p>
    <h1 class="mt-1 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Plan a trip</h1>
  </header>

  <form class="rounded-[2rem] bg-mint p-5 sm:p-7" onsubmit={search}>
    <div class="relative grid gap-3">
      <StationInput id="from" label="From" placeholder="Station" value={from} onSelect={(s) => (from = s)} />
      <StationInput id="to" label="To" placeholder="Station" value={to} onSelect={(s) => (to = s)} />
      <button
        type="button"
        class="btn btn-circle btn-sm absolute top-[3.1rem] right-4 z-10 border-base-300 bg-card shadow-card sm:right-5"
        aria-label="Swap stations"
        onclick={swap}
      >
        <ArrowDownUp class="h-4 w-4 text-primary" />
      </button>
    </div>

    <div class="mt-4 grid grid-cols-2 gap-3">
      <label class="block">
        <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Date</span>
        <input type="date" class="input h-12 w-full rounded-2xl border-base-300 bg-card font-medium" bind:value={date} required />
      </label>
      <label class="block">
        <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Time</span>
        <input type="time" class="input tabular h-12 w-full rounded-2xl border-base-300 bg-card font-medium" bind:value={time} required />
      </label>
    </div>

    <div class="mt-4 flex flex-wrap items-center gap-3">
      <div class="inline-flex rounded-full bg-card p-1 shadow-card" role="radiogroup" aria-label="Time refers to">
        {#each [{ v: false, l: "Depart" }, { v: true, l: "Arrive" }] as opt (opt.l)}
          <button
            type="button"
            role="radio"
            aria-checked={arriveBy === opt.v}
            class="rounded-full px-4 py-1.5 text-sm font-medium transition {arriveBy === opt.v ? 'bg-primary text-primary-content' : 'text-base-content/70'}"
            onclick={() => (arriveBy = opt.v)}
          >{opt.l}</button>
        {/each}
      </div>
      <div class="inline-flex rounded-full bg-card p-1 shadow-card" role="radiogroup" aria-label="Trains">
        {#each [{ v: false, l: "All trains" }, { v: true, l: "Regional only" }] as opt (opt.l)}
          <button
            type="button"
            role="radio"
            aria-checked={regionalOnly === opt.v}
            class="rounded-full px-4 py-1.5 text-sm font-medium transition {regionalOnly === opt.v ? 'bg-primary text-primary-content' : 'text-base-content/70'}"
            onclick={() => (regionalOnly = opt.v)}
          >{opt.l}</button>
        {/each}
      </div>
      <button type="submit" class="btn btn-primary ml-auto h-12 rounded-full px-7" disabled={!canSearch || searching}>
        {#if searching}<span class="loading loading-spinner loading-sm"></span>{:else}<Search class="h-4 w-4" />{/if}
        Search
      </button>
    </div>
  </form>

  {#if error}
    <p class="rounded-2xl bg-error/10 px-5 py-4 text-sm text-error" role="alert">{error}</p>
  {/if}

  {#if searching && journeys.length === 0}
    <div class="space-y-3" aria-busy="true">
      {#each [0, 1, 2, 3] as i (i)}<div class="h-32 animate-pulse rounded-3xl bg-base-200"></div>{/each}
    </div>
  {:else if journeys.length > 0}
    <section class="space-y-3 {searching ? 'opacity-50' : ''}">
      {#if previousCursor}
        <button class="btn btn-ghost btn-sm mx-auto flex rounded-full text-primary" onclick={() => page("earlier")} disabled={paging !== null}>
          {#if paging === "earlier"}<span class="loading loading-spinner loading-xs"></span>{:else}<ChevronUp class="h-4 w-4" />{/if}
          Earlier
        </button>
      {/if}
      {#each journeys as journey, i (i)}
        {#if i === 0 || dayKey(journey) !== dayKey(journeys[i - 1])}
          <p class="px-2 pt-2 text-xs font-semibold uppercase tracking-wider text-base-content/55">
            {formatLongDate(journey.legs[0]?.from.planned)}
          </p>
        {/if}
        <JourneyCard {journey} onChoose={() => (chosen = journey)} />
      {/each}
      {#if nextCursor}
        <button class="btn btn-ghost btn-sm mx-auto flex rounded-full text-primary" onclick={() => page("later")} disabled={paging !== null}>
          {#if paging === "later"}<span class="loading loading-spinner loading-xs"></span>{:else}<ChevronDown class="h-4 w-4" />{/if}
          Later
        </button>
      {/if}
    </section>
  {:else if searched}
    <div class="rounded-[2rem] bg-card p-8 text-center shadow-card">
      <p class="font-semibold text-ink">No connections found</p>
      <p class="mt-1 text-sm text-base-content/60">Try another time, or all trains instead of regional only.</p>
    </div>
  {:else}
    <div class="grid items-center gap-6 rounded-[2rem] bg-card p-6 shadow-card sm:p-8 md:grid-cols-[1fr_1.1fr]">
      <Illustration name="station" class="w-full" />
      <div>
        <h2 class="text-xl font-semibold text-ink">Find your connection</h2>
        <p class="mt-2 text-sm leading-relaxed text-base-content/65">
          Choose where you start and where you are going. Pick a connection to keep it — Abeona then follows its delays, platforms and changes for you.
        </p>
      </div>
    </div>
  {/if}
</div>

<SaveSheet journey={chosen} onClose={() => (chosen = null)} {onSaved} />
