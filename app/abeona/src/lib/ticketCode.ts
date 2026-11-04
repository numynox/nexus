/**
 * The ticket's barcode (a DB ticket's Aztec code), taken out of the PDF as the
 * image it is embedded as, so it can be shown alone and large. In the browser
 * only. Android does the same with PdfBox (ui/wallet/TicketCode.kt).
 *
 * The code is the largest roughly square raster image on the first pages; a
 * logo or a photo is not square, and a small icon is too small. A ticket that
 * draws its code as vector shapes yields null, and the PDF is shown instead.
 */
import type { PDFDocumentProxy, PDFPageProxy } from "pdfjs-dist";

const MIN_SIDE = 80;
const PAGES = 2;

/** What pdf.js hands out for a decoded image (canvas.js reads the same). */
export interface PdfImage {
  width: number;
  height: number;
  bitmap?: ImageBitmap;
  data?: Uint8Array | Uint8ClampedArray;
  /** pdf.js ImageKind: 1 = 1 bit grey, 2 = RGB, 3 = RGBA. */
  kind?: number;
}

function squareEnough(width: number, height: number): boolean {
  return Math.abs(width - height) <= Math.max(width, height) * 0.15;
}

/** pdf.js resolves an image when the page's operator list is built; wait for it, briefly. */
function image(page: PDFPageProxy, id: string): Promise<PdfImage | null> {
  const store = id.startsWith("g_") ? page.commonObjs : page.objs;
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(null), 2000);
    try {
      store.get(id, (value: PdfImage) => {
        clearTimeout(timer);
        resolve(value ?? null);
      });
    } catch {
      clearTimeout(timer);
      resolve(null);
    }
  });
}

function toCanvas(img: PdfImage): HTMLCanvasElement | null {
  const canvas = document.createElement("canvas");
  canvas.width = img.width;
  canvas.height = img.height;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  if (img.bitmap) {
    ctx.drawImage(img.bitmap, 0, 0);
    return canvas;
  }
  const src = img.data;
  if (!src) return null;
  const out = ctx.createImageData(img.width, img.height);
  const dest = out.data;
  const pixels = img.width * img.height;
  if (img.kind === 3) {
    dest.set(src.subarray(0, pixels * 4));
  } else if (img.kind === 2) {
    for (let i = 0; i < pixels; i++) {
      dest[i * 4] = src[i * 3];
      dest[i * 4 + 1] = src[i * 3 + 1];
      dest[i * 4 + 2] = src[i * 3 + 2];
      dest[i * 4 + 3] = 255;
    }
  } else if (img.kind === 1) {
    // Rows padded to whole bytes; a set bit is white.
    const rowBytes = Math.ceil(img.width / 8);
    for (let y = 0; y < img.height; y++) {
      for (let x = 0; x < img.width; x++) {
        const on = (src[y * rowBytes + (x >> 3)] >> (7 - (x & 7))) & 1;
        const i = (y * img.width + x) * 4;
        dest[i] = dest[i + 1] = dest[i + 2] = on ? 255 : 0;
        dest[i + 3] = 255;
      }
    }
  } else {
    return null;
  }
  ctx.putImageData(out, 0, 0);
  return canvas;
}

/** The PDF's barcode as pdf.js decoded it, or null when it embeds none. Separate from drawing, so Node can test it. */
export async function findTicketCode(doc: PDFDocumentProxy, ops: { paintImageXObject: number }): Promise<PdfImage | null> {
  const found: PdfImage[] = [];
  for (let n = 1; n <= Math.min(doc.numPages, PAGES); n++) {
    const page = await doc.getPage(n);
    const list = await page.getOperatorList();
    for (let i = 0; i < list.fnArray.length; i++) {
      if (list.fnArray[i] !== ops.paintImageXObject) continue;
      const id = list.argsArray[i]?.[0];
      if (typeof id !== "string") continue;
      const img = await image(page, id);
      if (img && img.width >= MIN_SIDE && img.height >= MIN_SIDE && squareEnough(img.width, img.height)) found.push(img);
    }
  }
  // Largest first; the first that is neither blank nor filled.
  found.sort((a, b) => b.width * b.height - a.width * a.height);
  return found.find((img) => darkShare(img) === null || (darkShare(img)! >= 0.2 && darkShare(img)! <= 0.8)) ?? null;
}

/** How much of the image is dark, from its raw data; null when pdf.js handed out a bitmap instead. */
export function darkShare(img: PdfImage): number | null {
  const src = img.data;
  if (!src) return null;
  const pixels = img.width * img.height;
  let dark = 0;
  for (let p = 0; p < pixels; p++) {
    if (img.kind === 1) {
      const rowBytes = Math.ceil(img.width / 8);
      const x = p % img.width;
      const y = Math.floor(p / img.width);
      if (((src[y * rowBytes + (x >> 3)] >> (7 - (x & 7))) & 1) === 0) dark++;
    } else {
      const step = img.kind === 3 ? 4 : 3;
      if (src[p * step] + src[p * step + 1] + src[p * step + 2] < 3 * 128) dark++;
    }
  }
  return dark / pixels;
}

/** The barcode as a PNG data URL, or null when the PDF embeds none. */
export async function extractTicketCode(doc: PDFDocumentProxy, ops: { paintImageXObject: number }): Promise<string | null> {
  const best = await findTicketCode(doc, ops);
  return best ? (toCanvas(best)?.toDataURL("image/png") ?? null) : null;
}
