<script lang="ts">
  import { onMount } from "svelte";
  import { signOut } from "../../lib/data";
  import { getTheme, setTheme } from "../../lib/storage";
  import { session } from "../../lib/stores";

  let isBusy = $state(false);
  let authError = $state("");
  let currentTheme = $state<string>("auto");

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
  });

  function handleThemeChange(theme: string) {
    currentTheme = theme;
    setTheme(theme);
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

  <!-- Appearance -->
  <section class="card bg-base-200 shadow-sm overflow-hidden">
    <div class="card-body p-6 lg:p-8">
      <h2 class="text-xl font-bold mb-6">Appearance</h2>

      <div class="space-y-4">
        <div class="font-semibold text-sm">Color Theme</div>

        <div class="space-y-2">
          <label class="flex items-center justify-between gap-4 cursor-pointer">
            <div class="flex-1">
              <span class="text-sm font-medium text-base-content/70 block"
                >Auto Theme</span
              >
              <span class="text-sm text-base-content/60"
                >Automatically switch between light and dark based on system
                preference</span
              >
            </div>
            <input
              type="checkbox"
              class="toggle toggle-primary"
              checked={currentTheme === "auto"}
              onchange={() =>
                handleThemeChange(currentTheme === "auto" ? "light" : "auto")}
            />
          </label>
        </div>

        <div class="space-y-2">
          <div class="text-sm font-medium text-base-content/70">
            Select Theme
          </div>
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2">
            {#each daisyThemes as theme}
              <button
                onclick={() => handleThemeChange(theme)}
                disabled={currentTheme === "auto"}
                class="btn btn-sm capitalize {currentTheme === theme
                  ? 'btn-primary'
                  : 'btn-soft'} {currentTheme === 'auto' ? 'btn-disabled' : ''}"
              >
                {theme}
              </button>
            {/each}
          </div>
          {#if currentTheme === "auto"}
            <p class="text-xs text-base-content/50">
              Theme selection is disabled when Auto is active
            </p>
          {/if}
        </div>
      </div>
    </div>
  </section>

  <!-- Account -->
  <section class="card bg-base-200 shadow-sm overflow-hidden">
    <div class="card-body p-6 lg:p-8">
      <h2 class="text-xl font-bold mb-6">Account</h2>
      <p class="text-sm text-base-content/60">
        Signed in as <span class="font-medium"
          >{$session?.user?.email || ""}</span
        >
      </p>

      {#if authError}
        <div class="alert alert-error text-sm mt-2">{authError}</div>
      {/if}

      <div class="mt-4">
        <button
          class="btn btn-outline w-full"
          disabled={isBusy}
          onclick={handleLogout}
        >
          {isBusy ? "Signing out..." : "Sign out"}
        </button>
      </div>
    </div>
  </section>
</div>
