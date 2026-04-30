<script lang="ts">
  import { ArrowLeft } from "lucide-svelte";
  import { createItem, type StorageLocation } from "../../lib/data";
  import { session } from "../../lib/stores";

  interface Props {
    productId: number;
    productName: string;
    locations: StorageLocation[];
    onClose: () => void;
  }

  let { productId, productName, locations, onClose }: Props = $props();

  let expirationDate = $state("");
  let storageLocationId = $state<number | "">("");
  let itemCount = $state(1);
  let saving = $state(false);
  let error = $state("");

  async function handleSubmit(e: SubmitEvent) {
    e.preventDefault();
    const s = $session;
    if (!s?.user) return;

    saving = true;
    error = "";
    try {
      const count = Math.max(1, Math.floor(itemCount));
      for (let i = 0; i < count; i++) {
        await createItem(s.user.id, {
          product_id: productId,
          expiration_date: expirationDate || null,
          storage_location_id:
            storageLocationId === "" ? null : storageLocationId,
        });
      }
      onClose();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
      saving = false;
    }
  }
</script>

<div class="space-y-6 animate-in slide-in-from-bottom duration-500">
  <!-- Mobile: back button at top -->
  <div class="flex lg:hidden mb-1">
    <button class="btn btn-ghost btn-sm gap-1" onclick={onClose}>
      <ArrowLeft class="w-4 h-4" />
      Back
    </button>
  </div>

  <div class="flex items-center gap-3">
    <button
      class="hidden lg:flex btn btn-ghost btn-sm btn-square shrink-0 -ml-11"
      onclick={onClose}
    >
      <ArrowLeft class="w-5 h-5" />
    </button>
    <div>
      <h1 class="text-2xl font-black text-base-content">Add Item</h1>
      <p class="text-sm text-base-content/60">{productName}</p>
    </div>
  </div>

  {#if error}
    <div class="alert alert-error text-sm">{error}</div>
  {/if}

  <form class="space-y-3 max-w-lg" onsubmit={handleSubmit}>
    <!-- Expiration date + quantity side by side -->
    <div class="flex gap-3">
      <div class="flex-1">
        <label class="text-xs font-medium text-base-content/60 mb-1 block"
          >Expiration Date *</label
        >
        <input
          type="date"
          class="input input-bordered w-full"
          bind:value={expirationDate}
          required
        />
      </div>
      <div class="w-28">
        <label class="text-xs font-medium text-base-content/60 mb-1 block"
          >Quantity</label
        >
        <input
          type="number"
          class="input input-bordered w-full"
          min="1"
          max="99"
          bind:value={itemCount}
        />
      </div>
    </div>

    <div>
      <label class="text-xs font-medium text-base-content/60 mb-1 block"
        >Storage Location</label
      >
      <select
        class="select select-bordered w-full"
        bind:value={storageLocationId}
      >
        <option value="">— None —</option>
        {#each locations as loc}
          <option value={loc.id}>{loc.name}</option>
        {/each}
      </select>
    </div>

    <div class="flex gap-2 pt-2">
      <button class="btn btn-primary flex-1" type="submit" disabled={saving}>
        {saving
          ? "Saving…"
          : itemCount > 1
            ? `Add ${itemCount} Items`
            : "Add Item"}
      </button>
      <button class="btn btn-ghost" type="button" onclick={onClose}>
        Cancel
      </button>
    </div>
  </form>
</div>
