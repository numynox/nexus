<script lang="ts">
  import { ThemePicker } from "@nexus/ui";
  interface Props {
    currentTheme: string;
    previousDays: number;
    onThemeChange: (theme: string) => void;
    onPreviousDaysChange: (days: number) => void;
  }

  let {
    currentTheme,
    previousDays,
    onThemeChange,
    onPreviousDaysChange,
  }: Props = $props();

  function handlePreviousDaysChange(event: Event) {
    const select = event.currentTarget as HTMLSelectElement;
    onPreviousDaysChange(Number(select.value));
  }
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8">
    <h2 class="text-xl font-bold mb-6 flex items-center gap-2">
      <span>🎨</span> Appearance
    </h2>

    <div class="space-y-4">
      <div class="font-semibold text-sm">Color Theme</div>

      <ThemePicker current={currentTheme} onSelect={onThemeChange} />

      <div class="space-y-2">
        <div class="font-semibold text-sm">Fuel Price</div>
        <label class="flex items-center justify-between gap-4">
          <div class="text-sm text-base-content/70">
            Previous days in price plot
          </div>
          <select
            class="select select-bordered select-sm w-28"
            value={previousDays}
            onchange={handlePreviousDaysChange}
          >
            <option value={1}>1</option>
            <option value={2}>2</option>
            <option value={3}>3</option>
            <option value={4}>4</option>
            <option value={5}>5</option>
            <option value={6}>6</option>
            <option value={7}>7</option>
          </select>
        </label>
      </div>
    </div>
  </div>
</section>
