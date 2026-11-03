<script lang="ts">
  import { ArrowLeft, ExternalLink, RefreshCw, Ticket, Trash2 } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import { deleteTrip, describeError, fetchTrip, fetchTripWarnings, refreshTripLive, type Trip, type TripWarning } from "../../lib/data";
  import { fareLabel, formatLongDate, formatPrice, formatTime } from "../../lib/format";
  import { dbNavigatorUrl, hasArrived, isLiveWindow, LIVE_REFRESH_MS, summarise } from "../../lib/journey";
  import { getStationMinutes } from "../../lib/storage";
  import { trainBindingLifted, transfers } from "../../lib/transfer";
  import { alternativesOrigin, alternativesReason, travelDay } from "../../lib/travelday";
  import BindingBanner from "../common/BindingBanner.svelte";
  import TravelDayHero from "../common/TravelDayHero.svelte";
  import AlternativesSection from "./AlternativesSection.svelte";
  import Illustration from "../common/Illustration.svelte";
  import LegCard from "./LegCard.svelte";
  import TicketCard from "./TicketCard.svelte";
  import TicketViewer from "./TicketViewer.svelte";
  import TransferRow from "./TransferRow.svelte";
  import TravellersCard from "./TravellersCard.svelte";
  import TripWarnings from "./TripWarnings.svelte";
  import TripMap from "./TripMap.svelte";
  import WalkRow from "./WalkRow.svelte";

  const base = getBaseUrl();

  let trip = $state<Trip | null>(null);
  let loading = $state(true);
  let error = $state("");
  let refreshing = $state(false);
  let refreshError = $state("");
  let showTicket = $state(false);
  let deleting = $state(false);
  let confirmDialog: HTMLDialogElement | undefined = $state();
  let ticketCard: ReturnType<typeof TicketCard> | undefined = $state();
  let warnings = $state<TripWarning[]>([]);
  let alternativesSection: ReturnType<typeof AlternativesSection> | undefined = $state();
  /** Ticks every half minute, so the travel day's countdowns move. */
  let now = $state(Date.now());
  let stationMinutes = $state(15);

  const legs = $derived(trip?.legs ?? []);
  const s = $derived(legs.length > 0 ? summarise(legs) : null);
  const transferByTarget = $derived(new Map(transfers(legs).map((t) => [t.toIndex, t])));
  const firstRideIndex = $derived(legs.findIndex((l) => l.mode !== "walk"));
  const lastRideIndex = $derived(legs.findLastIndex((l) => l.mode !== "walk"));
  const bindingLifted = $derived(
    !!trip && !!s && trainBindingLifted(trip.fare_type, trip.train_binding, s.arrivalDelay),
  );
  const navigatorUrl = $derived(dbNavigatorUrl(legs, trip?.travel_class ?? 2));
  const arrived = $derived(legs.length > 0 && hasArrived(legs));
  // The travel day only while the trip is live: from a day before it leaves
  // until shortly after it arrives.
  const live = $derived(legs.length > 0 && isLiveWindow(legs, now));
  const day = $derived(live ? travelDay(legs, now, stationMinutes) : null);
  const reason = $derived(day ? alternativesReason(legs, day) : null);
  const origin = $derived(day ? alternativesOrigin(legs, day, now) : null);

  async function load(id: string) {
    try {
      trip = await fetchTrip(id);
      if (!trip) error = "This trip does not exist, or it belongs to another household.";
      else void loadWarnings(trip.id);
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  async function loadWarnings(id: string) {
    // Warnings are advice: a failure leaves the trip page as it is.
    warnings = await fetchTripWarnings(id).catch(() => warnings);
  }

  async function refresh() {
    if (!trip || refreshing) return;
    refreshing = true;
    refreshError = "";
    try {
      if (await refreshTripLive(trip)) {
        const fresh = await fetchTrip(trip.id);
        if (fresh) trip = fresh;
      }
    } catch (e) {
      refreshError = describeError(e);
    } finally {
      refreshing = false;
    }
  }

  async function remove() {
    if (!trip) return;
    deleting = true;
    try {
      await deleteTrip(trip);
      window.location.href = `${base}/`;
    } catch (e) {
      error = describeError(e);
      deleting = false;
      confirmDialog?.close();
    }
  }

  function openTicket() {
    if (trip?.ticket_path) showTicket = true;
    else {
      document.getElementById("ticket")?.scrollIntoView({ behavior: "smooth", block: "center" });
      ticketCard?.choose();
    }
  }

  onMount(() => {
    stationMinutes = getStationMinutes();
    const tick = setInterval(() => (now = Date.now()), 30_000);
    const id = new URLSearchParams(window.location.search).get("id");
    if (!id) {
      error = "No trip selected.";
      loading = false;
      return () => clearInterval(tick);
    }

    let timer: ReturnType<typeof setInterval> | undefined;
    void load(id).then(() => {
      if (!trip || !isLiveWindow(trip.legs)) return;
      void refresh();
      // While the page is open on the day of the trip, every two minutes —
      // the web has no background service, so this is all it can do.
      timer = setInterval(() => {
        if (document.visibilityState !== "visible" || !trip) return;
        if (!isLiveWindow(trip.legs)) {
          clearInterval(timer);
          return;
        }
        void refresh();
      }, LIVE_REFRESH_MS);
    });

    return () => {
      clearInterval(timer);
      clearInterval(tick);
    };
  });
</script>

{#if loading}
  <div class="space-y-4" aria-busy="true">
    <div class="h-44 animate-pulse rounded-[2rem] bg-mint"></div>
    <div class="h-56 animate-pulse rounded-3xl bg-base-200"></div>
    <div class="h-56 animate-pulse rounded-3xl bg-base-200"></div>
  </div>
{:else if !trip || !s}
  <div class="mx-auto max-w-md pt-10 text-center">
    <Illustration name="station" class="mx-auto w-72" />
    <p class="mt-6 font-semibold text-ink">{error || "Trip not found"}</p>
    <a href={`${base}/`} class="btn btn-primary mt-6 rounded-full px-6">Back to trips</a>
  </div>
{:else}
  <div class="space-y-6">
    <a href={`${base}/`} class="inline-flex items-center gap-1.5 text-sm font-medium text-base-content/60 hover:text-primary">
      <ArrowLeft class="h-4 w-4" /> Trips
    </a>

    <header class="rounded-[2rem] bg-mint p-6 sm:p-8">
      <p class="text-xs font-semibold uppercase tracking-[0.12em] text-primary">{formatLongDate(trip.planned_departure)}</p>
      <h1 class="mt-2 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
        {trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}
      </h1>
      {#if trip.title}
        <p class="mt-1 text-base-content/70">{trip.origin_name} → {trip.destination_name}</p>
      {/if}
      <p class="tabular mt-2 text-lg font-medium text-ink/80">
        {formatTime(s.departure.planned)} – {formatTime(s.arrival.planned)}
      </p>

      <div class="mt-4 flex flex-wrap gap-2 text-xs font-semibold">
        <span class="rounded-full bg-card px-3 py-1 text-ink">{fareLabel(trip.fare_type)}</span>
        <span class="rounded-full bg-card px-3 py-1 text-ink">{trip.travel_class === 1 ? "1st" : "2nd"} class</span>
        {#if trip.price_cents !== null}
          <span class="tabular rounded-full bg-card px-3 py-1 text-ink">{formatPrice(trip.price_cents)}</span>
        {/if}
        {#if trip.bahncard_discount}
          <span class="rounded-full bg-card px-3 py-1 text-ink">BahnCard {trip.bahncard_discount} discount</span>
        {/if}
        {#if trip.train_binding}
          <span class="rounded-full bg-card px-3 py-1 text-ink">Train-bound</span>
        {/if}
      </div>

      <div class="mt-5 flex flex-wrap gap-2">
        <button class="btn btn-primary rounded-full px-5" onclick={openTicket}>
          <Ticket class="h-4 w-4" /> {trip.ticket_path ? "Show ticket" : "Add ticket"}
        </button>
        <a href={navigatorUrl} target="_blank" rel="noopener" class="btn rounded-full border-base-300 bg-card px-5">
          <ExternalLink class="h-4 w-4" /> Open DB Navigator
        </a>
      </div>
    </header>

    <div class="flex flex-wrap items-center justify-between gap-3 px-1">
      <p class="text-sm text-base-content/60">
        {#if arrived}
          Arrived
        {:else if s.hasRealtime && trip.refreshed_at}
          <span class="mr-1 inline-block h-2 w-2 rounded-full bg-primary"></span>
          Live · updated {formatTime(trip.refreshed_at)}
        {:else if trip.refreshed_at}
          Timetable as of {formatTime(trip.refreshed_at)}, {new Date(trip.refreshed_at).toLocaleDateString("en-GB", { day: "numeric", month: "short" })}
        {/if}
      </p>
      <button class="btn btn-ghost btn-sm rounded-full text-primary" onclick={refresh} disabled={refreshing}>
        <RefreshCw class="h-4 w-4 {refreshing ? 'animate-spin' : ''}" /> Refresh
      </button>
    </div>
    {#if refreshError}
      <p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">Could not update live times: {refreshError}</p>
    {/if}

    {#if day}
      <TravelDayHero
        {legs}
        {day}
        {stationMinutes}
        {reason}
        settingsHref={`${base}/settings#device`}
        onAlternatives={() => alternativesSection?.reveal()}
      />
    {/if}

    {#if bindingLifted}
      <BindingBanner delay={s.arrivalDelay} />
    {/if}

    {#if warnings.length > 0 && !arrived}
      <TripWarnings {warnings} settingsHref={`${base}/settings#bahncards`} />
    {/if}

    {#if day && origin}
      <AlternativesSection bind:this={alternativesSection} {trip} {origin} {reason} refreshKey={trip.refreshed_at} />
    {/if}

    <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <section aria-label="Journey" class="space-y-1">
        {#each legs as leg, i (i)}
          {#if leg.mode === "walk"}
            {#if i < firstRideIndex || i > lastRideIndex}
              <WalkRow {leg} />
            {/if}
          {:else}
            {@const transfer = transferByTarget.get(i)}
            {#if transfer}
              <TransferRow {transfer} arriving={legs[transfer.fromIndex]} departing={leg} />
            {/if}
            <LegCard {leg} />
          {/if}
        {/each}
      </section>

      <aside class="space-y-4 lg:sticky lg:top-6">
        <TripMap {legs} />
        <TicketCard
          bind:this={ticketCard}
          {trip}
          onChanged={(path) => trip && (trip = { ...trip, ticket_path: path })}
          onShow={() => (showTicket = true)}
        />
        <TravellersCard
          {trip}
          onChanged={(changed) => {
            trip = changed;
            void loadWarnings(changed.id);
          }}
        />
        <button class="btn btn-ghost btn-sm w-full rounded-full text-error" onclick={() => confirmDialog?.showModal()}>
          <Trash2 class="h-4 w-4" /> Delete trip
        </button>
      </aside>
    </div>
  </div>

  {#if showTicket && trip.ticket_path}
    <TicketViewer
      path={trip.ticket_path}
      title={trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}
      {navigatorUrl}
      onClose={() => (showTicket = false)}
    />
  {/if}

  <dialog bind:this={confirmDialog} class="modal modal-bottom sm:modal-middle">
    <div class="modal-box rounded-t-[2rem] sm:rounded-[2rem]">
      <h3 class="text-lg font-semibold text-ink">Delete this trip?</h3>
      <p class="mt-2 text-sm text-base-content/70">
        It disappears for everyone in your household, together with its ticket and its place in the statistics.
      </p>
      <div class="modal-action">
        <form method="dialog"><button class="btn btn-ghost rounded-full">Keep it</button></form>
        <button class="btn btn-error rounded-full" onclick={remove} disabled={deleting}>
          {#if deleting}<span class="loading loading-spinner loading-sm"></span>{/if}
          Delete
        </button>
      </div>
    </div>
    <form method="dialog" class="modal-backdrop"><button>close</button></form>
  </dialog>
{/if}
