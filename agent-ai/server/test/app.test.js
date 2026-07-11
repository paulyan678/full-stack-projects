import assert from "node:assert/strict";
import test from "node:test";
import request from "supertest";
import { createApp } from "../src/app.js";
import { DocumentStore } from "../src/document-store.js";

const config = {
  clientOrigins: ["http://localhost:5173"],
  maxUploadBytes: 1024,
  trustProxy: false,
};

function fixture({ parsePdf: parseOverride } = {}) {
  const store = new DocumentStore({ ttlMs: 5_000 });
  const answerer = {
    mode: "test",
    async answer({ chunks }) { return `answer from page ${chunks[0]?.page ?? "none"}`; },
    async summarizeWeb({ results }) { return `web ${results.length}`; },
  };
  const webSearch = {
    enabled: true,
    async search() { return { results: [{ title: "Result", link: "https://example.test", snippet: "Snippet" }] }; },
  };
  const parsePdf = parseOverride ?? (async () => [
    { page: 1, text: "General introduction." },
    { page: 2, text: "Refund requests are accepted for thirty days after purchase." },
  ]);
  return { app: createApp({ config, store, parsePdf, answerer, webSearch }), store };
}

test("health reports provider capabilities", async () => {
  const { app } = fixture();
  assert.equal(app.get("trust proxy"), false);
  const response = await request(app).get("/api/health").expect(200);
  assert.deepEqual(response.body, { status: "ok", aiMode: "test", webSearch: true });
});

test("upload, document chat, optional web search, and delete form a complete flow", async () => {
  const { app } = fixture();
  const upload = await request(app)
    .post("/api/documents")
    .attach("file", Buffer.from("%PDF-fake"), { filename: "guide.pdf", contentType: "application/pdf" })
    .expect(201);
  assert.equal(upload.body.pageCount, 2);

  const chat = await request(app)
    .post("/api/chat")
    .send({ documentId: upload.body.documentId, question: "What is the refund window?", includeWeb: true })
    .expect(200);
  assert.equal(chat.body.ragAnswer, "answer from page 2");
  assert.equal(chat.body.mcpAnswer, "web 1");
  assert.equal(chat.body.sources[0].page, 2);
  assert.equal(chat.body.webSources[0].link, "https://example.test");

  await request(app).delete(`/api/documents/${upload.body.documentId}`).expect(204);
  await request(app)
    .post("/api/chat")
    .send({ documentId: upload.body.documentId, question: "Still there?" })
    .expect(404);
});

test("upload rejects missing, mislabeled, and spoofed files", async () => {
  const { app } = fixture();
  await request(app).post("/api/documents").expect(400);
  await request(app)
    .post("/api/documents")
    .attach("file", Buffer.from("hello"), { filename: "guide.txt", contentType: "text/plain" })
    .expect(415);
  await request(app)
    .post("/api/documents")
    .attach("file", Buffer.from("not a pdf"), { filename: "guide.pdf", contentType: "application/pdf" })
    .expect(415);
});

test("malformed PDFs return a client error instead of an internal error", async () => {
  const { app } = fixture({ parsePdf: async () => { throw new Error("parser exploded"); } });
  const response = await request(app)
    .post("/api/documents")
    .attach("file", Buffer.from("%PDF-broken"), { filename: "broken.pdf", contentType: "application/pdf" })
    .expect(422);
  assert.match(response.body.error, /malformed|could not be read/i);
});

test("chat validates required and bounded input", async () => {
  const { app } = fixture();
  await request(app).post("/api/chat").send({}).expect(400);
  await request(app).post("/api/chat").send({ documentId: "missing", question: "x".repeat(2_001) }).expect(400);
  await request(app).post("/api/chat").send({ documentId: "missing", question: "valid question", includeWeb: "false" }).expect(400);
  await request(app).get("/chat").query({ documentId: "missing", question: "valid question", includeWeb: "maybe" }).expect(400);
});

test("proxy headers are trusted only when explicitly configured", () => {
  assert.equal(fixture().app.get("trust proxy"), false);
  const trusted = createApp({
    ...fixture(),
    config: { ...config, trustProxy: true },
    parsePdf: async () => [{ page: 1, text: "text" }],
    answerer: { mode: "test" },
    webSearch: { enabled: false },
  });
  assert.equal(trusted.get("trust proxy"), 1);
});

test("CORS blocks untrusted browser origins", async () => {
  const response = await request(fixture().app).get("/api/health").set("Origin", "https://evil.example").expect(403);
  assert.match(response.body.error, /origin/i);
});
