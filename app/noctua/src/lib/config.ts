/**
 * Build-time configuration, injected by astro.config.mjs from the repository's
 * config.yaml (see packages/config). Safe to import from both .astro files and
 * client-side components — the values are inlined at build time, so nothing is
 * read from the filesystem here.
 */
declare const __NEXUS_CONFIG__: {
  title: string;
  description: string;
  baseUrl: string;
  articleFetchLimit: number;
  statisticsWeeks: number;
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

export function getArticleFetchLimit(): number {
  return __NEXUS_CONFIG__.articleFetchLimit;
}

export function getStatisticsWeeks(): number {
  return __NEXUS_CONFIG__.statisticsWeeks;
}
