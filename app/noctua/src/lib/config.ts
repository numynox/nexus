import { existsSync, readFileSync } from "fs";
import { load } from "js-yaml";
import { join } from "path";

const PROJECT_ROOT = join(import.meta.dirname, "..", "..", "..", "..");

export function loadConfig() {
  const configPath = join(PROJECT_ROOT, "config.yaml");
  if (existsSync(configPath)) {
    const configData = readFileSync(configPath, "utf-8");
    return load(configData) as any;
  }
  return {};
}

export function getBaseUrl(): string {
  const config = loadConfig();
  return config.settings?.noctua?.base_url || "/";
}

export function getWebsiteTitle(): string {
  const config = loadConfig();
  return config.settings?.noctua?.title || "Noctua";
}

export function getWebsiteDescription(): string {
  const config = loadConfig();
  return config.settings?.noctua?.description || "An RSS feed reader.";
}

export function getArticleFetchLimit(): number {
  const config = loadConfig();
  const value = config.settings?.noctua?.article_fetch_limit;
  const parsed = Number(value);

  if (Number.isInteger(parsed) && parsed > 0) {
    return parsed;
  }

  return 300;
}

export function getStatisticsWeeks(): number {
  const config = loadConfig();
  const value = config.settings?.noctua?.statistics_weeks;
  const parsed = Number(value);

  if (Number.isInteger(parsed) && parsed > 0) {
    return parsed;
  }

  return 8;
}
