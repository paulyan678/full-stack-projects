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

async function invokeSearch(fetchImpl) {
  const { InMemoryTransport } = await import("@modelcontextprotocol/sdk/inMemory.js");
  const { Client } = await import("@modelcontextprotocol/sdk/client/index.js");
  const { createSearchServer } = await import("../src/mcp/search-server.js");
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const server = createSearchServer({ apiKey: "synthetic-key", fetchImpl });
  const client = new Client({ name: "contract-test", version: "1" });
  try {
    await server.connect(serverTransport);
    await client.connect(clientTransport);
    return await client.callTool({ name: "search_web", arguments: { query: "public synthetic policy", num: 2 } });
  } finally {
    await client.close();
    await server.close();
  }
}

test("MCP successful HTTP contract bounds and normalizes provider results", async () => {
  const result = await invokeSearch(async (url, options) => {
    assert.equal(url.origin, "https://serpapi.com");
    assert.equal(url.searchParams.get("q"), "public synthetic policy");
    assert.equal(url.searchParams.get("num"), "2");
    assert.ok(options.signal instanceof AbortSignal);
    return Response.json({ organic_results: [
      { title: "Policy", link: "https://example.test/policy", snippet: "Synthetic result" },
      { title: "Unsafe", link: "javascript:alert(1)" },
    ] });
  });
  assert.equal(result.isError, undefined);
  assert.deepEqual(JSON.parse(result.content[0].text).results, [
    { title: "Policy", link: "https://example.test/policy", snippet: "Synthetic result" },
  ]);
});

test("MCP upstream failure is an explicit tool error rather than a fabricated result", async () => {
  const result = await invokeSearch(async () => new Response("unavailable", { status: 503 }));
  assert.equal(result.isError, true);
  assert.match(JSON.parse(result.content[0].text).error, /HTTP 503/);
});
