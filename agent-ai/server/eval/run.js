import { readFile } from "node:fs/promises";
import { parsePdf } from "../src/pdf.js";
import { chunkPages, rankChunks } from "../src/retrieval.js";
const pages = await parsePdf(await readFile(new URL("fixtures/harbor-guide.pdf", import.meta.url)));
const questions = JSON.parse(await readFile(new URL("fixtures/questions.json", import.meta.url)));
const results = [{ chunkSize: 900, overlap: 120 }, { chunkSize: 180, overlap: 40 }].map((config) => {
  const chunks = chunkPages(pages, config);
  const cases = questions.map(({ id, question, expectedPage }) => {
    const ranked = rankChunks(chunks, question, 4);
    return { id, expectedPage, retrievedPages: ranked.map((chunk) => chunk.page) };
  });
  const answerable = cases.filter((item) => item.expectedPage !== null);
  const absent = cases.filter((item) => item.expectedPage === null);
  return {
    config, chunks: chunks.length,
    pageRecallAt1: answerable.filter((item) => item.retrievedPages[0] === item.expectedPage).length / answerable.length,
    pageRecallAt4: answerable.filter((item) => item.retrievedPages.includes(item.expectedPage)).length / answerable.length,
    noMatchAccuracy: absent.filter((item) => item.retrievedPages.length === 0).length / absent.length,
    cases,
  };
});
console.log(JSON.stringify({ fixture: "harbor-guide.pdf", scope: "synthetic lexical-retrieval smoke evaluation; not model answer quality", results }, null, 2));
if (results[0].pageRecallAt4 < 0.75 || results[0].noMatchAccuracy < 1) process.exitCode = 1;
