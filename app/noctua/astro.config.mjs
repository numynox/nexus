// @ts-check
import svelte from "@astrojs/svelte";
import { defineNexusConfig } from "@nexus/config";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "astro/config";

// Base path, output directory and the values injected as __NEXUS_CONFIG__ all
// come from the repository's config.yaml — see packages/config.
const { base, outDir, define } = defineNexusConfig("noctua");

// https://astro.build/config
export default defineConfig({
  integrations: [svelte()],

  // Output static files
  output: "static",

  outDir,
  base,

  // Build options
  build: {
    assets: "assets",
  },

  // Vite configuration
  vite: {
    envDir: "../..",
    plugins: [tailwindcss()],
    define,
    build: {
      // Ensure assets are inlined or properly referenced
      assetsInlineLimit: 4096,
    },
  },
});
