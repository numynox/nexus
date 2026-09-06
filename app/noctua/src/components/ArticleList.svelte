<script lang="ts">
  import { onMount } from "svelte";
  import {
    fetchReadArticlesForUser,
    getSession,
    markArticleAsReadForUser,
    markArticlesAsReadForUser,
    fetchSimilarArticleGroups,
    fetchStarredArticleIdsForUser,
    starArticleForUser,
    unmarkArticlesAsReadForUser,
    unstarArticleForUser,
    type ReadArticleStatuses,
  } from "../lib/data";
  import {
    getDensity,
    getFilters,
    getPreferences,
    getSeenArticles,
    getSortOrder,
    markAsSeen,
    setDensity,
    setSortOrder,
    type Density,
    type SeenArticleStatuses,
    type SortOrder,
  } from "../lib/storage";
  import {
    ArrowDownWideNarrow,
    ArrowUpWideNarrow,
    CheckCheck,
    ChevronDown,
    Keyboard,
    LayoutGrid,
    List,
    Layers,
    Star,
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
    /** When true, only starred articles are shown */
    onlyStarred?: boolean;
  }

  let {
    articles,
    onStatsChange,
    onlyRead = false,
    noDim = false,
    onRefresh,
    onlyStarred = false,
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

    if (onlyStarred) {
      result = result.filter((a) => !!starredArticles[a.id]);
    } else if (onlyRead) {
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

  let starredArticles = $state<Record<string, true>>({});

  /** article id → group key; articles with no twin map to themselves. */
  let similarGroups = $state<Record<string, string>>({});
  let expandedGroups = $state<Record<string, true>>({});

  function toggleGroup(groupKey: string) {
    const next = { ...expandedGroups };
    if (next[groupKey]) {
      delete next[groupKey];
    } else {
      next[groupKey] = true;
    }
    expandedGroups = next;
  }

  function toggleStar(articleId: string) {
    const isStarred = !!starredArticles[articleId];
    const next = { ...starredArticles };

    if (isStarred) {
      delete next[articleId];
    } else {
      next[articleId] = true;
    }
    starredArticles = next;

    if (!userId) return;

    const action = isStarred
      ? unstarArticleForUser(userId, articleId)
      : starArticleForUser(userId, articleId);

    action.catch((error) => {
      console.warn("Failed to sync star", error);
      // Put it back: the button should not lie about what is stored.
      const reverted = { ...starredArticles };
      if (isStarred) {
        reverted[articleId] = true;
      } else {
        delete reverted[articleId];
      }
      starredArticles = reverted;
    });
  }

  // ── Reading preferences, keyboard focus and undo ────────────────────────
  let density = $state<Density>("comfortable");
  let sortOrder = $state<SortOrder>("newest");
  let showShortcuts = $state(false);
  let focusedIndex = $state(-1);
  /** Articles the mark-all confirmation is waiting on, empty when it is shut. */
  let pendingMarkRead = $state<string[]>([]);
  let markReadDialog = $state<HTMLDialogElement | null>(null);
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

  /**
   * Date groups, with same-story articles collapsed inside them.
   *
   * The first article of a cluster in the current sort order leads; the rest
   * hang off it as `similar` and are shown when the cluster is expanded. The
   * flat index is kept alongside so j/k can walk the visible list, and only
   * leaders take an index — the keyboard should not stop on a hidden row.
   */
  let groupedArticles = $derived.by(() => {
    const groups: Array<{
      label: string;
      items: Array<{ article: Article; index: number; similar: Article[] }>;
    }> = [];

    const leaderFor = new Map<string, { article: Article; similar: Article[] }>();
    let visibleIndex = -1;

    sortedArticles.forEach((article) => {
      const key = similarGroups[article.id] ?? article.id;
      const leader = leaderFor.get(key);

      if (leader) {
        leader.similar.push(article);
        return;
      }

      const label = dayLabel(articleTime(article));
      const entry = { article, index: ++visibleIndex, similar: [] as Article[] };
      leaderFor.set(key, entry);

      const current = groups[groups.length - 1];
      if (current && current.label === label) {
        current.items.push(entry);
      } else {
        groups.push({ label, items: [entry] });
      }
    });

    return groups;
  });

  /** What j/k and the shortcuts act on: leaders only, in display order. */
  let navigableArticles = $derived.by(() =>
    groupedArticles.flatMap((group) => group.items.map((item) => item.article)),
  );

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
  /**
   * Marking a section read is the one action here that touches every article at
   * once, and the toast that follows only lives for fifteen seconds. Confirm
   * first; the undo stays for the fifteen seconds after that.
   */
  function requestMarkAllRead() {
    const ids = sortedArticles
      .filter((article) => !readArticles[article.id])
      .map((article) => article.id);

    if (ids.length === 0) return;

    pendingMarkRead = ids;
  }

  function confirmMarkAllRead() {
    const ids = pendingMarkRead;
    pendingMarkRead = [];

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

  function cancelMarkAllRead() {
    pendingMarkRead = [];
  }

  $effect(() => {
    const dialog = markReadDialog;
    if (!dialog) return;

    if (pendingMarkRead.length > 0) {
      if (!dialog.open) dialog.showModal();
    } else if (dialog.open) {
      dialog.close();
    }
  });

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
    const clamped = Math.max(0, Math.min(index, navigableArticles.length - 1));
    focusedIndex = clamped;

    const article = navigableArticles[clamped];
    if (!article) return;

    document
      .querySelector(`[data-article-id="${article.id}"]`)
      ?.scrollIntoView({ block: "center", behavior: "smooth" });
  }

  function openFocused() {
    const article = navigableArticles[focusedIndex];
    if (!article?.url) return;
    handleArticleClick(article.id);
    window.open(article.url, "_blank", "noopener");
  }

  function toggleReadFocused() {
    const article = navigableArticles[focusedIndex];
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
    // The confirmation dialog is modal; let it have the keyboard to itself.
    if (pendingMarkRead.length > 0) return;

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
      case "s":
        if (focusedIndex >= 0) {
          event.preventDefault();
          const article = navigableArticles[focusedIndex];
          if (article) toggleStar(article.id);
        }
        break;
      case "a":
        event.preventDefault();
        requestMarkAllRead();
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
    ["s", "Star the focused article"],
    ["a", "Mark everything shown as read"],
    ["u", "Undo that"],
    ["r", "Refresh"],
    ["?", "This list"],
  ];

  // ── Touch: overscroll past the end to refresh ───────────────────────────
  // At the top of the page this gesture belongs to the browser, which reloads
  // the whole document and loses the session with it. Past the last article
  // nothing else claims the drag, and "keep going once you are done" is the
  // more natural request anyway.
  let pullStartY = 0;
  let pullActive = false;
  let pullDistance = $state(0);

  const PULL_THRESHOLD = 80;

  function atPageEnd(): boolean {
    const doc = document.documentElement;
    const scrolled = window.scrollY || doc.scrollTop || 0;
    // Fractional pixels and a mobile browser's shifting chrome both land a
    // little short of the exact bottom.
    return scrolled + window.innerHeight >= doc.scrollHeight - 4;
  }

  function onTouchStart(event: TouchEvent) {
    pullActive = !!onRefresh && !refreshing && atPageEnd();
    pullStartY = event.touches[0].clientY;
    pullDistance = 0;
  }

  function onTouchMove(event: TouchEvent) {
    if (!pullActive) return;

    // A short list may not reach the end of the page until the drag has
    // already started; give up rather than refresh from halfway up.
    if (!atPageEnd()) {
      pullDistance = 0;
      pullActive = false;
      return;
    }

    const dy = pullStartY - event.touches[0].clientY;
    pullDistance = dy > 0 ? Math.min(dy, PULL_THRESHOLD * 1.5) : 0;
  }

  function onTouchEnd() {
    if (pullDistance >= PULL_THRESHOLD) void refresh();

    pullDistance = 0;
    pullActive = false;
  }

  let displayedUnreadAndUnseenCount = $derived.by(() => {
    if (onlyRead || onlyStarred) {
      // count read articles for stats on the read-only page
      return filteredArticles.length;
    }

    return filteredArticles.filter((article) => !(article.id in seenArticles))
      .length;
  });

  $effect(() => {
    onStatsChange?.(displayedUnreadAndUnseenCount);
  });

  // Ask Postgres which of these are the same story. Failing is not fatal: with
  // no groups every article maps to itself and the list is simply uncollapsed.
  $effect(() => {
    const ids = filteredArticles.map((article) => article.id);
    if (ids.length === 0) return;

    let cancelled = false;

    fetchSimilarArticleGroups(ids)
      .then((groups) => {
        if (!cancelled) similarGroups = groups;
      })
      .catch((error) => console.warn("Failed to group similar articles", error));

    return () => {
      cancelled = true;
    };
  });

  onMount(() => {
    density = getDensity();
    sortOrder = getSortOrder();

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

        if (userId) {
          fetchStarredArticleIdsForUser(userId)
            .then((starred) => (starredArticles = starred))
            .catch((error) => console.warn("Failed to load stars", error));
        }
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

{#if filteredArticles.length > 0}
  <div
    class="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-base-300/60 pb-3"
  >
    <div class="flex items-center gap-2 text-sm text-base-content/60">
      <span>{sortedArticles.length} articles</span>
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
          onclick={requestMarkAllRead}
          title="Mark everything shown as read"
        >
          <CheckCheck class="h-4 w-4" />
          <span class="hidden sm:inline">Mark read</span>
        </button>
      {/if}

      <!-- Nothing to press on a phone; the sm breakpoint is close enough to
           "has a keyboard" for a button whose only job is to explain one. -->
      <button
        type="button"
        class="btn btn-ghost btn-sm btn-square hidden sm:inline-flex"
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
          {#each group.items as { article, index, similar } (article.id)}
            <li
              data-article-id={article.id}
              class="isolate flex items-center gap-1 pr-1 transition-colors {focusedIndex ===
              index
                ? 'bg-base-200 ring-1 ring-primary/40'
                : ''}"
            >
              <a
                href={article.url}
                target="_blank"
                rel="noopener noreferrer"
                onclick={() => handleArticleClick(article.id)}
                class="flex min-w-0 flex-1 items-baseline gap-3 px-2 py-2 hover:bg-base-200/60 {(article.id in
                  readArticles ||
                  article.id in seenArticles) && !noDim
                  ? 'opacity-50'
                  : ''}"
              >
                <span
                  class="h-2 w-2 shrink-0 rounded-full {article.id in
                    readArticles || article.id in seenArticles
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

              <button
                type="button"
                class="btn btn-ghost btn-xs btn-square shrink-0"
                onclick={() => toggleStar(article.id)}
                aria-pressed={!!starredArticles[article.id]}
                title={starredArticles[article.id] ? "Unstar" : "Star (s)"}
              >
                <Star
                  class="h-4 w-4 {starredArticles[article.id]
                    ? 'fill-warning text-warning'
                    : 'text-base-content/40'}"
                />
              </button>
            </li>

            {#if similar.length > 0}
              <li class="px-2 pb-2 pl-7">
                <!-- Sized for a thumb: 44px tall and full width below sm, back
                     to a small inline chip once there is a pointer. -->
                <button
                  type="button"
                  class="btn btn-sm min-h-11 w-full justify-start gap-1.5 border-primary/30 bg-primary/10 text-primary hover:bg-primary/20 sm:btn-xs sm:min-h-0 sm:w-auto"
                  aria-expanded={!!expandedGroups[
                    similarGroups[article.id] ?? article.id
                  ]}
                  onclick={() => toggleGroup(similarGroups[article.id] ?? article.id)}
                >
                  <Layers class="h-4 w-4" />
                  {expandedGroups[similarGroups[article.id] ?? article.id]
                    ? "Hide similar"
                    : `+${similar.length} similar`}
                  <ChevronDown
                    class="h-4 w-4 transition-transform {expandedGroups[
                      similarGroups[article.id] ?? article.id
                    ]
                      ? 'rotate-180'
                      : ''}"
                  />
                </button>

                {#if expandedGroups[similarGroups[article.id] ?? article.id]}
                  <ul class="mt-1 space-y-1 border-l border-base-300 pl-3">
                    {#each similar as duplicate (duplicate.id)}
                      <li>
                        <a
                          href={duplicate.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          onclick={() => handleArticleClick(duplicate.id)}
                          class="flex items-baseline gap-3 py-1 text-sm text-base-content/70 hover:text-base-content"
                        >
                          <span class="min-w-0 flex-1 truncate">
                            {duplicate.title}
                          </span>
                          <span class="shrink-0 text-xs text-base-content/50">
                            {duplicate.feed_name}
                          </span>
                        </a>
                      </li>
                    {/each}
                  </ul>
                {/if}
              </li>
            {/if}
          {/each}
        </ul>
      {:else}
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6 xl:grid-cols-3">
          {#each group.items as { article, index, similar } (article.id)}
            <!-- isolate: ArticleCard lifts its body with `relative z-10`, which
                 otherwise competes with the sticky date header on equal terms
                 and wins on DOM order. Read cards happened to be dimmed with
                 opacity, which made a stacking context by accident and hid the
                 bug; unread ones cut straight across the date bar. -->
            <div
              data-article-id={article.id}
              class="isolate rounded-2xl {focusedIndex === index
                ? 'ring-2 ring-primary/50'
                : ''}"
            >
              <div class="relative">
                <ArticleCard
                  {article}
                  isRead={article.id in readArticles}
                  isSeen={article.id in seenArticles}
                  readTimestamp={readArticles[article.id]?.timestamp || null}
                  onArticleClick={() => handleArticleClick(article.id)}
                  {noDim}
                />

                <button
                  type="button"
                  class="btn btn-circle btn-ghost btn-sm absolute right-2 top-2 bg-base-100/70 backdrop-blur-sm"
                  onclick={() => toggleStar(article.id)}
                  aria-pressed={!!starredArticles[article.id]}
                  title={starredArticles[article.id] ? "Unstar" : "Star (s)"}
                >
                  <Star
                    class="h-4 w-4 {starredArticles[article.id]
                      ? 'fill-warning text-warning'
                      : 'text-base-content/50'}"
                  />
                </button>

                {#if similar.length > 0}
                  <div class="mt-2">
                    <button
                      type="button"
                      class="btn btn-sm min-h-11 w-full gap-1.5 border-primary/30 bg-primary/10 text-primary hover:bg-primary/20 sm:btn-xs sm:min-h-0"
                      aria-expanded={!!expandedGroups[
                        similarGroups[article.id] ?? article.id
                      ]}
                      onclick={() =>
                        toggleGroup(similarGroups[article.id] ?? article.id)}
                    >
                      <Layers class="h-4 w-4" />
                      {expandedGroups[similarGroups[article.id] ?? article.id]
                        ? "Hide similar"
                        : `+${similar.length} similar`}
                      <ChevronDown
                        class="h-4 w-4 transition-transform {expandedGroups[
                          similarGroups[article.id] ?? article.id
                        ]
                          ? 'rotate-180'
                          : ''}"
                      />
                    </button>

                    {#if expandedGroups[similarGroups[article.id] ?? article.id]}
                      <ul class="mt-1 space-y-1 border-l border-base-300 pl-3">
                        {#each similar as duplicate (duplicate.id)}
                          <li>
                            <a
                              href={duplicate.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              onclick={() => handleArticleClick(duplicate.id)}
                              class="block truncate py-0.5 text-sm text-base-content/70 hover:text-base-content"
                            >
                              {duplicate.title}
                              <span class="text-xs text-base-content/50">
                                · {duplicate.feed_name}
                              </span>
                            </a>
                          </li>
                        {/each}
                      </ul>
                    {/if}
                  </div>
                {/if}
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </section>
  {/each}
{/if}

<dialog class="modal" bind:this={markReadDialog} onclose={cancelMarkAllRead}>
  <div class="modal-box">
    <h3 class="text-lg font-bold">
      Mark {pendingMarkRead.length}
      {pendingMarkRead.length === 1 ? "article" : "articles"} as read?
    </h3>
    <p class="py-4 text-sm text-base-content/70">
      Everything currently shown is marked read. You can undo it for fifteen
      seconds afterwards.
    </p>
    <div class="modal-action">
      <button class="btn btn-ghost" onclick={cancelMarkAllRead}>Cancel</button>
      <button class="btn btn-primary gap-1" onclick={confirmMarkAllRead}>
        <CheckCheck class="h-4 w-4" />
        Mark read
      </button>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop">
    <button aria-label="Cancel">close</button>
  </form>
</dialog>

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

{#if !onlyRead && !onlyStarred}
  <!-- Sized to the viewport *below* the sticky header (--noctua-feed-height,
       defined in app.css) so it sits in the same place whether it is scrolled
       to at the end of a full feed or shown on its own with nothing to read. -->
  <div
    class="flex min-h-[var(--noctua-feed-height)] items-center justify-center bg-base-100 transition-transform duration-150"
    style={pullDistance > 0
      ? `transform: translateY(-${Math.round(pullDistance / 2)}px)`
      : ""}
    ontouchstart={onTouchStart}
    ontouchmove={onTouchMove}
    ontouchend={onTouchEnd}
    ontouchcancel={onTouchEnd}
  >
    <div
      class="w-full max-w-3xl rounded-3xl border border-dashed border-base-300 bg-base-200/50 py-8 text-center md:py-20"
    >
      <CheckCheck class="mx-auto mb-4 h-14 w-14 text-success" />
      <h3 class="mb-2 text-2xl font-bold">You're all caught up</h3>
      <p class="mb-6 px-6 text-base-content/60 md:px-16">
        There are no articles to show right now — either you've already viewed
        them, or your current filters hide some items.
      </p>
      <div class="flex items-center justify-center gap-3">
        <button class="btn btn-primary" onclick={handleReload}>
          Reload page
        </button>
      </div>

      {#if onRefresh}
        <!-- Touch only: there is no gesture to explain to a mouse, which has
             the button above and `r` on the keyboard. -->
        <p class="mt-6 px-6 text-sm text-base-content/50 sm:hidden">
          {#if refreshing}
            Refreshing…
          {:else if pullDistance >= PULL_THRESHOLD}
            Release to refresh
          {:else}
            Keep pulling up to refresh
          {/if}
        </p>
      {/if}
    </div>
  </div>
{/if}
