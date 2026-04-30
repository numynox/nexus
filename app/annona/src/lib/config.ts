export function getBaseUrl(): string {
  return import.meta.env.BASE_URL || "/";
}

export function getWebsiteTitle(): string {
  return "Annona";
}

export function getWebsiteDescription(): string {
  return "Grocery expiration tracker.";
}
