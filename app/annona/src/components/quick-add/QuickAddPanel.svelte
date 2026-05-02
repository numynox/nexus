<script lang="ts">
  import {
    Camera,
    CameraOff,
    Check,
    Package,
    Plus,
    Search,
  } from "lucide-svelte";
  import { onDestroy, onMount } from "svelte";
  import { getCategoryIconComponent } from "../../lib/categoryMeta";
  import {
    createItem,
    createProduct,
    fetchCategories,
    fetchProductByEan,
    fetchStorageLocations,
    searchProducts,
    type Category,
    type Product,
    type StorageLocation,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  let categories: Category[] = $state([]);
  let locations: StorageLocation[] = $state([]);
  let loading = $state(true);
  let error = $state("");

  // Scanner state
  let scanning = $state(false);
  let scannerContainer: HTMLDivElement | undefined = $state();
  let html5QrCode: any = null;

  // Product form state
  let scannedEan = $state("");
  let foundProduct: Product | null = $state(null);
  let showProductFields = $state(false);

  // Manual search state
  let manualQuery = $state("");
  let searchResults: Product[] = $state([]);
  let searchOpen = $state(false);
  let searchTimeout: ReturnType<typeof setTimeout> | null = null;
  let selectedIndex = $state(-1);

  // Product fields
  let productName = $state("");
  let productBrand = $state("");
  let productQuantity = $state("");
  let productCategoryId = $state<number | "">("");
  let productImageUrl = $state("");

  // Item fields
  let expirationDate = $state("");
  let storageLocationId = $state<number | "">("");
  let itemCount = $state(1);

  let saving = $state(false);

  // Workflow: ready → scan/type EAN → found product or create new → add item → repeat
  type Step = "input" | "details" | "done";
  let step: Step = $state("input");

  onMount(async () => {
    const s = $session;
    if (!s?.user) return;
    try {
      const [cats, locs] = await Promise.all([
        fetchCategories(),
        fetchStorageLocations(),
      ]);
      categories = cats;
      locations = locs;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  });

  onDestroy(() => {
    stopScanner();
    if (searchTimeout) clearTimeout(searchTimeout);
  });

  async function startScanner() {
    if (scanning || !scannerContainer) return;
    error = "";

    // Show the container before the library renders into it
    scanning = true;

    try {
      const { Html5Qrcode } = await import("html5-qrcode");
      html5QrCode = new Html5Qrcode(scannerContainer.id);

      await html5QrCode.start(
        { facingMode: "environment" },
        {
          fps: 10,
          qrbox: { width: 280, height: 120 },
          aspectRatio: 2.0,
          formatsToSupport: [
            0, // QR_CODE
            1, // AZTEC
            2, // CODABAR
            3, // CODE_39
            4, // CODE_93
            5, // CODE_128
            6, // DATA_MATRIX
            7, // MAXICODE
            8, // ITF
            9, // EAN_13
            10, // EAN_8
            11, // PDF_417
            12, // RSS_14
            13, // RSS_EXPANDED
            14, // UPC_A
            15, // UPC_E
            16, // UPC_EAN_EXTENSION
          ],
        },
        (decodedText: string) => {
          handleBarcodeScan(decodedText);
        },
        () => {
          // Ignore scan failures (continuous scanning)
        },
      );
    } catch (e) {
      scanning = false;
      error =
        "Camera access denied or not available. You can type the EAN manually.";
    }
  }

  async function stopScanner() {
    if (html5QrCode) {
      try {
        await html5QrCode.stop();
      } catch {
        // ignore
      }
      html5QrCode = null;
    }
    scanning = false;
  }

  async function handleBarcodeScan(code: string) {
    await stopScanner();
    scannedEan = code.trim();
    await lookupEan();
  }

  async function handleManualSearch() {
    const q = manualQuery.trim();
    if (!q) return;

    // If it looks like a pure EAN, do an exact lookup first
    if (/^\d{8,14}$/.test(q)) {
      scannedEan = q;
      await lookupEan();
      return;
    }

    // No match selected — create new product, prefill brand
    selectNewProduct(q);
  }

  function handleSearchInput() {
    const q = manualQuery.trim();
    selectedIndex = -1;

    if (searchTimeout) clearTimeout(searchTimeout);
    if (q.length < 2) {
      searchResults = [];
      searchOpen = false;
      return;
    }

    searchTimeout = setTimeout(async () => {
      try {
        searchResults = await searchProducts(q);
        searchOpen = searchResults.length > 0;
      } catch {
        searchResults = [];
        searchOpen = false;
      }
    }, 200);
  }

  function handleSearchKeydown(e: KeyboardEvent) {
    if (!searchOpen || searchResults.length === 0) {
      if (e.key === "Enter") {
        e.preventDefault();
        handleManualSearch();
      }
      return;
    }

    if (e.key === "ArrowDown") {
      e.preventDefault();
      selectedIndex = Math.min(selectedIndex + 1, searchResults.length - 1);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      selectedIndex = Math.max(selectedIndex - 1, 0);
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (selectedIndex >= 0 && selectedIndex < searchResults.length) {
        selectProduct(searchResults[selectedIndex]);
      } else {
        handleManualSearch();
      }
    } else if (e.key === "Escape") {
      searchOpen = false;
    }
  }

  function selectProduct(product: Product) {
    foundProduct = product;
    productName = product.name;
    productBrand = product.brand ?? "";
    productQuantity = product.quantity ?? "";
    productCategoryId = product.category_id ?? "";
    productImageUrl = product.image_url ?? "";
    scannedEan = product.ean ?? "";
    showProductFields = false;
    searchOpen = false;
    manualQuery = "";
    step = "details";
  }

  function selectNewProduct(query: string) {
    const isNumeric = /^\d+$/.test(query);
    foundProduct = null;
    showProductFields = true;
    scannedEan = isNumeric ? query : "";
    productName = "";
    productBrand = isNumeric ? "" : query;
    productQuantity = "";
    productCategoryId = "";
    productImageUrl = "";
    searchOpen = false;
    manualQuery = "";
    step = "details";
  }

  async function lookupEan() {
    error = "";

    try {
      const product = await fetchProductByEan(scannedEan.trim());
      if (product) {
        foundProduct = product;
        productName = product.name;
        productBrand = product.brand ?? "";
        productQuantity = product.quantity ?? "";
        productCategoryId = product.category_id ?? "";
        productImageUrl = product.image_url ?? "";
        showProductFields = false;
      } else {
        foundProduct = null;
        productName = "";
        productBrand = "";
        productQuantity = "";
        productCategoryId = "";
        productImageUrl = "";
        showProductFields = true;
      }
      step = "details";
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleSubmit(e: SubmitEvent) {
    e.preventDefault();
    const s = $session;
    if (!s?.user) return;

    saving = true;
    error = "";

    try {
      let productId: number;

      if (foundProduct) {
        productId = foundProduct.id;
      } else {
        // Create new product
        if (!productName.trim()) {
          error = "Product name is required.";
          saving = false;
          return;
        }
        const newProduct = await createProduct({
          name: productName.trim(),
          brand: productBrand.trim() || null,
          ean: scannedEan.trim() || null,
          quantity: productQuantity.trim() || null,
          category_id: productCategoryId === "" ? null : productCategoryId,
          image_url: productImageUrl.trim() || null,
        });
        productId = newProduct.id;
      }

      // Create item(s)
      const count = Math.max(1, Math.floor(itemCount));
      for (let i = 0; i < count; i++) {
        await createItem(s.user.id, {
          product_id: productId,
          expiration_date: expirationDate || null,
          storage_location_id:
            storageLocationId === "" ? null : storageLocationId,
        });
      }

      const displayName = foundProduct?.brand
        ? `${foundProduct.brand} ${foundProduct.name}`
        : (foundProduct?.name ??
          (productBrand ? `${productBrand} ${productName}` : productName));
      const countLabel = itemCount > 1 ? `${itemCount} items` : "item";
      addAnother();
      showToast(`Added ${countLabel} for "${displayName}"`);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      saving = false;
    }
  }

  let toastMessage = $state("");
  let toastVisible = $state(false);
  let toastTimeout: ReturnType<typeof setTimeout> | null = null;

  function showToast(message: string) {
    if (toastTimeout) clearTimeout(toastTimeout);
    toastMessage = message;
    toastVisible = true;
    toastTimeout = setTimeout(() => {
      toastVisible = false;
    }, 3500);
  }

  function addAnother() {
    scannedEan = "";
    foundProduct = null;
    showProductFields = false;
    productName = "";
    productBrand = "";
    productQuantity = "";
    productCategoryId = "";
    productImageUrl = "";
    expirationDate = "";
    // Keep storage location for convenience
    itemCount = 1;
    manualQuery = "";
    searchResults = [];
    searchOpen = false;
    selectedIndex = -1;
    error = "";
    step = "input";
  }
</script>

{#if toastVisible}
  <div
    class="toast toast-top toast-center z-50"
    role="status"
    aria-live="polite"
  >
    <div class="alert alert-success shadow-lg gap-2 pr-3">
      <Check class="w-4 h-4 shrink-0" />
      <span class="text-sm">{toastMessage}</span>
    </div>
  </div>
{/if}

<div class="space-y-6 animate-in slide-in-from-bottom duration-500">
  <div>
    <h1 class="text-3xl font-black text-base-content">Quick Add</h1>
    <p class="text-sm text-base-content/60">
      Scan a barcode or type an EAN to quickly add items
    </p>
  </div>

  {#if error}
    <div class="alert alert-error text-sm">{error}</div>
  {/if}

  {#if loading}
    <div class="flex justify-center py-12">
      <span class="loading loading-infinity loading-lg text-primary"></span>
    </div>
  {:else if step === "input"}
    <!-- Step 1: Scan or enter EAN -->
    <div class="space-y-4">
      <!-- Camera Scanner -->
      <div class="card bg-base-200">
        <div class="card-body p-4">
          <div
            id="barcode-scanner"
            bind:this={scannerContainer}
            class="w-full rounded-lg overflow-hidden [&_video]:!w-full [&_video]:!object-cover"
            class:hidden={!scanning}
          ></div>

          {#if !scanning}
            <button
              class="btn btn-primary btn-lg w-full gap-2"
              onclick={startScanner}
            >
              <Camera class="w-5 h-5" />
              Scan Barcode
            </button>
          {:else}
            <button
              class="btn btn-ghost btn-sm w-full gap-2 mt-2"
              onclick={stopScanner}
            >
              <CameraOff class="w-4 h-4" />
              Stop Scanner
            </button>
          {/if}
        </div>
      </div>

      <!-- Manual Entry -->
      <div class="divider text-sm text-base-content/40">or enter manually</div>

      <div class="relative">
        <div class="flex gap-2">
          <div class="relative flex-1">
            <input
              type="text"
              class="input input-bordered w-full pr-10"
              placeholder="EAN or brand"
              bind:value={manualQuery}
              oninput={handleSearchInput}
              onkeydown={handleSearchKeydown}
              onfocusin={() => {
                if (searchResults.length > 0) searchOpen = true;
              }}
              onfocusout={() => setTimeout(() => (searchOpen = false), 200)}
            />
            <Search
              class="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 text-base-content/30 pointer-events-none"
            />
          </div>
          <button
            class="btn btn-primary"
            onclick={handleManualSearch}
            disabled={!manualQuery.trim()}
          >
            <Plus class="w-4 h-4" />
            Add
          </button>
        </div>

        <!-- Autocomplete dropdown -->
        {#if searchOpen && searchResults.length > 0}
          <ul
            class="menu bg-base-100 border border-base-300 rounded-box shadow-lg absolute left-0 right-0 top-full mt-1 z-50 max-h-60 overflow-y-auto p-1"
          >
            {#each searchResults as product, i}
              <li>
                <button
                  type="button"
                  class="flex items-center gap-3 {i === selectedIndex
                    ? 'active'
                    : ''}"
                  onmousedown={() => selectProduct(product)}
                >
                  {#if product.image_url}
                    <img
                      src={product.image_url}
                      alt=""
                      class="w-8 h-8 rounded object-cover shrink-0"
                    />
                  {:else}
                    <div
                      class="w-8 h-8 rounded bg-base-300 flex items-center justify-center shrink-0"
                    >
                      <Package class="w-4 h-4 opacity-40" />
                    </div>
                  {/if}
                  <div class="flex-1 min-w-0 text-left">
                    <div class="text-sm font-medium truncate">
                      {#if product.brand}<span class="text-base-content/60"
                          >{product.brand}</span
                        >{/if}
                      {product.name}
                    </div>
                    <div class="text-xs text-base-content/40">
                      {#if product.quantity}{product.quantity}{/if}
                      {#if product.ean}
                        {product.quantity ? "·" : ""} EAN: {product.ean}{/if}
                    </div>
                  </div>
                </button>
              </li>
            {/each}
          </ul>
        {/if}
      </div>
    </div>
  {:else if step === "details"}
    <!-- Step 2: Product details + item details -->
    <form class="space-y-4" onsubmit={handleSubmit}>
      {#if foundProduct}
        {@const IconComp = getCategoryIconComponent(foundProduct.category_icon)}
        <div
          class="flex items-center gap-3 bg-primary/10 border border-primary/20 rounded-xl px-4 py-3"
        >
          {#if foundProduct.image_url}
            <img
              src={foundProduct.image_url}
              alt={foundProduct.name}
              class="w-10 h-10 rounded-lg object-cover shadow-sm shrink-0"
            />
          {:else}
            <div
              class="w-10 h-10 rounded-lg bg-base-300 flex items-center justify-center shrink-0"
            >
              <IconComp class="w-5 h-5 opacity-40" />
            </div>
          {/if}
          <div class="flex-1 min-w-0">
            <div class="font-semibold truncate text-base-content">
              {#if foundProduct.brand}
                <span class="font-normal opacity-70"
                  >{foundProduct.brand}
                </span>
              {/if}
              {foundProduct.name}
            </div>
            <div
              class="flex items-center gap-2 mt-0.5 text-xs text-base-content/50"
            >
              {#if foundProduct.quantity}<span>{foundProduct.quantity}</span
                >{/if}
              {#if foundProduct.quantity && (foundProduct.category_name || (foundProduct.active_item_count ?? 0) > 0)}<span
                  >·</span
                >{/if}
              {#if foundProduct.category_name}
                {@const CatIcon = getCategoryIconComponent(
                  foundProduct.category_icon,
                )}
                <span class="flex items-center gap-1">
                  <CatIcon class="w-3 h-3" />
                  {foundProduct.category_name}
                </span>
              {/if}
              {#if foundProduct.category_name && (foundProduct.active_item_count ?? 0) > 0}<span
                  >·</span
                >{/if}
              {#if (foundProduct.active_item_count ?? 0) > 0}
                <span>{foundProduct.active_item_count} in stock</span>
              {/if}
            </div>
          </div>
        </div>
      {:else}
        <!-- New product fields -->
        <div class="card bg-base-200">
          <div class="card-body p-4 space-y-3">
            <h3 class="font-bold text-sm">New Product</h3>

            {#if scannedEan}
              <div class="text-sm text-base-content/60">
                EAN: <span class="font-mono">{scannedEan}</span>
              </div>
            {/if}

            <label class="form-control w-full">
              <span class="label-text text-sm">Product Name *</span>
              <input
                type="text"
                class="input input-bordered input-sm w-full"
                bind:value={productName}
                placeholder="e.g. Farfalle"
                required
              />
            </label>

            <label class="form-control w-full">
              <span class="label-text text-sm">Brand</span>
              <input
                type="text"
                class="input input-bordered input-sm w-full"
                bind:value={productBrand}
                placeholder="e.g. Barilla"
              />
            </label>

            <div class="grid grid-cols-2 gap-2">
              <label class="form-control w-full">
                <span class="label-text text-sm">Quantity</span>
                <input
                  type="text"
                  class="input input-bordered input-sm w-full"
                  bind:value={productQuantity}
                  placeholder="e.g. 200g"
                />
              </label>

              <label class="form-control w-full">
                <span class="label-text text-sm">Category</span>
                <select
                  class="select select-bordered select-sm w-full"
                  bind:value={productCategoryId}
                >
                  <option value="">— None —</option>
                  {#each categories as cat}
                    <option value={cat.id}>{cat.name}</option>
                  {/each}
                </select>
              </label>
            </div>
          </div>
        </div>
      {/if}

      <!-- Item details -->
      <div class="card bg-base-200">
        <div class="card-body p-4 space-y-3">
          <h3 class="font-bold text-sm">Item Details</h3>

          <label class="form-control w-full">
            <span class="label-text text-sm">Expiration Date</span>
            <input
              type="date"
              class="input input-bordered input-sm w-full"
              bind:value={expirationDate}
              required
            />
          </label>

          <div class="grid grid-cols-2 gap-2">
            <label class="form-control w-full">
              <span class="label-text text-sm">Storage Location</span>
              <select
                class="select select-bordered select-sm w-full"
                bind:value={storageLocationId}
              >
                <option value="">— None —</option>
                {#each locations as loc}
                  <option value={loc.id}>{loc.name}</option>
                {/each}
              </select>
            </label>

            <label class="form-control w-full">
              <span class="label-text text-sm">Quantity</span>
              <input
                type="number"
                class="input input-bordered input-sm w-full"
                min="1"
                max="99"
                bind:value={itemCount}
              />
            </label>
          </div>
        </div>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary flex-1" type="submit" disabled={saving}>
          {#if saving}
            <span class="loading loading-spinner loading-sm"></span>
          {:else}
            <Check class="w-4 h-4" />
          {/if}
          {saving
            ? "Saving..."
            : itemCount > 1
              ? `Add ${itemCount} Items`
              : "Add Item"}
        </button>
        <button class="btn btn-ghost" type="button" onclick={addAnother}>
          Back
        </button>
      </div>
    </form>
  {/if}
</div>
