import { useEffect, useRef, useState } from "react";
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
  const generation = useRef(0);
  const currentDocument = useRef(null);
  const uploadRequest = useRef(null);
  const chatRequest = useRef(null);

  const cancelRequests = () => {
    generation.current += 1;
    uploadRequest.current?.abort();
    chatRequest.current?.abort();
    uploadRequest.current = null;
    chatRequest.current = null;
  };
  const removeSession = (session) => {
    if (session) void deleteDocument(session.documentId).catch(() => {});
  };

  useEffect(() => () => {
    cancelRequests();
    removeSession(currentDocument.current);
    currentDocument.current = null;
  }, []);

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
    cancelRequests();
    removeSession(currentDocument.current);
    currentDocument.current = null;
    setDocument(null);
    setTurns([]);
    setAsking(false);
    setUploading(true);
    const epoch = generation.current;
    const controller = new AbortController();
    uploadRequest.current = controller;
    try {
      const uploaded = await uploadDocument(file, { signal: controller.signal });
      // Aborting fetch is best effort: a server response can already be in flight.
      if (generation.current !== epoch) {
        removeSession(uploaded);
        return;
      }
      currentDocument.current = uploaded;
      setDocument(uploaded);
    } catch (uploadError) {
      if (generation.current === epoch && uploadError.name !== "AbortError") setError(uploadError.message);
    } finally {
      if (generation.current === epoch) {
        uploadRequest.current = null;
        setUploading(false);
      }
    }
  };

  const clearDocument = () => {
    cancelRequests();
    removeSession(currentDocument.current);
    currentDocument.current = null;
    setDocument(null);
    setTurns([]);
    setUploading(false);
    setAsking(false);
    setError("");
  };

  const handleAsk = async (question) => {
    const session = currentDocument.current;
    if (!session) return;
    chatRequest.current?.abort();
    const controller = new AbortController();
    const epoch = generation.current;
    chatRequest.current = controller;
    const isCurrent = () => generation.current === epoch && chatRequest.current === controller;
    setError("");
    setAsking(true);
    try {
      const answer = await askDocument(
        { documentId: session.documentId, question, includeWeb },
        { signal: controller.signal }
      );
      if (isCurrent()) setTurns((current) => [...current, { id: crypto.randomUUID(), question, answer }]);
    } catch (chatError) {
      if (isCurrent() && chatError.name !== "AbortError") setError(chatError.message);
    } finally {
      if (isCurrent()) {
        chatRequest.current = null;
        setAsking(false);
      }
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
      <ChatComposer disabled={!document} busy={asking} includeWeb={includeWeb} onIncludeWeb={setIncludeWeb} onAsk={handleAsk} lastAnswer={lastAnswer} />
      <footer>Answers are grounded in uploaded text. Verify important decisions against the source pages.</footer>
    </div>
  );
}
