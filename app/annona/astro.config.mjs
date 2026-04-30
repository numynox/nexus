// @ts-check
import svelte from "@astrojs/svelte";
import tailwind from "@tailwindcss/vite";
import { defineConfig } from "astro/config";

// https://astro.build/config
export default defineConfig({
  integrations: [svelte()],
  output: "static",
  outDir: "../../output/pages/annona",
  base: "/nexus/annona",
  vite: {
    envDir: "../..",
    plugins: [tailwind()],
  },
});
