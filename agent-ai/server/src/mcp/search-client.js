import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";

export function createMcpSearch({ enabled = false } = {}) {
  if (!enabled) {
    return {
      enabled: false,
      async search() {
        return { results: [], unavailableReason: "Web search is not configured." };
      },
      async close() {},
    };
  }

  let client;
  let transport;
  let connecting;

  const connect = async () => {
    if (client) return;
    if (connecting) return connecting;
    connecting = (async () => {
      const nextClient = new Client({ name: "agent-ai-server", version: "1.0.0" });
      const nextTransport = new StdioClientTransport({
        command: process.execPath,
        args: [fileURLToPath(new URL("./search-server.js", import.meta.url))],
        env: {
          NODE_ENV: process.env.NODE_ENV ?? "production",
          SERPAPI_KEY: process.env.SERPAPI_KEY ?? "",
        },
        stderr: "pipe",
      });
      try {
        await nextClient.connect(nextTransport);
        client = nextClient;
        transport = nextTransport;
      } catch (error) {
        await nextClient.close().catch(() => {});
        throw error;
      }
    })().finally(() => {
      connecting = null;
    });
    return connecting;
  };

  return {
    enabled: true,
    async search(query) {
      try {
        await connect();
        const response = await client.callTool({ name: "search_web", arguments: { query, num: 5 } });
        const text = response.content?.find((item) => item.type === "text")?.text;
        const payload = text ? JSON.parse(text) : { results: [] };
        if (response.isError || payload.error) throw new Error(payload.error || "Web search failed.");
        return { results: Array.isArray(payload.results) ? payload.results : [] };
      } catch (error) {
        await this.close();
        return { results: [], unavailableReason: error.message };
      }
    },
    async close() {
      if (client) await client.close().catch(() => {});
      client = null;
      transport = null;
    },
  };
}
