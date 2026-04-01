import assert from "node:assert/strict";
import test from "node:test";
import { DocumentStore } from "../src/document-store.js";

test("DocumentStore expires records and supports explicit deletion", () => {
  let now = 100;
  const store = new DocumentStore({ ttlMs: 50, now: () => now });
  const document = store.put({ filename: "guide.pdf", pages: [{ page: 1, text: "Useful guide content." }] });
  assert.equal(store.get(document.id)?.filename, "guide.pdf");
  now = 151;
  assert.equal(store.get(document.id), null);

  const second = store.put({ filename: "second.pdf", pages: [{ page: 1, text: "Another guide." }] });
  assert.equal(store.delete(second.id), true);
  assert.equal(store.get(second.id), null);
});
