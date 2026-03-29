import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

export function normalizeSearchResults(results, limit) {
  return (Array.isArray(results) ? results : [])
    .map((item) => {
      let link;
      try {
        const parsed = new URL(String(item?.link ?? ""));
        if (!["http:", "https:"].includes(parsed.protocol)) return null;
        link = parsed.toString();
        if (link.length > 2_048) return null;
      } catch {
        return null;
      }
      return {
        title: String(item?.title ?? "Untitled").slice(0, 300),
        link,
        snippet: String(item?.snippet ?? "").slice(0, 1_500),
      };
    })
    .filter(Boolean)
    .slice(0, limit);
}

export function createSearchServer({ apiKey = process.env.SERPAPI_KEY, fetchImpl = fetch } = {}) {
  const server = new McpServer({ name: "agent-ai-search", version: "1.0.0" });
  server.registerTool(
    "search_web",
    {
      description: "Search the public web with SerpAPI and return title, URL, and snippet fields.",
      inputSchema: {
        query: z.string().min(1).max(500),
        num: z.number().int().min(1).max(10).optional(),
      },
    },
    async ({ query, num = 5 }) => {
      if (!apiKey) {
        return {
          isError: true,
          content: [{ type: "text", text: JSON.stringify({ error: "SERPAPI_KEY is not configured." }) }],
        };
      }
      try {
        const url = new URL("https://serpapi.com/search.json");
        url.searchParams.set("engine", "google");
        url.searchParams.set("q", query);
        url.searchParams.set("num", String(num));
        url.searchParams.set("api_key", apiKey);
        const response = await fetchImpl(url, { signal: AbortSignal.timeout(10_000) });
        if (!response.ok) throw new Error(`SerpAPI returned HTTP ${response.status}`);
        const payload = await response.json();
        const results = normalizeSearchResults(payload.organic_results, num);
        return { content: [{ type: "text", text: JSON.stringify({ results }) }] };
      } catch (error) {
        return {
          isError: true,
          content: [{ type: "text", text: JSON.stringify({ error: error.message }) }],
        };
      }
    }
  );
  return server;
}

async function main() {
  const server = createSearchServer();
  await server.connect(new StdioServerTransport());
}

if (process.argv[1] && new URL(import.meta.url).pathname === process.argv[1]) {
  main().catch((error) => {
    console.error("MCP search server failed:", error);
    process.exitCode = 1;
  });
}
