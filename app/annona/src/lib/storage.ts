const isBrowser = (): boolean => typeof window !== "undefined";

const STORAGE_KEYS = {
  THEME: "annona_theme",
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

export function applyTheme(
  theme: string,
  options: { animated?: boolean } = {},
): void {
  if (!isBrowser()) return;

  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  let actualTheme = "dark";

  if (theme && theme !== "auto") {
    actualTheme = theme;
  } else if (!prefersDark) {
    actualTheme = "light";
  }

  if (options.animated) {
    document.documentElement.classList.add("theme-transition");
    setTimeout(
      () => document.documentElement.classList.remove("theme-transition"),
      400,
    );
  }

  document.documentElement.setAttribute("data-theme", actualTheme);
}
