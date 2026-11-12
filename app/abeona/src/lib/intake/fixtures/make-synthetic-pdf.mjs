#!/usr/bin/env node
// Writes the synthetic ticket PDFs: the anonymised ticket texts drawn as text
// PDFs, so the extraction tests (pdf.js on the web, PdfBox on Android) have a
// PDF to read without committing anyone's real ticket. Columns are placed
// where the `pdftotext -layout` text has them, in a proportional font
// (Helvetica), so the gaps between them are uneven the way a real PDF's are.
// A form feed in the text starts a new page.
//
//   synthetic-ticket.pdf            ← db-ticket-super-sparpreis-europa-return.txt
//   synthetic-trenitalia-ticket.pdf ← trenitalia-ticket-super-economy-2-passengers.txt
//
// The DB one also carries a made-up barcode, embedded the way DB's tickets embed their
// Aztec code: a 1-bit image in "[/Indexed /DeviceGray 1 <00FF>]", Flate
// compressed. PdfBox-Android draws that as a blank bitmap, so the ticket
// viewer decodes it itself (TicketCode.kt, lib/ticketCode.ts). The pattern is
// an Aztec-like bull's eye in noise, not a code anyone can scan.
//
//   node app/abeona/src/lib/intake/fixtures/make-synthetic-pdf.mjs
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { deflateSync } from "node:zlib";

const here = dirname(fileURLToPath(import.meta.url));

const LEFT = 12;
const TOP = 810;
const LEADING = 12;

// WinAnsiEncoding: Latin-1 for the upper half, plus the euro sign at 0x80.
function pdfString(s) {
  let out = "";
  for (const ch of s) {
    const code = ch === "€" ? 0x80 : ch.codePointAt(0);
    if (code > 0xff) throw new Error(`Not in WinAnsi: ${ch}`);
    if (ch === "(" || ch === ")" || ch === "\\") out += `\\${ch}`;
    else if (code < 0x20 || code > 0x7e) out += `\\${code.toString(8).padStart(3, "0")}`;
    else out += ch;
  }
  return `(${out})`;
}

const CODE = 120; // pixels a side
function codeBits() {
  const rowBytes = Math.ceil(CODE / 8);
  const bytes = new Uint8Array(rowBytes * CODE).fill(0xff); // 1 = white
  let seed = 12345;
  const random = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  const c = CODE / 2;
  for (let y = 0; y < CODE; y++) {
    for (let x = 0; x < CODE; x++) {
      const ring = Math.max(Math.abs(x - c), Math.abs(y - c));
      // Rings of 4 pixels, dark in the middle, inside a 40-pixel core; noise around.
      const dark = ring < 20 ? Math.floor(ring / 4) % 2 === 0 : random() < 0.5;
      if (dark) bytes[y * rowBytes + (x >> 3)] &= ~(0x80 >> (x & 7));
    }
  }
  return Buffer.from(deflateSync(bytes));
}

/** One page's content stream: the text, and the barcode when given. */
function pageContent(text, { fontSize, char, barcode }) {
  const ops = ["BT", `/F1 ${fontSize} Tf`];
  text.split("\n").forEach((line, row) => {
    // Segments are what a run of two or more spaces separates: the columns.
    const re = /\S+(?: \S+)*/g;
    let m;
    while ((m = re.exec(line))) {
      const x = LEFT + m.index * char;
      const y = TOP - row * LEADING;
      ops.push(`1 0 0 1 ${x.toFixed(2)} ${y.toFixed(2)} Tm ${pdfString(m[0])} Tj`);
    }
  });
  ops.push("ET");
  // The barcode, top right, where DB prints it.
  if (barcode) ops.push("q 150 0 0 150 420 650 cm /Im1 Do Q");
  return ops.join("\n");
}

function writePdf(source, target, options) {
  const pages = readFileSync(join(here, source), "utf8")
    .split("\f")
    .filter((page) => page.trim() !== "")
    .map((page) => pageContent(page.replace(/^\n/, ""), options));
  const code = options.barcode ? codeBits() : null;

  // 1 catalog, 2 pages, 3 font, 4 info, 5 barcode (or an empty dictionary), then a page and its content each.
  const pageObject = (n) => 6 + n * 2;
  const resources = `<< /Font << /F1 3 0 R >>${code ? " /XObject << /Im1 5 0 R >>" : ""} >>`;
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    `<< /Type /Pages /Kids [${pages.map((_, n) => `${pageObject(n)} 0 R`).join(" ")}] /Count ${pages.length} >>`,
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    `<< /Producer (Nexus Abeona synthetic fixture) /Title (${options.title}) >>`,
    code
      ? `<< /Type /XObject /Subtype /Image /Width ${CODE} /Height ${CODE} /BitsPerComponent 1 /ColorSpace [/Indexed /DeviceGray 1 <00FF>] /Filter /FlateDecode /Length ${code.length} >>\nstream\n${code.toString("latin1")}\nendstream`
      : "<< >>",
  ];
  pages.forEach((content, n) => {
    objects.push(
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources ${resources} /Contents ${pageObject(n) + 1} 0 R >>`,
      `<< /Length ${Buffer.byteLength(content, "latin1")} >>\nstream\n${content}\nendstream`,
    );
  });

  let pdf = "%PDF-1.4\n%\xe2\xe3\xcf\xd3\n";
  const offsets = [];
  objects.forEach((body, i) => {
    offsets.push(Buffer.byteLength(pdf, "latin1"));
    pdf += `${i + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = Buffer.byteLength(pdf, "latin1");
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const o of offsets) pdf += `${String(o).padStart(10, "0")} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R /Info 4 0 R >>\nstartxref\n${xref}\n%%EOF\n`;

  writeFileSync(join(here, target), Buffer.from(pdf, "latin1"));
  console.log(`wrote ${target}`);
}

writePdf("db-ticket-super-sparpreis-europa-return.txt", "synthetic-ticket.pdf", {
  fontSize: 6,
  char: 3.4, // points per column of the layout text
  barcode: true,
  title: "Synthetic DB ticket, anonymised",
});
// Trenitalia's lines are wider: a smaller font, so they fit the page.
writePdf("trenitalia-ticket-super-economy-2-passengers.txt", "synthetic-trenitalia-ticket.pdf", {
  fontSize: 5,
  char: 2.9,
  barcode: false,
  title: "Synthetic Trenitalia ticket, anonymised",
});
