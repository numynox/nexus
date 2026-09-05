/**
 * The one reader for the repository's `config.yaml`.
 *
 * Node only, build time only: it is imported by each app's `astro.config.mjs`,
 * which injects the resulting values into the bundle as `__NEXUS_CONFIG__` (see
 * `defineNexusConfig`). Application code reads them through its own
 * `src/lib/config.ts` and never touches YAML or the filesystem.
 */

import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { load } from "js-yaml";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const CONFIG_PATH = join(REPO_ROOT, "config.yaml");

/** Fallbacks, used when `config.yaml` is missing or a key is absent/invalid. */
const DEFAULTS = {
  noctua: {
    title: "Noctua",
    description: "An RSS feed reader.",
    baseUrl: "/nexus/noctua",
    articleFetchLimit: 300,
    statisticsWeeks: 8,
  },
  vibilia: {
    title: "Vibilia",
    description: "Fuel tracker for vehicles, prices, and refuel history.",
    baseUrl: "/nexus/vibilia",
    priceBucketMinutes: 10,
    searchRadiusKm: 3,
  },
  annona: {
    title: "Annona",
    description: "Grocery expiration tracker.",
    baseUrl: "/nexus/annona",
  },
};

export const APP_NAMES = Object.keys(DEFAULTS);

function readSettings() {
  if (!existsSync(CONFIG_PATH)) return {};
  const parsed = load(readFileSync(CONFIG_PATH, "utf-8"));
  return parsed?.settings ?? {};
}

function text(value, fallback) {
  return typeof value === "string" && value.trim() !== "" ? value : fallback;
}

function positiveInt(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function positiveNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

/** Strip a trailing slash so callers can append "/..." without doubling it. */
function baseUrl(value, fallback) {
  const raw = text(value, fallback);
  return raw !== "/" && raw.endsWith("/") ? raw.slice(0, -1) : raw;
}

/**
 * The values an app needs at runtime — injected into the bundle, so they must
 * stay small and JSON-serialisable.
 *
 * @param {"noctua" | "vibilia" | "annona"} app
 */
export function loadAppConfig(app) {
  const defaults = DEFAULTS[app];
  if (!defaults) {
    throw new Error(
      `Unknown app "${app}". Expected one of: ${APP_NAMES.join(", ")}.`,
    );
  }

  const settings = readSettings();
  const appSettings = settings[app] ?? {};

  const config = {
    title: text(appSettings.title, defaults.title),
    description: text(appSettings.description, defaults.description),
    baseUrl: baseUrl(appSettings.base_url, defaults.baseUrl),
  };

  if (app === "noctua") {
    config.articleFetchLimit = positiveInt(
      appSettings.article_fetch_limit,
      defaults.articleFetchLimit,
    );
    config.statisticsWeeks = positiveInt(
      appSettings.statistics_weeks,
      defaults.statisticsWeeks,
    );
  }

  if (app === "vibilia") {
    config.priceBucketMinutes = Math.round(
      positiveNumber(
        appSettings.price_bucket_minutes,
        defaults.priceBucketMinutes,
      ),
    );
    config.searchRadiusKm = positiveNumber(
      appSettings.search_radius,
      defaults.searchRadiusKm,
    );
  }

  return config;
}

/** Absolute build output directory for an app: `<output_base>/pages/<app>`. */
export function appOutDir(app) {
  const settings = readSettings();
  const outputBase = text(settings.output_base, "output");
  return join(REPO_ROOT, outputBase, "pages", app);
}

/**
 * Everything an `astro.config.mjs` needs: the `base` and `outDir` for the
 * build, and the `define` entry that makes the same values available to
 * `src/lib/config.ts` in both the built pages and the browser bundle.
 *
 * @param {"noctua" | "vibilia" | "annona"} app
 */
export function defineNexusConfig(app) {
  const config = loadAppConfig(app);
  return {
    config,
    base: config.baseUrl,
    outDir: appOutDir(app),
    define: { __NEXUS_CONFIG__: JSON.stringify(config) },
  };
}
