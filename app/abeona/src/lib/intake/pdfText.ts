/**
 * A PDF's text layer as lines, on the device: pdf.js reads the text items, and
 * they are laid out roughly the way `pdftotext -layout` would — items grouped
 * into lines by their baseline, columns kept apart by runs of spaces. That is
 * close enough for the parsers, which never rely on exact spacing.
 *
 * Android does the same with PdfBox (domain/intake/PdfText.kt).
 */

/** One pdf.js text item, reduced to what the layout needs. */
export interface TextItem {
  str: string;
  /** Baseline origin, in PDF units (y grows upwards). */
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Lines of one page, top to bottom; a blank line where the gap is larger than usual. */
export function layoutText(items: TextItem[]): string {
  const usable = items.filter((i) => i.str.length > 0 && i.str.trim().length > 0);
  if (usable.length === 0) return "";

  // The width of one character column: the median over the items.
  const widths = usable.map((i) => i.width / i.str.length).filter((w) => w > 0).sort((a, b) => a - b);
  const charWidth = widths.length > 0 ? widths[Math.floor(widths.length / 2)] : 5;

  const sorted = [...usable].sort((a, b) => b.y - a.y || a.x - b.x);
  const rows: { y: number; height: number; items: TextItem[] }[] = [];
  for (const item of sorted) {
    const row = rows[rows.length - 1];
    const tolerance = Math.max(2, (row?.height ?? item.height) * 0.4);
    if (row && Math.abs(row.y - item.y) <= tolerance) row.items.push(item);
    else rows.push({ y: item.y, height: item.height || 10, items: [item] });
  }

  const gaps = rows.slice(1).map((r, i) => rows[i].y - r.y).sort((a, b) => a - b);
  const leading = gaps.length > 0 ? gaps[Math.floor(gaps.length / 2)] : 12;

  const out: string[] = [];
  rows.forEach((row, index) => {
    if (index > 0 && rows[index - 1].y - row.y > leading * 1.6) out.push("");
    const cells = row.items.sort((a, b) => a.x - b.x);
    let line = "";
    let end: number | null = null;
    for (const cell of cells) {
      if (end !== null) {
        const gap = cell.x - end;
        if (gap > charWidth * 1.5) line += " ".repeat(Math.max(2, Math.round(gap / charWidth)));
        else if (gap > charWidth * 0.15 && !line.endsWith(" ") && !cell.str.startsWith(" ")) line += " ";
      }
      line += cell.str;
      end = cell.x + cell.width;
    }
    out.push(line.replace(/\s+$/, ""));
  });
  return out.join("\n");
}

/** The pdf.js pieces this module uses, so tests can hand in the Node build. */
export interface PdfJsLike {
  getDocument(src: { data: Uint8Array }): { promise: Promise<PdfDocumentLike>; destroy(): Promise<void> };
}
interface PdfDocumentLike {
  numPages: number;
  getPage(n: number): Promise<{
    getTextContent(): Promise<{ items: unknown[] }>;
  }>;
}

/** The text of every page, pages separated by a form feed (as pdftotext does). */
export async function pdfText(data: Uint8Array, pdfjs: PdfJsLike): Promise<string> {
  const task = pdfjs.getDocument({ data });
  try {
    const doc = await task.promise;
    const pages: string[] = [];
    for (let n = 1; n <= doc.numPages; n++) {
      const page = await doc.getPage(n);
      const content = await page.getTextContent();
      const items: TextItem[] = [];
      for (const raw of content.items) {
        const item = raw as { str?: string; transform?: number[]; width?: number; height?: number };
        if (typeof item.str !== "string" || !Array.isArray(item.transform)) continue;
        items.push({
          str: item.str,
          x: item.transform[4],
          y: item.transform[5],
          width: item.width ?? 0,
          height: item.height ?? Math.abs(item.transform[3]),
        });
      }
      pages.push(layoutText(items));
    }
    return pages.join("\n\f");
  } finally {
    await task.destroy();
  }
}

/** In the browser: pdf.js with its worker, loaded on first use (as the ticket viewer does). */
export async function browserPdfText(file: Blob): Promise<string> {
  const pdfjs = await import("pdfjs-dist");
  const worker = await import("pdfjs-dist/build/pdf.worker.min.mjs?url");
  pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
  return pdfText(new Uint8Array(await file.arrayBuffer()), pdfjs as unknown as PdfJsLike);
}
