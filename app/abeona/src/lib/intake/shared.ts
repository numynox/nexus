/**
 * What was shared to the installed web app (the Web Share Target in the
 * manifest). The service worker (public/sw.js) receives the POST, keeps the
 * text and file in its cache under SHARE_TEXT / SHARE_FILE and opens
 * `search?intake=shared`; the page takes them out here, once.
 */
import { getBaseUrl } from "../config";

/** Keep in step with public/sw.js. */
const CACHE = "abeona-v1";
const SHARE_TEXT = "share/text";
const SHARE_FILE = "share/file";

export interface SharedIntake {
  text: string | null;
  file: File | null;
}

export async function takeSharedIntake(): Promise<SharedIntake | null> {
  const params = new URLSearchParams(window.location.search);
  if (params.get("intake") !== "shared" || !("caches" in window)) return null;
  // Read once: a reload must not read the same share again.
  params.delete("intake");
  const query = params.toString();
  window.history.replaceState(null, "", `${window.location.pathname}${query ? `?${query}` : ""}`);
  try {
    const cache = await caches.open(CACHE);
    // The worker's scope is the app's base path, where sw.js lives.
    const base = new URL(`${getBaseUrl().replace(/\/$/, "")}/`, window.location.origin);
    const textUrl = new URL(SHARE_TEXT, base).href;
    const fileUrl = new URL(SHARE_FILE, base).href;
    const [textResponse, fileResponse] = await Promise.all([cache.match(textUrl), cache.match(fileUrl)]);
    await Promise.all([cache.delete(textUrl), cache.delete(fileUrl)]);
    let text: string | null = null;
    if (textResponse) {
      const shared = (await textResponse.json()) as { title?: string; text?: string; url?: string };
      // DB Navigator puts the link in the text; some apps pass it separately.
      text = [shared.text, shared.url && !shared.text?.includes(shared.url) ? shared.url : null].filter(Boolean).join("\n") || null;
    }
    let file: File | null = null;
    if (fileResponse) {
      const name = decodeURIComponent(fileResponse.headers.get("x-file-name") ?? "ticket.pdf");
      file = new File([await fileResponse.blob()], name, { type: fileResponse.headers.get("content-type") ?? "application/pdf" });
    }
    return text || file ? { text, file } : null;
  } catch {
    return null;
  }
}
