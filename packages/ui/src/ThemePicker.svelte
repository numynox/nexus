<script>
  import { AUTO, THEMES } from "./theme.js";

  /**
   * @typedef {Object} Props
   * @property {string} current - the stored preference ("auto" or a theme name)
   * @property {(theme: string) => void} onSelect
   */

  /** @type {Props} */
  let { current, onSelect } = $props();

  const options = [{ name: AUTO, label: "Auto", dark: true }, ...THEMES];
</script>

<div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
  {#each options as option (option.name)}
    <button
      type="button"
      onclick={() => onSelect(option.name)}
      aria-pressed={current === option.name}
      aria-label={option.label}
      class="group overflow-hidden rounded-box border-2 text-left transition
        {current === option.name
        ? 'border-primary shadow-md'
        : 'border-base-300 hover:border-base-content/30'}"
    >
      {#if option.name === AUTO}
        <!-- Half light, half dark: what "follow the system" actually means -->
        <div class="flex h-14 w-full">
          <div
            data-theme="lofi"
            class="flex flex-1 items-center justify-center bg-base-100"
          >
            <span class="h-5 w-5 rounded-full bg-primary"></span>
          </div>
          <div
            data-theme="dark"
            class="flex flex-1 items-center justify-center bg-base-100"
          >
            <span class="h-5 w-5 rounded-full bg-primary"></span>
          </div>
        </div>
      {:else}
        <div
          data-theme={option.name}
          class="flex h-14 w-full items-center gap-1.5 bg-base-100 px-3"
        >
          <span class="h-6 w-6 rounded-full bg-primary"></span>
          <span class="h-6 w-6 rounded-full bg-secondary"></span>
          <span class="h-6 w-6 rounded-full bg-accent"></span>
          <span class="h-6 w-2 rounded-full bg-base-content/40"></span>
        </div>
      {/if}

      <div
        class="flex items-center justify-between gap-2 bg-base-200 px-3 py-2"
      >
        <span class="text-sm font-medium">{option.label}</span>
        {#if current === option.name}
          <span class="badge badge-primary badge-xs"></span>
        {/if}
      </div>
    </button>
  {/each}
</div>
