import { useState } from "react";
import { askDocument, deleteDocument, uploadDocument } from "./api.js";
import { ChatComposer } from "./components/ChatComposer.jsx";
import { Conversation } from "./components/Conversation.jsx";
import { DocumentUploader } from "./components/DocumentUploader.jsx";
import "./styles.css";

export default function App() {
  const [document, setDocument] = useState(null);
  const [turns, setTurns] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [asking, setAsking] = useState(false);
  const [includeWeb, setIncludeWeb] = useState(false);
  const [error, setError] = useState("");

  const handleUpload = async (file) => {
    setError("");
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      setError("Choose a PDF file.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError("That PDF is larger than 10 MB.");
      return;
    }
    setUploading(true);
    try {
      setDocument(await uploadDocument(file));
      setTurns([]);
    } catch (uploadError) {
      setError(uploadError.message);
    } finally {
      setUploading(false);
    }
  };

  const clearDocument = async () => {
    const current = document;
    setDocument(null);
    setTurns([]);
    setError("");
    if (current) await deleteDocument(current.documentId).catch(() => {});
  };

  const handleAsk = async (question) => {
    setError("");
    setAsking(true);
    try {
      const answer = await askDocument({ documentId: document.documentId, question, includeWeb });
      setTurns((current) => [...current, { id: crypto.randomUUID(), question, answer }]);
    } catch (chatError) {
      setError(chatError.message);
    } finally {
      setAsking(false);
    }
  };

  const lastAnswer = turns.at(-1)?.answer?.ragAnswer;

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#main" aria-label="Agent AI home">
          <span className="brand-symbol">A</span>
          <span><strong>Agent AI</strong><small>Document companion</small></span>
        </a>
        <span className="status-pill"><i /> Local-first workspace</span>
      </header>

      <main id="main">
        <section className="hero">
          <p className="eyebrow">RAG + Model Context Protocol</p>
          <h1>Your documents,<br /><em>made conversational.</em></h1>
          <p>Upload a PDF, ask precise questions, and compare grounded answers with optional web research.</p>
        </section>

        {error && <div className="error-banner" role="alert">{error}<button type="button" onClick={() => setError("")} aria-label="Dismiss error">×</button></div>}

        <DocumentUploader document={document} busy={uploading} onUpload={handleUpload} onClear={clearDocument} />
        {document && <Conversation turns={turns} loading={asking} />}
      </main>

      <ChatComposer
        disabled={!document}
        busy={asking}
        includeWeb={includeWeb}
        onIncludeWeb={setIncludeWeb}
        onAsk={handleAsk}
        lastAnswer={lastAnswer}
      />
      <footer>Answers are grounded in uploaded text. Verify important decisions against the source pages.</footer>
    </div>
  );
}
