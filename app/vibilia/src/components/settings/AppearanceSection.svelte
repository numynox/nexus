<script lang="ts">
  interface Props {
    currentTheme: string;
    daisyThemes: string[];
    onThemeChange: (theme: string) => void;
  }

  let { currentTheme, daisyThemes, onThemeChange }: Props = $props();
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8">
    <h2 class="text-xl font-bold mb-6 flex items-center gap-2">
      <span>🎨</span> Appearance
    </h2>

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
              onThemeChange(currentTheme === "auto" ? "light" : "auto")}
          />
        </label>
      </div>

      <div class="space-y-2">
        <div class="text-sm font-medium text-base-content/70">Select Theme</div>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2">
          {#each daisyThemes as theme}
            <button
              onclick={() => onThemeChange(theme)}
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
