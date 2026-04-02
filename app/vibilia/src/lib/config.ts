export function getBaseUrl(): string {
  return import.meta.env.BASE_URL || "/";
}

export function getWebsiteTitle(): string {
  return "Vibilia";
}

export function getWebsiteDescription(): string {
  return "Fuel tracker for vehicles, prices, and refuel history.";
}
