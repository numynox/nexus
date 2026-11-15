<script lang="ts">
  import { Ban, CircleCheck, Clock, Flag, RefreshCw } from "lucide-svelte";
  import { onMount } from "svelte";
  import { fetchSharedStatus, type SharedStatus, type SharedStatusLeg } from "../../lib/data";
  import { formatRelativeDay, formatTime } from "../../lib/format";
  import { type SceneState, trainKind } from "../../lib/scene";
  import EmptyState from "../common/EmptyState.svelte";
  import Fact from "../common/Fact.svelte";
  import LegChip from "../common/LegChip.svelte";
  import Scene from "../common/Scene.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";
  import StopRow from "../common/StopRow.svelte";

  // The status a share link opens (decisions/2026-11-04-abeona-share-links.md):
  // read-only, no session, no app chrome. Asked again every minute while open.
  let status = $state<SharedStatus | null>(null);
  let error = $state("");
  let loading = $state(true);

  const HEADLINE: Record<SharedStatus["state"], string> = {
    upcoming: "Not left yet",
    on_board: "On the train",
    changing: "Changing trains",
    arrived: "Arrived",
    cancelled: "Trip cancelled",
  };

  const SCENE: Record<SharedStatus["state"], SceneState> = {
    upcoming: "waiting",
    on_board: "riding",
    changing: "waiting",
    arrived: "arrived",
    cancelled: "stopped",
  };

  /** A shared ride as the line chip reads it; the link does not say more than its name. */
  const chip = (leg: SharedStatusLeg) => ({ mode: "train" as const, line_name: leg.line_name, product: leg.product, cancelled: leg.cancelled });
  /** The ride the scene draws: the current one, else the last. */
  const ride = $derived(status ? (status.current ?? status.legs[status.legs.length - 1] ?? null) : null);

  async function load(token: string) {
    try {
      status = await fetchSharedStatus(token);
      error = "";
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    const token = new URLSearchParams(window.location.search).get("t") ?? "";
    void load(token);
    const timer = setInterval(() => {
      if (document.visibilityState === "visible" && status && status.state !== "arrived") void load(token);
    }, 60_000);
    return () => clearInterval(timer);
  });
</script>

<!-- The rail through a row's mark column, as on the trip's timeline. -->
{#snippet rail(part: "start" | "through" | "end", bad: boolean)}
  <span
    class="absolute left-1/2 w-0.5 -translate-x-1/2 {part === 'start' ? 'top-1/2 bottom-0' : part === 'end' ? 'top-0 bottom-1/2' : 'inset-y-0'} {bad ? 'bg-bad' : 'bg-ink'}"
    aria-hidden="true"
  ></span>
{/snippet}

{#snippet dot(bad: boolean, now: boolean)}
  <span class="relative h-3.5 w-3.5 rounded-full border-[3px] {bad ? 'border-bad bg-card' : now ? 'border-primary bg-primary' : 'border-ink bg-card'}" aria-hidden="true"></span>
{/snippet}

<main class="mx-auto min-h-dvh max-w-md space-y-3 px-4 py-4">
  {#if loading}
    <div class="h-64 animate-pulse rounded-3xl bg-brand-soft" aria-busy="true"></div>
  {:else if error && !status}
    <EmptyState title="This link does not work" state="stopped">
      <p class="t-meta">{error}</p>
    </EmptyState>
  {:else if status}
    <section class="overflow-hidden rounded-3xl shadow-card {status.state === 'cancelled' ? 'bg-bad-soft' : 'bg-brand-soft'}">
      <Scene state={SCENE[status.state]} size="header" train={ride ? trainKind(ride.product, ride.line_name, false) : undefined} />
      <div class="space-y-2 px-4 pb-4">
        <!-- Where they are, in a word; then where they are going and when they get there. -->
        <p class="t-label {status.state === 'cancelled' ? 'text-bad-ink' : 'text-brand'}">{HEADLINE[status.state]}</p>
        <h1 class="t-headline text-ink">{status.origin} → {status.destination}</h1>
        {#if status.state !== "cancelled"}
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
            <ShiftedTime planned={status.planned_arrival} current={status.expected_arrival} class="t-time text-ink" />
            <Flag class="h-5 w-5 text-muted" aria-label={status.state === "arrived" ? "Arrived" : "Arrives"} />
            <span class="t-meta">{formatRelativeDay(status.expected_arrival)}</span>
            {#if status.delay_minutes <= 0 && status.live}
              <StatusChip tone="ok" icon={CircleCheck} label="On time" />
            {/if}
          </div>
        {/if}
      </div>
    </section>

    {@const state = status.state}
    {@const current = status.current}
    <section class="rounded-3xl bg-card px-4 py-2 shadow-card" aria-label="Rides">
      {#each status.legs as leg, i (i)}
        {@const now = state !== "arrived" && !!current && leg.from.name === current.from.name && leg.from.planned === current.from.planned}
        <StopRow planned={leg.from.planned} current={leg.from.current} cancelled={leg.cancelled} name={leg.from.name} platformPlanned={leg.from.planned_platform} platformCurrent={leg.from.platform} strong>
          {#snippet mark()}
            {@render rail("start", leg.cancelled)}
            {@render dot(leg.cancelled, false)}
          {/snippet}
        </StopRow>
        <StopRow>
          {#snippet mark()}{@render rail("through", leg.cancelled)}{/snippet}
          {#snippet line()}
            <LegChip leg={chip(leg)} size="md" />
            {#if now}<span class="t-label text-brand">{state === "on_board" ? "Now" : "Next"}</span>{/if}
          {/snippet}
          {#snippet trailing()}
            {#if leg.cancelled}<StatusChip tone="bad" icon={Ban} label="Cancelled" />{/if}
          {/snippet}
        </StopRow>
        <StopRow planned={leg.to.planned} current={leg.to.current} cancelled={leg.cancelled} name={leg.to.name} platformPlanned={leg.to.planned_platform} platformCurrent={leg.to.platform} strong>
          {#snippet mark()}
            {@render rail("end", leg.cancelled)}
            {@render dot(leg.cancelled, false)}
          {/snippet}
        </StopRow>
      {/each}
    </section>

    <!-- How fresh it is, and how long the link works: the last, smallest lines. -->
    <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-1">
      <Fact
        icon={status.final ? CircleCheck : status.live ? RefreshCw : Clock}
        label={status.final ? "Final times" : status.live ? "Live, updated" : "Timetable times; no live data yet"}
        value={status.final ? "Final times" : status.live ? formatTime(status.updated_at) : "Timetable"}
      />
      <span class="t-meta">Link works until {formatTime(status.expires_at)}</span>
    </div>
    {#if error}<p class="t-meta px-1 text-bad-ink">{error}</p>{/if}
    <p class="t-meta px-1 text-xs">Abeona · shared trip · Timetable data: Transitous, © OpenStreetMap contributors, the operators</p>
  {/if}
</main>
