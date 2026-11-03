<script lang="ts">
  import { Clock, MapPin, RefreshCw, TrainFront } from "lucide-svelte";
  import { onMount } from "svelte";
  import { fetchSharedStatus, type SharedStatus, type SharedStatusStop } from "../../lib/data";
  import { formatRelativeDay, formatTime } from "../../lib/format";

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

  const expected = (stop: SharedStatusStop) => stop.current ?? stop.planned;
  const platform = (stop: SharedStatusStop) => stop.platform ?? stop.planned_platform;

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

<main class="mx-auto min-h-dvh max-w-md px-4 py-6 sm:py-10">
  <p class="text-center text-xs font-semibold uppercase tracking-[0.16em] text-base-content/50">Abeona · shared trip</p>

  {#if loading}
    <div class="mt-6 h-64 animate-pulse rounded-[2rem] bg-base-200"></div>
  {:else if error && !status}
    <section class="mt-6 rounded-[2rem] bg-card p-6 text-center shadow-card">
      <h1 class="text-xl font-semibold text-ink">This link does not work</h1>
      <p class="mt-2 text-base-content/70">{error}</p>
    </section>
  {:else if status}
    <section class="mt-6 overflow-hidden rounded-[2rem] bg-neutral text-neutral-content shadow-card">
      <div class="p-6">
        <p class="text-sm opacity-70">{status.origin} → {status.destination}</p>
        <h1 class="mt-1 text-3xl font-semibold tracking-tight">{HEADLINE[status.state]}</h1>
        {#if status.state !== "cancelled"}
          <p class="mt-5 text-sm opacity-70">{status.state === "arrived" ? "Arrived in" : "Arrives in"} {status.destination}</p>
          <p class="tabular mt-1 flex items-baseline gap-3">
            <span class="text-6xl font-semibold tracking-tight">{formatTime(status.expected_arrival)}</span>
            {#if status.delay_minutes > 0}
              <span class="rounded-full bg-amber-soft px-3 py-1 text-base font-semibold text-amber-ink">+{status.delay_minutes} min</span>
            {:else if status.live}
              <span class="rounded-full bg-white/15 px-3 py-1 text-base font-semibold">on time</span>
            {/if}
          </p>
          <p class="mt-1 text-sm opacity-70">
            {formatRelativeDay(status.expected_arrival)}{status.delay_minutes > 0 ? ` · planned ${formatTime(status.planned_arrival)}` : ""}
          </p>
        {/if}
      </div>

      {#if status.current && status.state !== "arrived"}
        {@const leg = status.current}
        <div class="bg-white/8 px-6 py-5">
          <p class="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider opacity-70">
            <TrainFront class="h-4 w-4" />{status.state === "on_board" ? "Now" : "Next"}
          </p>
          <p class="mt-1 text-xl font-semibold">{leg.line_name ?? leg.product ?? "Train"}{leg.cancelled ? " · cancelled" : ""}</p>
          <p class="tabular mt-1 text-sm opacity-80">
            {leg.from.name} {formatTime(expected(leg.from))}{platform(leg.from) ? `, platform ${platform(leg.from)}` : ""}
            → {leg.to.name} {formatTime(expected(leg.to))}{platform(leg.to) ? `, platform ${platform(leg.to)}` : ""}
          </p>
        </div>
      {/if}
    </section>

    {#if status.legs.length > 1}
      <section class="mt-4 rounded-[2rem] bg-card p-5 shadow-card" aria-label="Rides">
        <ol class="space-y-3">
          {#each status.legs as leg, i (i)}
            <li class="flex items-start gap-3">
              <MapPin class="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <div class="min-w-0 flex-1">
                <p class="font-medium">{leg.line_name ?? leg.product ?? "Train"}{leg.cancelled ? " · cancelled" : ""}</p>
                <p class="tabular text-sm text-base-content/70">
                  {leg.from.name} {formatTime(expected(leg.from))} → {leg.to.name} {formatTime(expected(leg.to))}
                  {#if leg.delay_minutes > 0}<span class="font-semibold text-amber-ink"> +{leg.delay_minutes}</span>{/if}
                </p>
              </div>
            </li>
          {/each}
        </ol>
      </section>
    {/if}

    <p class="mt-5 flex items-center justify-center gap-1.5 text-center text-xs text-base-content/55">
      {#if status.live}<RefreshCw class="h-3 w-3" />{:else}<Clock class="h-3 w-3" />{/if}
      {status.final ? "Final times" : status.live ? `Live · updated ${formatTime(status.updated_at)}` : "Timetable times; no live data yet"}
      · link works until {formatTime(status.expires_at)}
    </p>
    {#if error}<p class="mt-2 text-center text-xs text-error">{error}</p>{/if}
    <p class="mt-6 text-center text-xs text-base-content/45">Timetable data: Transitous, © OpenStreetMap contributors, the operators</p>
  {/if}
</main>
