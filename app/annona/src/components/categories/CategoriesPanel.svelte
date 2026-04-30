<script lang="ts">
  import { Check, Pencil, Plus, Trash2, X } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    CATEGORY_COLORS,
    CATEGORY_ICONS,
    getCategoryBorderColor,
    getCategoryIconComponent,
  } from "../../lib/categoryMeta";
  import {
    createCategory,
    deleteCategory,
    fetchCategories,
    updateCategory,
    type Category,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  let categories: Category[] = $state([]);
  let loading = $state(true);
  let error = $state("");

  // Add form
  let adding = $state(false);
  let newName = $state("");
  let newColor = $state("");
  let newIcon = $state("");

  // Edit form
  let editingId: number | null = $state(null);
  let editName = $state("");
  let editColor = $state("");
  let editIcon = $state("");

  // Delete confirmation
  let confirmDeleteId: number | null = $state(null);
  let confirmDeleteName = $state("");

  function sortAlpha(cats: Category[]): Category[] {
    return [...cats].sort((a, b) => a.name.localeCompare(b.name));
  }

  onMount(async () => {
    const s = $session;
    if (!s?.user) return;
    await loadCategories();
  });

  async function loadCategories() {
    loading = true;
    error = "";
    try {
      categories = sortAlpha(await fetchCategories());
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
      const cat = await createCategory(
        newName,
        newColor || null,
        newIcon || null,
      );
      categories = sortAlpha([...categories, cat]);
      newName = "";
      newColor = "";
      newIcon = "";
      adding = false;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  function startEdit(cat: Category) {
    editingId = cat.id;
    editName = cat.name;
    editColor = cat.color ?? "";
    editIcon = cat.icon ?? "";
  }

  function cancelEdit() {
    editingId = null;
    editName = "";
    editColor = "";
    editIcon = "";
  }

  async function saveEdit() {
    if (editingId === null || !editName.trim()) return;
    error = "";
    try {
      await updateCategory(
        editingId,
        editName,
        editColor || null,
        editIcon || null,
      );
      categories = sortAlpha(
        categories.map((c) =>
          c.id === editingId
            ? {
                ...c,
                name: editName.trim(),
                color: editColor || null,
                icon: editIcon || null,
              }
            : c,
        ),
      );
      cancelEdit();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleDelete(id: number) {
    error = "";
    try {
      await deleteCategory(id);
      categories = categories.filter((c) => c.id !== id);
      confirmDeleteId = null;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
      confirmDeleteId = null;
    }
  }
</script>

{#snippet colorPicker(selected: string, onSelect: (key: string) => void)}
  <div class="flex flex-wrap gap-1.5">
    <button
      type="button"
      class="w-6 h-6 rounded-full border-2 bg-base-300 flex items-center justify-center {selected ===
      ''
        ? 'border-base-content/60'
        : 'border-transparent opacity-50'}"
      title="None"
      onclick={() => onSelect("")}
    >
      <X class="w-3 h-3 opacity-60" />
    </button>
    {#each CATEGORY_COLORS as col}
      <button
        type="button"
        class="w-6 h-6 rounded-full border-2 transition-transform hover:scale-110 {selected ===
        col.key
          ? 'border-base-content/60 scale-110'
          : 'border-transparent'}"
        style="background-color: {col.value};"
        title={col.label}
        onclick={() => onSelect(col.key)}
      ></button>
    {/each}
  </div>
{/snippet}

{#snippet iconPicker(selected: string, onSelect: (key: string) => void)}
  <div class="flex flex-wrap gap-1">
    {#each CATEGORY_ICONS as ic}
      {@const Comp = ic.component}
      <button
        type="button"
        class="w-8 h-8 rounded-lg flex items-center justify-center transition-colors {selected ===
        ic.key
          ? 'bg-primary text-primary-content'
          : 'bg-base-300 hover:bg-base-content/20'}"
        title={ic.label}
        onclick={() => onSelect(ic.key)}
      >
        <svelte:component this={Comp} class="w-4 h-4" />
      </button>
    {/each}
  </div>
{/snippet}

<div class="space-y-6 animate-in slide-in-from-bottom duration-500">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-3xl font-black text-base-content">Categories</h1>
      <p class="text-sm text-base-content/60">Organize your products</p>
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

  {#if loading}
    <div class="flex justify-center py-12">
      <span class="loading loading-infinity loading-lg text-primary"></span>
    </div>
  {:else}
    {#if adding}
      <div class="bg-base-200 rounded-xl px-4 py-3 space-y-3">
        <div class="flex gap-2 items-center">
          <input
            type="text"
            class="input input-bordered input-sm flex-1"
            placeholder="Category name"
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
              newColor = "";
              newIcon = "";
            }}
          >
            <X class="w-4 h-4" />
          </button>
        </div>
        <div class="space-y-2">
          <p class="text-xs text-base-content/50 font-medium">Color</p>
          {@render colorPicker(newColor, (k) => (newColor = k))}
          <p class="text-xs text-base-content/50 font-medium mt-2">Icon</p>
          {@render iconPicker(newIcon, (k) => (newIcon = k))}
        </div>
      </div>
    {/if}

    <div class="space-y-2">
      {#each categories as cat}
        {@const border = getCategoryBorderColor(cat.color)}
        {@const IconComp = getCategoryIconComponent(cat.icon)}
        <div
          class="bg-base-200 rounded-xl px-4 py-3"
          style={border
            ? `border-left: 2px solid ${border};`
            : "border-left: 2px solid transparent;"}
        >
          {#if editingId === cat.id}
            <div class="space-y-3">
              <div class="flex gap-2 items-center">
                <input
                  type="text"
                  class="input input-bordered input-sm flex-1"
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
              </div>
              <div class="space-y-2">
                <p class="text-xs text-base-content/50 font-medium">Color</p>
                {@render colorPicker(editColor, (k) => (editColor = k))}
                <p class="text-xs text-base-content/50 font-medium mt-2">
                  Icon
                </p>
                {@render iconPicker(editIcon, (k) => (editIcon = k))}
              </div>
            </div>
          {:else}
            <div class="flex items-center gap-3">
              <svelte:component
                this={IconComp}
                class="w-4 h-4 shrink-0 opacity-60"
              />
              <span class="flex-1 font-medium">{cat.name}</span>
              <button
                class="btn btn-ghost btn-xs btn-square"
                title="Edit"
                onclick={() => startEdit(cat)}
              >
                <Pencil class="w-3.5 h-3.5" />
              </button>
              <button
                class="btn btn-ghost btn-xs btn-square text-error"
                title="Delete"
                onclick={() => {
                  confirmDeleteId = cat.id;
                  confirmDeleteName = cat.name;
                }}
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          {/if}
        </div>
      {/each}
    </div>

    {#if categories.length === 0 && !adding}
      <p class="text-center text-base-content/50 py-8">
        No categories yet. Add one to get started.
      </p>
    {/if}
  {/if}

  {#if confirmDeleteId !== null}
    <dialog class="modal modal-open">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Delete category?</h3>
        <p class="py-2 text-sm text-base-content/70">
          Delete <strong>{confirmDeleteName}</strong>? Products in this category
          will not be deleted — their category will be set to none.
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
