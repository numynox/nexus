<script lang="ts">
  import {
    ArrowLeft,
    ArrowRightLeft,
    CalendarClock,
    CalendarX2,
    CircleCheck,
    Clock,
    CloudOff,
    EllipsisVertical,
    ExternalLink,
    Pencil,
    RefreshCw,
    Share2,
    Shuffle,
    Ticket,
    Trash2,
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
  import { bahnCardName, formatDuration, formatLongDate, formatTime, legLabel } from "../../lib/format";
  import { dbNavigatorUrl, hasArrived, isLiveWindow, LIVE_REFRESH_MS, summarise } from "../../lib/journey";
  import { FRESH_MS, type Fix, type LocationState, watchPosition } from "../../lib/position";
  import { getStationMinutes, getTripLocation, setTripLocation } from "../../lib/storage";
  import { hasImpossibleChange, trainBindingLifted } from "../../lib/transfer";
  import { takeConnection, tripFollowedLegs } from "../../lib/followed";
  import type { Leg } from "../../lib/transit";
  import { type AlternativesOrigin, alternativesOrigin, alternativesReason, missedShortConnection, type TravelDay, travelDay } from "../../lib/travelday";
  import { cardsForTrip } from "../../lib/ticketNeeds";
  import Banner from "../common/Banner.svelte";
  import BindingBanner from "../common/BindingBanner.svelte";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import EmptyState from "../common/EmptyState.svelte";
  import Fact from "../common/Fact.svelte";
  import LiveDashboard from "../live/LiveDashboard.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import AlternativesSection from "./AlternativesSection.svelte";
  import CompensationCard from "./CompensationCard.svelte";
  import DisruptionCard from "./DisruptionCard.svelte";
  import EditTripSheet from "./EditTripSheet.svelte";
  import ShareSheet from "./ShareSheet.svelte";
  import TicketCard from "./TicketCard.svelte";
  import TicketViewer from "./TicketViewer.svelte";
  import Timeline from "./Timeline.svelte";
  import TripMap from "./TripMap.svelte";

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
  const navigatorUrl = $derived(dbNavigatorUrl(legs, trip?.travel_class ?? 2));
  const arrived = $derived(legs.length > 0 && hasArrived(legs));
  // The travel day only while the trip is live: from a day before it leaves
  // until shortly after it arrives.
  const live = $derived(legs.length > 0 && isLiveWindow(legs, now));
  const day = $derived(live ? travelDay(legs, now, stationMinutes, { position }) : null);
  /** After the live window the arrived card stays: where, when, the delay. The same card as the travel day's last state. */
  const ARRIVED: TravelDay = {
    kind: "arrived",
    legIndex: null,
    fromLegIndex: null,
    leaveAt: null,
    minutesToLeave: null,
    minutesToDeparture: null,
    minutesToArrival: null,
    cancelled: false,
    missedChange: false,
    source: "time",
  };
  const cardDay = $derived(day ?? (arrived ? ARRIVED : null));
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
  /** The live card shows the journey as its line, so the timeline waits until arrival. */
  const journeyLive = $derived(!!cardDay && cardDay.kind !== "arrived");
  /** How fresh the live values are: an icon and a time, the last and smallest line of the card or the page. */
  const status = $derived.by((): { text: string; label: string; kind: "live" | "timetable" | "offline" | "done" } | null => {
    if (!trip || !s) return null;
    if (arrived) return trip.finalized_at ? { text: "Final times", label: "Arrived, final times recorded", kind: "done" } : null;
    // On the live card: "Live 12:37", "Offline 12:37" (the last time it got through).
    if (liveCard && refreshError) {
      return { text: trip.refreshed_at ? formatTime(trip.refreshed_at) : "", label: `Offline: ${refreshError}`, kind: "offline" };
    }
    if (s.hasRealtime && trip.refreshed_at) return { text: formatTime(trip.refreshed_at), label: "Live, updated", kind: "live" };
    if (trip.refreshed_at) {
      return {
        text: liveCard
          ? formatTime(trip.refreshed_at)
          : `${formatTime(trip.refreshed_at)}, ${new Date(trip.refreshed_at).toLocaleDateString("en-GB", { day: "numeric", month: "short" })}`,
        label: "Timetable as of",
        kind: "timetable",
      };
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

{#snippet tripNotices()}
  {#if trip}
  <div class="space-y-2">
    {#if refreshError}
      <Banner tone="bad" icon={CloudOff} title="Could not update live times">{refreshError}</Banner>
    {/if}
    <!-- A dropped ride says the binding is lifted in its own banner. -->
    {#if bindingLifted && !arrived && !dropped}
      <BindingBanner
        delay={planDelay}
        because={planDelay >= 20 ? null : missedShort ? "Missed a change under 5 min" : "A change cannot be made"}
      />
    {/if}

    {#if dropped && !arrived && !following}
      <Banner tone="bad" icon={CalendarX2} title={`${legLabel(dropped)} is no longer in the timetable`}>
        {#if bindingLifted}Train binding lifted.{/if}
        {#snippet action()}
          {#if droppedOrigin}
            <button class="btn btn-sm rounded-full border-none bg-bad text-bad-content" onclick={() => alternativesSection?.reveal()}>
              <Shuffle class="h-4 w-4" aria-hidden="true" /> Find another connection
            </button>
          {/if}
        {/snippet}
      </Banner>
    {/if}

    {#if following && !arrived && trip.replacement_legs && adjusted}
      <Banner tone="watch" icon={CalendarClock} title={`${legLabel(dropped!)} is no longer in the timetable`}>
        Now: {trip.replacement_legs.filter((l) => l.mode !== "walk").map((l) => `${legLabel(l)} ${formatTime(l.from.planned)} ${l.from.name}`).join(" › ")}
      </Banner>
    {:else if following && !arrived && trip.replacement_legs}
      {@const first = trip.replacement_legs.find((l) => l.mode !== "walk") ?? trip.replacement_legs[0]}
      <Banner tone="ok" icon={Shuffle} title="You follow another connection">
        From {first.from.name}: {trip.replacement_legs.filter((l) => l.mode !== "walk").map((l) => legLabel(l)).join(" › ")}.
        {#snippet action()}
          <button class="btn btn-ghost btn-sm rounded-full" onclick={followPlan} disabled={busyTaking}>Back to the plan</button>
        {/snippet}
      </Banner>
    {/if}
  </div>
  {/if}
{/snippet}

{#if loading}
  <div class="space-y-3" aria-busy="true">
    <div class="h-10 animate-pulse rounded-full bg-well"></div>
    <div class="h-56 animate-pulse rounded-3xl bg-brand-soft"></div>
    <div class="h-56 animate-pulse rounded-3xl bg-well"></div>
  </div>
{:else if !trip || !s}
  <div class="mx-auto max-w-md pt-6">
    <EmptyState title={error || "Trip not found"} state="stopped">
      <a href={`${base}/`} class="btn btn-primary rounded-full px-6">Back to trips</a>
    </EmptyState>
  </div>
{:else}
  {@const route = `${trip.origin_name} → ${trip.destination_name}`}
  <div class="space-y-3">
    <!-- The top bar: the trip's name, the ticket one tap away, refresh, the rest under ⋮. -->
    <div class="flex min-h-12 items-center gap-1">
      <a href={`${base}/`} class="btn btn-ghost btn-circle btn-sm -ml-1" aria-label="Trips" title="Trips"><ArrowLeft class="h-5 w-5" aria-hidden="true" /></a>
      <div class="min-w-0 flex-1">
        <h1 class="t-title truncate text-ink">{trip.title ?? route}</h1>
        <p class="t-meta truncate">{formatLongDate(trip.planned_departure)}{trip.title ? ` · ${route}` : ""}</p>
      </div>
      <button class="btn btn-ghost btn-circle btn-sm" onclick={openTicket} aria-label={trip.ticket_path ? "Show ticket" : "Add ticket"} title={trip.ticket_path ? "Show ticket" : "Add ticket"}>
        <Ticket class="h-6 w-6 {trip.ticket_path ? 'text-brand' : ''}" aria-hidden="true" />
      </button>
      <button class="btn btn-ghost btn-circle btn-sm" onclick={refresh} disabled={refreshing} aria-label="Refresh" title="Refresh">
        <RefreshCw class="h-6 w-6 {refreshing ? 'animate-spin' : ''}" aria-hidden="true" />
      </button>
      <details class="dropdown dropdown-end" bind:this={menu}>
        <summary class="btn btn-ghost btn-circle btn-sm" aria-label="More"><EllipsisVertical class="h-6 w-6" aria-hidden="true" /></summary>
        <ul class="menu dropdown-content z-20 mt-2 w-56 rounded-2xl bg-card p-2 shadow-card">
          <li>
            <a href={navigatorUrl} target="_blank" rel="noopener" onclick={closeMenu}><ExternalLink class="h-4 w-4" aria-hidden="true" /> Open DB Navigator</a>
          </li>
          <li>
            <button onclick={() => { closeMenu(); editing = true; }}><Pencil class="h-4 w-4" aria-hidden="true" /> Edit trip</button>
          </li>
          <li>
            <button onclick={() => { closeMenu(); sharing = true; }}><Share2 class="h-4 w-4" aria-hidden="true" /> Share</button>
          </li>
          {#if day && origin && !arrived}
            <li>
              <button onclick={() => { closeMenu(); alternativesSection?.reveal(); }}><Shuffle class="h-4 w-4" aria-hidden="true" /> Alternatives</button>
            </li>
          {/if}
          <li>
            <button class="text-bad-ink" onclick={() => { closeMenu(); confirmDialog?.showModal(); }}><Trash2 class="h-4 w-4" aria-hidden="true" /> Delete trip</button>
          </li>
        </ul>
      </details>
    </div>

    {#if cardDay}
      <!-- The travel day, and afterwards the arrival: everything live is in the one card. -->
      <LiveDashboard
        trip={shown ?? trip}
        day={cardDay}
        {now}
        {stationMinutes}
        {reason}
        {position}
        {location}
        onLocation={useLocation}
        settingsHref={`${base}/settings#device`}
        onAlternatives={() => alternativesSection?.reveal()}
        {status}
        {bindingLifted}
        onBackToPlan={following && !adjusted && !arrived && trip.replacement_legs ? followPlan : undefined}
        busy={busyTaking}
      />
      {#if cardDay.kind === "arrived" && refreshError}
        {@render tripNotices()}
      {/if}
    {:else}
      <!-- Before the travel day: when it leaves and arrives, and how the trip is made up. -->
      <header class="space-y-2 px-1">
        <p class="flex items-center gap-2">
          <ShiftedTime planned={s.departure.planned} current={s.departure.current} cancelled={s.cancelled} class="t-time text-ink" />
          <span class="t-time text-muted" aria-label="to">→</span>
          <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} class="t-time text-ink" />
        </p>
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
          <Fact icon={Clock} label="Duration" value={formatDuration(s.plannedMinutes)} />
          <Fact icon={ArrowRightLeft} label={s.changes === 0 ? "Direct, changes" : "Changes"} value={s.changes} />
          {#if s.confidence && s.changes > 0}<ConfidenceChip confidence={s.confidence} />{/if}
        </div>
      </header>
      {#if anyNotice || refreshError}
        {@render tripNotices()}
      {/if}
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
      <div class="grid gap-3 lg:grid-cols-2 lg:items-start">
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

    <div class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      {#if !journeyLive}
        <!-- While the card is live it is the journey; the timeline returns once arrived. -->
        <Timeline {legs} />
      {/if}

      <aside class="space-y-3 lg:sticky lg:top-6">
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

    {#if status && !cardDay}
      <!-- How fresh it all is: the last, smallest line. -->
      <Fact icon={status.kind === "timetable" ? Clock : status.kind === "done" ? CircleCheck : RefreshCw} label={status.label} value={status.text} class="px-1" />
    {/if}
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
    <div class="modal-box rounded-t-3xl bg-card sm:rounded-3xl">
      <h3 class="t-title text-ink">Delete this trip?</h3>
      <p class="t-body mt-2 text-muted">
        It disappears for everyone in your household, together with its ticket and its place in the statistics.
      </p>
      <div class="modal-action">
        <form method="dialog"><button class="btn btn-ghost rounded-full">Keep it</button></form>
        <button class="btn rounded-full border-none bg-bad text-bad-content" onclick={remove} disabled={deleting}>
          {#if deleting}<span class="loading loading-spinner loading-sm"></span>{/if}
          Delete
        </button>
      </div>
    </div>
    <form method="dialog" class="modal-backdrop"><button>close</button></form>
  </dialog>
{/if}
