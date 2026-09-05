<script lang="ts">
  import { CalendarCheck, Clock, Flame, Newspaper } from "lucide-svelte";

  interface Props {
    currentStreak: number;
    longestStreak: number;
    mostReadFeed: { label: string; count: number } | null;
    busiestHour: { hour: number; count: number } | null;
    activeDays: number;
    windowDays: number;
  }

  let {
    currentStreak,
    longestStreak,
    mostReadFeed,
    busiestHour,
    activeDays,
    windowDays,
  }: Props = $props();

  function hourRange(hour: number): string {
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${pad(hour)}:00 – ${pad((hour + 1) % 24)}:00`;
  }
</script>

<div class="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
  <div class="card bg-base-200 shadow-sm">
    <div class="card-body p-5">
      <p class="flex items-center gap-2 text-sm text-base-content/70">
        <Flame class="h-4 w-4" /> Current streak
      </p>
      <p class="text-3xl font-bold">
        {currentStreak}
        {currentStreak === 1 ? "day" : "days"}
      </p>
      <p class="text-sm text-base-content/70">
        Longest {longestStreak}
        {longestStreak === 1 ? "day" : "days"}.
      </p>
    </div>
  </div>

  <div class="card bg-base-200 shadow-sm">
    <div class="card-body p-5">
      <p class="flex items-center gap-2 text-sm text-base-content/70">
        <Newspaper class="h-4 w-4" /> Most read feed
      </p>
      <p class="truncate text-3xl font-bold" title={mostReadFeed?.label ?? ""}>
        {mostReadFeed?.label ?? "—"}
      </p>
      <p class="text-sm text-base-content/70">
        {mostReadFeed ? `${mostReadFeed.count} articles read.` : "No reads yet."}
      </p>
    </div>
  </div>

  <div class="card bg-base-200 shadow-sm">
    <div class="card-body p-5">
      <p class="flex items-center gap-2 text-sm text-base-content/70">
        <Clock class="h-4 w-4" /> Busiest hour
      </p>
      <p class="text-3xl font-bold">
        {busiestHour ? hourRange(busiestHour.hour) : "—"}
      </p>
      <p class="text-sm text-base-content/70">
        {busiestHour ? `${busiestHour.count} articles read then.` : "No reads yet."}
      </p>
    </div>
  </div>

  <div class="card bg-base-200 shadow-sm">
    <div class="card-body p-5">
      <p class="flex items-center gap-2 text-sm text-base-content/70">
        <CalendarCheck class="h-4 w-4" /> Days with reading
      </p>
      <p class="text-3xl font-bold">{activeDays} / {windowDays}</p>
      <p class="text-sm text-base-content/70">
        {windowDays > 0
          ? `${Math.round((activeDays / windowDays) * 100)}% of the window.`
          : ""}
      </p>
    </div>
  </div>
</div>
