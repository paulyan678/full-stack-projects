import assert from "node:assert/strict";
import test from "node:test";
import { loadConfig } from "../src/config.js";

test("configuration uses documented safe defaults", () => {
  const config = loadConfig({});
  assert.equal(config.openAiModel, "gpt-5");
  assert.equal(config.trustProxy, false);
});

test("trust proxy configuration is explicit and strict", () => {
  assert.equal(loadConfig({ TRUST_PROXY: "true" }).trustProxy, true);
  assert.throws(() => loadConfig({ TRUST_PROXY: "yes" }), /true.*false/i);
});
