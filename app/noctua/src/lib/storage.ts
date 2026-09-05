import {
  getTheme as readTheme,
  setTheme as writeTheme,
} from "@nexus/ui";

/**
 * Local storage utilities for user preferences and read history
 * These are used client-side only (in Svelte components)
 */

// Check if running in browser environment
const isBrowser = (): boolean => typeof window !== "undefined";

// Storage keys
const STORAGE_KEYS = {
  THEME: "noctua_theme",
  SEEN_ARTICLES: "noctua_seen_articles",
  FILTERS: "noctua_filters",
  DENSITY: "noctua_density",
  SORT_ORDER: "noctua_sort_order",
  LAST_VISIT_AT: "noctua_last_visit_at",
  PREFERENCES: "noctua_preferences",
} as const;

/**
 * User preferences stored in local storage
 */
export interface UserPreferences {
  hideSeenArticles: boolean;
  autoMarkAsSeen: boolean;
}

export interface SeenArticleStatuses {
  [articleId: string]: true;
}

/**
 * Filter settings stored in local storage
 */
export interface FilterSettings {
  searchQuery: string;
}

const DEFAULT_PREFERENCES: UserPreferences = {
  hideSeenArticles: true,
  autoMarkAsSeen: true,
};

const DEFAULT_FILTERS: FilterSettings = {
  searchQuery: "",
};

/**
 * Migrate old localStorage data to new format
 */
function migrateLocalStorage(): void {
  if (!isBrowser()) return;

  try {
    // Migrate theme from preferences to separate key
    const oldPrefs = localStorage.getItem(STORAGE_KEYS.PREFERENCES);
    if (oldPrefs) {
      const prefs = JSON.parse(oldPrefs);
      if (prefs.theme && prefs.theme !== "auto") {
        setTheme(prefs.theme);
      }
      // Remove old theme from preferences
      delete prefs.theme;
      // Rename showReadArticles to hideSeenArticles (invert logic)
      if (prefs.showReadArticles !== undefined) {
        prefs.hideSeenArticles = !prefs.showReadArticles;
        delete prefs.showReadArticles;
      }
      // Save cleaned preferences
      localStorage.setItem(STORAGE_KEYS.PREFERENCES, JSON.stringify(prefs));
    }

    // Clean up old filter data
    const oldFilters = localStorage.getItem(STORAGE_KEYS.FILTERS);
    if (oldFilters) {
      const filters = JSON.parse(oldFilters);
      // Remove old fields
      delete filters.selectedSections;
      delete filters.selectedFeeds;
      delete filters.dateRange;
      // Keep only searchQuery
      const cleanedFilters = {
        searchQuery: filters.searchQuery || "",
      };
      localStorage.setItem(
        STORAGE_KEYS.FILTERS,
        JSON.stringify(cleanedFilters),
      );
    }

    // Remove any other old keys if they exist
    const oldKeys = [
      "noctua_hidden_sections", // This was replaced by hidden feeds logic
      "noctua_hidden_feeds",
    ];
    oldKeys.forEach((key) => {
      localStorage.removeItem(key);
    });
  } catch (error) {
    console.warn("Error during localStorage migration:", error);
  }
}

// Run migration on module load
migrateLocalStorage();

/**
 * Get item from local storage with fallback
 */
function getStorageItem<T>(key: string, fallback: T): T {
  if (!isBrowser()) return fallback;

  try {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : fallback;
  } catch {
    return fallback;
  }
}

/**
 * Set item in local storage
 */
function setStorageItem<T>(key: string, value: T): void {
  if (!isBrowser()) return;

  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    console.warn("Failed to save to localStorage:", error);
  }
}

function dispatchEventSafe(eventName: string): void {
  if (!isBrowser()) return;

  try {
    window.dispatchEvent(new CustomEvent(eventName));
  } catch {
    // ignore
  }
}

// ============ Theme ============

export function getTheme(): string {
  return readTheme(STORAGE_KEYS.THEME);
}

export function setTheme(theme: string): void {
  writeTheme(STORAGE_KEYS.THEME, theme);
}


// ============ Seen Articles ============

export function getSeenArticles(): SeenArticleStatuses {
  return getStorageItem(STORAGE_KEYS.SEEN_ARTICLES, {});
}

export function markAsSeen(articleId: string): void {
  const seen = getSeenArticles();
  seen[articleId] = true;
  setStorageItem(STORAGE_KEYS.SEEN_ARTICLES, seen);
  dispatchEventSafe("noctua:activity");
}

export function clearSeenHistory(): void {
  if (!isBrowser()) return;
  try {
    localStorage.removeItem(STORAGE_KEYS.SEEN_ARTICLES);
  } catch (e) {
    setStorageItem(STORAGE_KEYS.SEEN_ARTICLES, {});
  }
  dispatchEventSafe("seenHistoryCleared");
  dispatchEventSafe("noctua:activity");
}

// ============ Preferences ============

export function getPreferences(): UserPreferences {
  return getStorageItem(STORAGE_KEYS.PREFERENCES, DEFAULT_PREFERENCES);
}

export function setPreferences(prefs: Partial<UserPreferences>): void {
  const current = getPreferences();
  setStorageItem(STORAGE_KEYS.PREFERENCES, { ...current, ...prefs });
}

// ============ Filters ============

export function getFilters(): FilterSettings {
  return getStorageItem(STORAGE_KEYS.FILTERS, DEFAULT_FILTERS);
}

export function setFilters(filters: Partial<FilterSettings>): void {
  const current = getFilters();
  setStorageItem(STORAGE_KEYS.FILTERS, { ...current, ...filters });
}

// ============ Reading preferences ============

export type Density = "comfortable" | "compact";
export type SortOrder = "newest" | "oldest";

/** Cards with images, or a title-and-time list for scanning a few hundred. */
export function getDensity(): Density {
  return getStorageItem<Density>(STORAGE_KEYS.DENSITY, "comfortable") ===
    "compact"
    ? "compact"
    : "comfortable";
}

export function setDensity(density: Density): void {
  setStorageItem(STORAGE_KEYS.DENSITY, density);
}

/** Newest first for triage, oldest first for reading through a backlog. */
export function getSortOrder(): SortOrder {
  return getStorageItem<SortOrder>(STORAGE_KEYS.SORT_ORDER, "newest") ===
    "oldest"
    ? "oldest"
    : "newest";
}

export function setSortOrder(order: SortOrder): void {
  setStorageItem(STORAGE_KEYS.SORT_ORDER, order);
}

/**
 * When the feed was last opened, so "new since your last visit" can be marked.
 * Read once on mount and written immediately after, so a reload during the same
 * sitting does not keep re-announcing the same articles.
 */
export function getLastVisitAt(): string | null {
  const value = getStorageItem<string | null>(STORAGE_KEYS.LAST_VISIT_AT, null);
  return typeof value === "string" ? value : null;
}

export function setLastVisitAt(iso: string): void {
  setStorageItem(STORAGE_KEYS.LAST_VISIT_AT, iso);
}
