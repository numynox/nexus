<script lang="ts">
  import {
    BarChart3,
    Check,
    Menu,
    Search,
    Settings,
    Star,
    X,
  } from "lucide-svelte";
  import { getSectionIconComponent } from "../lib/sectionMeta";
  import { onMount } from "svelte";
  import FeedIcon from "./FeedIcon.svelte";
  import {
    fetchSectionsForUser,
    getSession,
    onAuthStateChange,
  } from "../lib/data";
  import { getFilters, setFilters } from "../lib/storage";
  import type { Section } from "../lib/types";

  interface Props {
    activeId?: string; // 'home', 'statistics', or 'settings'
    baseUrl?: string;
    siteTitle?: string;
  }

  let {
    activeId = "home",
    baseUrl = "/",
    siteTitle = "Noctua",
  }: Props = $props();

  let sections = $state<Section[]>([]);
  let isLoggedIn = $state(false);
  let selectedSectionId = $state<string | null>(null);
  let selectedFeedId = $state<string | null>(null);
  let searchQuery = $state("");
  let isMobileMenuOpen = $state(false);

  const currentContextId = $derived.by(() => selectedSectionId || "");

  const currentFeeds = $derived.by(() => {
    const section = sections.find((item) => item.id === currentContextId);
    return section ? section.feeds : [];
  });

  async function refreshSidebarData() {
    const params = new URLSearchParams(window.location.search);
    const sectionFromUrl = params.get("section");
    const feedFromUrl = params.get("feed");

    const session = await getSession();
    isLoggedIn = !!session?.user;

    if (!session?.user) {
      sections = [];
      selectedSectionId = null;
      selectedFeedId = null;
      return;
    }

    const loadedSections = await fetchSectionsForUser(session.user.id);

    sections = loadedSections;
    selectedSectionId =
      sectionFromUrl &&
      sections.some((section) => section.id === sectionFromUrl)
        ? sectionFromUrl
        : sections[0]?.id || null;

    const selectedSection = sections.find(
      (section) => section.id === selectedSectionId,
    );
    selectedFeedId =
      feedFromUrl &&
      selectedSection?.feeds.some((feed) => feed.id === feedFromUrl)
        ? feedFromUrl
        : null;
  }

  onMount(() => {
    const filters = getFilters();
    searchQuery = filters.searchQuery || "";

    refreshSidebarData();

    const { data } = onAuthStateChange((event) => {
      if (event === "SIGNED_OUT") {
        refreshSidebarData();
      }
    });

    const handlePopState = () => {
      refreshSidebarData();
    };

    window.addEventListener("popstate", handlePopState);

    window.addEventListener("filtersChanged", ((e: CustomEvent) => {
      searchQuery = e.detail.searchQuery;
    }) as EventListener);

    return () => {
      data.subscription.unsubscribe();
      window.removeEventListener("popstate", handlePopState);
    };
  });

  function updateSearch() {
    setFilters({ searchQuery });
    window.dispatchEvent(
      new CustomEvent("filtersChanged", {
        detail: { searchQuery },
      }),
    );
  }

  function toggleMobileMenu() {
    isMobileMenuOpen = !isMobileMenuOpen;
  }

  function closeMobileMenu() {
    isMobileMenuOpen = false;
  }

  function sectionHref(sectionId: string) {
    return `${baseUrl}?section=${encodeURIComponent(sectionId)}`;
  }

  function feedHref(sectionId: string, feedId: string) {
    return `${baseUrl}?section=${encodeURIComponent(sectionId)}&feed=${encodeURIComponent(feedId)}`;
  }

  function settingsHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/settings`;
  }

  function statisticsHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/statistics`;
  }

  function readHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/read`;
  }

  function starredHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/starred`;
  }
</script>

<div class="lg:hidden fixed bottom-6 right-6 z-50">
  <button
    onclick={toggleMobileMenu}
    class="btn btn-primary btn-circle shadow-lg"
    aria-label="Toggle Menu"
  >
    {#if isMobileMenuOpen}
      <X class="h-6 w-6" />
    {:else}
      <Menu class="h-6 w-6" />
    {/if}
  </button>
</div>

{#if isMobileMenuOpen}
  <div
    class="lg:hidden fixed inset-0 bg-black/50 z-40 backdrop-blur-sm"
    onclick={closeMobileMenu}
    onkeydown={(e) => e.key === "Escape" && closeMobileMenu()}
    role="button"
    tabindex="0"
    aria-label="Close Menu"
  ></div>
{/if}

<aside
  class="fixed lg:sticky top-0 left-0 z-40 w-80 h-screen bg-base-200 border-r border-base-300 transform transition-transform duration-300 ease-in-out flex flex-col
    {isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}"
>
  <div class="p-6">
    <a
      href={baseUrl}
      class="flex items-center gap-3 text-2xl font-bold hover:text-primary transition-all duration-300 group"
      onclick={closeMobileMenu}
    >
      <div class="relative w-8 h-8 flex-shrink-0">
        <div
          class="absolute inset-0 bg-primary/30 group-hover:bg-primary rounded-full blur-md transition-all duration-300"
        ></div>
        <img
          src="{baseUrl}/noctua.png"
          alt={`${siteTitle} Logo`}
          class="relative w-full h-full object-contain"
        />
      </div>
      <span>{siteTitle}</span>
    </a>
  </div>

  <div class="px-6 mb-6">
    <div class="relative">
      <input
        type="text"
        placeholder="Search articles..."
        class="input w-full pl-10 h-10 bg-base-100"
        bind:value={searchQuery}
        oninput={updateSearch}
      />
      <Search
        class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-base-content/40"
      />
    </div>
  </div>

  <nav class="flex-1 overflow-y-auto px-4 pb-6">
    <div class="space-y-2">
      {#if isLoggedIn}
        {#each sections as section}
          {@const SectionIcon = getSectionIconComponent(section.icon)}
          <div class="space-y-1">
            <a
              href={sectionHref(section.id)}
              class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
                {activeId === 'home' && selectedSectionId === section.id
                ? 'bg-primary text-primary-content font-semibold shadow-md'
                : 'hover:bg-base-300 text-base-content/80'}"
              onclick={closeMobileMenu}
            >
              <SectionIcon class="h-5 w-5 shrink-0" aria-hidden="true" />
              <span class="flex-1 truncate">{section.name}</span>
            </a>

            {#if activeId === "home" && selectedSectionId === section.id && currentFeeds.length > 0}
              <div
                class="ml-4 pl-4 border-l-2 border-primary/20 space-y-1 my-2"
              >
                {#each currentFeeds as feed}
                  <a
                    href={feedHref(section.id, feed.id)}
                    onclick={closeMobileMenu}
                    class="flex items-center gap-3 px-3 py-1.5 rounded-lg transition-colors
                    {selectedFeedId === feed.id
                      ? 'bg-primary/15 text-primary font-semibold'
                      : 'hover:bg-base-300/50 text-base-content/80'}"
                  >
                    <FeedIcon icon={feed.icon} />
                    <span class="text-xs truncate flex-1" title={feed.name}>
                      {feed.name}
                    </span>
                  </a>
                {/each}
              </div>
            {/if}
          </div>
        {/each}
      {/if}
    </div>
  </nav>

  <div class="p-4 border-t border-base-300">
    <a
      href={readHref()}
      class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
        {activeId === 'read'
        ? 'bg-primary text-primary-content font-semibold shadow-md'
        : 'hover:bg-base-300 text-base-content/80'}"
      onclick={closeMobileMenu}
    >
      <Check class="h-5 w-5" />
      <span>Read</span>
    </a>

    <a
      href={starredHref()}
      class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
        {activeId === 'starred'
        ? 'bg-primary text-primary-content font-semibold shadow-md'
        : 'hover:bg-base-300 text-base-content/80'}"
      onclick={closeMobileMenu}
    >
      <Star class="h-5 w-5" />
      <span>Starred</span>
    </a>

    <a
      href={statisticsHref()}
      class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
        {activeId === 'statistics'
        ? 'bg-primary text-primary-content font-semibold shadow-md'
        : 'hover:bg-base-300 text-base-content/80'}"
      onclick={closeMobileMenu}
    >
      <BarChart3 class="h-5 w-5" />
      <span>Statistics</span>
    </a>

    <a
      href={settingsHref()}
      class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
        {activeId === 'settings'
        ? 'bg-primary text-primary-content font-semibold shadow-md'
        : 'hover:bg-base-300 text-base-content/80'}"
      onclick={closeMobileMenu}
    >
      <Settings class="h-5 w-5" />
      <span>Settings</span>
    </a>
  </div>
</aside>
