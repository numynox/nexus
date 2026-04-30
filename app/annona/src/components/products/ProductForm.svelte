<script lang="ts">
  import { ArrowLeft } from "lucide-svelte";
  import {
    createProduct,
    updateProduct,
    type Category,
    type Product,
    type ProductInsert,
  } from "../../lib/data";

  interface Props {
    categories: Category[];
    product?: Product | null;
    onClose: () => void;
    /** If provided, pre-fills the EAN field */
    initialEan?: string;
  }

  let {
    categories,
    product = null,
    onClose,
    initialEan = "",
  }: Props = $props();

  let name = $state(product?.name ?? "");
  let brand = $state(product?.brand ?? "");
  let ean = $state(product?.ean ?? initialEan ?? "");
  let quantity = $state(product?.quantity ?? "");
  let categoryId = $state<number | "">(product?.category_id ?? "");
  let imageUrl = $state(product?.image_url ?? "");
  let saving = $state(false);
  let error = $state("");

  const isEditing = !!product;

  async function handleSubmit(e: SubmitEvent) {
    e.preventDefault();
    if (!name.trim()) return;

    saving = true;
    error = "";

    const payload: ProductInsert = {
      name: name.trim(),
      brand: brand.trim() || null,
      ean: ean.trim() || null,
      quantity: quantity.trim() || null,
      category_id: categoryId === "" ? null : categoryId,
      image_url: imageUrl.trim() || null,
    };

    try {
      if (isEditing) {
        await updateProduct(product!.id, payload);
      } else {
        await createProduct(payload);
      }
      onClose();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
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
    <h1 class="text-3xl font-black text-base-content">
      {isEditing ? "Edit Product" : "New Product"}
    </h1>
  </div>

  {#if error}
    <div class="alert alert-error text-sm">{error}</div>
  {/if}

  <form class="space-y-3 max-w-lg" onsubmit={handleSubmit}>
    <!-- Brand + Name side by side -->
    <div class="flex gap-3">
      <div class="flex-1">
        <label class="text-xs font-medium text-base-content/60 mb-1 block"
          >Brand</label
        >
        <input
          type="text"
          class="input input-bordered w-full"
          bind:value={brand}
          placeholder="e.g. Barilla"
        />
      </div>
      <div class="flex-[2]">
        <label class="text-xs font-medium text-base-content/60 mb-1 block"
          >Product Name *</label
        >
        <input
          type="text"
          class="input input-bordered w-full"
          bind:value={name}
          placeholder="e.g. Farfalle"
          required
        />
      </div>
    </div>

    <!-- Quantity + Category side by side -->
    <div class="flex gap-3">
      <div class="flex-1">
        <label class="text-xs font-medium text-base-content/60 mb-1 block"
          >Quantity</label
        >
        <input
          type="text"
          class="input input-bordered w-full"
          bind:value={quantity}
          placeholder="e.g. 500g, 1L"
        />
      </div>
      <div class="flex-1">
        <label class="text-xs font-medium text-base-content/60 mb-1 block"
          >Category</label
        >
        <select class="select select-bordered w-full" bind:value={categoryId}>
          <option value="">— None —</option>
          {#each categories as cat}
            <option value={cat.id}>{cat.name}</option>
          {/each}
        </select>
      </div>
    </div>

    <!-- EAN -->
    <div>
      <label class="text-xs font-medium text-base-content/60 mb-1 block"
        >EAN Barcode</label
      >
      <input
        type="text"
        class="input input-bordered w-full"
        bind:value={ean}
        placeholder="e.g. 8001120007520"
        inputmode="numeric"
      />
    </div>

    <!-- Image URL -->
    <div>
      <label class="text-xs font-medium text-base-content/60 mb-1 block"
        >Image URL</label
      >
      <input
        type="url"
        class="input input-bordered w-full"
        bind:value={imageUrl}
        placeholder="https://..."
      />
    </div>

    <div class="flex gap-2 pt-2">
      <button class="btn btn-primary flex-1" type="submit" disabled={saving}>
        {saving ? "Saving…" : isEditing ? "Save Changes" : "Create Product"}
      </button>
      <button class="btn btn-ghost" type="button" onclick={onClose}>
        Cancel
      </button>
    </div>
  </form>
</div>
