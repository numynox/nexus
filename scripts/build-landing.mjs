#!/usr/bin/env node
/**
 * Builds the landing page at the root of the Pages site.
 *
 * https://numynox.github.io/nexus/ was a 404: the three apps live at
 * /nexus/<app> and you had to know the URLs. This writes a small index.html
 * beside them.
 *
 * Everything on the page — names, descriptions, links — comes from config.yaml
 * through @nexus/config, so the landing page cannot drift from the apps it
 * links to. It is plain HTML with inline CSS: no framework, no build step, and
 * nothing to keep in step with the apps' dependencies.
 *
 * Usage: node scripts/build-landing.mjs   (npm run build:landing)
 */

import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { APP_NAMES, appOutDir, loadAppConfig } from "@nexus/config";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** Pages are published from the parent of the per-app output directories. */
const OUT_DIR = join(appOutDir(APP_NAMES[0]), "..");

const ACCENTS = {
  noctua: "#8b8cf0",
  vibilia: "#e05a5a",
  annona: "#4bbf7a",
};

function escapeHtml(value) {
  return String(value).replace(
    /[&<>"']/g,
    (c) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      })[c],
  );
}

/**
 * Each app already publishes its own logo, so link to that copy rather than
 * inlining it: three base64 PNGs turned this page into 400 kB.
 */
function iconHref(app, baseUrl) {
  const source = join(ROOT, "app", app, "public", `${app}.png`);
  if (!existsSync(source)) return "";
  return `${baseUrl}/${app}.png`;
}

const cards = APP_NAMES.map((app) => {
  const config = loadAppConfig(app);
  const icon = iconHref(app, config.baseUrl);

  return `      <a class="app" href="${escapeHtml(config.baseUrl)}/" style="--accent: ${ACCENTS[app] ?? "#8b8cf0"}">
        ${icon ? `<img class="app-icon" src="${escapeHtml(icon)}" alt="" width="48" height="48" loading="lazy" />` : ""}
        <span class="app-text">
          <span class="app-name">${escapeHtml(config.title)}</span>
          <span class="app-description">${escapeHtml(config.description)}</span>
        </span>
        <svg class="app-arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
      </a>`;
}).join("\n");

const html = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Nexus</title>
    <meta name="description" content="Noctua, Vibilia and Annona." />
    <meta name="theme-color" content="#242933" />
    <link
      rel="icon"
      href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Ccircle cx='16' cy='16' r='13' fill='none' stroke='%238b8cf0' stroke-width='3'/%3E%3Ccircle cx='16' cy='16' r='4' fill='%238b8cf0'/%3E%3C/svg%3E"
    />
    <style>
      :root {
        color-scheme: dark light;
        --bg: #242933;
        --surface: #2a303c;
        --surface-hover: #323a48;
        --text: #e8eaf0;
        --muted: #9aa3b2;
        --border: #3a4250;
      }

      @media (prefers-color-scheme: light) {
        :root {
          --bg: #f4f5f7;
          --surface: #ffffff;
          --surface-hover: #f0f1f4;
          --text: #1b1f27;
          --muted: #5c6473;
          --border: #dfe2e8;
        }
      }

      * { box-sizing: border-box; }

      body {
        margin: 0;
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: 2rem 1.25rem;
        background: var(--bg);
        color: var(--text);
        font: 16px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif;
        -webkit-font-smoothing: antialiased;
      }

      main { width: 100%; max-width: 34rem; }

      h1 {
        margin: 0 0 0.25rem;
        font-size: 2rem;
        letter-spacing: -0.02em;
      }

      .tagline { margin: 0 0 2rem; color: var(--muted); }

      .apps { display: grid; gap: 0.75rem; }

      .app {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 1rem 1.25rem;
        border: 1px solid var(--border);
        border-radius: 0.9rem;
        background: var(--surface);
        color: inherit;
        text-decoration: none;
        transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
      }

      .app:hover,
      .app:focus-visible {
        background: var(--surface-hover);
        border-color: var(--accent);
        transform: translateY(-1px);
      }

      .app:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

      .app-icon { flex: none; border-radius: 0.5rem; object-fit: contain; }

      .app-text { display: flex; flex-direction: column; min-width: 0; flex: 1; }

      .app-name { font-weight: 600; color: var(--accent); }

      .app-description { color: var(--muted); font-size: 0.9rem; }

      .app-arrow {
        flex: none;
        width: 1.25rem;
        height: 1.25rem;
        fill: none;
        stroke: var(--muted);
        stroke-width: 2;
        stroke-linecap: round;
        stroke-linejoin: round;
      }

      footer { margin-top: 2rem; color: var(--muted); font-size: 0.85rem; }
      footer a { color: inherit; }

      @media (prefers-reduced-motion: reduce) {
        .app { transition: none; }
        .app:hover { transform: none; }
      }
    </style>
  </head>
  <body>
    <main>
      <h1>Nexus</h1>
      <p class="tagline">Internal apps.</p>

      <div class="apps">
${cards}
      </div>

      <footer>
        <a href="https://github.com/numynox/nexus">Source on GitHub</a>
      </footer>
    </main>
  </body>
</html>
`;

mkdirSync(OUT_DIR, { recursive: true });
writeFileSync(join(OUT_DIR, "index.html"), html, "utf-8");
console.log(`Landing page written to ${join(OUT_DIR, "index.html")}`);
