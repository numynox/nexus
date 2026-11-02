<script lang="ts">
  import { MapPin } from "lucide-svelte";
  import { describeError, searchStations } from "../../lib/data";
  import type { Station } from "../../lib/transit";

  interface Props {
    id: string;
    label: string;
    placeholder?: string;
    value: { id: string; name: string } | null;
    onSelect: (station: { id: string; name: string } | null) => void;
  }

  let { id, label, placeholder = "", value, onSelect }: Props = $props();

  let text = $state("");
  let results = $state<Station[]>([]);
  let open = $state(false);
  let loading = $state(false);
  let error = $state("");
  let highlighted = $state(-1);
  let timer: ReturnType<typeof setTimeout> | undefined;
  let generation = 0;

  // Follows the selected station (swap, restored search) unless the user is typing.
  let lastValueName: string | null = null;
  $effect(() => {
    const name = value?.name ?? null;
    if (name !== lastValueName) {
      lastValueName = name;
      text = name ?? "";
    }
  });

  function onInput(event: Event) {
    text = (event.target as HTMLInputElement).value;
    if (value && text !== value.name) onSelect(null);
    clearTimeout(timer);
    error = "";
    const query = text.trim();
    if (query.length < 2) {
      results = [];
      open = false;
      return;
    }
    timer = setTimeout(() => void lookup(query), 250);
  }

  async function lookup(query: string) {
    const mine = ++generation;
    loading = true;
    try {
      const stations = await searchStations(query);
      if (mine !== generation) return;
      results = stations.slice(0, 8);
      highlighted = results.length > 0 ? 0 : -1;
      open = true;
    } catch (e) {
      if (mine !== generation) return;
      error = describeError(e);
      results = [];
      open = true;
    } finally {
      if (mine === generation) loading = false;
    }
  }

  function choose(station: Station) {
    lastValueName = station.name;
    text = station.name;
    open = false;
    results = [];
    onSelect({ id: station.id, name: station.name });
  }

  function onKeydown(event: KeyboardEvent) {
    if (!open || results.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      highlighted = (highlighted + 1) % results.length;
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      highlighted = (highlighted - 1 + results.length) % results.length;
    } else if (event.key === "Enter" && highlighted >= 0) {
      event.preventDefault();
      choose(results[highlighted]);
    } else if (event.key === "Escape") {
      open = false;
    }
  }
</script>

<div class="relative">
  <label for={id} class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">{label}</label>
  <div class="relative">
    <MapPin class="pointer-events-none absolute top-1/2 left-4 h-4 w-4 -translate-y-1/2 text-primary" />
    <input
      {id}
      type="text"
      autocomplete="off"
      spellcheck="false"
      role="combobox"
      aria-expanded={open}
      aria-controls={`${id}-list`}
      aria-autocomplete="list"
      class="input h-13 w-full rounded-2xl border-base-300 bg-card pl-11 text-base font-medium"
      {placeholder}
      value={text}
      oninput={onInput}
      onkeydown={onKeydown}
      onfocus={() => results.length > 0 && (open = true)}
      onblur={() => setTimeout(() => (open = false), 150)}
    />
    {#if loading}
      <span class="loading loading-spinner loading-xs absolute top-1/2 right-4 -translate-y-1/2 text-primary"></span>
    {/if}
  </div>

  {#if open}
    <ul
      id={`${id}-list`}
      role="listbox"
      class="absolute z-30 mt-2 max-h-80 w-full overflow-auto rounded-2xl border border-base-200 bg-card p-1.5 shadow-xl"
    >
      {#if error}
        <li class="px-3 py-2.5 text-sm text-error">{error}</li>
      {:else if results.length === 0}
        <li class="px-3 py-2.5 text-sm text-base-content/60">No stations found</li>
      {:else}
        {#each results as station, i (station.id)}
          <li role="option" aria-selected={i === highlighted}>
            <button
              type="button"
              class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left {i === highlighted ? 'bg-mint' : 'hover:bg-base-200'}"
              onmousedown={(e) => e.preventDefault()}
              onclick={() => choose(station)}
            >
              <span class="min-w-0 flex-1 truncate font-medium">{station.name}</span>
              {#if station.country}
                <span class="text-xs font-semibold text-base-content/45">{station.country}</span>
              {/if}
            </button>
          </li>
        {/each}
      {/if}
    </ul>
  {/if}
</div>
