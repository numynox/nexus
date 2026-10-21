<script lang="ts">
  import { onMount } from "svelte";
  import {
    fetchReadingStatisticsForUser,
    getLoginHref,
    getSession,
    onAuthStateChange,
    type ReadingStatistics,
  } from "../lib/data";
  import ArticleHeatmap, {
    type HeatmapDay,
  } from "./statistics/ArticleHeatmap.svelte";
  import FeedReadsBarChart from "./statistics/FeedReadsBarChart.svelte";
  import ReadingHabits from "./statistics/ReadingHabits.svelte";
  import SummaryCards from "./statistics/SummaryCards.svelte";
  import WeekdayAverageBarChart from "./statistics/WeekdayAverageBarChart.svelte";

  let isLoading = $state(true);
  let isLoggedIn = $state(false);
  let errorMessage = $state("");
  let loginHref = $state("/login");
  let userId = $state("");

  let statisticsWeeks = $state(8);

  // Counted in Postgres (noctua_reading_statistics): fetching the rows and
  // counting here stopped at PostgREST's 1,000-row limit.
  let stats = $state<ReadingStatistics | null>(null);

  async function refreshStatistics() {
    isLoading = true;
    errorMessage = "";

    try {
      const session = await getSession();
      if (!session?.user) {
        isLoggedIn = false;
        window.location.replace(loginHref);
        return;
      }

      userId = session.user.id;
      isLoggedIn = true;

      stats = await fetchReadingStatisticsForUser(userId, statisticsWeeks);
    } catch (error) {
      let message = "An error occurred";
      if (error instanceof Error) {
        message = error.message;
      } else if (
        typeof error === "object" &&
        error !== null &&
        "message" in error
      ) {
        message = String((error as any).message);
      } else if (typeof error === "string") {
        message = error;
      }
      errorMessage = message;
      stats = null;
    } finally {
      isLoading = false;
    }
  }

  onMount(() => {
    if (typeof document !== "undefined") {
      const baseUrl = document.documentElement.dataset.baseUrl || "/";
      loginHref = getLoginHref(baseUrl);

      const parsedStatisticsWeeks = Number(
        document.documentElement.dataset.statisticsWeeks,
      );
      statisticsWeeks =
        Number.isInteger(parsedStatisticsWeeks) && parsedStatisticsWeeks > 0
          ? parsedStatisticsWeeks
          : 8;
    }

    refreshStatistics();

    const { data } = onAuthStateChange((event, session) => {
      if (event === "SIGNED_OUT" || !session?.user) {
        window.location.replace(loginHref);
        return;
      }

      refreshStatistics();
    });

    return () => {
      data.subscription.unsubscribe();
    };
  });

  const DAY_MS = 24 * 60 * 60 * 1000;

  /** A YYYY-MM-DD calendar day as a day number, free of time zones and DST. */
  function dayNumber(day: string): number {
    return Math.round(Date.parse(`${day}T00:00:00Z`) / DAY_MS);
  }

  let effectiveWeeks = $derived(stats?.weeks ?? 1);
  let totalReadCount = $derived(stats?.totalRead ?? 0);
  let totalArticleCount = $derived(stats?.totalArticles ?? 0);
  let thisWeekReadCount = $derived(stats?.thisWeekRead ?? 0);
  let thisWeekArticleCount = $derived(stats?.thisWeekPublished ?? 0);
  let averagePerWeek = $derived((stats?.windowRead ?? 0) / effectiveWeeks);
  let averageArticlesPerWeek = $derived(
    (stats?.windowPublished ?? 0) / effectiveWeeks,
  );

  let feedReadEntries = $derived.by(() =>
    (stats?.feeds ?? []).map((feed) => ({
      label: feed.feed || "Unknown feed",
      count: feed.read,
      totalCount: feed.published,
      readSharePercent:
        feed.published > 0
          ? Math.round((feed.readPublished / feed.published) * 100)
          : 0,
    })),
  );

  let readDayNumbers = $derived(new Set((stats?.readDays ?? []).map(dayNumber)));

  /**
   * Days in a row ending today — or yesterday, so the streak does not appear
   * broken all morning before the day's first article.
   */
  let currentStreak = $derived.by(() => {
    if (!stats || readDayNumbers.size === 0) return 0;
    let cursor = dayNumber(stats.today);
    if (!readDayNumbers.has(cursor)) cursor -= 1;
    let streak = 0;
    while (readDayNumbers.has(cursor)) {
      streak += 1;
      cursor -= 1;
    }
    return streak;
  });

  let longestStreak = $derived.by(() => {
    const days = Array.from(readDayNumbers).sort((a, b) => a - b);
    let longest = 0;
    let run = 0;
    for (let i = 0; i < days.length; i += 1) {
      run = i > 0 && days[i] - days[i - 1] === 1 ? run + 1 : 1;
      longest = Math.max(longest, run);
    }
    return longest;
  });

  let mostReadFeed = $derived.by(() =>
    feedReadEntries.length > 0
      ? { label: feedReadEntries[0].label, count: feedReadEntries[0].count }
      : null,
  );

  let busiestHour = $derived.by(() => {
    const hours = Array.from({ length: 24 }, () => 0);
    for (const [, hour, count] of stats?.readHours ?? []) hours[hour] += count;

    let best = -1;
    let bestCount = 0;
    hours.forEach((count, hour) => {
      if (count > bestCount) {
        best = hour;
        bestCount = count;
      }
    });

    return best >= 0 ? { hour: best, count: bestCount } : null;
  });

  // The days of the window so far: the rest of this week has not happened.
  let windowDays = $derived(stats?.windowDays ?? 0);

  let activeDaysInWindow = $derived.by(() => {
    if (!stats) return 0;
    const start = dayNumber(stats.windowStart);
    return Array.from(readDayNumbers).filter((day) => day >= start).length;
  });

  let weekdayAverages = $derived.by(() => {
    const counts = Array.from({ length: 7 }, () => 0);
    for (const [weekday, , count] of stats?.readHours ?? []) {
      counts[weekday] += count;
    }
    return counts.map((count) => count / effectiveWeeks);
  });

  const dayLabels = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];

  function heatmap(cells: [number, number, number][]): {
    days: HeatmapDay[];
    maxCount: number;
  } {
    const counts = new Map<string, number>();
    for (const [weekday, hour, count] of cells) {
      counts.set(`${weekday}-${hour}`, count);
    }

    let maxCount = 0;
    const days: HeatmapDay[] = dayLabels.map((dayLabel, dayIndex) => ({
      dayLabel,
      cells: Array.from({ length: 24 }, (_, hour) => {
        const count = counts.get(`${dayIndex}-${hour}`) || 0;
        maxCount = Math.max(maxCount, count);
        return { hour, count };
      }),
    }));

    return { days, maxCount };
  }

  let readHeatmapData = $derived(heatmap(stats?.readHours ?? []));
  let publishedArticleHeatmapData = $derived(
    heatmap(stats?.publishedHours ?? []),
  );
</script>

{#if isLoading}
  <div class="flex items-center justify-center py-20">
    <span class="loading loading-spinner loading-lg"></span>
  </div>
{:else if errorMessage}
  <div class="alert alert-error max-w-3xl mx-auto">
    <span>{errorMessage}</span>
  </div>
{:else if !isLoggedIn}
  <div class="flex items-center justify-center py-20">
    <span class="loading loading-spinner loading-lg"></span>
  </div>
{:else}
  <div class="max-w-6xl mx-auto space-y-6">
    <ReadingHabits
      {currentStreak}
      {longestStreak}
      {mostReadFeed}
      {busiestHour}
      activeDays={activeDaysInWindow}
      {windowDays}
    />

    <SummaryCards
      {totalReadCount}
      {totalArticleCount}
      {thisWeekReadCount}
      thisWeekTotalCount={thisWeekArticleCount}
      {averagePerWeek}
      averageTotalPerWeek={averageArticlesPerWeek}
    />

    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <div class="md:col-span-2 space-y-6">
        <FeedReadsBarChart entries={feedReadEntries} />
        <WeekdayAverageBarChart {weekdayAverages} />
      </div>

      <div class="space-y-6">
        <ArticleHeatmap
          title="Articles read"
          emptyMessage="No read activity yet."
          days={readHeatmapData.days}
          maxCount={readHeatmapData.maxCount}
        />
      </div>

      <div class="space-y-6">
        <ArticleHeatmap
          title="Articles published"
          emptyMessage="No article activity yet."
          days={publishedArticleHeatmapData.days}
          maxCount={publishedArticleHeatmapData.maxCount}
        />
      </div>
    </div>
  </div>
{/if}
