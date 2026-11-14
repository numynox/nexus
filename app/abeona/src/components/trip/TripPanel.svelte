<script lang="ts">
  import {
    ArrowLeft,
    CalendarClock,
    CalendarX2,
    CreditCard,
    EllipsisVertical,
    ExternalLink,
    Pencil,
    RefreshCw,
    Share2,
    Shuffle,
    Ticket,
    Trash2,
    User,
  } from "lucide-svelte";
  import { onMount } from "svelte";
  import { getBaseUrl } from "../../lib/config";
  import {
    deleteTrip,
    describeError,
    fetchBahnCards,
    fetchTrip,
    recordDisruption,
    checkTimetable,
    refreshTripLive,
    type Trip,
  } from "../../lib/data";
  import {
    bahnCardName,
    bahnCardsShort,
    fareLabel,
    formatLongDate,
    formatPrice,
    formatTime,
    legLabel,
    ticketNeedsLabel,
  } from "../../lib/format";
  import { dbNavigatorUrl, hasArrived, isLiveWindow, LIVE_REFRESH_MS, summarise } from "../../lib/journey";
  import { FRESH_MS, type Fix, type LocationState, watchPosition } from "../../lib/position";
  import { getStationMinutes, getTripLocation, setTripLocation } from "../../lib/storage";
  import { hasImpossibleChange, trainBindingLifted, transfers } from "../../lib/transfer";
  import { takeConnection, tripFollowedLegs } from "../../lib/followed";
  import type { Leg } from "../../lib/transit";
  import { type AlternativesOrigin, alternativesOrigin, alternativesReason, missedShortConnection, travelDay } from "../../lib/travelday";
  import { cardsForTrip } from "../../lib/ticketNeeds";
  import BindingBanner from "../common/BindingBanner.svelte";
  import LiveDashboard from "../live/LiveDashboard.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import AlternativesSection from "./AlternativesSection.svelte";
  import CompensationCard from "./CompensationCard.svelte";
  import DisruptionCard from "./DisruptionCard.svelte";
  import Illustration from "../common/Illustration.svelte";
  import LegCard from "./LegCard.svelte";
  import EditTripSheet from "./EditTripSheet.svelte";
  import ShareSheet from "./ShareSheet.svelte";
  import TicketCard from "./TicketCard.svelte";
  import TicketViewer from "./TicketViewer.svelte";
  import TransferRow from "./TransferRow.svelte";
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
  let editing = $state(false);
  let sharing = $state(false);
  let alternativesSection: ReturnType<typeof AlternativesSection> | undefined = $state();
  let compensationCard: ReturnType<typeof CompensationCard> | undefined = $state();
  /** Ticks every 15 seconds, so the countdowns and the train on its line move. */
  let now = $state(Date.now());
  let stationMinutes = $state(15);
  /** This browser's position, while the trip is live and it was asked for. */
  let wantLocation = $state(false);
  let location = $state<LocationState>("off");
  let fix = $state<Fix | null>(null);
  const position = $derived(fix && now - fix.at <= FRESH_MS ? fix.position : null);

  // The journey as travelled: a connection taken from the alternatives
  // replaces the rest of the plan for everything live and the timeline.
  const legs = $derived(trip ? tripFollowedLegs(trip) : []);
  const shown = $derived(trip ? { ...trip, legs } : null);
  const following = $derived(!!trip && trip.missed_leg_idx !== null && (trip.replacement_legs?.length ?? 0) > 0);
  const s = $derived(legs.length > 0 ? summarise(legs) : null);
  const transferByTarget = $derived(new Map(transfers(legs).map((t) => [t.toIndex, t])));
  const firstRideIndex = $derived(legs.findIndex((l) => l.mode !== "walk"));
  const lastRideIndex = $derived(legs.findLastIndex((l) => l.mode !== "walk"));
  const navigatorUrl = $derived(dbNavigatorUrl(legs, trip?.travel_class ?? 2));
  const arrived = $derived(legs.length > 0 && hasArrived(legs));
  // The travel day only while the trip is live: from a day before it leaves
  // until shortly after it arrives.
  const live = $derived(legs.length > 0 && isLiveWindow(legs, now));
  const day = $derived(live ? travelDay(legs, now, stationMinutes, { position }) : null);
  // The ticket's binding is about the booked journey (as on the server), not a connection taken instead.
  const planDelay = $derived(trip && trip.legs.length > 0 ? summarise(trip.legs).arrivalDelay : 0);
  const missedShort = $derived(day ? missedShortConnection(legs, day) : false);
  // A ride no longer in the timetable for its day (checkTimetable): cancelled as booked.
  const droppedIdx = $derived(trip ? trip.legs.findIndex((l) => l.mode !== "walk" && !!l.dropped_at) : -1);
  const dropped = $derived(trip && droppedIdx >= 0 ? trip.legs[droppedIdx] : null);
  /** The connection followed instead was taken by Abeona for the dropped ride: the same trains, adjusted. */
  const adjusted = $derived(!!dropped && following && trip!.missed_leg_idx! <= droppedIdx);
  const connectionBroken = $derived(!!trip && (hasImpossibleChange(trip.legs) || missedShort || !!dropped));
  const bindingLifted = $derived(
    !!trip && trainBindingLifted(trip.fare_type, trip.train_binding, planDelay, connectionBroken),
  );

  // Only while the trip is live, the page open, and it was asked for.
  $effect(() => {
    if (!live || !wantLocation || arrived) return;
    const stop = watchPosition(
      (f) => (fix = f),
      (state) => (location = state),
    );
    return () => {
      stop();
      fix = null;
      location = "off";
    };
  });

  function useLocation(on: boolean) {
    wantLocation = on;
    setTripLocation(on);
  }
  // No origin once the only stop left is the destination: then nothing is offered.
  const origin = $derived(day ? alternativesOrigin(legs, day, now) : null);
  /** The travel day is on: the live card holds everything live, the header only what does not change. */
  const liveCard = $derived(!!day && !arrived);
  /** How fresh the live values are: the live card's last line, or a line under the header. */
  const status = $derived.by((): { text: string; offline?: boolean } | null => {
    if (!trip || !s) return null;
    if (arrived) return { text: `Arrived${trip.finalized_at ? " · final times recorded" : ""}` };
    if (liveCard && refreshError) return { text: "Live data unavailable", offline: true };
    if (s.hasRealtime && trip.refreshed_at) return { text: `Live · updated ${formatTime(trip.refreshed_at)}` };
    if (trip.refreshed_at) {
      return { text: `Timetable as of ${formatTime(trip.refreshed_at)}, ${new Date(trip.refreshed_at).toLocaleDateString("en-GB", { day: "numeric", month: "short" })}` };
    }
    return null;
  });
  const anyNotice = $derived(!!trip && !arrived && (bindingLifted || !!dropped || (following && !!trip.replacement_legs) || (liveCard && !!refreshError)));
  /**
   * Before the travel day, with a dropped ride and nothing followed instead:
   * connections from where it would have left, from the arrival of the ride
   * before it (else its own departure).
   */
  const droppedOrigin = $derived.by((): AlternativesOrigin | null => {
    if (!trip || !dropped || day || following || arrived) return null;
    const before = trip.legs.slice(0, droppedIdx).findLast((l) => l.mode !== "walk");
    const time = before?.to.planned ?? dropped.from.planned;
    if (!time) return null;
    return { id: dropped.from.id, name: dropped.from.name, lat: dropped.from.lat, lon: dropped.from.lon, time };
  });
  let menu: HTMLDetailsElement | undefined = $state();
  function closeMenu() {
    if (menu) menu.open = false;
  }
  const reason = $derived(day && origin ? alternativesReason(legs, day) : null);

  async function load(id: string) {
    try {
      trip = await fetchTrip(id);
      if (!trip) error = "This trip does not exist, or it belongs to another household.";
      else void loadTicketCards(trip);
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  /** After "Edit trip": the trip as stored, and everything worked out from its ticket. */
  async function reloadTrip() {
    if (!trip) return;
    // The edit is saved either way; without a fresh copy the page catches up on its next load.
    const fresh = await fetchTrip(trip.id).catch(() => null);
    if (!fresh) return;
    trip = fresh;
    void loadTicketCards(fresh);
    void compensationCard?.reload();
  }

  /** How many travellers the ticket is for and its BahnCards, small: "2", "BC25 · BC50"; null when nothing is known. */
  const ticketNeeds = $derived(
    trip && (trip.traveller_count !== null || trip.bahncard_discounts.length > 0)
      ? {
          count: trip.traveller_count,
          cards: bahnCardsShort(trip.bahncard_discounts),
          label: ticketNeedsLabel(trip.traveller_count, trip.bahncard_discounts),
        }
      : null,
  );

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

  async function checkRides() {
    if (!trip) return;
    try {
      if (await checkTimetable(trip)) {
        const fresh = await fetchTrip(trip.id);
        if (fresh) trip = fresh;
      }
    } catch {
      // Quietly: the ride keeps its printed times until the next try.
    }
  }

  let busyTaking = $state(false);

  /** Follows [chosen] from now on: stored as the trip's replacement, which the page and Android's companion follow. */
  async function take(chosen: Leg[]) {
    if (!trip || !day || busyTaking) return;
    const taken = takeConnection(trip.legs, trip.missed_leg_idx, trip.replacement_legs, day, chosen);
    if (!taken) return;
    busyTaking = true;
    try {
      await recordDisruption(trip.id, taken.missedIdx, taken.replacement);
      trip = { ...trip, missed_leg_idx: taken.missedIdx, replacement_legs: taken.replacement };
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (e) {
      refreshError = describeError(e);
    } finally {
      busyTaking = false;
    }
  }

  /** Before the travel day: [chosen] instead of the dropped ride and everything after it. */
  async function takeInstead(chosen: Leg[]) {
    if (!trip || droppedIdx < 0 || busyTaking) return;
    busyTaking = true;
    try {
      await recordDisruption(trip.id, droppedIdx, chosen);
      trip = { ...trip, missed_leg_idx: droppedIdx, replacement_legs: chosen };
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (e) {
      refreshError = describeError(e);
    } finally {
      busyTaking = false;
    }
  }

  /** Back to the plan: forgets the connection taken instead. */
  async function followPlan() {
    if (!trip || busyTaking) return;
    busyTaking = true;
    try {
      await recordDisruption(trip.id, null, null);
      trip = { ...trip, missed_leg_idx: null, replacement_legs: null };
    } catch (e) {
      refreshError = describeError(e);
    } finally {
      busyTaking = false;
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

  /** The household's BahnCards the ticket was booked with, one tap apart in the viewer. */
  let ticketCards = $state<{ key: string; label: string; path: string }[]>([]);

  async function loadTicketCards(t: Trip) {
    try {
      const cards = await fetchBahnCards();
      const day = new Date(t.planned_departure).toLocaleDateString("sv-SE");
      ticketCards = cardsForTrip(cards, t.bahncard_discounts, t.traveller_count, t.fare_type, day).map((c) => ({
        key: c.id,
        label: bahnCardName(c),
        path: c.file_path as string,
      }));
    } catch {
      ticketCards = [];
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
    wantLocation = getTripLocation();
    const tick = setInterval(() => (now = Date.now()), 15_000);
    const id = new URLSearchParams(window.location.search).get("id");
    if (!id) {
      error = "No trip selected.";
      loading = false;
      return () => clearInterval(tick);
    }

    let timer: ReturnType<typeof setInterval> | undefined;
    void load(id).then(() => {
      // Final trips (frozen by the job, or imported as past trips) never change again.
      if (!trip || trip.finalized_at) return;
      // Outside the live window, the rides are checked against the timetable
      // (inside it, refresh does that too).
      if (!isLiveWindow(trip.legs)) {
        void checkRides();
        return;
      }
      void refresh();
      // While the page is open on the day of the trip, every minute —
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

{#snippet needs(tone: string)}
  {#if ticketNeeds}
    <!-- Small on purpose: what the ticket was booked for, not something to act on. -->
    <span class="inline-flex items-center gap-2 normal-case tracking-normal {tone}" role="img" aria-label={ticketNeeds.label} title={ticketNeeds.label}>
      {#if ticketNeeds.count !== null}
        <span class="inline-flex items-center gap-0.5"><User class="h-3.5 w-3.5" />{ticketNeeds.count}</span>
      {/if}
      {#if ticketNeeds.cards}
        <span class="inline-flex items-center gap-1"><CreditCard class="h-3.5 w-3.5" />{ticketNeeds.cards}</span>
      {/if}
    </span>
  {/if}
{/snippet}

{#snippet tripNotices()}
  {#if trip}
  <div class="space-y-3">
    {#if liveCard && refreshError}
      <p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">Could not update live times: {refreshError}</p>
    {/if}
    {#if bindingLifted && !arrived}
      <BindingBanner
        delay={planDelay}
        because={planDelay >= 20 ? null : dropped ? `${legLabel(dropped)} no longer runs as booked` : missedShort ? "You missed a change that had less than 5 minutes" : "A change on your trip cannot be made"}
      />
    {/if}

    {#if dropped && !arrived && !following}
      <div class="flex items-start gap-3 rounded-2xl bg-error/10 px-4 py-3 text-error" role="alert">
        <CalendarX2 class="mt-0.5 h-5 w-5 shrink-0" />
        <div class="min-w-0 flex-1 text-sm leading-snug">
          <p class="font-semibold">{legLabel(dropped)} is no longer in the timetable</p>
          <p class="opacity-80">
            Not on {formatLongDate(dropped.from.planned)} any more, usually because of engineering works. Choose another connection below; Abeona follows it from then on.
          </p>
        </div>
      </div>
    {/if}

    {#if following && !arrived && trip.replacement_legs && adjusted}
      {@const first = trip.replacement_legs.find((l) => l.mode !== "walk") ?? trip.replacement_legs[0]}
      <div class="flex items-start gap-3 rounded-2xl bg-secondary px-4 py-3 text-secondary-content">
        <CalendarClock class="mt-0.5 h-5 w-5 shrink-0" />
        <div class="min-w-0 flex-1 text-sm leading-snug">
          <p class="font-semibold">Adjusted for a timetable change</p>
          <p class="opacity-80">
            {legLabel(dropped!)} no longer runs as booked, but your trains still connect. From {first.from.name}: {trip.replacement_legs.filter((l) => l.mode !== "walk").map((l) => `${legLabel(l)} ${formatTime(l.from.planned)}`).join(" › ")}.
          </p>
        </div>
      </div>
    {:else if following && !arrived && trip.replacement_legs}
      {@const first = trip.replacement_legs.find((l) => l.mode !== "walk") ?? trip.replacement_legs[0]}
      <div class="flex flex-wrap items-center gap-3 rounded-2xl bg-secondary px-4 py-3 text-secondary-content">
        <Shuffle class="h-5 w-5 shrink-0" />
        <div class="min-w-0 flex-1 text-sm leading-snug">
          <p class="font-semibold">You follow another connection</p>
          <p class="opacity-80">
            From {first.from.name}: {trip.replacement_legs.filter((l) => l.mode !== "walk").map((l) => legLabel(l)).join(" › ")}. The page follows these trains.
          </p>
        </div>
        <button class="btn btn-ghost btn-sm rounded-full" onclick={followPlan} disabled={busyTaking}>Back to the plan</button>
      </div>
    {/if}
  </div>
  {/if}
{/snippet}

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
    <!-- The trip's actions: the ticket one tap away, the rest under ⋮. -->
    <div class="flex items-center gap-1">
      <a href={`${base}/`} class="mr-auto inline-flex items-center gap-1.5 text-sm font-medium text-base-content/60 hover:text-primary">
        <ArrowLeft class="h-4 w-4" /> Trips
      </a>
      <button class="btn btn-ghost btn-circle btn-sm" onclick={openTicket} aria-label={trip.ticket_path ? "Show ticket" : "Add ticket"} title={trip.ticket_path ? "Show ticket" : "Add ticket"}>
        <Ticket class="h-5 w-5" />
      </button>
      <button class="btn btn-ghost btn-circle btn-sm" onclick={refresh} disabled={refreshing} aria-label="Refresh" title="Refresh">
        <RefreshCw class="h-5 w-5 {refreshing ? 'animate-spin' : ''}" />
      </button>
      <details class="dropdown dropdown-end" bind:this={menu}>
        <summary class="btn btn-ghost btn-circle btn-sm" aria-label="More"><EllipsisVertical class="h-5 w-5" /></summary>
        <ul class="menu dropdown-content z-20 mt-2 w-56 rounded-2xl bg-card p-2 shadow-card">
          <li>
            <a href={navigatorUrl} target="_blank" rel="noopener" onclick={closeMenu}><ExternalLink class="h-4 w-4" /> Open DB Navigator</a>
          </li>
          <li>
            <button onclick={() => { closeMenu(); editing = true; }}><Pencil class="h-4 w-4" /> Edit trip</button>
          </li>
          <li>
            <button onclick={() => { closeMenu(); sharing = true; }}><Share2 class="h-4 w-4" /> Share</button>
          </li>
          {#if day && origin && !arrived}
            <li>
              <button onclick={() => { closeMenu(); alternativesSection?.reveal(); }}><Shuffle class="h-4 w-4" /> Alternatives</button>
            </li>
          {/if}
          <li>
            <button class="text-error" onclick={() => { closeMenu(); confirmDialog?.showModal(); }}><Trash2 class="h-4 w-4" /> Delete trip</button>
          </li>
        </ul>
      </details>
    </div>

    {#if liveCard}
      <!-- While the trip is live, everything live is in the one card below; here only what does not change. -->
      <header class="px-1">
        <p class="flex flex-wrap items-center gap-x-1 text-xs font-semibold uppercase tracking-[0.12em] text-primary">
          <span>{formatLongDate(trip.planned_departure)}{trip.price_cents !== null ? ` · ${formatPrice(trip.price_cents)}` : ""}</span>
          {#if ticketNeeds}
            <span aria-hidden="true">·</span>
            {@render needs("text-primary/70")}
          {/if}
        </p>
        <h1 class="mt-2 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
          {trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}
        </h1>
        {#if trip.title}
          <p class="mt-1 text-base-content/70">{trip.origin_name} → {trip.destination_name}</p>
        {/if}
      </header>
    {:else}
      <header class="rounded-[2rem] bg-mint p-6 sm:p-8">
        <p class="text-xs font-semibold uppercase tracking-[0.12em] text-primary">{formatLongDate(trip.planned_departure)}</p>
        <!-- Start and destination once: on the board below, with their times. -->
        {#if trip.title}
          <h1 class="mt-2 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{trip.title}</h1>
        {:else}
          <h1 class="sr-only">{trip.origin_name} → {trip.destination_name}</h1>
        {/if}
        <div class="mt-3 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-start gap-3">
          <div class="min-w-0">
            <ShiftedTime planned={s.departure.planned} current={s.departure.current} cancelled={s.cancelled} class="text-3xl font-semibold tracking-tight text-ink sm:text-4xl" />
            <p class="mt-1 text-sm font-medium leading-snug text-ink/80">{trip.origin_name}</p>
          </div>
          <span class="pt-2 text-xl text-base-content/40" aria-hidden="true">→</span>
          <div class="flex min-w-0 flex-col items-end text-right">
            <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} class="text-3xl font-semibold tracking-tight text-ink sm:text-4xl" />
            <p class="mt-1 text-sm font-medium leading-snug text-ink/80">{trip.destination_name}</p>
          </div>
        </div>

        <div class="mt-4 flex flex-wrap gap-2 text-xs font-semibold">
          <span class="rounded-full bg-card px-3 py-1 text-ink">{fareLabel(trip.fare_type)}</span>
          <span class="rounded-full bg-card px-3 py-1 text-ink">{trip.travel_class === 1 ? "1st" : "2nd"} class</span>
          {#if trip.price_cents !== null}
            <span class="tabular rounded-full bg-card px-3 py-1 text-ink">{formatPrice(trip.price_cents)}</span>
          {/if}
          {#if trip.train_binding}
            <span class="rounded-full bg-card px-3 py-1 text-ink">Train-bound</span>
          {/if}
          {#if ticketNeeds}
            <span class="rounded-full bg-card/60 px-3 py-1">{@render needs("text-ink/70")}</span>
          {/if}
        </div>
      </header>

      {#if status}
        <p class="flex items-center gap-1.5 px-1 text-sm text-base-content/60">{status.text}</p>
      {/if}
      {#if refreshError}
        <p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">Could not update live times: {refreshError}</p>
      {/if}
    {/if}

    {#if day}
      <LiveDashboard
        trip={shown ?? trip}
        {day}
        {now}
        {stationMinutes}
        {reason}
        {position}
        {location}
        onLocation={useLocation}
        settingsHref={`${base}/settings#device`}
        onAlternatives={() => alternativesSection?.reveal()}
        {status}
        notices={anyNotice ? tripNotices : undefined}
      />
    {:else if anyNotice}
      {@render tripNotices()}
    {/if}

    {#if droppedOrigin}
      <AlternativesSection
        bind:this={alternativesSection}
        trip={shown ?? trip}
        origin={droppedOrigin}
        reason="dropped"
        refreshKey={trip.updated_at}
        onTake={takeInstead}
      />
    {:else if day && origin}
      <AlternativesSection
        bind:this={alternativesSection}
        trip={shown ?? trip}
        {origin}
        {reason}
        connectionMissed={missedShort}
        refreshKey={trip.refreshed_at}
        onTake={take}
      />
    {/if}

    {#if arrived}
      <!-- After the ride: what it is worth, and what really happened. -->
      <div class="grid gap-6 lg:grid-cols-2 lg:items-start">
        <CompensationCard bind:this={compensationCard} {trip} onTripChanged={(changed) => (trip = changed)} />
        <DisruptionCard
          {trip}
          onChanged={(changed) => {
            trip = changed;
            void compensationCard?.reload();
          }}
        />
      </div>
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
        {#if !day || arrived}
          <!-- While the trip is live the dashboard has the map, with you on it. -->
          <TripMap {legs} />
        {/if}
        <TicketCard
          bind:this={ticketCard}
          {trip}
          onChanged={(path) => trip && (trip = { ...trip, ticket_path: path })}
          onShow={() => (showTicket = true)}
        />
      </aside>
    </div>
  </div>

  {#if showTicket && trip.ticket_path}
    <TicketViewer
      path={trip.ticket_path}
      title={trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}
      {navigatorUrl}
      extras={ticketCards}
      onClose={() => (showTicket = false)}
    />
  {/if}

  <EditTripSheet trip={editing ? trip : null} onClose={() => (editing = false)} onSaved={reloadTrip} />
  <ShareSheet {trip} open={sharing} onClose={() => (sharing = false)} />

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
