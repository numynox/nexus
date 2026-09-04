/**
 * Build-time configuration, injected by astro.config.mjs from the repository's
 * config.yaml (see packages/config). Safe to import from both .astro files and
 * client-side components — the values are inlined at build time.
 */
declare const __NEXUS_CONFIG__: {
  title: string;
  description: string;
  baseUrl: string;
  priceBucketMinutes: number;
  searchRadiusKm: number;
};

export interface VibiliaPageConfig {
  priceBucketMinutes: number;
  searchRadiusKm: number;
}

export function getBaseUrl(): string {
  return __NEXUS_CONFIG__.baseUrl;
}

export function getWebsiteTitle(): string {
  return __NEXUS_CONFIG__.title;
}

export function getWebsiteDescription(): string {
  return __NEXUS_CONFIG__.description;
}

/** The two chart/search settings the fuel price pages pass into Svelte. */
export function getVibiliaPageConfig(): VibiliaPageConfig {
  return {
    priceBucketMinutes: __NEXUS_CONFIG__.priceBucketMinutes,
    searchRadiusKm: __NEXUS_CONFIG__.searchRadiusKm,
  };
}
