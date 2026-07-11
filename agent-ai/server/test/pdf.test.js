import assert from "node:assert/strict";
import test from "node:test";
import { PDFDocument, StandardFonts } from "pdf-lib";
import { parsePdf } from "../src/pdf.js";

test("parsePdf extracts page text from a real PDF", async () => {
  const document = await PDFDocument.create();
  const font = await document.embedFont(StandardFonts.Helvetica);
  const page = document.addPage([400, 300]);
  page.drawText("Agent AI test document", { x: 40, y: 240, size: 16, font });
  const pages = await parsePdf(Buffer.from(await document.save()));
  assert.equal(pages.length, 1);
  assert.match(pages[0].text, /Agent AI test document/);
});

test("parsePdf enforces page and extracted-text bounds", async () => {
  const document = await PDFDocument.create();
  const page = document.addPage([400, 300]);
  page.drawText("A bounded document");
  const buffer = Buffer.from(await document.save());
  await assert.rejects(parsePdf(buffer, { maxPages: 0 }), /page processing limit/i);
  await assert.rejects(parsePdf(buffer, { maxCharacters: 5 }), /too much extractable text/i);
});
