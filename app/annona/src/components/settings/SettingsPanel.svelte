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
  <div class="card bg-base-200">
    <div class="card-body p-5">
      <h2 class="card-title text-lg">Appearance</h2>
      <div class="form-control">
        <span class="label-text mb-2">Theme</span>
        <div class="flex flex-wrap gap-2">
          <button
            class="btn btn-xs {currentTheme === 'auto'
              ? 'btn-primary'
              : 'btn-ghost'}"
            onclick={() => handleThemeChange("auto")}
          >
            Auto
          </button>
          {#each daisyThemes as theme}
            <button
              class="btn btn-xs {currentTheme === theme
                ? 'btn-primary'
                : 'btn-ghost'}"
              onclick={() => handleThemeChange(theme)}
            >
              {theme}
            </button>
          {/each}
        </div>
      </div>
    </div>
  </div>

  <!-- Account -->
  <div class="card bg-base-200">
    <div class="card-body p-5">
      <h2 class="card-title text-lg">Account</h2>
      <p class="text-sm text-base-content/60">
        Signed in as <span class="font-medium"
          >{$session?.user?.email || ""}</span
        >
      </p>

      {#if authError}
        <div class="alert alert-error text-sm mt-2">{authError}</div>
      {/if}

      <div class="mt-3">
        <button
          class="btn btn-error btn-sm"
          disabled={isBusy}
          onclick={handleLogout}
        >
          {isBusy ? "Signing out..." : "Sign out"}
        </button>
      </div>
    </div>
  </div>
</div>
