import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RetrievalGate } from "./RetrievalGate";

describe("RetrievalGate", () => {
  const prompt = "Definește ce este un invariant de buclă.";

  it("renders the prompt", () => {
    render(<RetrievalGate prompt={prompt} onSubmit={() => {}} />);
    expect(screen.getByTestId("retrieval-gate")).toBeInTheDocument();
    expect(screen.getByTestId("retrieval-prompt")).toHaveTextContent(prompt);
  });

  it("submit button is disabled when answer input is empty", () => {
    render(<RetrievalGate prompt={prompt} onSubmit={() => {}} />);
    expect(screen.getByTestId("retrieval-submit")).toBeDisabled();
  });

  it("submit button enables when answer is typed", () => {
    render(<RetrievalGate prompt={prompt} onSubmit={() => {}} />);
    fireEvent.change(screen.getByTestId("retrieval-answer-input"), {
      target: { value: "Un invariant este o proprietate care rămâne adevărată." },
    });
    expect(screen.getByTestId("retrieval-submit")).not.toBeDisabled();
  });

  it("calls onSubmit with the typed answer text", () => {
    const onSubmit = vi.fn();
    render(<RetrievalGate prompt={prompt} onSubmit={onSubmit} />);
    const input = screen.getByTestId("retrieval-answer-input");
    fireEvent.change(input, { target: { value: "Răspunsul meu complet." } });
    fireEvent.click(screen.getByTestId("retrieval-submit"));
    expect(onSubmit).toHaveBeenCalledWith("Răspunsul meu complet.");
  });

  it("submit button stays disabled when answer is whitespace only", () => {
    render(<RetrievalGate prompt={prompt} onSubmit={() => {}} />);
    fireEvent.change(screen.getByTestId("retrieval-answer-input"), {
      target: { value: "   " },
    });
    expect(screen.getByTestId("retrieval-submit")).toBeDisabled();
  });
});
