import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "../src/App.jsx";
import { askDocument, deleteDocument, uploadDocument } from "../src/api.js";

vi.mock("../src/api.js", () => ({
  uploadDocument: vi.fn(),
  askDocument: vi.fn(),
  deleteDocument: vi.fn(() => Promise.resolve()),
}));

beforeEach(() => { vi.resetAllMocks(); deleteDocument.mockResolvedValue(null); });

describe("Agent AI application", () => {

  it("starts with an accessible upload and disabled question composer", () => {
    render(<App />);
    expect(screen.getByRole("heading", { name: /your documents/i })).toBeInTheDocument();
    expect(screen.getByLabelText("PDF file")).toHaveAttribute("accept", "application/pdf,.pdf");
    expect(screen.getByLabelText("Ask about the document")).toBeDisabled();
  });

  it("uploads a PDF and renders grounded chat results", async () => {
    const user = userEvent.setup();
    uploadDocument.mockResolvedValue({ documentId: "doc-1", filename: "guide.pdf", pageCount: 2, chunkCount: 3 });
    askDocument.mockResolvedValue({
      ragAnswer: "Returns are allowed for thirty days.",
      mcpAnswer: null,
      sources: [{ page: 2, score: 1.5, excerpt: "Return within thirty days." }],
      webSources: [],
    });
    render(<App />);
    await user.upload(screen.getByLabelText("PDF file"), new File(["%PDF"], "guide.pdf", { type: "application/pdf" }));
    expect(await screen.findByRole("heading", { name: "guide.pdf" })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Ask about the document"), "What is the return window?");
    await user.click(screen.getByLabelText("Send question"));
    expect(await screen.findByText("Returns are allowed for thirty days.")).toBeInTheDocument();
    expect(screen.getByText(/page 2/i)).toBeInTheDocument();
    await waitFor(() => expect(askDocument).toHaveBeenCalledWith({ documentId: "doc-1", question: "What is the return window?", includeWeb: false }, { signal: expect.any(AbortSignal) }));
  });

  it("rejects a non-PDF before making an upload request", async () => {
    render(<App />);
    fireEvent.change(screen.getByLabelText("PDF file"), {
      target: { files: [new File(["text"], "notes.txt", { type: "text/plain" })] },
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(/choose a pdf/i);
    expect(uploadDocument).not.toHaveBeenCalled();
  });
});


const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const selectPdf = (name) => fireEvent.change(screen.getByLabelText("PDF file"), {
  target: { files: [new File(["%PDF"], name, { type: "application/pdf" })] },
});
const session = (id) => ({ documentId: id, filename: `${id}.pdf`, pageCount: 1, chunkCount: 1 });

it("discards a late answer after replacing the document, even if abort is ignored", async () => {
  const pending = deferred();
  uploadDocument.mockResolvedValueOnce(session("A")).mockResolvedValueOnce(session("B"));
  askDocument.mockReturnValueOnce(pending.promise);
  render(<App />);
  selectPdf("A.pdf");
  await screen.findByRole("heading", { name: "A.pdf" });
  fireEvent.change(screen.getByLabelText("Ask about the document"), { target: { value: "Question A" } });
  fireEvent.click(screen.getByLabelText("Send question"));
  const requestSignal = askDocument.mock.calls.at(-1)[1].signal;
  fireEvent.click(screen.getByRole("button", { name: "Replace" }));
  expect(requestSignal.aborted).toBe(true);
  selectPdf("B.pdf");
  await screen.findByRole("heading", { name: "B.pdf" });
  await act(async () => pending.resolve({ ragAnswer: "Old A answer", sources: [] }));
  expect(screen.queryByText("Old A answer")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "B.pdf" })).toBeInTheDocument();
  expect(deleteDocument).toHaveBeenCalledWith("A");
});

it("keeps the latest upload and deletes a late superseded session", async () => {
  const pending = deferred();
  uploadDocument.mockReturnValueOnce(pending.promise).mockResolvedValueOnce(session("B"));
  render(<App />);
  selectPdf("A.pdf");
  const firstSignal = uploadDocument.mock.calls.at(-1)[1].signal;
  selectPdf("B.pdf");
  await screen.findByRole("heading", { name: "B.pdf" });
  await act(async () => pending.resolve(session("A")));
  expect(firstSignal.aborted).toBe(true);
  expect(screen.queryByRole("heading", { name: "A.pdf" })).not.toBeInTheDocument();
  expect(deleteDocument).toHaveBeenCalledWith("A");
});

it("ignores stale failures and aborts pending requests when unmounted", async () => {
  const pending = deferred();
  uploadDocument.mockReturnValueOnce(pending.promise);
  const { unmount } = render(<App />);
  selectPdf("A.pdf");
  const signal = uploadDocument.mock.calls.at(-1)[1].signal;
  unmount();
  expect(signal.aborted).toBe(true);
  await act(async () => pending.reject(new Error("obsolete error")));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
