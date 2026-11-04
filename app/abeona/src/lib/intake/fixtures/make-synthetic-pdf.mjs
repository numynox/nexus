#!/usr/bin/env node
// Writes synthetic-ticket.pdf: the anonymised ticket text
// (db-ticket-super-sparpreis-europa-return.txt) drawn as a text PDF, so the
// extraction tests (pdf.js on the web, PdfBox on Android) have a PDF to read
// without committing anyone's real ticket. Columns are placed where the
// `pdftotext -layout` text has them, in a proportional font (Helvetica), so
// the gaps between them are uneven the way a real DB PDF's are.
//
// It also carries a made-up barcode, embedded the way DB's tickets embed their
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
const text = readFileSync(join(here, "db-ticket-super-sparpreis-europa-return.txt"), "utf8").replace(/\f/g, "");

const FONT_SIZE = 6;
const CHAR = 3.4; // points per column of the layout text
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

const ops = ["BT", `/F1 ${FONT_SIZE} Tf`];
text.split("\n").forEach((line, row) => {
  // Segments are what a run of two or more spaces separates: the columns.
  const re = /\S+(?: \S+)*/g;
  let m;
  while ((m = re.exec(line))) {
    const x = LEFT + m.index * CHAR;
    const y = TOP - row * LEADING;
    ops.push(`1 0 0 1 ${x.toFixed(2)} ${y.toFixed(2)} Tm ${pdfString(m[0])} Tj`);
  }
});
ops.push("ET");
// The barcode, top right, where DB prints it.
ops.push("q 150 0 0 150 420 650 cm /Im1 Do Q");
const content = ops.join("\n");

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
const code = codeBits();

const objects = [
  "<< /Type /Catalog /Pages 2 0 R >>",
  "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
  "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> /XObject << /Im1 7 0 R >> >> /Contents 5 0 R >>",
  "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
  `<< /Length ${Buffer.byteLength(content, "latin1")} >>\nstream\n${content}\nendstream`,
  "<< /Producer (Nexus Abeona synthetic fixture) /Title (Synthetic DB ticket, anonymised) >>",
  `<< /Type /XObject /Subtype /Image /Width ${CODE} /Height ${CODE} /BitsPerComponent 1 /ColorSpace [/Indexed /DeviceGray 1 <00FF>] /Filter /FlateDecode /Length ${code.length} >>\nstream\n${code.toString("latin1")}\nendstream`,
];

let pdf = "%PDF-1.4\n%\xe2\xe3\xcf\xd3\n";
const offsets = [];
objects.forEach((body, i) => {
  offsets.push(Buffer.byteLength(pdf, "latin1"));
  pdf += `${i + 1} 0 obj\n${body}\nendobj\n`;
});
const xref = Buffer.byteLength(pdf, "latin1");
pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
for (const o of offsets) pdf += `${String(o).padStart(10, "0")} 00000 n \n`;
pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R /Info 6 0 R >>\nstartxref\n${xref}\n%%EOF\n`;

writeFileSync(join(here, "synthetic-ticket.pdf"), Buffer.from(pdf, "latin1"));
console.log("wrote synthetic-ticket.pdf");
