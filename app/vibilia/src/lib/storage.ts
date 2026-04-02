const isBrowser = (): boolean => typeof window !== "undefined";

const STORAGE_KEYS = {
  THEME: "vibilia_theme",
} as const;

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
