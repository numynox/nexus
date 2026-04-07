const isBrowser = (): boolean => typeof window !== "undefined";

const STORAGE_KEYS = {
  THEME: "vibilia_theme",
  FUEL_PRICE_PREVIOUS_DAYS: "vibilia_fuel_price_previous_days",
  PREFERRED_FUEL_TYPE: "vibilia_preferred_fuel_type",
  LAST_SELECTED_CAR_ID: "vibilia_last_selected_car_id",
} as const;

const FUEL_PRICE_PREVIOUS_DAYS_MIN = 1;
const FUEL_PRICE_PREVIOUS_DAYS_MAX = 7;
const VALID_FUEL_TYPES = ["E5", "E10", "Diesel"] as const;

function normalizePreferredFuelType(value: unknown): string {
  if (
    typeof value === "string" &&
    VALID_FUEL_TYPES.includes(value as (typeof VALID_FUEL_TYPES)[number])
  ) {
    return value;
  }

  return "E10";
}

function clampFuelPricePreviousDays(value: number): number {
  if (!Number.isFinite(value)) return 3;
  return Math.min(
    FUEL_PRICE_PREVIOUS_DAYS_MAX,
    Math.max(FUEL_PRICE_PREVIOUS_DAYS_MIN, Math.round(value)),
  );
}

function getStorageItem<T>(key: string, fallback: T): T {
  if (!isBrowser()) return fallback;

  try {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : fallback;
  } catch {
    return fallback;
  }
}

function setStorageItem<T>(key: string, value: T): void {
  if (!isBrowser()) return;

  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    console.warn("Failed to save to localStorage:", error);
  }
}

export function getTheme(): string {
  return getStorageItem(STORAGE_KEYS.THEME, "auto");
}

export function setTheme(theme: string): void {
  setStorageItem(STORAGE_KEYS.THEME, theme);
  applyTheme(theme, { animated: true });
}

export function getFuelPricePreviousDays(): number {
  const storedValue = getStorageItem<number>(
    STORAGE_KEYS.FUEL_PRICE_PREVIOUS_DAYS,
    3,
  );
  return clampFuelPricePreviousDays(storedValue);
}

export function setFuelPricePreviousDays(days: number): void {
  setStorageItem(
    STORAGE_KEYS.FUEL_PRICE_PREVIOUS_DAYS,
    clampFuelPricePreviousDays(days),
  );
}

export function getPreferredFuelType(): string {
  const storedValue = getStorageItem<string>(
    STORAGE_KEYS.PREFERRED_FUEL_TYPE,
    "E10",
  );

  return normalizePreferredFuelType(storedValue);
}

export function setPreferredFuelType(fuelType: string): void {
  setStorageItem(
    STORAGE_KEYS.PREFERRED_FUEL_TYPE,
    normalizePreferredFuelType(fuelType),
  );
}

export function getLastSelectedCarId(): string | null {
  const storedValue = getStorageItem<string | null>(
    STORAGE_KEYS.LAST_SELECTED_CAR_ID,
    null,
  );

  return typeof storedValue === "string" && storedValue.length > 0
    ? storedValue
    : null;
}

export function setLastSelectedCarId(carId: string): void {
  if (typeof carId !== "string" || carId.length === 0) return;
  setStorageItem(STORAGE_KEYS.LAST_SELECTED_CAR_ID, carId);
}

export function clearLastSelectedCarId(): void {
  if (!isBrowser()) return;

  try {
    localStorage.removeItem(STORAGE_KEYS.LAST_SELECTED_CAR_ID);
  } catch (error) {
    console.warn("Failed to remove from localStorage:", error);
  }
}

function applyTheme(theme: string, options: { animated?: boolean } = {}): void {
  if (!isBrowser()) return;

  const html = document.documentElement;
  const { animated = false } = options;

  if (animated) {
    html.classList.add("theme-transition");
    window.setTimeout(() => {
      html.classList.remove("theme-transition");
    }, 300);
  }

  if (theme === "auto") {
    const prefersDark = window.matchMedia(
      "(prefers-color-scheme: dark)",
    ).matches;
    html.setAttribute("data-theme", prefersDark ? "dark" : "light");
    html.classList.toggle("dark", prefersDark);
  } else {
    html.setAttribute("data-theme", theme);
    html.classList.toggle("dark", theme === "dark");
  }
}
