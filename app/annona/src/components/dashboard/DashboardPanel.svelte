<script lang="ts">
  import {
    ArcElement,
    BarController,
    BarElement,
    CategoryScale,
    Chart,
    DoughnutController,
    Legend,
    LinearScale,
    Tooltip,
    type ChartConfiguration,
  } from "chart.js";
  import {
    AlertTriangle,
    Check,
    Clock,
    Package,
    ShoppingBasket,
    Trash2,
  } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    getCategoryBorderColor,
    getCategoryIconComponent,
  } from "../../lib/categoryMeta";
  import { getBaseUrl } from "../../lib/config";
  import {
    consumeItem,
    deleteItem,
    fetchActiveItems,
    fetchDashboardSummary,
    fetchExpiringItems,
    type DashboardSummary,
    type Item,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  Chart.register(
    ArcElement,
    BarController,
    BarElement,
    CategoryScale,
    DoughnutController,
    LinearScale,
    Tooltip,
    Legend,
  );

  let summary: DashboardSummary | null = $state(null);
  let expiringItems: Item[] = $state([]);
  let allActiveItems: Item[] = $state([]);
  let loading = $state(true);
  let error = $state("");

  const baseUrl = getBaseUrl();

  function productsHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/products`;
  }

  function quickAddHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/add`;
  }

  let pieCanvas = $state<HTMLCanvasElement | null>(null);
  let barCanvas = $state<HTMLCanvasElement | null>(null);
  let pieChartInst: Chart<"doughnut"> | null = null;
  let barChartInst: Chart<"bar"> | null = null;

  onMount(async () => {
    const s = $session;
    if (!s?.user) return;
    await loadData();
    return () => {
      pieChartInst?.destroy();
      barChartInst?.destroy();
    };
  });

  async function loadData() {
    loading = true;
    error = "";
    try {
      const [s, items, all] = await Promise.all([
        fetchDashboardSummary(),
        fetchExpiringItems(30),
        fetchActiveItems(),
      ]);
      summary = s;
      expiringItems = items;
      allActiveItems = all;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  let timelineChartData = $derived.by(() => {
    const buckets: Record<number, number> = {};
    const today = new Date();
    const todayYear = today.getFullYear();
    const todayMonth = today.getMonth();

    for (const item of allActiveItems) {
      if (!item.expiration_date) continue;
      const exp = new Date(item.expiration_date + "T00:00:00");
      const offset =
        (exp.getFullYear() - todayYear) * 12 + (exp.getMonth() - todayMonth);
      buckets[offset] = (buckets[offset] ?? 0) + 1;
    }

    if (Object.keys(buckets).length === 0) return null;

    const months = Object.keys(buckets)
      .map(Number)
      .sort((a, b) => a - b);
    const min = months[0];
    const max = months[months.length - 1];

    const labels: string[] = [];
    const data: number[] = [];
    const colors: string[] = [];

    for (let m = min; m <= max; m++) {
      if (m === 0) labels.push("This mo.");
      else if (m > 0) labels.push(`+${m}mo`);
      else labels.push(`${m}mo`);
      data.push(buckets[m] ?? 0);
      if (m < 0) colors.push("rgba(239,68,68,0.75)");
      else if (m === 0) colors.push("rgba(245,158,11,0.75)");
      else if (m <= 2) colors.push("rgba(245,158,11,0.5)");
      else colors.push("rgba(99,102,241,0.55)");
    }

    return { labels, data, colors };
  });

  $effect(() => {
    if (!summary || !pieCanvas) return;
    pieChartInst?.destroy();
    const expired = summary.expired_items;
    const active = summary.total_active_items - expired;
    const cfg: ChartConfiguration<"doughnut"> = {
      type: "doughnut",
      data: {
        labels: ["Active", "Expired"],
        datasets: [
          {
            data: [active, expired],
            backgroundColor: ["rgba(34,197,94,0.75)", "rgba(239,68,68,0.75)"],
            borderWidth: 0,
            hoverOffset: 4,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: "65%",
        plugins: {
          legend: {
            position: "bottom",
            labels: { boxWidth: 10, padding: 14, font: { size: 11 } },
          },
        },
      },
    };
    pieChartInst = new Chart(pieCanvas, cfg);
  });

  $effect(() => {
    if (!timelineChartData || !barCanvas) return;
    barChartInst?.destroy();
    const { labels, data, colors } = timelineChartData;
    const cfg: ChartConfiguration<"bar"> = {
      type: "bar",
      data: {
        labels,
        datasets: [
          {
            label: "Items",
            data,
            backgroundColor: colors,
            borderRadius: 4,
            borderWidth: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { grid: { display: false }, ticks: { font: { size: 10 } } },
          y: {
            beginAtZero: true,
            ticks: { stepSize: 1, font: { size: 10 } },
            grid: { color: "rgba(128,128,128,0.1)" },
          },
        },
      },
    };
    barChartInst = new Chart(barCanvas, cfg);
  });

  function daysUntilExpiration(dateStr: string | null): number | null {
    if (!dateStr) return null;
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    const exp = new Date(dateStr + "T00:00:00");
    return Math.floor((exp.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
  }

  function expirationBadge(dateStr: string | null): {
    text: string;
    class: string;
  } {
    const days = daysUntilExpiration(dateStr);
    if (days === null) return { text: "No date", class: "badge-ghost" };
    if (days < 0)
      return { text: `Expired ${Math.abs(days)}d ago`, class: "badge-error" };
    if (days === 0) return { text: "Expires today", class: "badge-error" };
    if (days <= 3) return { text: `${days}d left`, class: "badge-warning" };
    if (days <= 7) return { text: `${days}d left`, class: "badge-info" };
    return { text: `${days}d left`, class: "badge-ghost" };
  }

  async function handleConsume(item: Item) {
    const s = $session;
    if (!s?.user) return;
    try {
      await consumeItem(item.id, s.user.id);
      expiringItems = expiringItems.filter((i) => i.id !== item.id);
      allActiveItems = allActiveItems.filter((i) => i.id !== item.id);
      if (summary) {
        summary = {
          ...summary,
          total_active_items: summary.total_active_items - 1,
          consumed_items: summary.consumed_items + 1,
        };
      }
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleDelete(item: Item) {
    try {
      await deleteItem(item.id);
      expiringItems = expiringItems.filter((i) => i.id !== item.id);
      allActiveItems = allActiveItems.filter((i) => i.id !== item.id);
      if (summary) {
        summary = {
          ...summary,
          total_active_items: summary.total_active_items - 1,
        };
      }
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }
</script>

<div class="space-y-6 animate-in slide-in-from-bottom duration-500">
  <div class="flex items-center justify-between">
    <div>
      <h1 class="text-3xl font-black text-base-content">Dashboard</h1>
      <p class="text-sm text-base-content/60">
        Your grocery inventory at a glance
      </p>
    </div>
    <a href={quickAddHref()} class="btn btn-primary gap-2">
      <ShoppingBasket class="w-4 h-4" />
      Quick Add
    </a>
  </div>

  {#if loading}
    <div class="flex justify-center py-12">
      <span class="loading loading-infinity loading-lg text-primary"></span>
    </div>
  {:else if error}
    <div class="alert alert-error">{error}</div>
  {:else if summary}
    <!-- Summary Cards -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div class="stat bg-base-200 rounded-xl p-4">
        <div class="stat-title text-xs">Total Products</div>
        <div class="stat-value text-2xl">{summary.total_products}</div>
      </div>
      <div class="stat bg-base-200 rounded-xl p-4">
        <div class="stat-title text-xs">Items in Stock</div>
        <div class="stat-value text-2xl">{summary.total_active_items}</div>
      </div>
      <div class="stat bg-base-200 rounded-xl p-4">
        <div class="stat-title text-xs flex items-center gap-1">
          <AlertTriangle class="w-3 h-3 text-error" /> Expired
        </div>
        <div class="stat-value text-2xl text-error">
          {summary.expired_items}
        </div>
      </div>
      <div class="stat bg-base-200 rounded-xl p-4">
        <div class="stat-title text-xs flex items-center gap-1">
          <Clock class="w-3 h-3 text-warning" /> Expiring ≤7d
        </div>
        <div class="stat-value text-2xl text-warning">
          {summary.expiring_within_7_days}
        </div>
      </div>
    </div>

    <!-- Charts -->
    {#if summary.total_active_items > 0}
      <div class="grid md:grid-cols-2 gap-4">
        <div class="card bg-base-200">
          <div class="card-body p-4">
            <div style="position: relative; height: 220px;">
              <canvas bind:this={pieCanvas}></canvas>
            </div>
          </div>
        </div>
        <div class="card bg-base-200">
          <div class="card-body p-4">
            {#if timelineChartData}
              <div style="position: relative; height: 220px;">
                <canvas bind:this={barCanvas}></canvas>
              </div>
            {:else}
              <p class="text-sm text-base-content/40 py-4 text-center">
                No items with expiration dates
              </p>
            {/if}
          </div>
        </div>
      </div>
    {/if}

    <!-- Distribution with split progress bars -->
    {#if summary.items_by_category.length > 0 || summary.items_by_location.length > 0}
      {@const maxCat = Math.max(
        ...summary.items_by_category.map((c) => c.count),
        1,
      )}
      {@const maxLoc = Math.max(
        ...summary.items_by_location.map((l) => l.count),
        1,
      )}
      <div class="grid md:grid-cols-2 gap-4">
        {#if summary.items_by_category.length > 0}
          <div class="card bg-base-200">
            <div class="card-body p-4">
              <h3 class="card-title text-sm">Categories</h3>
              <div class="space-y-2.5">
                {#each summary.items_by_category as cat}
                  {@const pct = Math.round((cat.count / maxCat) * 100)}
                  {@const okCount = cat.count - (cat.expired_count ?? 0)}
                  {@const expCount = cat.expired_count ?? 0}
                  <div class="space-y-1">
                    <div class="flex justify-between text-sm">
                      <span class="text-base-content/70"
                        >{cat.category ?? "Uncategorized"}</span
                      >
                      <span class="font-semibold text-xs tabular-nums">
                        {cat.count}{#if expCount > 0}<span class="text-error">
                            ({expCount} exp.)</span
                          >{/if}
                      </span>
                    </div>
                    <div class="h-1.5 rounded-full bg-base-300 overflow-hidden">
                      <div
                        class="h-full flex rounded-full overflow-hidden"
                        style="width: {pct}%"
                      >
                        <div
                          class="h-full bg-base-content/25"
                          style="flex: {okCount}"
                        ></div>
                        {#if expCount > 0}
                          <div
                            class="h-full bg-error/65"
                            style="flex: {expCount}"
                          ></div>
                        {/if}
                      </div>
                    </div>
                  </div>
                {/each}
              </div>
            </div>
          </div>
        {/if}
        {#if summary.items_by_location.length > 0}
          <div class="card bg-base-200">
            <div class="card-body p-4">
              <h3 class="card-title text-sm">Locations</h3>
              <div class="space-y-2.5">
                {#each summary.items_by_location as loc}
                  {@const pct = Math.round((loc.count / maxLoc) * 100)}
                  {@const okCount = loc.count - (loc.expired_count ?? 0)}
                  {@const expCount = loc.expired_count ?? 0}
                  <div class="space-y-1">
                    <div class="flex justify-between text-sm">
                      <span class="text-base-content/70">{loc.location}</span>
                      <span class="font-semibold text-xs tabular-nums">
                        {loc.count}{#if expCount > 0}<span class="text-error">
                            ({expCount} exp.)</span
                          >{/if}
                      </span>
                    </div>
                    <div class="h-1.5 rounded-full bg-base-300 overflow-hidden">
                      <div
                        class="h-full flex rounded-full overflow-hidden"
                        style="width: {pct}%"
                      >
                        <div
                          class="h-full bg-base-content/25"
                          style="flex: {okCount}"
                        ></div>
                        {#if expCount > 0}
                          <div
                            class="h-full bg-error/65"
                            style="flex: {expCount}"
                          ></div>
                        {/if}
                      </div>
                    </div>
                  </div>
                {/each}
              </div>
            </div>
          </div>
        {/if}
      </div>
    {/if}

    <!-- Expiring Items — styled like product list -->
    <div>
      <h2 class="text-lg font-bold mb-3">
        {#if expiringItems.length > 0}
          Items Expiring Soon
        {:else}
          No Items Expiring Within 30 Days
        {/if}
      </h2>

      {#if expiringItems.length === 0 && summary.total_active_items === 0}
        <div class="text-center py-12 text-base-content/50">
          <Package class="w-12 h-12 mx-auto mb-3 opacity-40" />
          <p class="text-lg font-medium">No items tracked yet</p>
          <p class="text-sm mt-1">
            <a href={quickAddHref()} class="link link-primary"
              >Add your first item</a
            >
            or
            <a href={productsHref()} class="link link-primary"
              >create a product</a
            >
          </p>
        </div>
      {:else}
        <div class="space-y-2">
          {#each expiringItems as item}
            {@const border = getCategoryBorderColor(item.category_color)}
            {@const IconComp = getCategoryIconComponent(item.category_icon)}
            {@const badge = expirationBadge(item.expiration_date)}
            <div
              class="flex items-center gap-3 bg-base-200 rounded-xl px-4 py-3"
              style={border
                ? `border-left: 2px solid ${border};`
                : "border-left: 2px solid transparent;"}
            >
              {#if item.product_image_url}
                <img
                  src={item.product_image_url}
                  alt={item.product_name}
                  class="w-10 h-10 rounded-lg object-cover shrink-0 shadow-sm"
                />
              {:else}
                <div
                  class="w-10 h-10 rounded-lg bg-base-300 flex items-center justify-center shrink-0"
                >
                  <svelte:component
                    this={IconComp}
                    class="w-5 h-5 opacity-40"
                  />
                </div>
              {/if}
              <div class="flex-1 min-w-0">
                <div class="font-semibold truncate text-base-content">
                  {#if item.product_brand}<span class="font-normal opacity-70"
                      >{item.product_brand}
                    </span>{/if}
                  {item.product_name}
                </div>
                <div
                  class="flex items-center gap-2 mt-0.5 text-xs text-base-content/50"
                >
                  {#if item.product_quantity}<span>{item.product_quantity}</span
                    >{/if}
                  {#if item.product_quantity && (item.category_name || item.location_name)}<span
                      >·</span
                    >{/if}
                  {#if item.category_name}
                    {@const CatIcon = getCategoryIconComponent(
                      item.category_icon,
                    )}
                    <span class="flex items-center gap-1">
                      <svelte:component this={CatIcon} class="w-3 h-3" />
                      {item.category_name}
                    </span>
                  {/if}
                  {#if item.category_name && item.location_name}<span>·</span
                    >{/if}
                  {#if item.location_name}<span>{item.location_name}</span>{/if}
                </div>
              </div>
              <span
                class="badge {badge.class} badge-sm whitespace-nowrap shrink-0"
                >{badge.text}</span
              >
              <div class="flex gap-1 shrink-0">
                <button
                  class="btn btn-ghost btn-sm btn-square"
                  title="Mark as consumed"
                  onclick={() => handleConsume(item)}
                >
                  <Check class="w-4 h-4" />
                </button>
                <button
                  class="btn btn-ghost btn-sm btn-square text-error"
                  title="Delete"
                  onclick={() => handleDelete(item)}
                >
                  <Trash2 class="w-4 h-4" />
                </button>
              </div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>
