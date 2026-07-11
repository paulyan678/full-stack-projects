import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "../src/App.jsx";
import { askDocument, uploadDocument } from "../src/api.js";

vi.mock("../src/api.js", () => ({
  uploadDocument: vi.fn(),
  askDocument: vi.fn(),
  deleteDocument: vi.fn(() => Promise.resolve()),
}));

describe("Agent AI application", () => {
  beforeEach(() => vi.clearAllMocks());

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
    await waitFor(() => expect(askDocument).toHaveBeenCalledWith({ documentId: "doc-1", question: "What is the return window?", includeWeb: false }));
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
