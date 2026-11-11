<script lang="ts" module>
  import dayjs, { type Dayjs } from "dayjs";
  import type { CarDrivenDay } from "../../lib/data";

  /** As the Android app (DistanceDrivenGraph.kt): this week plus 49 before it. */
  export const MAX_WEEKS = 50;
  /** Below this a day counts as not driven. */
  const DRIVEN = 0.5;

  function monday(day: Dayjs): Dayjs {
    return day.startOf("day").subtract((day.day() + 6) % 7, "day");
  }

  /** The start of the oldest day the graph can show. */
  export function drivenSince(today: Dayjs = dayjs()): string {
    return monday(today)
      .subtract(MAX_WEEKS - 1, "week")
      .toISOString();
  }

  /**
   * This week first, older weeks after it, each Monday to Sunday; only weeks
   * from the one of the first known day. Null is a day after today or before
   * the first known day: no square.
   */
  export function drivenWeeks(
    days: CarDrivenDay[],
    today: Dayjs = dayjs(),
  ): (number | null)[][] {
    if (days.length === 0) return [];
    const byDay = new Map(days.map((d) => [d.day, d.km]));
    const first = days.reduce((a, d) => (d.day < a ? d.day : a), days[0].day);
    const todayKey = today.format("YYYY-MM-DD");
    const thisWeek = monday(today);
    const span = thisWeek.diff(monday(dayjs(first)), "week") + 1;
    const weeks = Math.min(Math.max(span, 1), MAX_WEEKS);
    return Array.from({ length: weeks }, (_, ago) => {
      const start = thisWeek.subtract(ago, "week");
      return Array.from({ length: 7 }, (_, offset) => {
        const key = start.add(offset, "day").format("YYYY-MM-DD");
        return key < first || key > todayKey ? null : (byDay.get(key) ?? 0);
      });
    });
  }

  /** 25th, 50th, 75th percentile of the days driven: a long trip does not wash out the commutes. */
  function quartiles(weeks: (number | null)[][]): number[] {
    const driven = weeks
      .flat()
      .filter((km): km is number => km !== null && km >= DRIVEN)
      .sort((a, b) => a - b);
    if (driven.length === 0) return [];
    return [0.25, 0.5, 0.75].map(
      (q) => driven[Math.floor((driven.length - 1) * q)],
    );
  }

  const LEVELS = ["bg-primary/35", "bg-primary/55", "bg-primary/80", "bg-primary"];

  export function shade(km: number, thresholds: number[]): string {
    if (km < DRIVEN) return "bg-base-content/10";
    return LEVELS[thresholds.filter((t) => km > t).length];
  }

  export { quartiles };
</script>

<script lang="ts">
  interface Props {
    days: CarDrivenDay[];
  }

  let { days }: Props = $props();

  const weeks = $derived(drivenWeeks(days));
  const thresholds = $derived(quartiles(weeks));
  const total = $derived(
    weeks.flat().reduce<number>((sum, km) => sum + (km ?? 0), 0),
  );

  let scroller = $state<HTMLDivElement | null>(null);
  let fadeStart = $state(false);
  let fadeEnd = $state(false);

  function updateFades() {
    if (!scroller) return;
    fadeStart = scroller.scrollLeft > 1;
    fadeEnd =
      scroller.scrollLeft + scroller.clientWidth < scroller.scrollWidth - 1;
  }

  $effect(() => {
    if (!scroller) return;
    weeks;
    updateFades();
    const observer = new ResizeObserver(updateFades);
    observer.observe(scroller);
    return () => observer.disconnect();
  });

  const mask = $derived(
    `linear-gradient(to right, ${fadeStart ? "transparent" : "black"}, black 2.5rem, black calc(100% - 2.5rem), ${fadeEnd ? "transparent" : "black"})`,
  );
</script>

{#if weeks.length > 0}
  <section class="space-y-2">
    <h2
      class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
    >
      Distance driven
    </h2>
    <div
      bind:this={scroller}
      onscroll={updateFades}
      class="overflow-x-auto pb-1"
      style="mask-image: {mask}; -webkit-mask-image: {mask};"
      role="img"
      aria-label="Distance driven: {Math.round(total)} km in {weeks.length} weeks"
    >
      <div class="flex w-max gap-[3px]">
        {#each weeks as week, w (w)}
          <div class="flex flex-col gap-[3px]">
            {#each week as km, d (d)}
              {#if km === null}
                <div class="h-3.5 w-3.5"></div>
              {:else}
                <div class="h-3.5 w-3.5 rounded-[3px] {shade(km, thresholds)}"></div>
              {/if}
            {/each}
          </div>
        {/each}
      </div>
    </div>
  </section>
{/if}
