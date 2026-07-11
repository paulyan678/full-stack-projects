import { useEffect, useMemo, useRef, useState } from "react";

export function ChatComposer({ disabled, busy, includeWeb, onIncludeWeb, onAsk, lastAnswer }) {
  const [question, setQuestion] = useState("");
  const [listening, setListening] = useState(false);
  const recognitionRef = useRef(null);
  const Recognition = useMemo(
    () => globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition,
    []
  );

  useEffect(() => () => recognitionRef.current?.abort(), []);

  const submit = (event) => {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || disabled || busy) return;
    setQuestion("");
    onAsk(trimmed);
  };

  const toggleListening = () => {
    if (!Recognition) return;
    if (listening) {
      recognitionRef.current?.stop();
      return;
    }
    const recognition = new Recognition();
    recognition.lang = "en-US";
    recognition.interimResults = true;
    recognition.onresult = (event) => {
      setQuestion(Array.from(event.results).map((result) => result[0].transcript).join(""));
    };
    recognition.onend = () => setListening(false);
    recognition.onerror = () => setListening(false);
    recognitionRef.current = recognition;
    setListening(true);
    recognition.start();
  };

  const speak = () => {
    if (!lastAnswer || !globalThis.speechSynthesis) return;
    globalThis.speechSynthesis.cancel();
    globalThis.speechSynthesis.speak(new SpeechSynthesisUtterance(lastAnswer));
  };

  return (
    <div className="composer-shell">
      <form className="composer" onSubmit={submit}>
        <label className="visually-hidden" htmlFor="question">Ask about the document</label>
        <textarea
          id="question"
          rows="1"
          maxLength="2000"
          placeholder={disabled ? "Upload a PDF before asking a question" : "Ask anything about this document…"}
          value={question}
          disabled={disabled}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) submit(event);
          }}
        />
        {Recognition && (
          <button className={`icon-button ${listening ? "is-live" : ""}`} type="button" onClick={toggleListening} aria-label={listening ? "Stop dictation" : "Dictate question"}>
            {listening ? "■" : "◉"}
          </button>
        )}
        {lastAnswer && (
          <button className="icon-button" type="button" onClick={speak} aria-label="Read last answer aloud">◖</button>
        )}
        <button className="send-button" type="submit" disabled={disabled || busy || !question.trim()} aria-label="Send question">➜</button>
      </form>
      <label className="web-toggle">
        <input type="checkbox" checked={includeWeb} onChange={(event) => onIncludeWeb(event.target.checked)} />
        <span>Compare with live web results through MCP</span>
      </label>
    </div>
  );
}
