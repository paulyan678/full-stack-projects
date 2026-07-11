import assert from "node:assert/strict";
import test from "node:test";
import { chunkPages, extractiveAnswer, rankChunks, tokenize } from "../src/retrieval.js";

test("tokenize normalizes terms and removes common stop words", () => {
  assert.deepEqual(tokenize("What is the Refund-Policy for ORDERS?"), ["refund-policy", "orders"]);
});

test("chunkPages preserves page provenance and overlap-safe bounds", () => {
  const chunks = chunkPages([{ page: 3, text: "alpha ".repeat(300) }], { chunkSize: 120, overlap: 20 });
  assert.ok(chunks.length > 2);
  assert.ok(chunks.every((chunk) => chunk.page === 3 && chunk.text.length <= 120));
});

test("rankChunks favors document passages related to the question", () => {
  const chunks = [
    { id: "1", page: 1, text: "The office opens at nine and serves coffee." },
    { id: "2", page: 4, text: "The refund policy allows returns within thirty days of purchase." },
  ];
  const ranked = rankChunks(chunks, "How long is the refund policy?", 2);
  assert.equal(ranked[0].page, 4);
  assert.match(extractiveAnswer("How long is the refund policy?", ranked), /thirty days/i);
});

test("extractiveAnswer is honest when no passage matches", () => {
  assert.match(extractiveAnswer("unknown", []), /couldn't find/i);
});
