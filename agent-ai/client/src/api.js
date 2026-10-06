const API_URL = (import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? "http://localhost:5001" : "")).replace(/\/$/, "");

async function parseResponse(response) {
  if (response.ok) return response.status === 204 ? null : response.json();
  const payload = await response.json().catch(() => ({}));
  throw new Error(payload.error || `Request failed with status ${response.status}`);
}

export async function uploadDocument(file, { signal } = {}) {
  const body = new FormData();
  body.append("file", file);
  return parseResponse(await fetch(`${API_URL}/api/documents`, { method: "POST", body, signal }));
}

export async function askDocument({ documentId, question, includeWeb }, { signal } = {}) {
  return parseResponse(
    await fetch(`${API_URL}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentId, question, includeWeb }),
      signal,
    })
  );
}

export async function deleteDocument(documentId) {
  return parseResponse(await fetch(`${API_URL}/api/documents/${documentId}`, { method: "DELETE" }));
}
