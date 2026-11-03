<script lang="ts">
  import { Check, Download, History } from "lucide-svelte";
  import { describeError, readTraewelling, saveImportedTrip, type ImportedTrip, type ImportResult } from "../../lib/data";
  import { formatDuration, formatShortDate, formatTime, minutesBetween } from "../../lib/format";

  function day(offset: number): string {
    const d = new Date(Date.now() + offset * 86_400_000);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  // The token lives in this component's state only, for as long as the page
  // is open; it is cleared as soon as the check-ins are read.
  let token = $state("");
  let from = $state(day(-90));
  let to = $state(day(0));
  let reading = $state(false);
  let saving = $state(false);
  let result = $state<ImportResult | null>(null);
  let chosen = $state<ReadonlySet<string>>(new Set());
  let saved = $state(0);
  let error = $state("");

  async function read(event: Event) {
    event.preventDefault();
    if (reading) return;
    reading = true;
    error = "";
    saved = 0;
    try {
      result = await readTraewelling(token, from, to);
      token = "";
      chosen = new Set(result.trips.filter((t) => !t.duplicate).map((t) => t.external_id));
    } catch (e) {
      error = describeError(e);
    } finally {
      reading = false;
    }
  }

  function toggle(trip: ImportedTrip) {
    const next = new Set(chosen);
    if (next.has(trip.external_id)) next.delete(trip.external_id);
    else next.add(trip.external_id);
    chosen = next;
  }

  async function importChosen() {
    if (!result || saving) return;
    saving = true;
    error = "";
    try {
      for (const trip of result.trips) {
        if (!chosen.has(trip.external_id)) continue;
        await saveImportedTrip(trip);
        saved += 1;
        const next = new Set(chosen);
        next.delete(trip.external_id);
        chosen = next;
        result = { ...result, trips: result.trips.map((t) => (t.external_id === trip.external_id ? { ...t, duplicate: true } : t)) };
      }
    } catch (e) {
      error = describeError(e);
    } finally {
      saving = false;
    }
  }
</script>

<section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8" id="import">
  <div class="flex items-center gap-2">
    <History class="h-5 w-5 text-primary" />
    <h2 class="text-lg font-semibold text-ink">Import past trips</h2>
  </div>
  <p class="mt-1 mb-5 text-sm text-base-content/60">
    From Träwelling: create a personal access token under
    <a class="link" href="https://traewelling.de/settings/applications" target="_blank" rel="noreferrer">Settings → Applications</a>
    and paste it here. It is used once to read your check-ins and is not kept. Trips from the last month can also be found
    in the search and saved as past trips.
  </p>

  <form class="grid gap-3 sm:grid-cols-[1fr_auto_auto_auto] sm:items-end" onsubmit={read}>
    <label class="block">
      <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Träwelling token</span>
      <input class="input w-full rounded-2xl bg-card" type="password" autocomplete="off" required bind:value={token} placeholder="eyJ0eXAiOiJKV1Qi…" />
    </label>
    <label class="block">
      <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">From</span>
      <input class="input rounded-2xl bg-card" type="date" required bind:value={from} max={to} />
    </label>
    <label class="block">
      <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">To</span>
      <input class="input rounded-2xl bg-card" type="date" required bind:value={to} min={from} />
    </label>
    <button class="btn btn-primary rounded-full" disabled={reading || token.trim().length < 20}>
      {#if reading}<span class="loading loading-spinner loading-sm"></span>{:else}<Download class="h-4 w-4" />{/if}
      Read check-ins
    </button>
  </form>

  {#if result}
    <div class="mt-6">
      <p class="text-sm text-base-content/70">
        {result.statuses} check-ins, {result.trips.length} {result.trips.length === 1 ? "trip" : "trips"}{result.skipped > 0
          ? `, ${result.skipped} left out (planes, taxis)`
          : ""}.{result.truncated ? " Only the most recent were read; import an older range next." : ""}
        {#if saved > 0}<span class="font-medium text-primary"> {saved} imported.</span>{/if}
      </p>
      {#if result.trips.length > 0}
        <ul class="mt-3 max-h-96 space-y-2 overflow-y-auto">
          {#each result.trips as trip (trip.external_id)}
            {@const on = chosen.has(trip.external_id)}
            <li>
              <button
                class="flex w-full items-center gap-3 rounded-2xl border px-4 py-3 text-left transition
                  {on ? 'border-primary bg-mint' : 'border-base-300 bg-card'} {trip.duplicate ? 'opacity-60' : ''}"
                aria-pressed={on}
                disabled={trip.duplicate || saving}
                onclick={() => toggle(trip)}
              >
                <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-md border {on ? 'border-primary bg-primary text-primary-content' : 'border-base-300'}">
                  {#if on}<Check class="h-3.5 w-3.5" />{/if}
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-medium">{trip.origin} → {trip.destination}</span>
                  <span class="tabular block text-xs text-base-content/60">
                    {formatShortDate(trip.planned_departure)} · {formatTime(trip.planned_departure)}–{formatTime(trip.planned_arrival)}
                    · {formatDuration(minutesBetween(trip.planned_departure, trip.planned_arrival))}
                    · {trip.legs.map((l) => l.line_name ?? l.product ?? "?").join(", ")}
                  </span>
                </span>
                {#if trip.duplicate}
                  <span class="badge badge-ghost shrink-0">Saved</span>
                {:else if trip.arrival_delay_minutes}
                  <span class="badge shrink-0 border-none bg-amber-soft text-amber-ink">+{trip.arrival_delay_minutes}</span>
                {/if}
              </button>
            </li>
          {/each}
        </ul>
        <button class="btn btn-primary mt-4 rounded-full" disabled={chosen.size === 0 || saving} onclick={importChosen}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          Import {chosen.size} {chosen.size === 1 ? "trip" : "trips"}
        </button>
      {/if}
    </div>
  {/if}

  {#if error}<p class="mt-3 rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">{error}</p>{/if}
</section>
