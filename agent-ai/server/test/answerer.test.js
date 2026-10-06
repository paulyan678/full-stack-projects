import assert from "node:assert/strict";
import test from "node:test";
import { createAnswerer } from "../src/answerer.js";

test("OpenAI boundary sends bounded page evidence and uses the requested model", async () => {
  const requests = [];
  const answerer = createAnswerer({ model: "test-model", client: { responses: {
    async create(request) { requests.push(request); return { output_text: "  Policy ends after 30 days. [p. 2]  " }; },
  } } });
  const answer = await answerer.answer({ question: "Refund?", chunks: [{ page: 2, text: "Thirty-day refund policy." }] });
  assert.equal(answer, "Policy ends after 30 days. [p. 2]");
  assert.equal(requests[0].model, "test-model");
  assert.match(requests[0].input, /\[Page 2\] Thirty-day refund policy/);
  assert.match(requests[0].instructions, /untrusted reference text/);
  assert.equal(await answerer.answer({ question: "Missing?", chunks: [] }), "I couldn't find that information in the uploaded document.");
  assert.equal(requests.length, 1);
});

test("provider failures propagate and empty output has an explicit fallback", async () => {
  const failure = new Error("provider unavailable");
  const answerer = createAnswerer({ client: { responses: { async create() { throw failure; } } } });
  await assert.rejects(answerer.answer({ question: "Q", chunks: [{ page: 1, text: "Text" }] }), failure);
  const empty = createAnswerer({ client: { responses: { async create() { return {}; } } } });
  assert.equal(await empty.answer({ question: "Q", chunks: [{ page: 1, text: "Text" }] }), "I couldn't produce an answer.");
});

test("web summarization transmits source URLs and never calls a provider for zero results", async () => {
  let request;
  const answerer = createAnswerer({ client: { responses: { async create(value) { request = value; return { output_text: "Web summary" }; } } } });
  assert.equal(await answerer.summarizeWeb({ question: "Q", results: [] }), "No web results were found.");
  assert.equal(request, undefined);
  assert.equal(await answerer.summarizeWeb({ question: "Q", results: [{ title: "Policy", link: "https://example.test/policy", snippet: "Thirty days" }] }), "Web summary");
  assert.match(request.input, /https:\/\/example.test\/policy/);
  assert.match(request.instructions, /untrusted data/);
});
