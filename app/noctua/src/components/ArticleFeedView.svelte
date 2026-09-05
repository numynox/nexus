<script lang="ts">
  import { onMount } from "svelte";
  import {
    fetchArticlesForSections,
    getLoginHref,
    loadUserContent,
    onAuthStateChange,
  } from "../lib/data";
  import type { Article, Section } from "../lib/types";
  import ArticleList from "./ArticleList.svelte";

  export interface Props {
    onlyRead?: boolean;
    /** When true, only starred articles are shown */
    onlyStarred?: boolean;
  }

  // props passed from the parent (Astro page)
  let { onlyRead = false, onlyStarred = false }: Props = $props();

  let loading = $state(true);
  let errorMessage = $state("");
  let isLoggedIn = $state(false);
  let selectedSectionId = $state<string | null>(null);
  let selectedFeedId = $state<string | null>(null);
  let unreadAndUnseenDisplayed = $state(0);
  let sections = $state<Section[]>([]);
  let articles = $state<Article[]>([]);
  let loginHref = $state("/login");
  let baseUrl = $state("/");
  let siteTitle = $state("Noctua");
  let articleFetchLimit = $state(300);
  // Paging state: the fetch limit is a page size, not a ceiling. Before this,
  // anything past the first 300 was simply unreachable.
  let loadingMore = $state(false);
  let hasMore = $state(false);

  // Progress tracking for unseen articles
  let initialUnseen = $state(0);
  let progress = $derived.by(() => {
    if (initialUnseen === 0) return unreadAndUnseenDisplayed === 0 ? 100 : 0;
    const ratio = Math.max(
      0,
      Math.min(1, (initialUnseen - unreadAndUnseenDisplayed) / initialUnseen),
    );
    return Math.round(ratio * 100);
  });

  let sectionTitle = $derived.by(() => {
    if (!selectedSectionId) return "No Sections";
    return (
      sections.find((section) => section.id === selectedSectionId)?.name ||
      "Section"
    );
  });

  let sectionIcon = $derived.by(() => {
    if (!selectedSectionId) return "🦉";
    return (
      sections.find((section) => section.id === selectedSectionId)?.icon || "🦉"
    );
  });

  let selectedFeedTitle = $derived.by(() => {
    if (!selectedSectionId || !selectedFeedId) return null;

    const section = sections.find((item) => item.id === selectedSectionId);
    return (
      section?.feeds.find((feed) => feed.id === selectedFeedId)?.name || null
    );
  });

  let visibleTitle = $derived.by(() => {
    if (selectedFeedTitle) return selectedFeedTitle;
    return sectionTitle;
  });

  function getSelectedSectionFromUrl() {
    if (typeof window === "undefined") return null;
    return new URLSearchParams(window.location.search).get("section");
  }

  function getSelectedFeedFromUrl() {
    if (typeof window === "undefined") return null;
    return new URLSearchParams(window.location.search).get("feed");
  }

  function handleStatsChange(count: number) {
    unreadAndUnseenDisplayed = count;
  }

  function updateDocumentTitle() {
    if (typeof document === "undefined") {
      return;
    }

    if (onlyRead) {
      document.title = `Read articles | ${siteTitle}`;
      return;
    }

    if (selectedSectionId) {
      document.title = `${visibleTitle} | ${siteTitle}`;
      return;
    }

    document.title = siteTitle;
  }

  async function refreshContent() {
    loading = true;
    errorMessage = "";

    try {
      const selectedFromUrl = getSelectedSectionFromUrl();
      const selectedFeedFromUrl = getSelectedFeedFromUrl();
      // when viewing the read page we ignore any section from URL so pass null
      const content = await loadUserContent(
        onlyRead ? null : selectedFromUrl,
        articleFetchLimit,
      );

      sections = content.sections;
      // keep section id only when not in read mode; otherwise leave null so no
      // ?section= param is added later
      selectedSectionId = onlyRead ? null : content.selectedSectionId;
      isLoggedIn = content.isLoggedIn;

      if (!isLoggedIn) {
        window.location.replace(loginHref);
        return;
      }

      // determine feed selection if provided (works across all sections)
      if (selectedFeedFromUrl) {
        const exists = sections.some((sec) =>
          sec.feeds.some((feed) => feed.id === selectedFeedFromUrl),
        );
        selectedFeedId = exists ? selectedFeedFromUrl : null;
      } else {
        selectedFeedId = null;
      }

      if (onlyRead) {
        // loadUserContent will have fetched only the first-section articles,
        // so call the helper directly to get everything.
        const allArticles = await fetchArticlesForSections(
          sections,
          null,
          articleFetchLimit,
        );
        articles = selectedFeedId
          ? allArticles.filter(
              (article: Article) => article.feed_id === selectedFeedId,
            )
          : allArticles;
      } else {
        articles = selectedFeedId
          ? content.articles.filter(
              (article) => article.feed_id === selectedFeedId,
            )
          : content.articles;
      }

      // A full page suggests there is more behind it; the next fetch settles it.
      hasMore = (content.articles?.length ?? 0) >= articleFetchLimit;

      // update URL parameters: clear section when in read mode, otherwise
      // keep them in sync with current selection
      if (onlyRead) {
        const currentUrl = new URL(window.location.href);
        if (currentUrl.searchParams.has("section")) {
          currentUrl.searchParams.delete("section");
          window.history.replaceState({}, "", currentUrl.toString());
        }
      } else if (sections.length > 0 && selectedSectionId) {
        const currentUrl = new URL(window.location.href);
        const currentParam = currentUrl.searchParams.get("section");
        const currentFeedParam = currentUrl.searchParams.get("feed");

        if (currentParam !== selectedSectionId) {
          currentUrl.searchParams.set("section", selectedSectionId);
        }

        if (selectedFeedId) {
          currentUrl.searchParams.set("feed", selectedFeedId);
        } else {
          currentUrl.searchParams.delete("feed");
        }

        const sectionChanged = currentParam !== selectedSectionId;
        const feedChanged = (currentFeedParam || null) !== selectedFeedId;
        if (sectionChanged || feedChanged) {
          window.history.replaceState({}, "", currentUrl.toString());
          window.dispatchEvent(new PopStateEvent("popstate"));
        }
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      errorMessage = message;
      isLoggedIn = false;
      sections = [];
      articles = [];
      selectedSectionId = null;
      selectedFeedId = null;
    } finally {
      loading = false;
    }
  }

  /**
   * Fetch the next page and append it.
   *
   * Offset paging over a feed that gains articles at the top can repeat a row;
   * appending only ids we do not already have keeps that from showing.
   */
  async function loadMore() {
    if (loadingMore || !hasMore) return;

    loadingMore = true;

    try {
      const older = await fetchArticlesForSections(
        sections,
        onlyRead || onlyStarred ? null : selectedSectionId,
        articleFetchLimit,
        articles.length,
      );

      const known = new Set(articles.map((article) => article.id));
      const fresh = older.filter((article) => !known.has(article.id));

      articles = [
        ...articles,
        ...(selectedFeedId
          ? fresh.filter((article) => article.feed_id === selectedFeedId)
          : fresh),
      ];

      hasMore = older.length >= articleFetchLimit;
    } catch (error) {
      errorMessage = error instanceof Error ? error.message : String(error);
    } finally {
      loadingMore = false;
    }
  }

  onMount(() => {
    if (typeof document !== "undefined") {
      baseUrl = document.documentElement.dataset.baseUrl || "/";
      siteTitle = document.documentElement.dataset.siteTitle || "Noctua";
      const parsedLimit = Number(
        document.documentElement.dataset.articleFetchLimit,
      );
      articleFetchLimit =
        Number.isInteger(parsedLimit) && parsedLimit > 0 ? parsedLimit : 300;
      loginHref = getLoginHref(baseUrl);
    }

    refreshContent();

    const { data } = onAuthStateChange((event) => {
      if (event === "SIGNED_OUT") {
        refreshContent();
      }
    });

    const handlePopState = () => {
      refreshContent();
    };

    window.addEventListener("popstate", handlePopState);

    return () => {
      data.subscription.unsubscribe();
      window.removeEventListener("popstate", handlePopState);
    };
  });

  $effect(() => {
    if (!loading && !errorMessage && isLoggedIn) {
      updateDocumentTitle();
    }
  });

  // Capture the initial unseen count once after content finished loading.
  $effect(() => {
    if (
      !loading &&
      !errorMessage &&
      isLoggedIn &&
      initialUnseen === 0 &&
      unreadAndUnseenDisplayed > 0
    ) {
      initialUnseen = unreadAndUnseenDisplayed;
    }
  });
</script>

{#if loading}
  <div class="flex items-center justify-center min-h-[40vh]">
    <span class="loading loading-spinner loading-lg"></span>
  </div>
{:else if errorMessage}
  <div class="alert alert-error max-w-3xl mx-auto">
    <span>{errorMessage}</span>
  </div>
{:else if !isLoggedIn}
  <div class="flex items-center justify-center min-h-[40vh]">
    <span class="loading loading-spinner loading-lg"></span>
  </div>
{:else}
  <div
    class="sticky top-0 z-20 mb-6 -mx-4 lg:-mx-8 px-6 lg:px-8 bg-base-100/80 backdrop-blur-sm border-b border-base-300/60 relative"
  >
    <div class="py-3 flex items-center justify-between gap-4">
      {#if onlyStarred}
        <span class="text-3xl font-bold">Starred</span>
      {:else if onlyRead}
        <span class="text-3xl font-bold">Read articles</span>
      {:else if selectedSectionId}
        <button
          type="button"
          onclick={() => {
            window.location.reload();
          }}
          class="flex items-center gap-2 text-3xl font-bold hover:text-secondary transition-colors"
        >
          <span class="text-2xl md:hidden" aria-hidden="true">
            {sectionIcon}
          </span>
          <span>{visibleTitle}</span>
        </button>
      {/if}

      <div class="flex items-center gap-2">
        {#if onlyStarred}
          <span
            class="badge badge-warning badge-md md:badge-xl"
            title="Starred articles"
            aria-label="Starred articles">{unreadAndUnseenDisplayed}</span
          >
        {:else if onlyRead}
          <span
            class="badge badge-secondary badge-md md:badge-xl"
            title="Read articles"
            aria-label="Read articles">{unreadAndUnseenDisplayed}</span
          >
        {:else}
          <span
            class="badge badge-primary badge-md md:badge-xl"
            title="Unread and unseen articles"
            aria-label="Unread and unseen articles"
            >{unreadAndUnseenDisplayed}</span
          >
        {/if}
      </div>
    </div>

    {#if articles.length > 0 && !onlyRead}
      <!-- Thin progress bar as bottom border of the sticky header -->
      <div
        class="absolute left-0 bottom-0 w-full h-0.5 overflow-visible"
        role="progressbar"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={progress}
        title={progress + "% read — click to focus"}
      >
        <!-- track (matches the header border) -->
        <div class="absolute left-0 top-0 w-full h-full bg-base-300"></div>

        <!-- filled portion (grows with progress) -->
        <div
          class="absolute left-0 top-0 h-full bg-accent transition-all duration-300"
          style={`width: ${progress}%`}
        ></div>

        <!-- subtle glow behind the filled portion -->
        <div
          class="absolute left-0 rounded-full bg-accent"
          style={`width: ${progress}%; height: 6px; bottom: -3px; filter: blur(6px); opacity: 0.45;`}
        ></div>
      </div>
    {/if}
  </div>

  <div id="article-list">
    <ArticleList
      {articles}
      onStatsChange={handleStatsChange}
      {onlyRead}
      noDim={onlyRead}
      onRefresh={refreshContent}
      {onlyStarred}
    />

    {#if hasMore}
      <div class="flex justify-center py-8">
        <button
          class="btn btn-outline"
          onclick={loadMore}
          disabled={loadingMore}
        >
          {#if loadingMore}
            <span class="loading loading-spinner loading-sm"></span>
          {/if}
          Load older articles
        </button>
      </div>
    {/if}
  </div>
{/if}
