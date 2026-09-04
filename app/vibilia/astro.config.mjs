// @ts-check
import svelte from "@astrojs/svelte";
import { defineNexusConfig } from "@nexus/config";
import tailwind from "@tailwindcss/vite";
import { defineConfig } from "astro/config";

// Base path, output directory and the values injected as __NEXUS_CONFIG__ all
// come from the repository's config.yaml — see packages/config.
const { base, outDir, define } = defineNexusConfig("vibilia");

// https://astro.build/config
export default defineConfig({
  integrations: [svelte()],
  output: "static",
  outDir,
  base,
  vite: {
    envDir: "../..",
    plugins: [tailwind()],
    define,
  },
});
