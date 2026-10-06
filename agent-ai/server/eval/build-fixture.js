import { readFile, writeFile } from "node:fs/promises";
import { PDFDocument, StandardFonts } from "pdf-lib";
const guide = JSON.parse(await readFile(new URL("fixtures/guide.json", import.meta.url)));
const pdf = await PDFDocument.create();
pdf.setTitle(guide.title);
pdf.setAuthor("Full-Stack Application Suite contributors");
pdf.setCreationDate(new Date("2026-10-06T00:00:00Z"));
pdf.setModificationDate(new Date("2026-10-06T00:00:00Z"));
const font = await pdf.embedFont(StandardFonts.Helvetica);
for (const [index, text] of guide.pages.entries()) {
  const page = pdf.addPage([612, 792]);
  page.drawText(`Harbor Library - page ${index + 1}`, { x: 48, y: 744, size: 16, font });
  let y = 700, line = "";
  for (const word of text.split(" ")) {
    const next = `${line} ${word}`.trim();
    if (font.widthOfTextAtSize(next, 12) > 510) {
      page.drawText(line, { x: 48, y, size: 12, font }); y -= 20; line = word;
    } else line = next;
  }
  page.drawText(line, { x: 48, y, size: 12, font });
  page.drawText("Fictional evaluation fixture. CC0-1.0.", { x: 48, y: 48, size: 10, font });
}
await writeFile(new URL("fixtures/harbor-guide.pdf", import.meta.url), await pdf.save());
