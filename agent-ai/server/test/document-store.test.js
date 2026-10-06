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


test("scheduled cleanup removes idle expired content and close releases resources", () => {
  let now = 0, tick, cancelled = false;
  const timer = { unref() {} };
  const store = new DocumentStore({
    ttlMs: 1000, now: () => now,
    setIntervalFn(callback, interval) { tick = callback; assert.equal(interval, 1000); return timer; },
    clearIntervalFn(handle) { assert.equal(handle, timer); cancelled = true; },
  });
  const record = store.put({ filename: "idle.pdf", pages: [{ page: 1, text: "Synthetic data" }] });
  now = 1000;
  tick();
  // delete checks physical presence without triggering an expiry lookup.
  assert.equal(store.delete(record.id), false);
  const active = store.put({ filename: "active.pdf", pages: [{ page: 1, text: "New data" }] });
  store.close();
  assert.equal(cancelled, true);
  assert.equal(store.delete(active.id), false);
});

test("cleanup cadence is capped at a minute for long-lived sessions", () => {
  const store = new DocumentStore({ ttlMs: 3_600_000 });
  assert.equal(store.cleanupIntervalMs, 60_000);
  store.close();
});
