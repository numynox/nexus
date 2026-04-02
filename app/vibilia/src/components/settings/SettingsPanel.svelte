<script lang="ts">
  import { onMount } from "svelte";
  import { signOut, updatePreferredFuelType } from "../../lib/data";
  import {
    getDashboardPreviousDays,
    getTheme,
    setDashboardPreviousDays,
    setTheme,
  } from "../../lib/storage";
  import { preferredFuelType, session } from "../../lib/stores";
  import AccountSection from "./AccountSection.svelte";
  import AppearanceSection from "./AppearanceSection.svelte";
  import VehicleManagerSection from "./VehicleManagerSection.svelte";

  let saving = $state(false);
  let isBusy = $state(false);
  let authError = $state("");
  let currentTheme = $state<string>("auto");
  let dashboardPreviousDays = $state<number>(3);

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

  onMount(() => {
    currentTheme = getTheme();
    dashboardPreviousDays = getDashboardPreviousDays();
  });

  async function updateFuelPreference(type: string) {
    saving = true;
    preferredFuelType.set(type);
    try {
      if ($session?.user?.id) {
        await updatePreferredFuelType($session.user.id, type);
      }
    } finally {
      saving = false;
    }
  }

  async function handleLogout() {
    authError = "";
    isBusy = true;

    try {
      await signOut();
    } catch (error) {
      authError = error instanceof Error ? error.message : String(error);
    } finally {
      isBusy = false;
    }
  }

  function handleThemeChange(theme: string) {
    currentTheme = theme;
    setTheme(theme);
  }

  function handleDashboardPreviousDaysChange(days: number) {
    dashboardPreviousDays = days;
    setDashboardPreviousDays(days);
  }
</script>

<div
  class="max-w-2xl mx-auto space-y-8 animate-in slide-in-from-bottom duration-500"
>
  <div>
    <h1 class="text-3xl font-black text-base-content">Settings</h1>
    <p class="text-sm text-base-content/60">
      Manage your profile and preferences
    </p>
  </div>

  <VehicleManagerSection
    selectedFuelType={$preferredFuelType}
    savingFuelPreference={saving}
    onFuelTypeChange={updateFuelPreference}
  />

  <AppearanceSection
    {currentTheme}
    {daisyThemes}
    {dashboardPreviousDays}
    onThemeChange={handleThemeChange}
    onDashboardPreviousDaysChange={handleDashboardPreviousDaysChange}
  />

  <AccountSection
    userEmail={$session?.user?.email || ""}
    {authError}
    {isBusy}
    onLogout={handleLogout}
  />
</div>
