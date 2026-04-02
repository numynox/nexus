const isBrowser = (): boolean => typeof window !== "undefined";

const STORAGE_KEYS = {
  THEME: "vibilia_theme",
  DASHBOARD_PREVIOUS_DAYS: "vibilia_dashboard_previous_days",
} as const;

const DASHBOARD_PREVIOUS_DAYS_MIN = 1;
const DASHBOARD_PREVIOUS_DAYS_MAX = 7;

function clampDashboardPreviousDays(value: number): number {
  if (!Number.isFinite(value)) return 3;
  return Math.min(
    DASHBOARD_PREVIOUS_DAYS_MAX,
    Math.max(DASHBOARD_PREVIOUS_DAYS_MIN, Math.round(value)),
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

export function getDashboardPreviousDays(): number {
  const storedValue = getStorageItem<number>(
    STORAGE_KEYS.DASHBOARD_PREVIOUS_DAYS,
    3,
  );
  return clampDashboardPreviousDays(storedValue);
}

export function setDashboardPreviousDays(days: number): void {
  setStorageItem(
    STORAGE_KEYS.DASHBOARD_PREVIOUS_DAYS,
    clampDashboardPreviousDays(days),
  );
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
