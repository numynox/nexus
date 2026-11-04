// Run with `npm run test:abeona` (node --test; Node strips the types).
// The synthetic ticket's barcode is embedded the way DB's tickets embed their
// Aztec code, a 1-bit "[/Indexed /DeviceGray 1 <00FF>]" image
// (intake/fixtures/make-synthetic-pdf.mjs). Android's TicketCodeTest reads the same.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { darkShare, findTicketCode } from "./ticketCode.ts";

test("the synthetic ticket's indexed 1-bit barcode is found and decodes to dark and light", async () => {
  const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
  const data = new Uint8Array(readFileSync(new URL("./intake/fixtures/synthetic-ticket.pdf", import.meta.url)));
  // Node has no OffscreenCanvas: pdf.js hands out raw pixels, which is what this checks.
  const doc = await pdfjs.getDocument({ data, isOffscreenCanvasSupported: false }).promise;
  const code = await findTicketCode(doc as never, pdfjs.OPS);
  assert.ok(code, "the code is found");
  assert.equal(code.width, 120);
  const share = darkShare(code);
  assert.ok(share !== null && share > 0.2 && share < 0.8, `dark share ${share}`);
});
