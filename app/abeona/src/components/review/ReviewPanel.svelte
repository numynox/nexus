<script lang="ts">
  import { ChevronLeft, ChevronRight, CircleAlert, Gauge, MapPin, TrainFront } from "lucide-svelte";
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
  import { formatNumber, formatShortDate, parseDay } from "../../lib/format";
  import Banner from "../common/Banner.svelte";
  import EmptyState from "../common/EmptyState.svelte";
  import Fact from "../common/Fact.svelte";
  import Scene from "../common/Scene.svelte";
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

  /** The month with the most trips, for the last card's figure; a tie names none. */
  const busiest = $derived.by(() => {
    const months = stats?.by_month ?? [];
    const most = Math.max(0, ...months.map((m) => m.trips));
    const top = months.filter((m) => m.trips === most);
    const name =
      top.length === 1 ? new Date(`${top[0].month}-01T12:00:00`).toLocaleDateString("en-GB", { month: "long" }) : null;
    return { trips: most, name };
  });

  /** "ICE 25 · Frankfurt(Main)Hbf → Würzburg Hbf · 10 Oct": the ride the top speed was seen on. */
  const topSpeedRide = $derived.by(() => {
    const t = stats?.top_speed_trip;
    if (!t) return null;
    const day = parseDay(t.date).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
    return [t.line, `${t.from} → ${t.to}`, day].filter(Boolean).join(" · ");
  });

  /** Today at an hour of the day: each card's scene gets the sky that suits it. */
  function todayAt(hour: number): number {
    return new Date().setHours(hour, 0, 0, 0);
  }

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

<div class="space-y-3">
  <header class="flex flex-wrap items-center justify-between gap-3 px-1 pt-2">
    <h1 class="t-headline text-ink">Year in review</h1>
    <div class="inline-flex flex-wrap rounded-full bg-well p-0.5" role="radiogroup" aria-label="Year">
      {#each years as y (y)}
        <button
          role="radio"
          aria-checked={y === year}
          class="t-meta flex h-9 items-center rounded-full px-3.5 transition-colors {y === year ? 'bg-brand-soft font-semibold text-brand' : ''}"
          onclick={() => choose(y)}
        >{y}</button>
      {/each}
    </div>
  </header>

  {#if error}
    <Banner tone="bad" icon={CircleAlert} title={error} />
  {/if}

  {#if loading}
    <div class="flex gap-3 overflow-hidden" aria-busy="true">
      {#each [0, 1, 2] as i (i)}<div class="h-[25rem] w-[85%] max-w-[22rem] shrink-0 animate-pulse rounded-3xl bg-well"></div>{/each}
    </div>
  {:else if stats && stats.trips === 0}
    <section class="overflow-hidden rounded-3xl bg-card pb-6 shadow-card">
      <EmptyState title={isCurrent ? "Your year is still ahead" : `No trips in ${year}`}>
        <a href={`${base}/search`} class="btn btn-primary h-12 rounded-full px-6">Plan a trip</a>
      </EmptyState>
    </section>
  {:else if stats}
    <div class="relative">
      <!-- Every card: a scene or a chart on top, one figure, one line under it; what else it knew sits small at the bottom. -->
      <div
        bind:this={track}
        class="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto scroll-smooth px-4 pb-3 [scrollbar-width:none] md:-mx-6 md:px-6 lg:-mx-8 lg:px-8"
      >
        <!-- 1. Cover -->
        <article class="story">
          <Scene state="idle" size="full" />
          <div class="body">
            <p class="t-numeral text-ink">{formatNumber(stats.trips)}</p>
            <p class="t-body text-ink">{stats.trips === 1 ? "trip" : "trips"} by train in {year}{isCurrent ? " so far" : ""}</p>
            <p class="foot">
              <Fact icon={TrainFront} label="Rides" value={formatNumber(stats.rides)} />
              <Fact icon={MapPin} label="Stations" value={formatNumber(stats.stations)} />
            </p>
          </div>
        </article>

        <!-- 2. Distance -->
        <article class="story">
          <Scene state="riding" size="full" at={todayAt(11)} />
          <div class="body">
            <p class="t-numeral text-ink">{formatNumber(stats.km)}<span class="unit">km</span></p>
            <p class="t-body text-ink">{acrossGermany(stats.km)}</p>
            <p class="foot">
              {#if stats.longest_trip}
                <a class="t-meta hover:text-brand" href={`${base}/trip?id=${stats.longest_trip.trip_id}`}>
                  Longest trip {formatNumber(stats.longest_trip.km)} km · {stats.longest_trip.from} → {stats.longest_trip.to} · {formatShortDate(stats.longest_trip.date)}
                </a>
              {:else}
                <span class="t-meta">Longest trip {formatNumber(stats.longest_km)} km</span>
              {/if}
            </p>
          </div>
        </article>

        <!-- 2b. The map of everything travelled -->
        {#if lines.length > 0}
          <article class="story">
            <div class="h-60 p-2 pb-0"><TravelMap {lines} /></div>
            <div class="body">
              <p class="t-numeral text-ink">{formatNumber(lines.length)}</p>
              <p class="t-body text-ink">
                {lines.length === 1 ? "stretch" : "stretches"} of track{#if stats.countries.length > 1}, in {stats.countries.length} countries{/if}
              </p>
            </div>
          </article>
        {/if}

        <!-- 2c. Footprint -->
        {#if stats.co2_saved_kg > 0}
          <article class="story">
            <Scene state="idle" size="full" at={todayAt(9)} />
            <div class="body">
              <p class="t-numeral text-ink">{formatNumber(stats.co2_saved_kg, stats.co2_saved_kg < 10 ? 1 : 0)}<span class="unit">kg CO₂</span></p>
              <p class="t-body text-ink">saved against the car. {trees(stats.co2_saved_kg)}</p>
              <p class="foot t-meta">
                By train {formatNumber(stats.co2_train_kg)} kg · by car {formatNumber(stats.co2_car_kg)} kg · per traveller. {stats.co2_source ?? "Approximate."}
              </p>
            </div>
          </article>
        {/if}

        <!-- 2d. Countries -->
        {#if stats.countries.length > 0}
          <article class="story">
            <Scene state="parked" size="full" at={todayAt(15)} />
            <div class="body">
              <p class="t-numeral text-ink">{stats.countries.length}</p>
              <p class="t-body text-ink">{stats.countries.length === 1 ? "country" : "countries"} by train</p>
              <ul class="foot">
                {#each stats.countries.slice(0, 6) as code (code)}
                  <li class="t-meta inline-flex h-6 items-center gap-1 rounded-full bg-well px-2">
                    <span aria-hidden="true">{countryFlag(code)}</span>{countryName(code)}
                  </li>
                {/each}
              </ul>
            </div>
          </article>
        {/if}

        <!-- 3. Time on board: the night train -->
        <article class="story">
          <Scene state="riding" size="full" at={todayAt(23)} />
          <div class="body">
            <p class="t-numeral text-ink">{formatNumber(hours, hours < 10 ? 1 : 0)}<span class="unit">h</span></p>
            <p class="t-body text-ink">
              on board: time to read about {formatNumber(Math.max(1, Math.round(hours / 7)))} {Math.round(hours / 7) <= 1 ? "book" : "books"}, or simply arrive rested.
            </p>
          </div>
        </article>

        <!-- 3b. Top speed: only rides the Android companion followed on the train's Wi-Fi know it. -->
        {#if stats.top_speed_kmh !== null && stats.top_speed_kmh > 0}
          <article class="story">
            <Scene state="riding" size="full" at={todayAt(14)} speedKmh={stats.top_speed_kmh} />
            <div class="body">
              <p class="t-numeral text-ink">{formatNumber(stats.top_speed_kmh)}<span class="unit">km/h</span></p>
              {#if topSpeedRide && stats.top_speed_trip}
                <a class="t-body text-ink hover:text-brand" href={`${base}/trip?id=${stats.top_speed_trip.trip_id}`}>{topSpeedRide}</a>
              {/if}
              <p class="foot t-meta">
                <span class="inline-flex items-center gap-1"><Gauge class="h-4 w-4 shrink-0" aria-hidden="true" />Top speed, from the train's Wi-Fi</span>
              </p>
            </div>
          </article>
        {/if}

        <!-- 4. Punctuality -->
        <article class="story">
          <div class="chart">
            <svg viewBox="0 0 36 36" class="h-36 w-36 -rotate-90" aria-hidden="true">
              <circle cx="18" cy="18" r="15.5" fill="none" class="stroke-well" stroke-width="5" />
              <circle cx="18" cy="18" r="15.5" fill="none" class="stroke-primary" stroke-width="5" stroke-linecap="round"
                stroke-dasharray="{((punctual ?? 0) / 100) * 97.4} 97.4" />
            </svg>
          </div>
          <div class="body">
            <p class="t-numeral text-ink">{punctual ?? "–"}<span class="unit">%</span></p>
            <p class="t-body text-ink">arrived on time (less than 6 min late)</p>
            <p class="foot t-meta">
              <span>
                Delay in total {formatNumber(stats.delay_minutes)} min · worst arrival
                <span class="font-bold text-watch-ink">+{formatNumber(stats.max_delay_minutes)}</span>
              </span>
            </p>
          </div>
        </article>

        <!-- 5. Favourites -->
        {#if stats.top_route || stats.top_station}
          <article class="story">
            <Scene state="arrived" size="full" at={todayAt(13)} />
            <div class="body">
              {#if stats.top_route}
                <p class="t-numeral text-ink">{stats.top_route.count}<span class="unit">×</span></p>
                <p class="t-body text-ink">{stats.top_route.from} ⇄ {stats.top_route.to}, your route</p>
                {#if stats.top_station}
                  <p class="foot t-meta">
                    Most visited station: {stats.top_station.name} · {stats.top_station.count} arrivals and departures
                  </p>
                {/if}
              {:else if stats.top_station}
                <p class="t-numeral text-ink">{stats.top_station.count}</p>
                <p class="t-body text-ink">arrivals and departures at {stats.top_station.name}, your most visited station</p>
              {/if}
            </div>
          </article>
        {/if}

        <!-- 6. Trains -->
        {#if products.length > 0}
          <article class="story">
            <ul class="chart flex-col !items-stretch gap-2">
              {#each products as p (p.name)}
                <li class="grid grid-cols-[3.5rem_minmax(0,1fr)_2rem] items-center gap-2" title="{p.n} {p.n === 1 ? 'ride' : 'rides'}">
                  <span class="t-meta truncate font-semibold text-ink">{p.name}</span>
                  <span class="h-3 rounded-full bg-well">
                    <span class="block h-3 rounded-full bg-primary" style="width: {(p.n / productMax) * 100}%"></span>
                  </span>
                  <span class="t-meta text-right">{p.n}</span>
                </li>
              {/each}
            </ul>
            <div class="body">
              <p class="t-numeral text-ink">{formatNumber(products[0].n)}</p>
              <p class="t-body text-ink">{products[0].n === 1 ? "ride" : "rides"} by {products[0].name}, what you rode most</p>
            </div>
          </article>
        {/if}

        <!-- 7. Months -->
        <article class="story">
          <div class="chart !items-end"><div class="w-full"><MonthChart {year} months={stats.by_month} /></div></div>
          <div class="body">
            <p class="t-numeral text-ink">{formatNumber(busiest.trips)}</p>
            <p class="t-body text-ink">
              {busiest.trips === 1 ? "trip" : "trips"} in {busiest.name ? `${busiest.name}, your busiest month` : "each of your busiest months"}
            </p>
          </div>
        </article>
      </div>

      <div class="flex justify-center gap-2">
        <button class="btn btn-circle btn-ghost btn-sm" aria-label="Previous card" onclick={() => scrollBy(-1)}><ChevronLeft class="h-5 w-5" /></button>
        <button class="btn btn-circle btn-ghost btn-sm" aria-label="Next card" onclick={() => scrollBy(1)}><ChevronRight class="h-5 w-5" /></button>
      </div>
    </div>
  {/if}

  <ClaimsSection {year} />
</div>

<style>
  /* A story card: 200 of scene or chart, then the figure and its line. */
  .story {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    width: 85%;
    max-width: 22rem;
    min-height: 25rem;
    border-radius: 1.5rem;
    overflow: hidden;
    scroll-snap-align: start;
    background: var(--ab-card);
    box-shadow: var(--ab-shadow);
  }
  .chart {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    padding: 1rem 1rem 0;
  }
  .body {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 0.25rem;
    padding: 1rem;
  }
  .unit {
    margin-left: 0.375rem;
    font-size: 1.0625rem;
    font-weight: 600;
    letter-spacing: 0;
    color: var(--ab-muted);
  }
  .foot {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem 0.75rem;
    margin-top: auto;
    padding-top: 0.75rem;
  }
</style>
