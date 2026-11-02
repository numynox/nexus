/**
 * Build-time configuration, injected by astro.config.mjs from the repository's
 * config.yaml (see packages/config). Safe to import from both .astro files and
 * client-side components — the values are inlined at build time.
 */
declare const __NEXUS_CONFIG__: {
  title: string;
  description: string;
  baseUrl: string;
};

export function getBaseUrl(): string {
  return __NEXUS_CONFIG__.baseUrl;
}

export function getWebsiteTitle(): string {
  return __NEXUS_CONFIG__.title;
}

export function getWebsiteDescription(): string {
  return __NEXUS_CONFIG__.description;
}
