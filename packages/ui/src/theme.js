/**
 * Theme handling, shared by all three apps.
 *
 * daisyUI ships 35 themes; loading all of them made the picker a wall of
 * near-identical options and every app's CSS carry variables nobody used. These
 * are the curated set — keep this list, the `@plugin "daisyui"` block in each
 * app's stylesheet, and the picker in step.
 */

/** @typedef {{ name: string, label: string, dark: boolean }} NexusTheme */

/** @type {NexusTheme[]} */
export const THEMES = [
  { name: "dim", label: "Dim", dark: true },
  { name: "night", label: "Night", dark: true },
  { name: "dark", label: "Dark", dark: true },
  { name: "nord", label: "Nord", dark: true },
  { name: "business", label: "Business", dark: true },
  { name: "forest", label: "Forest", dark: true },
  { name: "coffee", label: "Coffee", dark: true },
  { name: "halloween", label: "Halloween", dark: true },
  { name: "sunset", label: "Sunset", dark: true },
  { name: "emerald", label: "Emerald", dark: false },
  { name: "lofi", label: "Lofi", dark: false },
];

/** Used when the stored preference is "auto". */
export const AUTO_DARK_THEME = "dark";
export const AUTO_LIGHT_THEME = "lofi";

export const AUTO = "auto";

const isBrowser = () => typeof window !== "undefined";

/** @param {string} value */
export function isKnownTheme(value) {
  return value === AUTO || THEMES.some((t) => t.name === value);
}

/**
 * Resolve a stored preference to the theme that should be on <html>.
 * @param {string} theme
 * @returns {string}
 */
export function resolveTheme(theme) {
  if (theme && theme !== AUTO && isKnownTheme(theme)) return theme;

  const prefersDark =
    isBrowser() && window.matchMedia("(prefers-color-scheme: dark)").matches;

  return prefersDark ? AUTO_DARK_THEME : AUTO_LIGHT_THEME;
}

/**
 * @param {string} storageKey
 * @returns {string} the stored preference, or "auto"
 */
export function getTheme(storageKey) {
  if (!isBrowser()) return AUTO;

  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return AUTO;
    const parsed = JSON.parse(raw);
    return typeof parsed === "string" && isKnownTheme(parsed) ? parsed : AUTO;
  } catch {
    return AUTO;
  }
}

/**
 * @param {string} theme
 * @param {{ animated?: boolean }} [options]
 */
export function applyTheme(theme, options = {}) {
  if (!isBrowser()) return;

  const html = document.documentElement;
  const resolved = resolveTheme(theme);

  if (options.animated) {
    html.classList.add("theme-transition");
    window.setTimeout(() => html.classList.remove("theme-transition"), 300);
  }

  html.setAttribute("data-theme", resolved);
  html.classList.toggle(
    "dark",
    THEMES.some((t) => t.name === resolved && t.dark),
  );
}

/**
 * @param {string} storageKey
 * @param {string} theme
 */
export function setTheme(storageKey, theme) {
  if (!isBrowser()) return;

  try {
    localStorage.setItem(storageKey, JSON.stringify(theme));
  } catch (error) {
    console.warn("Failed to save theme:", error);
  }

  applyTheme(theme, { animated: true });
}

/**
 * The inline <head> script that sets data-theme before first paint, so the page
 * never flashes the wrong colours. Returned as source text because an inline
 * Astro script cannot import anything — each Layout drops this into
 * `<script is:inline set:html={...}>`.
 *
 * @param {string} storageKey
 */
export function themeBootstrapScript(storageKey) {
  const themeNames = JSON.stringify(THEMES.map((t) => t.name));

  return `(function () {
  try {
    var known = ${themeNames};
    var stored = localStorage.getItem(${JSON.stringify(storageKey)});
    var theme = stored ? JSON.parse(stored) : "auto";
    if (typeof theme !== "string" || known.indexOf(theme) === -1) {
      var prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
      theme = prefersDark ? ${JSON.stringify(AUTO_DARK_THEME)} : ${JSON.stringify(AUTO_LIGHT_THEME)};
    }
    document.documentElement.setAttribute("data-theme", theme);
  } catch (e) {
    document.documentElement.setAttribute("data-theme", ${JSON.stringify(AUTO_DARK_THEME)});
  }
})();`;
}
