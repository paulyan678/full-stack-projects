import "dotenv/config";
import { createAnswerer } from "./answerer.js";
import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { DocumentStore } from "./document-store.js";
import { createMcpSearch } from "./mcp/search-client.js";
import { parsePdf } from "./pdf.js";

const config = loadConfig();
const store = new DocumentStore({ ttlMs: config.documentTtlMs });
const answerer = createAnswerer({ apiKey: config.openAiApiKey, model: config.openAiModel });
const webSearch = createMcpSearch({ enabled: Boolean(config.serpApiKey) });
const app = createApp({ config, store, parsePdf, answerer, webSearch });
const server = app.listen(config.port, () => {
  console.log(`Agent AI server listening on http://localhost:${config.port}`);
  console.log(`AI mode: ${answerer.mode}; MCP web search: ${webSearch.enabled ? "enabled" : "disabled"}`);
});

const shutdown = async (signal) => {
  console.log(`Received ${signal}; shutting down.`);
  server.close();
  await webSearch.close();
};

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
