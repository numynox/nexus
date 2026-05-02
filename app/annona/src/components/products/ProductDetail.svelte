<script lang="ts">
  import {
    ArrowLeft,
    Check,
    MessageSquare,
    Package,
    Pencil,
    Plus,
    Send,
    Trash2,
    Undo2,
    X,
  } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    getCategoryBorderColor,
    getCategoryIconComponent,
  } from "../../lib/categoryMeta";
  import {
    addItemComment,
    addItemLogEntry,
    consumeItem,
    deleteItem,
    deleteProduct,
    fetchItemLog,
    fetchItemsForProduct,
    fetchStorageLocations,
    unconsumeItem,
    updateItemExpiration,
    updateItemLocation,
    type Item,
    type ItemLogEntry,
    type Product,
    type StorageLocation,
  } from "../../lib/data";
  import { session } from "../../lib/stores";
  import ItemForm from "./ItemForm.svelte";

  interface Props {
    product: Product;
    onBack: () => void;
    onEdit: () => void;
  }

  let { product, onBack, onEdit }: Props = $props();

  let items: Item[] = $state([]);
  let locations: StorageLocation[] = $state([]);
  let loading = $state(true);
  let error = $state("");
  let showItemForm = $state(false);
  let confirmDeleteProduct = $state(false);

  /** Item log keyed by item id */
  let itemLogs: Record<number, ItemLogEntry[]> = $state({});
  let expandedLogItemId: number | null = $state(null);
  let commentTexts: Record<number, string> = $state({});
  let sendingComment: number | null = $state(null);

  /** Inline editing */
  let editingItemId: number | null = $state(null);
  let editExpiry = $state("");
  let editLocId = $state<number | "">("");
  let savingEdit = $state(false);

  onMount(async () => {
    const s = $session;
    if (!s?.user) return;
    await loadData();
  });

  async function loadData() {
    loading = true;
    error = "";
    try {
      const [i, l] = await Promise.all([
        fetchItemsForProduct(product.id),
        fetchStorageLocations(),
      ]);
      items = i;
      locations = l;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      loading = false;
    }
  }

  async function toggleLog(itemId: number) {
    if (expandedLogItemId === itemId) {
      expandedLogItemId = null;
      return;
    }
    expandedLogItemId = itemId;
    if (!itemLogs[itemId]) {
      try {
        itemLogs[itemId] = await fetchItemLog(itemId);
      } catch {
        itemLogs[itemId] = [];
      }
    }
  }

  async function handleSendComment(itemId: number) {
    const s = $session;
    if (!s?.user) return;
    const text = (commentTexts[itemId] ?? "").trim();
    if (!text) return;
    sendingComment = itemId;
    try {
      const entry = await addItemComment(itemId, s.user.id, text);
      const displayName =
        itemLogs[itemId]?.find((e) => e.user_id === s.user.id)
          ?.user_display_name ??
        (s.user.user_metadata?.full_name as string | undefined) ??
        s.user.email ??
        s.user.id.slice(0, 8);
      itemLogs[itemId] = [
        { ...entry, user_display_name: displayName },
        ...(itemLogs[itemId] ?? []),
      ];
      commentTexts[itemId] = "";
      // Update comment count
      items = items.map((i) =>
        i.id === itemId
          ? { ...i, comment_count: (i.comment_count ?? 0) + 1 }
          : i,
      );
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      sendingComment = null;
    }
  }

  function startEdit(item: Item) {
    editingItemId = item.id;
    editExpiry = item.expiration_date ?? "";
    editLocId = item.storage_location_id ?? "";
  }

  function cancelEdit() {
    editingItemId = null;
  }

  async function saveEdit(item: Item) {
    const s = $session;
    if (!s?.user) return;
    if (!editExpiry) {
      error = "Expiration date is required.";
      return;
    }
    savingEdit = true;
    error = "";
    try {
      const newExpiry = editExpiry || null;
      const newLocId = editLocId === "" ? null : (editLocId as number);
      const newLocName = locations.find((l) => l.id === newLocId)?.name ?? null;

      const expiryChanged = newExpiry !== item.expiration_date;
      const locChanged = newLocId !== item.storage_location_id;

      if (expiryChanged) {
        await updateItemExpiration(
          item.id,
          s.user.id,
          newExpiry,
          item.expiration_date,
        );
      }
      if (locChanged) {
        await updateItemLocation(item.id, s.user.id, newLocId, newLocName);
      }

      items = items.map((i) =>
        i.id === item.id
          ? {
              ...i,
              expiration_date: newExpiry,
              storage_location_id: newLocId,
              location_name: newLocName,
            }
          : i,
      );

      // Refresh log if expanded
      if (expandedLogItemId === item.id && (expiryChanged || locChanged)) {
        itemLogs[item.id] = await fetchItemLog(item.id);
      }
      cancelEdit();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      savingEdit = false;
    }
  }

  function daysUntilExpiration(dateStr: string | null): number | null {
    if (!dateStr) return null;
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    const exp = new Date(dateStr + "T00:00:00");
    return Math.floor((exp.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
  }

  function expirationLabel(days: number): string {
    const abs = Math.abs(days);
    if (abs >= 730) return `${Math.floor(abs / 365)}yr`;
    if (abs >= 60) return `${Math.floor(abs / 30)}mo`;
    return `${abs}d`;
  }

  function expirationBadge(dateStr: string | null): {
    text: string;
    class: string;
  } {
    const days = daysUntilExpiration(dateStr);
    if (days === null) return { text: "No date", class: "badge-ghost" };
    if (days < 0)
      return { text: `${expirationLabel(days)} ago`, class: "badge-error" };
    if (days === 0) return { text: "Expires today", class: "badge-warning" };
    if (days <= 7)
      return { text: `${expirationLabel(days)} left`, class: "badge-warning" };
    return { text: `${expirationLabel(days)} left`, class: "badge-primary" };
  }

  function formatDate(dateStr: string | null): string {
    if (!dateStr) return "—";
    const d = new Date(dateStr + "T00:00:00");
    return d.toLocaleDateString("de-DE", {
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  }

  function formatLogTime(ts: string): string {
    const d = new Date(ts);
    return d.toLocaleDateString("de-DE", {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  function logEntryLabel(entry: ItemLogEntry): string {
    switch (entry.entry_type) {
      case "created":
        return "Created item";
      case "consumed":
        return "Marked as consumed";
      case "unconsumed":
        return "Marked as active";
      case "expiration_changed":
        return entry.message
          ? `Changed expiration: ${entry.message}`
          : "Changed expiration date";
      case "location_changed":
        return entry.message
          ? `Moved to ${entry.message}`
          : "Changed storage location";
      case "deleted":
        return "Deleted item";
      default:
        return entry.entry_type;
    }
  }

  async function handleConsume(item: Item) {
    const s = $session;
    if (!s?.user) return;
    try {
      await consumeItem(item.id, s.user.id);
      items = items.map((i) =>
        i.id === item.id ? { ...i, is_consumed: true } : i,
      );
      if (expandedLogItemId === item.id) {
        itemLogs[item.id] = await fetchItemLog(item.id);
      }
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleUnconsume(item: Item) {
    const s = $session;
    if (!s?.user) return;
    try {
      await unconsumeItem(item.id, s.user.id);
      items = items.map((i) =>
        i.id === item.id ? { ...i, is_consumed: false } : i,
      );
      if (expandedLogItemId === item.id) {
        itemLogs[item.id] = await fetchItemLog(item.id);
      }
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  async function handleDeleteItem(item: Item) {
    const s = $session;
    if (!s?.user) return;
    try {
      await addItemLogEntry(item.id, s.user.id, "deleted", null);
      await deleteItem(item.id);
      items = items.filter((i) => i.id !== item.id);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  }

  function handleItemFormClose() {
    showItemForm = false;
    loadData();
  }

  let activeItems = $derived(items.filter((i) => !i.is_consumed));
  let consumedItems = $derived(items.filter((i) => i.is_consumed));
  let catBorder = $derived(getCategoryBorderColor(product.category_color));
  let catIconComp = $derived(getCategoryIconComponent(product.category_icon));

  async function handleDeleteProduct() {
    try {
      await deleteProduct(product.id);
      onBack();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
      confirmDeleteProduct = false;
    }
  }
</script>

{#snippet itemRow(item: Item, consumed: boolean)}
  {@const badge = expirationBadge(item.expiration_date)}
  {@const isEditing = editingItemId === item.id}
  {@const commentCount = item.comment_count ?? 0}
  <tr class={consumed ? "opacity-50" : ""}>
    <td
      class="py-3.5 pl-4 pr-3"
      style={catBorder
        ? `border-left: 2px solid ${catBorder};`
        : "border-left: 2px solid transparent;"}
    >
      <span class="text-sm {consumed ? 'line-through' : 'font-medium'}"
        >{formatDate(item.expiration_date)}</span
      >
      {#if !consumed}
        <span class="badge {badge.class} badge-xs ml-2">{badge.text}</span>
      {/if}
    </td>
    <td class="py-3.5 text-sm text-base-content/50">
      {item.location_name ?? "—"}
    </td>
    <td class="py-3 pr-3">
      <div class="flex items-center gap-1.5 justify-end">
        {#if !consumed}
          <button
            class="btn btn-ghost btn-sm btn-square"
            title="Edit expiration / location"
            onclick={() => (isEditing ? cancelEdit() : startEdit(item))}
          >
            <Pencil class="w-4 h-4 {isEditing ? 'text-primary' : ''}" />
          </button>
        {/if}
        <button
          class="btn btn-ghost btn-sm btn-square relative"
          title="Activity log & comments"
          onclick={() => toggleLog(item.id)}
        >
          <MessageSquare
            class="w-4 h-4 {expandedLogItemId === item.id
              ? 'text-primary'
              : ''}"
          />
          {#if commentCount > 0}
            <span
              class="absolute -top-1 -right-1 badge badge-primary badge-xs min-w-[14px] h-[14px] text-[9px] px-0.5"
              >{commentCount}</span
            >
          {/if}
        </button>
        {#if consumed}
          <button
            class="btn btn-ghost btn-sm btn-square"
            title="Undo consume"
            onclick={() => handleUnconsume(item)}
          >
            <Undo2 class="w-4 h-4" />
          </button>
        {:else}
          <button
            class="btn btn-success btn-sm gap-1.5 ml-1"
            title="Mark consumed"
            onclick={() => handleConsume(item)}
          >
            <Check class="w-3.5 h-3.5" />
            Used
          </button>
        {/if}
        <button
          class="btn btn-ghost btn-sm btn-square text-error ml-1"
          title="Delete"
          onclick={() => handleDeleteItem(item)}
        >
          <Trash2 class="w-4 h-4" />
        </button>
      </div>
    </td>
  </tr>
  {#if isEditing}
    <tr>
      <td colspan="3" class="px-4 pt-1 pb-4">
        <div class="flex items-end gap-2">
          <label class="form-control flex-1">
            <span class="label-text text-xs">Expiration Date</span>
            <input
              type="date"
              class="input input-bordered input-sm w-full"
              bind:value={editExpiry}
              required
            />
          </label>
          <label class="form-control flex-1">
            <span class="label-text text-xs">Location</span>
            <select
              class="select select-bordered select-sm w-full"
              bind:value={editLocId}
            >
              <option value="">— None —</option>
              {#each locations as loc}
                <option value={loc.id}>{loc.name}</option>
              {/each}
            </select>
          </label>
          <div class="flex gap-1 shrink-0">
            <button
              class="btn btn-primary btn-sm gap-1"
              disabled={savingEdit}
              onclick={() => saveEdit(item)}
            >
              <Check class="w-3.5 h-3.5" />
              Save
            </button>
            <button
              class="btn btn-ghost btn-sm btn-square"
              onclick={cancelEdit}
            >
              <X class="w-4 h-4" />
            </button>
          </div>
        </div>
      </td>
    </tr>
  {/if}
  {#if expandedLogItemId === item.id}
    <tr>
      <td colspan="3" class="px-4 pt-2 pb-4">
        <div class="space-y-3">
          <form
            class="flex gap-2"
            onsubmit={(e) => {
              e.preventDefault();
              handleSendComment(item.id);
            }}
          >
            <input
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="Add a comment…"
              bind:value={commentTexts[item.id]}
              disabled={sendingComment === item.id}
            />
            <button
              type="submit"
              class="btn btn-primary btn-sm btn-square"
              disabled={sendingComment === item.id ||
                !(commentTexts[item.id] ?? "").trim()}
            >
              <Send class="w-3.5 h-3.5" />
            </button>
          </form>
          {#if itemLogs[item.id]?.length}
            <div class="space-y-1">
              {#each itemLogs[item.id] as entry}
                {#if entry.entry_type === "comment"}
                  <div class="chat chat-start">
                    <div class="chat-header text-xs text-base-content/40">
                      {entry.user_display_name ?? entry.user_id.slice(0, 8)}
                      <time class="ml-1">{formatLogTime(entry.created_at)}</time
                      >
                    </div>
                    <div class="chat-bubble chat-bubble-primary text-sm">
                      {entry.message}
                    </div>
                  </div>
                {:else}
                  <div
                    class="flex items-center gap-2 text-xs text-base-content/40 py-0.5 pl-2 border-l-2 border-base-300"
                  >
                    <span class="italic">{logEntryLabel(entry)}</span>
                    <span>·</span>
                    <span
                      >{entry.user_display_name ??
                        entry.user_id.slice(0, 8)}</span
                    >
                    <span>·</span>
                    <span>{formatLogTime(entry.created_at)}</span>
                  </div>
                {/if}
              {/each}
            </div>
          {:else}
            <p class="text-xs text-base-content/40 pl-2">No activity yet.</p>
          {/if}
        </div>
      </td>
    </tr>
  {/if}
{/snippet}

{#if showItemForm}
  <ItemForm
    productId={product.id}
    productName={product.brand
      ? `${product.brand} ${product.name}`
      : product.name}
    {locations}
    onClose={handleItemFormClose}
  />
{:else}
  <div class="space-y-6 animate-in slide-in-from-bottom duration-500">
    <!-- Mobile: back button at top -->
    <div class="flex lg:hidden mb-1">
      <button class="btn btn-ghost btn-sm gap-1" onclick={onBack}>
        <ArrowLeft class="w-4 h-4" />
        Back
      </button>
    </div>

    <!-- Product header -->
    <div class="flex items-start gap-3">
      <!-- Desktop back button: -ml-11 pulls the button left so the image aligns with the content edge (btn w-8 + gap-3 = 44px) -->
      <button
        class="hidden lg:flex btn btn-ghost btn-sm btn-square shrink-0 -ml-11 mt-0.5"
        onclick={onBack}
      >
        <ArrowLeft class="w-5 h-5" />
      </button>
      {#if product.image_url}
        <img
          src={product.image_url}
          alt={product.name}
          class="w-16 h-16 rounded-xl object-cover shadow-md shrink-0"
        />
      {:else}
        <div
          class="w-16 h-16 rounded-xl bg-base-300 flex items-center justify-center shrink-0"
        >
          <svelte:component this={catIconComp} class="w-8 h-8 opacity-40" />
        </div>
      {/if}
      <div class="flex-1 min-w-0">
        <h1 class="text-2xl font-black text-base-content leading-tight">
          {#if product.brand}<span class="font-normal text-base-content/60"
              >{product.brand}</span
            >{" "}{/if}{product.name}
        </h1>
        <div
          class="flex items-center gap-2 mt-1 flex-wrap text-xs text-base-content/60"
        >
          {#if product.quantity}<span>{product.quantity}</span>{/if}
          {#if product.quantity && product.category_name}<span>·</span>{/if}
          {#if product.category_name}
            <span class="flex items-center gap-1">
              <svelte:component this={catIconComp} class="w-3 h-3" />
              {product.category_name}
            </span>
          {/if}
          {#if (product.quantity || product.category_name) && product.ean}<span
              >·</span
            >{/if}
          {#if product.ean}<span>EAN: {product.ean}</span>{/if}
        </div>
      </div>
      <div class="flex items-center gap-1 shrink-0 mt-0.5">
        <button class="btn btn-ghost btn-sm gap-1" onclick={onEdit}>
          <Pencil class="w-4 h-4" />
          Edit
        </button>
        <button
          class="btn btn-ghost btn-sm btn-square text-error"
          title="Delete product"
          onclick={() => (confirmDeleteProduct = true)}
        >
          <Trash2 class="w-4 h-4" />
        </button>
      </div>
    </div>

    {#if error}
      <div class="alert alert-error text-sm">{error}</div>
    {/if}

    {#if confirmDeleteProduct}
      <dialog class="modal modal-open">
        <div class="modal-box">
          <h3 class="font-bold text-lg">Delete product?</h3>
          <p class="py-2 text-sm text-base-content/70">
            This will permanently delete <strong
              >{product.brand
                ? `${product.brand} ${product.name}`
                : product.name}</strong
            > and all its items. This cannot be undone.
          </p>
          <div class="modal-action">
            <button class="btn btn-error" onclick={handleDeleteProduct}
              >Delete</button
            >
            <button
              class="btn btn-ghost"
              onclick={() => (confirmDeleteProduct = false)}>Cancel</button
            >
          </div>
        </div>
        <div
          class="modal-backdrop"
          onclick={() => (confirmDeleteProduct = false)}
        ></div>
      </dialog>
    {/if}

    <div class="flex items-center justify-between">
      <h2 class="text-lg font-bold">
        Items ({activeItems.length} active)
      </h2>
      <button
        class="btn btn-primary btn-sm gap-2"
        onclick={() => (showItemForm = true)}
      >
        <Plus class="w-4 h-4" />
        Add Item
      </button>
    </div>

    {#if loading}
      <div class="flex justify-center py-8">
        <span class="loading loading-infinity loading-lg text-primary"></span>
      </div>
    {:else if activeItems.length === 0 && consumedItems.length === 0}
      <div class="text-center py-8 text-base-content/50">
        <Package class="w-10 h-10 mx-auto mb-2 opacity-40" />
        <p>No items tracked for this product yet.</p>
      </div>
    {:else}
      <!-- Active Items -->
      {#if activeItems.length > 0}
        <div class="rounded-xl overflow-hidden border border-base-300">
          <table class="table table-sm w-full">
            <thead>
              <tr class="text-xs text-base-content/40 border-b border-base-300">
                <th class="pl-4 font-medium">Expiration</th>
                <th class="font-medium">Location</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {#each activeItems as item}
                {@render itemRow(item, false)}
              {/each}
            </tbody>
          </table>
        </div>
      {/if}

      <!-- Consumed Items -->
      {#if consumedItems.length > 0}
        <div class="collapse collapse-arrow rounded-xl border border-base-300">
          <input type="checkbox" />
          <div
            class="collapse-title font-medium text-sm text-base-content/60 py-3"
          >
            Consumed ({consumedItems.length})
          </div>
          <div class="collapse-content !p-0">
            <table class="table table-sm w-full">
              <tbody>
                {#each consumedItems as item}
                  {@render itemRow(item, true)}
                {/each}
              </tbody>
            </table>
          </div>
        </div>
      {/if}
    {/if}
  </div>
{/if}
