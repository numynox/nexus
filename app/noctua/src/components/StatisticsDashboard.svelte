<script lang="ts">
  import { onMount } from "svelte";
  import {
    fetchArticleCountForUser,
    fetchArticleTotalsByFeedForUser,
    fetchOldestReadAtForUser,
    fetchPublishedArticleStatisticsForUser,
    fetchReadStatisticsForUser,
    getLoginHref,
    getSession,
    onAuthStateChange,
    type FeedArticleTotalRecord,
    type PublishedArticleStatisticsRecord,
    type ReadStatisticsRecord,
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
  let effectiveWeeks = $state(8);

  let records = $state<ReadStatisticsRecord[]>([]);
  let publishedArticleRecords = $state<PublishedArticleStatisticsRecord[]>([]);
  let totalArticleCount = $state(0);
  let thisWeekArticleCount = $state(0);
  let windowArticleCount = $state(0);
  let feedArticleTotals = $state<FeedArticleTotalRecord[]>([]);

  function startOfWeekMonday(input: Date): Date {
    const date = new Date(input);
    const day = date.getDay();
    const offset = (day + 6) % 7;
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - offset);
    return date;
  }

  function addDays(input: Date, days: number): Date {
    const date = new Date(input);
    date.setDate(date.getDate() + days);
    return date;
  }

  function weekdayIndexMonday(input: Date): number {
    return (input.getDay() + 6) % 7;
  }

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

      const oldestReadAt = await fetchOldestReadAtForUser(userId);
      let availableWeeks = statisticsWeeks;
      if (oldestReadAt) {
        const oldestDate = new Date(oldestReadAt);
        const now = new Date();
        const startOfCurrentWeek = startOfWeekMonday(now);
        const startOfOldestWeek = startOfWeekMonday(oldestDate);
        const diffMs =
          startOfCurrentWeek.getTime() - startOfOldestWeek.getTime();
        const diffWeeks = Math.round(diffMs / (7 * 24 * 60 * 60 * 1000)) + 1;
        availableWeeks = Math.min(diffWeeks, statisticsWeeks);
      }
      effectiveWeeks = Math.max(availableWeeks, 1); // at least 1 week

      const currentWeekStart = startOfWeekMonday(new Date());
      const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);
      const currentWeekStartIso = currentWeekStart.toISOString();
      const windowStartIso = windowStart.toISOString();

      const [
        readRecords,
        publishedRecords,
        totalArticles,
        thisWeekArticles,
        windowArticles,
        perFeedTotals,
      ] = await Promise.all([
        fetchReadStatisticsForUser(userId),
        fetchPublishedArticleStatisticsForUser(userId, windowStartIso),
        fetchArticleCountForUser(userId),
        fetchArticleCountForUser(userId, currentWeekStartIso),
        fetchArticleCountForUser(userId, windowStartIso),
        fetchArticleTotalsByFeedForUser(userId, windowStartIso),
      ]);

      records = readRecords;
      publishedArticleRecords = publishedRecords;
      totalArticleCount = totalArticles;
      thisWeekArticleCount = thisWeekArticles;
      windowArticleCount = windowArticles;
      feedArticleTotals = perFeedTotals;
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
      records = [];
      publishedArticleRecords = [];
      totalArticleCount = 0;
      thisWeekArticleCount = 0;
      windowArticleCount = 0;
      feedArticleTotals = [];
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

  let totalReadCount = $derived.by(() => records.length);

  let thisWeekReadCount = $derived.by(() => {
    const start = startOfWeekMonday(new Date());
    return records.filter((record) => new Date(record.readAt) >= start).length;
  });

  let averagePerWeek = $derived.by(() => {
    const currentWeekStart = startOfWeekMonday(new Date());
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);
    const count = records.filter(
      (record) => new Date(record.readAt) >= windowStart,
    ).length;
    return count / effectiveWeeks;
  });

  let averageArticlesPerWeek = $derived.by(
    () => windowArticleCount / effectiveWeeks,
  );

  let feedReadEntries = $derived.by(() => {
    const currentWeekStart = startOfWeekMonday(new Date());
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);

    const counts = new Map<string, number>();

    for (const record of records) {
      const readDate = new Date(record.readAt);
      if (readDate < windowStart) continue;

      const label = record.feedName || "Unknown feed";
      counts.set(label, (counts.get(label) || 0) + 1);
    }

    const totalMap = new Map(
      feedArticleTotals.map((item) => [item.feedName, item.count]),
    );

    return Array.from(counts.entries())
      .map(([label, count]) => {
        const totalCount = totalMap.get(label) || 0;
        const readSharePercent =
          totalCount > 0 ? Math.round((count / totalCount) * 100) : 0;

        return {
          label,
          count,
          totalCount,
          readSharePercent,
        };
      })
      .sort((a, b) => b.count - a.count);
  });

  /** Local calendar days on which at least one article was read. */
  let readDays = $derived.by(() => {
    const days = new Set<string>();

    for (const record of records) {
      const date = new Date(record.readAt);
      if (Number.isNaN(date.getTime())) continue;
      days.add(
        `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`,
      );
    }

    return days;
  });

  function dayKey(date: Date): string {
    return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
  }

  /**
   * Days in a row ending today — or yesterday, so the streak does not appear
   * broken all morning before the day's first article.
   */
  let currentStreak = $derived.by(() => {
    const days = readDays;
    if (days.size === 0) return 0;

    const cursor = new Date();
    if (!days.has(dayKey(cursor))) {
      cursor.setDate(cursor.getDate() - 1);
      if (!days.has(dayKey(cursor))) return 0;
    }

    let streak = 0;
    while (days.has(dayKey(cursor))) {
      streak += 1;
      cursor.setDate(cursor.getDate() - 1);
    }

    return streak;
  });

  let longestStreak = $derived.by(() => {
    const keys = Array.from(readDays)
      .map((key) => {
        const [year, month, day] = key.split("-").map(Number);
        return new Date(year, month - 1, day).getTime();
      })
      .sort((a, b) => a - b);

    if (keys.length === 0) return 0;

    const DAY_MS = 24 * 60 * 60 * 1000;
    let longest = 1;
    let run = 1;

    for (let i = 1; i < keys.length; i += 1) {
      // Compare calendar days, not exact milliseconds: DST shifts a day by an
      // hour and would otherwise break the run.
      const gap = Math.round((keys[i] - keys[i - 1]) / DAY_MS);
      run = gap === 1 ? run + 1 : 1;
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
    const currentWeekStart = startOfWeekMonday(new Date());
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);
    const hours = Array.from({ length: 24 }, () => 0);

    for (const record of records) {
      const date = new Date(record.readAt);
      if (Number.isNaN(date.getTime()) || date < windowStart) continue;
      hours[date.getHours()] += 1;
    }

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

  let windowDays = $derived(effectiveWeeks * 7);

  let activeDaysInWindow = $derived.by(() => {
    const currentWeekStart = startOfWeekMonday(new Date());
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);
    const days = new Set<string>();

    for (const record of records) {
      const date = new Date(record.readAt);
      if (Number.isNaN(date.getTime()) || date < windowStart) continue;
      days.add(dayKey(date));
    }

    return days.size;
  });

  let weekdayAverages = $derived.by(() => {
    const counts = Array.from({ length: 7 }, () => 0);
    const currentWeekStart = startOfWeekMonday(new Date());
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);

    for (const record of records) {
      const date = new Date(record.readAt);
      if (date < windowStart) continue;
      counts[weekdayIndexMonday(date)] += 1;
    }

    return counts.map((count) => count / effectiveWeeks);
  });

  let readHeatmapData = $derived.by(() => {
    const today = new Date();
    const currentWeekStart = startOfWeekMonday(today);
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);

    const hourDayCounts = new Map<string, number>();
    for (const record of records) {
      const date = new Date(record.readAt);
      if (date < windowStart) continue;

      const dayOfWeek = weekdayIndexMonday(date);
      const hour = date.getHours();
      const key = `${dayOfWeek}-${hour}`;
      hourDayCounts.set(key, (hourDayCounts.get(key) || 0) + 1);
    }

    const days: HeatmapDay[] = [];
    let maxCount = 0;

    const dayLabels = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];

    for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
      const cells = [];

      for (let hour = 0; hour < 24; hour += 1) {
        const key = `${dayIndex}-${hour}`;
        const count = hourDayCounts.get(key) || 0;
        maxCount = Math.max(maxCount, count);

        cells.push({ hour, count });
      }

      days.push({ dayLabel: dayLabels[dayIndex], cells });
    }

    return {
      days,
      maxCount,
    };
  });

  let publishedArticleHeatmapData = $derived.by(() => {
    const today = new Date();
    const currentWeekStart = startOfWeekMonday(today);
    const windowStart = addDays(currentWeekStart, -(effectiveWeeks - 1) * 7);

    const hourDayCounts = new Map<string, number>();
    for (const record of publishedArticleRecords) {
      const date = new Date(record.publishedAt);
      if (date < windowStart) continue;

      const dayOfWeek = weekdayIndexMonday(date);
      const hour = date.getHours();
      const key = `${dayOfWeek}-${hour}`;
      hourDayCounts.set(key, (hourDayCounts.get(key) || 0) + 1);
    }

    const days: HeatmapDay[] = [];
    let maxCount = 0;

    const dayLabels = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];

    for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
      const cells = [];

      for (let hour = 0; hour < 24; hour += 1) {
        const key = `${dayIndex}-${hour}`;
        const count = hourDayCounts.get(key) || 0;
        maxCount = Math.max(maxCount, count);

        cells.push({ hour, count });
      }

      days.push({ dayLabel: dayLabels[dayIndex], cells });
    }

    return {
      days,
      maxCount,
    };
  });
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
