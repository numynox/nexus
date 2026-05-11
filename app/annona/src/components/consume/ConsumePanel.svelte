<script lang="ts">
  import {
    CameraOff,
    Check,
    CheckCircle2,
    Package,
    PackageMinus,
    Search,
  } from "lucide-svelte";
  import { onDestroy, onMount } from "svelte";
  import { getCategoryIconComponent } from "../../lib/categoryMeta";
  import {
    consumeItem,
    fetchItemsForProduct,
    fetchProductByEan,
    searchProducts,
    type Item,
    type Product,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  let loading = $state(true);
  let error = $state("");

  // Scanner state
  let scanning = $state(false);
  let cameraFailed = $state(false);
  let scannerContainer: HTMLDivElement | undefined = $state();
  let html5QrCode: any = null;

  // Manual search state
  let manualQuery = $state("");
  let scannedEan = $state("");
  let searchResults: Product[] = $state([]);
  let searchOpen = $state(false);
  let searchFocused = $state(false);
  let searchTimeout: ReturnType<typeof setTimeout> | null = null;
  let selectedIndex = $state(-1);

  // Items state
  let foundProduct: Product | null = $state(null);
  let items: Item[] = $state([]);
  let consumedIds: Set<number> = $state(new Set());
  let loadingItems = $state(false);

  type Step = "input" | "items";
  let step: Step = $state("input");

  onMount(() => {
    loading = false;
  });

  onDestroy(() => {
    stopScanner();
    if (searchTimeout) clearTimeout(searchTimeout);
  });

  $effect(() => {
    if (
      scannerContainer &&
      step === "input" &&
      !scanning &&
      !loading &&
      !cameraFailed &&
      !searchFocused
    ) {
      startScanner();
    }
  });

  async function startScanner() {
    if (scanning || !scannerContainer) return;
    error = "";
    scanning = true;
    try {
      const { Html5Qrcode } = await import("html5-qrcode");
      if (!scannerContainer) {
        scanning = false;
        return;
      }
      scannerContainer.innerHTML = "";
      html5QrCode = new Html5Qrcode(scannerContainer.id);
      await html5QrCode.start(
        { facingMode: "environment" },
        {
          fps: 10,
          qrbox: { width: 280, height: 120 },
          aspectRatio: 4 / 3,
          formatsToSupport: [
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
          ],
        },
        (decodedText: string) => {
          handleBarcodeScan(decodedText);
        },
        () => {},
      );
    } catch {
      scanning = false;
      cameraFailed = true;
    }
  }

  async function stopScanner() {
    scanning = false;
    if (html5QrCode) {
      try {
        await html5QrCode.stop();
      } catch {
        // ignore
      }
      html5QrCode = null;
    }
  }

  async function handleBarcodeScan(code: string) {
    await stopScanner();
    scannedEan = code.trim();
    await lookupEan();
  }

  async function lookupEan() {
    error = "";
    try {
      const product = await fetchProductByEan(scannedEan.trim());
      if (product) {
        await selectProduct(product);
      } else {
        error = `No product found for EAN "${scannedEan}". Add it first via Add Product.`;
        scannedEan = "";
      }
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleManualSearch() {
    const q = manualQuery.trim();
    if (!q) return;
    if (/^\d{8,14}$/.test(q)) {
      scannedEan = q;
      await lookupEan();
      return;
    }
    // For non-numeric queries, rely on autocomplete dropdown
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

  async function selectProduct(product: Product) {
    await stopScanner();
    searchOpen = false;
    manualQuery = "";
    scannedEan = "";
    foundProduct = product;
    consumedIds = new Set();
    error = "";
    step = "items";
    loadingItems = true;
    try {
      const allItems = await fetchItemsForProduct(product.id);
      items = allItems.filter((i) => !i.is_consumed);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loadingItems = false;
    }
  }

  async function toggleConsumed(item: Item) {
    const s = $session;
    if (!s?.user || consumedIds.has(item.id)) return;
    consumedIds = new Set([...consumedIds, item.id]);
    try {
      await consumeItem(item.id, s.user.id);
    } catch (e) {
      consumedIds = new Set([...consumedIds].filter((id) => id !== item.id));
      error = e instanceof Error ? e.message : String(e);
    }
  }

  function done() {
    foundProduct = null;
    items = [];
    consumedIds = new Set();
    scannedEan = "";
    manualQuery = "";
    searchResults = [];
    searchOpen = false;
    searchFocused = false;
    selectedIndex = -1;
    error = "";
    step = "input";
  }

  function formatExpiry(date: string | null): string {
    if (!date) return "No expiry date";
    const [year, month, day] = date.split("-").map(Number);
    return new Date(year, month - 1, day).toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  }

  function getExpiryClass(date: string | null): string {
    if (!date) return "text-base-content/40";
    const today = new Date().toISOString().split("T")[0];
    const in30 = new Date(Date.now() + 30 * 864e5).toISOString().split("T")[0];
    if (date < today) return "text-error font-medium";
    if (date <= in30) return "text-warning font-medium";
    return "text-success";
  }

  const activeCount = $derived(
    items.filter((i) => !consumedIds.has(i.id)).length,
  );
</script>

<div class="space-y-6 animate-in slide-in-from-bottom duration-500">
  <div>
    <h1 class="text-3xl font-black text-base-content">Consume Product</h1>
    <p class="text-sm text-base-content/60">
      Scan a barcode or search to mark items as consumed
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
    <div class="space-y-4 md:max-w-[50%] md:mx-auto">
      <!-- Camera Scanner -->
      {#if !searchFocused && !cameraFailed}
        <div class="relative w-full" style="aspect-ratio: 4 / 3;">
          <div
            id="consume-barcode-scanner"
            bind:this={scannerContainer}
            class="w-full rounded-xl overflow-hidden [&_video]:!w-full [&_video]:!object-cover"
            class:hidden={!scanning}
          ></div>
          {#if !scanning}
            <div
              class="absolute inset-0 rounded-xl bg-base-200 animate-pulse"
            ></div>
          {/if}
        </div>
      {/if}

      {#if cameraFailed}
        <div class="flex items-center gap-2 text-sm text-base-content/50">
          <CameraOff class="w-4 h-4 shrink-0" />
          <span>Camera not available — use manual entry below.</span>
        </div>
      {/if}

      {#if !searchFocused}
        <div class="divider text-sm text-base-content/40">
          or enter manually
        </div>
      {/if}

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
                searchFocused = true;
                stopScanner();
                if (searchResults.length > 0) searchOpen = true;
              }}
              onfocusout={() => {
                setTimeout(() => {
                  searchOpen = false;
                  searchFocused = false;
                }, 200);
              }}
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
            <Search class="w-4 h-4" />
            Find
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
  {:else if step === "items" && foundProduct}
    {@const IconComp = getCategoryIconComponent(foundProduct.category_icon)}
    <div class="space-y-4 md:max-w-[50%] md:mx-auto">
      <!-- Product header -->
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
            {#if foundProduct.brand}<span class="font-normal opacity-70"
                >{foundProduct.brand}
              </span>{/if}
            {foundProduct.name}
          </div>
          <div class="text-xs text-base-content/50">
            {#if foundProduct.quantity}{foundProduct.quantity}{/if}
            {#if foundProduct.quantity && foundProduct.category_name}
              ·
            {/if}
            {#if foundProduct.category_name}{foundProduct.category_name}{/if}
          </div>
        </div>
        {#if activeCount > 0}
          <div class="badge badge-primary badge-outline shrink-0">
            {activeCount} left
          </div>
        {/if}
      </div>

      <!-- Items list -->
      {#if loadingItems}
        <div class="flex justify-center py-8">
          <span class="loading loading-spinner loading-lg text-primary"></span>
        </div>
      {:else if items.length === 0}
        <div class="text-center py-10 text-base-content/40 space-y-2">
          <PackageMinus class="w-12 h-12 mx-auto opacity-30" />
          <p class="text-sm">No active items for this product.</p>
        </div>
      {:else}
        <div class="space-y-2">
          {#each items as item (item.id)}
            {@const consumed = consumedIds.has(item.id)}
            <div
              class="flex items-center gap-3 bg-base-200 rounded-xl px-4 py-3 transition-opacity {consumed
                ? 'opacity-40'
                : ''}"
            >
              <div class="flex-1 min-w-0">
                <div
                  class="text-sm {consumed
                    ? 'line-through text-base-content/50'
                    : getExpiryClass(item.expiration_date)}"
                >
                  {formatExpiry(item.expiration_date)}
                </div>
                {#if item.location_name}
                  <div class="text-xs text-base-content/40 mt-0.5">
                    {item.location_name}
                  </div>
                {/if}
              </div>
              {#if consumed}
                <CheckCircle2 class="w-5 h-5 text-success shrink-0" />
              {:else}
                <button
                  class="btn btn-sm btn-success"
                  onclick={() => toggleConsumed(item)}
                >
                  <Check class="w-4 h-4" />
                  Consume
                </button>
              {/if}
            </div>
          {/each}
        </div>
      {/if}

      <button class="btn btn-primary w-full" onclick={done}>Done</button>
    </div>
  {/if}
</div>
