import cors from "cors";
import express from "express";
import multer from "multer";
import path from "node:path";
import { rankChunks } from "./retrieval.js";

const httpError = (status, message) => Object.assign(new Error(message), { status });

function securityHeaders(_request, response, next) {
  response.set({
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), geolocation=()",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
  });
  next();
}

function createRateLimiter({ limit = 60, windowMs = 60_000, now = () => Date.now() } = {}) {
  const buckets = new Map();
  let cleanupAt = now() + windowMs;
  return (request, response, next) => {
    const timestamp = now();
    if (timestamp >= cleanupAt) {
      for (const [key, bucket] of buckets) {
        if (timestamp - bucket.startedAt >= windowMs) buckets.delete(key);
      }
      cleanupAt = timestamp + windowMs;
    }
    const key = request.ip || "local";
    const current = buckets.get(key);
    if (!current || timestamp - current.startedAt >= windowMs) {
      buckets.set(key, { count: 1, startedAt: timestamp });
      return next();
    }
    current.count += 1;
    if (current.count > limit) {
      response.set("Retry-After", String(Math.max(1, Math.ceil((windowMs - (timestamp - current.startedAt)) / 1000))));
      return response.status(429).json({ error: "Too many requests. Try again shortly." });
    }
    next();
  };
}

export function createApp({ config, store, parsePdf, answerer, webSearch }) {
  const app = express();
  app.disable("x-powered-by");
  app.set("trust proxy", config.trustProxy ? 1 : false);
  app.use(securityHeaders);
  app.use(
    cors({
      origin(origin, callback) {
        if (!origin || config.clientOrigins.includes(origin)) return callback(null, true);
        callback(httpError(403, "Origin is not allowed."));
      },
      methods: ["GET", "POST", "DELETE"],
    })
  );
  app.use(express.json({ limit: "64kb" }));

  const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: config.maxUploadBytes, files: 1 },
    fileFilter(_request, file, callback) {
      const looksLikePdf = file.mimetype === "application/pdf" && file.originalname.toLowerCase().endsWith(".pdf");
      callback(looksLikePdf ? null : httpError(415, "Only PDF files are accepted."), looksLikePdf);
    },
  });

  app.get("/api/health", (_request, response) => {
    response.json({ status: "ok", aiMode: answerer.mode, webSearch: webSearch.enabled });
  });

  const uploadHandler = async (request, response) => {
    if (!request.file) throw httpError(400, "Attach a PDF in the 'file' field.");
    if (request.file.buffer.subarray(0, 5).toString("ascii") !== "%PDF-") {
      throw httpError(415, "The uploaded file is not a valid PDF.");
    }
    let pages;
    try {
      pages = await parsePdf(request.file.buffer);
    } catch (error) {
      if (Number.isInteger(error.status)) throw error;
      throw httpError(422, "The PDF could not be read or is malformed.");
    }
    const filename = path.basename(request.file.originalname)
      .replace(/[\u0000-\u001f\u007f]/g, "")
      .slice(0, 200) || "document.pdf";
    const document = store.put({ filename, pages });
    response.status(201).json({
      documentId: document.id,
      filename: document.filename,
      pageCount: document.pageCount,
      chunkCount: document.chunks.length,
      expiresInMs: store.ttlMs,
    });
  };

  const uploadLimiter = createRateLimiter({ limit: 20, windowMs: 60_000 });
  app.post("/api/documents", uploadLimiter, upload.single("file"), uploadHandler);
  app.post("/upload", uploadLimiter, upload.single("file"), uploadHandler);

  app.delete("/api/documents/:documentId", (request, response) => {
    if (!store.delete(request.params.documentId)) throw httpError(404, "Document not found or expired.");
    response.status(204).end();
  });

  const chatLimiter = createRateLimiter();
  const chat = async ({ documentId, question, includeWeb }) => {
    if (typeof documentId !== "string" || !documentId) throw httpError(400, "documentId is required.");
    if (typeof question !== "string" || !question.trim()) throw httpError(400, "question is required.");
    if (question.length > 2_000) throw httpError(400, "question must be 2,000 characters or fewer.");
    if (typeof includeWeb !== "boolean") throw httpError(400, "includeWeb must be a boolean.");
    const document = store.get(documentId);
    if (!document) throw httpError(404, "Document not found or expired.");

    const chunks = rankChunks(document.chunks, question.trim(), 4);
    const ragAnswer = await answerer.answer({ question: question.trim(), chunks });
    let mcpAnswer = null;
    let webSources = [];
    if (includeWeb) {
      const search = await webSearch.search(question.trim());
      webSources = search.results;
      mcpAnswer = search.unavailableReason
        ? `Web search unavailable: ${search.unavailableReason}`
        : await answerer.summarizeWeb({ question: question.trim(), results: search.results });
    }
    return {
      ragAnswer,
      mcpAnswer,
      aiMode: answerer.mode,
      sources: chunks.map((chunk) => ({
        page: chunk.page,
        score: Number(chunk.score.toFixed(3)),
        excerpt: chunk.text.slice(0, 240),
      })),
      webSources,
    };
  };

  app.post("/api/chat", chatLimiter, async (request, response) => {
    response.json(await chat({ ...request.body, includeWeb: request.body?.includeWeb ?? false }));
  });

  app.get("/chat", chatLimiter, async (request, response) => {
    const includeWeb = request.query.includeWeb == null
      ? true
      : request.query.includeWeb === "true"
        ? true
        : request.query.includeWeb === "false"
          ? false
          : request.query.includeWeb;
    response.json(
      await chat({
        documentId: request.query.documentId,
        question: request.query.question,
        includeWeb,
      })
    );
  });

  app.use((_request, response) => response.status(404).json({ error: "Route not found." }));
  app.use((error, _request, response, _next) => {
    const status = error instanceof multer.MulterError
      ? error.code === "LIMIT_FILE_SIZE" ? 413 : 400
      : Number.isInteger(error.status) ? error.status : 500;
    const message = status >= 500 ? "Unexpected server error." : error.message;
    if (status >= 500) console.error(error);
    response.status(status).json({ error: message });
  });

  return app;
}
