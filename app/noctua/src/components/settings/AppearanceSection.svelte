<script lang="ts">
  import { Palette } from "lucide-svelte";
  import { ThemePicker } from "@nexus/ui";
  interface Props {
    currentTheme: string;
    hideSeenArticles: boolean;
    autoMarkAsSeen: boolean;
    onThemeChange: (theme: string) => void;
    onPreferencesChange: () => void;
  }

  let {
    currentTheme,
    hideSeenArticles = $bindable(false),
    autoMarkAsSeen = $bindable(false),
    onThemeChange,
    onPreferencesChange,
  }: Props = $props();
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8">
    <h2 class="text-xl font-bold mb-6 flex items-center gap-2">
      <Palette class="h-5 w-5" /> Appearance
    </h2>

    <div class="space-y-4">
      <label class="flex items-center justify-between gap-4 cursor-pointer">
        <div class="flex-1">
          <span class="font-semibold block">Hide Seen Articles</span>
          <span class="text-sm text-base-content/60"
            >Read or seen articles will be hidden on next page load</span
          >
        </div>
        <input
          type="checkbox"
          class="toggle toggle-primary"
          bind:checked={hideSeenArticles}
          onchange={onPreferencesChange}
        />
      </label>

      <label class="flex items-center justify-between gap-4 cursor-pointer">
        <div class="flex-1">
          <span class="font-semibold block">Auto-Mark as Seen</span>
          <span class="text-sm text-base-content/60"
            >Automatically mark articles as seen when scrolled past</span
          >
        </div>
        <input
          type="checkbox"
          class="toggle toggle-primary"
          bind:checked={autoMarkAsSeen}
          onchange={onPreferencesChange}
        />
      </label>
    </div>

    <div class="divider"></div>

    <div class="space-y-3">
      <div class="font-semibold text-sm">Color Theme</div>
      <ThemePicker current={currentTheme} onSelect={onThemeChange} />
    </div>
  </div>
</section>
