import { existsSync, readFileSync } from "fs";
import { load } from "js-yaml";
import { join } from "path";

const PROJECT_ROOT = join(import.meta.dirname, "..", "..", "..", "..");
const CONFIG_PATH = join(PROJECT_ROOT, "config.yaml");

export interface VibiliaPageConfig {
  priceBucketMinutes: number;
  searchRadiusKm: number;
}

const DEFAULT_CONFIG: VibiliaPageConfig = {
  priceBucketMinutes: 10,
  searchRadiusKm: 3,
};

function parsePositiveNumber(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function getVibiliaPageConfig(): VibiliaPageConfig {
  if (!existsSync(CONFIG_PATH)) {
    return DEFAULT_CONFIG;
  }

  const configData = readFileSync(CONFIG_PATH, "utf-8");
  const config = load(configData) as any;

  return {
    priceBucketMinutes: Math.round(
      parsePositiveNumber(
        config.settings?.vibilia?.price_bucket_minutes,
        DEFAULT_CONFIG.priceBucketMinutes,
      ),
    ),
    searchRadiusKm: parsePositiveNumber(
      config.settings?.vibilia?.search_radius,
      DEFAULT_CONFIG.searchRadiusKm,
    ),
  };
}
