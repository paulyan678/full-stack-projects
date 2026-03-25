import { useRef, useState } from "react";

export function DocumentUploader({ document, busy, onUpload, onClear }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);

  const choose = (files) => {
    const file = files?.[0];
    if (file) onUpload(file);
  };

  if (document) {
    return (
      <section className="document-card" aria-label="Current document">
        <div className="file-mark" aria-hidden="true">PDF</div>
        <div>
          <p className="eyebrow">Ready to explore</p>
          <h2>{document.filename}</h2>
          <p>{document.pageCount} pages · {document.chunkCount} searchable passages</p>
        </div>
        <button className="text-button" type="button" onClick={onClear}>Replace</button>
      </section>
    );
  }

  return (
    <section
      className={`drop-zone ${dragging ? "is-dragging" : ""}`}
      onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        choose(event.dataTransfer.files);
      }}
      aria-label="Upload a PDF"
    >
      <input
        ref={inputRef}
        className="visually-hidden"
        aria-label="PDF file"
        type="file"
        accept="application/pdf,.pdf"
        onChange={(event) => choose(event.target.files)}
      />
      <div className="upload-icon" aria-hidden="true">↥</div>
      <p className="eyebrow">Your private workspace</p>
      <h2>Drop a PDF to begin</h2>
      <p>Text is held in memory for one hour and is never committed to the repository.</p>
      <button className="primary-button" type="button" disabled={busy} onClick={() => inputRef.current?.click()}>
        {busy ? "Reading document…" : "Choose PDF"}
      </button>
      <span className="microcopy">PDF only · 10 MB maximum</span>
    </section>
  );
}
