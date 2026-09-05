<script lang="ts">
  import { onMount } from "svelte";
  import {
    clearReadArticlesForUser,
    getLoginHref,
    getSession,
    onAuthStateChange,
    signOut,
    describeError,} from "../lib/data";
  import {
    clearSeenHistory,
    getPreferences,
    getTheme,
    setPreferences,
    setTheme,
  } from "../lib/storage";
  import AccountSection from "./settings/AccountSection.svelte";
  import AppearanceSection from "./settings/AppearanceSection.svelte";
  import DangerZoneSection from "./settings/DangerZoneSection.svelte";
  import SectionsSection from "./settings/SectionsSection.svelte";

  let isLoading = $state(true);
  let isBusy = $state(false);
  let userEmail = $state("");
  let authError = $state("");
  let showReadArticles = $state(true);
  let autoMarkAsSeen = $state(true);
  let currentTheme = $state<string>("auto");
  let userId = $state("");

  let loginHref = $state("/login");

  const daisyThemes = [
    "light",
    "dark",
    "cupcake",
    "bumblebee",
    "emerald",
    "corporate",
    "synthwave",
    "retro",
    "cyberpunk",
    "valentine",
    "halloween",
    "garden",
    "forest",
    "aqua",
    "lofi",
    "pastel",
    "fantasy",
    "wireframe",
    "black",
    "luxury",
    "dracula",
    "cmyk",
    "autumn",
    "business",
    "acid",
    "lemonade",
    "night",
    "coffee",
    "winter",
    "dim",
    "nord",
    "sunset",
  ];

  async function refreshSession() {
    const session = await getSession();
    if (!session?.user) {
      window.location.replace(loginHref);
      return;
    }

    userId = session.user.id;
    userEmail = session.user.email || "";
    isLoading = false;
  }

  onMount(() => {
    const prefs = getPreferences();
    showReadArticles = prefs.hideSeenArticles;
    autoMarkAsSeen = prefs.autoMarkAsSeen;
    currentTheme = getTheme();

    if (typeof document !== "undefined") {
      const baseUrl = document.documentElement.dataset.baseUrl || "/";
      loginHref = getLoginHref(baseUrl);
    }

    refreshSession().catch((error) => {
      const message = describeError(error);
      authError = message;
      isLoading = false;
    });

    const { data } = onAuthStateChange((_, session) => {
      if (!session?.user) {
        window.location.replace(loginHref);
        return;
      }

      userId = session.user.id;
      userEmail = session.user.email || "";
      authError = "";
      isLoading = false;
    });

    return () => {
      data.subscription.unsubscribe();
    };
  });

  async function handleLogout() {
    authError = "";
    isBusy = true;

    try {
      await signOut();
      window.location.replace(loginHref);
    } catch (error) {
      const message = describeError(error);
      authError = message;
    } finally {
      isBusy = false;
    }
  }

  function updatePreferences() {
    setPreferences({
      hideSeenArticles: showReadArticles,
      autoMarkAsSeen,
    });

    window.dispatchEvent(
      new CustomEvent("preferencesChanged", {
        detail: {
          hideSeenArticles: showReadArticles,
          autoMarkAsSeen,
        },
      }),
    );
  }

  function handleThemeChange(theme: string) {
    currentTheme = theme;
    setTheme(theme);
  }

  async function handleClearHistory() {
    if (
      confirm(
        "Are you sure you want to clear your read article history? This cannot be undone.",
      )
    ) {
      try {
        if (userId) {
          await clearReadArticlesForUser(userId);
        }

        clearSeenHistory();
        window.dispatchEvent(new CustomEvent("readHistoryCleared"));
        alert("Read and seen history cleared.");
      } catch (error) {
        const message = describeError(error);
        alert(`Failed to clear history: ${message}`);
      }
    }
  }

  function notifySectionDataChanged() {
    window.dispatchEvent(new PopStateEvent("popstate"));
  }
</script>

{#if isLoading}
  <div class="flex items-center justify-center py-20">
    <span class="loading loading-spinner loading-lg"></span>
  </div>
{:else}
  <div class="max-w-2xl mx-auto space-y-8">
    <SectionsSection {userId} onSectionDataChanged={notifySectionDataChanged} />

    <AppearanceSection
      bind:showReadArticles
      bind:autoMarkAsSeen
      {currentTheme}
      {daisyThemes}
      onThemeChange={handleThemeChange}
      onPreferencesChange={updatePreferences}
    />

    <AccountSection {userEmail} {authError} {isBusy} onLogout={handleLogout} />

    <DangerZoneSection onClearHistory={handleClearHistory} />
  </div>
{/if}
