<script lang="ts">
  import { Fuel } from "lucide-svelte";

  interface Props {
    selectedType: string;
    saving: boolean;
    onSelect: (type: string) => void;
  }

  let { selectedType, saving, onSelect }: Props = $props();
</script>

<div class="card bg-base-200 shadow-xl border border-primary/5">
  <div class="card-body">
    <h2 class="card-title flex items-center gap-2">
      <Fuel class="w-5 h-5 text-primary" />
      Fuel Preferences
    </h2>
    <p class="text-sm text-base-content/70">
      Select the fuel type to show in the dashboard and statistics.
    </p>

    <div class="flex flex-wrap gap-2 mt-4">
      {#each ["E5", "E10", "Diesel"] as type}
        <button
          class="btn flex-1 {selectedType === type
            ? 'btn-primary'
            : 'btn-outline'}"
          disabled={saving}
          onclick={() => onSelect(type)}
        >
          {type}
        </button>
      {/each}
    </div>
  </div>
</div>
