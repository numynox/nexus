<script lang="ts">
  import { ChevronLeft, ChevronRight } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import { countryFlag, countryName } from "../../lib/basemap";
  import {
    describeError,
    fetchTripYears,
    fetchYearStatistics,
    fetchYearTravelMap,
    type Statistics,
    type TravelMapLine,
  } from "../../lib/data";
  import { formatNumber, formatShortDate } from "../../lib/format";
  import Illustration from "../common/Illustration.svelte";
  import ClaimsSection from "./ClaimsSection.svelte";
  import MonthChart from "./MonthChart.svelte";
  import TravelMap from "./TravelMap.svelte";

  const base = getBaseUrl();
  /** Germany from Sylt to the Alps, roughly, as the crow flies. */
  const GERMANY_KM = 876;

  let years = $state<number[]>([new Date().getFullYear()]);
  let year = $state(new Date().getFullYear());
  let stats = $state<Statistics | null>(null);
  let lines = $state<TravelMapLine[]>([]);
  let loading = $state(true);
  let error = $state("");
  let track: HTMLDivElement | undefined = $state();

  const hours = $derived(stats ? stats.minutes_on_train / 60 : 0);
  const products = $derived(
    stats
      ? Object.entries(stats.products)
          .map(([name, n]) => ({ name, n: Number(n) }))
          .sort((a, b) => b.n - a.n)
          .slice(0, 6)
      : [],
  );
  const productMax = $derived(Math.max(1, ...products.map((p) => p.n)));
  const punctual = $derived(stats?.punctual_share === null || !stats ? null : Math.round(stats.punctual_share * 100));
  const isCurrent = $derived(year === new Date().getFullYear());

  async function load(y: number) {
    loading = true;
    error = "";
    try {
      const [s, map] = await Promise.all([fetchYearStatistics(y), fetchYearTravelMap(y).catch(() => [])]);
      stats = s;
      lines = map;
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
    track?.scrollTo({ left: 0 });
  }

  function choose(y: number) {
    year = y;
    void load(y);
  }

  // Steps to the neighbouring card by position; relative smooth scrolls
  // fight scroll snapping when the button is pressed quickly.
  let targetCard = 0;
  function scrollBy(direction: 1 | -1) {
    if (!track) return;
    const cards = Array.from(track.children) as HTMLElement[];
    const pad = cards[0]?.offsetLeft ?? 0;
    const current = cards.findIndex((c) => c.offsetLeft - pad >= track!.scrollLeft - 4);
    targetCard = Math.min(cards.length - 1, Math.max(0, Math.max(current, 0) + direction));
    track.scrollTo({ left: cards[targetCard].offsetLeft - pad, behavior: "smooth" });
  }

  onMount(() => {
    void load(year);
    fetchTripYears().then((list) => (years = list)).catch(() => {});
  });

  /** A beech absorbs roughly 12.5 kg of CO₂ a year (an often-quoted rule of thumb). */
  function trees(kg: number): string {
    const n = Math.round(kg / 12.5);
    return n >= 1 ? `About what ${formatNumber(n)} ${n === 1 ? "beech tree takes" : "beech trees take"} up in a year.` : "";
  }

  function acrossGermany(km: number): string {
    const times = km / GERMANY_KM;
    if (times >= 1.15) return `That is ${formatNumber(times, times < 10 ? 1 : 0)} times the length of Germany.`;
    if (times >= 0.9) return "That is once the whole length of Germany.";
    if (km > 0) return `That is ${Math.round(times * 100)}% of the way from the North Sea to the Alps.`;
    return "";
  }
</script>

<div class="space-y-6">
  <header class="flex flex-wrap items-end justify-between gap-4 pt-2">
    <div>
      <p class="text-sm font-medium text-base-content/55">Your household on rails</p>
      <h1 class="mt-1 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Year in review</h1>
    </div>
    <div class="flex flex-wrap gap-1.5 rounded-full bg-base-200 p-1" role="radiogroup" aria-label="Year">
      {#each years as y (y)}
        <button
          role="radio"
          aria-checked={y === year}
          class="tabular rounded-full px-4 py-1.5 text-sm font-semibold transition {y === year ? 'bg-card text-primary shadow-card' : 'text-base-content/60'}"
          onclick={() => choose(y)}
        >{y}</button>
      {/each}
    </div>
  </header>

  {#if error}
    <p class="rounded-2xl bg-error/10 px-5 py-4 text-sm text-error" role="alert">{error}</p>
  {/if}

  {#if loading}
    <div class="flex gap-4 overflow-hidden" aria-busy="true">
      {#each [0, 1, 2] as i (i)}<div class="h-[28rem] w-[85%] shrink-0 animate-pulse rounded-[2rem] bg-base-200 sm:w-80"></div>{/each}
    </div>
  {:else if stats && stats.trips === 0}
    <section class="grid items-center gap-8 rounded-[2rem] bg-card p-6 shadow-card sm:p-10 md:grid-cols-2">
      <Illustration name="year-review" class="w-full" />
      <div>
        <h2 class="text-2xl font-semibold text-ink">{isCurrent ? "Your year is still ahead" : `No trips in ${year}`}</h2>
        <p class="mt-2 text-base-content/70">
          Every trip you save counts here once it has arrived: kilometres, hours on board, delays and your favourite routes.
        </p>
        <a href={`${base}/search`} class="btn btn-primary mt-6 rounded-full px-6">Plan a trip</a>
      </div>
    </section>
  {:else if stats}
    <div class="relative">
      <div
        bind:this={track}
        class="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth px-4 pb-4 [scrollbar-width:none] md:-mx-6 md:px-6 lg:-mx-8 lg:px-8"
      >
        <!-- 1. Cover -->
        <article class="story bg-neutral text-neutral-content">
          <Illustration name="year-review" class="-mx-2 -mt-2 w-[calc(100%+1rem)]" />
          <p class="mt-6 text-sm font-semibold uppercase tracking-[0.14em] opacity-70">{year}{isCurrent ? " so far" : ""}</p>
          <p class="tabular mt-2 text-7xl font-semibold tracking-tight">{formatNumber(stats.trips)}</p>
          <p class="mt-1 text-xl font-medium">{stats.trips === 1 ? "trip" : "trips"} by train</p>
          <p class="mt-auto pt-6 text-sm opacity-70">{formatNumber(stats.rides)} rides · {formatNumber(stats.stations)} stations</p>
        </article>

        <!-- 2. Distance -->
        <article class="story bg-mint text-ink">
          <p class="eyebrow text-primary">Distance</p>
          <p class="tabular mt-6 text-6xl font-semibold tracking-tight">{formatNumber(stats.km)}<span class="ml-2 text-2xl font-medium opacity-60">km</span></p>
          <p class="mt-4 text-lg leading-snug">{acrossGermany(stats.km)}</p>
          <div class="mt-auto rounded-2xl bg-card p-4">
            <p class="text-xs font-semibold uppercase tracking-wider text-base-content/55">Longest trip</p>
            <p class="tabular mt-1 text-2xl font-semibold">{formatNumber(stats.longest_trip?.km ?? stats.longest_km)} km</p>
            {#if stats.longest_trip}
              <a class="mt-1 block text-sm text-base-content/70 hover:text-primary" href={`${base}/trip?id=${stats.longest_trip.trip_id}`}>
                {stats.longest_trip.from} → {stats.longest_trip.to} · {formatShortDate(stats.longest_trip.date)}
              </a>
            {/if}
          </div>
        </article>

        <!-- 2b. The map of everything travelled -->
        {#if lines.length > 0}
          <article class="story bg-card text-ink shadow-card">
            <p class="eyebrow text-primary">Where you went</p>
            <p class="mt-3 text-lg leading-snug">
              {formatNumber(lines.length)} {lines.length === 1 ? "stretch" : "stretches"} of track{#if stats.countries.length > 1}, in {stats.countries.length} countries{/if}.
            </p>
            <div class="mt-4 -mx-3 flex-1">
              <TravelMap {lines} />
            </div>
          </article>
        {/if}

        <!-- 2c. Footprint -->
        {#if stats.co2_saved_kg > 0}
          <article class="story bg-mint text-ink">
            <p class="eyebrow text-primary">Footprint</p>
            <p class="tabular mt-6 text-6xl font-semibold tracking-tight">{formatNumber(stats.co2_saved_kg, stats.co2_saved_kg < 10 ? 1 : 0)}<span class="ml-2 text-2xl font-medium opacity-60">kg</span></p>
            <p class="mt-1 text-xl font-medium">CO₂ saved against the car</p>
            <p class="mt-4 text-lg leading-snug">{trees(stats.co2_saved_kg)}</p>
            <div class="mt-auto grid grid-cols-2 gap-3">
              <div class="rounded-2xl bg-card p-4">
                <p class="text-xs font-semibold uppercase tracking-wider text-base-content/55">By train</p>
                <p class="tabular mt-1 text-xl font-semibold">{formatNumber(stats.co2_train_kg)} kg</p>
              </div>
              <div class="rounded-2xl bg-card p-4">
                <p class="text-xs font-semibold uppercase tracking-wider text-base-content/55">By car</p>
                <p class="tabular mt-1 text-xl font-semibold">{formatNumber(stats.co2_car_kg)} kg</p>
              </div>
            </div>
            <p class="mt-3 text-xs text-base-content/60">Per traveller. {stats.co2_source ?? "Approximate."}</p>
          </article>
        {/if}

        <!-- 2d. Countries -->
        {#if stats.countries.length > 0}
          <article class="story bg-sky text-ink">
            <p class="eyebrow text-info">Countries</p>
            <p class="tabular mt-6 text-7xl font-semibold tracking-tight">{stats.countries.length}</p>
            <p class="mt-1 text-xl font-medium">{stats.countries.length === 1 ? "country" : "countries"} by train</p>
            <ul class="mt-auto space-y-2">
              {#each stats.countries.slice(0, 6) as code (code)}
                <li class="flex items-center gap-3 rounded-2xl bg-card px-4 py-2.5 font-medium">
                  <span class="text-xl" aria-hidden="true">{countryFlag(code)}</span>{countryName(code)}
                </li>
              {/each}
            </ul>
          </article>
        {/if}

        <!-- 3. Time on board -->
        <article class="story bg-sky text-ink">
          <p class="eyebrow text-info">On board</p>
          <p class="tabular mt-6 text-6xl font-semibold tracking-tight">{formatNumber(hours, hours < 10 ? 1 : 0)}<span class="ml-2 text-2xl font-medium opacity-60">h</span></p>
          <p class="mt-4 text-lg leading-snug">
            Time to read about {formatNumber(Math.max(1, Math.round(hours / 7)))} {Math.round(hours / 7) <= 1 ? "book" : "books"}, look out of the window, or simply arrive rested.
          </p>
          <Illustration name="train-hero" class="mt-auto -mr-8 w-[115%]" />
        </article>

        <!-- 4. Punctuality -->
        <article class="story bg-card text-ink shadow-card">
          <p class="eyebrow text-primary">Punctuality</p>
          <div class="mt-6 flex items-center gap-5">
            <svg viewBox="0 0 36 36" class="h-28 w-28 -rotate-90" aria-hidden="true">
              <circle cx="18" cy="18" r="15.5" fill="none" class="stroke-base-300" stroke-width="5" />
              <circle cx="18" cy="18" r="15.5" fill="none" class="stroke-primary" stroke-width="5" stroke-linecap="round"
                stroke-dasharray="{((punctual ?? 0) / 100) * 97.4} 97.4" />
            </svg>
            <div>
              <p class="tabular text-5xl font-semibold tracking-tight">{punctual ?? "–"}<span class="text-2xl">%</span></p>
              <p class="text-sm text-base-content/60">arrived on time<br />(less than 6 min late)</p>
            </div>
          </div>
          <div class="mt-auto grid grid-cols-2 gap-3">
            <div class="rounded-2xl bg-amber-soft p-4 text-amber-ink">
              <p class="text-xs font-semibold uppercase tracking-wider opacity-80">Delay in total</p>
              <p class="tabular mt-1 text-2xl font-semibold">{formatNumber(stats.delay_minutes)} min</p>
            </div>
            <div class="rounded-2xl bg-base-300/60 p-4">
              <p class="text-xs font-semibold uppercase tracking-wider text-base-content/55">Worst arrival</p>
              <p class="tabular mt-1 text-2xl font-semibold">+{formatNumber(stats.max_delay_minutes)} min</p>
            </div>
          </div>
        </article>

        <!-- 5. Favourites -->
        <article class="story bg-primary text-primary-content">
          <p class="eyebrow opacity-80">Favourites</p>
          {#if stats.top_route}
            <p class="mt-6 text-sm opacity-80">Your route</p>
            <p class="mt-1 text-2xl leading-tight font-semibold">{stats.top_route.from}<br /><span class="opacity-60">⇄</span> {stats.top_route.to}</p>
            <p class="mt-1 text-sm opacity-80">{stats.top_route.count} {stats.top_route.count === 1 ? "time" : "times"}</p>
          {/if}
          {#if stats.top_station}
            <div class="mt-auto rounded-2xl bg-white/12 p-4">
              <p class="text-xs font-semibold uppercase tracking-wider opacity-80">Most visited station</p>
              <p class="mt-1 text-xl font-semibold">{stats.top_station.name}</p>
              <p class="text-sm opacity-80">{stats.top_station.count} arrivals and departures</p>
            </div>
          {/if}
        </article>

        <!-- 6. Trains -->
        <article class="story bg-card text-ink shadow-card">
          <p class="eyebrow text-primary">Trains</p>
          <p class="mt-6 text-lg">What you rode most</p>
          <ul class="mt-4 space-y-3">
            {#each products as p (p.name)}
              <li title="{p.n} {p.n === 1 ? 'ride' : 'rides'}">
                <div class="flex justify-between text-sm"><span class="font-semibold">{p.name}</span><span class="tabular text-base-content/60">{p.n}</span></div>
                <div class="mt-1 h-2.5 rounded-full bg-base-200">
                  <div class="h-2.5 rounded-full bg-primary" style="width: {(p.n / productMax) * 100}%"></div>
                </div>
              </li>
            {/each}
          </ul>
        </article>

        <!-- 7. Months -->
        <article class="story bg-card text-ink shadow-card">
          <p class="eyebrow text-primary">Month by month</p>
          <p class="mt-6 text-lg">Trips per month</p>
          <div class="mt-auto text-primary">
            <MonthChart {year} months={stats.by_month} />
          </div>
          <p class="mt-3 text-xs text-base-content/55">The busiest month in amber · hover a bar for details</p>
        </article>
      </div>

      <div class="mt-2 flex justify-center gap-2">
        <button class="btn btn-circle btn-sm bg-card shadow-card" aria-label="Previous card" onclick={() => scrollBy(-1)}><ChevronLeft class="h-4 w-4" /></button>
        <button class="btn btn-circle btn-sm bg-card shadow-card" aria-label="Next card" onclick={() => scrollBy(1)}><ChevronRight class="h-4 w-4" /></button>
      </div>
    </div>
  {/if}

  <ClaimsSection {year} />
</div>

<style>
  .story {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    width: 85%;
    max-width: 22rem;
    min-height: 28rem;
    padding: 1.75rem;
    border-radius: 2rem;
    overflow: hidden;
    scroll-snap-align: start;
  }
  .eyebrow {
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.14em;
  }
</style>
