<script lang="ts">
  import { ChevronRight, Package, Plus, Search } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    getCategoryBorderColor,
    getCategoryIconComponent,
  } from "../../lib/categoryMeta";
  import {
    fetchCategories,
    fetchProducts,
    type Category,
    type Product,
  } from "../../lib/data";
  import { session } from "../../lib/stores";
  import ProductDetail from "./ProductDetail.svelte";
  import ProductForm from "./ProductForm.svelte";

  let products: Product[] = $state([]);
  let categories: Category[] = $state([]);
  let loading = $state(true);
  let error = $state("");
  let searchQuery = $state("");
  let filterCategory = $state<number | "" | null>("");
  let showForm = $state(false);
  let editingProduct: Product | null = $state(null);
  let selectedProduct: Product | null = $state(null);

  let filteredProducts = $derived.by(() => {
    let list = products;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          (p.brand && p.brand.toLowerCase().includes(q)) ||
          (p.ean && p.ean.includes(q)),
      );
    }
    if (filterCategory === null) {
      // "None" — uncategorised
      list = list.filter((p) => !p.category_id);
    } else if (filterCategory !== "") {
      list = list.filter((p) => p.category_id === filterCategory);
    }
    return list;
  });

  let categoryCounts = $derived.by(() => {
    const counts: Record<string, number> = {
      __all__: products.length,
      __none__: 0,
    };
    for (const p of products) {
      if (!p.category_id) counts["__none__"] = (counts["__none__"] ?? 0) + 1;
      else counts[p.category_id] = (counts[p.category_id] ?? 0) + 1;
    }
    return counts;
  });

  onMount(async () => {
    const s = $session;
    if (!s?.user) return;
    await loadData();
    // Deep-link: ?product=<id>
    const params = new URLSearchParams(window.location.search);
    const pid = params.get("product");
    if (pid) {
      const found = products.find((p) => p.id === Number(pid));
      if (found) selectedProduct = found;
    }
  });

  async function loadData() {
    loading = true;
    error = "";
    try {
      const [prods, cats] = await Promise.all([
        fetchProducts(),
        fetchCategories(),
      ]);
      products = prods;
      categories = cats;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  function handleNew() {
    editingProduct = null;
    showForm = true;
  }

  function handleEdit(product: Product) {
    editingProduct = product;
    showForm = true;
  }

  function handleFormClose() {
    showForm = false;
    editingProduct = null;
    loadData();
  }

  function handleSelect(product: Product) {
    selectedProduct = product;
    history.replaceState(null, "", `?product=${product.id}`);
  }

  function handleDetailClose() {
    selectedProduct = null;
    history.replaceState(null, "", window.location.pathname);
    loadData();
  }
</script>

{#if showForm}
  <ProductForm
    {categories}
    product={editingProduct}
    onClose={handleFormClose}
  />
{:else if selectedProduct}
  <ProductDetail
    product={selectedProduct}
    onBack={handleDetailClose}
    onEdit={() => handleEdit(selectedProduct!)}
  />
{:else}
  <div class="space-y-6 animate-in slide-in-from-bottom duration-500">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-black text-base-content">Products</h1>
        <p class="text-sm text-base-content/60">
          {products.length} product{products.length !== 1 ? "s" : ""}
        </p>
      </div>
      <button class="btn btn-primary btn-sm gap-2" onclick={handleNew}>
        <Plus class="w-4 h-4" />
        New
      </button>
    </div>

    {#if error}
      <div class="alert alert-error text-sm">{error}</div>
    {/if}

    <!-- Search & Filter -->
    <div class="flex flex-col gap-4">
      <label class="input input-bordered flex items-center gap-2 w-full">
        <Search class="w-4 h-10 opacity-50" />
        <input
          type="text"
          placeholder="Search products..."
          class="grow"
          bind:value={searchQuery}
        />
      </label>
      <div class="flex flex-wrap gap-1.5">
        <button
          class="btn btn-sm gap-1.5 {filterCategory === ''
            ? 'btn-accent'
            : 'btn-primary'}"
          onclick={() => (filterCategory = "")}
        >
          All
          <span class="badge badge-sm badge-neutral"
            >{categoryCounts["__all__"] ?? 0}</span
          >
        </button>
        {#if (categoryCounts["__none__"] ?? 0) > 0}
          <button
            class="btn btn-sm gap-1.5 {filterCategory === null
              ? 'btn-accent'
              : 'btn-primary'}"
            onclick={() => (filterCategory = null)}
          >
            None
            <span class="badge badge-sm badge-neutral"
              >{categoryCounts["__none__"] ?? 0}</span
            >
          </button>
        {/if}
        {#each categories as cat}
          {#if (categoryCounts[cat.id] ?? 0) > 0}
            {@const CatIcon = getCategoryIconComponent(cat.icon)}
            <button
              class="btn btn-sm gap-1.5 {filterCategory === cat.id
                ? 'btn-accent'
                : 'btn-secondary'}"
              onclick={() => (filterCategory = cat.id)}
            >
              <CatIcon class="w-3.5 h-3.5" />
              {cat.name}
              <span class="badge badge-sm badge-neutral"
                >{categoryCounts[cat.id] ?? 0}</span
              >
            </button>
          {/if}
        {/each}
      </div>
    </div>

    {#if loading}
      <div class="flex justify-center py-12">
        <span class="loading loading-infinity loading-lg text-primary"></span>
      </div>
    {:else if filteredProducts.length === 0}
      <div class="text-center py-12 text-base-content/50">
        <Package class="w-12 h-12 mx-auto mb-3 opacity-40" />
        <p class="text-lg font-medium">No products found</p>
        {#if searchQuery || filterCategory !== ""}
          <p class="text-sm mt-1">Try adjusting your filters</p>
        {:else}
          <p class="text-sm mt-1">Create your first product to get started</p>
        {/if}
      </div>
    {:else}
      <div class="space-y-2">
        {#each filteredProducts as product}
          {@const border = getCategoryBorderColor(product.category_color)}
          {@const IconComp = getCategoryIconComponent(product.category_icon)}
          {@const expiredCount = product.expired_item_count ?? 0}
          {@const expiringSoonCount = product.expiring_soon_count ?? 0}
          {@const badgeCount = expiredCount + expiringSoonCount}
          <div
            class="flex items-center gap-3 bg-base-200 rounded-xl px-4 py-3 w-full text-left hover:bg-base-300 transition-colors cursor-pointer"
            style={border
              ? `border-left: 2px solid ${border};`
              : "border-left: 2px solid transparent;"}
            onclick={() => handleSelect(product)}
            onkeydown={(e) => e.key === "Enter" && handleSelect(product)}
            role="button"
            tabindex="0"
          >
            <div class="relative shrink-0">
              {#if product.image_url}
                <img
                  src={product.image_url}
                  alt={product.name}
                  class="w-10 h-10 rounded-lg object-cover shadow-sm"
                />
              {:else}
                <div
                  class="w-10 h-10 rounded-lg bg-base-300 flex items-center justify-center"
                >
                  <svelte:component
                    this={IconComp}
                    class="w-5 h-5 opacity-40"
                  />
                </div>
              {/if}
              {#if badgeCount > 0}
                <span
                  class="absolute -top-1.5 -right-1.5 min-w-[1.1rem] h-[1.1rem] px-0.5 rounded-full text-[0.6rem] font-bold flex items-center justify-center leading-none {expiredCount >
                  0
                    ? 'bg-error text-error-content'
                    : 'bg-warning text-warning-content'}">{badgeCount}</span
                >
              {/if}
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-semibold truncate text-base-content">
                {#if product.brand}
                  <span class="font-normal opacity-70">{product.brand} </span>
                {/if}
                {product.name}
              </div>
              <div
                class="flex items-center gap-2 mt-0.5 text-xs text-base-content/50"
              >
                {#if product.quantity}<span>{product.quantity}</span>{/if}
                {#if product.quantity && (product.category_name || (product.active_item_count ?? 0) > 0)}
                  <span>·</span>
                {/if}
                {#if product.category_name}
                  {@const CatIcon = getCategoryIconComponent(
                    product.category_icon,
                  )}
                  <span class="flex items-center gap-1">
                    <svelte:component this={CatIcon} class="w-3 h-3" />
                    {product.category_name}
                  </span>
                {/if}
                {#if product.category_name && (product.active_item_count ?? 0) > 0}
                  <span>·</span>
                {/if}
                {#if (product.active_item_count ?? 0) > 0}
                  <span>{product.active_item_count} in stock</span>
                {/if}
              </div>
            </div>
            <ChevronRight class="w-4 h-4 opacity-30 shrink-0" />
          </div>
        {/each}
      </div>
    {/if}
  </div>
{/if}
