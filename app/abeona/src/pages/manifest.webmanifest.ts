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
  const themeColor = "#1f6f5c";

  const manifest = {
    name,
    short_name: name,
    description,
    start_url: base || "/",
    scope: base || "/",
    display: "standalone",
    background_color: "#f7f8f5",
    theme_color: themeColor,
    // "Share" from DB Navigator, or a ticket PDF from mail or files, opens the
    // intake on the search page. The service worker (public/sw.js) receives
    // the POST; Chrome offers this only once the app is installed.
    share_target: {
      action: `${base}/share-target`,
      method: "POST",
      enctype: "multipart/form-data",
      params: {
        title: "title",
        text: "text",
        url: "url",
        files: [{ name: "ticket", accept: ["application/pdf", ".pdf"] }],
      },
    },
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
