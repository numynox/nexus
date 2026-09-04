export type AppName = "noctua" | "vibilia" | "annona";

export const APP_NAMES: AppName[];

export interface NexusAppConfig {
  title: string;
  description: string;
  baseUrl: string;
  /** Noctua only. */
  articleFetchLimit?: number;
  /** Noctua only. */
  statisticsWeeks?: number;
  /** Vibilia only. */
  priceBucketMinutes?: number;
  /** Vibilia only. */
  searchRadiusKm?: number;
}

export function loadAppConfig(app: AppName): NexusAppConfig;

export function appOutDir(app: AppName): string;

export function defineNexusConfig(app: AppName): {
  config: NexusAppConfig;
  base: string;
  outDir: string;
  define: Record<string, string>;
};
