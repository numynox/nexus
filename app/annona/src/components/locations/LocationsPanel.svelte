<script lang="ts">
  import { Check, Pencil, Plus, Trash2, X } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    getCategoryBorderColor,
    getCategoryIconComponent,
  } from "../../lib/categoryMeta";
  import { getBaseUrl } from "../../lib/config";
  import {
    createStorageLocation,
    deleteStorageLocation,
    fetchItemsGroupedByLocation,
    fetchStorageLocations,
    updateStorageLocation,
    type LocationGroup,
    type StorageLocation,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  const baseUrl = getBaseUrl();

  function productsHref(productId?: number) {
    const base = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${base}/products${productId != null ? `?product=${productId}` : ""}`;
  }

  let locations: StorageLocation[] = $state([]);
  let groups: LocationGroup[] = $state([]);
  let loading = $state(true);
  let error = $state("");
  let newName = $state("");
  let adding = $state(false);
  let editingId: number | null = $state(null);
  let editName = $state("");
  let confirmDeleteId: number | null = $state(null);
  let confirmDeleteName = $state("");

  onMount(async () => {
    const s = $session;
    if (!s?.user) return;
    await loadData();
  });

  async function loadData() {
    loading = true;
    error = "";
    try {
      const [locs, grps] = await Promise.all([
        fetchStorageLocations(),
        fetchItemsGroupedByLocation(),
      ]);
      locations = locs;
      groups = grps;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  async function handleAdd() {
    if (!newName.trim()) return;
    error = "";
    try {
      const loc = await createStorageLocation(newName);
      locations = [...locations, loc];
      newName = "";
      adding = false;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  function startEdit(loc: StorageLocation) {
    editingId = loc.id;
    editName = loc.name;
  }

  function cancelEdit() {
    editingId = null;
    editName = "";
  }

  async function saveEdit() {
    if (editingId === null || !editName.trim()) return;
    error = "";
    try {
      await updateStorageLocation(editingId, editName);
      locations = locations.map((l) =>
        l.id === editingId ? { ...l, name: editName.trim() } : l,
      );
      groups = groups.map((g) =>
        g.location_id === editingId
          ? { ...g, location_name: editName.trim() }
          : g,
      );
      cancelEdit();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleDelete(id: number) {
    error = "";
    try {
      await deleteStorageLocation(id);
      locations = locations.filter((l) => l.id !== id);
      groups = groups.filter((g) => g.location_id !== id);
      confirmDeleteId = null;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
      confirmDeleteId = null;
    }
  }
</script>

<div class="space-y-8 animate-in slide-in-from-bottom duration-500">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-3xl font-black text-base-content">Storage Locations</h1>
      <p class="text-sm text-base-content/60">Where you keep your groceries</p>
    </div>
    <button
      class="btn btn-primary btn-sm gap-2"
      onclick={() => (adding = true)}
    >
      <Plus class="w-4 h-4" />
      Add
    </button>
  </div>

  {#if error}
    <div class="alert alert-error text-sm">{error}</div>
  {/if}

  {#if adding}
    <div class="flex gap-2 items-center bg-base-200 rounded-xl px-4 py-3">
      <input
        type="text"
        class="input input-bordered input-sm flex-1"
        placeholder="Location name (e.g. Fridge, Pantry, Freezer)"
        bind:value={newName}
        onkeydown={(e) => e.key === "Enter" && handleAdd()}
      />
      <button class="btn btn-primary btn-sm btn-square" onclick={handleAdd}>
        <Check class="w-4 h-4" />
      </button>
      <button
        class="btn btn-ghost btn-sm btn-square"
        onclick={() => {
          adding = false;
          newName = "";
        }}
      >
        <X class="w-4 h-4" />
      </button>
    </div>
  {/if}

  {#if loading}
    <div class="flex justify-center py-12">
      <span class="loading loading-infinity loading-lg text-primary"></span>
    </div>
  {:else if groups.length === 0 && locations.length === 0 && !adding}
    <p class="text-center text-base-content/50 py-8">
      No locations yet. Add one like "Fridge", "Pantry", or "Freezer".
    </p>
  {:else}
    <!-- Render each location group -->
    {#each groups as group}
      {@const loc = locations.find((l) => l.id === group.location_id)}
      <div class="space-y-3">
        <!-- Location headline -->
        <div class="flex items-center gap-2">
          {#if group.location_id !== null && editingId === group.location_id}
            <input
              type="text"
              class="input input-bordered input-sm flex-1 max-w-xs"
              bind:value={editName}
              onkeydown={(e) => e.key === "Enter" && saveEdit()}
            />
            <button
              class="btn btn-primary btn-sm btn-square"
              onclick={saveEdit}
            >
              <Check class="w-4 h-4" />
            </button>
            <button
              class="btn btn-ghost btn-sm btn-square"
              onclick={cancelEdit}
            >
              <X class="w-4 h-4" />
            </button>
          {:else}
            <h2 class="text-lg font-bold text-base-content">
              {group.location_name}
            </h2>
            {#if group.location_id !== null && loc}
              <button
                class="btn btn-ghost btn-xs btn-square opacity-50 hover:opacity-100"
                title="Edit"
                onclick={() => startEdit(loc)}
              >
                <Pencil class="w-3.5 h-3.5" />
              </button>
              <button
                class="btn btn-ghost btn-xs btn-square text-error opacity-50 hover:opacity-100"
                title="Delete"
                onclick={() => {
                  confirmDeleteId = loc.id;
                  confirmDeleteName = loc.name;
                }}
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            {/if}
          {/if}
        </div>

        <!-- Product chips -->
        <div class="flex flex-wrap gap-4">
          {#each group.products as prod}
            {@const border = getCategoryBorderColor(prod.category_color)}
            {@const IconComp = getCategoryIconComponent(prod.category_icon)}
            {@const hasBadge =
              prod.expired_count > 0 || prod.expiring_soon_count > 0}
            {@const badgeCount = prod.expired_count + prod.expiring_soon_count}
            <a
              href={productsHref(prod.product_id)}
              class="relative flex items-start gap-2 bg-base-200 hover:bg-base-300 transition-colors rounded-xl px-3 py-2.5 text-sm no-underline min-w-[8rem]"
              style={border
                ? `border-left: 2px solid ${border};`
                : "border-left: 2px solid transparent;"}
              title="Go to {prod.product_brand
                ? prod.product_brand + ' '
                : ''}{prod.product_name}"
            >
              {#if hasBadge}
                <span
                  class="absolute -top-1.5 -right-1.5 min-w-[1.1rem] h-[1.1rem] px-0.5 rounded-full text-[0.6rem] font-bold flex items-center justify-center leading-none z-10 {prod.expired_count >
                  0
                    ? 'bg-error text-error-content'
                    : 'bg-warning text-warning-content'}">{badgeCount}</span
                >
              {/if}
              <svelte:component
                this={IconComp}
                class="w-4 h-4 opacity-50 shrink-0 mt-0.5"
              />
              <span class="leading-tight flex-1">
                <div class="flex items-center justify-between gap-3">
                  {#if prod.product_brand}<span class="opacity-60 text-xs"
                      >{prod.product_brand}</span
                    >{:else}<span></span>{/if}
                  <span
                    class="font-bold tabular-nums text-primary text-xs shrink-0"
                    >{prod.item_count}</span
                  >
                </div>
                <div>{prod.product_name}</div>
              </span>
            </a>
          {/each}
        </div>
      </div>
    {/each}

    <!-- Locations with no items (not in groups) -->
    {#each locations.filter((l) => !groups.some((g) => g.location_id === l.id)) as loc}
      <div class="space-y-2">
        <div class="flex items-center gap-2">
          {#if editingId === loc.id}
            <input
              type="text"
              class="input input-bordered input-sm flex-1 max-w-xs"
              bind:value={editName}
              onkeydown={(e) => e.key === "Enter" && saveEdit()}
            />
            <button
              class="btn btn-primary btn-sm btn-square"
              onclick={saveEdit}
            >
              <Check class="w-4 h-4" />
            </button>
            <button
              class="btn btn-ghost btn-sm btn-square"
              onclick={cancelEdit}
            >
              <X class="w-4 h-4" />
            </button>
          {:else}
            <h2 class="text-lg font-bold text-base-content/40">{loc.name}</h2>
            <button
              class="btn btn-ghost btn-xs btn-square opacity-50 hover:opacity-100"
              title="Edit"
              onclick={() => startEdit(loc)}
            >
              <Pencil class="w-3.5 h-3.5" />
            </button>
            <button
              class="btn btn-ghost btn-xs btn-square text-error opacity-50 hover:opacity-100"
              title="Delete"
              onclick={() => {
                confirmDeleteId = loc.id;
                confirmDeleteName = loc.name;
              }}
            >
              <Trash2 class="w-3.5 h-3.5" />
            </button>
          {/if}
        </div>
        <p class="text-xs text-base-content/40">No items</p>
      </div>
    {/each}
  {/if}

  {#if confirmDeleteId !== null}
    <dialog class="modal modal-open">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Delete location?</h3>
        <p class="py-2 text-sm text-base-content/70">
          Delete <strong>{confirmDeleteName}</strong>? Items stored here will
          not be deleted — their location will be set to none.
        </p>
        <div class="modal-action">
          <button
            class="btn btn-error"
            onclick={() => handleDelete(confirmDeleteId!)}>Delete</button
          >
          <button class="btn btn-ghost" onclick={() => (confirmDeleteId = null)}
            >Cancel</button
          >
        </div>
      </div>
      <div
        class="modal-backdrop"
        onclick={() => (confirmDeleteId = null)}
      ></div>
    </dialog>
  {/if}
</div>
