<script lang="ts">
  import { onMount } from "svelte";
  import {
    fetchReadArticlesForUser,
    getSession,
    markArticleAsReadForUser,
    markArticlesAsReadForUser,
    unmarkArticlesAsReadForUser,
    type ReadArticleStatuses,
  } from "../lib/data";
  import {
    getDensity,
    getFilters,
    getLastVisitAt,
    getPreferences,
    getSeenArticles,
    getSortOrder,
    markAsSeen,
    setDensity,
    setLastVisitAt,
    setSortOrder,
    type Density,
    type SeenArticleStatuses,
    type SortOrder,
  } from "../lib/storage";
  import {
    ArrowDownWideNarrow,
    ArrowUpWideNarrow,
    CheckCheck,
    Keyboard,
    LayoutGrid,
    List,
    Undo2,
  } from "lucide-svelte";
  import type { Article } from "../lib/types";
  import ArticleCard from "./ArticleCard.svelte";

  interface Props {
    articles: Article[];
    onStatsChange?: (count: number) => void;
    /** When true, only articles that have been marked read are shown */
    onlyRead?: boolean;
    /** When true, cards should not be dimmed/gray */
    noDim?: boolean;
    /** Re-fetch the feed; used by pull-to-refresh and the r shortcut. */
    onRefresh?: () => void | Promise<void>;
  }

  let {
    articles,
    onStatsChange,
    onlyRead = false,
    noDim = false,
    onRefresh,
  }: Props = $props();

  let persistedReadArticles = $state<ReadArticleStatuses>({});
  let optimisticReadArticles = $state<ReadArticleStatuses>({});
  let readArticles = $derived.by(() => ({
    ...persistedReadArticles,
    ...optimisticReadArticles,
  }));
  let seenArticles = $state<SeenArticleStatuses>({});
  let userId = $state("");
  let searchQuery = $state("");
  let hideSeenArticles = $state(true);
  let autoMarkAsSeen = $state(true);
  // Only render the list after initial storage/prefs are loaded to avoid
  // a flash of unfiltered articles on first paint.
  let initialized = $state(false);

  // Snapshot of read/seen state at page load. We use these snapshots
  // for filtering so articles marked as seen/read during the current
  // session are not immediately hidden — they will be hidden after
  // a page refresh. Live `readArticles`/`seenArticles` are still used
  // for counts and UI state.
  let initialSeenArticles = $state<SeenArticleStatuses>({});

  // Scroll handler to mark articles as seen
  let scrollHandler: (() => void) | null = null;
  // Track last scroll position so we only mark as seen when scrolling down
  let lastScrollY = 0;

  function setupScrollDetection() {
    if (scrollHandler) {
      window.removeEventListener("scroll", scrollHandler);
    }

    // Initialize lastScrollY to current scroll position to avoid marking
    // items immediately when setting up detection.
    lastScrollY = window.scrollY || window.pageYOffset || 0;

    scrollHandler = () => {
      const currentScrollY = window.scrollY || window.pageYOffset || 0;
      // Only proceed when scrolling down
      if (currentScrollY <= lastScrollY) {
        lastScrollY = currentScrollY;
        return;
      }

      // Check all article elements that are currently rendered
      const articleElements = document.querySelectorAll("[data-article-id]");
      articleElements.forEach((element) => {
        const rect = element.getBoundingClientRect();
        const articleId = element.getAttribute("data-article-id");

        // Mark as seen if article is above viewport (scrolled past) and not already marked
        // but only if it is within half the viewport height above the top. This
        // prevents marking items that were jumped far past (e.g., from a
        // navigation or rapid scroll).
        const bottom = rect.bottom;
        const viewportH =
          window.innerHeight || document.documentElement.clientHeight || 0;
        if (
          bottom < 0 &&
          bottom > -0.5 * viewportH &&
          articleId &&
          !seenArticles[articleId]
        ) {
          markAsSeen(articleId);
          seenArticles = getSeenArticles();
        }
      });

      lastScrollY = currentScrollY;
    };

    window.addEventListener("scroll", scrollHandler, { passive: true });
  }

  // Filtered articles
  let filteredArticles = $derived.by(() => {
    // If we haven't loaded storage/prefs yet, render nothing to avoid
    // briefly showing articles that will immediately be hidden.
    if (!initialized) return [];

    let result = articles;

    if (onlyRead) {
      // keep only articles with a read status (either persisted or optimistic)
      result = result.filter((a) => !!readArticles[a.id]);
    } else {
      // Filter by read/seen status (hide articles that were read/seen at page load)
      if (hideSeenArticles) {
        result = result.filter((a) => !initialSeenArticles[a.id]);
      }
    }

    // Filter by search query
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (a) =>
          a.title.toLowerCase().includes(query) ||
          a.summary?.toLowerCase().includes(query) ||
          a.feed_name.toLowerCase().includes(query),
      );
    }

    return result;
  });

  // ── Reading preferences, keyboard focus and undo ────────────────────────
  let density = $state<Density>("comfortable");
  let sortOrder = $state<SortOrder>("newest");
  let showShortcuts = $state(false);
  let focusedIndex = $state(-1);
  let lastVisitAt = $state<string | null>(null);
  /** Articles marked read by the last bulk action, so it can be undone. */
  let undoable = $state<string[]>([]);
  let undoTimer: ReturnType<typeof setTimeout> | null = null;
  let refreshing = $state(false);

  function articleTime(article: Article): number {
    const value = article.published || article.updated;
    const parsed = value ? Date.parse(value) : NaN;
    return Number.isNaN(parsed) ? 0 : parsed;
  }

  let sortedArticles = $derived.by(() => {
    const result = [...filteredArticles];
    result.sort((a, b) =>
      sortOrder === "oldest"
        ? articleTime(a) - articleTime(b)
        : articleTime(b) - articleTime(a),
    );
    return result;
  });

  /** How many of the visible articles arrived since the feed was last opened. */
  let newSinceLastVisit = $derived.by(() => {
    if (!lastVisitAt || onlyRead) return 0;
    const since = Date.parse(lastVisitAt);
    if (Number.isNaN(since)) return 0;
    return sortedArticles.filter((a) => articleTime(a) > since).length;
  });

  const DAY = 24 * 60 * 60 * 1000;

  /** "Today" / "Yesterday" / a weekday / a date — whatever orients fastest. */
  function dayLabel(timestamp: number): string {
    if (!timestamp) return "Undated";

    const date = new Date(timestamp);
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);
    const diffDays = Math.floor((startOfToday.getTime() - date.getTime()) / DAY);

    if (date.getTime() >= startOfToday.getTime()) return "Today";
    if (diffDays < 1) return "Yesterday";
    if (diffDays < 6)
      return date.toLocaleDateString(undefined, { weekday: "long" });

    return date.toLocaleDateString(undefined, {
      day: "numeric",
      month: "long",
      year:
        date.getFullYear() === new Date().getFullYear() ? undefined : "numeric",
    });
  }

  /** Flat index is kept alongside so j/k can walk across group boundaries. */
  let groupedArticles = $derived.by(() => {
    const groups: Array<{
      label: string;
      items: Array<{ article: Article; index: number }>;
    }> = [];

    sortedArticles.forEach((article, index) => {
      const label = dayLabel(articleTime(article));
      const current = groups[groups.length - 1];

      if (current && current.label === label) {
        current.items.push({ article, index });
      } else {
        groups.push({ label, items: [{ article, index }] });
      }
    });

    return groups;
  });

  function toggleDensity() {
    density = density === "compact" ? "comfortable" : "compact";
    setDensity(density);
  }

  function toggleSortOrder() {
    sortOrder = sortOrder === "newest" ? "oldest" : "newest";
    setSortOrder(sortOrder);
  }

  function relativeTime(article: Article): string {
    const timestamp = articleTime(article);
    if (!timestamp) return "";

    const minutes = Math.round((Date.now() - timestamp) / 60000);
    if (minutes < 1) return "now";
    if (minutes < 60) return `${minutes}m`;
    if (minutes < 60 * 24) return `${Math.round(minutes / 60)}h`;
    return `${Math.round(minutes / (60 * 24))}d`;
  }

  // ── Mark everything visible as read, with an undo ────────────────────────
  function markAllVisibleRead() {
    const ids = sortedArticles
      .filter((article) => !readArticles[article.id])
      .map((article) => article.id);

    if (ids.length === 0) return;

    const now = new Date().toISOString();
    const optimistic = { ...optimisticReadArticles };
    ids.forEach((id) => {
      optimistic[id] = { timestamp: now };
    });
    optimisticReadArticles = optimistic;

    undoable = ids;
    if (undoTimer) clearTimeout(undoTimer);
    undoTimer = setTimeout(() => (undoable = []), 15000);

    if (!userId) return;

    markArticlesAsReadForUser(userId, ids)
      .then(() => {
        const persisted = { ...persistedReadArticles };
        ids.forEach((id) => (persisted[id] = { timestamp: now }));
        persistedReadArticles = persisted;
      })
      .catch((error) => console.warn("Failed to mark all read", error));
  }

  function undoMarkAllRead() {
    const ids = undoable;
    if (ids.length === 0) return;

    undoable = [];
    if (undoTimer) clearTimeout(undoTimer);

    const optimistic = { ...optimisticReadArticles };
    const persisted = { ...persistedReadArticles };
    ids.forEach((id) => {
      delete optimistic[id];
      delete persisted[id];
    });
    optimisticReadArticles = optimistic;
    persistedReadArticles = persisted;

    if (!userId) return;

    unmarkArticlesAsReadForUser(userId, ids).catch((error) =>
      console.warn("Failed to undo", error),
    );
  }

  async function refresh() {
    if (!onRefresh || refreshing) return;
    refreshing = true;
    try {
      await onRefresh();
    } finally {
      refreshing = false;
    }
  }

  // ── Keyboard ─────────────────────────────────────────────────────────────
  function focusArticle(index: number) {
    const clamped = Math.max(0, Math.min(index, sortedArticles.length - 1));
    focusedIndex = clamped;

    const article = sortedArticles[clamped];
    if (!article) return;

    document
      .querySelector(`[data-article-id="${article.id}"]`)
      ?.scrollIntoView({ block: "center", behavior: "smooth" });
  }

  function openFocused() {
    const article = sortedArticles[focusedIndex];
    if (!article?.url) return;
    handleArticleClick(article.id);
    window.open(article.url, "_blank", "noopener");
  }

  function toggleReadFocused() {
    const article = sortedArticles[focusedIndex];
    if (!article) return;

    if (readArticles[article.id]) {
      const optimistic = { ...optimisticReadArticles };
      const persisted = { ...persistedReadArticles };
      delete optimistic[article.id];
      delete persisted[article.id];
      optimisticReadArticles = optimistic;
      persistedReadArticles = persisted;

      if (userId) {
        unmarkArticlesAsReadForUser(userId, [article.id]).catch(() => {});
      }
      return;
    }

    handleArticleClick(article.id);
  }

  function handleKeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement | null;
    const typing =
      target &&
      (target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable);

    if (typing || event.metaKey || event.ctrlKey || event.altKey) return;

    switch (event.key) {
      case "j":
        event.preventDefault();
        focusArticle(focusedIndex + 1);
        break;
      case "k":
        event.preventDefault();
        focusArticle(focusedIndex <= 0 ? 0 : focusedIndex - 1);
        break;
      case "o":
      case "Enter":
        if (focusedIndex >= 0) {
          event.preventDefault();
          openFocused();
        }
        break;
      case "m":
        if (focusedIndex >= 0) {
          event.preventDefault();
          toggleReadFocused();
        }
        break;
      case "a":
        event.preventDefault();
        markAllVisibleRead();
        break;
      case "u":
        event.preventDefault();
        undoMarkAllRead();
        break;
      case "r":
        event.preventDefault();
        void refresh();
        break;
      case "?":
        event.preventDefault();
        showShortcuts = !showShortcuts;
        break;
      case "Escape":
        if (showShortcuts) {
          event.preventDefault();
          showShortcuts = false;
        }
        break;
    }
  }

  const SHORTCUTS: Array<[string, string]> = [
    ["j / k", "Next / previous article"],
    ["o or Enter", "Open the focused article"],
    ["m", "Toggle read on the focused article"],
    ["a", "Mark everything shown as read"],
    ["u", "Undo that"],
    ["r", "Refresh"],
    ["?", "This list"],
  ];

  // ── Touch: swipe an article to toggle read, pull down to refresh ─────────
  let swipeStartX = 0;
  let swipeStartY = 0;
  let swipeId: string | null = null;
  let swipeOffset = $state(0);
  let pullDistance = $state(0);
  let pullActive = false;

  const SWIPE_THRESHOLD = 90;
  const PULL_THRESHOLD = 80;

  function onTouchStart(event: TouchEvent, articleId: string) {
    const touch = event.touches[0];
    swipeStartX = touch.clientX;
    swipeStartY = touch.clientY;
    swipeId = articleId;
    swipeOffset = 0;

    pullActive = window.scrollY <= 0 && !!onRefresh;
    pullDistance = 0;
  }

  function onTouchMove(event: TouchEvent) {
    const touch = event.touches[0];
    const dx = touch.clientX - swipeStartX;
    const dy = touch.clientY - swipeStartY;

    if (pullActive && dy > 0 && Math.abs(dy) > Math.abs(dx)) {
      pullDistance = Math.min(dy, PULL_THRESHOLD * 1.5);
      return;
    }

    if (Math.abs(dx) > Math.abs(dy)) {
      swipeOffset = dx;
    }
  }

  function onTouchEnd() {
    if (pullDistance >= PULL_THRESHOLD) {
      pullDistance = 0;
      pullActive = false;
      void refresh();
      return;
    }

    if (swipeId && Math.abs(swipeOffset) >= SWIPE_THRESHOLD) {
      const index = sortedArticles.findIndex((a) => a.id === swipeId);
      if (index >= 0) {
        focusedIndex = index;
        toggleReadFocused();
      }
    }

    swipeId = null;
    swipeOffset = 0;
    pullDistance = 0;
    pullActive = false;
  }

  let displayedUnreadAndUnseenCount = $derived.by(() => {
    if (onlyRead) {
      // count read articles for stats on the read-only page
      return filteredArticles.length;
    }

    return filteredArticles.filter((article) => !(article.id in seenArticles))
      .length;
  });

  $effect(() => {
    onStatsChange?.(displayedUnreadAndUnseenCount);
  });

  onMount(() => {
    density = getDensity();
    sortOrder = getSortOrder();

    // Read the previous visit, then stamp this one straight away: a reload in
    // the same sitting should not keep announcing the same articles as new.
    lastVisitAt = getLastVisitAt();
    setLastVisitAt(new Date().toISOString());

    window.addEventListener("keydown", handleKeydown);

    seenArticles = getSeenArticles();

    // Snapshot initial seen state for filtering so items marked as seen during
    // this session are not hidden immediately.
    initialSeenArticles = { ...seenArticles };

    const filters = getFilters();
    const prefs = getPreferences();

    searchQuery = filters.searchQuery;
    hideSeenArticles = prefs.hideSeenArticles;
    autoMarkAsSeen = prefs.autoMarkAsSeen;

    getSession()
      .then((session) => {
        userId = session?.user?.id || "";
      })
      .catch(() => {
        userId = "";
      });

    // Mark initialization complete so filteredArticles can render.
    initialized = true;

    // Listen for filter changes
    window.addEventListener("filtersChanged", ((e: CustomEvent) => {
      searchQuery = e.detail.searchQuery;
      // When the search query changes, jump to the top so users see
      // results from the start of the list.
      try {
        window.scrollTo(0, 0);
      } catch (e) {
        /* ignore */
      }
    }) as EventListener);

    window.addEventListener("preferencesChanged", ((e: CustomEvent) => {
      hideSeenArticles = e.detail.hideSeenArticles;
      autoMarkAsSeen = e.detail.autoMarkAsSeen;

      // Update scroll detection when autoMarkAsSeen changes
      if (autoMarkAsSeen && !scrollHandler) {
        setTimeout(() => {
          setupScrollDetection();
        }, 1000);
      } else if (!autoMarkAsSeen && scrollHandler) {
        window.removeEventListener("scroll", scrollHandler);
        scrollHandler = null;
      }
    }) as EventListener);

    window.addEventListener("readHistoryCleared", () => {
      persistedReadArticles = {};
      optimisticReadArticles = {};
      seenArticles = getSeenArticles();
      initialSeenArticles = { ...seenArticles };
    });

    // On activity update live seen map but do NOT update the initial snapshot
    // so articles are not hidden immediately during this session.
    window.addEventListener("noctua:activity", () => {
      seenArticles = getSeenArticles();
    });

    // Set up scroll detection for auto-marking as seen (with delay to prevent auto-marking on reload)
    if (autoMarkAsSeen) {
      setTimeout(() => {
        setupScrollDetection();
      }, 1000); // 1 second delay
    }

    return () => {
      window.removeEventListener("keydown", handleKeydown);
      if (undoTimer) clearTimeout(undoTimer);
      if (scrollHandler) {
        window.removeEventListener("scroll", scrollHandler);
      }
    };
  });

  $effect(() => {
    if (typeof document === "undefined") {
      return;
    }

    const shouldLockScroll = initialized && filteredArticles.length === 0;

    if (shouldLockScroll) {
      document.documentElement.style.overflowY = "hidden";
      document.body.style.overflowY = "hidden";
    } else {
      document.documentElement.style.overflowY = "";
      document.body.style.overflowY = "";
    }

    return () => {
      document.documentElement.style.overflowY = "";
      document.body.style.overflowY = "";
    };
  });

  let readFetchRequest = 0;
  $effect(() => {
    if (!userId) {
      persistedReadArticles = {};
      return;
    }

    const articleIds = articles.map((article) => article.id);
    if (!articleIds.length) {
      persistedReadArticles = {};
      return;
    }

    const requestId = ++readFetchRequest;

    fetchReadArticlesForUser(userId, articleIds)
      .then((statuses) => {
        if (requestId !== readFetchRequest) {
          return;
        }

        persistedReadArticles = statuses;
      })
      .catch((error) => {
        if (requestId !== readFetchRequest) {
          return;
        }

        console.warn("Failed to fetch read statuses", error);
      });
  });

  function handleArticleClick(articleId: string) {
    const now = new Date().toISOString();
    optimisticReadArticles = {
      ...optimisticReadArticles,
      [articleId]: { timestamp: now },
    };

    if (!seenArticles[articleId]) {
      markAsSeen(articleId);
      seenArticles = getSeenArticles();
    }

    if (!userId) {
      return;
    }

    markArticleAsReadForUser(userId, articleId)
      .then(() => {
        persistedReadArticles = {
          ...persistedReadArticles,
          [articleId]: { timestamp: now },
        };

        if (optimisticReadArticles[articleId]) {
          const next = { ...optimisticReadArticles };
          delete next[articleId];
          optimisticReadArticles = next;
        }
      })
      .catch((error) => {
        console.warn("Failed to sync read status", error);
      });
  }

  function handleReload() {
    try {
      const url = new URL(window.location.href);
      url.searchParams.set("_noctua_reload", String(Date.now()));
      window.location.replace(url.toString());
    } catch (e) {
      window.location.reload();
    }
  }
</script>

{#if pullDistance > 0}
  <div
    class="pointer-events-none flex items-center justify-center overflow-hidden text-sm text-base-content/60"
    style={`height: ${pullDistance}px`}
  >
    {pullDistance >= 80 ? "Release to refresh" : "Pull to refresh"}
  </div>
{/if}

{#if filteredArticles.length > 0}
  <div
    class="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-base-300/60 pb-3"
  >
    <div class="flex items-center gap-2 text-sm text-base-content/60">
      <span>{sortedArticles.length} articles</span>
      {#if newSinceLastVisit > 0}
        <span class="badge badge-primary badge-sm">
          {newSinceLastVisit} new since your last visit
        </span>
      {/if}
      {#if refreshing}
        <span class="loading loading-spinner loading-xs"></span>
      {/if}
    </div>

    <div class="flex items-center gap-1">
      <button
        type="button"
        class="btn btn-ghost btn-sm gap-1"
        onclick={toggleSortOrder}
        title={sortOrder === "newest"
          ? "Newest first — click for oldest first"
          : "Oldest first — click for newest first"}
      >
        {#if sortOrder === "newest"}
          <ArrowDownWideNarrow class="h-4 w-4" />
          <span class="hidden sm:inline">Newest</span>
        {:else}
          <ArrowUpWideNarrow class="h-4 w-4" />
          <span class="hidden sm:inline">Oldest</span>
        {/if}
      </button>

      <button
        type="button"
        class="btn btn-ghost btn-sm gap-1"
        onclick={toggleDensity}
        title={density === "compact" ? "Switch to cards" : "Switch to a list"}
        aria-pressed={density === "compact"}
      >
        {#if density === "compact"}
          <LayoutGrid class="h-4 w-4" />
          <span class="hidden sm:inline">Cards</span>
        {:else}
          <List class="h-4 w-4" />
          <span class="hidden sm:inline">Compact</span>
        {/if}
      </button>

      {#if !onlyRead}
        <button
          type="button"
          class="btn btn-ghost btn-sm gap-1"
          onclick={markAllVisibleRead}
          title="Mark everything shown as read (a)"
        >
          <CheckCheck class="h-4 w-4" />
          <span class="hidden sm:inline">Mark read</span>
        </button>
      {/if}

      <button
        type="button"
        class="btn btn-ghost btn-sm btn-square"
        onclick={() => (showShortcuts = !showShortcuts)}
        title="Keyboard shortcuts (?)"
        aria-label="Keyboard shortcuts"
      >
        <Keyboard class="h-4 w-4" />
      </button>
    </div>
  </div>

  {#each groupedArticles as group (group.label)}
    <section class="mb-6">
      <h2
        class="sticky z-10 -mx-2 mb-3 bg-base-100/90 px-2 py-1 text-sm font-semibold text-base-content/70 backdrop-blur-sm"
        style="top: var(--noctua-header-height, 4rem)"
      >
        {group.label}
      </h2>

      {#if density === "compact"}
        <ul class="divide-y divide-base-300/60">
          {#each group.items as { article, index } (article.id)}
            <li
              data-article-id={article.id}
              class="transition-colors {focusedIndex === index
                ? 'bg-base-200 ring-1 ring-primary/40'
                : ''}"
              style={swipeId === article.id
                ? `transform: translateX(${swipeOffset}px)`
                : ""}
              ontouchstart={(e) => onTouchStart(e, article.id)}
              ontouchmove={onTouchMove}
              ontouchend={onTouchEnd}
            >
              <a
                href={article.url}
                target="_blank"
                rel="noopener noreferrer"
                onclick={() => handleArticleClick(article.id)}
                class="flex items-baseline gap-3 px-2 py-2 hover:bg-base-200/60 {article.id in
                  readArticles && !noDim
                  ? 'opacity-50'
                  : ''}"
              >
                <span
                  class="h-2 w-2 shrink-0 rounded-full {article.id in
                  readArticles
                    ? 'bg-transparent'
                    : 'bg-primary'}"
                  aria-hidden="true"
                ></span>
                <span class="min-w-0 flex-1 truncate">{article.title}</span>
                <span
                  class="hidden shrink-0 text-xs text-base-content/50 sm:inline"
                >
                  {article.feed_name}
                </span>
                <span class="shrink-0 text-xs tabular-nums text-base-content/40">
                  {relativeTime(article)}
                </span>
              </a>
            </li>
          {/each}
        </ul>
      {:else}
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6 xl:grid-cols-3">
          {#each group.items as { article, index } (article.id)}
            <div
              data-article-id={article.id}
              class="rounded-2xl transition-transform {focusedIndex === index
                ? 'ring-2 ring-primary/50'
                : ''}"
              style={swipeId === article.id
                ? `transform: translateX(${swipeOffset}px)`
                : ""}
              ontouchstart={(e) => onTouchStart(e, article.id)}
              ontouchmove={onTouchMove}
              ontouchend={onTouchEnd}
            >
              <ArticleCard
                {article}
                isRead={article.id in readArticles}
                isSeen={article.id in seenArticles}
                readTimestamp={readArticles[article.id]?.timestamp || null}
                onArticleClick={() => handleArticleClick(article.id)}
                {noDim}
              />
            </div>
          {/each}
        </div>
      {/if}
    </section>
  {/each}
{/if}

{#if undoable.length > 0}
  <div class="toast toast-center toast-bottom z-50">
    <div class="alert alert-info shadow-lg">
      <span>{undoable.length} marked read</span>
      <button class="btn btn-ghost btn-sm gap-1" onclick={undoMarkAllRead}>
        <Undo2 class="h-4 w-4" />
        Undo
      </button>
    </div>
  </div>
{/if}

{#if showShortcuts}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    role="button"
    tabindex="-1"
    onclick={() => (showShortcuts = false)}
    onkeydown={(e) => e.key === "Escape" && (showShortcuts = false)}
  >
    <div class="card w-full max-w-md bg-base-200 shadow-xl">
      <div class="card-body">
        <h2 class="card-title text-base">Keyboard shortcuts</h2>
        <dl class="mt-2 space-y-1 text-sm">
          {#each SHORTCUTS as [keys, description] (keys)}
            <div class="flex items-center justify-between gap-4">
              <dt class="text-base-content/70">{description}</dt>
              <dd><kbd class="kbd kbd-sm">{keys}</kbd></dd>
            </div>
          {/each}
        </dl>
      </div>
    </div>
  </div>
{/if}

{#if !onlyRead}
  <div class="h-[100svh] bg-base-100 flex items-center justify-center">
    <div
      class="text-center py-8 md:py-20 bg-base-200/50 rounded-3xl border border-dashed border-base-300 w-full max-w-3xl"
    >
      <div class="text-6xl mb-4">✅</div>
      <h3 class="text-2xl font-bold mb-2">You're all caught up</h3>
      <p class="text-base-content/60 mb-6 px-6 md:px-16">
        There are no articles to show right now — either you've already viewed
        them, or your current filters hide some items.
      </p>
      <div class="flex items-center justify-center gap-3">
        <button class="btn btn-primary" onclick={handleReload}>
          Reload Page
        </button>
      </div>
    </div>
  </div>
{/if}
