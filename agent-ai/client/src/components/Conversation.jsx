export function Conversation({ turns, loading }) {
  if (turns.length === 0 && !loading) {
    return (
      <div className="empty-state">
        <span aria-hidden="true">✦</span>
        <p>Ask for a summary, a decision, a definition, or a fact hidden in the document.</p>
      </div>
    );
  }

  return (
    <div className="conversation" aria-live="polite">
      {turns.map((turn) => (
        <article className="turn" key={turn.id}>
          <p className="question-bubble">{turn.question}</p>
          <div className="answer-grid">
            <section className="answer-card document-answer">
              <p className="eyebrow">From your document</p>
              <p>{turn.answer.ragAnswer}</p>
              {turn.answer.sources?.length > 0 && (
                <details>
                  <summary>View {turn.answer.sources.length} source passages</summary>
                  <ul className="source-list">
                    {turn.answer.sources.map((source, index) => (
                      <li key={`${source.page}-${index}`}><strong>Page {source.page}</strong> — {source.excerpt}</li>
                    ))}
                  </ul>
                </details>
              )}
            </section>
            {turn.answer.mcpAnswer && (
              <section className="answer-card web-answer">
                <p className="eyebrow">From MCP web search</p>
                <p>{turn.answer.mcpAnswer}</p>
                {turn.answer.webSources?.length > 0 && (
                  <ul className="web-links">
                    {turn.answer.webSources.map((source) => (
                      <li key={source.link}><a href={source.link} target="_blank" rel="noreferrer">{source.title}</a></li>
                    ))}
                  </ul>
                )}
              </section>
            )}
          </div>
        </article>
      ))}
      {loading && <div className="thinking"><span /><span /><span /><em>Reading and reasoning</em></div>}
    </div>
  );
}
