import type { AuthChangeEvent, Session } from "@supabase/supabase-js";
import { getSupabaseClient } from "./supabase";

// ── Types ──────────────────────────────────────────────────────────

export interface Category {
  id: number;
  name: string;
  color: string | null;
  icon: string | null;
  created_at: string;
}

export interface StorageLocation {
  id: number;
  name: string;
  sort_order: number;
  created_at: string;
}

export interface Product {
  id: number;
  name: string;
  brand: string | null;
  ean: string | null;
  quantity: string | null;
  category_id: number | null;
  image_url: string | null;
  created_at: string;
  updated_at: string;
  // joined
  category_name?: string | null;
  category_color?: string | null;
  category_icon?: string | null;
  active_item_count?: number;
  expired_item_count?: number;
  expiring_soon_count?: number;
}

export interface Item {
  id: number;
  product_id: number;
  expiration_date: string | null;
  storage_location_id: number | null;
  is_consumed: boolean;
  created_at: string;
  // joined
  product_name?: string;
  product_brand?: string | null;
  product_quantity?: string | null;
  product_ean?: string | null;
  product_image_url?: string | null;
  category_name?: string | null;
  category_color?: string | null;
  category_icon?: string | null;
  location_name?: string | null;
  comment_count?: number;
}

export interface ItemLogEntry {
  id: number;
  item_id: number;
  user_id: string;
  entry_type:
    | "comment"
    | "created"
    | "expiration_changed"
    | "location_changed"
    | "consumed"
    | "unconsumed"
    | "deleted";
  message: string | null;
  created_at: string;
  // joined
  user_display_name?: string | null;
}

export interface DashboardSummary {
  total_products: number;
  total_active_items: number;
  expired_items: number;
  expiring_within_7_days: number;
  expiring_within_30_days: number;
  no_expiration: number;
  consumed_items: number;
  items_by_category: Array<{
    category: string | null;
    count: number;
    expired_count: number;
  }>;
  items_by_location: Array<{
    location: string;
    count: number;
    expired_count: number;
  }>;
}

// ── Default categories ─────────────────────────────────────────────

export const DEFAULT_CATEGORIES: {
  name: string;
  color: string;
  icon: string;
}[] = [
  { name: "Bakery", color: "amber", icon: "cookie" },
  { name: "Beverages", color: "sky", icon: "coffee" },
  { name: "Condiments & Dressings", color: "coral", icon: "flame" },
  { name: "Cooking & Baking", color: "yellow", icon: "wheat" },
  { name: "Dairy & Cheese", color: "blue", icon: "milk" },
  { name: "Frozen Foods", color: "teal", icon: "snowflake" },
  { name: "Meat", color: "rose", icon: "beef" },
  { name: "Pasta", color: "brown", icon: "wheat" },
  { name: "Seafood", color: "green", icon: "fish" },
  { name: "Snacks", color: "pink", icon: "candy" },
  { name: "Soups & Canned Goods", color: "violet", icon: "soup" },
  { name: "Spices", color: "lime", icon: "flame" },
];

// ── Auth ───────────────────────────────────────────────────────────

export async function getSession(): Promise<Session | null> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.auth.getSession();
  if (error) throw error;
  return data.session;
}

export async function signInWithPassword(
  email: string,
  password: string,
): Promise<Session> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.auth.signInWithPassword({ email, password });
  if (error) throw error;
  return data.session;
}

export async function signOut(): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.auth.signOut();
  if (error) throw error;
}

export function onAuthStateChange(
  callback: (event: AuthChangeEvent, session: Session | null) => void,
) {
  const sb = getSupabaseClient();
  return sb.auth.onAuthStateChange(callback);
}

// ── Categories ─────────────────────────────────────────────────────

export async function fetchCategories(): Promise<Category[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_categories")
    .select("*")
    .order("name", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function createCategory(
  name: string,
  color?: string | null,
  icon?: string | null,
): Promise<Category> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_categories")
    .insert({ name: name.trim(), color: color ?? null, icon: icon ?? null })
    .select()
    .single();
  if (error) throw error;
  return data;
}

export async function updateCategory(
  categoryId: number,
  name: string,
  color?: string | null,
  icon?: string | null,
): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_categories")
    .update({ name: name.trim(), color: color ?? null, icon: icon ?? null })
    .eq("id", categoryId);
  if (error) throw error;
}

export async function deleteCategory(categoryId: number): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_categories")
    .delete()
    .eq("id", categoryId);
  if (error) throw error;
}

export async function seedDefaultCategories(): Promise<void> {
  const existing = await fetchCategories();
  if (existing.length > 0) return;

  const sb = getSupabaseClient();
  const rows = DEFAULT_CATEGORIES.map(({ name, color, icon }) => ({
    name,
    color,
    icon,
  }));
  const { error } = await sb.from("annona_categories").insert(rows);
  if (error) throw error;
}

// ── Storage Locations ──────────────────────────────────────────────

export async function fetchStorageLocations(): Promise<StorageLocation[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_storage_locations")
    .select("*")
    .order("sort_order", { ascending: true })
    .order("name", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function createStorageLocation(
  name: string,
): Promise<StorageLocation> {
  const sb = getSupabaseClient();
  const { data: maxRow } = await sb
    .from("annona_storage_locations")
    .select("sort_order")
    .order("sort_order", { ascending: false })
    .limit(1)
    .single();
  const nextSort = (maxRow?.sort_order ?? -1) + 1;

  const { data, error } = await sb
    .from("annona_storage_locations")
    .insert({ name: name.trim(), sort_order: nextSort })
    .select()
    .single();
  if (error) throw error;
  return data;
}

export async function updateStorageLocation(
  locationId: number,
  name: string,
): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_storage_locations")
    .update({ name: name.trim() })
    .eq("id", locationId);
  if (error) throw error;
}

export async function deleteStorageLocation(locationId: number): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_storage_locations")
    .delete()
    .eq("id", locationId);
  if (error) throw error;
}

// ── Products ───────────────────────────────────────────────────────

export async function fetchProducts(): Promise<Product[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_products")
    .select(
      "*, annona_categories(name, color, icon), annona_items(id, is_consumed, expiration_date)",
    )
    .order("brand", { ascending: true, nullsFirst: false })
    .order("name", { ascending: true });
  if (error) throw error;

  return (data ?? [])
    .map((p: any) => ({
      ...p,
      category_name: p.annona_categories?.name ?? null,
      category_color: p.annona_categories?.color ?? null,
      category_icon: p.annona_categories?.icon ?? null,
      active_item_count: (p.annona_items ?? []).filter(
        (i: any) => !i.is_consumed,
      ).length,
      expired_item_count: (() => {
        const today = new Date().toISOString().split("T")[0];
        return (p.annona_items ?? []).filter(
          (i: any) =>
            !i.is_consumed && i.expiration_date && i.expiration_date < today,
        ).length;
      })(),
      expiring_soon_count: (() => {
        const today = new Date().toISOString().split("T")[0];
        const in7 = new Date(Date.now() + 7 * 864e5)
          .toISOString()
          .split("T")[0];
        return (p.annona_items ?? []).filter(
          (i: any) =>
            !i.is_consumed &&
            i.expiration_date &&
            i.expiration_date >= today &&
            i.expiration_date <= in7,
        ).length;
      })(),
      annona_categories: undefined,
      annona_items: undefined,
    }))
    .sort((a: any, b: any) => {
      const aStock = (a.active_item_count ?? 0) > 0 ? 0 : 1;
      const bStock = (b.active_item_count ?? 0) > 0 ? 0 : 1;
      if (aStock !== bStock) return aStock - bStock;
      const aBrand = (a.brand ?? "").toLowerCase();
      const bBrand = (b.brand ?? "").toLowerCase();
      if (aBrand !== bBrand) return aBrand.localeCompare(bBrand);
      return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
    });
}

export async function fetchProductByEan(ean: string): Promise<Product | null> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_products")
    .select(
      "*, annona_categories(name, color, icon), annona_items(id, is_consumed)",
    )
    .eq("ean", ean)
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;

  return {
    ...data,
    category_name: (data as any).annona_categories?.name ?? null,
    category_color: (data as any).annona_categories?.color ?? null,
    category_icon: (data as any).annona_categories?.icon ?? null,
    active_item_count: ((data as any).annona_items ?? []).filter(
      (i: any) => !i.is_consumed,
    ).length,
    annona_categories: undefined,
    annona_items: undefined,
  } as Product;
}

/** Search products by name, brand, or EAN (case-insensitive prefix/contains). */
export async function searchProducts(query: string): Promise<Product[]> {
  const q = query.trim();
  if (!q) return [];
  const sb = getSupabaseClient();
  const pattern = `%${q}%`;
  const { data, error } = await sb
    .from("annona_products")
    .select(
      "*, annona_categories(name, color, icon), annona_items(id, is_consumed)",
    )
    .or(`name.ilike.${pattern},brand.ilike.${pattern},ean.ilike.${pattern}`)
    .order("name", { ascending: true })
    .limit(10);
  if (error) throw error;
  return (data ?? []).map((p: any) => ({
    ...p,
    category_name: p.annona_categories?.name ?? null,
    category_color: p.annona_categories?.color ?? null,
    category_icon: p.annona_categories?.icon ?? null,
    active_item_count: (p.annona_items ?? []).filter((i: any) => !i.is_consumed)
      .length,
    annona_categories: undefined,
    annona_items: undefined,
  }));
}

export async function fetchProductById(
  productId: number,
): Promise<Product | null> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_products")
    .select("*, annona_categories(name, color, icon)")
    .eq("id", productId)
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;

  return {
    ...data,
    category_name: (data as any).annona_categories?.name ?? null,
    category_color: (data as any).annona_categories?.color ?? null,
    category_icon: (data as any).annona_categories?.icon ?? null,
    annona_categories: undefined,
  } as Product;
}

export interface ProductInsert {
  name: string;
  brand: string | null;
  ean: string | null;
  quantity: string | null;
  category_id: number | null;
  image_url: string | null;
}

export async function createProduct(product: ProductInsert): Promise<Product> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_products")
    .insert(product)
    .select()
    .single();
  if (error) throw error;
  return data;
}

export async function updateProduct(
  productId: number,
  product: Partial<ProductInsert>,
): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_products")
    .update({ ...product, updated_at: new Date().toISOString() })
    .eq("id", productId);
  if (error) throw error;
}

export async function deleteProduct(productId: number): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_products")
    .delete()
    .eq("id", productId);
  if (error) throw error;
}

// ── Items ──────────────────────────────────────────────────────────

export async function fetchItemsForProduct(productId: number): Promise<Item[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_items")
    .select(
      "*, annona_products(name, brand, quantity, ean, image_url, annona_categories(name, color, icon)), annona_storage_locations(name)",
    )
    .eq("product_id", productId)
    .order("is_consumed", { ascending: true })
    .order("expiration_date", { ascending: true, nullsFirst: false });
  if (error) throw error;
  const items = (data ?? []).map(mapItemRow);

  // Attach comment counts
  if (items.length > 0) {
    const ids = items.map((i) => i.id);
    const { data: comments } = await sb
      .from("annona_item_log")
      .select("item_id")
      .eq("entry_type", "comment")
      .in("item_id", ids);
    const counts: Record<number, number> = {};
    for (const c of comments ?? [])
      counts[c.item_id] = (counts[c.item_id] ?? 0) + 1;
    return items.map((i) => ({ ...i, comment_count: counts[i.id] ?? 0 }));
  }
  return items;
}

export interface LocationProductEntry {
  product_id: number;
  product_name: string;
  product_brand: string | null;
  product_quantity: string | null;
  category_name: string | null;
  category_color: string | null;
  category_icon: string | null;
  item_count: number;
  expired_count: number;
  expiring_soon_count: number; // within 7 days (not yet expired)
}

export interface LocationGroup {
  location_id: number | null; // null = no location
  location_name: string;
  products: LocationProductEntry[];
}

export async function fetchItemsGroupedByLocation(): Promise<LocationGroup[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_items")
    .select(
      "product_id, storage_location_id, expiration_date, annona_products(name, brand, quantity, annona_categories(name, color, icon)), annona_storage_locations(id, name, sort_order)",
    )
    .eq("is_consumed", false);
  if (error) throw error;

  const today = new Date().toISOString().split("T")[0];
  const in7 = new Date(Date.now() + 7 * 864e5).toISOString().split("T")[0];

  // Group by location then product
  const locationMap = new Map<
    string,
    {
      id: number | null;
      name: string;
      sort: number;
      products: Map<number, LocationProductEntry>;
    }
  >();

  for (const row of data ?? []) {
    const locId: number | null = row.storage_location_id ?? null;
    const locName: string =
      (row as any).annona_storage_locations?.name ?? "No Location";
    const locSort: number =
      (row as any).annona_storage_locations?.sort_order ?? 999999;
    const locKey = locId !== null ? String(locId) : "__none__";

    if (!locationMap.has(locKey)) {
      locationMap.set(locKey, {
        id: locId,
        name: locName,
        sort: locSort,
        products: new Map(),
      });
    }
    const locEntry = locationMap.get(locKey)!;

    const prodId: number = row.product_id;
    const prod = (row as any).annona_products ?? {};
    const cat = prod.annona_categories ?? {};

    if (!locEntry.products.has(prodId)) {
      locEntry.products.set(prodId, {
        product_id: prodId,
        product_name: prod.name ?? "",
        product_brand: prod.brand ?? null,
        product_quantity: prod.quantity ?? null,
        category_name: cat.name ?? null,
        category_color: cat.color ?? null,
        category_icon: cat.icon ?? null,
        item_count: 0,
        expired_count: 0,
        expiring_soon_count: 0,
      });
    }
    const pe = locEntry.products.get(prodId)!;
    pe.item_count++;
    const exp = row.expiration_date;
    if (exp && exp < today) pe.expired_count++;
    else if (exp && exp >= today && exp <= in7) pe.expiring_soon_count++;
  }

  return [...locationMap.values()]
    .sort((a, b) => {
      // "No Location" goes last
      if (a.id === null) return 1;
      if (b.id === null) return -1;
      return a.sort - b.sort || a.name.localeCompare(b.name);
    })
    .map(({ id, name, products }) => ({
      location_id: id,
      location_name: name,
      products: [...products.values()].sort((a, b) => {
        const aBrand = (a.product_brand ?? "").toLowerCase();
        const bBrand = (b.product_brand ?? "").toLowerCase();
        if (aBrand !== bBrand) return aBrand.localeCompare(bBrand);
        return a.product_name
          .toLowerCase()
          .localeCompare(b.product_name.toLowerCase());
      }),
    }));
}

export async function fetchActiveItems(): Promise<Item[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_items")
    .select(
      "*, annona_products(name, brand, quantity, ean, image_url, annona_categories(name, color, icon)), annona_storage_locations(name)",
    )
    .eq("is_consumed", false)
    .order("expiration_date", { ascending: true, nullsFirst: false });
  if (error) throw error;
  return (data ?? []).map(mapItemRow);
}

export async function fetchExpiringItems(withinDays: number): Promise<Item[]> {
  const sb = getSupabaseClient();
  const now = new Date();
  const until = new Date(now);
  until.setDate(until.getDate() + withinDays);

  const { data, error } = await sb
    .from("annona_items")
    .select(
      "*, annona_products(name, brand, quantity, ean, image_url, annona_categories(name, color, icon)), annona_storage_locations(name)",
    )
    .eq("is_consumed", false)
    .lte("expiration_date", until.toISOString().split("T")[0])
    .order("expiration_date", { ascending: true });
  if (error) throw error;
  return (data ?? []).map(mapItemRow);
}

function mapItemRow(row: any): Item {
  const product = row.annona_products ?? {};
  return {
    ...row,
    product_name: product.name ?? "",
    product_brand: product.brand ?? null,
    product_quantity: product.quantity ?? null,
    product_ean: product.ean ?? null,
    product_image_url: product.image_url ?? null,
    category_name: product.annona_categories?.name ?? null,
    category_color: product.annona_categories?.color ?? null,
    category_icon: product.annona_categories?.icon ?? null,
    location_name: row.annona_storage_locations?.name ?? null,
    annona_products: undefined,
    annona_storage_locations: undefined,
  };
}

export interface ItemInsert {
  product_id: number;
  expiration_date: string | null;
  storage_location_id: number | null;
}

export async function createItem(
  userId: string,
  item: ItemInsert,
): Promise<Item> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_items")
    .insert(item)
    .select()
    .single();
  if (error) throw error;

  // Log the creation
  await addItemLogEntry(data.id, userId, "created", null);

  return data;
}

export async function updateItem(
  itemId: number,
  updates: Partial<ItemInsert & { is_consumed: boolean }>,
): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb
    .from("annona_items")
    .update(updates)
    .eq("id", itemId);
  if (error) throw error;
}

export async function deleteItem(itemId: number): Promise<void> {
  const sb = getSupabaseClient();
  const { error } = await sb.from("annona_items").delete().eq("id", itemId);
  if (error) throw error;
}

export async function consumeItem(
  itemId: number,
  userId: string,
): Promise<void> {
  await updateItem(itemId, { is_consumed: true });
  await addItemLogEntry(itemId, userId, "consumed", null);
}

export async function unconsumeItem(
  itemId: number,
  userId: string,
): Promise<void> {
  await updateItem(itemId, { is_consumed: false });
  await addItemLogEntry(itemId, userId, "unconsumed", null);
}

export async function updateItemExpiration(
  itemId: number,
  userId: string,
  newDate: string | null,
  oldDate: string | null,
): Promise<void> {
  await updateItem(itemId, { expiration_date: newDate });
  const msg = `${oldDate ?? "none"} → ${newDate ?? "none"}`;
  await addItemLogEntry(itemId, userId, "expiration_changed", msg);
}

export async function updateItemLocation(
  itemId: number,
  userId: string,
  newLocationId: number | null,
  newLocationName: string | null,
): Promise<void> {
  await updateItem(itemId, { storage_location_id: newLocationId });
  await addItemLogEntry(itemId, userId, "location_changed", newLocationName);
}

// ── Item Log ───────────────────────────────────────────────────────

export async function fetchItemLog(itemId: number): Promise<ItemLogEntry[]> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_item_log")
    .select("*")
    .eq("item_id", itemId)
    .order("created_at", { ascending: false });
  if (error) throw error;
  const entries: ItemLogEntry[] = data ?? [];

  // Attach display names from profiles
  if (entries.length > 0) {
    const userIds = [...new Set(entries.map((e) => e.user_id))];
    const { data: profiles } = await sb
      .from("profiles")
      .select("id, full_name, email")
      .in("id", userIds);
    const nameMap: Record<string, string> = {};
    for (const p of profiles ?? [])
      nameMap[p.id] = p.full_name || p.email || p.id.slice(0, 8);
    return entries.map((e) => ({
      ...e,
      user_display_name: nameMap[e.user_id] ?? null,
    }));
  }
  return entries;
}

export async function addItemLogEntry(
  itemId: number,
  userId: string,
  entryType: ItemLogEntry["entry_type"],
  message: string | null,
): Promise<ItemLogEntry> {
  const sb = getSupabaseClient();
  const { data, error } = await sb
    .from("annona_item_log")
    .insert({
      item_id: itemId,
      user_id: userId,
      entry_type: entryType,
      message,
    })
    .select()
    .single();
  if (error) throw error;
  return data;
}

export async function addItemComment(
  itemId: number,
  userId: string,
  message: string,
): Promise<ItemLogEntry> {
  return addItemLogEntry(itemId, userId, "comment", message);
}

// ── Dashboard ──────────────────────────────────────────────────────

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const sb = getSupabaseClient();
  const { data, error } = await sb.rpc("annona_dashboard_summary");
  if (error) throw error;
  return data as DashboardSummary;
}
