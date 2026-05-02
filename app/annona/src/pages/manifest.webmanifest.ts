import {
  getBaseUrl,
  getWebsiteDescription,
  getWebsiteTitle,
} from "../lib/config";

export async function GET() {
  const rawBase = getBaseUrl();
  const base = rawBase === "/" ? "" : rawBase.replace(/\/$/, "");

  const name = getWebsiteTitle();
  const description = getWebsiteDescription();
  const themeColor = "#242933";

  const manifest = {
    name,
    short_name: name,
    description,
    start_url: base || "/",
    scope: base || "/",
    display: "standalone",
    background_color: "#242933",
    theme_color: themeColor,
    icons: [
      {
        src: `${base}/android-chrome-192x192.png`,
        sizes: "192x192",
        type: "image/png",
      },
      {
        src: `${base}/android-chrome-512x512.png`,
        sizes: "512x512",
        type: "image/png",
      },
    ],
  };

  return new Response(JSON.stringify(manifest), {
    headers: {
      "Content-Type": "application/manifest+json",
      "Cache-Control": "public, max-age=0, must-revalidate",
    },
  });
}
