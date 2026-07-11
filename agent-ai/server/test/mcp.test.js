import assert from "node:assert/strict";
import test from "node:test";
import { createMcpSearch } from "../src/mcp/search-client.js";
import { normalizeSearchResults } from "../src/mcp/search-server.js";

test("MCP client and stdio server return a clear configuration error without a SerpAPI key", async () => {
  const original = process.env.SERPAPI_KEY;
  delete process.env.SERPAPI_KEY;
  const search = createMcpSearch({ enabled: true });
  try {
    const result = await search.search("agent interoperability");
    assert.deepEqual(result.results, []);
    assert.match(result.unavailableReason, /SERPAPI_KEY/i);
  } finally {
    await search.close();
    if (original) process.env.SERPAPI_KEY = original;
  }
});

test("web results retain only bounded HTTP(S) links", () => {
  const results = normalizeSearchResults([
    { title: "Safe", link: "https://example.test/guide", snippet: "Useful" },
    { title: "Unsafe", link: "javascript:alert(1)", snippet: "Bad" },
    { title: "Broken", link: "not a URL", snippet: "Bad" },
  ], 5);
  assert.deepEqual(results, [{ title: "Safe", link: "https://example.test/guide", snippet: "Useful" }]);
});
