/**
 * Theme handling, shared by all the apps.
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

/**
 * An app may bring its own themes on top of the curated set (Abeona does: its
 * brand themes, which "auto" resolves to). Every function below takes these
 * optionally; without them it is the shared set with the shared auto pair.
 *
 * @typedef {{ themes?: NexusTheme[], autoLight?: string, autoDark?: string }} ThemeSet
 */

/** @param {ThemeSet} [set] */
function themeSet(set) {
  return {
    themes: set?.themes ?? THEMES,
    autoLight: set?.autoLight ?? AUTO_LIGHT_THEME,
    autoDark: set?.autoDark ?? AUTO_DARK_THEME,
  };
}

const isBrowser = () => typeof window !== "undefined";

/**
 * @param {string} value
 * @param {ThemeSet} [set]
 */
export function isKnownTheme(value, set) {
  return value === AUTO || themeSet(set).themes.some((t) => t.name === value);
}

/**
 * Resolve a stored preference to the theme that should be on <html>.
 * @param {string} theme
 * @param {ThemeSet} [set]
 * @returns {string}
 */
export function resolveTheme(theme, set) {
  if (theme && theme !== AUTO && isKnownTheme(theme, set)) return theme;

  const prefersDark =
    isBrowser() && window.matchMedia("(prefers-color-scheme: dark)").matches;

  const { autoDark, autoLight } = themeSet(set);
  return prefersDark ? autoDark : autoLight;
}

/**
 * @param {string} storageKey
 * @param {ThemeSet} [set]
 * @returns {string} the stored preference, or "auto"
 */
export function getTheme(storageKey, set) {
  if (!isBrowser()) return AUTO;

  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return AUTO;
    const parsed = JSON.parse(raw);
    return typeof parsed === "string" && isKnownTheme(parsed, set)
      ? parsed
      : AUTO;
  } catch {
    return AUTO;
  }
}

/**
 * @param {string} theme
 * @param {{ animated?: boolean } & ThemeSet} [options]
 */
export function applyTheme(theme, options = {}) {
  if (!isBrowser()) return;

  const html = document.documentElement;
  const resolved = resolveTheme(theme, options);

  if (options.animated) {
    html.classList.add("theme-transition");
    window.setTimeout(() => html.classList.remove("theme-transition"), 300);
  }

  html.setAttribute("data-theme", resolved);
  html.classList.toggle(
    "dark",
    themeSet(options).themes.some((t) => t.name === resolved && t.dark),
  );
}

/**
 * @param {string} storageKey
 * @param {string} theme
 * @param {ThemeSet} [set]
 */
export function setTheme(storageKey, theme, set) {
  if (!isBrowser()) return;

  try {
    localStorage.setItem(storageKey, JSON.stringify(theme));
  } catch (error) {
    console.warn("Failed to save theme:", error);
  }

  applyTheme(theme, { ...set, animated: true });
}

/**
 * The inline <head> script that sets data-theme before first paint, so the page
 * never flashes the wrong colours. Returned as source text because an inline
 * Astro script cannot import anything — each Layout drops this into
 * `<script is:inline set:html={...}>`.
 *
 * @param {string} storageKey
 * @param {ThemeSet} [set]
 */
export function themeBootstrapScript(storageKey, set) {
  const { themes, autoLight, autoDark } = themeSet(set);
  const themeNames = JSON.stringify(themes.map((t) => t.name));

  return `(function () {
  try {
    var known = ${themeNames};
    var stored = localStorage.getItem(${JSON.stringify(storageKey)});
    var theme = stored ? JSON.parse(stored) : "auto";
    if (typeof theme !== "string" || known.indexOf(theme) === -1) {
      var prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
      theme = prefersDark ? ${JSON.stringify(autoDark)} : ${JSON.stringify(autoLight)};
    }
    document.documentElement.setAttribute("data-theme", theme);
  } catch (e) {
    document.documentElement.setAttribute("data-theme", ${JSON.stringify(autoDark)});
  }
})();`;
}
